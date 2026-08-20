package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 工作台需求统计响应。
 */
public record IntakeDashboardResponse(String demandType,
                                      String demandTypeLabel,
                                      int businessLineRecordsCount,
                                      List<StageCard> demandCards,
                                      List<BusinessLineStats> businessLineStats,
                                      List<DashboardDemand> highlightedDemands,
                                      ProgressReport progressReport) {

    public record StageCard(String key,
                            String label,
                            List<String> statuses,
                            String note,
                            String className,
                            String tagClass,
                            int value) {
    }

    public record BusinessLineStats(String businessLineCode,
                                    String businessLine,
                                    int notStartedCount,
                                    int developingCount,
                                    int testingCount,
                                    int pendingReleaseCount,
                                    int totalCount,
                                    int demandCount,
                                    double demandRatio) {
    }

    public record DashboardDemand(Long id,
                                  String title,
                                  String businessLine,
                                  String businessLineCode,
                                  String demandStatus,
                                  String requirementType,
                                  String approvalCode,
                                  String proposerName,
                                  String receivedAt,
                                  String note,
                                  String groupLabel,
                                  String groupTagClass) {
    }

    public record ProgressReport(String title,
                                 String generatedAt,
                                 String statusNote,
                                 List<ProgressReportSummary> summary,
                                 List<ProgressReportReleasedDemand> weeklyReleasedDemands,
                                 List<ProgressReportDemand> pendingReleaseDemands) {
    }

    public record ProgressReportSummary(String key,
                                        String label,
                                        int value,
                                        List<String> statuses) {
    }

    public record ProgressReportDemand(Long id,
                                       String title,
                                       String businessLine,
                                       String demandStatus,
                                       String approvalCode,
                                       String proposerName,
                                       String receivedAt,
                                       List<ProgressReportDateItem> dateItems) {
    }

    public record ProgressReportDateItem(String label,
                                         String value) {
    }

    public record ProgressReportReleasedDemand(Long id,
                                               String title,
                                               String businessLine,
                                               String demandStatus,
                                               String requirementType,
                                               String approvalCode,
                                               String proposerName,
                                               String receivedAt,
                                               String releasedTime) {
    }
}
