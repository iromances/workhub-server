package cn.aslight.workhub.model.project;

/**
 * ProjectSummary 响应模型。
 */
public record ProjectSummaryResponse(Long id,
                                     String businessLineCode,
                                     String businessLineName,
                                     String code,
                                     String name,
                                     String type,
                                     String group,
                                     String ownerUserName,
                                     String status) {
}
