package cn.aslight.workhub.domain.workitem.dto;

import java.time.LocalDateTime;

public record WorkItemTransitionResponse(Long id,
                                         String fromStatus,
                                         String toStatus,
                                         String reason,
                                         String operatorUserName,
                                         LocalDateTime createdAt) {
}
