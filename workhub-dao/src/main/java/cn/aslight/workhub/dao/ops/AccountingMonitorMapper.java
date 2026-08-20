package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AccountingMonitorMapper {

    @Select("""
            <script>
            SELECT c.id,
                   c.business_line_code AS businessLineCode,
                   bl.business_line_name AS businessLineName,
                   c.environment_code AS environmentCode,
                   c.system_name AS systemName,
                   c.display_name AS displayName,
                   c.database_target_key AS databaseTargetKey,
                   c.schema_name AS schemaName,
                   c.rule_profile AS ruleProfile,
                   c.enabled,
                   c.daily_enabled AS dailyEnabled,
                   c.remark,
                   c.created_at AS createdAt,
                   c.updated_at AS updatedAt
            FROM ops_account_monitor_config c
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE 1 = 1
            <if test="businessLineCode != null and businessLineCode != ''">
              AND c.business_line_code = #{businessLineCode}
            </if>
            <if test="enabledOnly">
              AND c.enabled = 1
            </if>
            ORDER BY c.enabled DESC, c.business_line_code ASC, c.system_name ASC
            </script>
            """)
    List<AccountingMonitorConfigEntity> findAll(@Param("businessLineCode") String businessLineCode,
                                                @Param("enabledOnly") boolean enabledOnly);

    @Select("""
            SELECT c.id,
                   c.business_line_code AS businessLineCode,
                   bl.business_line_name AS businessLineName,
                   c.environment_code AS environmentCode,
                   c.system_name AS systemName,
                   c.display_name AS displayName,
                   c.database_target_key AS databaseTargetKey,
                   c.schema_name AS schemaName,
                   c.rule_profile AS ruleProfile,
                   c.enabled,
                   c.daily_enabled AS dailyEnabled,
                   c.remark,
                   c.created_at AS createdAt,
                   c.updated_at AS updatedAt
            FROM ops_account_monitor_config c
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE c.id = #{id}
            """)
    AccountingMonitorConfigEntity findById(Long id);

    @Select("""
            SELECT c.id,
                   c.business_line_code AS businessLineCode,
                   bl.business_line_name AS businessLineName,
                   c.environment_code AS environmentCode,
                   c.system_name AS systemName,
                   c.display_name AS displayName,
                   c.database_target_key AS databaseTargetKey,
                   c.schema_name AS schemaName,
                   c.rule_profile AS ruleProfile,
                   c.enabled,
                   c.daily_enabled AS dailyEnabled,
                   c.remark,
                   c.created_at AS createdAt,
                   c.updated_at AS updatedAt
            FROM ops_account_monitor_config c
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE c.business_line_code = #{businessLineCode}
              AND c.environment_code = #{environmentCode}
              AND c.system_name = #{systemName}
            """)
    AccountingMonitorConfigEntity findByIdentity(@Param("businessLineCode") String businessLineCode,
                                                  @Param("environmentCode") String environmentCode,
                                                  @Param("systemName") String systemName);

    @Insert("""
            INSERT INTO ops_account_monitor_config (
                business_line_code, environment_code, system_name, display_name,
                database_target_key, schema_name, rule_profile, enabled, daily_enabled, remark
            ) VALUES (
                #{businessLineCode}, #{environmentCode}, #{systemName}, #{displayName},
                #{databaseTargetKey}, #{schemaName}, #{ruleProfile}, #{enabled}, #{dailyEnabled}, #{remark}
            )
            """)
    int insert(AccountingMonitorConfigEntity entity);

    @Update("""
            UPDATE ops_account_monitor_config
            SET business_line_code = #{businessLineCode},
                environment_code = #{environmentCode},
                system_name = #{systemName},
                display_name = #{displayName},
                database_target_key = #{databaseTargetKey},
                schema_name = #{schemaName},
                rule_profile = #{ruleProfile},
                enabled = #{enabled},
                daily_enabled = #{dailyEnabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(AccountingMonitorConfigEntity entity);

    @Select("""
            SELECT rule_code
            FROM ops_account_rule_config
            WHERE monitor_config_id = #{configId}
              AND enabled = 1
            ORDER BY rule_code
            """)
    List<String> findEnabledRuleCodes(Long configId);

    @Select("""
            SELECT COUNT(*)
            FROM ops_account_rule_config
            WHERE monitor_config_id = #{configId}
            """)
    int countRuleConfigs(Long configId);
}
