package cn.aslight.workhub.domain.project.dto;

import java.time.LocalDateTime;

public record ProjectDetailResponse(Long id,
                                    String code,
                                    String name,
                                    String type,
                                    String ownerUserName,
                                    String status,
                                    String description,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt) {
}
