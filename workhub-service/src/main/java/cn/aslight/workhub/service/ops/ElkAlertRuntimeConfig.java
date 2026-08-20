package cn.aslight.workhub.service.ops;

/**
 * 单次 ELK 采集使用的完整运行时配置快照。
 */
public record ElkAlertRuntimeConfig(
        boolean enabled,
        String baseUrl,
        String indexPattern,
        String authType,
        String apiKey,
        String username,
        String password,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        int pageSize,
        int maxEventsPerRun,
        int initialLookbackMinutes,
        String pitKeepAlive,
        String timestampField,
        String serviceQueryField,
        String serviceSourceField,
        String environmentQueryField,
        String levelQueryField,
        String levelSourceField,
        String errorLevel,
        String messageSourceField,
        String loggerSourceField,
        String errorTypeSourceField,
        String stackTraceSourceField,
        String traceIdSourceField,
        String requestIdSourceField
) {
}
