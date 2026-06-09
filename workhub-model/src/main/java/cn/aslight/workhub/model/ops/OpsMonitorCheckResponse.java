package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.Map;

public record OpsMonitorCheckResponse(Long id,
                                      String monitorType,
                                      String monitorKey,
                                      String status,
                                      String message,
                                      LocalDateTime checkedAt,
                                      Map<String, Object> detail) {
}
