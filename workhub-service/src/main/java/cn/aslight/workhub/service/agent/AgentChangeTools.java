package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentChangePlan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;

@Configuration(proxyBeanMethods = false)
public class AgentChangeTools {
    private final AgentChangeService service;
    private final AgentAccessService access;
    private final ObjectMapper json;
    public AgentChangeTools(AgentChangeService service, AgentAccessService access, ObjectMapper json) {
        this.service = service; this.access = access; this.json = json;
    }

    @Bean AgentTool agentChangePrepareTool() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("requestKey", string(64)); properties.put("caseId", string(36));
        for (String name : List.of("summary", "impact", "acceptance", "rollback")) properties.put(name, string(4000));
        properties.put("dependencies", Map.of("type", "array", "maxItems", 100, "items", Map.of("type", "object", "properties",
                Map.of("repositoryId", string(64), "dependsOn", string(64)), "required", List.of("repositoryId", "dependsOn"), "additionalProperties", false)));
        return tool("change.prepare", "固定本次运行全部仓库的候选代码、真实差异、影响/验收/回退和无环依赖顺序；领取了巡检问题时必须提供 caseId。请求重试沿用原清单，代码更新需新 requestKey。候选清单尚不等于实测、远端分支或发布审核通过。", false,
                properties, List.of("requestKey", "summary", "impact", "acceptance", "rollback", "dependencies"), (context, args) -> {
                    var dependencies = new ArrayList<AgentChangePlan.Dependency>();
                    for (var item : args.array("dependencies", 100)) {
                        var edge = new AgentToolArguments(item, Set.of("repositoryId", "dependsOn"));
                        dependencies.add(new AgentChangePlan.Dependency(edge.required("repositoryId"), edge.required("dependsOn")));
                    }
                    return AgentChangeService.summary(service.prepare(context, args.required("requestKey"), args.optional("caseId"), new AgentChangePlan(
                            args.content("summary", 4000), args.content("impact", 4000), args.content("acceptance", 4000), args.content("rollback", 4000), dependencies)));
                });
    }
    @Bean AgentTool agentChangeListTool() {
        return tool("change.list", "列出本次运行各版候选交付清单及当前未就绪原因，保留旧版本而不覆盖。", true,
                Map.of("page", Map.of("type", "integer", "minimum", 1, "maximum", 5)), List.of(),
                (context, args) -> service.list(context, args.bounded("page", 1, 1, 5)));
    }
    @Bean AgentTool agentChangeDetailTool() {
        return tool("change.detail", "SUMMARY 读取清单摘要，PLAN 读取完整交付说明，REPOSITORY 配合 repositoryId 读取确切 SHA、实际差异预览及前20条路径。预览截断需用 code.read/diff 继续调查，不视为已实测或已交付。", true,
                Map.of("changeSetId", string(36), "repositoryId", string(64), "section", Map.of("type", "string", "enum", List.of("SUMMARY", "PLAN", "REPOSITORY"))), List.of("changeSetId"), (context, args) -> {
                    var value = service.detail(context, args.required("changeSetId"));
                    String section = args.optional("section");
                    if (section == null) section = "SUMMARY";
                    if (!Set.of("SUMMARY", "PLAN", "REPOSITORY").contains(section)) throw new IllegalArgumentException("清单详情分区无效");
                    String repository = args.optional("repositoryId");
                    if (!"REPOSITORY".equals(section) && repository != null) throw new IllegalArgumentException("repositoryId 仅用于 REPOSITORY 分区");
                    if ("SUMMARY".equals(section)) return AgentChangeService.summary(value);
                    if ("PLAN".equals(section)) return Map.of("changeSetId", value.id(), "manifestHash", value.manifestHash(), "plan", value.manifest().plan(), "source", "AI_DELIVERY_PLAN");
                    if (repository == null) throw new IllegalArgumentException("仓库详情需要 repositoryId");
                    var repo = value.manifest().repositories().stream().filter(item -> item.repositoryId().equals(repository)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("清单中不存在该仓库"));
                    var result = new LinkedHashMap<String, Object>();
                    result.put("changeSetId", value.id()); result.put("manifestHash", value.manifestHash()); result.put("repositoryId", repo.repositoryId());
                    result.put("baseSha", repo.baseSha()); result.put("resultSha", repo.resultSha()); result.put("scope", repo.scope());
                    result.put("changedPaths", repo.changedPaths().stream().limit(20).toList()); result.put("changedPathCount", repo.changedPaths().size());
                    result.put("diffHash", repo.diffHash()); result.put("diff", repo.diffPreview()); result.put("diffTruncated", repo.diffTruncated());
                    return result;
                });
    }
    private AgentTool tool(String code, String description, boolean readOnly, Map<String, Object> properties, List<String> required,
                            BiFunction<AgentToolContext, AgentToolArguments, Object> action) {
        var schema = json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return description; }
            public JsonNode inputSchema() { return schema; }
            public Set<String> permissions() { return Set.of("agent:code:use"); }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode args) {
                try { access.requireExecution(context, this); return action.apply(context, new AgentToolArguments(args, properties.keySet())); }
                catch (IllegalArgumentException | AccessDeniedException | AgentCodePolicy.Unavailable ex) { throw new Rejected(ex.getMessage()); }
            }
        };
    }
    private Map<String, Object> string(int max) { return Map.of("type", "string", "maxLength", max); }
}
