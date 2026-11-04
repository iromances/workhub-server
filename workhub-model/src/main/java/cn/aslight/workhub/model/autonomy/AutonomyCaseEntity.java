package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

/** 问题处理版本与观察次数独立，重复轮询不会推进审批绑定的处理版本。 */
public record AutonomyCaseEntity(String id, String fingerprint, String businessLineCode, String environmentCode,
                                 String sourceType, String subjectKey, String category, String title, String status,
                                 String activeRunId, String latestProposalId, long occurrenceCount, long evidenceVersion,
                                 long version, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt,
                                 LocalDateTime lastObservedAt, LocalDateTime resolvedAt,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) { }
