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
            SELECT id, intake_id, project_id, business_line, repository_url, analysis_status, analysis_message, draft_json,
                   zentao_sync_status, zentao_sync_message, created_by, updated_by, created_at, updated_at
            FROM pm_intake_development_analysis
            WHERE id = #{id}
            """)
    DevelopmentAnalysisEntity findById(Long id);

    @Select("""
            SELECT id, intake_id, project_id, business_line, repository_url, analysis_status, analysis_message, draft_json,
                   zentao_sync_status, zentao_sync_message, created_by, updated_by, created_at, updated_at
            FROM pm_intake_development_analysis
            WHERE intake_id = #{intakeId}
            ORDER BY id DESC
            LIMIT 1
            """)
    DevelopmentAnalysisEntity findLatestByIntakeId(Long intakeId);

    @Insert("""
            INSERT INTO pm_intake_development_analysis (
                intake_id, project_id, business_line, repository_url, analysis_status, analysis_message, draft_json,
                zentao_sync_status, zentao_sync_message, created_by, updated_by
            ) VALUES (
                #{intakeId}, #{projectId}, #{businessLine}, #{repositoryUrl}, #{analysisStatus}, #{analysisMessage}, #{draftJson},
                #{zentaoSyncStatus}, #{zentaoSyncMessage}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DevelopmentAnalysisEntity entity);

    @Update("""
            UPDATE pm_intake_development_analysis
            SET project_id = #{projectId},
                business_line = #{businessLine},
                repository_url = #{repositoryUrl},
                analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                draft_json = #{draftJson},
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
                business_line = #{businessLine},
                repository_url = #{repositoryUrl},
                analysis_status = #{analysisStatus},
                analysis_message = #{analysisMessage},
                draft_json = #{draftJson},
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
