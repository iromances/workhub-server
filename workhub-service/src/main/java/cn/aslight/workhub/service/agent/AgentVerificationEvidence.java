package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.autonomy.AgentObservationEntity;
import cn.aslight.workhub.service.autonomy.AgentObservationService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 只解释平台来源的完整性和时间，不计算业务通过条件；业务含义由 Agent 判断。 */
@Component
public class AgentVerificationEvidence {
    private final ObjectMapper json;
    public AgentVerificationEvidence(ObjectMapper json) { this.json = json; }

    public Fact inspect(AgentObservationEntity row, AgentToolContext context, AgentReleaseVerificationPlan.Check check,
                        String pointer, Instant deployedAt, Instant now) {
        if (row == null || !row.runId().equals(context.runId()) || !row.agentId().equals(context.agentId())
                || !row.toolCode().equals(check.toolCode())
                || !row.sourceType().equals(AgentObservationService.sourceType(check.toolCode(), check.scope().businessLineCode()))
                || !row.businessLineCode().equals(check.scope().businessLineCode()) || !row.environmentCode().equals(check.scope().environmentCode()))
            throw new IllegalArgumentException("复查证据不属于本次运行、获批工具或业务线环境");
        if (!AgentCodePolicy.hash(row.payloadJson()).equals(row.contentHash())) throw new IllegalArgumentException("监测证据摘要不一致");
        JsonNode root = json.readTree(row.payloadJson());
        if (!json.valueToTree(check.scope()).equals(root.path("scope"))) throw new IllegalArgumentException("监测快照范围与来源索引不一致");
        var problems = new LinkedHashSet<String>();
        Instant observedAt = row.observedAt().toInstant(ZoneOffset.UTC);
        Instant queriedAt = instant(root.path("observedAt"), null);
        // 数据库 DATETIME(6) 只能保留微秒；来源原文可能含纳秒。
        if (queriedAt == null || Duration.between(observedAt, queriedAt).abs().compareTo(Duration.ofNanos(1000)) > 0)
            throw new IllegalArgumentException("监测查询时间与来源索引不一致");
        if (observedAt.isAfter(now) || observedAt.isBefore(deployedAt)) problems.add("OBSERVATION_OUTSIDE_DEPLOYED_WINDOW");
        String sourceKey = check.toolCode(), eventKey = row.id();
        Instant dataAt = null, coverageFrom = null, coverageThrough = null;
        if (root.path("truncated").asBoolean()) problems.add("TRUNCATED_EVIDENCE");
        else {
            JsonNode selected = select(root, check.toolCode(), pointer);
            ZoneId zone = zone(root.path("recordTimeZone"));
            switch (check.toolCode()) {
                case "monitor.business.status" -> {
                    sourceKey += ":" + positive(selected, "id");
                    // 现有业务监测结果没有固定的执行范围；updatedAt 在每次采集时也变化，不能冒充配置版本。
                    problems.add("EXECUTION_SCOPE_UNVERIFIED");
                    if (!selected.path("enabled").asBoolean() || !"HAS_RESULT".equals(selected.path("dataState").asText())
                            || !"XXL_JOB".equals(selected.path("type").asText())) problems.add("MONITOR_NOT_EVALUATED");
                    dataAt = instant(selected.path("lastCheckedAt"), zone);
                    Instant changed = instant(selected.path("configurationUpdatedAt"), zone);
                    if (changed == null || dataAt == null || changed.isAfter(dataAt)) problems.add("MONITOR_CONFIGURATION_UNVERIFIED");
                    if ("ERROR".equals(selected.path("status").asText()) || selected.path("status").asText().isBlank())
                        problems.add("MONITOR_QUERY_NOT_COMPLETED");
                    eventKey = sourceKey + ":" + dataAt;
                }
                case "monitor.accounting.status", "monitor.accounting.runs", "monitor.accounting.detail" -> {
                    JsonNode run = switch (check.toolCode()) {
                        case "monitor.accounting.status" -> selected.path("latestRun");
                        case "monitor.accounting.detail" -> root.at("/data/run");
                        default -> selected;
                    };
                    JsonNode proof = run.path("executionEvidence"), snapshot = proof.path("snapshot"), config = snapshot.path("configuration");
                    if (!proof.path("scopeVerified").asBoolean() || !"RECORDED".equals(proof.path("state").asText())) problems.add("EXECUTION_SCOPE_UNVERIFIED");
                    else if (!check.scope().businessLineCode().equals(config.path("businessLineCode").asText())
                            || !check.scope().environmentCode().equals(config.path("environmentCode").asText())
                            || !run.path("configId").equals(config.path("configId")) || !run.path("id").equals(snapshot.path("runId")))
                        throw new IllegalArgumentException("账务执行凭据范围或运行身份不一致");
                    zone = zone(snapshot.path("recordTimeZone"));
                    Instant startedAt = instant(run.path("startedAt"), zone);
                    dataAt = instant(run.path("finishedAt"), zone);
                    if (startedAt == null || startedAt.isBefore(deployedAt) || dataAt == null || dataAt.isBefore(startedAt))
                        problems.add("EXECUTION_NOT_AFTER_DEPLOYMENT");
                    if (!Set.of("SUCCESS", "WARNING").contains(run.path("status").asText())
                            || !zero(run, "failedCount") || !zero(run, "skippedCount")) problems.add("EXECUTION_INCOMPLETE");
                    if ("monitor.accounting.status".equals(check.toolCode()) && !selected.path("enabled").asBoolean())
                        problems.add("MONITOR_DISABLED");
                    sourceKey += ":" + run.path("configId").asText() + ":" + snapshot.path("configurationHash").asText()
                            + ":" + snapshot.path("targetBindingHash").asText();
                    if ("monitor.accounting.detail".equals(check.toolCode()) && pointer.startsWith("/data/results/")) {
                        String code = selected.path("ruleCode").asText();
                        JsonNode rule = null;
                        for (var candidate : snapshot.path("rules")) if (code.equals(candidate.path("ruleCode").asText())) rule = candidate;
                        if (code.isBlank() || rule == null || !run.path("id").equals(selected.path("runId")))
                            problems.add("RULE_EXECUTION_UNVERIFIED");
                        if (!Set.of("PASS", "WARN", "ERROR").contains(selected.path("status").asText())) problems.add("RULE_NOT_EVALUATED");
                        sourceKey += ":" + code + ":" + (rule == null ? "" : rule.path("definitionHash").asText());
                    } else {
                        var definitions = new TreeMap<String, String>();
                        for (var rule : snapshot.path("rules")) definitions.put(rule.path("ruleCode").asText(), rule.path("definitionHash").asText());
                        if (definitions.isEmpty()) problems.add("RULE_EXECUTION_UNVERIFIED");
                        sourceKey += ":" + AgentCodePolicy.hash(json.writeValueAsString(definitions));
                        if ("monitor.accounting.detail".equals(check.toolCode()) && (root.at("/data/hasMore").asBoolean()
                                || !root.at("/data/results").isArray() || root.at("/data/results").size() != run.path("ruleTotal").asInt(-1)))
                            problems.add("RULE_RESULTS_INCOMPLETE");
                    }
                    eventKey = sourceKey + ":" + run.path("id").asText();
                }
                case "monitor.business.detail" -> {
                    var raw = selected.path("executionEvidence");
                    if (!raw.isObject()) problems.add("EXECUTION_SCOPE_UNVERIFIED");
                    else {
                        var proof = json.treeToValue(raw, cn.aslight.workhub.model.ops.XxlJobQueryEvidence.class);
                        var definition = proof.definition();
                        if (proof.schemaVersion() != 1 || definition == null || definition.monitorId() == null
                                || !AgentCodePolicy.hash(json.writeValueAsString(definition)).equals(proof.definitionHash())
                                || proof.targetBindingHash() == null || !proof.targetBindingHash().matches("[a-f0-9]{64}")
                                || !check.scope().businessLineCode().equals(definition.businessLineCode())
                                || !check.scope().environmentCode().equals(definition.environmentCode())
                                || definition.monitorId() != selected.path("id").asLong()
                                || !definition.businessLineCode().equals(selected.path("businessLineCode").asText())
                                || !definition.environmentCode().equals(selected.path("environmentCode").asText()))
                            throw new IllegalArgumentException("业务监测执行凭据的来源、范围或摘要不一致");
                        if (!definition.enabled() || !"XXL_JOB".equals(definition.monitorType())) problems.add("MONITOR_NOT_EVALUATED");
                        if (proof.startedAt() == null || proof.finishedAt() == null || proof.startedAt().isBefore(deployedAt)
                                || proof.finishedAt().isBefore(proof.startedAt()) || proof.finishedAt().isAfter(observedAt))
                            problems.add("EXECUTION_NOT_AFTER_DEPLOYMENT");
                        if (!"QUERY_SUCCEEDED".equals(root.path("coverage").asText()) || !proof.completePage() || definition.page() != 1
                                || !selected.path("logs").isArray() || proof.matchingCount() != selected.path("logs").size()
                                || proof.matchingCount() != selected.path("total").asLong(-1) || proof.matchingCount() < 0
                                || proof.activityCount() < proof.matchingCount()) problems.add("QUERY_RESULTS_INCOMPLETE");
                        if (proof.activityCount() == 0) problems.add("NO_BUSINESS_ACTIVITY");
                        ZoneId recordZone = zone(raw.path("recordTimeZone"));
                        dataAt = instant(raw.path("latestActivityAt"), recordZone);
                        if (proof.finishedAt() != null && dataAt != null && dataAt.isAfter(proof.finishedAt())) problems.add("DATA_OUTSIDE_QUERY_WINDOW");
                        sourceKey += ":" + proof.definitionHash() + ":" + proof.targetBindingHash();
                        eventKey = sourceKey + ":" + proof.executionId();
                    }
                }
                case "workhub.logs" -> {
                    var raw = selected.path("executionEvidence");
                    if (!raw.isObject()) {
                        problems.add("COLLECTION_COVERAGE_UNPROVEN");
                        if (selected.path("truncated").asBoolean() || selected.path("hasMore").asBoolean()) problems.add("TRUNCATED_EVIDENCE");
                    } else {
                        var proof = json.treeToValue(raw, cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.class);
                        var definition = proof.definition();
                        if (proof.schemaVersion() != 1 || definition == null || definition.scopeId() == null
                                || !AgentCodePolicy.hash(json.writeValueAsString(definition)).equals(proof.definitionHash())
                                || !digest(proof.targetBindingHash()) || !digest(proof.snapshotHash())
                                || !check.scope().businessLineCode().equals(definition.businessLineCode())
                                || !check.scope().environmentCode().equals(definition.environmentCode())
                                || !definition.businessLineCode().equals(selected.path("businessLineCode").asText())
                                || !definition.environmentCode().equals(selected.path("environmentCode").asText())
                                || !json.valueToTree(definition.indexPatterns()).equals(selected.path("indexPatterns"))
                                || !json.valueToTree(definition.serviceNames()).equals(selected.path("serviceNames"))
                                || !json.valueToTree(definition.levels()).equals(selected.path("levels"))
                                || !json.valueToTree(proof.from()).equals(selected.path("from")) || !json.valueToTree(proof.to()).equals(selected.path("to")))
                            throw new IllegalArgumentException("日志执行凭据的来源、范围或摘要不一致");
                        if (proof.startedAt() == null || proof.finishedAt() == null || proof.startedAt().isBefore(deployedAt)
                                || proof.finishedAt().isBefore(proof.startedAt()) || proof.finishedAt().isAfter(observedAt))
                            problems.add("EXECUTION_NOT_AFTER_DEPLOYMENT");
                        if (!proof.complete() || selected.path("truncated").asBoolean() || !selected.path("items").isArray()
                                || selected.path("count").asInt(-1) != selected.path("items").size()) problems.add("QUERY_RESULTS_INCOMPLETE");
                        var expected = definition.serviceNames() == null ? Set.<String>of() : new HashSet<>(definition.serviceNames());
                        var activities = proof.activities() == null ? List.<cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.Activity>of() : proof.activities();
                        if (expected.isEmpty() || activities.size() != expected.size()
                                || !expected.equals(new HashSet<>(activities.stream().map(cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.Activity::serviceName).toList())))
                            problems.add("SERVICE_ACTIVITY_COVERAGE_UNPROVEN");
                        for (var activity : activities) {
                            if (activity.count() <= 0 || activity.latestAt() == null || !digest(activity.latestEventId())) problems.add("NO_SERVICE_ACTIVITY");
                            else {
                                if (proof.from() == null || proof.to() == null || activity.latestAt().isBefore(proof.from())
                                        || activity.latestAt().isAfter(proof.to()) || proof.startedAt() == null || activity.latestAt().isAfter(proof.startedAt()))
                                    problems.add("DATA_OUTSIDE_QUERY_WINDOW");
                                if (dataAt == null || activity.latestAt().isBefore(dataAt)) dataAt = activity.latestAt();
                            }
                        }
                        coverageFrom = proof.from(); coverageThrough = proof.to();
                        if (coverageFrom == null || coverageThrough == null || !coverageFrom.isBefore(coverageThrough)
                                || proof.startedAt() == null || coverageThrough.isAfter(proof.startedAt())) problems.add("QUERY_WINDOW_UNVERIFIED");
                        sourceKey += ":" + proof.definitionHash() + ":" + proof.targetBindingHash();
                        eventKey = sourceKey + ":" + proof.executionId();
                    }
                }
                case "monitor.system.alerts" -> {
                    // 当前来源只有失败/匹配记录，没有查询范围内连续采集健康证明；空列表不能自动通过。
                    problems.add("COLLECTION_COVERAGE_UNPROVEN");
                    if (root.at("/data/truncated").asBoolean() || root.at("/data/hasMore").asBoolean()) problems.add("TRUNCATED_EVIDENCE");
                }
                default -> throw new IllegalArgumentException("尚未接入此复查来源");
            }
        }
        if (dataAt == null) problems.add("DATA_TIMESTAMP_MISSING");
        else {
            if (dataAt.isAfter(observedAt) || dataAt.isBefore(deployedAt)) problems.add("DATA_OUTSIDE_DEPLOYED_WINDOW");
            if (dataAt.plusSeconds(check.maxDataAgeSeconds()).isBefore(observedAt)) problems.add("DATA_STALE_AT_QUERY");
        }
        return new Fact(row.id(), row.contentHash(), pointer, sourceKey, eventKey, observedAt, dataAt, List.copyOf(problems), coverageFrom, coverageThrough);
    }

    private JsonNode select(JsonNode root, String tool, String pointer) {
        boolean allowed = pointer != null && switch (tool) {
            case "monitor.business.status", "monitor.accounting.status" -> pointer.matches("/data/(0|[1-9][0-9]*)");
            case "monitor.accounting.runs" -> pointer.matches("/data/runs/(0|[1-9][0-9]*)");
            case "monitor.accounting.detail" -> pointer.equals("/data") || pointer.matches("/data/results/(0|[1-9][0-9]*)");
            default -> pointer.equals("/data");
        };
        if (!allowed) throw new IllegalArgumentException("须引用该工具的完整监测项、账务运行/规则或查询结果，不能只选择标量");
        var selected = root.at(pointer);
        if (!selected.isObject()) throw new IllegalArgumentException("引用位置没有完整监测记录，请重新读取真实监测证据");
        return selected;
    }
    private boolean digest(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private boolean zero(JsonNode value, String key) { return value.path(key).isIntegralNumber() && value.path(key).asLong() == 0; }
    private long positive(JsonNode value, String key) {
        if (!value.path(key).isIntegralNumber() || value.path(key).asLong() <= 0) throw new IllegalArgumentException("来源记录标识缺失");
        return value.path(key).asLong();
    }
    private ZoneId zone(JsonNode value) {
        try { return value.isString() ? ZoneId.of(value.asText()) : null; }
        catch (DateTimeException ex) { return null; }
    }
    private Instant instant(JsonNode value, ZoneId zone) {
        if (!value.isString()) return null;
        try {
            if (zone == null) return Instant.parse(value.asText());
            return LocalDateTime.parse(value.asText()).atZone(zone).toInstant();
        } catch (DateTimeException ex) { return null; }
    }
    public record Fact(String observationId, String contentHash, String pointer, String sourceKey, String eventKey,
                       Instant observedAt, Instant dataAt, List<String> problems, Instant coverageFrom, Instant coverageThrough) { }
}
