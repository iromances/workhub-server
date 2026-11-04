package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentRunEntity(String id, String taskId, String agentId, String triggerKey,
                             String requestedBy, String configurationJson, String specificationJson, String providerBindingJson,
                             String status, int stepCount, long elapsedMillis,
                             boolean cancelRequested, String ownerToken, LocalDateTime leaseUntil,
                             String externalHandle, String resultText, String errorMessage,
                             long version, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
