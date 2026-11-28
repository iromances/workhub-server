package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.AccountingMonitorCheckEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorFamilyEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * REQ-7f26a063：监测规则组与检查项存取（ops_account_monitor_family / ops_account_monitor_check）。
 * 与账务监测既有表一致不建外键；插入不回填自增主键，按 (config_id, family_code) 反查。
 */
@Mapper
public interface AccountingRuleMapper {

    String FAMILY_COLUMNS = """
            id, config_id AS configId, family_code AS familyCode, family_name AS familyName, description,
            batch_size AS batchSize, source_type AS sourceType, enabled, deleted, sort_order AS sortOrder,
            created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, updated_at AS updatedAt
            """;

    String CHECK_COLUMNS = """
            c.id, c.family_id AS familyId, c.check_id AS checkId, c.check_name AS checkName, c.severity, c.note,
            c.enabled, c.sort_order AS sortOrder, c.created_by AS createdBy, c.updated_by AS updatedBy,
            c.created_at AS createdAt, c.updated_at AS updatedAt
            """;

    @Select("SELECT " + FAMILY_COLUMNS + """
            FROM ops_account_monitor_family WHERE config_id=#{configId} ORDER BY sort_order, id
            """)
    List<AccountingMonitorFamilyEntity> findFamilies(Long configId);

    @Select("SELECT " + FAMILY_COLUMNS + """
            FROM ops_account_monitor_family
            WHERE config_id=#{configId} AND family_code=#{familyCode}
            """)
    AccountingMonitorFamilyEntity findFamily(@Param("configId") Long configId, @Param("familyCode") String familyCode);

    @Insert("""
            INSERT INTO ops_account_monitor_family (
                config_id, family_code, family_name, description, batch_size, source_type,
                enabled, deleted, sort_order, created_by, updated_by
            ) VALUES (
                #{configId}, #{familyCode}, #{familyName}, #{description}, #{batchSize}, #{sourceType},
                #{enabled}, #{deleted}, #{sortOrder}, #{createdBy}, #{updatedBy}
            )
            """)
    int insertFamily(AccountingMonitorFamilyEntity entity);

    @Update("""
            UPDATE ops_account_monitor_family
            SET enabled=#{enabled}, updated_by=#{updatedBy}, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{id}
            """)
    int updateFamilyEnabled(@Param("id") Long id, @Param("enabled") boolean enabled, @Param("updatedBy") String updatedBy);

    @Update("""
            UPDATE ops_account_monitor_family
            SET deleted=1, enabled=0, updated_by=#{updatedBy}, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{id} AND source_type='CUSTOM'
            """)
    int markFamilyDeleted(@Param("id") Long id, @Param("updatedBy") String updatedBy);

    @Update("""
            UPDATE ops_account_monitor_family
            SET family_name=#{familyName}, description=#{description}, batch_size=#{batchSize},
                updated_by=#{updatedBy}, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{id}
            """)
    int updateFamilyMeta(@Param("id") Long id,
                         @Param("familyName") String familyName,
                         @Param("description") String description,
                         @Param("batchSize") int batchSize,
                         @Param("updatedBy") String updatedBy);

    @Select("SELECT " + CHECK_COLUMNS + """
            FROM ops_account_monitor_check c
            JOIN ops_account_monitor_family f ON f.id=c.family_id
            WHERE f.config_id=#{configId}
            ORDER BY f.sort_order, f.id, c.sort_order, c.id
            """)
    List<AccountingMonitorCheckEntity> findChecks(Long configId);

    @Select("SELECT " + CHECK_COLUMNS + """
            FROM ops_account_monitor_check c
            WHERE c.family_id=#{familyId}
            ORDER BY c.sort_order, c.id
            """)
    List<AccountingMonitorCheckEntity> findChecksByFamily(Long familyId);

    @Insert("""
            INSERT INTO ops_account_monitor_check (
                family_id, check_id, check_name, severity, note, enabled, sort_order, created_by, updated_by
            ) VALUES (
                #{familyId}, #{checkId}, #{checkName}, #{severity}, #{note}, #{enabled}, #{sortOrder}, #{createdBy}, #{updatedBy}
            )
            """)
    int insertCheck(AccountingMonitorCheckEntity entity);

    @Update("""
            UPDATE ops_account_monitor_check
            SET enabled=#{enabled}, severity=#{severity}, updated_by=#{updatedBy}, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{id}
            """)
    int updateCheckState(@Param("id") Long id,
                         @Param("enabled") boolean enabled,
                         @Param("severity") String severity,
                         @Param("updatedBy") String updatedBy);

    @Update("""
            UPDATE ops_account_monitor_check
            SET check_name=#{checkName}, severity=#{severity}, note=#{note},
                updated_by=#{updatedBy}, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{id}
            """)
    int updateCheckMeta(@Param("id") Long id,
                        @Param("checkName") String checkName,
                        @Param("severity") String severity,
                        @Param("note") String note,
                        @Param("updatedBy") String updatedBy);

    @Delete("DELETE FROM ops_account_monitor_check WHERE id=#{id}")
    int deleteCheck(Long id);
}
