package cn.aslight.workhub.service.autonomy;

import cn.aslight.workhub.model.autonomy.AgentObservationEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/** 从真实查询快照提取稳定身份和发生时间，不接受模型自报的来源或业务范围。 */
@Component
public class AutonomyEvidence {
    private final ObjectMapper json;
    public AutonomyEvidence(ObjectMapper json) { this.json = json; }

    public Extracted extract(AgentObservationEntity observation, String pointer, String category, Long staleAfterSeconds) {
        JsonNode root = json.readTree(observation.payloadJson());
        if (root.path("truncated").asBoolean()) throw new IllegalArgumentException("证据已截断，请缩小查询范围后重新取得具体记录");
        if (pointer == null || pointer.length() > 256) throw new IllegalArgumentException("证据位置无效");
        if (!"STALE_DATA".equals(category) && staleAfterSeconds != null) throw new IllegalArgumentException("数据时效阈值只能用于 STALE_DATA");
        String code = observation.toolCode();
        if ("inspection.next".equals(code)) code = switch (observation.sourceType()) {
            case "SYSTEM_ALERT" -> "monitor.system.alerts";
            case "ACCOUNTING" -> "monitor.accounting.runs";
            default -> throw new IllegalArgumentException("该巡检来源尚无问题证据适配器");
        };
        if ("/data".equals(pointer) && Set.of("monitor.business.status", "monitor.accounting.status").contains(code)) {
            require("MONITORING_GAP".equals(category) && "NO_CONFIGURATION".equals(root.path("coverage").asText())
                    && root.path("data").isArray() && root.path("data").isEmpty(), "只能将实际无配置的查询登记为监测覆盖待核实问题");
            return extracted("no-configuration", "no-configuration", json.valueToTree(List.of(code, "NO_CONFIGURATION")), null,
                    Map.of("coverage", "NO_CONFIGURATION", "assessment", "AI_CANDIDATE_REQUIRES_REVIEW"));
        }
        String prefix = switch (code) {
            case "monitor.business.status", "monitor.accounting.status" -> "/data/";
            case "monitor.business.detail" -> "/data/logs/";
            case "monitor.accounting.detail" -> "/data/results/";
            case "monitor.accounting.runs" -> "/data/runs/";
            case "monitor.system.alerts" -> "/data/events/";
            case "workhub.logs" -> "/data/items/";
            default -> throw new IllegalArgumentException("该来源尚无受支持的问题证据适配器");
        };
        require(pointer.startsWith(prefix) && pointer.substring(prefix.length()).matches("0|[1-9][0-9]{0,5}"), "请引用工具 data 下的一条完整记录");
        JsonNode item = root.at(pointer);
        require(item.isObject(), "证据位置没有实际记录");
        ZoneId zone = ZoneId.of(root.path("recordTimeZone").asText());
        return switch (code) {
            case "monitor.business.status" -> business(item, zone, category, staleAfterSeconds, observation);
            case "monitor.business.detail" -> businessFailure(root, item, zone, category);
            case "monitor.accounting.status" -> accounting(item, zone, category, staleAfterSeconds, observation);
            case "monitor.accounting.detail" -> accountingResult(root, item, zone, category);
            case "monitor.accounting.runs" -> accountingRunFailure(item, zone, category);
            case "monitor.system.alerts", "workhub.logs" -> error(item, zone, category, code);
            default -> throw new IllegalArgumentException("来源尚未接入");
        };
    }

    private Extracted business(JsonNode item, ZoneId zone, String category, Long threshold, AgentObservationEntity observation) {
        String id = required(item, "id");
        String state = item.path("dataState").asText();
        LocalDateTime occurred = time(item, "lastCheckedAt", zone);
        switch (category) {
            case "MONITORING_GAP" -> require(Set.of("DISABLED", "NEVER_RUN", "UNAVAILABLE", "FUTURE_TIMESTAMP").contains(state), "该记录不支持监测覆盖问题");
            case "EXECUTION_FAILURE" -> require("ERROR".equals(item.path("status").asText()) && "HAS_RESULT".equals(state), "该监测记录不是实际执行异常");
            case "STALE_DATA" -> stale(observation, occurred, threshold);
            default -> throw new IllegalArgumentException("服务监测汇总不能证明该问题类型，请继续读取失败详情");
        }
        // dataAgeSeconds 随每次查询改变，不参与来源版本。
        var version = item.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) version).remove("dataAgeSeconds");
        return extracted("monitor:" + id, "monitor:" + id, version, occurred, evidence(item, category, threshold));
    }

    private Extracted businessFailure(JsonNode root, JsonNode item, ZoneId zone, String category) {
        require("EXECUTION_FAILURE".equals(category), "XXL-JOB 失败记录仅支持执行失败问题");
        String monitorId = required(root.path("data"), "id");
        String job = required(item, "jobId"), log = required(item, "logId");
        require(Set.of("EXECUTION_FAILED", "SCHEDULE_FAILED").contains(item.path("failureType").asText()), "记录不是调度失败或执行失败");
        LocalDateTime occurred = time(item, "handleTime", zone);
        if (occurred == null) occurred = time(item, "triggerTime", zone);
        return extracted("monitor:" + monitorId + ":job:" + job, "monitor:" + monitorId + ":log:" + log, item, occurred, item);
    }

    private Extracted accounting(JsonNode item, ZoneId zone, String category, Long threshold, AgentObservationEntity observation) {
        String config = required(item, "configId"), state = item.path("dataState").asText();
        JsonNode run = item.path("latestRun");
        zone = runZone(run, zone);
        if ("EXECUTION_FAILURE".equals(category)) return accountingRunFailure(run, zone, category);
        LocalDateTime occurred = time(run, "finishedAt", zone);
        switch (category) {
            case "MONITORING_GAP" -> require(Set.of("DISABLED", "NEVER_RUN", "NOT_EVALUATED", "FUTURE_TIMESTAMP").contains(state), "当前记录不是未评估或监测覆盖待核实状态");
            case "STALE_DATA" -> stale(observation, occurred, threshold);
            default -> throw new IllegalArgumentException("账务差异必须引用具体规则结果，不能从汇总或执行失败推断");
        }
        var version = (tools.jackson.databind.node.ObjectNode) item.deepCopy(); version.remove("dataAgeSeconds");
        String event = run.hasNonNull("id") ? "run:" + required(run, "id") : "config:" + config;
        return extracted("config:" + config, event, version, occurred, evidence(item, category, threshold));
    }

    private Extracted accountingRunFailure(JsonNode run, ZoneId zone, String category) {
        zone = runZone(run, zone);
        require("EXECUTION_FAILURE".equals(category) && "FAILED".equals(run.path("status").asText()),
                "历史运行只可凭 FAILED 登记执行失败；账务差异需引用具体规则结果");
        return extracted("config:" + required(run, "configId"), "run:" + required(run, "id"), run, time(run, "finishedAt", zone), run);
    }

    private Extracted accountingResult(JsonNode root, JsonNode item, ZoneId zone, String category) {
        JsonNode run = root.path("data").path("run");
        zone = runZone(run, zone);
        String config = required(run, "configId"), runId = required(run, "id"), rule = required(item, "ruleCode");
        String status = item.path("status").asText();
        require(runId.equals(required(item, "runId")), "规则记录与查询运行不匹配");
        switch (category) {
            case "DATA_DISCREPANCY" -> require(Set.of("WARN", "ERROR").contains(status), "仅 WARN/ERROR 规则是账务差异候选；FAILED/SKIPPED 不能解释为差异");
            case "EXECUTION_FAILURE" -> require("FAILED".equals(status), "该规则没有执行失败");
            case "MONITORING_GAP" -> require("SKIPPED".equals(status), "该规则并非跳过或未评估");
            default -> throw new IllegalArgumentException("规则结果不支持该问题类型");
        }
        return extracted("config:" + config + ":rule:" + rule, "run:" + runId + ":result:" + required(item, "id"), item,
                time(item, "createdAt", zone), item);
    }

    private ZoneId runZone(JsonNode run, ZoneId fallback) {
        var evidence = run.path("executionEvidence");
        return evidence.path("scopeVerified").asBoolean() ? ZoneId.of(evidence.path("snapshot").path("recordTimeZone").asText()) : fallback;
    }

    private Extracted error(JsonNode item, ZoneId zone, String category, String code) {
        String eventCategory = item.path("eventCategory").asText();
        if ("PERFORMANCE".equals(category)) require("SLOW_SQL".equals(eventCategory), "仅已识别的慢 SQL 预警支持性能问题");
        else {
            require("APPLICATION_ERROR".equals(category), "日志或系统预警不支持该问题类型");
            require(Set.of("ERROR", "FATAL").contains(item.path("level").asText().toUpperCase(Locale.ROOT))
                    || !item.path("errorType").asText().isBlank() || !item.path("stackTrace").asText().isBlank(), "该日志没有异常等级、异常类型或堆栈证据");
        }
        String service = required(item, "serviceName");
        String stack = item.path("stackTrace").asText();
        String signature = item.path("errorType").asText() + "|" + eventCategory + "|"
                + (stack.isBlank() ? item.path("message").asText() : String.join("\n", stack.lines().limit(8).toList()));
        require(!signature.replace("|", "").isBlank(), "缺少可归并的异常特征");
        // 去掉请求 UUID、时间和堆栈行号；业务错误码仍保留，避免把不同故障混成同一问题。
        signature = signature.replaceAll("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<uuid>")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}[T ][0-9:.+Z-]+", "<time>").replaceAll("(?<=\\.java):\\d+", ":<line>");
        String event = required(item, "workhub.logs".equals(code) ? "sourceEventId" : "id");
        return extracted("service:" + digest(service) + ":error:" + digest(signature), event, item, time(item, "occurredAt", zone), item);
    }

    private Object evidence(JsonNode item, String category, Long threshold) {
        if (!"STALE_DATA".equals(category)) return item;
        return Map.of("record", item, "criterion", Map.of("source", "AI_PROPOSED_LIMIT", "staleAfterSeconds", threshold),
                "assessment", "DATA_FRESHNESS_REQUIRES_REVIEW");
    }

    private void stale(AgentObservationEntity observation, LocalDateTime occurred, Long threshold) {
        require(threshold != null && threshold >= 60 && threshold <= 2_592_000, "STALE_DATA 需给出60秒至30天的预期时效阈值；此阈值作为 AI 建议保留供审核");
        require(occurred != null && Duration.between(occurred, observation.observedAt()).getSeconds() > threshold, "记录时间尚未超过所给预期时效，或记录时间未知");
    }

    private Extracted extracted(String subject, String event, JsonNode version, LocalDateTime occurred, Object evidence) {
        return new Extracted(subject.length() <= 256 ? subject : "sha256:" + digest(subject),
                event.length() <= 256 ? event : "sha256:" + digest(event), digest(json.writeValueAsString(version)), occurred, json.valueToTree(evidence));
    }
    private String required(JsonNode node, String field) {
        require(node.hasNonNull(field) && !node.path(field).asText().isBlank(), "证据缺少来源字段: " + field);
        return node.path(field).asText();
    }
    private LocalDateTime time(JsonNode node, String field, ZoneId zone) {
        if (!node.hasNonNull(field) || node.path(field).asText().isBlank()) return null;
        String value = node.path(field).asText().replace(' ', 'T');
        try { return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC); }
        catch (DateTimeParseException ex) {
            try { return LocalDateTime.ofInstant(LocalDateTime.parse(value).atZone(zone).toInstant(), ZoneOffset.UTC); }
            catch (DateTimeParseException invalid) { throw new IllegalArgumentException("来源时间格式无法核实: " + field); }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public record Extracted(String subjectKey, String sourceEventId, String sourceVersion, LocalDateTime occurredAt, JsonNode evidence) { }
}
