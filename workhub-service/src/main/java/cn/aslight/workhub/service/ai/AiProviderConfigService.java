package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.dao.ai.AiProviderConfigMapper;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiProviderConfigRequest;
import cn.aslight.workhub.model.ai.AiProviderConfigResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * AI 接入配置服务。
 */
@Service
public class AiProviderConfigService {

    private static final String CHANNEL_API = "API";
    private static final String CHANNEL_CLI = "CLI";
    private static final String VENDOR_XIANXINGTONG = "XIANXINGTONG";
    private static final String PROTOCOL_OPENAI_RESPONSES = "OPENAI_RESPONSES";
    private static final String XIANXINGTONG_HOST = "aiserver.thchengtay.com";
    private static final String XIANXINGTONG_BASE_PATH = "/v1";

    private final AiProviderConfigMapper aiProviderConfigMapper;
    private final AiCredentialCryptoService credentialCryptoService;
    private final SystemAuditService auditService;

    public AiProviderConfigService(AiProviderConfigMapper aiProviderConfigMapper,
                                   AiCredentialCryptoService credentialCryptoService) {
        this(aiProviderConfigMapper, credentialCryptoService, null);
    }

    @Autowired
    public AiProviderConfigService(AiProviderConfigMapper aiProviderConfigMapper,
                                   AiCredentialCryptoService credentialCryptoService,
                                   SystemAuditService auditService) {
        this.aiProviderConfigMapper = aiProviderConfigMapper;
        this.credentialCryptoService = credentialCryptoService;
        this.auditService = auditService;
    }

    public List<AiProviderConfigResponse> list() {
        return aiProviderConfigMapper.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AiProviderConfigResponse> listForManage() { return list(); }
    public AiProviderConfigResponse detailForManage(Long id) { return detail(id); }
    public AiProviderConfigEntity getById(Long id) { return requireExisting(id); }
    public AiProviderConfigEntity getEnabledById(Long id) {
        AiProviderConfigEntity entity = requireExisting(id);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("AI 接入配置不存在或已停用");
        }
        return entity;
    }

    public AiProviderConfigResponse detail(Long id) {
        return toResponse(requireExisting(id));
    }

    public AiProviderConfigEntity requireExisting(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("AI 接入配置 ID 不能为空");
        }
        AiProviderConfigEntity entity = aiProviderConfigMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("AI 接入配置不存在");
        }
        return entity;
    }

    public AiProviderConfigEntity requireByCode(String providerCode) {
        String normalizedCode = requireText(providerCode, "AI 接入配置编码不能为空");
        AiProviderConfigEntity entity = aiProviderConfigMapper.findByCode(normalizedCode);
        if (entity == null) {
            throw new IllegalArgumentException("AI 接入配置不存在");
        }
        return entity;
    }

    /**
     * 供服务端运行时解析真实 API Key，响应 DTO 不得调用该方法回传明文。
     */
    public String resolveApiKey(Long id) {
        return credentialCryptoService.decrypt(requireExisting(id).getApiKey());
    }

    /**
     * 将页面当前单通道表单转换为仅供本次连通测试使用的临时配置，不执行任何持久化。
     */
    public ConnectionTestContext prepareConnectionTest(AiProviderConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AI 接入配置不能为空");
        }
        AiProviderConfigEntity existing = request.getId() == null ? null : requireExisting(request.getId());
        String requestedChannel = requireText(request.getChannelType(), "调用通道不能为空").toUpperCase(Locale.ROOT);
        if (existing != null && !requestedChannel.equalsIgnoreCase(existing.getChannelType())) {
            throw new IllegalArgumentException("AI 接入配置 ID 与调用通道不匹配");
        }

        AiProviderConfigEntity provider = toEntity(new AiProviderConfigEntity(), request, existing);
        provider.setId(request.getId());
        provider.setEnabled(true);
        validateChannelConfig(provider);
        String apiKey = CHANNEL_API.equals(requestedChannel)
                ? resolveApiKeyForConnectionTest(request.getApiKey(), existing)
                : null;
        return new ConnectionTestContext(provider, apiKey);
    }

    @Transactional
    public AiProviderConfigResponse create(AiProviderConfigRequest request) {
        return createInternal(request, null, null);
    }

    @Transactional
    public AiProviderConfigResponse create(AiProviderConfigRequest request, String operator, String ip) {
        return createInternal(request, operator, ip);
    }

    private AiProviderConfigResponse createInternal(AiProviderConfigRequest request, String operator, String ip) {
        ensureCodeAvailable(request.getProviderCode(), null);
        AiProviderConfigEntity entity = toEntity(new AiProviderConfigEntity(), request, null);
        entity.setCreateBy(operator);
        entity.setModifyBy(operator);
        validateChannelConfig(entity);
        aiProviderConfigMapper.insert(entity);
        AiProviderConfigResponse response = detail(entity.getId());
        audit("CREATE", null, entity, operator, ip);
        return response;
    }

    @Transactional
    public AiProviderConfigResponse update(Long id, AiProviderConfigRequest request) {
        return updateInternal(id, request, null, null);
    }

    @Transactional
    public AiProviderConfigResponse update(Long id,
                                           AiProviderConfigRequest request,
                                           String operator,
                                           String ip) {
        return updateInternal(id, request, operator, ip);
    }

    private AiProviderConfigResponse updateInternal(Long id,
                                                    AiProviderConfigRequest request,
                                                    String operator,
                                                    String ip) {
        AiProviderConfigEntity existing = requireExisting(id);
        String requestedCode = requireText(request.getProviderCode(), "接入配置编码不能为空");
        if (!requestedCode.equals(existing.getProviderCode())) {
            throw new IllegalArgumentException("AI 接入配置编码创建后不可修改");
        }
        ensureCodeAvailable(requestedCode, id);
        AiProviderConfigEntity entity = toEntity(new AiProviderConfigEntity(), request, existing);
        entity.setId(id);
        entity.setVersion(existing.getVersion());
        entity.setModifyBy(operator);
        validateChannelConfig(entity);
        if (aiProviderConfigMapper.update(entity) != 1) {
            throw new IllegalStateException("AI 接入配置已被其他操作修改，请刷新后重试");
        }
        AiProviderConfigResponse response = detail(id);
        audit("UPDATE", existing, entity, operator, ip);
        return response;
    }

    private void ensureCodeAvailable(String providerCode, Long currentId) {
        String normalizedCode = requireText(providerCode, "接入配置编码不能为空");
        AiProviderConfigEntity existing = aiProviderConfigMapper.findByCode(normalizedCode);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("AI 接入配置编码已存在");
        }
    }

    private AiProviderConfigEntity toEntity(AiProviderConfigEntity entity,
                                            AiProviderConfigRequest request,
                                            AiProviderConfigEntity existing) {
        String channelType = requireText(request.getChannelType(), "调用通道不能为空").toUpperCase(Locale.ROOT);
        entity.setProviderCode(requireText(request.getProviderCode(), "接入配置编码不能为空"));
        entity.setProviderName(requireText(request.getProviderName(), "接入配置名称不能为空"));
        entity.setChannelType(channelType);
        entity.setVendor(trimToNull(request.getVendor()));
        entity.setModelProvider(requireText(request.getModelProvider(), "模型厂商不能为空").toUpperCase(Locale.ROOT));
        entity.setDefaultModel(firstText(request.getDefaultModel(), existing == null ? null : existing.getDefaultModel()));
        entity.setDefaultReasoningLevel(upperOrExisting(request.getDefaultReasoningLevel(), existing == null ? null : existing.getDefaultReasoningLevel()));
        entity.setDefaultSpeedMode(upperOrExisting(request.getDefaultSpeedMode(), existing == null ? null : existing.getDefaultSpeedMode()));
        entity.setApiProtocol(trimToNull(request.getApiProtocol()));
        entity.setApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        entity.setApiKey(resolveApiKeyForStorage(request.getApiKey(), existing));
        entity.setCliCommand(trimToNull(request.getCliCommand()));
        entity.setCliWorkingDirectory(trimToNull(request.getCliWorkingDirectory()));
        entity.setConnectTimeoutSeconds(defaultPositive(request.getConnectTimeoutSeconds(), 30));
        entity.setReadTimeoutSeconds(defaultPositive(request.getReadTimeoutSeconds(), CHANNEL_CLI.equals(channelType) ? 300 : 300));
        entity.setCallTimeoutSeconds(defaultPositive(request.getCallTimeoutSeconds(), CHANNEL_CLI.equals(channelType) ? 1800 : 600));
        entity.setSiteUrl(trimToNull(request.getSiteUrl()));
        entity.setAppName(trimToNull(request.getAppName()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        return entity;
    }

    private void validateChannelConfig(AiProviderConfigEntity entity) {
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            return;
        }
        requireText(entity.getDefaultModel(), "通道默认模型不能为空");
        requireText(entity.getDefaultReasoningLevel(), "通道默认推理强度不能为空");
        requireText(entity.getDefaultSpeedMode(), "通道默认速度模式不能为空");
        if (entity.getCallTimeoutSeconds() == null || entity.getCallTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("通道默认总调用超时必须大于0秒");
        }
        if (!CHANNEL_API.equals(entity.getChannelType()) && !CHANNEL_CLI.equals(entity.getChannelType())) {
            throw new IllegalArgumentException("调用通道只支持 API 或 CLI");
        }
        if (CHANNEL_API.equals(entity.getChannelType())) {
            requireText(entity.getApiProtocol(), "API 通道协议不能为空");
            requireText(entity.getApiBaseUrl(), "API Base URL 不能为空");
            requireText(entity.getApiKey(), "API Key 不能为空");
            validateXianxingtongUrl(entity);
        }
        if (CHANNEL_CLI.equals(entity.getChannelType())) {
            requireText(entity.getCliCommand(), "CLI 命令不能为空");
        }
    }

    private AiProviderConfigResponse toResponse(AiProviderConfigEntity entity) {
        return new AiProviderConfigResponse(
                entity.getId(),
                entity.getProviderCode(),
                entity.getProviderName(),
                entity.getChannelType(),
                entity.getVendor(),
                entity.getModelProvider(),
                entity.getDefaultModel(),
                entity.getDefaultReasoningLevel(),
                entity.getDefaultSpeedMode(),
                entity.getApiProtocol(),
                entity.getApiBaseUrl(),
                credentialCryptoService.mask(credentialCryptoService.decrypt(entity.getApiKey())),
                entity.getCliCommand(),
                entity.getCliWorkingDirectory(),
                entity.getConnectTimeoutSeconds(),
                entity.getReadTimeoutSeconds(),
                entity.getCallTimeoutSeconds(),
                entity.getSiteUrl(),
                entity.getAppName(),
                entity.getEnabled(),
                entity.getRemark()
        );
    }

    private String resolveApiKeyForStorage(String requestedApiKey, AiProviderConfigEntity existing) {
        String normalized = trimToNull(requestedApiKey);
        if (existing == null) {
            return credentialCryptoService.encrypt(normalized);
        }

        String existingStoredValue = trimToNull(existing.getApiKey());
        String existingRawValue = credentialCryptoService.decrypt(existingStoredValue);
        String existingMaskedValue = credentialCryptoService.mask(existingRawValue);
        if (normalized == null || "******".equals(normalized) || normalized.equals(existingMaskedValue)) {
            if (existingStoredValue == null) {
                return null;
            }
            return credentialCryptoService.isEncrypted(existingStoredValue)
                    ? existingStoredValue
                    : credentialCryptoService.encrypt(existingRawValue);
        }
        return credentialCryptoService.encrypt(normalized);
    }

    private String resolveApiKeyForConnectionTest(String requestedApiKey, AiProviderConfigEntity existing) {
        String normalized = trimToNull(requestedApiKey);
        if (existing == null) {
            if (normalized == null || "******".equals(normalized) || normalized.matches("^\\*{4,}[^*]{0,4}$")) {
                throw new IllegalArgumentException("新接入配置测试必须填写真实 API Key");
            }
            return normalized;
        }

        String existingRawValue = trimToNull(credentialCryptoService.decrypt(existing.getApiKey()));
        String existingMaskedValue = credentialCryptoService.mask(existingRawValue);
        if (normalized == null || "******".equals(normalized) || normalized.equals(existingMaskedValue)) {
            return requireText(existingRawValue, "已保存接入配置未维护 API Key");
        }
        return normalized;
    }

    private void validateXianxingtongUrl(AiProviderConfigEntity entity) {
        if (!VENDOR_XIANXINGTONG.equalsIgnoreCase(entity.getVendor())
                || !PROTOCOL_OPENAI_RESPONSES.equalsIgnoreCase(entity.getApiProtocol())) {
            return;
        }

        URI uri;
        try {
            uri = new URI(entity.getApiBaseUrl());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("先行通 OPENAI_RESPONSES Base URL 格式不合法", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("先行通 OPENAI_RESPONSES Base URL 必须使用 HTTPS");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("先行通 OPENAI_RESPONSES Base URL 禁止包含用户信息");
        }
        if (!XIANXINGTONG_HOST.equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("先行通 OPENAI_RESPONSES Base URL 域名必须是 " + XIANXINGTONG_HOST);
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("先行通 OPENAI_RESPONSES Base URL 端口只允许默认端口或 443");
        }
        String rawPath = uri.getRawPath();
        if (!XIANXINGTONG_BASE_PATH.equals(rawPath)
                && !(XIANXINGTONG_BASE_PATH + "/").equals(rawPath)) {
            throw new IllegalArgumentException("先行通 OPENAI_RESPONSES Base URL 路径必须是 /v1 或 /v1/");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("先行通 OPENAI_RESPONSES Base URL 禁止包含查询参数或片段");
        }
    }

    private Integer defaultPositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private void audit(String action,
                       AiProviderConfigEntity before,
                       AiProviderConfigEntity after,
                       String operator,
                       String ip) {
        if (auditService == null || operator == null || operator.isBlank()) {
            return;
        }
        auditService.operation(
                operator,
                "CREATE".equals(action) ? "system:ai-config:create" : "system:ai-config:update",
                action,
                "AI_PROVIDER_CONFIG",
                String.valueOf(after.getId()),
                auditSnapshot(before),
                auditSnapshot(after),
                "SUCCESS",
                null,
                ip
        );
    }

    private String auditSnapshot(AiProviderConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return "providerCode=" + entity.getProviderCode()
                + ", vendor=" + entity.getVendor()
                + ", protocol=" + entity.getApiProtocol()
                + ", baseUrl=" + entity.getApiBaseUrl()
                + ", enabled=" + entity.getEnabled();
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

    private String firstText(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
    }

    private String upperOrExisting(String first, String second) {
        String value = firstText(first, second);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    @Transactional
    public AiProviderConfigResponse saveForManage(AiProviderConfigRequest request, String operator, String ip) {
        if (request == null) {
            throw new IllegalArgumentException("AI 接入配置不能为空");
        }
        if (request.getId() != null) {
            return update(request.getId(), request, operator, ip);
        }
        AiProviderConfigEntity byCode = aiProviderConfigMapper.findByCode(requireText(request.getProviderCode(), "接入配置编码不能为空"));
        return byCode == null ? create(request, operator, ip) : update(byCode.getId(), request, operator, ip);
    }

    public AiProviderConfigResponse saveForManage(AiProviderConfigRequest request) {
        return saveForManage(request, null, null);
    }

    public record ConnectionTestContext(AiProviderConfigEntity provider, String apiKey) {
    }
}
