package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentCodeOperationEntity(String workspaceId, String requestKey, String requestHash,
                                       String resultJson, LocalDateTime createdAt) { }
