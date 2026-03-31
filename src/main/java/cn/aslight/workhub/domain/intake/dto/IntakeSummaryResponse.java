package cn.aslight.workhub.domain.intake.dto;

import java.time.LocalDateTime;

public record IntakeSummaryResponse(Long id,
                                    String sourceType,
                                    String sourceChannel,
                                    String senderName,
                                    LocalDateTime receivedAt,
                                    String category,
                                    String approvalCode,
                                    String requirementName,
                                    String plannedDueDate,
                                    String status,
                                    String preview,
                                    Long convertedWorkItemId) {
}
