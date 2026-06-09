package cn.aslight.workhub.model.ops;

public record XxlJobFailedJobResponse(String executorAppName,
                                      Long jobId,
                                      String jobDesc,
                                      String author,
                                      String executorHandler,
                                      Integer triggerStatus,
                                      String failureType,
                                      String failureTypeName,
                                      Long logId,
                                      String triggerTime,
                                      Integer triggerCode,
                                      String triggerMsg,
                                      String handleTime,
                                      Integer handleCode,
                                      String handleMsg) {
}
