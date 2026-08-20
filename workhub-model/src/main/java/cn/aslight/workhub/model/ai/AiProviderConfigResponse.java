package cn.aslight.workhub.model.ai;

/**
 * AI 接入配置响应。
 */
public record AiProviderConfigResponse(Long id,
                                       String providerCode,
                                       String providerName,
                                       String channelType,
                                       String vendor,
                                       String modelProvider,
                                       String defaultModel,
                                       String defaultReasoningLevel,
                                       String defaultSpeedMode,
                                       String apiProtocol,
                                       String apiBaseUrl,
                                       String apiKey,
                                       String cliCommand,
                                       String cliWorkingDirectory,
                                       Integer connectTimeoutSeconds,
                                       Integer readTimeoutSeconds,
                                       Integer callTimeoutSeconds,
                                       String siteUrl,
                                       String appName,
                                       Boolean enabled,
                                       String remark) {
}
