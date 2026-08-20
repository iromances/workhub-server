package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeClarificationAnalysisEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 需求澄清分析数据访问接口。
 */
@Mapper
public interface IntakeClarificationAnalysisMapper {

    @Select("""
            SELECT a.id, a.intake_id, a.business_line_code, bl.business_line_name AS business_line,
                   a.analysis_status, a.analysis_message,
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
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateExecutionState(IntakeClarificationAnalysisEntity entity);
}
