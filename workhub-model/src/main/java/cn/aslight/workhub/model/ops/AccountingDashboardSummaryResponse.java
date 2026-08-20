package cn.aslight.workhub.model.ops;

public record AccountingDashboardSummaryResponse(Integer monitorCount,
                                                 Integer runningCount,
                                                 Integer successCount,
                                                 Integer warningCount,
                                                 Integer errorCount,
                                                 Integer anomalyCount) {
}
