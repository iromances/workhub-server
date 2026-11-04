package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentInspectionAnalysis(String id, long itemId, String runId, String observationId,
                                      String outcome, String evidencePointer, String reason, String caseId, LocalDateTime createdAt) { }
