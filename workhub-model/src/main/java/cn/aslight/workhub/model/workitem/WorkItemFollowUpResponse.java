package cn.aslight.workhub.model.workitem;

import java.time.LocalDateTime;

/**
 * WorkItemFollowUp 响应模型。
 */
public record WorkItemFollowUpResponse(Long id,
                                       String content,
                                       String operatorUserName,
                                       LocalDateTime createdAt) {
}
