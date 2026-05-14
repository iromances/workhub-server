package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * IntakeSummary 响应模型。
 */
public record IntakeSummaryResponse(Long id,
                                    String sourceType,
                                    String sourceChannel,
                                    String senderName,
                                    String developmentOwnerUserName,
                                    LocalDateTime receivedAt,
                                    String demandStatus,
                                    String proposerName,
                                    String approvalCode,
                                    String submittedTime,
                                    String requirementType,
                                    String requirementDigest,
                                    String department,
                                    String requirementName,
                                    String requirementSummary,
                                    String businessLine,
                                    String remark,
                                    String estimatedEffort,
                                    String plannedDueDate,
                                    String actualEffort,
                                    String actualCompletedTime,
                                    String acceptanceTime,
                                    String releasedTime,
                                    String projectHint,
                                    String enrichmentStatus,
                                    String status,
                                    Long convertedWorkItemId) {
}
