package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** AI 判断与平台检查分别保存，证据引用不可被后续采样覆盖。 */
public record AgentVerificationSample(String id, String verificationId, String checkId, long sampleOrder,
        String requestKey, String requestHash, String assessment, String reason, boolean historicalDataPending,
        String evidenceJson, String evidenceHash, LocalDateTime createdAt) { }
