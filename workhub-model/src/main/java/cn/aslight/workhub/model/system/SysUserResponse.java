package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户响应。
 */
public record SysUserResponse(Long id,
                              String userName,
                              String displayName,
                              String email,
                              String mobile,
                              String wecomUserid,
                              String avatarUrl,
                              String status,
                              Boolean mustChangePassword,
                              Integer loginFailCount,
                              LocalDateTime lockedUntil,
                              LocalDateTime lastLoginAt,
                              String lastLoginIp,
                              List<String> roleCodes,
                              LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
}
