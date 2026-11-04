package cn.aslight.workhub.model.autonomy;

import java.time.LocalDateTime;

/** 不可变关闭凭据；复发只改变问题当前状态，不覆盖原判断或发布证据。 */
public record AutonomyCaseResolution(String id,String caseId,long caseVersion,long resolvedVersion,long evidenceVersion,
        String runId,String verificationId,String planId,String changeSetId,String manifestHash,String proposalId,String proposalHash,
        String artifactId,String artifactHash,String checkIdsJson,LocalDateTime windowStart,LocalDateTime windowEnd,
        String reason,LocalDateTime createdAt) { }
