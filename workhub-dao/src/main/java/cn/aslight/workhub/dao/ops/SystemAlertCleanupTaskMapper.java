package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemAlertCleanupTaskMapper {

    @Insert("""
            INSERT INTO ops_system_alert_cleanup_task (
              task_no, message_keyword, business_line_code, environment_code, service_name,
              log_level, event_category, start_time, end_time, status,
              operator_user_name, request_ip
            ) VALUES (
              #{taskNo}, #{messageKeyword}, #{businessLineCode}, #{environmentCode}, #{serviceName},
              #{logLevel}, #{eventCategory}, #{startTime}, #{endTime}, #{status},
              #{operatorUserName}, #{requestIp}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemAlertCleanupTaskEntity entity);

    @Select("""
            SELECT id, task_no AS taskNo, message_keyword AS messageKeyword,
                   business_line_code AS businessLineCode, environment_code AS environmentCode,
                   service_name AS serviceName, log_level AS logLevel, event_category AS eventCategory,
                   start_time AS startTime, end_time AS endTime, status, rule_id AS ruleId,
                   max_event_id AS maxEventId, processed_event_id AS processedEventId,
                   deleted_event_count AS deletedEventCount,
                   deleted_notification_count AS deletedNotificationCount,
                   operator_user_name AS operatorUserName, request_ip AS requestIp,
                   error_message AS errorMessage, created_at AS createdAt, started_at AS startedAt,
                   finished_at AS finishedAt, updated_at AS updatedAt
            FROM ops_system_alert_cleanup_task
            WHERE id = #{id}
            """)
    SystemAlertCleanupTaskEntity findById(Long id);

    @Update("""
            UPDATE ops_system_alert_cleanup_task
            SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP, error_message = NULL
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int claim(Long id);

    @Update("""
            UPDATE ops_system_alert_cleanup_task
            SET rule_id = #{ruleId}, max_event_id = #{maxEventId}
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int setExecutionPlan(@Param("id") Long id,
                         @Param("ruleId") Long ruleId,
                         @Param("maxEventId") Long maxEventId);

    @Update("""
            UPDATE ops_system_alert_cleanup_task
            SET processed_event_id = #{processedEventId},
                deleted_event_count = deleted_event_count + #{deletedEventCount},
                deleted_notification_count = deleted_notification_count + #{deletedNotificationCount}
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int advance(@Param("id") Long id,
                @Param("processedEventId") Long processedEventId,
                @Param("deletedEventCount") long deletedEventCount,
                @Param("deletedNotificationCount") long deletedNotificationCount);

    @Update("""
            UPDATE ops_system_alert_cleanup_task
            SET status = 'SUCCESS', processed_event_id = max_event_id,
                finished_at = CURRENT_TIMESTAMP, error_message = NULL
            WHERE id = #{id} AND status = 'RUNNING'
            """)
    int markSuccess(Long id);

    @Update("""
            UPDATE ops_system_alert_cleanup_task
            SET status = 'FAILED', error_message = #{errorMessage}, finished_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status IN ('PENDING', 'RUNNING')
            """)
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
}
