package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertCleanupTaskMapper;
import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskEntity;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskResponse;
import cn.aslight.workhub.model.ops.SystemAlertDeleteAndFilterRequest;
import cn.aslight.workhub.model.ops.SystemAlertDeleteByEventRequest;
import cn.aslight.workhub.model.ops.SystemAlertDeleteByMessageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemAlertCleanupTaskServiceTest {

    @Test
    void submit_shouldCreateIndependentTasksWhileAnotherTaskIsRunning() {
        FakeTaskMapper mapper = new FakeTaskMapper();
        RecordingTaskExecutor executor = new RecordingTaskExecutor();
        SystemAlertCleanupTaskService service = new SystemAlertCleanupTaskService(
                mapper, null, null, executor);

        SystemAlertCleanupTaskResponse first = service.submit(request(), "admin", "127.0.0.1");
        mapper.tasks.get(first.id()).setStatus("RUNNING");
        SystemAlertCleanupTaskResponse second = service.submit(request(), "admin", "127.0.0.1");

        assertEquals(2, mapper.tasks.size());
        assertEquals(2, executor.tasks.size());
        assertEquals("RUNNING", mapper.tasks.get(first.id()).getStatus());
        assertEquals("PENDING", second.status());
        assertEquals("DELETE_AND_FILTER", second.taskType());
        assertEquals("Insert Person Time", second.messageKeyword());
        assertEquals("BL000004", second.businessLineCode());
    }

    @Test
    void submitDeleteByMessage_shouldCreateDeleteTaskWithoutPageFilters() {
        FakeTaskMapper mapper = new FakeTaskMapper();
        RecordingTaskExecutor executor = new RecordingTaskExecutor();
        SystemAlertCleanupTaskService service = new SystemAlertCleanupTaskService(
                mapper, null, null, executor);
        SystemAlertDeleteByMessageRequest request = new SystemAlertDeleteByMessageRequest();
        request.setMessageKeyword(" NullPointerException ");

        SystemAlertCleanupTaskResponse response = service.submitDeleteByMessage(
                request, "admin", "127.0.0.1");

        assertEquals("DELETE", response.taskType());
        assertEquals("NullPointerException", response.messageKeyword());
        assertNull(response.businessLineCode());
        assertNull(response.environmentCode());
        assertNull(response.serviceName());
        assertNull(response.level());
        assertNull(response.eventCategory());
        assertNull(response.startTime());
        assertNull(response.endTime());
        assertEquals(1, executor.tasks.size());
    }

    @Test
    void submitDeleteExactMessage_shouldResolveFullMessageFromEvent() {
        FakeTaskMapper mapper = new FakeTaskMapper();
        RecordingTaskExecutor executor = new RecordingTaskExecutor();
        String fullMessage = "x".repeat(250);
        SystemAlertCleanupTaskService service = new SystemAlertCleanupTaskService(
                mapper, eventMapper("  " + fullMessage + "  "), null, executor);
        SystemAlertDeleteByEventRequest request = new SystemAlertDeleteByEventRequest();
        request.setEventId(11L);

        SystemAlertCleanupTaskResponse response = service.submitDeleteExactMessage(
                request, "admin", "127.0.0.1");

        assertEquals("DELETE_EXACT_MESSAGE", response.taskType());
        assertEquals(fullMessage, response.messageKeyword());
        assertNull(response.businessLineCode());
        assertNull(response.environmentCode());
        assertNull(response.serviceName());
        assertEquals(1, executor.tasks.size());
    }

    @Test
    void submitDeleteExactMessage_shouldRejectMissingOrBlankEventMessage() {
        SystemAlertDeleteByEventRequest request = new SystemAlertDeleteByEventRequest();
        request.setEventId(11L);
        SystemAlertCleanupTaskService service = new SystemAlertCleanupTaskService(
                new FakeTaskMapper(), eventMapper("  "), null, task -> { });

        assertThrows(IllegalArgumentException.class,
                () -> service.submitDeleteExactMessage(request, "admin", null));
    }

    @Test
    void submit_shouldMarkTaskFailedWhenQueueRejects() {
        FakeTaskMapper mapper = new FakeTaskMapper();
        TaskExecutor rejectingExecutor = task -> {
            throw new TaskRejectedException("full");
        };
        SystemAlertCleanupTaskService service = new SystemAlertCleanupTaskService(
                mapper, null, null, rejectingExecutor);

        SystemAlertCleanupTaskResponse response = service.submit(
                request(), "admin", "127.0.0.1");

        assertEquals("FAILED", response.status());
        assertEquals("系统预警清理任务队列已满，请稍后重试", response.errorMessage());
    }

    @Test
    void submit_shouldRejectBlankKeywordAndInvalidTimeRange() {
        SystemAlertCleanupTaskService service = new SystemAlertCleanupTaskService(
                new FakeTaskMapper(), null, null, task -> { });
        SystemAlertDeleteAndFilterRequest blank = request();
        blank.setMessageKeyword("  ");
        SystemAlertDeleteAndFilterRequest invalidTime = request();
        invalidTime.setStartTime(LocalDateTime.of(2026, 8, 25, 10, 0));
        invalidTime.setEndTime(LocalDateTime.of(2026, 8, 25, 9, 0));

        assertThrows(IllegalArgumentException.class,
                () -> service.submit(blank, "admin", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(invalidTime, "admin", null));
    }

    private SystemAlertMapper eventMapper(String displayMessage) {
        return (SystemAlertMapper) Proxy.newProxyInstance(
                SystemAlertMapper.class.getClassLoader(),
                new Class<?>[]{SystemAlertMapper.class},
                (proxy, method, args) -> "findDisplayMessageByEventId".equals(method.getName())
                        ? displayMessage
                        : null);
    }

    private SystemAlertDeleteAndFilterRequest request() {
        SystemAlertDeleteAndFilterRequest request = new SystemAlertDeleteAndFilterRequest();
        request.setMessageKeyword(" Insert Person Time ");
        request.setBusinessLineCode(" BL000004 ");
        request.setEnvironmentCode("prod");
        request.setServiceName("amp-order");
        request.setLevel("error");
        request.setEventCategory("system_error");
        return request;
    }

    private static class RecordingTaskExecutor implements TaskExecutor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }
    }

    private static class FakeTaskMapper implements SystemAlertCleanupTaskMapper {
        private final Map<Long, SystemAlertCleanupTaskEntity> tasks = new LinkedHashMap<>();
        private long sequence;

        @Override
        public int insert(SystemAlertCleanupTaskEntity entity) {
            entity.setId(++sequence);
            entity.setMaxEventId(0L);
            entity.setProcessedEventId(0L);
            entity.setDeletedEventCount(0L);
            entity.setDeletedNotificationCount(0L);
            entity.setCreatedAt(LocalDateTime.of(2026, 8, 25, 0, 0));
            entity.setUpdatedAt(entity.getCreatedAt());
            tasks.put(entity.getId(), entity);
            return 1;
        }

        @Override
        public SystemAlertCleanupTaskEntity findById(Long id) {
            return tasks.get(id);
        }

        @Override public int claim(Long id) { return 0; }
        @Override public int setExecutionPlan(Long id, Long ruleId, Long maxEventId) { return 0; }
        @Override public int advance(Long id, Long processedEventId, long events, long notifications) { return 0; }
        @Override public int markSuccess(Long id) { return 0; }

        @Override
        public int markFailed(Long id, String errorMessage) {
            SystemAlertCleanupTaskEntity task = tasks.get(id);
            task.setStatus("FAILED");
            task.setErrorMessage(errorMessage);
            return 1;
        }
    }
}
