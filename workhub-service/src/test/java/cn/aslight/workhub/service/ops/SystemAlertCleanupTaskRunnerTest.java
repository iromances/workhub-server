package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertCleanupTaskMapper;
import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertCleanupEventReference;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemAlertCleanupTaskRunnerTest {

    @Test
    void execute_shouldProcessUnlimitedMatchesInFiveHundredRowBatches() {
        FakeTaskMapper tasks = new FakeTaskMapper(task());
        FakeRuleService rules = new FakeRuleService();
        FakeBatchService batches = new FakeBatchService(tasks.task);
        SystemAlertCleanupTaskRunner runner = new SystemAlertCleanupTaskRunner(
                tasks, eventMapper(), rules, batches);

        runner.execute(1L);

        assertEquals(1, tasks.claimCount);
        assertEquals(2, batches.batchCount);
        assertEquals(700L, batches.completedTask.getDeletedEventCount());
        assertEquals(700L, batches.completedTask.getProcessedEventId());
        assertEquals(27L, batches.completedTask.getRuleId());
    }

    @Test
    void execute_shouldIgnoreDuplicateDeliveryWhenTaskWasAlreadyClaimed() {
        FakeTaskMapper tasks = new FakeTaskMapper(task());
        tasks.task.setStatus("RUNNING");
        FakeRuleService rules = new FakeRuleService();
        FakeBatchService batches = new FakeBatchService(tasks.task);
        SystemAlertCleanupTaskRunner runner = new SystemAlertCleanupTaskRunner(
                tasks, eventMapper(), rules, batches);

        runner.execute(1L);

        assertEquals(0, rules.callCount);
        assertEquals(0, batches.batchCount);
    }

    private SystemAlertMapper eventMapper() {
        return (SystemAlertMapper) Proxy.newProxyInstance(
                SystemAlertMapper.class.getClassLoader(),
                new Class<?>[]{SystemAlertMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findMaxEventId" -> 1_000L;
                    case "findCleanupEventBatch" -> cleanupBatch((Long) args[8]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private List<SystemAlertCleanupEventReference> cleanupBatch(long processedEventId) {
        if (processedEventId == 0) return references(1, 500);
        if (processedEventId == 500) return references(501, 700);
        return List.of();
    }

    private SystemAlertCleanupTaskEntity task() {
        SystemAlertCleanupTaskEntity task = new SystemAlertCleanupTaskEntity();
        task.setId(1L);
        task.setTaskNo("SACT-1");
        task.setMessageKeyword("Insert Person Time");
        task.setStatus("PENDING");
        task.setDeletedEventCount(0L);
        task.setDeletedNotificationCount(0L);
        task.setProcessedEventId(0L);
        return task;
    }

    private List<SystemAlertCleanupEventReference> references(long start, long end) {
        return LongStream.rangeClosed(start, end)
                .mapToObj(id -> new SystemAlertCleanupEventReference(
                        id, "BL000004", "prod", "source-" + id))
                .toList();
    }

    private static class FakeTaskMapper implements SystemAlertCleanupTaskMapper {
        private final SystemAlertCleanupTaskEntity task;
        private int claimCount;

        private FakeTaskMapper(SystemAlertCleanupTaskEntity task) {
            this.task = task;
        }

        @Override public int insert(SystemAlertCleanupTaskEntity entity) { return 0; }
        @Override public SystemAlertCleanupTaskEntity findById(Long id) { return task; }
        @Override public int claim(Long id) {
            if (!"PENDING".equals(task.getStatus())) return 0;
            task.setStatus("RUNNING");
            claimCount++;
            return 1;
        }
        @Override public int setExecutionPlan(Long id, Long ruleId, Long maxEventId) {
            task.setRuleId(ruleId);
            task.setMaxEventId(maxEventId);
            return 1;
        }
        @Override public int advance(Long id, Long processedEventId, long events, long notifications) { return 0; }
        @Override public int markSuccess(Long id) { return 1; }
        @Override public int markFailed(Long id, String errorMessage) {
            task.setStatus("FAILED");
            task.setErrorMessage(errorMessage);
            return 1;
        }
    }

    private static class FakeRuleService extends SystemAlertCleanupRuleService {
        private int callCount;

        private FakeRuleService() {
            super(null);
        }

        @Override
        public Long ensureIgnoreRule(String keyword) {
            callCount++;
            return 27L;
        }
    }

    private static class FakeBatchService extends SystemAlertCleanupBatchService {
        private final SystemAlertCleanupTaskEntity task;
        private int batchCount;
        private SystemAlertCleanupTaskEntity completedTask;

        private FakeBatchService(SystemAlertCleanupTaskEntity task) {
            super(null, null, null);
            this.task = task;
        }

        @Override
        public void deleteAndAdvance(Long taskId,
                                     List<SystemAlertCleanupEventReference> references,
                                     long processedEventId) {
            batchCount++;
            task.setDeletedEventCount(task.getDeletedEventCount() + references.size());
            task.setProcessedEventId(processedEventId);
        }

        @Override
        public void complete(SystemAlertCleanupTaskEntity task) {
            completedTask = task;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        return null;
    }
}
