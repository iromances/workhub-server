package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 需求待办响应模型。
 */
public record IntakeTodoResponse(Long id,
                                 Long intakeId,
                                 String title,
                                 String content,
                                 String status,
                                 String assigneeUserName,
                                 LocalDateTime plannedAt,
                                 LocalDateTime completedAt,
                                 String processResult,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
}
