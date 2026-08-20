package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

public record SystemAlertSubsystemResponse(Long id,
                                           String businessLineCode,
                                           String environmentCode,
                                           String subsystemName,
                                           String serviceName,
                                           List<String> indexPatterns,
                                           Boolean enabled,
                                           String remark,
                                           LocalDateTime createdAt,
                                           LocalDateTime updatedAt) {
}
