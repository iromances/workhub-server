package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;

/**
 * 系统角色响应。
 */
public record SysRoleResponse(Long id,
                              String roleCode,
                              String roleName,
                              String roleType,
                              Boolean enabled,
                              Boolean builtIn,
                              String remark,
                              LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
}
