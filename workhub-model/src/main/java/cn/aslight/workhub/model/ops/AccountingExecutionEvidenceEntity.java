package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record AccountingExecutionEvidenceEntity(Long runId, String businessLineCode, String environmentCode,
        String systemName, String displayName, String snapshotJson, String contentHash, LocalDateTime capturedAt) { }
