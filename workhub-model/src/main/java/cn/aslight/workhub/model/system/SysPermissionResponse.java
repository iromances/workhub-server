package cn.aslight.workhub.model.system;

import java.util.List;

/**
 * 系统权限响应。
 */
public record SysPermissionResponse(String permissionCode,
                                    String permissionName,
                                    String permissionType,
                                    String parentCode,
                                    String routePath,
                                    Integer sortOrder,
                                    Boolean enabled,
                                    List<SysPermissionResponse> children) {
}
