package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OpsMonitorMapper {

    @Select("""
            <script>
            SELECT id,
                   monitor_type AS monitorType,
                   monitor_key AS monitorKey,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   name,
                   admin_base_url AS adminBaseUrl,
                   username,
                   password_encrypted AS passwordEncrypted,
                   xxl_job_database_name AS xxlJobDatabaseName,
                   executor_app_name AS executorAppName,
                   job_handler AS jobHandler,
                   job_desc AS jobDesc,
                   mq_topic AS mqTopic,
                   mq_consumer_group AS mqConsumerGroup,
                   mq_lag_threshold AS mqLagThreshold,
                   enabled,
                   last_status AS lastStatus,
                   last_message AS lastMessage,
                   last_checked_at AS lastCheckedAt,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM ops_monitor_config
            WHERE 1 = 1
            <if test="monitorType != null and monitorType != ''">
              AND monitor_type = #{monitorType}
            </if>
            <if test="businessLineCode != null and businessLineCode != ''">
              AND business_line_code = #{businessLineCode}
            </if>
            <if test="environmentCode != null and environmentCode != ''">
              AND environment_code = #{environmentCode}
            </if>
            <if test="enabledOnly">
              AND enabled = 1
            </if>
            <if test="keyword != null and keyword != ''">
              AND (
                monitor_key LIKE CONCAT('%', #{keyword}, '%')
                OR name LIKE CONCAT('%', #{keyword}, '%')
                OR xxl_job_database_name LIKE CONCAT('%', #{keyword}, '%')
                OR job_handler LIKE CONCAT('%', #{keyword}, '%')
                OR job_desc LIKE CONCAT('%', #{keyword}, '%')
                OR mq_topic LIKE CONCAT('%', #{keyword}, '%')
                OR mq_consumer_group LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY enabled DESC, business_line_code ASC, environment_code ASC, monitor_key ASC
            </script>
            """)
    List<OpsMonitorEntity> findAll(@Param("monitorType") String monitorType,
                                   @Param("businessLineCode") String businessLineCode,
                                   @Param("environmentCode") String environmentCode,
                                   @Param("keyword") String keyword,
                                   @Param("enabledOnly") boolean enabledOnly);

    @Select("""
            SELECT id,
                   monitor_type AS monitorType,
                   monitor_key AS monitorKey,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   name,
                   admin_base_url AS adminBaseUrl,
                   username,
                   password_encrypted AS passwordEncrypted,
                   xxl_job_database_name AS xxlJobDatabaseName,
                   executor_app_name AS executorAppName,
                   job_handler AS jobHandler,
                   job_desc AS jobDesc,
                   mq_topic AS mqTopic,
                   mq_consumer_group AS mqConsumerGroup,
                   mq_lag_threshold AS mqLagThreshold,
                   enabled,
                   last_status AS lastStatus,
                   last_message AS lastMessage,
                   last_checked_at AS lastCheckedAt,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM ops_monitor_config
            WHERE id = #{id}
            """)
    OpsMonitorEntity findById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   monitor_type AS monitorType,
                   monitor_key AS monitorKey,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   name,
                   admin_base_url AS adminBaseUrl,
                   username,
                   password_encrypted AS passwordEncrypted,
                   xxl_job_database_name AS xxlJobDatabaseName,
                   executor_app_name AS executorAppName,
                   job_handler AS jobHandler,
                   job_desc AS jobDesc,
                   mq_topic AS mqTopic,
                   mq_consumer_group AS mqConsumerGroup,
                   mq_lag_threshold AS mqLagThreshold,
                   enabled,
                   last_status AS lastStatus,
                   last_message AS lastMessage,
                   last_checked_at AS lastCheckedAt,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM ops_monitor_config
            WHERE monitor_key = #{monitorKey}
            """)
    OpsMonitorEntity findByMonitorKey(@Param("monitorKey") String monitorKey);

    @Insert("""
            INSERT INTO ops_monitor_config (
                monitor_type,
                monitor_key,
                business_line_code,
                environment_code,
                name,
                admin_base_url,
                username,
                password_encrypted,
                xxl_job_database_name,
                executor_app_name,
                job_handler,
                job_desc,
                mq_topic,
                mq_consumer_group,
                mq_lag_threshold,
                enabled,
                remark
            ) VALUES (
                #{monitorType},
                #{monitorKey},
                #{businessLineCode},
                #{environmentCode},
                #{name},
                #{adminBaseUrl},
                #{username},
                #{passwordEncrypted},
                #{xxlJobDatabaseName},
                #{executorAppName},
                #{jobHandler},
                #{jobDesc},
                #{mqTopic},
                #{mqConsumerGroup},
                #{mqLagThreshold},
                #{enabled},
                #{remark}
            )
            """)
    void insert(OpsMonitorEntity entity);

    @Update("""
            UPDATE ops_monitor_config
            SET monitor_type = #{monitorType},
                monitor_key = #{monitorKey},
                business_line_code = #{businessLineCode},
                environment_code = #{environmentCode},
                name = #{name},
                admin_base_url = #{adminBaseUrl},
                username = #{username},
                password_encrypted = #{passwordEncrypted},
                xxl_job_database_name = #{xxlJobDatabaseName},
                executor_app_name = #{executorAppName},
                job_handler = #{jobHandler},
                job_desc = #{jobDesc},
                mq_topic = #{mqTopic},
                mq_consumer_group = #{mqConsumerGroup},
                mq_lag_threshold = #{mqLagThreshold},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(OpsMonitorEntity entity);

    @Update("""
            UPDATE ops_monitor_config
            SET last_status = #{status},
                last_message = #{message},
                last_checked_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateCheckResult(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("message") String message);

    @Delete("DELETE FROM ops_monitor_config WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
