package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import cn.aslight.workhub.service.ops.AccountingExecutionEvidenceService;
import cn.aslight.workhub.service.autonomy.AgentObservationService;
import cn.aslight.workhub.service.autonomy.AutonomyEvidence;
import cn.aslight.workhub.service.autonomy.AutonomyCaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.*;
import java.util.*;

/** 有界重扫补漏，先保存事实再交付分析；确认分析不等于关闭业务问题。 */
@Service
public class AgentInspectionService {
    public enum Source {
        SYSTEM_ALERT(Set.of("ops:system-alert:view", "ops:system-alert:manage")),
        ACCOUNTING_RUN(Set.of("ops:accounting:view", "ops:accounting:manage")),
        BUG(Set.of("work-item:view"));
        private final Set<String> permissions;
        Source(Set<String> permissions) { this.permissions = permissions; }
        public Set<String> permissions() { return permissions; }
    }
    private static final int PAGE_SIZE = 20;
    private static final int PENDING_PAGE_SIZE = 10;
    private final AgentInspectionMapper mapper;
    private final AgentMonitorReadMapper sources;
    private final AgentMapper agents;
    private final AutonomyMapper autonomy;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final AgentObservationService observations;
    private final AutonomyEvidence evidence;
    private final AgentToolData data;
    private final ObjectMapper json;
    private final AutonomyCaseService cases;
    private final AccountingExecutionEvidenceService executionEvidence;

    public AgentInspectionService(AgentInspectionMapper mapper, AgentMonitorReadMapper sources, AgentMapper agents,
            AutonomyMapper autonomy, AgentRunStore runs, AgentAccessService access, AgentObservationService observations,
            AutonomyEvidence evidence, AgentToolData data, ObjectMapper json, AutonomyCaseService cases,
            AccountingExecutionEvidenceService executionEvidence) {
        this.mapper = mapper; this.sources = sources; this.agents = agents; this.autonomy = autonomy; this.runs = runs;
        this.access = access; this.observations = observations; this.evidence = evidence; this.data = data; this.json = json;
        this.cases = cases;
        this.executionEvidence = executionEvidence;
    }

    // 授权读取发生在行锁之前；等待其他扫描提交后必须看到新队列，不能沿用 MySQL 默认的旧一致性快照。
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode next(AgentToolContext context, Source source, String line, String environment, Long retryItemId) {
        String env = authorize(context, "inspection.next", source, line, environment);
        String key = callKey(context, "inspection.next");
        String hash = digest(Arrays.asList("inspection.next", source, line, env, retryItemId));
        var previous = replay(context, key, hash);
        if (previous != null) return previous;
        String id = streamId(context, source, line, env);
        mapper.ensureStream(new AgentInspectionStream(id, context.taskId(), source.name(), line, env, 0, 0, 0, now()));
        var stream = mapper.lockStream(id);
        var item = mapper.next(id);
        if (retryItemId != null) {
            if (item != null && item.id() != retryItemId) throw new IllegalArgumentException("先确认当前未分析的记录，再重新领取待补充记录");
            item = mapper.item(retryItemId);
            if (item == null || !id.equals(item.streamId()) || !Set.of("NEEDS_INPUT", "PENDING").contains(item.status()))
                throw new IllegalArgumentException("只能重新领取本任务同一来源范围的待补充记录");
        } else if (item == null) {
            scan(stream, source);
            stream = mapper.stream(id);
            item = mapper.next(id);
        }
        ObjectNode result = json.createObjectNode();
        result.set("progress", json.valueToTree(stream));
        result.put("coverage", "PERSISTED_SOURCE_ONLY");
        result.put("interpretation", "扫描位置仅代表已保存事实；空页、完成一轮扫描或 AI 确认均不代表业务健康或问题已修复。");
        if (item == null) result.put("state", "SCAN_ADVANCED");
        else if ("PENDING".equals(item.status()) && !canTake(context, item)) {
            result.put("state", "WAITING_OTHER_RUN").put("itemId", item.id()).put("assignedRunId", item.assignedRunId());
        } else deliver(context, source, item, result);
        result.put("waitingForInput", mapper.pendingCount(id));
        return save(context, key, hash, result);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode acknowledge(AgentToolContext context, long itemId, String outcome, String reason, String caseId,
                                String findingObservationId, String findingPointer) {
        var item = requiredItem(itemId);
        var stream = mapper.stream(item.streamId());
        authorize(context, "inspection.ack", Source.valueOf(stream.sourceType()), stream.businessLineCode(), stream.environmentCode());
        if (!context.taskId().equals(stream.taskId())) throw new IllegalArgumentException("记录不属于本任务");
        if (!Set.of("NO_FINDING", "FINDING", "NEEDS_INPUT").contains(outcome) || reason == null || reason.isBlank() || reason.length() > 2000)
            throw new IllegalArgumentException("请提供有效分析结论及1至2000字的理由");
        String safeReason = data.sanitize(reason.trim()).asText();
        if (safeReason.length() > 2000) throw new IllegalArgumentException("理由脱敏后超过2000字，请精简后再提交");
        if ("FINDING".equals(outcome) != (caseId != null && !caseId.isBlank()))
            throw new IllegalArgumentException("仅 FINDING 必须提供已登记的问题编号");
        if ((findingObservationId == null) != (findingPointer == null)
                || (!"FINDING".equals(outcome) && findingObservationId != null))
            throw new IllegalArgumentException("补充证据编号与位置须同时提供，且仅用于 FINDING");
        String key = callKey(context, "inspection.ack"), hash = digest(Arrays.asList("inspection.ack", itemId, outcome, reason, caseId, findingObservationId, findingPointer));
        var previous = replay(context, key, hash);
        if (previous != null) return previous;
        mapper.lockStream(stream.id());
        item = requiredItem(itemId);
        if (!"PENDING".equals(item.status()) || !context.runId().equals(item.assignedRunId()))
            throw new IllegalArgumentException("只能确认本次运行领取且尚未确认的记录");
        var payload = item.observationId() == null ? json.readTree(item.payloadJson()) : json.readTree(autonomy.observation(item.observationId()).payloadJson());
        if (payload.path("truncated").asBoolean() && !"NEEDS_INPUT".equals(outcome))
            throw new IllegalArgumentException("截断记录只能标为待补充，不能确认已完整分析");
        if ("FINDING".equals(outcome)) {
            if ("BUG".equals(stream.sourceType())) {
                if (findingObservationId != null) throw new IllegalArgumentException("缺陷证据从已保存的关联读取，请不要提交替代证据");
                var linked = cases.requireBugFinding(context, caseId, item.sourceId(), item.contentHash());
                findingObservationId = linked.observationId(); findingPointer = linked.evidencePointer();
            } else findingPointer = verifyFinding(context, item, caseId, findingObservationId, findingPointer);
        }
        var analysis = new AgentInspectionAnalysis(UUID.randomUUID().toString(), itemId, context.runId(),
                findingObservationId == null ? item.observationId() : findingObservationId,
                outcome, findingPointer, safeReason, caseId, now());
        mapper.insertAnalysis(analysis);
        mapper.acknowledge(itemId, outcome, now());
        return save(context, key, hash, json.valueToTree(Map.of("analysis", analysis, "assessment", "AI_ANALYSIS_ONLY", "resolved", false)));
    }

    public JsonNode pending(AgentToolContext context, Source source, String line, String environment, long afterId) {
        String env = authorize(context, "inspection.pending", source, line, environment);
        runs.requireActive(context.runId(), context.ownerToken());
        if (afterId < 0) throw new IllegalArgumentException("续页位置不能为负数");
        String id = streamId(context, source, line, env);
        var found = mapper.pending(id, afterId, PENDING_PAGE_SIZE + 1);
        var page = found.stream().limit(PENDING_PAGE_SIZE).toList();
        return json.valueToTree(Map.of("items", page.stream().map(item -> Map.of("itemId", item.id(), "sourceId", item.sourceId(),
                        "status", item.status(), "analysis", mapper.latestAnalysis(item.id()))).toList(),
                "hasMore", found.size() > PENDING_PAGE_SIZE, "nextAfterItemId", found.size() > PENDING_PAGE_SIZE ? page.getLast().id() : 0L,
                "total", mapper.pendingCount(id)));
    }

    private void scan(AgentInspectionStream stream, Source source) {
        String line = stream.businessLineCode(), env = stream.environmentCode();
        long upper = stream.upperId() > 0 ? stream.upperId() : switch (source) {
            case SYSTEM_ALERT -> sources.alertUpper(line, env);
            case ACCOUNTING_RUN -> sources.accountingUpper(line, env);
            case BUG -> sources.bugUpper(line);
        };
        List<?> rows = switch (source) {
            case SYSTEM_ALERT -> sources.scanAlerts(line, env, stream.afterId(), upper, PAGE_SIZE);
            case ACCOUNTING_RUN -> sources.scanAccounting(line, env, stream.afterId(), upper, PAGE_SIZE);
            case BUG -> sources.scanBugs(line, stream.afterId(), upper, PAGE_SIZE);
        };
        long after = stream.afterId();
        for (Object row : rows) {
            var raw = row instanceof AccountingRunEntity accountRun ? executionEvidence.describe(accountRun) : json.valueToTree(row);
            after = raw.path("id").asLong();
            var masked = data.sanitize(raw);
            var payload = json.createObjectNode().put("observedAt", Instant.now().toString()).put("recordTimeZone", ZoneId.systemDefault().getId());
            payload.put("truncated", masked.path("truncated").asBoolean());
            if (source == Source.BUG) {
                payload.put("sourceType", "BUG").put("businessLineCode", line).put("environmentState", "UNRESOLVED");
                payload.set("data", masked);
            } else {
                payload.set("scope", json.valueToTree(new AgentScope(line, env)));
                payload.set("data", json.createObjectNode().set(source == Source.SYSTEM_ALERT ? "events" : "runs", json.createArrayNode().add(masked)));
                if (source == Source.ACCOUNTING_RUN) {
                    boolean verified = AccountingExecutionEvidenceService.scopeVerified(raw);
                    payload.put("scopeBasis", verified ? "RUN_EXECUTION_SNAPSHOT" : "CURRENT_MONITOR_CONFIGURATION").put("historicalEnvironmentVerified", verified);
                    if (verified) payload.set("recordTimeZone", raw.at("/executionEvidence/snapshot/recordTimeZone"));
                }
            }
            if (json.writeValueAsString(payload).length() > 40_000) {
                String preview = json.writeValueAsString(payload).substring(0, 16_000);
                payload.remove("data");
                payload.put("truncated", true).put("preview", preview);
            }
            mapper.ingest(new AgentInspectionItem(0, stream.id(), after, digest(raw), json.writeValueAsString(payload),
                    "PENDING", null, null, now(), now()));
        }
        boolean completed = rows.size() < PAGE_SIZE || after >= upper;
        mapper.advance(stream.id(), completed ? 0 : after, completed ? 0 : upper, completed ? 1 : 0, now());
    }

    private void deliver(AgentToolContext context, Source source, AgentInspectionItem item, ObjectNode result) {
        JsonNode payload = json.readTree(item.payloadJson());
        String observationId = null;
        if (source != Source.BUG) {
            payload = observations.captureInspection(context, source == Source.SYSTEM_ALERT ? "SYSTEM_ALERT" : "ACCOUNTING", payload);
            observationId = payload.path("observationId").asText();
        }
        mapper.assign(item.id(), context.runId(), observationId, now());
        result.put("state", "AWAITING_ANALYSIS").put("itemId", item.id()).put("sourceId", item.sourceId());
        result.put("sourceVersion", item.contentHash());
        result.put("evidencePointer", source == Source.SYSTEM_ALERT ? "/data/events/0" : source == Source.ACCOUNTING_RUN ? "/data/runs/0" : "");
        result.set("observation", payload);
    }

    private String verifyFinding(AgentToolContext context, AgentInspectionItem item, String caseId, String findingObservationId, String pointer) {
        if (item.observationId() == null) throw new IllegalArgumentException("缺陷线索尚无已定位环境的证据，请先标记待补充并继续定位");
        var original = autonomy.observation(item.observationId());
        var observation = findingObservationId == null ? original : autonomy.observation(findingObservationId);
        var problem = autonomy.find(caseId);
        if (problem == null || observation == null || !context.runId().equals(observation.runId()) || !context.agentId().equals(observation.agentId())
                || !original.businessLineCode().equals(observation.businessLineCode()) || !original.environmentCode().equals(observation.environmentCode())
                || !problem.businessLineCode().equals(observation.businessLineCode())
                || !problem.environmentCode().equals(observation.environmentCode()) || !problem.sourceType().equals(observation.sourceType()))
            throw new IllegalArgumentException("问题与本次巡检证据不匹配");
        if (findingObservationId != null) {
            var run = json.readTree(original.payloadJson()).at("/data/runs/0");
            var detailed = json.readTree(observation.payloadJson()).at("/data/run");
            if (!"ACCOUNTING".equals(original.sourceType()) || !"monitor.accounting.detail".equals(observation.toolCode())
                    || !run.hasNonNull("id") || !run.path("id").equals(detailed.path("id")) || !run.path("configId").equals(detailed.path("configId")))
                throw new IllegalArgumentException("补充规则证据必须来自当前账务运行及同一监测配置");
        } else pointer = "SYSTEM_ALERT".equals(observation.sourceType()) ? "/data/events/0" : "/data/runs/0";
        var extracted = evidence.extract(observation, pointer, problem.category(), null);
        String fingerprint = digest(List.of(observation.businessLineCode(), observation.environmentCode(), observation.sourceType(), extracted.subjectKey(), problem.category()));
        var signal = autonomy.signal(digest(List.of(fingerprint, extracted.sourceEventId(), extracted.sourceVersion())));
        if (signal == null || !caseId.equals(signal.caseId())) throw new IllegalArgumentException("请先用当前快照的记录位置登记问题，不能用其他问题代替分析");
        return pointer;
    }

    private boolean canTake(AgentToolContext context, AgentInspectionItem item) {
        if (item.assignedRunId() == null || context.runId().equals(item.assignedRunId())) return true;
        var prior = agents.findRun(item.assignedRunId());
        return prior != null && prior.ownerToken() == null && prior.externalHandle() == null
                && Set.of("COMPLETED", "FAILED", "CANCELLED").contains(prior.status());
    }

    private String authorize(AgentToolContext context, String tool, Source source, String line, String environment) {
        access.requireExecution(context, tool, source.permissions());
        if (source == Source.BUG) {
            if (environment != null && !environment.isBlank()) throw new IllegalArgumentException("缺陷工作项没有环境字段，请不要传入或推断环境");
            access.requireBusinessLine(context, line);
            return "";
        }
        if (environment == null || environment.isBlank()) throw new IllegalArgumentException("监测来源必须指定实际环境");
        access.requireScope(context, new AgentScope(line, environment));
        return environment;
    }

    private String callKey(AgentToolContext context, String code) {
        var run = agents.lockRun(context.runId());
        runs.requireActive(context.runId(), context.ownerToken());
        if (!context.taskId().equals(run.taskId()) || !context.agentId().equals(run.agentId())) throw new IllegalStateException("巡检身份与运行不符");
        var handle = run.externalHandle() == null ? json.nullNode() : json.readTree(run.externalHandle());
        if ("CLI".equals(handle.path("kind").asText())) handle = handle.path("activeTool");
        if (!"TOOL".equals(handle.path("kind").asText()) || !code.equals(handle.path("tool").asText()) || handle.path("key").asText().isBlank())
            throw new IllegalStateException("巡检进度必须关联当前实际工具调用");
        return handle.path("key").asText();
    }

    private JsonNode replay(AgentToolContext context, String key, String hash) {
        var saved = mapper.call(context.runId(), key);
        if (saved == null) return null;
        if (!saved.requestHash().equals(hash)) throw new IllegalArgumentException("工具调用标识已用于不同巡检请求");
        return json.readTree(saved.resultJson());
    }
    private JsonNode save(AgentToolContext context, String key, String hash, JsonNode result) {
        var safe = data.sanitize(result);
        mapper.insertCall(new AgentInspectionCall(context.runId(), key, hash, json.writeValueAsString(safe), now()));
        return safe;
    }
    private AgentInspectionItem requiredItem(long id) {
        var item = mapper.item(id);
        if (item == null) throw new IllegalArgumentException("巡检记录不存在");
        return item;
    }
    private String streamId(AgentToolContext context, Source source, String line, String env) { return digest(List.of(context.taskId(), source, line, env)); }
    private String digest(Object value) { return AutonomyEvidence.digest(json.writeValueAsString(value)); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
