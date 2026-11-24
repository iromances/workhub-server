package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentIssueMapper;
import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.AgentIssueEntity;
import cn.aslight.workhub.model.agent.AgentIssueResponse;
import cn.aslight.workhub.model.agent.AgentIssueReviewRequest;
import cn.aslight.workhub.model.agent.AgentRunEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REQ-4d361388：巡检问题工单域。跨轮聚合（task+signature 唯一）：复发更新观测不重建；
 * FIXED 后再出现自动重开；状态机 OPEN→ANALYZED→FIXING→PENDING_REVIEW→TESTING→FIXED（拒绝回开放）。
 */
@Service
public class AgentIssueService {
    public static final Set<String> DOMAINS = Set.of("SYSTEM_ALERT", "BUSINESS_MONITOR", "ACCOUNTING");
    public static final Set<String> TERMINAL = Set.of("OPEN", "ANALYZED", "FIXING", "PENDING_REVIEW", "TESTING", "FIXED", "REJECTED", "CLOSED");

    private cn.aslight.workhub.dao.agent.AgentReportMapper reportMapper;
    @org.springframework.beans.factory.annotation.Autowired
    public void setReportMapper(cn.aslight.workhub.dao.agent.AgentReportMapper mapper) { this.reportMapper = mapper; }
    private final AgentIssueMapper issues;
    private final AgentMapper mapper;
    private final ObjectMapper json;

    public AgentIssueService(AgentIssueMapper issues, AgentMapper mapper, ObjectMapper json) {
        this.issues = issues;
        this.mapper = mapper;
        this.json = json;
    }

    /** 巡检运行完成后摄取结构化问题：按去重键聚合；FIXED 复现自动重开。 */
    @Transactional
    public void ingest(String runId) {
        var run = mapper.lockRun(runId);
        if (run == null || !"COMPLETED".equals(run.status())) return;
        if (mapper.findEventByKey(runId, "report:issues-ingested") != null) return;
        JsonNode problems = extractProblems(run.resultText());
        if (problems == null || !problems.isArray()) problems = cliResultProblems(runId);
        if (problems == null || !problems.isArray()) return;
        var context = mapper.findTask(run.taskId());
        if (context == null) return;
        var seen = new ArrayList<String>();
        for (JsonNode problem : problems) {
            ingestOne(run, context.agentId(), problem, seen);
            if (reportMapper != null && seen.contains(text(problem, "signature"))) {
                var issue = issues.findBySignature(run.taskId(), text(problem, "signature"));
                if (issue != null) reportMapper.linkIssue(run.id(), issue.signature(), issue.id());
            }
        }
        if (completeCoverage(runId)) markUnseen(run.taskId(), run.id(), seen);
        mapper.insertEvent(new cn.aslight.workhub.model.agent.AgentEventEntity(UUID.randomUUID().toString(), runId,
                "report:issues-ingested", mapper.nextEventOrdinal(runId), "REPORT_ISSUES_INGESTED", "{}", "agent-runtime", LocalDateTime.now(ZoneOffset.UTC)));
    }

    private boolean completeCoverage(String runId) {
        for (var event : mapper.listEvents(runId).reversed()) {
            if (!Set.of("CLI_RESULT", "STRUCTURED_RESULT").contains(event.kind())) continue;
            var result = json.readTree(event.payloadJson()).path("result");
            if (!AgentReportContract.isV2(result)) continue;
            var report = result.path("report");
            if ("INCONCLUSIVE".equals(report.path("assessment").asText()) || !report.path("limitations").isEmpty() || report.path("checks").isEmpty()) return false;
            for (var check : report.path("checks")) if (!Set.of("PASS", "FAIL", "NOT_APPLICABLE").contains(check.path("outcome").asText())) return false;
            return true;
        }
        return true;
    }

    /** 结果中的结构化问题块：兼容 CLI 最终输出 {"type","summary","problems":[...]}。 */
    private JsonNode extractProblems(String resultText) {
        if (resultText == null || resultText.isBlank()) return null;
        try {
            JsonNode root = json.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(resultText.strip());
            return root.path("problems");
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 原生 CLI 完成时 result_text 只落 summary，完整结果（含 problems）留在最近的 CLI_RESULT 事件里；
     * 从后往前找最近一次带 problems 数组的结果，多轮 CLI 重规划时对应最终结果。
     */
    private JsonNode cliResultProblems(String runId) {
        for (var event : mapper.listEvents(runId).reversed()) {
            if (!Set.of("CLI_RESULT", "STRUCTURED_RESULT").contains(event.kind()) || event.payloadJson() == null) continue;
            try {
                JsonNode problems = AgentReportContract.problems(json.readTree(event.payloadJson()).path("result"));
                if (problems.isArray()) return problems;
            } catch (RuntimeException ignored) { /* 坏事件不阻断，继续找上一条 */ }
        }
        return null;
    }

    private void ingestOne(AgentRunEntity run, String agentId, JsonNode problem, List<String> seen) {
        String domain = text(problem, "domain");
        String signature = text(problem, "signature");
        String title = text(problem, "title");
        String severity = text(problem, "severity");
        if (!DOMAINS.contains(domain) || signature.isBlank() || title.isBlank()
                || !Set.of("P0", "P1", "P2").contains(severity)) return;
        signature = signature.length() > 200 ? signature.substring(0, 200) : signature;
        title = title.length() > 500 ? title.substring(0, 500) : title;
        seen.add(signature);
        var existing = issues.findBySignature(run.taskId(), signature);
        LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.now(), ZoneOffset.UTC);
        if (existing == null) {
            var entity = new AgentIssueEntity(UUID.randomUUID().toString(), run.taskId(), agentId,
                    scopeLine(run), scopeEnvironment(run), domain, signature, title, severity, "ANALYZED",
                    json.writeValueAsString(analysisOf(problem)), json.writeValueAsString(evidenceOf(problem, run)),
                    null, null, null, run.id(), run.id(), now, now, 1, 0, 0, 0, now, now);
            issues.insert(entity);
            mapper.insertAudit(UUID.randomUUID().toString(), "ISSUE", entity.id(), "CREATE", "agent-runtime",
                    json.writeValueAsString(java.util.Map.of("title", title, "severity", severity, "domain", domain)), now);
            return;
        }
        if (run.id().equals(existing.lastRunId())) return;
        // 复发：更新观测；FIXED/TESTING 后再出现自动重开（修复无效信号）。
        boolean reopen = Set.of("FIXED", "CLOSED").contains(existing.status());
        var updated = new AgentIssueEntity(existing.id(), existing.taskId(), existing.agentId(),
                existing.businessLineCode(), existing.environmentCode(), existing.domain(), existing.signature(),
                title, severity, reopen ? "OPEN" : existing.status(),
                hasAnalysis(problem) ? json.writeValueAsString(analysisOf(problem)) : existing.analysisJson(),
                json.writeValueAsString(evidenceOf(problem, run)), existing.fixJson(), existing.reviewJson(),
                existing.deployJson(), existing.firstRunId(), run.id(), existing.firstSeenAt(), now,
                existing.occurrenceCount() + 1, 0, existing.reopenCount() + (reopen ? 1 : 0), existing.version(),
                existing.createdAt(), now);
        if (issues.updateObservation(updated) != 1) throw new IllegalStateException("工单观测更新冲突: " + existing.id());
        if (reopen) {
            mapper.insertAudit(UUID.randomUUID().toString(), "ISSUE", existing.id(), "REOPEN", "agent-runtime",
                    json.writeValueAsString(java.util.Map.of("reason", "已修复问题再次出现", "reopenCount", existing.reopenCount() + 1)), now);
        }
    }

    /** 本轮未出现且仍开放的工单：累计未复现轮数（不自动关闭）。 */
    private void markUnseen(String taskId, String runId, List<String> seen) {
        for (var issue : issues.list(null, null, null, null, null, null, 500)) {
            if (!taskId.equals(issue.taskId()) || !Set.of("OPEN", "ANALYZED", "REJECTED").contains(issue.status())) continue;
            if (seen.contains(issue.signature()) || runId.equals(issue.lastRunId())) continue;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            var updated = new AgentIssueEntity(issue.id(), issue.taskId(), issue.agentId(),
                    issue.businessLineCode(), issue.environmentCode(), issue.domain(), issue.signature(),
                    issue.title(), issue.severity(), issue.status(), issue.analysisJson(), issue.evidenceJson(),
                    issue.fixJson(), issue.reviewJson(), issue.deployJson(), issue.firstRunId(), issue.lastRunId(),
                    issue.firstSeenAt(), issue.lastSeenAt(), issue.occurrenceCount(), issue.unseenRounds() + 1,
                    issue.reopenCount(), issue.version(), issue.createdAt(), now);
            try { issues.updateObservation(updated); } catch (RuntimeException ignored) { }
        }
    }

    public AgentIssueResponse detail(String id) {
        var issue = issues.find(id);
        if (issue == null) throw new IllegalArgumentException("工单不存在");
        return response(issue);
    }

    public List<AgentIssueResponse> list(String status, String domain, String severity,
                                         String businessLineCode, String agentId, String runId, int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        return issues.list(status, domain, severity, businessLineCode, agentId, runId, size)
                .stream().map(this::response).toList();
    }

    public AgentIssueResponse response(AgentIssueEntity entity) {
        return new AgentIssueResponse(entity.id(), entity.taskId(), entity.agentId(), entity.businessLineCode(),
                entity.environmentCode(), entity.domain(), entity.signature(), entity.title(), entity.severity(),
                entity.status(), parse(entity.analysisJson(), AgentIssueResponse.Analysis.class),
                parse(entity.evidenceJson(), AgentIssueResponse.Evidence.class),
                parse(entity.fixJson(), AgentIssueResponse.Fix.class),
                parse(entity.reviewJson(), AgentIssueResponse.Review.class),
                parse(entity.deployJson(), AgentIssueResponse.Deploy.class),
                entity.firstRunId(), entity.lastRunId(), entity.firstSeenAt(), entity.lastSeenAt(),
                entity.occurrenceCount(), entity.unseenRounds(), entity.reopenCount(), entity.version(),
                entity.createdAt(), entity.updatedAt());
    }

    private <T> T parse(String value, Class<T> type) {
        if (value == null || value.isBlank()) return null;
        try { return json.readValue(value, type); } catch (RuntimeException ex) { return null; }
    }

    private AgentIssueResponse.Analysis analysisOf(JsonNode problem) {
        JsonNode node = problem.path("analysis");
        List<AgentIssueResponse.RepoRef> repos = new ArrayList<>();
        for (JsonNode repo : node.path("repos")) {
            repos.add(new AgentIssueResponse.RepoRef(text(repo, "system"), text(repo, "repository"), text(repo, "path")));
        }
        return new AgentIssueResponse.Analysis(text(node, "codeLocation"), text(node, "repro"),
                text(node, "rootCause"), text(node, "impactVerification"), text(node, "fixDirection"), List.copyOf(repos));
    }

    private boolean hasAnalysis(JsonNode problem) {
        JsonNode node = problem.path("analysis");
        return !text(node, "rootCause").isBlank() || !text(node, "codeLocation").isBlank();
    }

    private AgentIssueResponse.Evidence evidenceOf(JsonNode problem, AgentRunEntity run) {
        return new AgentIssueResponse.Evidence(text(problem, "evidence"), List.of(run.id()), text(problem, "window"));
    }

    private String scopeLine(AgentRunEntity run) {
        return run.agentId() != null ? lineOf(run) : "";
    }

    private String lineOf(AgentRunEntity run) {
        try {
            var task = mapper.findTask(run.taskId());
            var spec = json.readValue(task.specificationJson(), cn.aslight.workhub.model.agent.AgentTaskSpecification.class);
            return spec.scopes().isEmpty() ? "" : spec.scopes().getFirst().businessLineCode();
        } catch (RuntimeException ex) { return ""; }
    }

    private String scopeEnvironment(AgentRunEntity run) {
        try {
            var task = mapper.findTask(run.taskId());
            var spec = json.readValue(task.specificationJson(), cn.aslight.workhub.model.agent.AgentTaskSpecification.class);
            return spec.scopes().isEmpty() ? "" : spec.scopes().getFirst().environmentCode();
        } catch (RuntimeException ex) { return ""; }
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() ? value.asText() : "";
    }
}
