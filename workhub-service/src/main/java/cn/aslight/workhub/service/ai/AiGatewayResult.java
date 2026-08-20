package cn.aslight.workhub.service.ai;

/**
 * AI 网关执行结果。失败时不携带供应商原始响应、提示词或密钥。
 */
public record AiGatewayResult(boolean succeeded,
                              String output,
                              String failureSummary,
                              String providerCode,
                              String model,
                              long durationMs) {

    public static AiGatewayResult succeeded(String output,
                                            String providerCode,
                                            String model,
                                            long durationMs) {
        return new AiGatewayResult(true, output, null, providerCode, model, durationMs);
    }

    public static AiGatewayResult failed(String failureSummary,
                                         String providerCode,
                                         String model,
                                         long durationMs) {
        return new AiGatewayResult(false, null, failureSummary, providerCode, model, durationMs);
    }
}
