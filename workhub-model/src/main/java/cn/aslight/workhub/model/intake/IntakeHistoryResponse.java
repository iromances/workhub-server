package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 需求管理历史响应模型。
 */
public record IntakeHistoryResponse(Long id,
                                    String actionType,
                                    String actionSummary,
                                    String detailText,
                                    String operatorUserName,
                                    LocalDateTime createdAt) {
}
