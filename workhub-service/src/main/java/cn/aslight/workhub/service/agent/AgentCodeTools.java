package cn.aslight.workhub.service.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;

/** 所选 AI 可直接完成查找、读写和差异检查，无需把 API 任务转交 Codex。 */
@Configuration(proxyBeanMethods = false)
public class AgentCodeTools {
    private final AgentCodeWorkspaceService code;
    private final AgentAccessService access;
    private final ObjectMapper json;

    public AgentCodeTools(AgentCodeWorkspaceService code, AgentAccessService access, ObjectMapper json) {
        this.code = code; this.access = access; this.json = json;
    }

    @Bean AgentTool agentCodeRepositoriesTool() {
        return tool("code.repositories", "列出本任务授权的仓库和实际基线 SHA；配置缺失明确返回未就绪，不推断路径或默认分支。", true,
                Map.of(), List.of(), (context, args) -> code.repositories(context));
    }
    @Bean AgentTool agentCodeOpenTool() {
        return tool("code.open", "按已绑定仓库和完整 baseSha 准备独立工作区；同一运行的同一仓库幂等，不带入用户未提交修改。", false,
                Map.of("repositoryId", string(64), "baseSha", string(64)), List.of("repositoryId", "baseSha"),
                (context, args) -> code.open(context, args.required("repositoryId"), args.required("baseSha")));
    }
    @Bean AgentTool agentCodeStatusTool() {
        return tool("code.status", "查看本次运行全部仓库工作区、版本和候选 SHA；候选修改尚不代表分支已交付或测试通过。", true,
                Map.of(), List.of(), (context, args) -> code.status(context));
    }
    @Bean AgentTool agentCodeListTool() {
        return tool("code.list", "按路径前缀列出工作区的授权普通文件，每页100项，返回 blob 用于修改校验。", true,
                Map.of("workspaceId", string(36), "prefix", string(500), "offset", integer(0, 100000)), List.of("workspaceId"),
                (context, args) -> code.list(context, args.required("workspaceId"), args.optional("prefix"), args.bounded("offset", 0, 0, 100000)));
    }
    @Bean AgentTool agentCodeReadTool() {
        return tool("code.read", "分页读取授权文本与原 blob，返回 nextOffset 和是否脱敏；不读取软链接、二进制、敏感配置或超过128KiB的文件。", true,
                Map.of("workspaceId", string(36), "path", string(500), "offset", integer(0, 131072), "limit", integer(100, 16000)), List.of("workspaceId", "path"),
                (context, args) -> code.read(context, args.required("workspaceId"), args.required("path"), args.bounded("offset", 0, 0, 131072), args.bounded("limit", 16000, 100, 16000)));
    }
    @Bean AgentTool agentCodeSearchTool() {
        return tool("code.search", "在授权代码中按字面文本检索；每批最多100个文件、100条命中，返回 nextOffset 和未检查的大文件。", true,
                Map.of("workspaceId", string(36), "text", string(200), "offset", integer(0, 100000)), List.of("workspaceId", "text"),
                (context, args) -> code.search(context, args.required("workspaceId"), args.content("text", 200), args.bounded("offset", 0, 0, 100000)));
    }
    @Bean AgentTool agentCodeWriteTool() {
        var properties = mutationProperties(); properties.put("content", string(131072));
        return tool("code.write", "以完整文本创建或更新授权文件；必须提供当前 version、原 blob（新文件为 MISSING）和唯一 requestKey。仅生成候选修改，不发布。", false,
                properties, List.copyOf(properties.keySet()), (context, args) -> code.write(context, args.required("workspaceId"), args.version("version"),
                        args.required("path"), args.required("expectedBlob"), args.content("content", 131072), args.required("requestKey"), false));
    }
    @Bean AgentTool agentCodeDeleteTool() {
        var properties = mutationProperties();
        return tool("code.delete", "删除工作区中已授权的普通文件，需当前版本、原 blob 和唯一 requestKey；不修改源仓库。", false,
                properties, List.copyOf(properties.keySet()), (context, args) -> code.write(context, args.required("workspaceId"), args.version("version"),
                        args.required("path"), args.required("expectedBlob"), null, args.required("requestKey"), true));
    }
    @Bean AgentTool agentCodeReplaceTool() {
        var properties = mutationProperties(); properties.put("oldText", string(16000)); properties.put("newText", string(16000));
        return tool("code.replace", "以恰好出现一次的原文进行局部修改；保留文件其余内容，适合分页读取或包含脱敏段落的文件。需要当前版本、原 blob 和唯一请求标识。", false,
                properties, List.copyOf(properties.keySet()), (context, args) -> code.replace(context, args.required("workspaceId"), args.version("version"),
                        args.required("path"), args.required("expectedBlob"), args.content("oldText", 16000), args.content("newText", 16000), args.required("requestKey")));
    }
    @Bean AgentTool agentCodeDiffTool() {
        return tool("code.diff", "返回相对固定基线的实际候选差异；最多100个文件/32000字，截断时应分文件复核。", true,
                Map.of("workspaceId", string(36)), List.of("workspaceId"), (context, args) -> code.diff(context, args.required("workspaceId")));
    }

    private AgentTool tool(String name, String description, boolean readOnly, Map<String, Object> properties,
                            List<String> required, BiFunction<AgentToolContext, AgentToolArguments, Object> action) {
        JsonNode schema = json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
        return new AgentTool() {
            public String code() { return name; }
            public String description() { return description; }
            public JsonNode inputSchema() { return schema; }
            public Set<String> permissions() { return Set.of("agent:code:use"); }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                access.requireExecution(context, this);
                try { return action.apply(context, new AgentToolArguments(arguments, properties.keySet())); }
                catch (IllegalArgumentException | AgentCodePolicy.Unavailable ex) { throw new Rejected(ex.getMessage()); }
            }
        };
    }

    private Map<String, Object> mutationProperties() {
        return new LinkedHashMap<>(Map.of("workspaceId", string(36), "version", integer(0, Long.MAX_VALUE),
                "path", string(500), "expectedBlob", string(64), "requestKey", string(64)));
    }
    private Map<String, Object> string(int limit) { return Map.of("type", "string", "maxLength", limit); }
    private Map<String, Object> integer(long min, long max) { return Map.of("type", "integer", "minimum", min, "maximum", max); }
}
