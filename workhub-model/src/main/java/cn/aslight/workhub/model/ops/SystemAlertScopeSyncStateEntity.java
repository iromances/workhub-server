package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record SystemAlertScopeSyncStateEntity(Long scopeId,
                                              LocalDateTime lastOccurredAt,
                                              String lastStatus,
                                              String lastMessage,
                                              LocalDateTime lastSyncedAt) {
}
