package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentRunResponse(String id, String taskId, String agentId, String status,
                               int stepCount, long elapsedMillis, boolean cancelRequested,
                               String resultText, String errorMessage, boolean retryAllowed, long version,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
}
