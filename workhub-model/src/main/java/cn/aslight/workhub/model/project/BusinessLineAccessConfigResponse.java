package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * 业务线访问地址响应。
 */
public record BusinessLineAccessConfigResponse(Long id,
                                               String environmentCode,
                                               String endpointType,
                                               String endpointName,
                                               String endpointUrl,
                                               String pathPrefix,
                                               Integer sortOrder,
                                               Boolean enabled,
                                               LocalDateTime createdAt,
                                               LocalDateTime updatedAt) {
}
