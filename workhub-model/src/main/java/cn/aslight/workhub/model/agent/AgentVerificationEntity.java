package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 获批发布后的复查；标准与实际部署凭据固定，结论和等待时间独立持久化。 */
public record AgentVerificationEntity(String id, String planId, String runId, String changeSetId,
        String manifestHash, String stage, String approvedSnapshotHash, String snapshotJson, String snapshotHash,
        String status, String artifactId, String artifactHash, String conclusionJson, LocalDateTime nextCheckAt,
        long version, LocalDateTime createdAt, LocalDateTime updatedAt) { }
