package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.SysLoginLogResponse;
import cn.aslight.workhub.model.system.SysOperationLogResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统审计数据访问接口。
 */
@Mapper
public interface SysAuditMapper {

    @Insert("""
            INSERT INTO sys_login_log (user_name, login_result, fail_reason, ip, user_agent)
            VALUES (#{userName}, #{loginResult}, #{failReason}, #{ip}, #{userAgent})
            """)
    int insertLoginLog(@Param("userName") String userName,
                       @Param("loginResult") String loginResult,
                       @Param("failReason") String failReason,
                       @Param("ip") String ip,
                       @Param("userAgent") String userAgent);

    @Insert("""
            INSERT INTO sys_operation_log (
                operator_user_name, permission_code, action_type, target_type, target_id,
                before_snapshot, after_snapshot, result, error_message, ip
            ) VALUES (
                #{operatorUserName}, #{permissionCode}, #{actionType}, #{targetType}, #{targetId},
                #{beforeSnapshot}, #{afterSnapshot}, #{result}, #{errorMessage}, #{ip}
            )
            """)
    int insertOperationLog(@Param("operatorUserName") String operatorUserName,
                           @Param("permissionCode") String permissionCode,
                           @Param("actionType") String actionType,
                           @Param("targetType") String targetType,
                           @Param("targetId") String targetId,
                           @Param("beforeSnapshot") String beforeSnapshot,
                           @Param("afterSnapshot") String afterSnapshot,
                           @Param("result") String result,
                           @Param("errorMessage") String errorMessage,
                           @Param("ip") String ip);

    @Select({
            "<script>",
            "SELECT id, user_name, login_result, fail_reason, ip, user_agent, occurred_at",
            "FROM sys_login_log",
            "<where>",
            "<if test='userName != null and userName != \"\"'>",
            "AND user_name = #{userName}",
            "</if>",
            "<if test='loginResult != null and loginResult != \"\"'>",
            "AND login_result = #{loginResult}",
            "</if>",
            "</where>",
            "ORDER BY occurred_at DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<SysLoginLogResponse> findLoginLogs(@Param("userName") String userName,
                                            @Param("loginResult") String loginResult,
                                            @Param("limit") int limit);

    @Select({
            "<script>",
            "SELECT id, operator_user_name, permission_code, action_type, target_type, target_id, before_snapshot, after_snapshot,",
            "result, error_message, ip, occurred_at",
            "FROM sys_operation_log",
            "<where>",
            "<if test='operatorUserName != null and operatorUserName != \"\"'>",
            "AND operator_user_name = #{operatorUserName}",
            "</if>",
            "<if test='permissionCode != null and permissionCode != \"\"'>",
            "AND permission_code = #{permissionCode}",
            "</if>",
            "<if test='result != null and result != \"\"'>",
            "AND result = #{result}",
            "</if>",
            "</where>",
            "ORDER BY occurred_at DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<SysOperationLogResponse> findOperationLogs(@Param("operatorUserName") String operatorUserName,
                                                    @Param("permissionCode") String permissionCode,
                                                    @Param("result") String result,
                                                    @Param("limit") int limit);
}
