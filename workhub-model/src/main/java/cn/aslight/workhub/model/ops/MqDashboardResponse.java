package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

/** MQ 监测面板：窗口统计汇总与逐库明细；messages 仅在明细查询时非空。 */
public record MqDashboardResponse(Long id,
                                  String monitorKey,
                                  String businessLineCode,
                                  String environmentCode,
                                  String name,
                                  String status,
                                  String message,
                                  LocalDateTime checkedAt,
                                  int databaseCount,
                                  long sendTotalCount,
                                  long sendSuccessCount,
                                  long sendFailedCount,
                                  long sendPendingCount,
                                  long receiveTotalCount,
                                  long receiveSuccessCount,
                                  long receiveFailedCount,
                                  List<MqDatabaseStatsResponse> databaseStats,
                                  long failedMessageCount,
                                  int messagePage,
                                  int messagePageSize,
                                  List<MqMessageResponse> messages,
                                  String mqTopic,
                                  String mqConsumerGroup,
                                  Integer mqLagThreshold,
                                  String remark) {
}
