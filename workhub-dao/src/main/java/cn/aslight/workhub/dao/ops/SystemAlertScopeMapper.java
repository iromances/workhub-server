package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.SystemAlertScopeEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeIndexEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SystemAlertScopeMapper {

    @Select("""
            <script>
            SELECT id, business_line_code AS businessLineCode, environment_code AS environmentCode,
                   watch_mode AS watchMode, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_scope
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
            ORDER BY business_line_code, environment_code, id
            </script>
            """)
    List<SystemAlertScopeEntity> findScopes(@Param("businessLineCode") String businessLineCode,
                                            @Param("environmentCode") String environmentCode,
                                            @Param("enabledOnly") boolean enabledOnly);

    @Select("""
            SELECT id, business_line_code AS businessLineCode, environment_code AS environmentCode,
                   watch_mode AS watchMode, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_scope WHERE id = #{id}
            """)
    SystemAlertScopeEntity findById(Long id);

    @Select("""
            SELECT id, business_line_code AS businessLineCode, environment_code AS environmentCode,
                   watch_mode AS watchMode, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_scope
            WHERE business_line_code = #{businessLineCode} AND environment_code = #{environmentCode}
            """)
    SystemAlertScopeEntity findByIdentity(@Param("businessLineCode") String businessLineCode,
                                          @Param("environmentCode") String environmentCode);

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

    @Insert("""
            INSERT INTO ops_system_alert_scope (
                business_line_code, environment_code, watch_mode, enabled, remark
            ) VALUES (
                #{businessLineCode}, #{environmentCode}, #{watchMode}, #{enabled}, #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertScope(SystemAlertScopeEntity entity);

    @Update("""
            UPDATE ops_system_alert_scope
            SET business_line_code = #{businessLineCode}, environment_code = #{environmentCode},
                watch_mode = #{watchMode}, enabled = #{enabled}, remark = #{remark}
            WHERE id = #{id}
            """)
    int updateScope(SystemAlertScopeEntity entity);

    @Insert("""
            INSERT INTO ops_system_alert_scope_service (
                scope_id, subsystem_name, service_name, enabled, sort_order
            ) VALUES (#{scopeId}, #{subsystemName}, #{serviceName}, #{enabled}, #{sortOrder})
            """)
    int insertService(SystemAlertScopeServiceEntity entity);

    @Insert("""
            INSERT INTO ops_system_alert_scope_index (scope_id, index_pattern, sort_order)
            VALUES (#{scopeId}, #{indexPattern}, #{sortOrder})
            """)
    int insertIndexPattern(SystemAlertScopeIndexEntity entity);

    @Delete("DELETE FROM ops_system_alert_scope_service WHERE scope_id = #{scopeId}")
    int deleteServices(Long scopeId);

    @Delete("DELETE FROM ops_system_alert_scope_index WHERE scope_id = #{scopeId}")
    int deleteIndexPatterns(Long scopeId);

    @Delete("DELETE FROM ops_system_alert_scope_sync_state WHERE scope_id = #{scopeId}")
    int deleteSyncState(Long scopeId);

    @Delete("DELETE FROM ops_system_alert_scope WHERE id = #{id}")
    int deleteScope(Long id);
}
