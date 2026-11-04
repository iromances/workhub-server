package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentInspectionStream(String id, String taskId, String sourceType, String businessLineCode,
                                    String environmentCode, long afterId, long upperId, long sweepCount,
                                    LocalDateTime updatedAt) { }
