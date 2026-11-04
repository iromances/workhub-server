package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentChangeSetEntity(String id, String runId, String taskId, long revision, String requestKey,
                                   String requestHash, String manifestHash, String manifestJson, LocalDateTime createdAt) { }
