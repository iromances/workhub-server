package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentReportMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.agent.AgentReportRows.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class AgentReportService {
    private final AgentReportMapper mapper;
    private final ObjectMapper json;
    public AgentReportService(AgentReportMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }

    /** Must participate in the locked run-completion transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AgentRunEntity run, JsonNode result, String originalEventId) {
        AgentReportContract.validate(result);
        if (!AgentReportContract.isV2(result)) return;
        var report = result.path("report");
        String hash = digest(canonical(result));
        var existing = mapper.find(run.id());
        if (existing != null) {
            if (!hash.equals(existing.payloadHash())) throw new IllegalStateException("本轮报告已保存，拒绝覆盖不同结果");
            return;
        }
        String id = UUID.randomUUID().toString();
        mapper.insertReport(new Report(id, run.id(), run.taskId(), run.agentId(), 2,
                report.path("reportType").asText(), report.path("title").asText(), report.path("conclusion").asText(), report.path("assessment").asText(),
                json.writeValueAsString(report.path("scope")), json.writeValueAsString(report.path("limitations")), originalEventId, hash, LocalDateTime.now(ZoneOffset.UTC)));
        int ordinal = 0;
        for (var row : report.path("checks")) mapper.insertCheck(row(row, id, ordinal++, Check.class));
        ordinal = 0;
        for (var row : report.path("findings")) mapper.insertFinding(row(row, id, ordinal++, Finding.class));
        ordinal = 0;
        for (var row : report.path("evidence")) mapper.insertEvidence(row(row, id, ordinal++, Evidence.class));
        ordinal = 0;
        for (var row : report.path("actions")) mapper.insertAction(row(row, id, ordinal++, Action.class));
    }
    private <T> T row(JsonNode input, String id, int ordinal, Class<T> type) {
        ObjectNode node = (ObjectNode) input.deepCopy();
        node.put("id", UUID.randomUUID().toString()).put("reportId", id).put("ordinal", ordinal);
        return json.treeToValue(node, type);
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public JsonNode read(String runId) {
        var stored = mapper.find(runId);
        if (stored == null) return null;
        var report = json.createObjectNode();
        report.put("reportType", stored.reportType()).put("title", stored.title()).put("conclusion", stored.conclusion()).put("assessment", stored.assessment());
        report.set("scope", json.readTree(stored.scopeJson())); report.set("limitations", json.readTree(stored.limitationsJson()));
        report.set("checks", items(mapper.listCheck(stored.id())));
        report.set("findings", items(mapper.listFinding(stored.id())));
        report.set("evidence", items(mapper.listEvidence(stored.id())));
        report.set("actions", items(mapper.listAction(stored.id())));
        return report;
    }
    private JsonNode items(List<?> rows) {
        var items = json.createArrayNode();
        for (var row : rows) {
            ObjectNode node = json.valueToTree(row);
            for (String field : List.of("id", "reportId", "ordinal", "issueId")) node.remove(field);
            items.add(node);
        }
        return items;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteRun(String id) {
        return mapper.deleteCheckByRun(id)+mapper.deleteFindingByRun(id)+mapper.deleteEvidenceByRun(id)+mapper.deleteActionByRun(id)+mapper.deleteReportByRun(id);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteTask(String id) {
        return mapper.deleteCheckByTask(id)+mapper.deleteFindingByTask(id)+mapper.deleteEvidenceByTask(id)+mapper.deleteActionByTask(id)+mapper.deleteReportByTask(id);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteAgent(String id) {
        return mapper.deleteCheckByAgent(id)+mapper.deleteFindingByAgent(id)+mapper.deleteEvidenceByAgent(id)+mapper.deleteActionByAgent(id)+mapper.deleteReportByAgent(id);
    }
    private String canonical(JsonNode value) {
        if (value.isObject()) {
            var sorted = new TreeMap<String, JsonNode>(); value.properties().forEach(e -> sorted.put(e.getKey(), e.getValue()));
            var node = json.createObjectNode(); sorted.forEach((k,v) -> node.set(k, json.readTree(canonical(v))));
            return json.writeValueAsString(node);
        }
        if (value.isArray()) { var array = json.createArrayNode(); value.forEach(v -> array.add(json.readTree(canonical(v)))); return json.writeValueAsString(array); }
        return json.writeValueAsString(value);
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
