package cn.aslight.workhub.service.notification;

import cn.aslight.workhub.dao.notification.NotificationMapper;
import cn.aslight.workhub.model.notification.NotificationEntity;
import cn.aslight.workhub.model.notification.NotificationResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationSchemaInitializer schemaInitializer;

    public NotificationService(NotificationMapper notificationMapper,
                               NotificationSchemaInitializer schemaInitializer) {
        this.notificationMapper = notificationMapper;
        this.schemaInitializer = schemaInitializer;
    }

    public List<NotificationResponse> list(String recipientUserName, int limit) {
        schemaInitializer.ensureInitialized();
        int size = Math.min(Math.max(limit, 1), 100);
        return notificationMapper.findByRecipient(requireUser(recipientUserName), size).stream()
                .map(this::toResponse)
                .toList();
    }

    public int unreadCount(String recipientUserName) {
        schemaInitializer.ensureInitialized();
        return notificationMapper.countUnread(requireUser(recipientUserName));
    }

    public void createSystemNotification(String recipientUserName,
                                         String notificationType,
                                         String title,
                                         String content,
                                         String businessLineCode,
                                         String environmentCode,
                                         String dedupeKey) {
        schemaInitializer.ensureInitialized();
        NotificationEntity entity = new NotificationEntity();
        entity.setRecipientUserName(requireUser(recipientUserName));
        entity.setNotificationType(requireText(notificationType, "通知类型不能为空"));
        entity.setTitle(requireText(title, "通知标题不能为空"));
        entity.setContent(content);
        entity.setBusinessLineCode(trimToNull(businessLineCode));
        entity.setEnvironmentCode(trimToNull(environmentCode));
        entity.setDedupeKey(requireText(dedupeKey, "通知去重 key 不能为空"));
        notificationMapper.insertIgnore(entity);
    }

    public void markRead(Long id, String recipientUserName) {
        schemaInitializer.ensureInitialized();
        notificationMapper.markRead(id, requireUser(recipientUserName));
    }

    public void markAllRead(String recipientUserName) {
        schemaInitializer.ensureInitialized();
        notificationMapper.markAllRead(requireUser(recipientUserName));
    }

    private NotificationResponse toResponse(NotificationEntity entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getRecipientUserName(),
                entity.getNotificationType(),
                entity.getTitle(),
                entity.getContent(),
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getReadFlag(),
                entity.getReadAt(),
                entity.getCreatedAt()
        );
    }

    private String requireUser(String value) {
        return requireText(value, "用户不能为空");
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
