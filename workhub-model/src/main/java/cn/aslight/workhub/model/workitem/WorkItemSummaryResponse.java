package cn.aslight.workhub.model.workitem;

/**
 * WorkItemSummary 响应模型。
 */
public record WorkItemSummaryResponse(Long id,
                                      String no,
                                      String title,
                                      String type,
                                      String projectName,
                                      String ownerUserName,
                                      String status,
                                      String priority) {
}
