package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * ProjectDetail 响应模型。
 */
public record ProjectDetailResponse(Long id,
                                    String code,
                                    String name,
                                    String type,
                                    String businessLineCode,
                                    String businessLine,
                                    String ownerUserName,
                                    String status,
                                    String description,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt) {

    public ProjectDetailResponse(Long id,
                                 String code,
                                 String name,
                                 String type,
                                 String businessLine,
                                 String ownerUserName,
                                 String status,
                                 String description,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
        this(id, code, name, type, null, businessLine, ownerUserName, status, description, createdAt, updatedAt);
    }
}
