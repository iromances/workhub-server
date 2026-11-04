package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentTaskResponse(String id, String agentId, AgentTaskSpecification specification,
                                String requestedBy, AgentScheduleRequest schedule,
                                LocalDateTime nextRunAt, long version,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
}
