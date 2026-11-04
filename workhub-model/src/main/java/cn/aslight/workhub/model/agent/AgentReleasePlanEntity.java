package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 整组发布的不可变审核材料；状态与材料版本分开保存。 */
public record AgentReleasePlanEntity(String id, String runId, String changeSetId, String requestKey,
        String definitionHash, String manifestHash, String stage, String snapshotJson, String snapshotHash,
        String status, String createdBy, long version, LocalDateTime createdAt, LocalDateTime updatedAt) { }
