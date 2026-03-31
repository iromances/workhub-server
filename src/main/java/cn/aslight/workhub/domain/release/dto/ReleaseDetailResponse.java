package cn.aslight.workhub.domain.release.dto;

import java.time.LocalDateTime;

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
