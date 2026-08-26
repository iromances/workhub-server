package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertCleanupTaskMapper;
import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertCleanupEventReference;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SystemAlertCleanupTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemAlertCleanupTaskRunner.class);
    private static final int EVENT_BATCH_SIZE = 500;
    private final SystemAlertCleanupTaskMapper taskMapper;
    private final SystemAlertMapper systemAlertMapper;
    private final SystemAlertCleanupRuleService ruleService;
    private final SystemAlertCleanupBatchService batchService;

    public SystemAlertCleanupTaskRunner(SystemAlertCleanupTaskMapper taskMapper,
                                        SystemAlertMapper systemAlertMapper,
                                        SystemAlertCleanupRuleService ruleService,
                                        SystemAlertCleanupBatchService batchService) {
        this.taskMapper = taskMapper;
        this.systemAlertMapper = systemAlertMapper;
        this.ruleService = ruleService;
        this.batchService = batchService;
    }

    public void execute(Long taskId) {
        if (taskMapper.claim(taskId) != 1) {
            return;
        }
        try {
            SystemAlertCleanupTaskEntity task = requireTask(taskId);
            Long ruleId = ruleService.ensureIgnoreRule(task.getMessageKeyword());
            long maxEventId = systemAlertMapper.findMaxEventId();
            if (taskMapper.setExecutionPlan(taskId, ruleId, maxEventId) != 1) {
                throw new IllegalStateException("系统预警清理任务状态已变化");
            }

            long processedEventId = 0;
            while (processedEventId < maxEventId) {
                List<SystemAlertCleanupEventReference> batch = systemAlertMapper.findCleanupEventBatch(
                        task.getBusinessLineCode(),
                        task.getEnvironmentCode(),
                        task.getServiceName(),
                        task.getLogLevel(),
                        task.getEventCategory(),
                        task.getMessageKeyword(),
                        task.getStartTime(),
                        task.getEndTime(),
                        processedEventId,
                        maxEventId,
                        EVENT_BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                processedEventId = batch.getLast().id();
                batchService.deleteAndAdvance(taskId, batch, processedEventId);
            }
            batchService.complete(requireTask(taskId));
        } catch (Exception ex) {
            String errorMessage = errorMessage(ex);
            taskMapper.markFailed(taskId, errorMessage);
            log.error("System alert delete-and-filter task failed. taskId={}, message={}",
                    taskId, errorMessage, ex);
        }
    }

    private SystemAlertCleanupTaskEntity requireTask(Long taskId) {
        SystemAlertCleanupTaskEntity task = taskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("系统预警清理任务不存在");
        }
        return task;
    }

    private String errorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
