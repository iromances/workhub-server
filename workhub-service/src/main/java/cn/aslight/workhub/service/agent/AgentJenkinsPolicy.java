package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.*;

/** 实例和 Job 只由既有系统配置维护；AI 不能提供 Jenkins 地址、凭据或任意参数。 */
@Service
public class AgentJenkinsPolicy {
    private final SysConfigService configs;
    private final ObjectMapper json;

    public AgentJenkinsPolicy(SysConfigService configs, ObjectMapper json) { this.configs = configs; this.json = json; }

    public Policy current() {
        String raw = configs.findPlainValue("agent.jenkins", "policy");
        if (raw == null) throw new AgentCodePolicy.Unavailable("尚未配置 Jenkins 实例及 Job 绑定（agent.jenkins/policy）");
        var policy = json.readValue(raw, Policy.class);
        if (policy.instances() == null || policy.bindings() == null || policy.instances().size() > 50 || policy.bindings().size() > 500) throw new IllegalArgumentException("Jenkins 策略实例或绑定数量非法");
        var instances = new HashMap<String, Instance>();
        for (var instance : policy.instances()) {
            id(instance.id()); text(instance.name(), 128); text(instance.baseUrl(), 1000);
            text(instance.userName(), 100); text(instance.credentialGroup(), 100); text(instance.tokenKey(), 100);
            // 这里只做结构预检，不读取密钥也不发送 HTTP。
            new AgentJenkinsClient.Target(URI.create(instance.baseUrl()), List.of("validate"), instance.userName(), "validation-placeholder");
            if (instances.put(instance.id(), instance) != null) throw new IllegalArgumentException("Jenkins 实例标识重复");
        }
        var ids = new HashSet<String>();
        for (var binding : policy.bindings()) {
            id(binding.id()); text(binding.name(), 128); text(binding.systemName(), 128);
            if (!ids.add(binding.id())) throw new IllegalArgumentException("Jenkins Job 绑定标识重复");
            var instance = instances.get(binding.instanceId());
            if (instance == null) throw new IllegalArgumentException("Jenkins Job 引用的实例不存在");
            new AgentJenkinsClient.Target(URI.create(instance.baseUrl()), binding.jobSegments(), instance.userName(), "validation-placeholder");
            if (binding.scope() == null) throw new IllegalArgumentException("Jenkins Job 必须绑定业务线及环境");
            text(binding.scope().businessLineCode(), 32); text(binding.scope().environmentCode(), 64);
            if (!Set.of("BUILD_TEST", "TEST_DEPLOY", "PROD_DEPLOY").contains(binding.stage())) throw new IllegalArgumentException("Jenkins 阶段须为 BUILD_TEST、TEST_DEPLOY 或 PROD_DEPLOY");
            if ("BUILD_TEST".equals(binding.stage()) && !"TEST".equals(binding.scope().environmentCode()))
                throw new IllegalArgumentException("独立构建测试只允许 TEST 环境，不能绑定生产");
            if ("TEST_DEPLOY".equals(binding.stage()) && !"TEST".equals(binding.scope().environmentCode())
                    || "PROD_DEPLOY".equals(binding.stage()) && !"PROD".equals(binding.scope().environmentCode()))
                throw new IllegalArgumentException("Jenkins 发布阶段与目标环境不一致");
            if (binding.commitParameters() == null || binding.commitParameters().isEmpty() || binding.commitParameters().size() > 20) throw new IllegalArgumentException("Jenkins Job 必须明确1到20个仓库的精确提交参数");
            var names = new HashSet<String>();
            for (String parameter : Arrays.asList(binding.correlationParameter(), binding.manifestParameter(), binding.environmentParameter())) {
                parameter(parameter); if (!names.add(parameter)) throw new IllegalArgumentException("Jenkins 关联/清单/环境参数不能重名");
            }
            binding.commitParameters().forEach((repo, name) -> { id(repo); parameter(name); if (!names.add(name)) throw new IllegalArgumentException("Jenkins 提交参数不能重复"); });
            if (binding.fixedParameters() == null) throw new IllegalArgumentException("Jenkins 固定参数必须为对象");
            binding.fixedParameters().forEach((name, value) -> {
                parameter(name); if (!names.add(name)) throw new IllegalArgumentException("Jenkins 固定参数不能覆盖受控参数");
                text(value, 4000);
                if (!new AgentToolData(json).sanitize(Map.of(name, value)).path(name).asText().equals(value)) throw new IllegalArgumentException("Jenkins 固定参数不能包含明文敏感值；密钥留在 Jenkins 凭据中");
            });
            path(binding.receiptPath());
        }
        return policy;
    }

    public BindingSnapshot binding(String id) {
        var policy = current();
        var binding = policy.bindings().stream().filter(item -> item.enabled() && item.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jenkins Job 绑定不存在或已停用"));
        var instance = policy.instances().stream().filter(item -> item.enabled() && item.id().equals(binding.instanceId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jenkins 实例不存在或已停用"));
        return new BindingSnapshot(instance, binding, AgentCodePolicy.hash(json.writeValueAsString(new BindingIdentity(instance, binding))));
    }

    public AgentJenkinsClient.Target target(BindingSnapshot snapshot) {
        requireCurrent(snapshot);
        var instance = snapshot.instance();
        return new AgentJenkinsClient.Target(URI.create(instance.baseUrl()), snapshot.binding().jobSegments(), instance.userName(),
                configs.requireSecretValue(instance.credentialGroup(), instance.tokenKey()));
    }

    public void requireCurrent(BindingSnapshot snapshot) {
        if (!binding(snapshot.binding().id()).equals(snapshot)) throw new IllegalStateException("Jenkins 绑定已改变，旧预检或审批不能继续使用");
    }

    /** 仅由平台构造固定参数；模型提供不了任意键或动态脚本。发布仍需独立的审批与出站记录。 */
    public Map<String, String> parameters(BindingSnapshot snapshot, String requestId, String manifestHash, Map<String, String> commits) {
        requireCurrent(snapshot);
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}") || manifestHash == null || !manifestHash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("发布请求标识或清单摘要非法");
        var binding = snapshot.binding();
        if (!binding.commitParameters().keySet().equals(commits.keySet())) throw new IllegalArgumentException("提交清单与 Jenkins 绑定仓库不一致");
        var result = new TreeMap<>(binding.fixedParameters());
        commits.forEach((repo, sha) -> result.put(binding.commitParameters().get(repo), AgentCodeGit.sha(sha)));
        result.put(binding.correlationParameter(), requestId);
        result.put(binding.manifestParameter(), manifestHash);
        result.put(binding.environmentParameter(), binding.scope().environmentCode());
        AgentJenkinsClient.validateParameters(result);
        return Collections.unmodifiableMap(result);
    }

    public List<String> requiredParameters(JobBinding binding) {
        var names = new TreeSet<>(binding.fixedParameters().keySet()); names.addAll(binding.commitParameters().values());
        names.addAll(List.of(binding.correlationParameter(), binding.manifestParameter(), binding.environmentParameter()));
        return List.copyOf(names);
    }
    private static void id(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) throw new IllegalArgumentException("Jenkins/仓库绑定标识非法"); }
    private static void text(String value, int max) { if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Jenkins 配置字段缺失或超长"); }
    private static void parameter(String value) { if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_-]{0,63}")) throw new IllegalArgumentException("Jenkins 参数名称非法"); }
    private static void path(String value) {
        text(value, 300);
        if (value.startsWith("/") || Arrays.stream(value.split("/", -1)).anyMatch(part -> part.isBlank() || part.equals(".") || part.equals("..")
                || part.chars().anyMatch(c -> c < 32 || c == 127 || "\\%?#:".indexOf(c) >= 0))) throw new IllegalArgumentException("Jenkins 回执路径必须为明确的相对路径");
    }

    public record Policy(List<Instance> instances, List<JobBinding> bindings) { }
    public record Instance(String id, String name, String baseUrl, String userName, String credentialGroup, String tokenKey, boolean enabled) { }
    public record JobBinding(String id, String name, String instanceId, AgentScope scope, String systemName, String stage,
                              List<String> jobSegments, Map<String, String> commitParameters, String correlationParameter,
                              String manifestParameter, String environmentParameter, Map<String, String> fixedParameters,
                              String receiptPath, boolean enabled) {
        public JobBinding {
            if (commitParameters != null) commitParameters = Collections.unmodifiableMap(new TreeMap<>(commitParameters));
            if (fixedParameters != null) fixedParameters = Collections.unmodifiableMap(new TreeMap<>(fixedParameters));
        }
    }
    public record BindingSnapshot(Instance instance, JobBinding binding, String hash) { }
    private record BindingIdentity(Instance instance, JobBinding binding) { }
}
