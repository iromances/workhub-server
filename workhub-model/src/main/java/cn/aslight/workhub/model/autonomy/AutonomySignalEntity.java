package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

public record AutonomySignalEntity(String id, String signalKey, String caseId, String observationId,
                                   String evidencePointer, String sourceEventId, String sourceVersion,
                                   String evidenceJson, LocalDateTime occurredAt, LocalDateTime observedAt) { }
