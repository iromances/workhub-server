package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

public record AutonomyProposalEntity(String id, String caseId, String runId, String requestKey, String requestHash,
                                     long caseVersion, String proposalJson, LocalDateTime createdAt) { }
