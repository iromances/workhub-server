package cn.aslight.workhub.model.workitem;

import java.time.LocalDateTime;

/**
 * WorkItemDetail 响应模型。
 */
public record WorkItemDetailResponse(Long id,
                                     String no,
                                     Long projectId,
                                     String projectName,
                                     Long sprintId,
                                     String sprintName,
                                     Long releaseId,
                                     String releaseName,
                                     String type,
                                     String title,
                                     String description,
                                     String sourceType,
                                     String sourceChannel,
                                     String priority,
                                     String urgency,
                                     String status,
                                     String creatorUserName,
                                     String ownerUserName,
                                     String followerUserName,
                                     String proposerName,
                                     String acceptanceCriteria,
                                     LocalDateTime plannedStartAt,
                                     LocalDateTime plannedEndAt,
                                     LocalDateTime finishedAt,
                                     LocalDateTime createdAt,
                                     LocalDateTime updatedAt) {
}
