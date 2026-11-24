package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.FuncCallAuditEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** REQ-b4d7e6f2：Function Call 执行审计读写。 */
@Mapper
public interface FuncCallAuditMapper {

    String COLUMNS = """
            monitor_run_id AS monitorRunId, rule_family AS ruleFamily, parameters_json AS parametersJson, template_hash AS templateHash,
            id, sql_code AS sqlCode, scenario_code AS scenarioCode, source, operator,
            business_line_code AS businessLineCode, environment_code AS environmentCode,
            rendered_sql AS renderedSql, status, row_count AS rowCount, duration_ms AS durationMs,
            error_message AS errorMessage, started_at AS startedAt, finished_at AS finishedAt,
            created_at AS createdAt
            """;

    @Insert("""
            INSERT INTO func_call_sql_audit_log (
                monitor_run_id, rule_family, parameters_json, template_hash,
                sql_code, scenario_code, source, operator, business_line_code, environment_code,
                rendered_sql, status, row_count, duration_ms, error_message, started_at, finished_at
            ) VALUES (
                #{monitorRunId}, #{ruleFamily}, #{parametersJson}, #{templateHash},
                #{sqlCode}, #{scenarioCode}, #{source}, #{operator}, #{businessLineCode}, #{environmentCode},
                #{renderedSql}, #{status}, #{rowCount}, #{durationMs}, #{errorMessage}, #{startedAt}, #{finishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FuncCallAuditEntity entity);

    @Select("""
            <script>
            SELECT COUNT(1) FROM func_call_sql_audit_log
            WHERE 1 = 1
            <if test="sqlCode != null and sqlCode != ''">AND sql_code LIKE CONCAT('%', #{sqlCode}, '%')</if>
            <if test="source != null and source != ''">AND source = #{source}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="from != null">AND started_at &gt;= #{from}</if>
            <if test="to != null">AND started_at &lt; #{to}</if>
            </script>
            """)
    long count(@Param("sqlCode") String sqlCode, @Param("source") String source,
               @Param("status") String status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM func_call_sql_audit_log
            WHERE 1 = 1
            <if test="sqlCode != null and sqlCode != ''">AND sql_code LIKE CONCAT('%', #{sqlCode}, '%')</if>
            <if test="source != null and source != ''">AND source = #{source}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="from != null">AND started_at &gt;= #{from}</if>
            <if test="to != null">AND started_at &lt; #{to}</if>
            ORDER BY started_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FuncCallAuditEntity> findPage(@Param("sqlCode") String sqlCode, @Param("source") String source,
                                       @Param("status") String status, @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to, @Param("limit") int limit,
                                       @Param("offset") int offset);

    /** REQ-372e4ce1：families 为空时不过滤；非空时按 rule_family 精确匹配（维度查看 SQL）。 */
    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM func_call_sql_audit_log
            WHERE monitor_run_id=#{runId}
            <if test="families != null and families.size() > 0">AND rule_family IN
                <foreach item="item" collection="families" open="(" separator="," close=")">#{item}</foreach>
            </if>
            ORDER BY id LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FuncCallAuditEntity> forMonitor(@Param("runId") Long runId, @Param("families") List<String> families,
                                         @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM func_call_sql_audit_log
            WHERE monitor_run_id=#{runId}
            <if test="families != null and families.size() > 0">AND rule_family IN
                <foreach item="item" collection="families" open="(" separator="," close=")">#{item}</foreach>
            </if>
            </script>
            """)
    long countMonitor(@Param("runId") Long runId, @Param("families") List<String> families);

    @Delete("DELETE FROM func_call_sql_audit_log WHERE started_at < #{cutoff}")
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
