package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;

/** 沿用系统配置及加密凭据，不复用仓库自动发现的模糊匹配结果进行写入。 */
@Service
public class AgentGitLabPolicy {
    private final SysConfigService configs;
    private final ObjectMapper json;
    public AgentGitLabPolicy(SysConfigService configs, ObjectMapper json) { this.configs = configs; this.json = json; }

    public Snapshot binding(String repositoryId) {
        String raw = configs.findPlainValue("agent.gitlab", "policy");
        if (raw == null) throw new AgentCodePolicy.Unavailable("尚未配置 Agent GitLab 项目及交付凭据绑定（agent.gitlab/policy）");
        var policy = json.readValue(raw, Policy.class);
        if (policy.instances() == null || policy.bindings() == null || policy.instances().size() > 50 || policy.bindings().size() > 200) throw new IllegalArgumentException("GitLab 策略实例或绑定数量非法");
        var instances = new HashMap<String, Instance>();
        for (var instance : policy.instances()) {
            id(instance.id()); text(instance.name(), 128); text(instance.baseUrl(), 1000); text(instance.credentialGroup(), 100); text(instance.tokenKey(), 100);
            if (instances.put(instance.id(), instance) != null) throw new IllegalArgumentException("GitLab 实例标识重复");
        }
        var bindings = new HashMap<String, Binding>();
        for (var value : policy.bindings()) {
            id(value.repositoryId()); AgentCodePolicy.branch(value.targetBranch());
            var instance = instances.get(value.instanceId());
            if (instance == null || bindings.put(value.repositoryId(), value) != null) throw new IllegalArgumentException("GitLab 仓库绑定重复或引用了缺失实例");
            new AgentGitLabClient.Target(URI.create(instance.baseUrl()), value.projectId(), value.projectPath(), "validation-placeholder");
        }
        var value = bindings.get(repositoryId);
        if (value == null || !value.enabled()) throw new AgentCodePolicy.Unavailable("该仓库缺少已启用的 GitLab 交付绑定");
        var instance = instances.get(value.instanceId());
        if (!instance.enabled()) throw new AgentCodePolicy.Unavailable("GitLab 交付实例已停用");
        return new Snapshot(instance, value, AgentCodePolicy.hash(json.writeValueAsString(new Identity(instance, value))));
    }

    public void requireCurrent(Snapshot snapshot) {
        if (!binding(snapshot.binding().repositoryId()).hash().equals(snapshot.hash())) throw new IllegalStateException("GitLab 交付绑定已改变，旧请求不能继续写入");
    }
    public AgentGitLabClient.Target target(Snapshot snapshot) {
        requireCurrent(snapshot);
        var instance = snapshot.instance(); var value = snapshot.binding();
        return new AgentGitLabClient.Target(URI.create(instance.baseUrl()), value.projectId(), value.projectPath(),
                configs.requireSecretValue(instance.credentialGroup(), instance.tokenKey()));
    }
    private static void id(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) throw new IllegalArgumentException("GitLab 绑定标识非法"); }
    private static void text(String value, int max) { if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw new IllegalArgumentException("GitLab 配置字段缺失或超长"); }
    public record Policy(List<Instance> instances, List<Binding> bindings) { }
    public record Instance(String id, String name, String baseUrl, String credentialGroup, String tokenKey, boolean enabled) { }
    public record Binding(String repositoryId, String instanceId, long projectId, String projectPath, String targetBranch, boolean enabled) { }
    public record Snapshot(Instance instance, Binding binding, String hash) { }
    private record Identity(Instance instance, Binding binding) { }
}
