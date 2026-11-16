package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeSubmitTestReviewEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 提测 AI 审核记录 Mapper。
 */
@Mapper
public interface IntakeSubmitTestReviewMapper {

    @Select("""
            SELECT id, intake_id, review_status, review_message, findings_json, conclusion, summary,
                   human_confirmed_by, human_confirmed_at,
                   zentao_test_task_no, zentao_test_task_url,
                   jenkins_environment_code, jenkins_build_number, jenkins_build_url, jenkins_build_status,
                   created_by, updated_by, created_at, updated_at
            FROM pm_intake_submit_test_review
            WHERE intake_id = #{intakeId}
            """)
    IntakeSubmitTestReviewEntity findByIntakeId(@Param("intakeId") Long intakeId);

    @Insert("""
            INSERT INTO pm_intake_submit_test_review (
                intake_id, review_status, review_message, findings_json, conclusion, summary,
                human_confirmed_by, human_confirmed_at,
                zentao_test_task_no, zentao_test_task_url,
                jenkins_environment_code, jenkins_build_number, jenkins_build_url, jenkins_build_status,
                created_by, updated_by
            ) VALUES (
                #{intakeId}, #{reviewStatus}, #{reviewMessage}, #{findingsJson}, #{conclusion}, #{summary},
                #{humanConfirmedBy}, #{humanConfirmedAt},
                #{zentaoTestTaskNo}, #{zentaoTestTaskUrl},
                #{jenkinsEnvironmentCode}, #{jenkinsBuildNumber}, #{jenkinsBuildUrl}, #{jenkinsBuildStatus},
                #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntakeSubmitTestReviewEntity entity);

    @Update("""
            UPDATE pm_intake_submit_test_review
            SET review_status = #{reviewStatus},
                review_message = #{reviewMessage},
                findings_json = #{findingsJson},
                conclusion = #{conclusion},
                summary = #{summary},
                human_confirmed_by = #{humanConfirmedBy},
                human_confirmed_at = #{humanConfirmedAt},
                zentao_test_task_no = #{zentaoTestTaskNo},
                zentao_test_task_url = #{zentaoTestTaskUrl},
                jenkins_environment_code = #{jenkinsEnvironmentCode},
                jenkins_build_number = #{jenkinsBuildNumber},
                jenkins_build_url = #{jenkinsBuildUrl},
                jenkins_build_status = #{jenkinsBuildStatus},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE intake_id = #{intakeId}
            """)
    int updateByIntakeId(IntakeSubmitTestReviewEntity entity);

    /**
     * 只回写审核结果列，不触碰人工确认与禅道/Jenkins 回执；
     * 供异步审核执行使用，避免与确认提测事务的全量回写互相覆盖。
     */
    @Update("""
            UPDATE pm_intake_submit_test_review
            SET review_status = #{reviewStatus},
                review_message = #{reviewMessage},
                findings_json = #{findingsJson},
                conclusion = #{conclusion},
                summary = #{summary},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE intake_id = #{intakeId}
            """)
    int updateReviewOutcomeByIntakeId(IntakeSubmitTestReviewEntity entity);
}
