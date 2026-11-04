package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 人工审核的不可变发布授权；请求和参数在审核时即固定。 */
public record AgentReleaseApprovalEntity(String id, String requestId, String runId, String changeSetId,
        String manifestHash, String bindingJson, String bindingHash, String parametersJson, String parametersHash,
        String decision, String reviewedBy, String comment, String snapshotJson, LocalDateTime reviewedAt) { }
