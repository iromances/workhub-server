package cn.aslight.workhub.model.ai;

/**
 * AI 供应商连接探针响应，不返回供应商原始正文或敏感凭据。
 */
public record AiProviderProbeResponse(Long providerId,
                                      String providerCode,
                                      String model,
                                      boolean success,
                                      long durationMs,
                                      String message) {
}
