package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertCleanupTaskMapper;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskEntity;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskResponse;
import cn.aslight.workhub.model.ops.SystemAlertDeleteAndFilterRequest;
import cn.aslight.workhub.model.ops.SystemAlertEventCategory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class SystemAlertCleanupTaskService {

    private final SystemAlertCleanupTaskMapper taskMapper;
    private final SystemAlertCleanupTaskRunner taskRunner;
    private final TaskExecutor taskExecutor;

    public SystemAlertCleanupTaskService(
            SystemAlertCleanupTaskMapper taskMapper,
            SystemAlertCleanupTaskRunner taskRunner,
            @Qualifier("systemAlertCleanupTaskExecutor") TaskExecutor taskExecutor) {
        this.taskMapper = taskMapper;
        this.taskRunner = taskRunner;
        this.taskExecutor = taskExecutor;
    }

    public SystemAlertCleanupTaskResponse submit(SystemAlertDeleteAndFilterRequest request,
                                                 String operator,
                                                 String ip) {
        SystemAlertCleanupTaskEntity task = toEntity(request, operator, ip);
        taskMapper.insert(task);
        try {
            taskExecutor.execute(() -> taskRunner.execute(task.getId()));
        } catch (TaskRejectedException ex) {
            taskMapper.markFailed(task.getId(), "系统预警清理任务队列已满，请稍后重试");
        }
        return toResponse(requireTask(task.getId()));
    }

    public SystemAlertCleanupTaskResponse detail(Long id, String operator, boolean canManage) {
        SystemAlertCleanupTaskEntity task = requireTask(id);
        if (!canManage && !task.getOperatorUserName().equals(operator)) {
            throw new AccessDeniedException("无权查看该系统预警清理任务");
        }
        return toResponse(task);
    }

    private SystemAlertCleanupTaskEntity toEntity(SystemAlertDeleteAndFilterRequest request,
                                                   String operator,
                                                   String ip) {
        String messageKeyword = trimToNull(request.getMessageKeyword());
        if (messageKeyword == null) {
            throw new IllegalArgumentException("错误消息关键词不能为空");
        }
        if (messageKeyword.length() > 200) {
            throw new IllegalArgumentException("错误消息关键词不能超过200个字符");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        SystemAlertEventCategory eventCategory = SystemAlertEventCategory.parseNullable(
                request.getEventCategory());
        SystemAlertCleanupTaskEntity task = new SystemAlertCleanupTaskEntity();
        task.setTaskNo("SACT-" + UUID.randomUUID().toString().replace("-", ""));
        task.setMessageKeyword(messageKeyword);
        task.setBusinessLineCode(trimToNull(request.getBusinessLineCode()));
        task.setEnvironmentCode(trimToNull(request.getEnvironmentCode()));
        task.setServiceName(trimToNull(request.getServiceName()));
        task.setLogLevel(upperToNull(request.getLevel()));
        task.setEventCategory(eventCategory == null ? null : eventCategory.name());
        task.setStartTime(request.getStartTime());
        task.setEndTime(request.getEndTime());
        task.setStatus("PENDING");
        task.setOperatorUserName(operator == null || operator.isBlank() ? "unknown" : operator.trim());
        task.setRequestIp(trimToNull(ip));
        return task;
    }

    private SystemAlertCleanupTaskEntity requireTask(Long id) {
        SystemAlertCleanupTaskEntity task = taskMapper.findById(id);
        if (task == null) {
            throw new IllegalArgumentException("系统预警清理任务不存在");
        }
        return task;
    }

    private SystemAlertCleanupTaskResponse toResponse(SystemAlertCleanupTaskEntity task) {
        return new SystemAlertCleanupTaskResponse(
                task.getId(), task.getTaskNo(), task.getMessageKeyword(),
                task.getBusinessLineCode(), task.getEnvironmentCode(), task.getServiceName(),
                task.getLogLevel(), task.getEventCategory(), task.getStartTime(), task.getEndTime(),
                task.getStatus(), task.getRuleId(), task.getMaxEventId(), task.getProcessedEventId(),
                task.getDeletedEventCount(), task.getDeletedNotificationCount(), task.getErrorMessage(),
                task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt(), task.getUpdatedAt());
    }

    private String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
