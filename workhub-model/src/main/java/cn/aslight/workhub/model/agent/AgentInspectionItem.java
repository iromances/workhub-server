package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentInspectionItem(long id, String streamId, long sourceId, String contentHash, String payloadJson,
                                  String status, String assignedRunId, String observationId,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) { }
