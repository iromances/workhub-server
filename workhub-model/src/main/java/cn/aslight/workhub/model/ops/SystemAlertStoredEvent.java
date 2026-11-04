package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

/** 采集时读回的实际记录；只通过范围及来源唯一键定位。 */
public record SystemAlertStoredEvent(Long id, String businessLineCode, String environmentCode, String serviceName,
        String level, String eventCategory, String title, String message, String errorType, String stackTrace,
        String traceId, String requestId, LocalDateTime occurredAt, String sourceType, String sourceEventId) { }
