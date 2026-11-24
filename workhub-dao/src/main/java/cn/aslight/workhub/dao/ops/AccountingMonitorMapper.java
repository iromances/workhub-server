package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * REQ-d227e09d：账务场景监测配置存取（ops_account_monitor_config 已迁移为场景结构）。
 */
@Mapper
public interface AccountingMonitorMapper {

    String CONFIG_COLUMNS = """
            c.id,
            c.business_line_code AS businessLineCode,
            bl.business_line_name AS businessLineName,
            c.environment_code AS environmentCode,
            c.system_name AS systemName,
            c.display_name AS displayName,
            c.scenario_type AS scenarioType,
            c.expected_time AS expectedTime,
            c.check_time AS checkTime,
            c.expected_count_sql_code AS expectedCountSqlCode,
            c.expected_fixed_count AS expectedFixedCount,
            c.actual_count_sql_code AS actualCountSqlCode,
            c.success_count_sql_code AS successCountSqlCode,
            c.tolerance_rate AS toleranceRate,
            c.alert_enabled AS alertEnabled,
            c.enabled,
            c.remark,
            c.created_at AS createdAt,
            c.updated_at AS updatedAt
            """;

    @Select("SELECT id FROM ops_account_monitor_config WHERE id=#{id} FOR UPDATE")
    Long lockConfig(Long id);

    @Select("<script>SELECT " + CONFIG_COLUMNS + """
            FROM ops_account_monitor_config c
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE 1 = 1
            <if test="businessLineCode != null and businessLineCode != ''">
              AND c.business_line_code = #{businessLineCode}
            </if>
            <if test="enabledOnly">
              AND c.enabled = 1
            </if>
            ORDER BY c.enabled DESC, c.business_line_code ASC, c.system_name ASC, c.scenario_type ASC
            </script>
            """)
    List<AccountingMonitorConfigEntity> findAll(@Param("businessLineCode") String businessLineCode,
                                                @Param("enabledOnly") boolean enabledOnly);

    @Select("SELECT " + CONFIG_COLUMNS + """
            FROM ops_account_monitor_config c
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE c.id = #{id}
            """)
    AccountingMonitorConfigEntity findById(Long id);

    @Select("SELECT " + CONFIG_COLUMNS + """
            FROM ops_account_monitor_config c
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE c.business_line_code = #{businessLineCode}
              AND c.environment_code = #{environmentCode}
              AND c.system_name = #{systemName}
              AND c.scenario_type = #{scenarioType}
            """)
    AccountingMonitorConfigEntity findByIdentity(@Param("businessLineCode") String businessLineCode,
                                                  @Param("environmentCode") String environmentCode,
                                                  @Param("systemName") String systemName,
                                                  @Param("scenarioType") String scenarioType);

    @Insert("""
            INSERT INTO ops_account_monitor_config (
                business_line_code, environment_code, system_name, display_name,
                scenario_type, expected_time, check_time,
                expected_count_sql_code, expected_fixed_count, actual_count_sql_code, success_count_sql_code,
                tolerance_rate, alert_enabled, enabled, remark
            ) VALUES (
                #{businessLineCode}, #{environmentCode}, #{systemName}, #{displayName},
                #{scenarioType}, #{expectedTime}, #{checkTime},
                #{expectedCountSqlCode}, #{expectedFixedCount}, #{actualCountSqlCode}, #{successCountSqlCode},
                #{toleranceRate}, #{alertEnabled}, #{enabled}, #{remark}
            )
            """)
    int insert(AccountingMonitorConfigEntity entity);

    @Update("""
            UPDATE ops_account_monitor_config
            SET business_line_code = #{businessLineCode},
                environment_code = #{environmentCode},
                system_name = #{systemName},
                display_name = #{displayName},
                scenario_type = #{scenarioType},
                expected_time = #{expectedTime},
                check_time = #{checkTime},
                expected_count_sql_code = #{expectedCountSqlCode},
                expected_fixed_count = #{expectedFixedCount},
                actual_count_sql_code = #{actualCountSqlCode},
                success_count_sql_code = #{successCountSqlCode},
                tolerance_rate = #{toleranceRate},
                alert_enabled = #{alertEnabled},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(AccountingMonitorConfigEntity entity);
}
