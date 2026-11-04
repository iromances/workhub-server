package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

/** AI 提出的缺陷与运行问题关联；保留原记录版本及实际取证位置，不改写人工工作项。 */
public record AutonomyBugLinkEntity(String id, String caseId, long workItemId, String workItemVersion,
        String snapshotJson, String observationId, String evidencePointer, String signalId, String reason,
        String runId, long caseVersion, LocalDateTime createdAt) { }
