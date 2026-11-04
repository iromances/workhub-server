package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentVerificationCall(String runId, String toolKey, String toolCode, String requestHash,
        String resultJson, LocalDateTime createdAt) { }
