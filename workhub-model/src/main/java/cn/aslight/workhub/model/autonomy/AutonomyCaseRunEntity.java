package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

public record AutonomyCaseRunEntity(String caseId, String runId, String relation, LocalDateTime createdAt) { }
