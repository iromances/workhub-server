package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.SysUserEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户数据访问接口。
 */
@Mapper
public interface SysUserMapper {

    @Select({
            "<script>",
            "SELECT id, user_name, display_name, email, mobile, wecom_userid, avatar_url, password_hash, status,",
            "must_change_password, login_fail_count, locked_until, last_login_at, last_login_ip, deleted, created_at, updated_at",
            "FROM sys_user",
            "WHERE deleted = 0",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (user_name LIKE CONCAT('%', #{keyword}, '%') OR display_name LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND status = #{status}",
            "</if>",
            "ORDER BY id ASC",
            "</script>"
    })
    List<SysUserEntity> findAll(@Param("keyword") String keyword, @Param("status") String status);

    @Select("""
            SELECT id, user_name, display_name, email, mobile, wecom_userid, avatar_url, password_hash, status,
                   must_change_password, login_fail_count, locked_until, last_login_at, last_login_ip, deleted, created_at, updated_at
            FROM sys_user
            WHERE id = #{id}
              AND deleted = 0
            """)
    SysUserEntity findById(@Param("id") Long id);

    @Select("""
            SELECT id, user_name, display_name, email, mobile, wecom_userid, avatar_url, password_hash, status,
                   must_change_password, login_fail_count, locked_until, last_login_at, last_login_ip, deleted, created_at, updated_at
            FROM sys_user
            WHERE user_name = #{userName}
              AND deleted = 0
            LIMIT 1
            """)
    SysUserEntity findByUserName(@Param("userName") String userName);

    @Insert("""
            INSERT INTO sys_user (
                user_name, display_name, email, mobile, wecom_userid, avatar_url, password_hash, status,
                must_change_password, login_fail_count, deleted
            ) VALUES (
                #{userName}, #{displayName}, #{email}, #{mobile}, #{wecomUserid}, #{avatarUrl}, #{passwordHash}, #{status},
                #{mustChangePassword}, 0, 0
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUserEntity entity);

    @Update("""
            UPDATE sys_user
            SET display_name = #{displayName},
                email = #{email},
                mobile = #{mobile},
                wecom_userid = #{wecomUserid},
                avatar_url = #{avatarUrl},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(SysUserEntity entity);

    @Update("""
            UPDATE sys_user
            SET display_name = #{displayName},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateProfile(@Param("id") Long id, @Param("displayName") String displayName);

    @Update("""
            UPDATE sys_user
            SET avatar_url = #{avatarUrl},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);

    @Update("""
            UPDATE sys_user
            SET status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("""
            UPDATE sys_user
            SET password_hash = #{passwordHash},
                must_change_password = #{mustChangePassword},
                login_fail_count = 0,
                locked_until = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("mustChangePassword") boolean mustChangePassword);

    @Update("""
            UPDATE sys_user
            SET login_fail_count = #{loginFailCount},
                locked_until = #{lockedUntil},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateLoginFailure(@Param("id") Long id,
                           @Param("loginFailCount") int loginFailCount,
                           @Param("lockedUntil") LocalDateTime lockedUntil);

    @Update("""
            UPDATE sys_user
            SET login_fail_count = 0,
                locked_until = NULL,
                last_login_at = CURRENT_TIMESTAMP,
                last_login_ip = #{ip},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markLoginSuccess(@Param("id") Long id, @Param("ip") String ip);

    @Delete("""
            UPDATE sys_user
            SET deleted = 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int softDelete(@Param("id") Long id);
}
