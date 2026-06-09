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
            SELECT id, intake_id, business_line, analysis_status, analysis_message, items_json,
                   created_by, updated_by, created_at, updated_at
            FROM pm_intake_clarification_analysis
            WHERE id = #{id}
            """)
    IntakeClarificationAnalysisEntity findById(Long id);

    @Select("""
            SELECT id, intake_id, business_line, analysis_status, analysis_message, items_json,
                   created_by, updated_by, created_at, updated_at
            FROM pm_intake_clarification_analysis
            WHERE intake_id = #{intakeId}
            ORDER BY id DESC
            LIMIT 1
            """)
    IntakeClarificationAnalysisEntity findLatestByIntakeId(Long intakeId);

    @Insert("""
            INSERT INTO pm_intake_clarification_analysis (
                intake_id, business_line, analysis_status, analysis_message, items_json, created_by, updated_by
            ) VALUES (
                #{intakeId}, #{businessLine}, #{analysisStatus}, #{analysisMessage}, #{itemsJson}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntakeClarificationAnalysisEntity entity);

    @Update("""
            UPDATE pm_intake_clarification_analysis
            SET business_line = #{businessLine},
                analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                items_json = #{itemsJson},
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
