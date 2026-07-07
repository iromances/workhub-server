package cn.aslight.workhub.dao.system;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户角色关系数据访问接口。
 */
@Mapper
public interface SysUserRoleMapper {

    @Select("""
            SELECT role_id
            FROM sys_user_role
            WHERE user_id = #{userId}
            ORDER BY role_id ASC
            """)
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Insert({
            "<script>",
            "INSERT INTO sys_user_role (user_id, role_id) VALUES",
            "<foreach collection='roleIds' item='roleId' separator=','>",
            "(#{userId}, #{roleId})",
            "</foreach>",
            "</script>"
    })
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Select("""
            SELECT COUNT(1)
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            JOIN sys_user u ON u.id = ur.user_id
            WHERE r.role_code = 'SUPER_ADMIN'
              AND r.enabled = 1
              AND u.status = 'ACTIVE'
              AND u.deleted = 0
              AND u.id != #{excludedUserId}
            """)
    int countOtherActiveSuperAdmins(@Param("excludedUserId") Long excludedUserId);
}
