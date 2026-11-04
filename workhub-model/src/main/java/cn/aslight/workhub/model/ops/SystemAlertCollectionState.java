package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

/** 内部持久续读状态；positionJson含PIT句柄，不向Agent或日志输出。 */
public record SystemAlertCollectionState(Long scopeId, String windowId, String definitionJson, String definitionHash,
        LocalDateTime fromTime, LocalDateTime toTime, String positionJson, LocalDateTime latestOccurredAt,
        LocalDateTime coveredUntil, String status, String ownerToken, LocalDateTime leaseUntil,
        long issueCount, long pageNumber, long version, LocalDateTime updatedAt) { }
