package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysJobMapper;
import cn.aslight.workhub.model.system.JobAdminModels.JobAdminItem;
import cn.aslight.workhub.model.system.JobAdminModels.JobExecutionPage;
import cn.aslight.workhub.model.system.JobAdminModels.JobExecutionSnapshot;
import cn.aslight.workhub.model.system.JobAdminModels.JobScheduleUpdateRequest;
import cn.aslight.workhub.model.system.JobAdminModels.JobTriggerResult;
import cn.aslight.workhub.model.system.SysJobConfigEntity;
import cn.aslight.workhub.model.system.SysJobExecutionEntity;
import cn.aslight.workhub.service.system.WorkhubJob.ScheduleType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** REQ-9f3c2a84：Job 管理页服务——列表、改调度（即时生效）、启停、手动触发、执行历史。 */
@Service
public class JobAdminService {

    private static final int MAX_PAGE_SIZE = 200;

    private final SysJobMapper sysJobMapper;
    private final DynamicJobScheduler scheduler;

    public JobAdminService(SysJobMapper sysJobMapper, DynamicJobScheduler scheduler) {
        this.sysJobMapper = sysJobMapper;
        this.scheduler = scheduler;
    }

    public List<JobAdminItem> list() {
        return scheduler.jobs().stream()
                .map(job -> {
                    SysJobConfigEntity config = sysJobMapper.findConfigByCode(job.code());
                    if (config == null) {
                        config = defaultConfig(job);
                    }
                    return toItem(config);
                })
                .toList();
    }

    public JobAdminItem updateSchedule(String jobCode, JobScheduleUpdateRequest request) {
        scheduler.requireJob(jobCode);
        if (request == null || request.scheduleType() == null || request.scheduleValue() == null) {
            throw new IllegalArgumentException("调度类型与调度值不能为空");
        }
        ScheduleType type = parseScheduleType(request.scheduleType());
        String value = scheduler.validateScheduleValue(type, request.scheduleValue());
        if (sysJobMapper.updateSchedule(jobCode, type.name(), value) == 0) {
            throw new IllegalArgumentException("任务不存在：" + jobCode);
        }
        scheduler.reschedule(jobCode);
        return toItem(sysJobMapper.findConfigByCode(jobCode));
    }

    public JobAdminItem updateEnabled(String jobCode, Boolean enabled) {
        scheduler.requireJob(jobCode);
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        if (sysJobMapper.updateEnabled(jobCode, enabled) == 0) {
            throw new IllegalArgumentException("任务不存在：" + jobCode);
        }
        scheduler.reschedule(jobCode);
        return toItem(sysJobMapper.findConfigByCode(jobCode));
    }

    public JobTriggerResult trigger(String jobCode, String params) {
        SysJobExecutionEntity execution = scheduler.triggerNow(jobCode, trimToNull(params));
        return new JobTriggerResult(execution.getJobCode(), execution.getTriggerType(), execution.getStatus(),
                execution.getStartedAt(), execution.getFinishedAt(), execution.getDurationMs(), execution.getErrorMessage());
    }

    public JobExecutionPage executions(String jobCode, int page, int pageSize, String status) {
        scheduler.requireJob(jobCode);
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        List<String> statuses = status == null || status.isBlank() ? null : List.of(status.trim().toUpperCase());
        long total = sysJobMapper.countExecutions(jobCode, statuses);
        List<JobExecutionSnapshot> items = sysJobMapper
                .findExecutions(jobCode, statuses, normalizedPageSize, (normalizedPage - 1) * normalizedPageSize)
                .stream()
                .map(JobAdminService::toSnapshot)
                .toList();
        return new JobExecutionPage(total, normalizedPage, normalizedPageSize, items);
    }

    private JobAdminItem toItem(SysJobConfigEntity config) {
        return new JobAdminItem(config.getJobCode(), config.getJobName(), config.getScheduleType(),
                config.getScheduleValue(), config.getDefaultScheduleValue(),
                Boolean.TRUE.equals(config.getEnabled()), Boolean.TRUE.equals(config.getAllowManualTrigger()),
                config.getParamDesc(), config.getRemark(), scheduler.isRunning(config.getJobCode()),
                nextFireTimeOrNull(config), toSnapshot(sysJobMapper.findLastExecution(config.getJobCode())));
    }

    private LocalDateTime nextFireTimeOrNull(SysJobConfigEntity config) {
        try {
            return scheduler.nextFireTime(config.getJobCode());
        } catch (Exception ex) {
            return null;
        }
    }

    private SysJobConfigEntity defaultConfig(WorkhubJob job) {
        SysJobConfigEntity config = new SysJobConfigEntity();
        config.setJobCode(job.code());
        config.setJobName(job.name());
        config.setScheduleType(job.scheduleType().name());
        config.setScheduleValue(job.defaultScheduleValue());
        config.setDefaultScheduleValue(job.defaultScheduleValue());
        config.setEnabled(true);
        config.setAllowManualTrigger(true);
        config.setParamDesc(job.paramDescription());
        return config;
    }

    private static ScheduleType parseScheduleType(String value) {
        try {
            return ScheduleType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("调度类型只支持 CRON 或 FIXED_DELAY：" + value);
        }
    }

    private static JobExecutionSnapshot toSnapshot(SysJobExecutionEntity execution) {
        if (execution == null) {
            return null;
        }
        return new JobExecutionSnapshot(execution.getStatus(), execution.getStartedAt(), execution.getFinishedAt(),
                execution.getDurationMs(), execution.getErrorMessage(), execution.getTriggerType(), execution.getParams());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
