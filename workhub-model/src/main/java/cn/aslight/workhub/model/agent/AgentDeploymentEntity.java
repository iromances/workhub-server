package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** Jenkins 写入意图和持久化句柄；UNKNOWN 保留资源占用，不能重发。 */
public record AgentDeploymentEntity(String id, String approvalId, String runId, String changeSetId, String manifestHash,
        String bindingJson, String bindingHash, String parametersJson, String parametersHash,
        String status, Long queueId, Long buildNumber, String buildResult, String receiptJson,
        String errorMessage, String ownerToken, LocalDateTime leaseUntil, LocalDateTime nextCheckAt,
        long version, LocalDateTime createdAt, LocalDateTime updatedAt) { }
