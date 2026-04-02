package cn.aslight.workhub.model.workitem;

import java.time.LocalDateTime;

/**
 * WorkItemTransition 响应模型。
 */
public record WorkItemTransitionResponse(Long id,
                                         String fromStatus,
                                         String toStatus,
                                         String reason,
                                         String operatorUserName,
                                         LocalDateTime createdAt) {
}
