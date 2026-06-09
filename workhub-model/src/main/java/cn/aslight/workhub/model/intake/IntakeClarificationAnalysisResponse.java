package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求澄清分析响应。
 */
public record IntakeClarificationAnalysisResponse(Long id,
                                                  Long intakeId,
                                                  String status,
                                                  String message,
                                                  List<IntakeClarificationItem> items,
                                                  LocalDateTime createdAt,
                                                  LocalDateTime updatedAt) {
}
