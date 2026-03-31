package cn.aslight.workhub.domain.workitem.dto;

import java.time.LocalDateTime;

public record WorkItemFollowUpResponse(Long id,
                                       String content,
                                       String operatorUserName,
                                       LocalDateTime createdAt) {
}
