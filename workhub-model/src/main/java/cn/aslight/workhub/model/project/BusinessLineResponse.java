package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * 业务线响应模型。
 */
public record BusinessLineResponse(Long id,
                                   String businessLineCode,
                                   String businessLineName,
                                   String gitlabGroupName,
                                   String description,
                                   Boolean enabled,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
}
