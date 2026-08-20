package cn.aslight.workhub.model.ai;

/** asset 历史全局 AI 配置兼容对象。 */
public record AiGatewayConfig(String queryKey, String provider, String model,
                              String reasoningLevel, String speedMode, Integer timeoutSeconds,
                              Boolean jsonSchemaEnabled, String cliCommand, String cliWorkingDirectory,
                              String apiBaseUrl, String apiKey, Integer connectTimeoutSeconds,
                              Integer readTimeoutSeconds, String siteUrl, String appName) {
}
