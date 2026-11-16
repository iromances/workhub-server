package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.UserOptionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户候选数据访问接口。
 *
 * <p>身份唯一来源是账号管理（sys_user），业务表里的 user_name 字符串都以该表为准做展示映射。</p>
 */
@Mapper
public interface UserMapper {

    /**
     * 活跃登录账号候选，作为全系统选人入口的唯一数据源。
     */
    @Select("""
            SELECT user_name AS userName,
                   display_name AS displayName,
                   NULL AS businessLine
            FROM sys_user
            WHERE status = 'ACTIVE'
              AND deleted = 0
            ORDER BY user_name ASC
            """)
    List<UserOptionResponse> findActiveUsers();

    @Select("""
            SELECT m.member_user_name AS userName,
                   u.display_name AS displayName,
                   bl.business_line_name AS businessLine
            FROM pm_business_line_member m
            JOIN pm_business_line bl ON bl.business_line_code = m.business_line_code
            LEFT JOIN sys_user u ON u.user_name = m.member_user_name AND u.deleted = 0
            WHERE (m.business_line_code = #{businessLine}
                   OR bl.business_line_name = #{businessLine})
              AND m.enabled = 1
            ORDER BY m.id ASC
            """)
    List<UserOptionResponse> findBusinessLineMembers(@Param("businessLine") String businessLine);
}
