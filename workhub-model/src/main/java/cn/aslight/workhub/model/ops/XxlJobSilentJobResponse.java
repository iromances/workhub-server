package cn.aslight.workhub.model.ops;

/** 静默任务明细：启用中但调度停摆（trigger_next_time 落后超过宽限期）的任务，用于定位调度侧问题。 */
public record XxlJobSilentJobResponse(String executorAppName,
                                      Long jobId,
                                      String jobDesc,
                                      String author,
                                      String executorHandler,
                                      String triggerNextTime,
                                      Long lagMinutes) {
}
