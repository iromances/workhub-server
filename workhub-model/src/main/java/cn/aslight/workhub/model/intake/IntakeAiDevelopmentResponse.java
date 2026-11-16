package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 需求 AI 分支开发执行状态响应。
 */
public record IntakeAiDevelopmentResponse(Long intakeId,
                                          String developmentBranch,
                                          String agentId,
                                          String agentTaskId,
                                          String executionStatus,
                                          String executionMessage,
                                          LocalDateTime updatedAt) {
}
