package cn.aslight.workhub.model.ops;

import java.time.Instant;

public record ElkSystemAlertLog(String sourceEventId,
                                String serviceName,
                                String level,
                                String title,
                                String message,
                                String errorType,
                                String stackTrace,
                                String traceId,
                                String requestId,
                                Instant occurredAt) {
}
