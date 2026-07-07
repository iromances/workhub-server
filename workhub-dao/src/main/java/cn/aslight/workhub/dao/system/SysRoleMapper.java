package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.SysRoleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统角色数据访问接口。
 */
@Mapper
public interface SysRoleMapper {

    @Select({
            "<script>",
            "SELECT id, role_code, role_name, role_type, enabled, built_in, remark, created_at, updated_at",
            "FROM sys_role",
            "<where>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (role_code LIKE CONCAT('%', #{keyword}, '%') OR role_name LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='enabled != null'>",
            "AND enabled = #{enabled}",
            "</if>",
            "</where>",
            "ORDER BY built_in DESC, id ASC",
            "</script>"
    })
    List<SysRoleEntity> findAll(@Param("keyword") String keyword, @Param("enabled") Boolean enabled);

    @Select("""
            SELECT id, role_code, role_name, role_type, enabled, built_in, remark, created_at, updated_at
            FROM sys_role
            WHERE id = #{id}
            """)
    SysRoleEntity findById(@Param("id") Long id);

    @Select("""
            SELECT id, role_code, role_name, role_type, enabled, built_in, remark, created_at, updated_at
            FROM sys_role
            WHERE role_code = #{roleCode}
            LIMIT 1
            """)
    SysRoleEntity findByCode(@Param("roleCode") String roleCode);

    @Select("""
            SELECT r.id, r.role_code, r.role_name, r.role_type, r.enabled, r.built_in, r.remark, r.created_at, r.updated_at
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id ASC
            """)
    List<SysRoleEntity> findByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO sys_role (role_code, role_name, role_type, enabled, built_in, remark)
            VALUES (#{roleCode}, #{roleName}, #{roleType}, #{enabled}, #{builtIn}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysRoleEntity entity);

    @Update("""
            UPDATE sys_role
            SET role_code = #{roleCode},
                role_name = #{roleName},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(SysRoleEntity entity);

    @Update("""
            UPDATE sys_role
            SET enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("enabled") boolean enabled);

    @Delete("DELETE FROM sys_role WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
