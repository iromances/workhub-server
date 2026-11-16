package cn.aslight.workhub.model.ops;

/** 单个子应用库的 MQ 消息窗口统计；计数口径见 MqMonitorCollector，errorMessage 非空表示该库采集失败。 */
public record MqDatabaseStatsResponse(String databaseName,
                                     long sendTotalCount,
                                     long sendSuccessCount,
                                     long sendFailedCount,
                                     long sendPendingCount,
                                     long receiveTotalCount,
                                     long receiveSuccessCount,
                                     long receiveFailedCount,
                                     String latestActivityAt,
                                     String errorMessage) {
}
