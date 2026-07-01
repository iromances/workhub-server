package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 待整理箱数据访问接口。
 */
@Mapper
public interface IntakeMapper {

    @Select({
            "<script>",
            "SELECT id,",
            "source_type,",
            "source_channel,",
            "external_message_id,",
            "sender_name,",
            "received_at,",
            "raw_content,",
            "development_owner_user_name,",
            "structured_data_json,",
            "ai_draft_json,",
            "intake_status,",
            "demand_status,",
            "pause_previous_demand_status,",
            "pause_reason,",
            "pause_date,",
            "enrichment_status,",
            "enrichment_error_summary,",
            "enrichment_updated_at,",
            "converted_work_item_id,",
            "approval_code,",
            "approval_title,",
            "approval_status,",
            "proposer_name,",
            "submitted_at,",
            "requirement_type,",
            "requirement_name,",
            "requirement_summary,",
            "requirement_digest,",
            "department,",
            "business_line,",
            "business_line_code,",
            "project_hint,",
            "development_branch_name,",
            "zentao_url,",
            "remark,",
            "planned_due_date,",
            "planned_development_start_date,",
            "planned_testing_start_date,",
            "planned_release_date,",
            "development_started_date,",
            "testing_started_date,",
            "actual_completed_date,",
            "scheduled_acceptance_date,",
            "actual_testing_completed_date,",
            "acceptance_date,",
            "released_date,",
            "closed_date,",
            "close_reason,",
            "estimated_effort,",
            "actual_effort,",
            "actual_testing_effort,",
            "priority,",
            "urgency,",
            "(SELECT JSON_UNQUOTE(JSON_EXTRACT(da.draft_json, '$.totalEstimatedEffort'))",
            " FROM pm_intake_development_analysis da",
            " WHERE da.intake_id = pm_intake_record.id",
            " ORDER BY da.id DESC LIMIT 1) AS total_estimated_effort,",
            "(SELECT JSON_UNQUOTE(JSON_EXTRACT(da.draft_json, '$.developmentEstimatedEffort'))",
            " FROM pm_intake_development_analysis da",
            " WHERE da.intake_id = pm_intake_record.id",
            " ORDER BY da.id DESC LIMIT 1) AS development_estimated_effort,",
            "(SELECT JSON_UNQUOTE(JSON_EXTRACT(da.draft_json, '$.testingEstimatedEffort'))",
            " FROM pm_intake_development_analysis da",
            " WHERE da.intake_id = pm_intake_record.id",
            " ORDER BY da.id DESC LIMIT 1) AS testing_estimated_effort,",
            "(SELECT da.draft_json",
            " FROM pm_intake_development_analysis da",
            " WHERE da.intake_id = pm_intake_record.id",
            " ORDER BY da.id DESC LIMIT 1) AS latest_development_draft_json,",
            "(SELECT COUNT(1)",
            " FROM pm_intake_todo todo",
            " WHERE todo.intake_id = pm_intake_record.id",
            "   AND todo.todo_status IN ('待处理', '处理中')) AS active_todo_count,",
            "deleted,",
            "deleted_at,",
            "deleted_by,",
            "created_at,",
            "updated_at",
            "FROM pm_intake_record",
            "<where>",
            "deleted = 0",
            "<if test='status != null and status != \"\"'>",
            "AND intake_status = #{status}",
            "</if>",
            "</where>",
            "ORDER BY CASE WHEN demand_status IN ('已完成', '终止关闭') THEN 1 ELSE 0 END, received_at DESC, id DESC",
            "</script>"
    })
    List<IntakeRecordEntity> findAll(@Param("status") String status);

    @Select("""
            SELECT id,
                   source_type,
                   source_channel,
                   external_message_id,
                   sender_name,
                   received_at,
                   raw_content,
                   development_owner_user_name,
                   structured_data_json,
                   ai_draft_json,
                   intake_status,
                   demand_status,
                   pause_previous_demand_status,
                   pause_reason,
                   pause_date,
                   enrichment_status,
                   enrichment_error_summary,
                   enrichment_updated_at,
                   converted_work_item_id,
                   approval_code,
                   approval_title,
                   approval_status,
                   proposer_name,
                   submitted_at,
                   requirement_type,
                   requirement_name,
                   requirement_summary,
                   requirement_digest,
                   department,
                   business_line,
                   business_line_code,
                   project_hint,
                   development_branch_name,
                   zentao_url,
                   remark,
                   planned_due_date,
                   planned_development_start_date,
                   planned_testing_start_date,
                   planned_release_date,
                   development_started_date,
                   testing_started_date,
                   actual_completed_date,
                   scheduled_acceptance_date,
                   actual_testing_completed_date,
                   acceptance_date,
                   released_date,
                   closed_date,
                   close_reason,
                   estimated_effort,
                   actual_effort,
                   actual_testing_effort,
                   priority,
                   urgency,
                   (SELECT da.draft_json
                    FROM pm_intake_development_analysis da
                    WHERE da.intake_id = pm_intake_record.id
                    ORDER BY da.id DESC LIMIT 1) AS latest_development_draft_json,
                   deleted,
                   deleted_at,
                   deleted_by,
                   created_at,
                   updated_at
            FROM pm_intake_record
            WHERE id = #{id}
              AND deleted = 0
            """)
    IntakeRecordEntity findById(Long id);

    @Select("""
            SELECT id,
                   source_type,
                   source_channel,
                   external_message_id,
                   sender_name,
                   received_at,
                   raw_content,
                   development_owner_user_name,
                   structured_data_json,
                   ai_draft_json,
                   intake_status,
                   demand_status,
                   pause_previous_demand_status,
                   pause_reason,
                   pause_date,
                   enrichment_status,
                   enrichment_error_summary,
                   enrichment_updated_at,
                   converted_work_item_id,
                   approval_code,
                   approval_title,
                   approval_status,
                   proposer_name,
                   submitted_at,
                   requirement_type,
                   requirement_name,
                   requirement_summary,
                   requirement_digest,
                   department,
                   business_line,
                   business_line_code,
                   project_hint,
                   development_branch_name,
                   zentao_url,
                   remark,
                   planned_due_date,
                   planned_development_start_date,
                   planned_testing_start_date,
                   planned_release_date,
                   development_started_date,
                   testing_started_date,
                   actual_completed_date,
                   scheduled_acceptance_date,
                   actual_testing_completed_date,
                   acceptance_date,
                   released_date,
                   closed_date,
                   close_reason,
                   estimated_effort,
                   actual_effort,
                   actual_testing_effort,
                   priority,
                   urgency,
                   (SELECT da.draft_json
                    FROM pm_intake_development_analysis da
                    WHERE da.intake_id = pm_intake_record.id
                    ORDER BY da.id DESC LIMIT 1) AS latest_development_draft_json,
                   deleted,
                   deleted_at,
                   deleted_by,
                   created_at,
                   updated_at
            FROM pm_intake_record
            WHERE external_message_id = #{externalMessageId}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    IntakeRecordEntity findByExternalMessageId(String externalMessageId);

    @Insert("""
            INSERT INTO pm_intake_record (
                source_type,
                source_channel,
                external_message_id,
                sender_name,
                received_at,
                raw_content,
                development_owner_user_name,
                structured_data_json,
                ai_draft_json,
                intake_status,
                demand_status,
                enrichment_status,
                enrichment_error_summary,
                enrichment_updated_at,
                converted_work_item_id,
                approval_code,
                approval_title,
                approval_status,
                proposer_name,
                submitted_at,
                requirement_type,
                requirement_name,
                requirement_summary,
                requirement_digest,
                department,
                business_line,
                business_line_code,
                project_hint,
                development_branch_name,
                zentao_url,
                remark,
                planned_due_date,
                planned_development_start_date,
                planned_testing_start_date,
                planned_release_date,
                development_started_date,
                testing_started_date,
                actual_completed_date,
                scheduled_acceptance_date,
                actual_testing_completed_date,
                acceptance_date,
                released_date,
                closed_date,
                close_reason,
                estimated_effort,
                actual_effort,
                actual_testing_effort,
                priority,
                urgency,
                deleted,
                deleted_at,
                deleted_by
            ) VALUES (
                #{sourceType},
                #{sourceChannel},
                #{externalMessageId},
                #{senderName},
                #{receivedAt},
                #{rawContent},
                #{developmentOwnerUserName},
                #{structuredDataJson},
                #{aiDraftJson},
                #{intakeStatus},
                #{demandStatus},
                #{enrichmentStatus},
                #{enrichmentErrorSummary},
                #{enrichmentUpdatedAt},
                #{convertedWorkItemId},
                #{approvalCode},
                #{approvalTitle},
                #{approvalStatus},
                #{proposerName},
                #{submittedAt},
                #{requirementType},
                #{requirementName},
                #{requirementSummary},
                #{requirementDigest},
                #{department},
                #{businessLine},
                #{businessLineCode},
                #{projectHint},
                #{developmentBranchName},
                #{zentaoUrl},
                #{remark},
                #{plannedDueDate},
                #{plannedDevelopmentStartDate},
                #{plannedTestingStartDate},
                #{plannedReleaseDate},
                #{developmentStartedDate},
                #{testingStartedDate},
                #{actualCompletedDate},
                #{scheduledAcceptanceDate},
                #{actualTestingCompletedDate},
                #{acceptanceDate},
                #{releasedDate},
                #{closedDate},
                #{closeReason},
                #{estimatedEffort},
                #{actualEffort},
                #{actualTestingEffort},
                #{priority},
                #{urgency},
                #{deleted},
                #{deletedAt},
                #{deletedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntakeRecordEntity entity);

    @Update("""
            UPDATE pm_intake_record
            SET ai_draft_json = #{aiDraftJson},
                intake_status = #{intakeStatus}
            WHERE id = #{id}
            """)
    int updateAiDraft(@Param("id") Long id,
                      @Param("aiDraftJson") String aiDraftJson,
                      @Param("intakeStatus") String intakeStatus);

    @Update("""
            UPDATE pm_intake_record
            SET intake_status = #{intakeStatus},
                demand_status = #{demandStatus},
                converted_work_item_id = #{convertedWorkItemId}
            WHERE id = #{id}
            """)
    int markConverted(@Param("id") Long id,
                      @Param("intakeStatus") String intakeStatus,
                      @Param("demandStatus") String demandStatus,
                      @Param("convertedWorkItemId") Long convertedWorkItemId);

    @Update("""
            UPDATE pm_intake_record
            SET structured_data_json = #{structuredDataJson},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStructuredData(@Param("id") Long id,
                             @Param("structuredDataJson") String structuredDataJson);

    @Update("""
            UPDATE pm_intake_record
            SET enrichment_status = #{enrichmentStatus},
                demand_status = #{demandStatus},
                enrichment_error_summary = #{enrichmentErrorSummary},
                enrichment_updated_at = #{enrichmentUpdatedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateEnrichmentState(@Param("id") Long id,
                              @Param("enrichmentStatus") String enrichmentStatus,
                              @Param("demandStatus") String demandStatus,
                              @Param("enrichmentErrorSummary") String enrichmentErrorSummary,
                              @Param("enrichmentUpdatedAt") java.time.LocalDateTime enrichmentUpdatedAt);

    @Update("""
            UPDATE pm_intake_record
            SET structured_data_json = #{structuredDataJson},
                demand_status = #{demandStatus},
                enrichment_status = #{enrichmentStatus},
                enrichment_error_summary = #{enrichmentErrorSummary},
                enrichment_updated_at = #{enrichmentUpdatedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStructuredDataAndEnrichment(@Param("id") Long id,
                                          @Param("structuredDataJson") String structuredDataJson,
                                          @Param("demandStatus") String demandStatus,
                                          @Param("enrichmentStatus") String enrichmentStatus,
                                          @Param("enrichmentErrorSummary") String enrichmentErrorSummary,
                                          @Param("enrichmentUpdatedAt") java.time.LocalDateTime enrichmentUpdatedAt);

    @Update("""
            UPDATE pm_intake_record
            SET approval_code = #{approvalCode},
                approval_title = #{approvalTitle},
                approval_status = #{approvalStatus},
                proposer_name = #{proposerName},
                submitted_at = #{submittedAt},
                requirement_type = #{requirementType},
                requirement_name = #{requirementName},
                requirement_summary = #{requirementSummary},
                requirement_digest = #{requirementDigest},
                department = #{department},
                business_line = #{businessLine},
                business_line_code = #{businessLineCode},
                project_hint = #{projectHint},
                development_branch_name = #{developmentBranchName},
                zentao_url = #{zentaoUrl},
                remark = #{remark},
                planned_due_date = #{plannedDueDate},
                planned_development_start_date = #{plannedDevelopmentStartDate},
                planned_testing_start_date = #{plannedTestingStartDate},
                planned_release_date = #{plannedReleaseDate},
                development_started_date = #{developmentStartedDate},
                testing_started_date = #{testingStartedDate},
                actual_completed_date = #{actualCompletedDate},
                scheduled_acceptance_date = #{scheduledAcceptanceDate},
                actual_testing_completed_date = #{actualTestingCompletedDate},
                acceptance_date = #{acceptanceDate},
                released_date = #{releasedDate},
                closed_date = #{closedDate},
                close_reason = #{closeReason},
                estimated_effort = #{estimatedEffort},
                actual_effort = #{actualEffort},
                actual_testing_effort = #{actualTestingEffort},
                priority = #{priority},
                urgency = #{urgency},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateFormalFields(IntakeRecordEntity entity);

    @Update("""
            UPDATE pm_intake_record
            SET development_owner_user_name = #{developmentOwnerUserName},
                demand_status = #{demandStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateManagementState(@Param("id") Long id,
                              @Param("developmentOwnerUserName") String developmentOwnerUserName,
                              @Param("demandStatus") String demandStatus);

    @Update("""
            UPDATE pm_intake_record
            SET structured_data_json = #{structuredDataJson},
                development_owner_user_name = #{developmentOwnerUserName},
                demand_status = #{demandStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateManagementFields(@Param("id") Long id,
                               @Param("structuredDataJson") String structuredDataJson,
                               @Param("developmentOwnerUserName") String developmentOwnerUserName,
                               @Param("demandStatus") String demandStatus);

    @Update("""
            UPDATE pm_intake_record
            SET demand_status = '已暂停',
                pause_previous_demand_status = #{previousDemandStatus},
                pause_reason = #{pauseReason},
                pause_date = #{pauseDate},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int pauseDemand(@Param("id") Long id,
                    @Param("previousDemandStatus") String previousDemandStatus,
                    @Param("pauseReason") String pauseReason,
                    @Param("pauseDate") LocalDate pauseDate);

    @Update("""
            UPDATE pm_intake_record
            SET demand_status = #{restoredDemandStatus},
                pause_previous_demand_status = NULL,
                pause_reason = NULL,
                pause_date = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int restorePausedDemand(@Param("id") Long id,
                            @Param("restoredDemandStatus") String restoredDemandStatus);

    @Update("""
            UPDATE pm_intake_record
            SET deleted = 1,
                deleted_at = #{deletedAt},
                deleted_by = #{deletedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted = 0
            """)
    int markDeleted(@Param("id") Long id,
                    @Param("deletedAt") java.time.LocalDateTime deletedAt,
                    @Param("deletedBy") String deletedBy);
}
