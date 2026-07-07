package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;

/**
 * 登录日志响应。
 */
public record SysLoginLogResponse(Long id,
                                  String userName,
                                  String loginResult,
                                  String failReason,
                                  String ip,
                                  String userAgent,
                                  LocalDateTime occurredAt) {
}
