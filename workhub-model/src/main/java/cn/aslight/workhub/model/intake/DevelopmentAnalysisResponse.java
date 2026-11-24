package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 研发分析响应模型。
 */
public record DevelopmentAnalysisResponse(Long id,
                                          Long intakeId,
                                          String status,
                                          String message,
                                          boolean retryable,
                                          LocalDateTime startedAt,
                                          LocalDateTime finishedAt,
                                          DevelopmentAnalysisDraft draft,
                                          LocalDateTime createdAt,
                                          LocalDateTime updatedAt,
                                          String errorStack) {
    public DevelopmentAnalysisResponse(Long id, Long intakeId, String status, String message, boolean retryable,
                                       LocalDateTime startedAt, LocalDateTime finishedAt, DevelopmentAnalysisDraft draft,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, intakeId, status, message, retryable, startedAt, finishedAt, draft, createdAt, updatedAt, null);
    }
}
