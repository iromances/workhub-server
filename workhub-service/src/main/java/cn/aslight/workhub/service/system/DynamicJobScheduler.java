package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysJobMapper;
import cn.aslight.workhub.model.system.SysJobConfigEntity;
import cn.aslight.workhub.model.system.SysJobExecutionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REQ-9f3c2a84：动态调度器。sys_job_config 为唯一调度事实源：
 * 启动时按 WorkhubJob Bean 幂等回写默认配置并调度，配置修改后立即重调度，无需重启。
 * 同一任务运行中：调度触发跳过本轮（记日志），手动触发直接拒绝。
 */
@Service
public class DynamicJobScheduler {

    public static final int MIN_FIXED_DELAY_MS = 1000;
    public static final int MAX_FIXED_DELAY_MS = 3_600_000;
    private static final int POOL_SIZE = 4;
    private static final int EXECUTION_RETENTION_DAYS = 90;
    private static final long CLEANUP_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final int ERROR_MESSAGE_MAX = 4000;

    private static final Logger log = LoggerFactory.getLogger(DynamicJobScheduler.class);

    private final SysJobMapper sysJobMapper;
    private final List<WorkhubJob> jobs;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<String, JobState> states = new ConcurrentHashMap<>();
    private final String hostname = resolveHostname();
    private volatile boolean started;

    private record JobState(ScheduledFuture<?> future, AtomicBoolean running) {
    }

    public DynamicJobScheduler(SysJobMapper sysJobMapper, List<WorkhubJob> jobs) {
        this.sysJobMapper = sysJobMapper;
        this.jobs = List.copyOf(jobs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started) {
            return;
        }
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("workhub-job-");
        scheduler.setErrorHandler(ex -> log.warn("Job execution failed with uncaught error", ex));
        scheduler.initialize();
        for (WorkhubJob job : jobs) {
            SysJobConfigEntity config = ensureSeeded(job);
            schedule(job, config);
        }
        scheduler.scheduleWithFixedDelay(this::cleanupExpiredExecutions, CLEANUP_INTERVAL_MS);
        started = true;
        log.info("DynamicJobScheduler started with {} jobs", jobs.size());
    }

    /** 配置修改/启停后调用：按最新配置重调度该任务；运行中标记跨重调度保留，避免改配置瞬间失去并发保护。 */
    public synchronized void reschedule(String jobCode) {
        WorkhubJob job = requireJob(jobCode);
        cancelExisting(jobCode);
        SysJobConfigEntity config = sysJobMapper.findConfigByCode(jobCode);
        if (config == null) {
            config = ensureSeeded(job);
        }
        schedule(job, config);
    }

    /** 手动触发：同步执行一次并返回执行记录；任务运行中或禁止手动触发时抛出 IllegalStateException。 */
    public SysJobExecutionEntity triggerNow(String jobCode, String params) {
        WorkhubJob job = requireJob(jobCode);
        SysJobConfigEntity config = sysJobMapper.findConfigByCode(jobCode);
        if (config == null) {
            config = ensureSeeded(job);
        }
        if (Boolean.FALSE.equals(config.getAllowManualTrigger())) {
            throw new IllegalStateException("该任务不允许手动触发");
        }
        return execute(job, "MANUAL", params);
    }

    public boolean isRunning(String jobCode) {
        JobState state = states.get(jobCode);
        return state != null && state.running().get();
    }

    /** 下次预计触发时间：CRON 按 cron 表达式推算；FIXED_DELAY 按上次开始时间加间隔估算。 */
    public LocalDateTime nextFireTime(String jobCode) {
        WorkhubJob job = requireJob(jobCode);
        SysJobConfigEntity config = sysJobMapper.findConfigByCode(jobCode);
        if (config == null || Boolean.FALSE.equals(config.getEnabled())) {
            return null;
        }
        if (WorkhubJob.ScheduleType.CRON.name().equals(config.getScheduleType())) {
            CronExpression cron = CronExpression.parse(config.getScheduleValue());
            return cron.next(LocalDateTime.now());
        }
        SysJobExecutionEntity last = sysJobMapper.findLastExecution(jobCode);
        long delayMs;
        try {
            delayMs = Long.parseLong(config.getScheduleValue());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (last == null || last.getStartedAt() == null) {
            return LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);
        }
        LocalDateTime next = last.getStartedAt().plus(delayMs, ChronoUnit.MILLIS);
        // 上次执行是运行中（无法精确知道结束时刻）时仅给出估算值。
        return next.isBefore(LocalDateTime.now()) ? LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS) : next;
    }

    public List<WorkhubJob> jobs() {
        return jobs;
    }

    public WorkhubJob requireJob(String jobCode) {
        return jobs.stream()
                .filter(job -> job.code().equals(jobCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("任务不存在：" + jobCode));
    }

    /** 校验并规范化调度值：CRON 返回 cron 表达式，FIXED_DELAY 返回毫秒数字符串。 */
    public String validateScheduleValue(WorkhubJob.ScheduleType type, String value) {
        String normalized = value == null ? "" : value.trim();
        if (type == WorkhubJob.ScheduleType.CRON) {
            if (!CronExpression.isValidExpression(normalized)) {
                throw new IllegalArgumentException("cron 表达式非法：" + normalized);
            }
            return normalized;
        }
        long delayMs;
        try {
            delayMs = Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("fixedDelay 必须为毫秒数：" + normalized);
        }
        if (delayMs < MIN_FIXED_DELAY_MS || delayMs > MAX_FIXED_DELAY_MS) {
            throw new IllegalArgumentException("fixedDelay 必须在 " + MIN_FIXED_DELAY_MS + "-" + MAX_FIXED_DELAY_MS + " 毫秒之间");
        }
        return String.valueOf(delayMs);
    }

    private SysJobConfigEntity ensureSeeded(WorkhubJob job) {
        SysJobConfigEntity existing = sysJobMapper.findConfigByCode(job.code());
        if (existing != null) {
            return existing;
        }
        SysJobConfigEntity seed = new SysJobConfigEntity();
        seed.setJobCode(job.code());
        seed.setJobName(job.name());
        seed.setScheduleType(job.scheduleType().name());
        seed.setScheduleValue(job.defaultScheduleValue());
        seed.setDefaultScheduleValue(job.defaultScheduleValue());
        seed.setEnabled(true);
        seed.setAllowManualTrigger(true);
        seed.setParamDesc(job.paramDescription());
        seed.setRemark("DynamicJobScheduler 启动种子");
        sysJobMapper.insertConfig(seed);
        log.info("Seeded job config {} -> {} {}", job.code(), job.scheduleType(), job.defaultScheduleValue());
        return seed;
    }

    private void schedule(WorkhubJob job, SysJobConfigEntity config) {
        AtomicBoolean running = states.containsKey(job.code())
                ? states.get(job.code()).running()
                : new AtomicBoolean(false);
        if (Boolean.FALSE.equals(config.getEnabled())) {
            states.put(job.code(), new JobState(null, running));
            log.info("Job {} disabled, not scheduled", job.code());
            return;
        }
        Runnable task = () -> runScheduled(job, running);
        ScheduledFuture<?> future;
        try {
            String scheduleValue = validateScheduleValue(
                    WorkhubJob.ScheduleType.valueOf(config.getScheduleType()), config.getScheduleValue());
            if (WorkhubJob.ScheduleType.CRON.name().equals(config.getScheduleType())) {
                future = scheduler.schedule(task, new org.springframework.scheduling.support.CronTrigger(scheduleValue));
            } else {
                future = scheduler.scheduleWithFixedDelay(task, Long.parseLong(scheduleValue));
            }
        } catch (IllegalArgumentException ex) {
            log.error("Job {} has invalid schedule {} {}, skipped: {}", job.code(),
                    config.getScheduleType(), config.getScheduleValue(), ex.getMessage());
            states.put(job.code(), new JobState(null, running));
            return;
        }
        states.put(job.code(), new JobState(future, running));
        log.info("Job {} scheduled as {} {}", job.code(), config.getScheduleType(), config.getScheduleValue());
    }

    private void runScheduled(WorkhubJob job, AtomicBoolean running) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Job {} previous run still in progress, skip this tick", job.code());
            return;
        }
        try {
            executeRecorded(job, "SCHEDULED", null);
        } finally {
            running.set(false);
        }
    }

    private SysJobExecutionEntity execute(WorkhubJob job, String triggerType, String params) {
        AtomicBoolean running = state(job.code()).running();
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("任务正在运行中，请稍后再试");
        }
        try {
            return executeRecorded(job, triggerType, params);
        } finally {
            running.set(false);
        }
    }

    private SysJobExecutionEntity executeRecorded(WorkhubJob job, String triggerType, String params) {
        SysJobExecutionEntity execution = new SysJobExecutionEntity();
        execution.setJobCode(job.code());
        execution.setTriggerType(triggerType);
        execution.setParams(params);
        execution.setStatus("RUNNING");
        LocalDateTime startedAt = LocalDateTime.now().withNano(0);
        execution.setStartedAt(startedAt);
        execution.setHostname(hostname);
        sysJobMapper.insertExecution(execution);
        String error = null;
        try {
            job.run(params);
        } catch (Exception ex) {
            error = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
            log.warn("Job {} {} execution failed: {}", job.code(), triggerType, error, ex);
        }
        LocalDateTime finishedAt = LocalDateTime.now().withNano(0);
        long durationMs = ChronoUnit.MILLIS.between(startedAt, finishedAt);
        String status = error == null ? "SUCCESS" : "ERROR";
        sysJobMapper.finishExecution(execution.getId(), status, finishedAt, durationMs, truncate(error));
        execution.setStatus(status);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(durationMs);
        execution.setErrorMessage(truncate(error));
        return execution;
    }

    private void cancelExisting(String jobCode) {
        JobState existing = states.get(jobCode);
        if (existing != null && existing.future() != null) {
            existing.future().cancel(false);
        }
    }

    private JobState state(String jobCode) {
        return states.computeIfAbsent(jobCode, code -> new JobState(null, new AtomicBoolean(false)));
    }

    private void cleanupExpiredExecutions() {
        try {
            int removed = sysJobMapper.deleteExecutionsBefore(LocalDateTime.now().minusDays(EXECUTION_RETENTION_DAYS));
            if (removed > 0) {
                log.info("Cleaned {} job execution logs older than {} days", removed, EXECUTION_RETENTION_DAYS);
            }
        } catch (Exception ex) {
            log.warn("Job execution log cleanup failed: {}", ex.getMessage());
        }
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= ERROR_MESSAGE_MAX) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_MAX);
    }
}
