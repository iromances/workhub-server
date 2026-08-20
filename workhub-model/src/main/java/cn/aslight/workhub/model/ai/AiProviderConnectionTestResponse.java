package cn.aslight.workhub.model.ai;

/**
 * AI 接入配置当前表单连通性测试结果，不返回模型原文或敏感凭据。
 */
public record AiProviderConnectionTestResponse(boolean success,
                                               String channelType,
                                               String model,
                                               long elapsedMillis,
                                               String message) {
}
