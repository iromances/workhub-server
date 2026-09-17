package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertCleanupTaskMapper;
import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertCleanupEventReference;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskEntity;
import cn.aslight.workhub.model.ops.SystemAlertNotificationCandidate;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemAlertCleanupBatchServiceTest {

    @Test
    void deleteAndAdvance_shouldDeleteOnlyNotificationsInSameBusinessLineAndEnvironment() {
        RecordingEventMapper events = new RecordingEventMapper();
        RecordingTaskMapper tasks = new RecordingTaskMapper();
        SystemAlertCleanupBatchService service = new SystemAlertCleanupBatchService(
                events.proxy(), tasks, new SystemAuditService(null));

        service.deleteAndAdvance(27L, List.of(
                new SystemAlertCleanupEventReference(11L, "BL000004", "prod", "source-11"),
                new SystemAlertCleanupEventReference(12L, "BL000004", "prod", "source-12")), 12L);

        assertEquals(List.of(101L, 102L), events.deletedNotificationIds);
        assertEquals(List.of(11L, 12L), events.deletedEventIds);
        assertEquals(27L, tasks.advancedTaskId);
        assertEquals(12L, tasks.processedEventId);
        assertEquals(2L, tasks.deletedEvents);
        assertEquals(2L, tasks.deletedNotifications);
    }

    @Test
    void complete_shouldAuditDeleteTaskWithoutFilterRule() {
        RecordingTaskMapper tasks = new RecordingTaskMapper();
        RecordingAuditService audit = new RecordingAuditService();
        SystemAlertCleanupBatchService service = new SystemAlertCleanupBatchService(null, tasks, audit);
        SystemAlertCleanupTaskEntity task = new SystemAlertCleanupTaskEntity();
        task.setId(28L);
        task.setTaskNo("SACT-28");
        task.setTaskType("DELETE");
        task.setMessageKeyword("NullPointerException");
        task.setDeletedEventCount(12L);
        task.setDeletedNotificationCount(8L);
        task.setOperatorUserName("admin");

        service.complete(task);

        assertEquals(28L, tasks.completedTaskId);
        assertEquals("DELETE", audit.actionType);
        assertTrue(audit.afterSnapshot.contains("未创建过滤规则"));
        assertTrue(audit.afterSnapshot.contains("事件12条"));
    }

    private static class RecordingEventMapper {
        private final List<Long> deletedNotificationIds = new ArrayList<>();
        private final List<Long> deletedEventIds = new ArrayList<>();

        private SystemAlertMapper proxy() {
            return (SystemAlertMapper) Proxy.newProxyInstance(
                    SystemAlertMapper.class.getClassLoader(),
                    new Class<?>[]{SystemAlertMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findNotificationCandidatesByEventIds" -> List.of(
                                new SystemAlertNotificationCandidate(101L, "BL000004", "prod", "ELK:BATCH:source-11"),
                                new SystemAlertNotificationCandidate(102L, "BL000004", "prod", "ELK:BATCH:source-12"),
                                new SystemAlertNotificationCandidate(103L, "BL000004", "test", "ELK:BATCH:source-11"),
                                new SystemAlertNotificationCandidate(104L, "BL000004", "prod", "ELK:BATCH:other"));
                        case "deleteNotificationsByIds" -> addAll(deletedNotificationIds, args[0]);
                        case "deleteEventsByIds" -> addAll(deletedEventIds, args[0]);
                        default -> defaultValue(method.getReturnType());
                    });
        }

        @SuppressWarnings("unchecked")
        private int addAll(List<Long> target, Object value) {
            List<Long> ids = (List<Long>) value;
            target.addAll(ids);
            return ids.size();
        }
    }

    private static class RecordingTaskMapper implements SystemAlertCleanupTaskMapper {
        private Long advancedTaskId;
        private Long processedEventId;
        private long deletedEvents;
        private long deletedNotifications;
        private Long completedTaskId;

        @Override public int insert(SystemAlertCleanupTaskEntity entity) { return 0; }
        @Override public SystemAlertCleanupTaskEntity findById(Long id) { return null; }
        @Override public int claim(Long id) { return 0; }
        @Override public int setExecutionPlan(Long id, Long ruleId, Long maxEventId) { return 0; }
        @Override public int advance(Long id, Long processedEventId, long events, long notifications) {
            this.advancedTaskId = id;
            this.processedEventId = processedEventId;
            this.deletedEvents = events;
            this.deletedNotifications = notifications;
            return 1;
        }
        @Override public int markSuccess(Long id) {
            completedTaskId = id;
            return 1;
        }
        @Override public int markFailed(Long id, String errorMessage) { return 0; }
    }

    private static class RecordingAuditService extends SystemAuditService {
        private String actionType;
        private String afterSnapshot;

        private RecordingAuditService() {
            super(null);
        }

        @Override
        public void operation(String operator,
                              String permissionCode,
                              String actionType,
                              String targetType,
                              String targetId,
                              String beforeSnapshot,
                              String afterSnapshot,
                              String result,
                              String errorMessage,
                              String ip) {
            this.actionType = actionType;
            this.afterSnapshot = afterSnapshot;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        return null;
    }
}
