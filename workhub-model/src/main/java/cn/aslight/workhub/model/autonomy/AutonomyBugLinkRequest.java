package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

public record AutonomyBugLinkRequest(String caseId, String runId, String requestKey, String requestHash,
                                     String linkId, LocalDateTime createdAt) { }
