package cn.aslight.workhub.model.intake;

/**
 * 需求关联正式工作项响应。
 */
public record IntakeRelatedWorkItemResponse(Long id,
                                            String no,
                                            String title,
                                            String type,
                                            String projectName,
                                            String ownerUserName,
                                            String status,
                                            String priority,
                                            Integer draftIndex,
                                            String relationType) {
}
