package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentReleaseReviewEntity(String id, String planId, String requestKey, String decision,
        String reviewedBy, String comment, String snapshotHash, long expectedVersion, LocalDateTime createdAt) { }
