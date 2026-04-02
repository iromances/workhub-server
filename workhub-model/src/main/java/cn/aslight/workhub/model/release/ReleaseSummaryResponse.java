package cn.aslight.workhub.model.release;

import java.time.LocalDateTime;

/**
 * ReleaseSummary 响应模型。
 */
public record ReleaseSummaryResponse(Long id,
                                     Long projectId,
                                     String projectName,
                                     String name,
                                     String version,
                                     String status,
                                     LocalDateTime plannedAt,
                                     LocalDateTime releasedAt) {
}
