package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeIndexEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeSyncStateEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SystemAlertScopeIngestionMapper {

    @Select("""
            SELECT id, business_line_code AS businessLineCode, environment_code AS environmentCode,
                   watch_mode AS watchMode, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_scope WHERE enabled = 1 ORDER BY id
            """)
    List<SystemAlertScopeEntity> findEnabledScopes();

    @Select({
            "<script>",
            "SELECT id, scope_id AS scopeId, subsystem_name AS subsystemName, service_name AS serviceName,",
            "enabled, sort_order AS sortOrder FROM ops_system_alert_scope_service WHERE scope_id IN",
            "<foreach collection='scopeIds' item='scopeId' open='(' separator=',' close=')'>#{scopeId}</foreach>",
            "ORDER BY scope_id, sort_order, id",
            "</script>"
    })
    List<SystemAlertScopeServiceEntity> findServices(@Param("scopeIds") List<Long> scopeIds);

    @Select({
            "<script>",
            "SELECT id, scope_id AS scopeId, index_pattern AS indexPattern, sort_order AS sortOrder",
            "FROM ops_system_alert_scope_index WHERE scope_id IN",
            "<foreach collection='scopeIds' item='scopeId' open='(' separator=',' close=')'>#{scopeId}</foreach>",
            "ORDER BY scope_id, sort_order, id",
            "</script>"
    })
    List<SystemAlertScopeIndexEntity> findIndexPatterns(@Param("scopeIds") List<Long> scopeIds);

    @Select("""
            SELECT id, rule_name AS ruleName, action, match_scope AS matchScope,
                   match_mode AS matchMode, priority, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_rule WHERE enabled = 1 ORDER BY priority, id
            """)
    List<SystemAlertRuleEntity> findEnabledRules();

    @Select({
            "<script>",
            "SELECT id, rule_id AS ruleId, keyword, sort_order AS sortOrder",
            "FROM ops_system_alert_rule_keyword WHERE rule_id IN",
            "<foreach collection='ruleIds' item='ruleId' open='(' separator=',' close=')'>#{ruleId}</foreach>",
            "ORDER BY rule_id, sort_order, id",
            "</script>"
    })
    List<SystemAlertRuleKeywordEntity> findRuleKeywords(@Param("ruleIds") List<Long> ruleIds);

    @Select("""
            SELECT scope_id AS scopeId, last_occurred_at AS lastOccurredAt,
                   last_status AS lastStatus, last_message AS lastMessage, last_synced_at AS lastSyncedAt
            FROM ops_system_alert_scope_sync_state WHERE scope_id = #{scopeId}
            """)
    SystemAlertScopeSyncStateEntity findSyncState(Long scopeId);

    @Insert("""
            INSERT INTO ops_system_alert_event (
                business_line_code, environment_code, subsystem_name, service_name, log_level, event_category,
                title, message, error_type, stack_trace, trace_id, request_id, occurred_at,
                source_type, source_event_id
            ) VALUES (
                #{scope.businessLineCode}, #{scope.environmentCode}, #{subsystemName},
                #{log.serviceName}, #{log.level}, #{eventCategory}, #{log.title}, #{log.message}, #{log.errorType},
                #{log.stackTrace}, #{log.traceId}, #{log.requestId}, #{occurredAt}, 'ELK', #{log.sourceEventId}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertElkEvent(@Param("scope") SystemAlertScopeEntity scope,
                       @Param("subsystemName") String subsystemName,
                       @Param("log") ElkSystemAlertLog log,
                       @Param("occurredAt") LocalDateTime occurredAt,
                       @Param("eventCategory") String eventCategory);

    @Insert("""
            INSERT INTO ops_system_alert_scope_sync_state (
                scope_id, last_occurred_at, last_status, last_message, last_synced_at
            ) VALUES (#{scopeId}, #{lastOccurredAt}, 'SUCCESS', #{message}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                last_occurred_at = VALUES(last_occurred_at), last_status = 'SUCCESS',
                last_message = VALUES(last_message), last_synced_at = CURRENT_TIMESTAMP
            """)
    int saveSuccess(@Param("scopeId") Long scopeId,
                    @Param("lastOccurredAt") LocalDateTime lastOccurredAt,
                    @Param("message") String message);

    @Insert("""
            INSERT INTO ops_system_alert_scope_sync_state (scope_id, last_status, last_message, last_synced_at)
            VALUES (#{scopeId}, 'ERROR', #{message}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE last_status = 'ERROR', last_message = VALUES(last_message),
                                    last_synced_at = CURRENT_TIMESTAMP
            """)
    int saveFailure(@Param("scopeId") Long scopeId, @Param("message") String message);
}
