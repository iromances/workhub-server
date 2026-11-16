package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提测 AI 审核响应：审核结论、缺陷清单与提测回执。
 */
public record IntakeSubmitTestReviewResponse(Long intakeId,
                                             String reviewStatus,
                                             String reviewMessage,
                                             String conclusion,
                                             String summary,
                                             List<SubmitTestFinding> findings,
                                             String humanConfirmedBy,
                                             LocalDateTime humanConfirmedAt,
                                             String zentaoTestTaskNo,
                                             String zentaoTestTaskUrl,
                                             String jenkinsEnvironmentCode,
                                             Long jenkinsBuildNumber,
                                             String jenkinsBuildUrl,
                                             String jenkinsBuildStatus,
                                             LocalDateTime updatedAt) {

    /**
     * AI 审核缺陷条目。
     */
    public record SubmitTestFinding(String title,
                                    String severity,
                                    String description,
                                    String evidence) {
    }
}
