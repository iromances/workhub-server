package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.SysJobConfigEntity;
import cn.aslight.workhub.model.system.SysJobExecutionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** REQ-9f3c2a84：系统定时任务配置与执行历史（DB 为唯一调度事实源）。 */
@Mapper
public interface SysJobMapper {

    String CONFIG_COLUMNS = """
            id, job_code AS jobCode, job_name AS jobName, schedule_type AS scheduleType,
            schedule_value AS scheduleValue, default_schedule_value AS defaultScheduleValue,
            enabled, allow_manual_trigger AS allowManualTrigger, param_desc AS paramDesc,
            remark, created_at AS createdAt, updated_at AS updatedAt
            """;

    String EXECUTION_COLUMNS = """
            id, job_code AS jobCode, trigger_type AS triggerType, params, status,
            started_at AS startedAt, finished_at AS finishedAt, duration_ms AS durationMs,
            error_message AS errorMessage, hostname
            """;

    @Select("SELECT " + CONFIG_COLUMNS + " FROM sys_job_config ORDER BY job_code ASC")
    List<SysJobConfigEntity> findAllConfigs();

    @Select("SELECT " + CONFIG_COLUMNS + " FROM sys_job_config WHERE job_code = #{jobCode}")
    SysJobConfigEntity findConfigByCode(@Param("jobCode") String jobCode);

    @Insert("""
            INSERT INTO sys_job_config (
                job_code, job_name, schedule_type, schedule_value, default_schedule_value,
                enabled, allow_manual_trigger, param_desc, remark
            ) VALUES (
                #{jobCode}, #{jobName}, #{scheduleType}, #{scheduleValue}, #{defaultScheduleValue},
                #{enabled}, #{allowManualTrigger}, #{paramDesc}, #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConfig(SysJobConfigEntity entity);

    @Update("""
            UPDATE sys_job_config
            SET schedule_type = #{scheduleType},
                schedule_value = #{scheduleValue},
                updated_at = CURRENT_TIMESTAMP
            WHERE job_code = #{jobCode}
            """)
    int updateSchedule(@Param("jobCode") String jobCode,
                       @Param("scheduleType") String scheduleType,
                       @Param("scheduleValue") String scheduleValue);

    @Update("UPDATE sys_job_config SET enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP WHERE job_code = #{jobCode}")
    int updateEnabled(@Param("jobCode") String jobCode, @Param("enabled") boolean enabled);

    @Insert("""
            INSERT INTO sys_job_execution_log (
                job_code, trigger_type, params, status, started_at, hostname
            ) VALUES (
                #{jobCode}, #{triggerType}, #{params}, #{status}, #{startedAt}, #{hostname}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExecution(SysJobExecutionEntity entity);

    @Update("""
            UPDATE sys_job_execution_log
            SET status = #{status},
                finished_at = #{finishedAt},
                duration_ms = #{durationMs},
                error_message = #{errorMessage}
            WHERE id = #{id}
            """)
    int finishExecution(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("finishedAt") LocalDateTime finishedAt,
                        @Param("durationMs") long durationMs,
                        @Param("errorMessage") String errorMessage);

    @Select("SELECT " + EXECUTION_COLUMNS + " FROM sys_job_execution_log WHERE job_code = #{jobCode} ORDER BY started_at DESC, id DESC LIMIT 1")
    SysJobExecutionEntity findLastExecution(@Param("jobCode") String jobCode);

    @Select("""
            <script>
            SELECT COUNT(1) FROM sys_job_execution_log
            WHERE job_code = #{jobCode}
            <if test="statuses != null and statuses.size() > 0">
                AND status IN
                <foreach collection="statuses" item="item" open="(" separator="," close=")">#{item}</foreach>
            </if>
            </script>
            """)
    long countExecutions(@Param("jobCode") String jobCode, @Param("statuses") List<String> statuses);

    @Select("""
            <script>
            SELECT
            """ + EXECUTION_COLUMNS + """
            FROM sys_job_execution_log
            WHERE job_code = #{jobCode}
            <if test="statuses != null and statuses.size() > 0">
                AND status IN
                <foreach collection="statuses" item="item" open="(" separator="," close=")">#{item}</foreach>
            </if>
            ORDER BY started_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SysJobExecutionEntity> findExecutions(@Param("jobCode") String jobCode,
                                               @Param("statuses") List<String> statuses,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    @Delete("DELETE FROM sys_job_execution_log WHERE started_at < #{cutoff}")
    int deleteExecutionsBefore(@Param("cutoff") LocalDateTime cutoff);
}
