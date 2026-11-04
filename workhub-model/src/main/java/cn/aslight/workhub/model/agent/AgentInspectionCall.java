package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentInspectionCall(String runId, String toolKey, String requestHash, String resultJson,
                                  LocalDateTime createdAt) { }
