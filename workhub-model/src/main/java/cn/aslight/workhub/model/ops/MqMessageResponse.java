package cn.aslight.workhub.model.ops;

/** MQ 失败或积压消息明细行；errorMsg 已截断，不含消息正文。 */
public record MqMessageResponse(String databaseName,
                                String logType,
                                long logId,
                                String messageId,
                                String exchange,
                                String routingKey,
                                String queue,
                                Integer status,
                                String failureType,
                                String failureTypeName,
                                Integer retryCount,
                                Boolean autoRetry,
                                Boolean dealBusinessSuccess,
                                String occurredAt,
                                String createTime,
                                String errorMsg) {
}
