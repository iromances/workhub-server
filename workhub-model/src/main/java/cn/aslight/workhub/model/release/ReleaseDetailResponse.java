package cn.aslight.workhub.model.release;

import java.time.LocalDateTime;

/**
 * ReleaseDetail 响应模型。
 */
public record ReleaseDetailResponse(Long id,
                                    Long projectId,
                                    String projectName,
                                    String name,
                                    String version,
                                    String status,
                                    LocalDateTime plannedAt,
                                    LocalDateTime releasedAt,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt) {
}
