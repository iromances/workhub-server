package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiProviderConfigRequest;
import cn.aslight.workhub.model.ai.AiProviderConnectionTestResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 使用当前尚未保存的单通道表单执行最小真实模型调用。
 */
@Service
public class AiProviderConnectionTestService {

    private final AiProviderConfigService providerConfigService;
    private final AiGatewayClient aiGatewayClient;
    private final SystemAuditService auditService;

    public AiProviderConnectionTestService(AiProviderConfigService providerConfigService,
                                           AiGatewayClient aiGatewayClient,
                                           SystemAuditService auditService) {
        this.providerConfigService = providerConfigService;
        this.aiGatewayClient = aiGatewayClient;
        this.auditService = auditService;
    }

    public AiProviderConnectionTestResponse test(AiProviderConfigRequest request,
                                                 String operator,
                                                 String ip) {
        AiProviderConfigService.ConnectionTestContext context = providerConfigService.prepareConnectionTest(request);
        AiProviderConfigEntity provider = context.provider();
        AiGatewayResult result = aiGatewayClient.testConnection(provider, context.apiKey());
        String message = result.succeeded() ? "连接成功，模型已返回有效响应" : safeFailure(result.failureSummary());

        auditService.operation(
                operator,
                request.getId() == null ? "system:ai-config:create" : "system:ai-config:update",
                "TEST_CONNECTION",
                "AI_PROVIDER_CONFIG",
                request.getId() == null ? "TEMP" : String.valueOf(request.getId()),
                null,
                auditSummary(provider),
                result.succeeded() ? "SUCCESS" : "FAILURE",
                result.succeeded() ? null : message,
                ip
        );
        return new AiProviderConnectionTestResponse(
                result.succeeded(),
                provider.getChannelType(),
                result.model(),
                result.durationMs(),
                message
        );
    }

    private String auditSummary(AiProviderConfigEntity provider) {
        return "连通测试 channel=" + provider.getChannelType()
                + ", protocol=" + provider.getApiProtocol()
                + ", model=" + provider.getDefaultModel();
    }

    private String safeFailure(String failureSummary) {
        String normalized = failureSummary == null ? "" : failureSummary.replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized")
                || lower.contains("forbidden") || lower.contains("认证")) {
            return "连接失败：认证未通过，请检查 API Key 或访问权限";
        }
        if (lower.contains("404") || lower.contains("not found")) {
            return "连接失败：接口地址或模型不存在，请检查 Base URL 和模型";
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时")) {
            return "连接失败：调用超时，请检查网络和超时配置";
        }
        if (lower.contains("unknownhost") || lower.contains("name or service not known") || lower.contains("域名")) {
            return "连接失败：域名解析失败，请检查 Base URL";
        }
        if (lower.contains("connect") || lower.contains("connection refused") || lower.contains("连接")) {
            return "连接失败：无法连接目标服务，请检查地址和网络";
        }
        if (lower.contains("codex cli 未启用")) {
            return "连接失败：Codex CLI 未启用";
        }
        if (lower.contains("cli") && (lower.contains("执行失败") || lower.contains("cannot run program")
                || lower.contains("no such file"))) {
            return "连接失败：CLI 命令无法执行，请检查命令和运行环境";
        }
        if (lower.contains("工作目录")) {
            return "连接失败：CLI 工作目录不存在或不可访问";
        }
        if (lower.contains("返回内容不符合预期") || lower.contains("结构化")) {
            return "连接失败：模型未返回预期的结构化结果";
        }
        return "连接失败：请检查当前通道配置和服务日志";
    }
}
