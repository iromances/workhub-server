package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.DevelopmentAnalysisEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 研发分析草稿数据访问接口。
 */
@Mapper
public interface DevelopmentAnalysisMapper {

    @Select("""
            SELECT a.id, a.intake_id, a.project_id, a.business_line_code, bl.business_line_name AS business_line,
                   a.repository_url, a.analysis_status, a.analysis_message,
                   CASE WHEN JSON_VALID(a.draft_json) THEN JSON_SET(JSON_REMOVE(a.draft_json, '$.businessLine'), '$.businessLineCode', a.business_line_code, '$.businessLine', bl.business_line_name) ELSE a.draft_json END AS draft_json,
                   a.zentao_sync_status, a.zentao_sync_message, a.created_by, a.updated_by, a.created_at, a.updated_at
            FROM pm_intake_development_analysis a
            LEFT JOIN pm_business_line bl
              ON CAST(bl.business_line_code AS BINARY) = CAST(a.business_line_code AS BINARY)
            WHERE a.id = #{id}
            """)
    DevelopmentAnalysisEntity findById(Long id);

    @Select("""
            SELECT a.id, a.intake_id, a.project_id, a.business_line_code, bl.business_line_name AS business_line,
                   a.repository_url, a.analysis_status, a.analysis_message,
                   CASE WHEN JSON_VALID(a.draft_json) THEN JSON_SET(JSON_REMOVE(a.draft_json, '$.businessLine'), '$.businessLineCode', a.business_line_code, '$.businessLine', bl.business_line_name) ELSE a.draft_json END AS draft_json,
                   a.zentao_sync_status, a.zentao_sync_message, a.created_by, a.updated_by, a.created_at, a.updated_at
            FROM pm_intake_development_analysis a
            LEFT JOIN pm_business_line bl
              ON CAST(bl.business_line_code AS BINARY) = CAST(a.business_line_code AS BINARY)
            WHERE a.intake_id = #{intakeId}
            ORDER BY a.id DESC
            LIMIT 1
            """)
    DevelopmentAnalysisEntity findLatestByIntakeId(Long intakeId);

    @Insert("""
            INSERT INTO pm_intake_development_analysis (
                intake_id, project_id, business_line_code, repository_url, analysis_status, analysis_message, draft_json,
                zentao_sync_status, zentao_sync_message, created_by, updated_by
            ) VALUES (
                #{intakeId}, #{projectId}, #{businessLineCode}, #{repositoryUrl}, #{analysisStatus}, #{analysisMessage},
                CASE WHEN JSON_VALID(#{draftJson}) THEN JSON_REMOVE(#{draftJson}, '$.businessLine') ELSE #{draftJson} END,
                #{zentaoSyncStatus}, #{zentaoSyncMessage}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DevelopmentAnalysisEntity entity);

    @Update("""
            UPDATE pm_intake_development_analysis
            SET project_id = #{projectId},
                business_line_code = #{businessLineCode},
                repository_url = #{repositoryUrl},
                analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                draft_json = CASE WHEN JSON_VALID(#{draftJson}) THEN JSON_REMOVE(#{draftJson}, '$.businessLine') ELSE #{draftJson} END,
                zentao_sync_status = #{zentaoSyncStatus},
                zentao_sync_message = #{zentaoSyncMessage},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(DevelopmentAnalysisEntity entity);

    @Update("""
            UPDATE pm_intake_development_analysis
            SET project_id = #{projectId},
                business_line_code = #{businessLineCode},
                repository_url = #{repositoryUrl},
                analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                draft_json = CASE WHEN JSON_VALID(#{draftJson}) THEN JSON_REMOVE(#{draftJson}, '$.businessLine') ELSE #{draftJson} END,
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateExecutionState(DevelopmentAnalysisEntity entity);

    @Update("""
            UPDATE pm_intake_development_analysis
            SET analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                zentao_sync_status = #{zentaoSyncStatus},
                zentao_sync_message = #{zentaoSyncMessage},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("analysisStatus") String analysisStatus,
                     @Param("analysisMessage") String analysisMessage,
                     @Param("zentaoSyncStatus") String zentaoSyncStatus,
                     @Param("zentaoSyncMessage") String zentaoSyncMessage,
                     @Param("updatedBy") String updatedBy);

}
