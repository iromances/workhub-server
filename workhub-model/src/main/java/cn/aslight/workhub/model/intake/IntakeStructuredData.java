package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * IntakeStructuredData 模型。
 */
public record IntakeStructuredData(String category,
                                   String approvalTitle,
                                   String proposerName,
                                   String developmentOwnerUserName,
                                   String approvalCode,
                                   String submittedTime,
                                   String requirementType,
                                   String developmentBranchName,
                                   String zentaoUrl,
                                   String requirementDigest,
                                   String requirementName,
                                   String requirementSummary,
                                   String department,
                                   String businessLine,
                                   String remark,
                                   String estimatedEffort,
                                   String plannedDueDate,
                                   String developmentStartedDate,
                                   String actualEffort,
                                   String testingStartedDate,
                                   String actualCompletedTime,
                                   String acceptanceTime,
                                   String releasedTime,
                                   String projectHint,
                                   List<IntakeStructuredField> fields,
                                   List<IntakeAttachmentSummary> attachmentSummaries) {

    public String requirementNameOrTitle() {
        if (requirementName != null && !requirementName.trim().isEmpty()) {
            return requirementName.trim();
        }
        if (approvalTitle != null && !approvalTitle.trim().isEmpty()) {
            return approvalTitle.trim();
        }
        return null;
    }
}
