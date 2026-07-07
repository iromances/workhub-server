package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.SysPermissionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统权限数据访问接口。
 */
@Mapper
public interface SysPermissionMapper {

    @Select("""
            SELECT id, permission_code, permission_name, permission_type, parent_code, route_path, api_method, api_pattern,
                   sort_order, enabled, remark, created_at, updated_at
            FROM sys_permission
            WHERE enabled = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<SysPermissionEntity> findEnabledPermissions();

    @Select("""
            SELECT p.permission_code
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_code = p.permission_code
            JOIN sys_role r ON r.id = rp.role_id
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND p.enabled = 1
              AND r.enabled = 1
            ORDER BY p.permission_code ASC
            """)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT p.permission_code
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_code = p.permission_code
            WHERE rp.role_id = #{roleId}
              AND p.enabled = 1
            ORDER BY p.permission_code ASC
            """)
    List<String> findPermissionCodesByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") Long roleId);

    @Insert({
            "<script>",
            "INSERT INTO sys_role_permission (role_id, permission_code) VALUES",
            "<foreach collection='permissionCodes' item='permissionCode' separator=','>",
            "(#{roleId}, #{permissionCode})",
            "</foreach>",
            "</script>"
    })
    int insertRolePermissions(@Param("roleId") Long roleId,
                              @Param("permissionCodes") List<String> permissionCodes);
}
