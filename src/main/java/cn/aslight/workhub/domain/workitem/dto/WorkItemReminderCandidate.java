package cn.aslight.workhub.domain.workitem.dto;

import java.time.LocalDateTime;

public record WorkItemReminderCandidate(Long id,
                                        String no,
                                        String title,
                                        String projectName,
                                        String ownerUserName,
                                        String status,
                                        String priority,
                                        LocalDateTime plannedEndAt) {
}
