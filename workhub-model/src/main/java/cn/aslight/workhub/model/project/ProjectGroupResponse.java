package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * 项目组响应模型。
 */
public record ProjectGroupResponse(Long id,
                                   String groupName,
                                   String gitlabGroupName,
                                   String description,
                                   Boolean enabled,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
}
