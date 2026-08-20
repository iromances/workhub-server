package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.AccountingResultEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AccountingRunMapper {

    String RUN_COLUMNS = """
            r.id,
            r.run_no AS runNo,
            r.idempotency_key AS idempotencyKey,
            r.monitor_config_id AS configId,
            c.business_line_code AS businessLineCode,
            bl.business_line_name AS businessLineName,
            c.system_name AS systemName,
            c.display_name AS displayName,
            r.trigger_type AS triggerType,
            r.start_time AS startTime,
            r.end_time AS endTime,
            r.rule_codes AS ruleCodes,
            r.status,
            r.rule_total AS ruleTotal,
            r.passed_count AS passedCount,
            r.warning_count AS warningCount,
            r.failed_count AS failedCount,
            r.skipped_count AS skippedCount,
            r.anomaly_count AS anomalyCount,
            r.requested_by AS requestedBy,
            r.started_at AS startedAt,
            r.finished_at AS finishedAt,
            r.error_message AS errorMessage,
            r.created_at AS createdAt
            """;

    @Insert("""
            INSERT INTO ops_account_reconcile_run (
                run_no, idempotency_key, monitor_config_id, trigger_type,
                start_time, end_time, rule_codes, status, requested_by
            ) VALUES (
                #{runNo}, #{idempotencyKey}, #{configId}, #{triggerType},
                #{startTime}, #{endTime}, #{ruleCodes}, #{status}, #{requestedBy}
            )
            """)
    int insertRun(@Param("runNo") String runNo,
                  @Param("idempotencyKey") String idempotencyKey,
                  @Param("configId") Long configId,
                  @Param("triggerType") String triggerType,
                  @Param("startTime") LocalDateTime startTime,
                  @Param("endTime") LocalDateTime endTime,
                  @Param("ruleCodes") String ruleCodes,
                  @Param("status") String status,
                  @Param("requestedBy") String requestedBy);

    @Select("SELECT id FROM ops_account_reconcile_run WHERE run_no = #{runNo}")
    Long findIdByRunNo(String runNo);

    @Select("SELECT " + RUN_COLUMNS + """
            FROM ops_account_reconcile_run r
            JOIN ops_account_monitor_config c ON c.id = r.monitor_config_id
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE r.id = #{id}
            """)
    AccountingRunEntity findById(Long id);

    @Select("SELECT " + RUN_COLUMNS + """
            FROM ops_account_reconcile_run r
            JOIN ops_account_monitor_config c ON c.id = r.monitor_config_id
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE r.idempotency_key = #{idempotencyKey}
            """)
    AccountingRunEntity findByIdempotencyKey(String idempotencyKey);

    @Select("SELECT " + RUN_COLUMNS + """
            FROM ops_account_reconcile_run r
            JOIN ops_account_monitor_config c ON c.id = r.monitor_config_id
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE r.monitor_config_id = #{configId}
            ORDER BY r.id DESC
            LIMIT 1
            """)
    AccountingRunEntity findLatestByConfigId(Long configId);

    @Select("<script>SELECT " + RUN_COLUMNS + """
            FROM ops_account_reconcile_run r
            JOIN ops_account_monitor_config c ON c.id = r.monitor_config_id
            LEFT JOIN pm_business_line bl ON bl.business_line_code = c.business_line_code
            WHERE 1 = 1
            <if test="businessLineCode != null and businessLineCode != ''">
              AND c.business_line_code = #{businessLineCode}
            </if>
            <if test="status != null and status != ''">
              AND r.status = #{status}
            </if>
            ORDER BY r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AccountingRunEntity> findRuns(@Param("businessLineCode") String businessLineCode,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ops_account_reconcile_run r
            JOIN ops_account_monitor_config c ON c.id = r.monitor_config_id
            WHERE 1 = 1
            <if test="businessLineCode != null and businessLineCode != ''">
              AND c.business_line_code = #{businessLineCode}
            </if>
            <if test="status != null and status != ''">
              AND r.status = #{status}
            </if>
            </script>
            """)
    int countRuns(@Param("businessLineCode") String businessLineCode,
                  @Param("status") String status);

    @Update("""
            UPDATE ops_account_reconcile_run
            SET status = 'RUNNING',
                started_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status = 'PENDING'
            """)
    int markRunning(Long id);

    @Update("""
            UPDATE ops_account_reconcile_run
            SET status = #{status},
                rule_total = #{ruleTotal},
                passed_count = #{passedCount},
                warning_count = #{warningCount},
                failed_count = #{failedCount},
                skipped_count = #{skippedCount},
                anomaly_count = #{anomalyCount},
                error_message = #{errorMessage},
                finished_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int finishRun(@Param("id") Long id,
                  @Param("status") String status,
                  @Param("ruleTotal") int ruleTotal,
                  @Param("passedCount") int passedCount,
                  @Param("warningCount") int warningCount,
                  @Param("failedCount") int failedCount,
                  @Param("skippedCount") int skippedCount,
                  @Param("anomalyCount") int anomalyCount,
                  @Param("errorMessage") String errorMessage);

    @Insert("""
            INSERT INTO ops_account_reconcile_result (
                run_id, rule_code, rule_name, category, granularity, severity,
                status, anomaly_count, difference_amount, sample_json, message, duration_ms
            ) VALUES (
                #{runId}, #{ruleCode}, #{ruleName}, #{category}, #{granularity}, #{severity},
                #{status}, #{anomalyCount}, #{differenceAmount}, #{sampleJson}, #{message}, #{durationMs}
            )
            """)
    int insertResult(AccountingResultEntity result);

    @Select("""
            SELECT id,
                   run_id AS runId,
                   rule_code AS ruleCode,
                   rule_name AS ruleName,
                   category,
                   granularity,
                   severity,
                   status,
                   anomaly_count AS anomalyCount,
                   difference_amount AS differenceAmount,
                   sample_json AS sampleJson,
                   message,
                   duration_ms AS durationMs,
                   created_at AS createdAt
            FROM ops_account_reconcile_result
            WHERE run_id = #{runId}
            ORDER BY id ASC
            """)
    List<AccountingResultEntity> findResults(Long runId);
}
