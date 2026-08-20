package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;

/**
 * WorkHub 系统内部唯一 AI 调用入口。
 */
public interface AiGatewayClient {

    boolean isConfigured(String useCaseCode);

    AiGatewayResult execute(AiGatewayRequest request);

    AiGatewayResult executeStructured(AiGatewayRequest request);

    /**
     * 使用固定无业务数据提示词探测指定 Provider。
     */
    AiGatewayResult probe(Long providerId, String model);

    /**
     * 使用当前表单构造的临时 Provider 执行 API 或 CLI 连通测试。
     */
    default AiGatewayResult testConnection(AiProviderConfigEntity provider, String apiKey) {
        throw new UnsupportedOperationException("当前 AI 网关实现不支持临时连通性测试");
    }
}
