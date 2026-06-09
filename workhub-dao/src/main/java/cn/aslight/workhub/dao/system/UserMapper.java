package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.UserOptionResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户候选数据访问接口。
 */
@Mapper
public interface UserMapper {

    @Select("""
            SELECT user_name AS userName,
                   display_name AS displayName,
                   NULL AS businessLine
            FROM pm_developer_resource
            WHERE enabled = 1
            ORDER BY user_name ASC
            """)
    List<UserOptionResponse> findActiveUsers();

    @Select("""
            SELECT member_user_name AS userName,
                   member_display_name AS displayName,
                   business_line AS businessLine
            FROM pm_business_line_member
            WHERE business_line = #{businessLine}
              AND enabled = 1
            ORDER BY id ASC
            """)
    List<UserOptionResponse> findBusinessLineMembers(@Param("businessLine") String businessLine);

    @Insert("""
            INSERT INTO pm_developer_resource (
                user_name,
                display_name,
                enabled
            ) VALUES (
                #{userName},
                #{displayName},
                1
            )
            ON DUPLICATE KEY UPDATE
                display_name = IF(display_name IS NULL OR display_name = '', VALUES(display_name), display_name),
                enabled = 1,
                updated_at = CURRENT_TIMESTAMP
            """)
    void upsertActiveUser(@Param("userName") String userName, @Param("displayName") String displayName);
}
