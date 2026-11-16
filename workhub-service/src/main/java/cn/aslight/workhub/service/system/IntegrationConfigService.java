package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysConfigMapper;
import cn.aslight.workhub.model.system.IntegrationConfigResponse;
import cn.aslight.workhub.model.system.IntegrationConfigSaveRequest;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import cn.aslight.workhub.model.system.SysConfigItemEntity;
import cn.aslight.workhub.model.system.SysConfigSaveRequest;
import cn.aslight.workhub.service.agent.AgentJenkinsPolicy;
import cn.aslight.workhub.service.intake.ZentaoClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统集成连接配置服务：禅道与 Jenkins 的地址/账号/凭据表单读写。
 * 凭据继续存 sys_config（SECRET 加密，SysConfigService 落库）；Jenkins 实例写回
 * agent.jenkins/policy 的 instances 段并原样保留 bindings 段。响应只回地址与账号，
 * 凭据一律以“已配置”布尔表达，任何路径不回显明文。
 */
@Service
public class IntegrationConfigService {

    public static final String JENKINS_POLICY_GROUP = "agent.jenkins";
    public static final String JENKINS_POLICY_KEY = "policy";
    public static final String JENKINS_CREDENTIAL_GROUP_PREFIX = "jenkins.credential.";
    public static final String JENKINS_TOKEN_KEY = "apiToken";
    private static final int MAX_INSTANCES = 50;

    private final IntegrationCatalogService catalogService;
    private final SysConfigService sysConfigService;
    private final SysConfigMapper sysConfigMapper;
    private final AgentJenkinsPolicy jenkinsPolicy;
    private final SystemAuditService auditService;
    private final ObjectMapper objectMapper;

    public IntegrationConfigService(IntegrationCatalogService catalogService,
                                    SysConfigService sysConfigService,
                                    SysConfigMapper sysConfigMapper,
                                    AgentJenkinsPolicy jenkinsPolicy,
                                    SystemAuditService auditService,
                                    ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.sysConfigService = sysConfigService;
        this.sysConfigMapper = sysConfigMapper;
        this.jenkinsPolicy = jenkinsPolicy;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询集成系统连接配置：仅返回地址/账号与凭据已配置布尔。
     */
    public IntegrationConfigResponse get(Long id) {
        IntegrationSystemEntity system = catalogService.requireExisting(id);
        return switch (system.getSystemCode()) {
            case "ZENTAO" -> zentaoConfig();
            case "JENKINS" -> jenkinsConfig();
            default -> throw new IllegalArgumentException("该集成系统不支持连接配置表单：" + system.getSystemCode());
        };
    }

    /**
     * 保存集成系统连接配置：ZENTAO 覆盖 zentao.base 三项；JENKINS 全量覆盖 policy instances
     * 并保留 bindings；保存后经 AgentJenkinsPolicy.current() 结构复核，失败即整体回滚。
     */
    @Transactional
    public IntegrationConfigResponse save(Long id, IntegrationConfigSaveRequest request, String operator, String ip) {
        IntegrationSystemEntity system = catalogService.requireExisting(id);
        IntegrationConfigResponse response = switch (system.getSystemCode()) {
            case "ZENTAO" -> {
                requireSection(request.getZentao() != null, "请求缺少禅道配置段（zentao）");
                yield saveZentao(request.getZentao(), operator, ip);
            }
            case "JENKINS" -> {
                requireSection(request.getJenkins() != null, "请求缺少 Jenkins 配置段（jenkins）");
                yield saveJenkins(request.getJenkins(), operator, ip);
            }
            default -> throw new IllegalArgumentException("该集成系统不支持连接配置表单：" + system.getSystemCode());
        };
        audit(system, "CONFIG_UPDATE", "{systemCode=%s, fields=%s}".formatted(
                system.getSystemCode(),
                system.getSystemCode().equals("ZENTAO") ? "baseUrl,account,password" : "instances"),
                operator, ip);
        return response;
    }

    // ---------------- 禅道 ----------------

    private IntegrationConfigResponse zentaoConfig() {
        IntegrationConfigResponse response = baseResponse("ZENTAO");
        IntegrationConfigResponse.ZentaoConfig zentao = new IntegrationConfigResponse.ZentaoConfig();
        zentao.setBaseUrl(sysConfigService.findPlainValue(ZentaoClient.CONFIG_GROUP, ZentaoClient.CONFIG_KEY_BASE_URL));
        zentao.setAccount(sysConfigService.findPlainValue(ZentaoClient.CONFIG_GROUP, ZentaoClient.CONFIG_KEY_ACCOUNT));
        zentao.setPasswordConfigured(secretConfigured(ZentaoClient.CONFIG_GROUP, ZentaoClient.CONFIG_KEY_PASSWORD));
        response.setZentao(zentao);
        return response;
    }

    private IntegrationConfigResponse saveZentao(IntegrationConfigSaveRequest.ZentaoSave zentao, String operator, String ip) {
        // 入参在控制器反序列化后于此统一校验，password 留空=保留既有值
        String baseUrl = requireText(zentao.getBaseUrl(), "禅道地址不能为空");
        String account = requireText(zentao.getAccount(), "禅道账号不能为空");
        String password = trimToNull(zentao.getPassword());
        if (password == null && !secretConfigured(ZentaoClient.CONFIG_GROUP, ZentaoClient.CONFIG_KEY_PASSWORD)) {
            throw new IllegalArgumentException("禅道密码尚未配置，首次保存必须填写密码");
        }
        upsertConfigValue(ZentaoClient.CONFIG_GROUP, ZentaoClient.CONFIG_KEY_BASE_URL, "禅道地址",
                SysConfigService.VALUE_TYPE_TEXT, baseUrl, operator, ip);
        upsertConfigValue(ZentaoClient.CONFIG_GROUP, ZentaoClient.CONFIG_KEY_ACCOUNT, "禅道账号",
                SysConfigService.VALUE_TYPE_TEXT, account, operator, ip);
        if (password != null) {
            upsertConfigValue(ZentaoClient.CONFIG_GROUP, ZentaoClient.CONFIG_KEY_PASSWORD, "禅道密码",
                    SysConfigService.VALUE_TYPE_SECRET, password, operator, ip);
        }
        return zentaoConfig();
    }

    // ---------------- Jenkins ----------------

    private IntegrationConfigResponse jenkinsConfig() {
        IntegrationConfigResponse response = baseResponse("JENKINS");
        IntegrationConfigResponse.JenkinsConfig config = new IntegrationConfigResponse.JenkinsConfig();
        List<IntegrationConfigResponse.JenkinsInstance> instances = new ArrayList<>();
        AgentJenkinsPolicy.Policy policy = readPolicyLenient();
        if (policy != null && policy.instances() != null) {
            for (AgentJenkinsPolicy.Instance instance : policy.instances()) {
                IntegrationConfigResponse.JenkinsInstance view = new IntegrationConfigResponse.JenkinsInstance();
                view.setId(instance.id());
                view.setName(instance.name());
                view.setBaseUrl(instance.baseUrl());
                view.setUserName(instance.userName());
                view.setEnabled(instance.enabled());
                view.setTokenConfigured(secretConfigured(instance.credentialGroup(), instance.tokenKey()));
                instances.add(view);
            }
        }
        config.setInstances(instances);
        response.setJenkins(config);
        return response;
    }

    private IntegrationConfigResponse saveJenkins(IntegrationConfigSaveRequest.JenkinsSave save, String operator, String ip) {
        List<IntegrationConfigSaveRequest.JenkinsInstanceSave> inputs = save.getInstances();
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Jenkins 实例列表不能为空");
        }
        if (inputs.size() > MAX_INSTANCES) {
            throw new IllegalArgumentException("Jenkins 实例数超出上限：" + MAX_INSTANCES);
        }
        AgentJenkinsPolicy.Policy currentPolicy = readPolicyLenient();
        List<AgentJenkinsPolicy.JobBinding> bindings = currentPolicy == null || currentPolicy.bindings() == null
                ? List.of()
                : currentPolicy.bindings();
        Map<String, AgentJenkinsPolicy.Instance> existingById = currentPolicy == null || currentPolicy.instances() == null
                ? Map.of()
                : currentPolicy.instances().stream()
                        .collect(Collectors.toMap(AgentJenkinsPolicy.Instance::id, Function.identity()));

        List<AgentJenkinsPolicy.Instance> instances = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        for (IntegrationConfigSaveRequest.JenkinsInstanceSave input : inputs) {
            String instanceId = requireText(input.getId(), "Jenkins 实例标识不能为空");
            if (!instanceId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
                throw new IllegalArgumentException("Jenkins 实例标识只能为字母、数字、下划线或连字符，且以字母数字开头：" + instanceId);
            }
            if (!keptIds.add(instanceId)) {
                throw new IllegalArgumentException("Jenkins 实例标识重复：" + instanceId);
            }
            String name = requireText(input.getName(), "Jenkins 实例名称不能为空（" + instanceId + "）");
            String baseUrl = requireText(input.getBaseUrl(), "Jenkins 地址不能为空（" + instanceId + "）");
            String userName = requireText(input.getUserName(), "Jenkins 账号不能为空（" + instanceId + "）");
            String apiToken = trimToNull(input.getApiToken());
            AgentJenkinsPolicy.Instance existing = existingById.get(instanceId);
            String credentialGroup;
            String tokenKey;
            if (existing != null) {
                credentialGroup = existing.credentialGroup();
                tokenKey = existing.tokenKey();
            } else {
                if (apiToken == null) {
                    throw new IllegalArgumentException("新增 Jenkins 实例（" + instanceId + "）必须填写 API Token");
                }
                credentialGroup = JENKINS_CREDENTIAL_GROUP_PREFIX + instanceId;
                tokenKey = JENKINS_TOKEN_KEY;
            }
            instances.add(new AgentJenkinsPolicy.Instance(instanceId, name, baseUrl, userName,
                    credentialGroup, tokenKey, input.getEnabled() == null || input.getEnabled()));
            if (apiToken != null) {
                upsertConfigValue(credentialGroup, tokenKey, "Jenkins API Token（" + instanceId + "）",
                        SysConfigService.VALUE_TYPE_SECRET, apiToken, operator, ip);
            }
        }
        for (AgentJenkinsPolicy.JobBinding binding : bindings) {
            if (!keptIds.contains(binding.instanceId())) {
                throw new IllegalArgumentException("Jenkins 实例（" + binding.instanceId()
                        + "）仍被 Job 绑定（" + binding.id() + "）引用，不能删除");
            }
        }

        AgentJenkinsPolicy.Policy updated = new AgentJenkinsPolicy.Policy(instances, bindings);
        upsertConfigValue(JENKINS_POLICY_GROUP, JENKINS_POLICY_KEY, "Jenkins 策略（实例与 Job 绑定）",
                SysConfigService.VALUE_TYPE_TEXT, writePolicy(updated), operator, ip);
        // 结构复核（含 URI、长度与 bindings 引用），失败抛出使整体事务回滚
        jenkinsPolicy.current();
        return jenkinsConfig();
    }

    /** 宽松读取 policy 原文：未配置返回 null；解析失败明确报错，便于表单展示存量数据。 */
    private AgentJenkinsPolicy.Policy readPolicyLenient() {
        String raw = sysConfigService.findPlainValue(JENKINS_POLICY_GROUP, JENKINS_POLICY_KEY);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, AgentJenkinsPolicy.Policy.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Jenkins 策略（agent.jenkins/policy）解析失败，请先在系统配置中修复：" + ex.getMessage(), ex);
        }
    }

    private String writePolicy(AgentJenkinsPolicy.Policy policy) {
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (Exception ex) {
            throw new IllegalStateException("Jenkins 策略序列化失败", ex);
        }
    }

    // ---------------- 公共 ----------------

    private IntegrationConfigResponse baseResponse(String systemCode) {
        IntegrationConfigResponse response = new IntegrationConfigResponse();
        response.setSystemCode(systemCode);
        return response;
    }

    private boolean secretConfigured(String configGroup, String configKey) {
        SysConfigItemEntity entity = findConfigItem(configGroup, configKey);
        return entity != null && entity.getEncryptedValue() != null && !entity.getEncryptedValue().isBlank();
    }

    /** 按组+键精确查找（含停用项），避免 findEnabledByKey 漏掉停用配置后重复插入。 */
    private SysConfigItemEntity findConfigItem(String configGroup, String configKey) {
        return sysConfigMapper.findAll(configGroup, null).stream()
                .filter(item -> configKey.equals(item.getConfigKey()))
                .findFirst()
                .orElse(null);
    }

    private void upsertConfigValue(String configGroup, String configKey, String configName,
                                   String valueType, String value, String operator, String ip) {
        SysConfigItemEntity existing = findConfigItem(configGroup, configKey);
        SysConfigSaveRequest request = new SysConfigSaveRequest();
        request.setConfigGroup(configGroup);
        request.setConfigKey(configKey);
        request.setConfigName(configName);
        request.setValueType(valueType);
        request.setValue(value);
        if (existing == null) {
            sysConfigService.create(request, operator, ip);
        } else {
            request.setEnabled(existing.getEnabled());
            request.setRemark(existing.getRemark());
            sysConfigService.update(existing.getId(), request, operator, ip);
        }
    }

    private void audit(IntegrationSystemEntity system, String actionType, String snapshot, String operator, String ip) {
        auditService.operation(operator, "system:integration:update", actionType,
                "SYS_INTEGRATION_SYSTEM", String.valueOf(system.getId()), null, snapshot, "SUCCESS", null, ip);
    }

    private void requireSection(boolean present, String message) {
        if (!present) {
            throw new IllegalArgumentException(message);
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
