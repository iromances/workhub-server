package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;

@Configuration(proxyBeanMethods = false)
public class AgentReleaseTools {
    private final AgentReleasePlanService service;
    private final AgentChangeService changes;
    private final AgentAccessService access;
    private final ObjectMapper json;
    public AgentReleaseTools(AgentReleasePlanService service, AgentChangeService changes, AgentAccessService access, ObjectMapper json) {
        this.service = service; this.changes = changes; this.access = access; this.json = json;
    }
    @Bean AgentTool agentReleaseBindingsTool() {
        return tool("release.bindings", "查看候选清单范围内管理员配置的 Jenkins 发布绑定。只返回绑定标识、环境、阶段和仓库；不能新增地址、凭据或任意参数。", true,
                Map.of("changeSetId", string(36), "page", Map.of("type", "integer", "minimum", 1, "maximum", 25)), List.of("changeSetId"), (context, args) -> {
                    changes.detail(context, args.required("changeSetId"));
                    var result = service.bindings(args.required("changeSetId"), context.requestedBy());
                    int start = Math.min(result.size(), (args.bounded("page", 1, 1, 25) - 1) * 20);
                    return new AgentPage<>(result.size(), result.subList(start, Math.min(result.size(), start + 20)));
                });
    }
    @Bean AgentTool agentReleasePrepareTool() {
        return tool("release.prepare", "准备整组 Jenkins 发布审核材料，覆盖候选清单全部仓库。明确多个 Job 的先后依赖；共享部署资源必须有顺序。缺少实测时只能形成未就绪草案。不能批准或触发发布。成功后等待人工审核，证据更新需用新 requestKey 重建计划。", false,
                Map.of("requestKey", string(64), "changeSetId", string(36), "stage", Map.of("type", "string", "enum", List.of("TEST_DEPLOY", "PROD_DEPLOY")),
                        "bindingIds", Map.of("type", "array", "minItems", 1, "maxItems", 20, "items", string(64)),
                        "dependencies", Map.of("type", "array", "maxItems", 100, "items", Map.of("type", "object", "properties", Map.of("bindingId", string(64), "dependsOn", string(64)),
                                "required", List.of("bindingId", "dependsOn"), "additionalProperties", false))),
                List.of("requestKey", "changeSetId", "stage", "bindingIds", "dependencies"), (context, args) -> {
                    changes.detail(context, args.required("changeSetId"));
                    var ids = new ArrayList<String>();
                    for (var id : args.array("bindingIds", 20)) { if (!id.isString()) throw new IllegalArgumentException("绑定标识必须为字符串"); ids.add(id.asText()); }
                    var edges = new ArrayList<AgentReleasePlanRequest.Dependency>();
                    for (var edge : args.array("dependencies", 100)) {
                        var item = new AgentToolArguments(edge, Set.of("bindingId", "dependsOn"));
                        edges.add(new AgentReleasePlanRequest.Dependency(item.required("bindingId"), item.required("dependsOn")));
                    }
                    return compact(service.prepare(new AgentReleasePlanRequest(args.required("requestKey"), args.required("changeSetId"), args.required("stage"), ids, edges), context.requestedBy()));
                });
    }
    @Bean AgentTool agentReleaseStatusTool() {
        return tool("release.status", "读取本次运行的整组审核结论、人工意见和各个 Jenkins 请求状态。UNKNOWN 必须核对原请求；构建成功和部署回执都不等于业务复查通过。", true,
                Map.of("planId", string(36), "changeSetId", string(36)), List.of("planId", "changeSetId"), (context, args) -> {
                    changes.detail(context, args.required("changeSetId"));
                    var result = service.detail(args.required("planId"), context.requestedBy());
                    if (!context.runId().equals(result.get("runId")) || !args.required("changeSetId").equals(result.get("changeSetId"))) throw new AccessDeniedException("发布计划不属于本次运行及清单");
                    return compact(result);
                });
    }
    private Map<String, Object> compact(Map<String, Object> detail) {
        var result = new LinkedHashMap<>(detail); result.remove("snapshot");
        var snapshot = (AgentReleasePlanSnapshot) detail.get("snapshot");
        result.put("steps", snapshot.steps().stream().map(step -> Map.of("bindingId", step.bindingId(), "requestId", step.requestId(),
                "scope", step.binding().binding().scope(), "dependsOn", step.dependsOn(), "repositories", step.binding().binding().commitParameters().keySet())).toList());
        return result;
    }
    private AgentTool tool(String code, String description, boolean readOnly, Map<String, Object> properties, List<String> required,
                           BiFunction<AgentToolContext, AgentToolArguments, Object> action) {
        var schema = json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return description; }
            public JsonNode inputSchema() { return schema; }
            public Set<String> permissions() { return Set.of("agent:code:deliver"); }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode args) {
                try { access.requireExecution(context, this); return action.apply(context, new AgentToolArguments(args, properties.keySet())); }
                catch (IllegalArgumentException | AccessDeniedException | AgentCodePolicy.Unavailable ex) { throw new Rejected(ex.getMessage()); }
            }
        };
    }
    private Map<String, Object> string(int max) { return Map.of("type", "string", "maxLength", max); }
}
