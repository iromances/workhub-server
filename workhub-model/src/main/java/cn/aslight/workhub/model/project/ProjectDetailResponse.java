package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * ProjectDetail 响应模型。
 */
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
