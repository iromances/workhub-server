package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 研发分析响应模型。
 */
public record DevelopmentAnalysisResponse(Long id,
                                          Long intakeId,
                                          String status,
                                          String message,
                                          DevelopmentAnalysisDraft draft,
                                          LocalDateTime createdAt,
                                          LocalDateTime updatedAt) {
}
