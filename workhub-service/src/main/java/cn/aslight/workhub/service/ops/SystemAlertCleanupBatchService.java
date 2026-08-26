package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertCleanupTaskMapper;
import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertCleanupEventReference;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskEntity;
import cn.aslight.workhub.model.ops.SystemAlertNotificationCandidate;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SystemAlertCleanupBatchService {

    private static final int DELETE_BATCH_SIZE = 500;
    private final SystemAlertMapper systemAlertMapper;
    private final SystemAlertCleanupTaskMapper taskMapper;
    private final SystemAuditService auditService;

    public SystemAlertCleanupBatchService(SystemAlertMapper systemAlertMapper,
                                          SystemAlertCleanupTaskMapper taskMapper,
                                          SystemAuditService auditService) {
        this.systemAlertMapper = systemAlertMapper;
        this.taskMapper = taskMapper;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAndAdvance(Long taskId,
                                 List<SystemAlertCleanupEventReference> references,
                                 long processedEventId) {
        List<Long> eventIds = references.stream().map(SystemAlertCleanupEventReference::id).toList();
        List<Long> notificationIds = findRelatedNotificationIds(eventIds, references);
        int deletedNotificationCount = deleteNotificationIds(notificationIds);
        int deletedEventCount = deleteEventIds(eventIds);
        if (taskMapper.advance(taskId, processedEventId,
                deletedEventCount, deletedNotificationCount) != 1) {
            throw new IllegalStateException("系统预警清理任务状态已变化");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(SystemAlertCleanupTaskEntity task) {
        if (taskMapper.markSuccess(task.getId()) != 1) {
            throw new IllegalStateException("系统预警清理任务无法完成");
        }
        auditService.operation(
                task.getOperatorUserName(),
                "ops:system-alert:delete",
                "DELETE_AND_FILTER",
                "OPS_SYSTEM_ALERT_CLEANUP_TASK",
                task.getTaskNo(),
                filterSnapshot(task),
                "新增或复用过滤规则" + task.getRuleId()
                        + "，物理删除系统预警事件" + task.getDeletedEventCount()
                        + "条，关联站内通知" + task.getDeletedNotificationCount() + "条",
                "SUCCESS",
                null,
                task.getRequestIp()
        );
    }

    private List<Long> findRelatedNotificationIds(
            List<Long> eventIds,
            List<SystemAlertCleanupEventReference> references) {
        Map<NotificationScope, Set<String>> sourceEventIdsByScope = references.stream()
                .filter(reference -> reference.sourceEventId() != null && !reference.sourceEventId().isBlank())
                .collect(Collectors.groupingBy(
                        reference -> new NotificationScope(
                                reference.businessLineCode(), reference.environmentCode()),
                        Collectors.mapping(SystemAlertCleanupEventReference::sourceEventId, Collectors.toSet())));
        if (sourceEventIdsByScope.isEmpty()) {
            return List.of();
        }
        return systemAlertMapper.findNotificationCandidatesByEventIds(eventIds).stream()
                .filter(candidate -> isRelatedNotification(candidate, sourceEventIdsByScope))
                .map(SystemAlertNotificationCandidate::id)
                .distinct()
                .toList();
    }

    private boolean isRelatedNotification(
            SystemAlertNotificationCandidate candidate,
            Map<NotificationScope, Set<String>> sourceEventIdsByScope) {
        Set<String> sourceEventIds = sourceEventIdsByScope.get(
                new NotificationScope(candidate.businessLineCode(), candidate.environmentCode()));
        if (sourceEventIds == null || candidate.dedupeKey() == null) {
            return false;
        }
        int separatorIndex = candidate.dedupeKey().lastIndexOf(':');
        return separatorIndex >= 0
                && sourceEventIds.contains(candidate.dedupeKey().substring(separatorIndex + 1));
    }

    private int deleteNotificationIds(List<Long> ids) {
        int count = 0;
        for (int start = 0; start < ids.size(); start += DELETE_BATCH_SIZE) {
            count += systemAlertMapper.deleteNotificationsByIds(
                    ids.subList(start, Math.min(start + DELETE_BATCH_SIZE, ids.size())));
        }
        return count;
    }

    private int deleteEventIds(List<Long> ids) {
        int count = 0;
        for (int start = 0; start < ids.size(); start += DELETE_BATCH_SIZE) {
            count += systemAlertMapper.deleteEventsByIds(
                    ids.subList(start, Math.min(start + DELETE_BATCH_SIZE, ids.size())));
        }
        return count;
    }

    private String filterSnapshot(SystemAlertCleanupTaskEntity task) {
        return "消息关键词=" + task.getMessageKeyword()
                + "，业务线=" + value(task.getBusinessLineCode())
                + "，环境=" + value(task.getEnvironmentCode())
                + "，服务=" + value(task.getServiceName())
                + "，级别=" + value(task.getLogLevel())
                + "，事件分类=" + value(task.getEventCategory())
                + "，开始时间=" + value(task.getStartTime())
                + "，结束时间=" + value(task.getEndTime());
    }

    private String value(Object value) {
        return value == null ? "全部" : String.valueOf(value);
    }

    private record NotificationScope(String businessLineCode, String environmentCode) {
    }
}
