package cn.aslight.workhub.domain.workitem.dto;

public record WorkItemSummaryResponse(Long id,
                                      String no,
                                      String title,
                                      String type,
                                      String projectName,
                                      String ownerUserName,
                                      String status,
                                      String priority) {
}
