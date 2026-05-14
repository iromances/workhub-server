package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.UserOptionResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户与项目组成员数据访问接口。
 */
@Mapper
public interface UserMapper {

    @Select("""
            SELECT user_name AS userName,
                   display_name AS displayName,
                   NULL AS projectGroup
            FROM sys_user
            WHERE status = 'ACTIVE'
            ORDER BY user_name ASC
            """)
    List<UserOptionResponse> findActiveUsers();

    @Select("""
            SELECT member_user_name AS userName,
                   member_display_name AS displayName,
                   project_group AS projectGroup
            FROM pm_project_group_member
            WHERE project_group = #{projectGroup}
              AND enabled = 1
            ORDER BY id ASC
            """)
    List<UserOptionResponse> findProjectGroupMembers(@Param("projectGroup") String projectGroup);

    @Insert("""
            INSERT INTO sys_user (
                user_name,
                display_name,
                password_hash,
                status
            ) VALUES (
                #{userName},
                #{displayName},
                '',
                'ACTIVE'
            )
            ON DUPLICATE KEY UPDATE
                display_name = IF(display_name IS NULL OR display_name = '', VALUES(display_name), display_name),
                status = 'ACTIVE',
                updated_at = CURRENT_TIMESTAMP
            """)
    void upsertActiveUser(@Param("userName") String userName, @Param("displayName") String displayName);
}
