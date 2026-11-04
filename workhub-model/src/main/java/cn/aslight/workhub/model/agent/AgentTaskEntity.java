package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentTaskEntity(String id, String agentId, String requestKey, String requestHash, String specificationJson,
                              String requestedBy, String scheduleMode, String cronExpression,
                              String zoneId, String misfirePolicy, boolean scheduleEnabled,
                              LocalDateTime nextRunAt, long version,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
