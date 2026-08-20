package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SystemAlertMapper {

    @Select("""
            <script>
            SELECT id,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   subsystem_name AS subsystemName,
                   service_name AS serviceName,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM ops_system_alert_subsystem
            WHERE 1 = 1
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
                subsystem_name LIKE CONCAT('%', #{keyword}, '%')
                OR service_name LIKE CONCAT('%', #{keyword}, '%')
                OR remark LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY business_line_code ASC, environment_code ASC, subsystem_name ASC, id ASC
            </script>
            """)
    List<SystemAlertSubsystemEntity> findSubsystems(@Param("businessLineCode") String businessLineCode,
                                                    @Param("environmentCode") String environmentCode,
                                                    @Param("enabledOnly") boolean enabledOnly,
                                                    @Param("keyword") String keyword);

    @Select("""
            SELECT id,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   subsystem_name AS subsystemName,
                   service_name AS serviceName,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM ops_system_alert_subsystem
            WHERE id = #{id}
            """)
    SystemAlertSubsystemEntity findSubsystemById(Long id);

    @Select("""
            SELECT id,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   subsystem_name AS subsystemName,
                   service_name AS serviceName,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM ops_system_alert_subsystem
            WHERE business_line_code = #{businessLineCode}
              AND environment_code = #{environmentCode}
              AND service_name = #{serviceName}
            """)
    SystemAlertSubsystemEntity findSubsystemByIdentity(@Param("businessLineCode") String businessLineCode,
                                                       @Param("environmentCode") String environmentCode,
                                                       @Param("serviceName") String serviceName);

    @Select({
            "<script>",
            "SELECT id, subsystem_id AS subsystemId, index_pattern AS indexPattern, sort_order AS sortOrder",
            "FROM ops_system_alert_subsystem_index",
            "WHERE subsystem_id IN",
            "<foreach collection='subsystemIds' item='subsystemId' open='(' separator=',' close=')'>",
            "#{subsystemId}",
            "</foreach>",
            "ORDER BY subsystem_id ASC, sort_order ASC, id ASC",
            "</script>"
    })
    List<SystemAlertSubsystemIndexPatternEntity> findIndexPatterns(
            @Param("subsystemIds") List<Long> subsystemIds);

    @Insert("""
            INSERT INTO ops_system_alert_subsystem (
                business_line_code,
                environment_code,
                subsystem_name,
                service_name,
                enabled,
                remark
            ) VALUES (
                #{businessLineCode},
                #{environmentCode},
                #{subsystemName},
                #{serviceName},
                #{enabled},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSubsystem(SystemAlertSubsystemEntity entity);

    @Insert("""
            INSERT INTO ops_system_alert_subsystem_index (subsystem_id, index_pattern, sort_order)
            VALUES (#{subsystemId}, #{indexPattern}, #{sortOrder})
            """)
    int insertIndexPattern(@Param("subsystemId") Long subsystemId,
                           @Param("indexPattern") String indexPattern,
                           @Param("sortOrder") int sortOrder);

    @Update("""
            UPDATE ops_system_alert_subsystem
            SET business_line_code = #{businessLineCode},
                environment_code = #{environmentCode},
                subsystem_name = #{subsystemName},
                service_name = #{serviceName},
                enabled = #{enabled},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int updateSubsystem(SystemAlertSubsystemEntity entity);

    @Delete("DELETE FROM ops_system_alert_subsystem_index WHERE subsystem_id = #{subsystemId}")
    int deleteIndexPatterns(Long subsystemId);

    @Delete("DELETE FROM ops_system_alert_subsystem WHERE id = #{id}")
    int deleteSubsystemById(Long id);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ops_system_alert_event e
            WHERE 1 = 1
            <if test="businessLineCode != null and businessLineCode != ''">
              AND e.business_line_code = #{businessLineCode}
            </if>
            <if test="environmentCode != null and environmentCode != ''">
              AND e.environment_code = #{environmentCode}
            </if>
            <if test="serviceName != null and serviceName != ''">
              AND e.service_name = #{serviceName}
            </if>
            <if test="level != null and level != ''">
              AND e.log_level = #{level}
            </if>
            <if test="eventCategory != null and eventCategory != ''">
              AND e.event_category = #{eventCategory}
            </if>
            <if test="startTime != null">
              AND e.occurred_at &gt;= #{startTime}
            </if>
            <if test="endTime != null">
              AND e.occurred_at &lt;= #{endTime}
            </if>
            </script>
            """)
    int countEvents(@Param("businessLineCode") String businessLineCode,
                    @Param("environmentCode") String environmentCode,
                    @Param("serviceName") String serviceName,
                    @Param("level") String level,
                    @Param("eventCategory") String eventCategory,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime);

    @Select("""
            <script>
            SELECT e.business_line_code AS businessLineCode,
                   e.environment_code AS environmentCode,
                   COALESCE(s.subsystem_name, e.subsystem_name) AS subsystemName,
                   e.service_name AS serviceName,
                   COUNT(1) AS errorCount,
                   MAX(e.occurred_at) AS latestOccurredAt
            FROM ops_system_alert_event e
            LEFT JOIN ops_system_alert_subsystem s
              ON s.business_line_code = e.business_line_code
             AND s.environment_code = e.environment_code
             AND s.service_name = e.service_name
            WHERE 1 = 1
            <if test="businessLineCode != null and businessLineCode != ''">
              AND e.business_line_code = #{businessLineCode}
            </if>
            <if test="environmentCode != null and environmentCode != ''">
              AND e.environment_code = #{environmentCode}
            </if>
            <if test="serviceName != null and serviceName != ''">
              AND e.service_name = #{serviceName}
            </if>
            <if test="level != null and level != ''">
              AND e.log_level = #{level}
            </if>
            <if test="eventCategory != null and eventCategory != ''">
              AND e.event_category = #{eventCategory}
            </if>
            <if test="startTime != null">
              AND e.occurred_at &gt;= #{startTime}
            </if>
            <if test="endTime != null">
              AND e.occurred_at &lt;= #{endTime}
            </if>
            GROUP BY e.business_line_code,
                     e.environment_code,
                     COALESCE(s.subsystem_name, e.subsystem_name),
                     e.service_name
            ORDER BY errorCount DESC, latestOccurredAt DESC
            LIMIT 50
            </script>
            """)
    List<SystemAlertSubsystemSummaryResponse> summarizeEvents(@Param("businessLineCode") String businessLineCode,
                                                              @Param("environmentCode") String environmentCode,
                                                              @Param("serviceName") String serviceName,
                                                              @Param("level") String level,
                                                              @Param("eventCategory") String eventCategory,
                                                              @Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);

    @Select("""
            <script>
            SELECT e.id AS id,
                   e.business_line_code AS businessLineCode,
                   e.environment_code AS environmentCode,
                   COALESCE(s.subsystem_name, e.subsystem_name) AS subsystemName,
                   e.service_name AS serviceName,
                   e.log_level AS level,
                   e.event_category AS eventCategory,
                   e.title AS title,
                   e.message AS message,
                   e.error_type AS errorType,
                   e.stack_trace AS stackTrace,
                   e.trace_id AS traceId,
                   e.request_id AS requestId,
                   e.occurred_at AS occurredAt,
                   e.source_type AS sourceType
            FROM ops_system_alert_event e
            LEFT JOIN ops_system_alert_subsystem s
              ON s.business_line_code = e.business_line_code
             AND s.environment_code = e.environment_code
             AND s.service_name = e.service_name
            WHERE 1 = 1
            <if test="businessLineCode != null and businessLineCode != ''">
              AND e.business_line_code = #{businessLineCode}
            </if>
            <if test="environmentCode != null and environmentCode != ''">
              AND e.environment_code = #{environmentCode}
            </if>
            <if test="serviceName != null and serviceName != ''">
              AND e.service_name = #{serviceName}
            </if>
            <if test="level != null and level != ''">
              AND e.log_level = #{level}
            </if>
            <if test="eventCategory != null and eventCategory != ''">
              AND e.event_category = #{eventCategory}
            </if>
            <if test="startTime != null">
              AND e.occurred_at &gt;= #{startTime}
            </if>
            <if test="endTime != null">
              AND e.occurred_at &lt;= #{endTime}
            </if>
            ORDER BY e.occurred_at DESC, e.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SystemAlertEventResponse> findEvents(@Param("businessLineCode") String businessLineCode,
                                              @Param("environmentCode") String environmentCode,
                                              @Param("serviceName") String serviceName,
                                              @Param("level") String level,
                                              @Param("eventCategory") String eventCategory,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);
}
