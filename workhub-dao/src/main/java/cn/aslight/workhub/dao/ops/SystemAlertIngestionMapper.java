package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSyncStateEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SystemAlertIngestionMapper {

    @Select("""
            SELECT id, business_line_code AS businessLineCode, environment_code AS environmentCode,
                   subsystem_name AS subsystemName, service_name AS serviceName, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_subsystem
            WHERE enabled = 1
            ORDER BY id ASC
            """)
    List<SystemAlertSubsystemEntity> findEnabledSubsystems();

    @Select("""
            SELECT id, rule_name AS ruleName, action, match_scope AS matchScope,
                   match_mode AS matchMode, priority, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_rule
            WHERE enabled = 1
            ORDER BY priority ASC, id ASC
            """)
    List<SystemAlertRuleEntity> findEnabledRules();

    @Select({
            "<script>",
            "SELECT id, rule_id AS ruleId, keyword, sort_order AS sortOrder",
            "FROM ops_system_alert_rule_keyword",
            "WHERE rule_id IN",
            "<foreach collection='ruleIds' item='ruleId' open='(' separator=',' close=')'>",
            "#{ruleId}",
            "</foreach>",
            "ORDER BY rule_id ASC, sort_order ASC, id ASC",
            "</script>"
    })
    List<SystemAlertRuleKeywordEntity> findRuleKeywords(@Param("ruleIds") List<Long> ruleIds);

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

    @Select("""
            SELECT subsystem_id AS subsystemId, last_occurred_at AS lastOccurredAt,
                   last_status AS lastStatus, last_message AS lastMessage, last_synced_at AS lastSyncedAt
            FROM ops_system_alert_sync_state WHERE subsystem_id = #{subsystemId}
            """)
    SystemAlertSyncStateEntity findSyncState(Long subsystemId);

    @Insert("""
            INSERT INTO ops_system_alert_event (
                business_line_code, environment_code, subsystem_name, service_name, log_level, event_category,
                title, message, error_type, stack_trace, trace_id, request_id, occurred_at,
                source_type, source_event_id
            ) VALUES (
                #{subsystem.businessLineCode}, #{subsystem.environmentCode}, #{subsystem.subsystemName},
                #{log.serviceName}, #{log.level}, #{eventCategory}, #{log.title}, #{log.message}, #{log.errorType},
                #{log.stackTrace}, #{log.traceId}, #{log.requestId}, #{occurredAt}, 'ELK', #{log.sourceEventId}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertElkEvent(@Param("subsystem") SystemAlertSubsystemEntity subsystem,
                       @Param("log") ElkSystemAlertLog log,
                       @Param("occurredAt") LocalDateTime occurredAt,
                       @Param("eventCategory") String eventCategory);

    @Insert("""
            INSERT INTO ops_system_alert_sync_state (
                subsystem_id, last_occurred_at, last_status, last_message, last_synced_at
            ) VALUES (#{subsystemId}, #{lastOccurredAt}, 'SUCCESS', #{message}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                last_occurred_at = VALUES(last_occurred_at), last_status = 'SUCCESS',
                last_message = VALUES(last_message), last_synced_at = CURRENT_TIMESTAMP
            """)
    int saveSuccess(@Param("subsystemId") Long subsystemId,
                    @Param("lastOccurredAt") LocalDateTime lastOccurredAt,
                    @Param("message") String message);

    @Insert("""
            INSERT INTO ops_system_alert_sync_state (subsystem_id, last_status, last_message, last_synced_at)
            VALUES (#{subsystemId}, 'ERROR', #{message}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE last_status = 'ERROR', last_message = VALUES(last_message),
                                    last_synced_at = CURRENT_TIMESTAMP
            """)
    int saveFailure(@Param("subsystemId") Long subsystemId, @Param("message") String message);
}
