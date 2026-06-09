package cn.aslight.workhub.model.notification;

import java.time.LocalDateTime;

public record NotificationResponse(Long id,
                                   String recipientUserName,
                                   String notificationType,
                                   String title,
                                   String content,
                                   String businessLineCode,
                                   String environmentCode,
                                   Boolean readFlag,
                                   LocalDateTime readAt,
                                   LocalDateTime createdAt) {
}
