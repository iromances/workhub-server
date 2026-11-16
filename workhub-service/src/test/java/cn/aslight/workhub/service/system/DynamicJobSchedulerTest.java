package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysJobMapper;
import cn.aslight.workhub.model.system.SysJobConfigEntity;
import cn.aslight.workhub.model.system.SysJobExecutionEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-9f3c2a84：动态调度器——种子回写、调度值校验、并发保护与执行历史。 */
class DynamicJobSchedulerTest {

    private final FakeSysJobMapper mapper = new FakeSysJobMapper();
    private final CountDownLatch jobStarted = new CountDownLatch(1);
    private final CountDownLatch releaseJob = new CountDownLatch(1);
    private final AtomicInteger runs = new AtomicInteger();

    private final WorkhubJob blockingJob = new WorkhubJob() {
        @Override
        public String code() {
            return "TEST_BLOCKING";
        }

        @Override
        public String name() {
            return "阻塞测试任务";
        }

        @Override
        public ScheduleType scheduleType() {
            return ScheduleType.CRON;
        }

        @Override
        public String defaultScheduleValue() {
            return "0 */30 * * * *";
        }

        @Override
        public void run(String params) throws Exception {
            runs.incrementAndGet();
            jobStarted.countDown();
            releaseJob.await();
        }
    };

    private DynamicJobScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            releaseJob.countDown();
        }
    }

    @Test
    void startSeedsDefaultsAndSchedulesEnabledJobs() {
        scheduler = newScheduler(blockingJob);
        scheduler.start();
        SysJobConfigEntity config = mapper.configs.get("TEST_BLOCKING");
        assertEquals("CRON", config.getScheduleType());
        assertEquals("0 */30 * * * *", config.getScheduleValue());
        assertEquals("0 */30 * * * *", config.getDefaultScheduleValue());
        assertTrue(scheduler.nextFireTime("TEST_BLOCKING") != null);
    }

    @Test
    void manualTriggerRejectedWhileRunning() throws Exception {
        scheduler = newScheduler(blockingJob);
        scheduler.start();
        Thread runner = new Thread(() -> scheduler.triggerNow("TEST_BLOCKING", "p=1"));
        runner.start();
        assertTrue(jobStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> scheduler.triggerNow("TEST_BLOCKING", null));
        assertTrue(ex.getMessage().contains("正在运行"));
        releaseJob.countDown();
        runner.join(5000);
        assertEquals(1, runs.get());
        SysJobExecutionEntity execution = mapper.executions.get("TEST_BLOCKING").getFirst();
        assertEquals("SUCCESS", execution.getStatus());
        assertEquals("p=1", execution.getParams());
    }

    @Test
    void failingJobRecordsErrorExecution() {
        WorkhubJob failing = new WorkhubJob() {
            @Override
            public String code() {
                return "TEST_FAILING";
            }

            @Override
            public String name() {
                return "失败测试任务";
            }

            @Override
            public ScheduleType scheduleType() {
                return ScheduleType.CRON;
            }

            @Override
            public String defaultScheduleValue() {
                return "0 */30 * * * *";
            }

            @Override
            public void run(String params) {
                throw new IllegalStateException("boom");
            }
        };
        scheduler = newScheduler(failing);
        scheduler.start();
        SysJobExecutionEntity execution = scheduler.triggerNow("TEST_FAILING", null);
        assertEquals("ERROR", execution.getStatus());
        assertEquals("boom", execution.getErrorMessage());
        assertEquals("TEST_FAILING", execution.getJobCode());
    }

    @Test
    void scheduleValueValidationRejectsIllegalInput() {
        scheduler = newScheduler(blockingJob);
        scheduler.start();
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.validateScheduleValue(WorkhubJob.ScheduleType.CRON, "not-a-cron"));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.validateScheduleValue(WorkhubJob.ScheduleType.FIXED_DELAY, "10"));
        assertEquals("5000", scheduler.validateScheduleValue(WorkhubJob.ScheduleType.FIXED_DELAY, " 5000 "));
        assertEquals("0 */30 * * * *",
                scheduler.validateScheduleValue(WorkhubJob.ScheduleType.CRON, "0 */30 * * * *"));
    }

    @Test
    void disabledJobIsNotScheduledAndRescheduleHonorsNewConfig() throws Exception {
        scheduler = newScheduler(blockingJob);
        scheduler.start();
        mapper.updateEnabled("TEST_BLOCKING", false);
        scheduler.reschedule("TEST_BLOCKING");
        assertEquals(null, scheduler.nextFireTime("TEST_BLOCKING"));
        // 恢复启用并改 cron，重调度后恢复下次触发时间
        mapper.updateEnabled("TEST_BLOCKING", true);
        mapper.updateSchedule("TEST_BLOCKING", "CRON", "0 */45 * * * *");
        scheduler.reschedule("TEST_BLOCKING");
        assertTrue(scheduler.nextFireTime("TEST_BLOCKING") != null);
        releaseJob.countDown();
    }

    @Test
    void unknownJobIsRejected() {
        scheduler = newScheduler(blockingJob);
        assertThrows(IllegalArgumentException.class, () -> scheduler.requireJob("NO_SUCH"));
    }

    private DynamicJobScheduler newScheduler(WorkhubJob job) {
        return new DynamicJobScheduler(mapper, List.of(job));
    }

    /** 内存版 SysJobMapper，仅覆盖调度器用到的行为。 */
    private static class FakeSysJobMapper implements SysJobMapper {
        final Map<String, SysJobConfigEntity> configs = new ConcurrentHashMap<>();
        final Map<String, List<SysJobExecutionEntity>> executions = new ConcurrentHashMap<>();

        @Override
        public List<SysJobConfigEntity> findAllConfigs() {
            return new ArrayList<>(configs.values());
        }

        @Override
        public SysJobConfigEntity findConfigByCode(String jobCode) {
            return configs.get(jobCode);
        }

        @Override
        public int insertConfig(SysJobConfigEntity entity) {
            configs.put(entity.getJobCode(), entity);
            return 1;
        }

        @Override
        public int updateSchedule(String jobCode, String scheduleType, String scheduleValue) {
            SysJobConfigEntity entity = configs.get(jobCode);
            if (entity == null) {
                return 0;
            }
            entity.setScheduleType(scheduleType);
            entity.setScheduleValue(scheduleValue);
            return 1;
        }

        @Override
        public int updateEnabled(String jobCode, boolean enabled) {
            SysJobConfigEntity entity = configs.get(jobCode);
            if (entity == null) {
                return 0;
            }
            entity.setEnabled(enabled);
            return 1;
        }

        @Override
        public int insertExecution(SysJobExecutionEntity entity) {
            entity.setId((long) (executions.size() + 1));
            executions.computeIfAbsent(entity.getJobCode(), code -> new ArrayList<>()).add(0, entity);
            return 1;
        }

        @Override
        public int finishExecution(Long id, String status, LocalDateTime finishedAt, long durationMs, String errorMessage) {
            for (List<SysJobExecutionEntity> list : executions.values()) {
                for (SysJobExecutionEntity execution : list) {
                    if (id.equals(execution.getId())) {
                        execution.setStatus(status);
                        execution.setFinishedAt(finishedAt);
                        execution.setDurationMs(durationMs);
                        execution.setErrorMessage(errorMessage);
                        return 1;
                    }
                }
            }
            return 0;
        }

        @Override
        public SysJobExecutionEntity findLastExecution(String jobCode) {
            List<SysJobExecutionEntity> list = executions.get(jobCode);
            return list == null || list.isEmpty() ? null : list.getFirst();
        }

        @Override
        public long countExecutions(String jobCode, List<String> statuses) {
            return executions.getOrDefault(jobCode, List.of()).size();
        }

        @Override
        public List<SysJobExecutionEntity> findExecutions(String jobCode, List<String> statuses, int limit, int offset) {
            return executions.getOrDefault(jobCode, List.of()).stream()
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public int deleteExecutionsBefore(LocalDateTime cutoff) {
            return 0;
        }
    }
}
