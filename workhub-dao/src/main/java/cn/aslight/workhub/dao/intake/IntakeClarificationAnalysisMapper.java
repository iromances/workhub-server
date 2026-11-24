package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeClarificationAnalysisEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 需求澄清分析数据访问接口。
 */
@Mapper
public interface IntakeClarificationAnalysisMapper {
    @Select("SELECT raw_output FROM pm_intake_clarification_analysis WHERE intake_id=#{intakeId} AND input_fingerprint=#{fingerprint} AND analysis_status='FAILED' AND recovery_pending=1 AND raw_output IS NOT NULL AND id=(SELECT MAX(id) FROM pm_intake_clarification_analysis WHERE intake_id=#{intakeId} AND input_fingerprint=#{fingerprint}) LIMIT 1")
    String findRecoverableOutput(Long intakeId, String fingerprint);

    @Update("UPDATE pm_intake_clarification_analysis SET raw_output=#{output}, input_fingerprint=#{fingerprint}, recovery_pending=1 WHERE id=#{id} AND analysis_status='RUNNING'")
    int saveRawOutput(Long id, String fingerprint, String output);

    @Update("UPDATE pm_intake_clarification_analysis SET recovery_pending=0 WHERE id=#{id} AND analysis_status='RUNNING'")
    int completeRawFormatting(Long id);



    @Select("""
            SELECT a.id, a.intake_id, a.business_line_code, bl.business_line_name AS business_line,
                   a.analysis_status, a.analysis_message,
                   a.started_at, a.finished_at,
                   CASE WHEN JSON_VALID(a.items_json) THEN JSON_SET(JSON_REMOVE(a.items_json, '$.businessLine'), '$.businessLineCode', a.business_line_code, '$.businessLine', bl.business_line_name) ELSE a.items_json END AS items_json,
                   a.created_by, a.updated_by, a.created_at, a.updated_at
            FROM pm_intake_clarification_analysis a
            LEFT JOIN pm_business_line bl
              ON CAST(bl.business_line_code AS BINARY) = CAST(a.business_line_code AS BINARY)
            WHERE a.id = #{id}
            """)
    IntakeClarificationAnalysisEntity findById(Long id);

    @Select("""
            SELECT a.id, a.intake_id, a.business_line_code, bl.business_line_name AS business_line,
                   a.analysis_status, a.analysis_message,
                   a.started_at, a.finished_at,
                   CASE WHEN JSON_VALID(a.items_json) THEN JSON_SET(JSON_REMOVE(a.items_json, '$.businessLine'), '$.businessLineCode', a.business_line_code, '$.businessLine', bl.business_line_name) ELSE a.items_json END AS items_json,
                   a.created_by, a.updated_by, a.created_at, a.updated_at
            FROM pm_intake_clarification_analysis a
            LEFT JOIN pm_business_line bl
              ON CAST(bl.business_line_code AS BINARY) = CAST(a.business_line_code AS BINARY)
            WHERE a.intake_id = #{intakeId}
            ORDER BY a.id DESC
            LIMIT 1
            """)
    IntakeClarificationAnalysisEntity findLatestByIntakeId(Long intakeId);

    @Select("""
            SELECT a.id, a.intake_id, a.business_line_code, bl.business_line_name AS business_line,
                   a.analysis_status, a.analysis_message,
                   a.started_at, a.finished_at,
                   CASE WHEN JSON_VALID(a.items_json) THEN JSON_SET(JSON_REMOVE(a.items_json, '$.businessLine'), '$.businessLineCode', a.business_line_code, '$.businessLine', bl.business_line_name) ELSE a.items_json END AS items_json,
                   a.created_by, a.updated_by, a.created_at, a.updated_at
            FROM pm_intake_clarification_analysis a
            LEFT JOIN pm_business_line bl
              ON CAST(bl.business_line_code AS BINARY) = CAST(a.business_line_code AS BINARY)
            WHERE a.intake_id = #{intakeId}
            ORDER BY a.id DESC
            """)
    List<IntakeClarificationAnalysisEntity> listByIntakeId(Long intakeId);

    @Update("""
            UPDATE pm_intake_clarification_analysis
            SET analysis_status = 'FAILED',
                analysis_message = #{message},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE intake_id = #{intakeId}
              AND analysis_status IN ('PENDING', 'RUNNING')
            """)
    int failInFlightByIntakeId(Long intakeId, String message, String updatedBy);

    @Insert("""
            INSERT INTO pm_intake_clarification_analysis (
                intake_id, business_line_code, analysis_status, analysis_message, items_json, created_by, updated_by
            ) VALUES (
                #{intakeId}, #{businessLineCode}, #{analysisStatus}, #{analysisMessage},
                CASE WHEN JSON_VALID(#{itemsJson}) THEN JSON_REMOVE(#{itemsJson}, '$.businessLine') ELSE #{itemsJson} END,
                #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntakeClarificationAnalysisEntity entity);

    @Update("""
            UPDATE pm_intake_clarification_analysis
            SET business_line_code = #{businessLineCode},
                analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                started_at = CASE WHEN #{analysisStatus} = 'RUNNING' AND started_at IS NULL THEN CURRENT_TIMESTAMP ELSE started_at END,
                finished_at = CASE WHEN #{analysisStatus} IN ('DRAFT', 'CONFIRMED', 'FAILED') AND finished_at IS NULL THEN CURRENT_TIMESTAMP ELSE finished_at END,
                items_json = CASE WHEN JSON_VALID(#{itemsJson}) THEN JSON_REMOVE(#{itemsJson}, '$.businessLine') ELSE #{itemsJson} END,
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(IntakeClarificationAnalysisEntity entity);

    @Update("""
            UPDATE pm_intake_clarification_analysis
            SET analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                started_at = CASE WHEN #{analysisStatus} = 'RUNNING' AND started_at IS NULL THEN CURRENT_TIMESTAMP ELSE started_at END,
                finished_at = CASE WHEN #{analysisStatus} IN ('DRAFT', 'CONFIRMED', 'FAILED') AND finished_at IS NULL THEN CURRENT_TIMESTAMP ELSE finished_at END,
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateExecutionState(IntakeClarificationAnalysisEntity entity);
}
