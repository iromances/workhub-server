package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;
import java.util.List;

/** Job 管理页列表项与执行历史 DTO（REQ-9f3c2a84）。 */
public final class JobAdminModels {

    private JobAdminModels() {
    }

    public record JobExecutionSnapshot(String status, LocalDateTime startedAt, LocalDateTime finishedAt,
                                       Long durationMs, String errorMessage, String triggerType, String params) {
    }

    public record JobAdminItem(String jobCode, String jobName, String scheduleType, String scheduleValue,
                               String defaultScheduleValue, boolean enabled, boolean allowManualTrigger,
                               String paramDesc, String remark, boolean running,
                               LocalDateTime nextFireTime, JobExecutionSnapshot lastExecution) {
    }

    public record JobScheduleUpdateRequest(String scheduleType, String scheduleValue) {
    }

    public record JobEnabledUpdateRequest(Boolean enabled) {
    }

    public record JobTriggerRequest(String params) {
    }

    public record JobTriggerResult(String jobCode, String triggerType, String status,
                                   LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMs,
                                   String errorMessage) {
    }

    public record JobExecutionPage(long total, int page, int pageSize, List<JobExecutionSnapshot> items) {
    }
}
