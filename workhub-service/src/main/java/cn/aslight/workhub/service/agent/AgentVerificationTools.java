package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentVerificationEvidenceReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;

@Configuration(proxyBeanMethods = false)
public class AgentVerificationTools {
    private final AgentVerificationService service;
    private final ObjectMapper json;
    public AgentVerificationTools(AgentVerificationService service, ObjectMapper json) { this.service = service; this.json = json; }

    @Bean AgentTool agentVerificationStartTool() {
        return tool("verification.start", "整组已审核发布实际完成后开始业务复查，绑定准确提交、部署回执和获批标准。部署失败、未完成或旧标准不能开始。", false,
                Map.of("planId", text(36)), List.of("planId"), (context, args) -> service.start(context, args.required("planId")));
    }
    @Bean AgentTool agentVerificationRecordTool() {
        var properties = assessmentProperties(); properties.put("requestKey", text(64)); properties.put("checkId", text(64));
        properties.put("evidence", Map.of("type", "array", "minItems", 1, "maxItems", 10, "items", Map.of("type", "object",
                "properties", Map.of("observationId", text(36), "pointer", text(200)), "required", List.of("observationId", "pointer"), "additionalProperties", false)));
        return tool("verification.record", "Agent 按获批条件判断 PASS/FAIL/INCONCLUSIVE，说明理由及历史数据是否待处理，并引用本次运行真实监测 observationId 和完整记录位置。平台检查来源、范围、数据时间和观察窗口；重复查询旧数据不增加有效样本。账务明细可引用 /data 或 /data/results/序号，状态引用 /data/序号，历史账务引用 /data/runs/序号；其他查询引用 /data。", false,
                properties, List.copyOf(properties.keySet()), (context, args) -> {
                    var references = new ArrayList<AgentVerificationEvidenceReference>();
                    for (var row : args.array("evidence", 10)) {
                        var ref = new AgentToolArguments(row, Set.of("observationId", "pointer"));
                        references.add(new AgentVerificationEvidenceReference(ref.required("observationId"), ref.required("pointer")));
                    }
                    return service.record(context, args.required("verificationId"), args.required("requestKey"), args.required("checkId"),
                            args.required("assessment"), args.content("reason", 4000), args.bool("historicalDataPending"), references);
                });
    }
    @Bean AgentTool agentVerificationFinishTool() {
        var properties = assessmentProperties();
        return tool("verification.finish", "Agent 根据全部获批标准与真实监测证据提交最终业务判断。只有全部样本、范围、窗口、新鲜度约束满足且无待处理历史数据，平台才签发复查凭据；证据不足仍需继续观察，不能据此关闭原问题。", false,
                properties, List.copyOf(properties.keySet()), (context, args) -> service.finish(context, args.required("verificationId"),
                        args.required("assessment"), args.content("reason", 4000), args.bool("historicalDataPending")));
    }
    @Bean AgentTool agentVerificationResolveCaseTool() {
        return tool("verification.resolve_case", "已通过复查后，读取原问题最新 version 和 evidenceVersion，确认全部证据及历史数据已处置，再提交关闭理由。平台重新核对同一候选/方案、持久凭据、新鲜观察窗口及原环境/监测对象；测试复查不能关闭生产问题。保留不可变关闭凭据，不修改关联缺陷工作项。另需 autonomy:case:operate。", false,
                Map.of("verificationId",text(36),"caseVersion",Map.of("type","integer","minimum",0),
                        "evidenceVersion",Map.of("type","integer","minimum",0),"reason",text(4000)),
                List.of("verificationId","caseVersion","evidenceVersion","reason"),
                (context,args) -> service.resolveCase(context,args.required("verificationId"),args.version("caseVersion"),args.version("evidenceVersion"),args.content("reason",4000)));
    }
    @Bean AgentTool agentVerificationStatusTool() {
        return tool("verification.status", "读取已获批标准、Agent 判断、平台约束缺口、有效样本和观察窗口；原始证据与已结束结论不会被覆盖。", true,
                Map.of("verificationId", text(36)), List.of("verificationId"), (context, args) -> service.status(context, args.required("verificationId")));
    }
    @Bean AgentTool agentVerificationWaitTool() {
        return tool("verification.wait", "登记下一次采样等待，随后将运行结束为 WAITING_INPUT。平台到时唤醒原任务，保留样本和预算；不会绕过撤权、取消或未知外部调用。等待不能放宽获批采样间隔，须为取数和调度留出时间。", false,
                Map.of("verificationId", text(36), "seconds", Map.of("type", "integer", "minimum", 1, "maximum", 86400)),
                List.of("verificationId", "seconds"), (context, args) -> service.waitForNext(context, args.required("verificationId"), args.id("seconds")));
    }
    private LinkedHashMap<String, Object> assessmentProperties() {
        return new LinkedHashMap<>(Map.of("verificationId", text(36), "assessment", Map.of("type", "string", "enum", List.of("PASS", "FAIL", "INCONCLUSIVE")),
                "reason", text(4000), "historicalDataPending", Map.of("type", "boolean")));
    }
    private AgentTool tool(String code, String description, boolean readOnly, Map<String, Object> properties, List<String> required,
                           BiFunction<AgentToolContext, AgentToolArguments, JsonNode> action) {
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return description; }
            public JsonNode inputSchema() { return json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false)); }
            public Set<String> permissions() { return Set.of("agent:code:deliver"); }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                try { return action.apply(context, new AgentToolArguments(arguments, properties.keySet())); }
                catch (IllegalArgumentException | AccessDeniedException ex) { throw new Rejected(ex.getMessage()); }
            }
        };
    }
    private Map<String, Object> text(int length) { return Map.of("type", "string", "minLength", 1, "maxLength", length); }
}
