package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;

/**
 * 系统配置响应模型。
 */
public record SysConfigResponse(Long id,
                                String configGroup,
                                String configKey,
                                String configName,
                                String valueType,
                                String value,
                                Boolean enabled,
                                String remark,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt) {
}
