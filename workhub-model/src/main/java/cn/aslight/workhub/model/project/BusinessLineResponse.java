package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务线响应模型。
 */
public record BusinessLineResponse(Long id,
                                   String businessLineCode,
                                   String businessLineName,
                                   String gitlabGroupName,
                                   String description,
                                   Boolean enabled,
                                   List<BusinessLineAccessConfigResponse> accessConfigs,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
}
