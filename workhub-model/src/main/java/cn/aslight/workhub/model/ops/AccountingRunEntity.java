package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record AccountingRunEntity(Long id,
                                  String runNo,
                                  String idempotencyKey,
                                  Long configId,
                                  String businessLineCode,
                                  String businessLineName,
                                  String systemName,
                                  String displayName,
                                  String triggerType,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  String ruleCodes,
                                  String status,
                                  Integer ruleTotal,
                                  Integer passedCount,
                                  Integer warningCount,
                                  Integer failedCount,
                                  Integer skippedCount,
                                  Integer anomalyCount,
                                  String requestedBy,
                                  LocalDateTime startedAt,
                                  LocalDateTime finishedAt,
                                  String errorMessage,
                                  LocalDateTime createdAt) {
}
