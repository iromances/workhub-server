package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.dao.ai.AiUseCaseConfigMapper;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigQuery;
import cn.aslight.workhub.model.ai.AiUseCaseConfigRequest;
import cn.aslight.workhub.model.ai.AiUseCaseConfigResponse;
import cn.aslight.workhub.model.ai.AiUseCaseManagePage;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.io.InputStream;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 任务配置服务。
 */
@Service
public class AiUseCaseConfigService {

    private final AiUseCaseConfigMapper aiUseCaseConfigMapper;
    private final AiProviderConfigService aiProviderConfigService;
    private final SystemAuditService auditService;

    public AiUseCaseConfigService(AiUseCaseConfigMapper aiUseCaseConfigMapper,
                                  AiProviderConfigService aiProviderConfigService) {
        this(aiUseCaseConfigMapper, aiProviderConfigService, null);
    }

    @Autowired
    public AiUseCaseConfigService(AiUseCaseConfigMapper aiUseCaseConfigMapper,
                                  AiProviderConfigService aiProviderConfigService,
                                  SystemAuditService auditService) {
        this.aiUseCaseConfigMapper = aiUseCaseConfigMapper;
        this.aiProviderConfigService = aiProviderConfigService;
        this.auditService = auditService;
    }

    public List<AiUseCaseConfigResponse> list(AiUseCaseConfigQuery query) {
        AiUseCaseConfigQuery safeQuery = query == null ? new AiUseCaseConfigQuery() : query;
        return aiUseCaseConfigMapper.findAll(
                upperOrNull(safeQuery.getDomain()),
                safeQuery.getProviderConfigId(),
                upperOrNull(safeQuery.getChannelType()),
                trimToNull(safeQuery.getVendor()),
                upperOrNull(safeQuery.getModelProvider()),
                safeQuery.getEnabled(),
                trimToNull(safeQuery.getKeyword())
        );
    }

    public AiUseCaseConfigResponse detail(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("AI 任务配置 ID 不能为空");
        }
        AiUseCaseConfigResponse response = aiUseCaseConfigMapper.findDetailById(id);
        if (response == null) {
            throw new IllegalArgumentException("AI 任务配置不存在");
        }
        return response;
    }

    public AiUseCaseManagePage listForManage(AiUseCaseConfigQuery query) {
        AiUseCaseConfigQuery safe = query == null ? new AiUseCaseConfigQuery() : query;
        return AiUseCaseManagePage.of(list(safe), safe.getPageNum(), safe.getPageSize());
    }

    public AiUseCaseConfigResponse detailForManage(Long id) { return detail(id); }
    public AiUseCaseConfigEntity getEnabledByUseCaseCode(String useCaseCode) { return requireEnabledByCode(useCaseCode); }

    public AiUseCaseConfigEntity requireEnabledByCode(String useCaseCode) {
        String normalizedCode = requireText(useCaseCode, "AI 场景编码不能为空");
        AiUseCaseConfigEntity entity = aiUseCaseConfigMapper.findEntityByCode(normalizedCode);
        if (entity == null) {
            throw new IllegalStateException("AI 场景配置不存在: " + normalizedCode);
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalStateException("AI 场景配置已停用: " + normalizedCode);
        }
        return entity;
    }

    @Transactional
    public AiUseCaseConfigResponse create(AiUseCaseConfigRequest request) {
        return createInternal(request, null, null);
    }

    @Transactional
    public AiUseCaseConfigResponse create(AiUseCaseConfigRequest request, String operator, String ip) {
        return createInternal(request, operator, ip);
    }

    private AiUseCaseConfigResponse createInternal(AiUseCaseConfigRequest request, String operator, String ip) {
        ensureCodeAvailable(request.getUseCaseCode(), null);
        AiUseCaseConfigEntity entity = toEntity(new AiUseCaseConfigEntity(), request, null);
        entity.setCreateBy(operator);
        entity.setModifyBy(operator);
        aiUseCaseConfigMapper.insert(entity);
        AiUseCaseConfigResponse response = detail(entity.getId());
        audit("CREATE", null, entity, operator, ip);
        return response;
    }

    @Transactional
    public AiUseCaseConfigResponse update(Long id, AiUseCaseConfigRequest request) {
        return updateInternal(id, request, null, null);
    }

    @Transactional
    public AiUseCaseConfigResponse update(Long id,
                                          AiUseCaseConfigRequest request,
                                          String operator,
                                          String ip) {
        return updateInternal(id, request, operator, ip);
    }

    private AiUseCaseConfigResponse updateInternal(Long id,
                                                   AiUseCaseConfigRequest request,
                                                   String operator,
                                                   String ip) {
        AiUseCaseConfigEntity existing = requireExisting(id);
        String requestedCode = requireText(request.getUseCaseCode(), "AI 任务编码不能为空");
        if (!requestedCode.equals(existing.getUseCaseCode())) {
            throw new IllegalArgumentException("AI 任务编码创建后不可修改");
        }
        ensureCodeAvailable(request.getUseCaseCode(), id);
        AiUseCaseConfigEntity entity = toEntity(new AiUseCaseConfigEntity(), request, existing);
        entity.setId(id);
        entity.setVersion(existing.getVersion());
        entity.setModifyBy(operator);
        if (aiUseCaseConfigMapper.update(entity) != 1) {
            throw new IllegalStateException("AI 任务配置已被其他操作修改，请刷新后重试");
        }
        AiUseCaseConfigResponse response = detail(id);
        audit("UPDATE", existing, entity, operator, ip);
        return response;
    }

    private AiUseCaseConfigEntity requireExisting(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("AI 任务配置 ID 不能为空");
        }
        AiUseCaseConfigEntity entity = aiUseCaseConfigMapper.findEntityById(id);
        if (entity == null) {
            throw new IllegalArgumentException("AI 任务配置不存在");
        }
        return entity;
    }

    private void ensureCodeAvailable(String useCaseCode, Long currentId) {
        String normalizedCode = requireText(useCaseCode, "AI 任务编码不能为空");
        AiUseCaseConfigEntity existing = aiUseCaseConfigMapper.findEntityByCode(normalizedCode);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("AI 任务编码已存在");
        }
    }

    private AiUseCaseConfigEntity toEntity(AiUseCaseConfigEntity entity,
                                           AiUseCaseConfigRequest request,
                                           AiUseCaseConfigEntity existing) {
        AiUseCaseDefinitions.Definition definition = AiUseCaseDefinitions.require(
                requireText(request.getUseCaseCode(), "AI 任务编码不能为空")
        );
        AiProviderConfigEntity provider = aiProviderConfigService.requireExisting(request.getProviderConfigId());
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        if (enabled && !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new IllegalArgumentException("AI 接入配置已停用，不能绑定任务");
        }
        definition.requireChannel(provider.getChannelType());
        String promptTemplate = requireText(request.getPromptTemplate(), "提示词模板不能为空");
        entity.setUseCaseCode(requireText(request.getUseCaseCode(), "AI 任务编码不能为空"));
        entity.setUseCaseName(requireText(request.getUseCaseName(), "AI 任务名称不能为空"));
        entity.setDomain(requireText(request.getDomain(), "AI 任务领域不能为空").toUpperCase(Locale.ROOT));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setProviderConfigId(request.getProviderConfigId());
        entity.setModel(trimToNull(request.getModel()));
        entity.setReasoningLevel(upperOrNull(request.getReasoningLevel()));
        entity.setSpeedMode(upperOrNull(request.getSpeedMode()));
        entity.setTimeoutSeconds(positiveOrNull(request.getTimeoutSeconds()));
        entity.setJsonSchemaEnabled(request.getJsonSchemaEnabled() == null || request.getJsonSchemaEnabled());
        entity.setSchemaClasspath(trimToNull(request.getSchemaClasspath()));
        validateSchemaClasspath(entity.getSchemaClasspath());
        entity.setPromptTemplate(promptTemplate);
        entity.setPromptVariablesDesc(trimToNull(request.getPromptVariablesDesc()));
        entity.setPromptChecksum(sha256(promptTemplate));
        entity.setPromptVersion(resolvePromptVersion(request, existing, entity.getPromptChecksum()));
        entity.setEnabled(enabled);
        entity.setRemark(trimToNull(request.getRemark()));
        if (enabled) {
            validateEffectiveConfig(entity, provider);
        }
        return entity;
    }

    private void validateEffectiveConfig(AiUseCaseConfigEntity entity, AiProviderConfigEntity provider) {
        if (firstText(entity.getModel(), provider.getDefaultModel()) == null) {
            throw new IllegalArgumentException("AI 任务最终生效模型不能为空，请配置任务覆盖值或通道默认值");
        }
        if (firstText(entity.getReasoningLevel(), provider.getDefaultReasoningLevel()) == null) {
            throw new IllegalArgumentException("AI 任务最终生效推理强度不能为空，请配置任务覆盖值或通道默认值");
        }
        if (firstText(entity.getSpeedMode(), provider.getDefaultSpeedMode()) == null) {
            throw new IllegalArgumentException("AI 任务最终生效速度模式不能为空，请配置任务覆盖值或通道默认值");
        }
        if (firstPositive(entity.getTimeoutSeconds(), provider.getCallTimeoutSeconds()) == null) {
            throw new IllegalArgumentException("AI 任务最终生效总调用超时不能为空，请配置任务覆盖值或通道默认值");
        }
    }

    private void validateSchemaClasspath(String path) {
        if (path == null) return;
        if (path.startsWith("/") || path.contains("..") || !path.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("Schema 路径必须是安全的 classpath JSON 相对路径");
        }
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("Schema classpath 资源不存在: " + path);
            new ObjectMapper().readTree(input.readAllBytes());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Schema 资源不是合法 JSON: " + path, ex);
        }
    }

    private Integer resolvePromptVersion(AiUseCaseConfigRequest request,
                                         AiUseCaseConfigEntity existing,
                                         String promptChecksum) {
        if (existing == null) {
            return request.getPromptVersion() == null || request.getPromptVersion() <= 0 ? 1 : request.getPromptVersion();
        }
        int currentVersion = existing.getPromptVersion() == null || existing.getPromptVersion() <= 0 ? 1 : existing.getPromptVersion();
        if (promptChecksum != null && promptChecksum.equals(existing.getPromptChecksum())) {
            return currentVersion;
        }
        return currentVersion + 1;
    }

    private Integer defaultPositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private Integer positiveOrNull(Integer value) { return value != null && value > 0 ? value : null; }
    private Integer firstPositive(Integer first, Integer second) {
        return first != null && first > 0 ? first : (second != null && second > 0 ? second : null);
    }
    private String firstText(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
    }

    @Transactional
    public AiUseCaseConfigResponse saveForManage(AiUseCaseConfigRequest request, String operator, String ip) {
        if (request == null) throw new IllegalArgumentException("AI useCase 配置不能为空");
        if (request.getId() != null) return update(request.getId(), request, operator, ip);
        AiUseCaseConfigEntity existing = aiUseCaseConfigMapper.findEntityByCode(requireText(request.getUseCaseCode(), "AI useCase 编码不能为空"));
        return existing == null ? create(request, operator, ip) : update(existing.getId(), request, operator, ip);
    }

    public AiUseCaseConfigResponse saveForManage(AiUseCaseConfigRequest request) {
        return saveForManage(request, null, null);
    }

    private void audit(String action,
                       AiUseCaseConfigEntity before,
                       AiUseCaseConfigEntity after,
                       String operator,
                       String ip) {
        if (auditService == null || operator == null || operator.isBlank()) {
            return;
        }
        auditService.operation(
                operator,
                "CREATE".equals(action) ? "system:ai-config:create" : "system:ai-config:update",
                action,
                "AI_USE_CASE_CONFIG",
                String.valueOf(after.getId()),
                auditSnapshot(before),
                auditSnapshot(after),
                "SUCCESS",
                null,
                ip
        );
    }

    private String auditSnapshot(AiUseCaseConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return "useCaseCode=" + entity.getUseCaseCode()
                + ", providerConfigId=" + entity.getProviderConfigId()
                + ", model=" + entity.getModel()
                + ", promptVersion=" + entity.getPromptVersion()
                + ", enabled=" + entity.getEnabled();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    private String upperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
