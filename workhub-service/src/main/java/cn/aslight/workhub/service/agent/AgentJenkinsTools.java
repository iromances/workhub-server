package cn.aslight.workhub.service.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;

/** 发布准备仅暴露目标清单与只读预检；触发构建只能由后续审批出站流程调用。 */
@Configuration(proxyBeanMethods = false)
public class AgentJenkinsTools {
    private final AgentJenkinsPolicy policy;
    private final AgentJenkinsClient client;
    private final AgentAccessService access;
    private final ObjectMapper json;

    public AgentJenkinsTools(AgentJenkinsPolicy policy, AgentJenkinsClient client, AgentAccessService access, ObjectMapper json) {
        this.policy = policy; this.client = client; this.access = access; this.json = json;
    }

    @Bean AgentTool agentJenkinsTargetsTool() {
        return tool("release.jenkins.targets", "列出本任务范围内配置的 Jenkins 发布目标；不会触发构建，不返回地址或凭据。", Map.of(), List.of(), (context, args) -> {
            var result = new ArrayList<Object>();
            for (var binding : policy.current().bindings()) {
                if (!binding.enabled() || !context.specification().scopes().contains(binding.scope())) continue;
                access.requireScope(context, binding.scope());
                var snapshot = policy.binding(binding.id());
                result.add(Map.of("bindingId", binding.id(), "name", binding.name(), "scope", binding.scope(),
                        "systemName", binding.systemName(), "stage", binding.stage(), "repositories", binding.commitParameters().keySet(),
                        "bindingHash", snapshot.hash(), "state", "CONFIGURED_NOT_PROBED"));
            }
            return Map.of("targets", result, "coverage", result.isEmpty() ? "NO_AUTHORIZED_TARGET" : "CONFIGURED");
        });
    }

    @Bean AgentTool agentJenkinsPreflightTool() {
        return tool("release.jenkins.preflight", "只读核对已绑定 Job 可构建状态与关联/提交/清单参数；预检通过不代表已获发布审核。", Map.of("bindingId", Map.of("type", "string", "maxLength", 64)), List.of("bindingId"), (context, args) -> {
            var snapshot = policy.binding(args.required("bindingId"));
            access.requireScope(context, snapshot.binding().scope());
            var probe = client.probe(policy.target(snapshot));
            policy.requireCurrent(snapshot);
            var required = policy.requiredParameters(snapshot.binding());
            var missing = required.stream().filter(name -> !probe.parameterNames().contains(name)).toList();
            boolean sameJob = String.join("/", snapshot.binding().jobSegments()).equals(probe.jobName());
            return Map.of("bindingId", snapshot.binding().id(), "bindingHash", snapshot.hash(), "scope", snapshot.binding().scope(),
                    "buildable", probe.buildable(), "jobMatches", sameJob, "missingParameters", missing,
                    "state", probe.buildable() && sameJob && missing.isEmpty() ? "PREFLIGHT_PASSED" : "NOT_READY", "approved", false, "triggered", false);
        });
    }

    private AgentTool tool(String name, String description, Map<String, Object> properties, List<String> required,
                            BiFunction<AgentToolContext, AgentToolArguments, Object> action) {
        var schema = json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
        return new AgentTool() {
            public String code() { return name; }
            public String description() { return description; }
            public JsonNode inputSchema() { return schema; }
            public Set<String> permissions() { return Set.of("agent:task:assign"); }
            public boolean readOnly() { return true; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                access.requireExecution(context, this);
                return action.apply(context, new AgentToolArguments(arguments, properties.keySet()));
            }
        };
    }
}
