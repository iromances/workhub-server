package cn.aslight.workhub.model.workitem;

import java.time.LocalDateTime;

/**
 * WorkItemReminderCandidate 模型。
 */
public record WorkItemReminderCandidate(Long id,
                                        String no,
                                        String title,
                                        String projectName,
                                        String ownerUserName,
                                        String status,
                                        String priority,
                                        LocalDateTime plannedEndAt) {
}
