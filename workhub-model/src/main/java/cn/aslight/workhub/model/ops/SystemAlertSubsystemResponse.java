package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record SystemAlertSubsystemResponse(Long id,
                                           String businessLineCode,
                                           String environmentCode,
                                           String subsystemName,
                                           String serviceName,
                                           Boolean enabled,
                                           String remark,
                                           LocalDateTime createdAt,
                                           LocalDateTime updatedAt) {
}
