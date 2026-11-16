package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

/** 内部持久续读状态；positionJson含PIT句柄，不向Agent或日志输出。字段序须与表物理列序一致（SELECT * 按位置映射记录构造器；consecutiveFailures/lastFailureMessage 由ALTER追加在表尾）。 */
public record SystemAlertCollectionState(Long scopeId, String windowId, String definitionJson, String definitionHash,
        LocalDateTime fromTime, LocalDateTime toTime, String positionJson, LocalDateTime latestOccurredAt,
        LocalDateTime coveredUntil, String status, String ownerToken, LocalDateTime leaseUntil,
        long issueCount, long pageNumber, long version, LocalDateTime updatedAt,
        long consecutiveFailures, String lastFailureMessage) { }
