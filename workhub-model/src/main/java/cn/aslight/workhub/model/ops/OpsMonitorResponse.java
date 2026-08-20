package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record OpsMonitorResponse(Long id,
                                 String monitorType,
                                 String monitorKey,
                                 String businessLineCode,
                                 String businessLineName,
                                 String environmentCode,
                                 String name,
                                 String adminBaseUrl,
                                 String username,
                                 Boolean passwordConfigured,
                                 String xxlJobDatabaseName,
                                 String executorAppName,
                                 String jobHandler,
                                 String jobDesc,
                                 String mqTopic,
                                 String mqConsumerGroup,
                                 Integer mqLagThreshold,
                                 Boolean enabled,
                                 String lastStatus,
                                 String lastStatusName,
                                 String lastMessage,
                                 LocalDateTime lastCheckedAt,
                                 String remark,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
}
