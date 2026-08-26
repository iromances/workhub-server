package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

public record SystemAlertScopeResponse(Long id,
                                       String businessLineCode,
                                       String environmentCode,
                                       String watchMode,
                                       List<SystemAlertScopeServiceResponse> services,
                                       List<String> indexPatterns,
                                       Boolean enabled,
                                       String remark,
                                       LocalDateTime createdAt,
                                       LocalDateTime updatedAt) {
}
