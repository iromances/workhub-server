package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record SystemAlertCleanupTaskResponse(
        Long id,
        String taskNo,
        String messageKeyword,
        String businessLineCode,
        String environmentCode,
        String serviceName,
        String level,
        String eventCategory,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        Long ruleId,
        Long maxEventId,
        Long processedEventId,
        Long deletedEventCount,
        Long deletedNotificationCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime updatedAt) {
}
