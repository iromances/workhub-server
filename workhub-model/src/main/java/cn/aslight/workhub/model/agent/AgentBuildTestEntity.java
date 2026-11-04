package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 独立构建测试的持久意图；未知执行继续占用原资源，不能当作未提交。 */
public record AgentBuildTestEntity(String id, String runId, String changeSetId, String bindingId, long attempt,
        String requestKey, String requestHash, String manifestHash, String bindingJson, String bindingHash,
        String parametersJson, String parametersHash, String status, Long queueId, Long buildNumber,
        String buildResult, String receiptJson, String errorMessage, String ownerToken,
        LocalDateTime leaseUntil, LocalDateTime nextCheckAt, boolean wakePending, long version,
        LocalDateTime createdAt, LocalDateTime updatedAt) { }
