package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 研发拆解确认响应。
 */
public record DevelopmentAnalysisConfirmResponse(Long intakeId,
                                                 List<Long> workItemIds,
                                                 String zentaoSyncStatus,
                                                 String message) {
}
