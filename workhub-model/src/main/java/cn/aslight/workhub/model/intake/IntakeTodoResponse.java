package cn.aslight.workhub.model.intake;

import cn.aslight.workhub.model.attachment.AttachmentResponse;

import java.time.LocalDateTime;
import java.util.List;

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
                                 LocalDateTime updatedAt,
                                 List<AttachmentResponse> screenshots) {
}
