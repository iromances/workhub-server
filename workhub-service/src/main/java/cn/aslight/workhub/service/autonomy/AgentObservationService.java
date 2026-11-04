package cn.aslight.workhub.service.autonomy;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.model.autonomy.AgentObservationEntity;
import cn.aslight.workhub.service.agent.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.*;
import java.util.UUID;

/** 保存企业查询实际返回的事实；AI 只取得引用，不能自行写入 Observation。 */
@Service
public class AgentObservationService {
    private final AutonomyMapper mapper;
    private final AgentMapper agents;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final ObjectMapper json;
    private final AgentToolData data;

    public AgentObservationService(AutonomyMapper mapper, AgentMapper agents, AgentRunStore runs,
                                    AgentAccessService access, ObjectMapper json, AgentToolData data) {
        this.mapper = mapper; this.agents = agents; this.runs = runs;
        this.access = access; this.json = json; this.data = data;
    }

    @Transactional
    public JsonNode capture(AgentToolContext context, String toolCode, Object value) {
        return capture(context, toolCode, value, null);
    }

    /** 仅持久化巡检器可将已保存的真实来源快照重新交付给本次运行。 */
    @Transactional
    public JsonNode captureInspection(AgentToolContext context, String source, Object value) {
        if (!java.util.Set.of("SYSTEM_ALERT", "ACCOUNTING").contains(source))
            throw new IllegalArgumentException("该巡检来源尚不能生成环境问题证据");
        return capture(context, "inspection.next", value, source);
    }

    private JsonNode capture(AgentToolContext context, String toolCode, Object value, String inspectionSource) {
        var run = agents.lockRun(context.runId());
        runs.requireActive(context.runId(), context.ownerToken());
        var handle = run.externalHandle() == null ? json.nullNode() : json.readTree(run.externalHandle());
        if ("CLI".equals(handle.path("kind").asText())) handle = handle.path("activeTool");
        if (!"TOOL".equals(handle.path("kind").asText()) || !toolCode.equals(handle.path("tool").asText())
                || handle.path("key").asText().isBlank()) throw new IllegalStateException("巡检证据必须关联当前实际工具调用");
        String key = handle.path("key").asText();
        var raw = json.valueToTree(value);
        var scope = json.treeToValue(raw.path("scope"), AgentScope.class);
        access.requireScope(context, scope);
        String source = inspectionSource == null ? sourceType(toolCode, scope.businessLineCode()) : inspectionSource;
        var previous = mapper.observationByCall(context.runId(), key);
        if (previous != null) {
            if (!previous.toolCode().equals(toolCode) || !previous.sourceType().equals(source) || !previous.businessLineCode().equals(scope.businessLineCode())
                    || !previous.environmentCode().equals(scope.environmentCode())) throw new IllegalStateException("工具调用已绑定其他来源证据");
            return result(previous);
        }
        var payload = (ObjectNode) data.sanitize(value);
        String masked = json.writeValueAsString(payload);
        if (!payload.path("truncated").asBoolean() && masked.length() > 40_000) {
            payload = json.createObjectNode().put("truncated", true).put("preview", masked.substring(0, 16_000))
                    .put("message", "监测记录超过证据单页上限，请缩小查询范围或分页。");
        }
        if (payload.path("truncated").asBoolean()) {
            payload.set("scope", json.valueToTree(scope));
            payload.set("observedAt", raw.path("observedAt"));
            payload.set("recordTimeZone", raw.path("recordTimeZone"));
            payload.put("coverage", "TRUNCATED");
        }
        String encoded = json.writeValueAsString(payload);
        var observation = new AgentObservationEntity(UUID.randomUUID().toString(), context.runId(), context.agentId(), key,
                toolCode, source, scope.businessLineCode(), scope.environmentCode(), encoded, AutonomyEvidence.digest(encoded),
                LocalDateTime.ofInstant(Instant.parse(raw.path("observedAt").asText()), ZoneOffset.UTC));
        mapper.insertObservation(observation);
        return result(observation);
    }

    private JsonNode result(AgentObservationEntity observation) {
        var value = (ObjectNode) json.readTree(observation.payloadJson());
        value.put("observationId", observation.id());
        value.put("sourceType", observation.sourceType());
        value.put("evidenceUsage", "问题登记需引用 observationId 和 data 下具体记录的 JSON Pointer；截断预览不可作完整证据。");
        return value;
    }

    public static String sourceType(String toolCode, String line) {
        return switch (toolCode) {
            case "monitor.business.status", "monitor.business.detail" -> "BUSINESS_MONITOR";
            case "monitor.accounting.status", "monitor.accounting.detail", "monitor.accounting.runs" -> "ACCOUNTING";
            case "monitor.system.alerts" -> "SYSTEM_ALERT";
            case "workhub.logs" -> "BL000012".equals(line) ? "SELF_LOG" : "INSPECTION";
            default -> throw new IllegalArgumentException("该工具尚未接入巡检证据登记");
        };
    }
}
