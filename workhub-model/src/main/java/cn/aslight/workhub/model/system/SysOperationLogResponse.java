package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;

/**
 * 操作日志响应。
 */
public record SysOperationLogResponse(Long id,
                                      String operatorUserName,
                                      String permissionCode,
                                      String actionType,
                                      String targetType,
                                      String targetId,
                                      String beforeSnapshot,
                                      String afterSnapshot,
                                      String result,
                                      String errorMessage,
                                      String ip,
                                      LocalDateTime occurredAt) {
}
