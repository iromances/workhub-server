package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentEventEntity;
import cn.aslight.workhub.service.ai.AiAgentApiRequest;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiPromptTemplateRenderer;
import cn.aslight.workhub.service.ai.AiUseCaseConfigService;
import cn.aslight.workhub.service.ai.AiUseCaseDefinitions;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 现有模型 API 的步进执行：依据职责描述、任务与轨迹规划，无工具调用。 */
@Component
public class AgentApiExecutionAdapter implements AgentExecutionAdapter {
    private static final int MAX_CONTEXT_CHARS = 180_000;
    private static final int MAX_FORMAT_ERRORS = 3;
    private final AgentRunStore store;
    private final AgentToolData data;
    private final AiGatewayClient gateway;
    private final AiUseCaseConfigService aiUseCaseConfigService;
    private final AiPromptTemplateRenderer promptRenderer;
    private final AgentPlatformGuide platformGuide;
    private final ObjectMapper json;

    public AgentApiExecutionAdapter(AgentRunStore store, AgentToolData data, AiGatewayClient gateway,
                                    AiUseCaseConfigService aiUseCaseConfigService, AiPromptTemplateRenderer promptRenderer,
                                    AgentPlatformGuide platformGuide, ObjectMapper json) {
        this.store = store;
        this.data = data;
        this.gateway = gateway;
        this.aiUseCaseConfigService = aiUseCaseConfigService;
        this.promptRenderer = promptRenderer;
        this.platformGuide = platformGuide;
        this.json = json;
    }

    @Override public String channelType() { return "API"; }

    @Override
    public void execute(String runId, String owner) {
        while (true) {
            store.requireActive(runId, owner);
            var run = store.get(runId);
            int step = run.stepCount();
            var response = step == 0 ? null : store.event(runId, "step:" + step + ":response");
            var actionDone = step == 0 ? null : store.event(runId, "step:" + step + ":handled");
            AgentApiDecision decision;
            if (response != null && actionDone == null) {
                var payload = json.readTree(response.payloadJson());
                if (!payload.path("succeeded").asBoolean()) {
                    store.finish(runId, owner, "FAILED", null, payload.path("error").asText("AI 调用失败"));
                    return;
                }
                if (!payload.hasNonNull("decision")) {
                    store.record(runId, owner, "step:" + step + ":handled", "FORMAT_ERROR", Map.of("message", payload.path("formatError").asText()));
                    continue;
                }
                decision = json.treeToValue(payload.get("decision"), AgentApiDecision.class);
            } else {
                if (store.events(runId).stream().filter(event -> "FORMAT_ERROR".equals(event.kind())).count() >= MAX_FORMAT_ERRORS) {
                    store.finish(runId, owner, "FAILED", null, "AI 行动格式纠正次数已达上限");
                    return;
                }
                var context = store.context(run);
                var promptEvents = store.events(runId);
                String prompt;
                try {
                    prompt = prompt(context, promptEvents);
                } catch (RuntimeException ex) {
                    String reason = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
                    store.finish(runId, owner, "FAILED", null, "AI 任务配置不可用 " + AiUseCaseDefinitions.AGENT_API_STEP + "：" + reason);
                    return;
                }
                if (prompt.length() > MAX_CONTEXT_CHARS) {
                    store.finish(runId, owner, "WAITING_INPUT", null, "任务轨迹超过单次上下文上限；证据已保留，请拆分任务或调整范围");
                    return;
                }
                int timeout = Math.toIntExact(Math.max(1, Math.min(300, context.configuration().maxRunSeconds() - run.elapsedMillis() / 1000)));
                try {
                    step = store.beginModel(runId, owner, Map.of("providerConfigId", context.configuration().providerConfigId(),
                            "model", context.configuration().model(), "timeoutSeconds", timeout), promptEvents.isEmpty() ? 0L : promptEvents.getLast().ordinal());
                } catch (AgentRunStore.InputChanged ex) { continue; }
                var binding = json.readValue(run.providerBindingJson(), cn.aslight.workhub.model.agent.AgentProviderBinding.class);
                var answer = gateway.executeAgentApi(new AiAgentApiRequest(context.configuration().providerConfigId(),
                        context.configuration().model(), prompt, timeout, binding.configurationVersion(), binding.apiProtocol()));
                Map<String, Object> receipt = new LinkedHashMap<>();
                receipt.put("succeeded", answer.succeeded());
                receipt.put("model", answer.model());
                receipt.put("provider", answer.providerCode());
                receipt.put("durationMillis", answer.durationMs());
                if (!answer.succeeded()) {
                    receipt.put("error", answer.failureSummary());
                } else {
                    try {
                        if (!context.configuration().model().equals(answer.model())) throw new IllegalArgumentException("实际模型与本次任务快照不符");
                        store.record(runId, owner, "step:" + step + ":raw", "API_RAW_RESULT", Map.of("text", answer.output()));
                        receipt.put("decision", AgentApiDecision.parse(answer.output(), json));
                    } catch (IllegalArgumentException | tools.jackson.core.JacksonException ex) {
                        receipt.put("formatError", "请严格按协议返回单个行动对象：" + ex.getClass().getSimpleName());
                    }
                }
                store.endModel(runId, owner, step, receipt);
                // 回读已持久化回执再行动；重启会从相同位置继续。
                continue;
            }
            String modelRequestKey = "step:" + step + ":request";
            if (store.hasNewInputSince(runId, modelRequestKey)) {
                store.record(runId, owner, "step:" + step + ":handled", "INPUT_CHANGED", Map.of("message", "已收到新指令，旧行动未发出，请重新规划"));
                continue;
            }
            store.record(runId, owner, "step:" + step + ":plan", "PLAN", Map.of("summary", decision.plan()));
            store.requireActive(runId, owner);
            if ("RESULT".equals(decision.type()) || "NEEDS_INPUT".equals(decision.type())) {
                try { store.finishDecision(runId, owner, modelRequestKey, "RESULT".equals(decision.type()) ? "COMPLETED" : "WAITING_INPUT", decision.summary(), decision.structuredResult()); }
                catch (AgentRunStore.InputChanged ex) {
                    store.record(runId, owner, "step:" + step + ":handled", "INPUT_CHANGED", Map.of("message", "完成前收到新指令，请继续规划"));
                    continue;
                }
                return;
            }
            store.record(runId, owner, "step:" + step + ":handled", "STEP_COMPLETED", Map.of("type", decision.type()));
        }
    }

    private String prompt(AgentToolContext context, List<AgentEventEntity> events) {
        var config = aiUseCaseConfigService.requireEnabledByCode(AiUseCaseDefinitions.AGENT_API_STEP);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("agentInstructions", json.writeValueAsString(data.sanitize(context.configuration().instructions())));
        variables.put("taskSpecification", json.writeValueAsString(data.sanitize(context.specification())));
        variables.put("dataWindow", json.writeValueAsString(data.sanitize(store.dataWindow(context))));
        variables.put("platformGuide", platformGuide.guide(context));
        variables.put("trajectory", json.writeValueAsString(events.stream()
                .map(event -> Map.of("ordinal", event.ordinal(), "kind", event.kind(), "payload", json.readTree(event.payloadJson()))).toList()));
        return promptRenderer.render(config.getPromptTemplate(), variables, AiUseCaseDefinitions.require(AiUseCaseDefinitions.AGENT_API_STEP))
                + "\n最终报告按以下唯一结构返回，不再使用旧 plan/summary 格式；只返回 JSON，不加说明或围栏。"
                + AgentReportContract.PRESENTATION_GUIDE + "\n" + AgentReportContract.SCHEMA;
    }
}
