package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record SystemAlertEventResponse(Long id,
                                       String businessLineCode,
                                       String environmentCode,
                                       String subsystemName,
                                       String serviceName,
                                       String level,
                                       String title,
                                       String message,
                                       String errorType,
                                       String stackTrace,
                                       String traceId,
                                       String requestId,
                                       LocalDateTime occurredAt,
                                       String sourceType) {
}
