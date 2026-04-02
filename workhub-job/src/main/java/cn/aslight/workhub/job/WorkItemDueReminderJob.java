package cn.aslight.workhub.job;

import cn.aslight.workhub.config.ReminderProperties;
import cn.aslight.workhub.model.workitem.WorkItemReminderCandidate;
import cn.aslight.workhub.dao.workitem.WorkItemMapper;
import cn.aslight.workhub.integration.wecom.WecomRobotNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作项到期提醒定时任务。
 */
@Component
public class WorkItemDueReminderJob {

    private static final Logger log = LoggerFactory.getLogger(WorkItemDueReminderJob.class);

    private final ReminderProperties reminderProperties;
    private final WorkItemMapper workItemMapper;
    private final WecomRobotNotifier wecomRobotNotifier;
    private final Map<Long, Instant> remindedAt = new ConcurrentHashMap<>();

    public WorkItemDueReminderJob(ReminderProperties reminderProperties,
                                  WorkItemMapper workItemMapper,
                                  WecomRobotNotifier wecomRobotNotifier) {
        this.reminderProperties = reminderProperties;
        this.workItemMapper = workItemMapper;
        this.wecomRobotNotifier = wecomRobotNotifier;
    }

    @Scheduled(cron = "${workhub.reminder.cron:0 */30 * * * *}")
    public void remindDueSoonItems() {
        if (!reminderProperties.isEnabled()) {
            return;
        }

        cleanupExpiredDedupeKeys();

        LocalDateTime deadline = LocalDateTime.now().plusHours(reminderProperties.getDueSoonHours());
        List<WorkItemReminderCandidate> candidates = workItemMapper.findDueSoonItems(deadline);
        for (WorkItemReminderCandidate candidate : candidates) {
            if (remindedAt.putIfAbsent(candidate.id(), Instant.now()) == null) {
                wecomRobotNotifier.notifyDueReminder(candidate);
            }
        }
        log.info("Due reminder scan finished. dueSoonCount={}", candidates.size());
    }

    private void cleanupExpiredDedupeKeys() {
        Instant expireBefore = Instant.now().minus(reminderProperties.getDedupeMinutes(), ChronoUnit.MINUTES);
        remindedAt.entrySet().removeIf(entry -> entry.getValue().isBefore(expireBefore));
    }
}
