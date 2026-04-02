package cn.aslight.workhub.model.project;

/**
 * ProjectSummary 响应模型。
 */
public record ProjectSummaryResponse(Long id,
                                     String code,
                                     String name,
                                     String type,
                                     String ownerUserName,
                                     String status) {
}
