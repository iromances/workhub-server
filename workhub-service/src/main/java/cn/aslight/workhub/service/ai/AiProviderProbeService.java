package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiProviderProbeRequest;
import cn.aslight.workhub.model.ai.AiProviderProbeResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.stereotype.Service;

/**
 * 使用无业务数据的最小结构化请求探测 AI 供应商连接。
 */
@Service
public class AiProviderProbeService {

    private final AiProviderConfigService aiProviderConfigService;
    private final AiGatewayClient aiGatewayClient;
    private final SystemAuditService auditService;

    public AiProviderProbeService(AiProviderConfigService aiProviderConfigService,
                                  AiGatewayClient aiGatewayClient,
                                  SystemAuditService auditService) {
        this.aiProviderConfigService = aiProviderConfigService;
        this.aiGatewayClient = aiGatewayClient;
        this.auditService = auditService;
    }

    public AiProviderProbeResponse probe(Long providerId,
                                         AiProviderProbeRequest request,
                                         String operator,
                                         String ip) {
        AiProviderConfigEntity provider = aiProviderConfigService.requireExisting(providerId);
        String model = requireText(request == null ? null : request.getModel(), "探针模型不能为空");
        AiGatewayResult result = aiGatewayClient.probe(providerId, model);
        long durationMs = result.durationMs();
        boolean success = result.succeeded();
        String message = success ? "AI Provider 连接探针通过" : safeFailure(result.failureSummary());

        auditService.operation(
                operator,
                "system:ai-config:update",
                "PROBE",
                "AI_PROVIDER_CONFIG",
                String.valueOf(providerId),
                null,
                "供应商探针 providerCode=" + provider.getProviderCode() + ", model=" + model,
                success ? "SUCCESS" : "FAILURE",
                success ? null : message,
                ip
        );
        return new AiProviderProbeResponse(
                providerId,
                provider.getProviderCode(),
                model,
                success,
                durationMs,
                message
        );
    }

    private String safeFailure(String failureSummary) {
        String normalized = failureSummary == null ? "供应商未返回预期探针结果" : failureSummary.trim();
        if (normalized.isEmpty()) {
            return "供应商未返回预期探针结果";
        }
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
