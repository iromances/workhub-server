package cn.aslight.workhub.domain.intake.dto;

import java.util.List;

public record IntakeStructuredData(String category,
                                   String approvalTitle,
                                   String approvalCode,
                                   String requirementName,
                                   String requirementSummary,
                                   String department,
                                   String businessLine,
                                   String remark,
                                   String estimatedEffort,
                                   String plannedDueDate,
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
