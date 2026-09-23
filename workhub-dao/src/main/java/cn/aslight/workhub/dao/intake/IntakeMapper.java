package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeListQuery;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 待整理箱数据访问接口。
 */
@Mapper
public interface IntakeMapper {

    String SUMMARY_COLUMNS = """
            id,
            source_type,
            source_channel,
            external_message_id,
            sender_name,
            received_at,
            raw_content,
            development_owner_user_name,
            CASE WHEN JSON_VALID(structured_data_json) THEN JSON_SET(JSON_REMOVE(structured_data_json, '$.businessLine'), '$.businessLineCode', business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1)) ELSE structured_data_json END AS structured_data_json,
            CASE WHEN JSON_VALID(ai_draft_json) THEN JSON_SET(JSON_REMOVE(ai_draft_json, '$.businessLine'), '$.businessLineCode', business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1)) ELSE ai_draft_json END AS ai_draft_json,
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
            (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1) AS business_line,
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
            process_estimated_effort,
            actual_effort,
            actual_testing_effort,
            priority,
            urgency,
            (SELECT JSON_UNQUOTE(JSON_EXTRACT(da.draft_json, '$.totalEstimatedEffort'))
             FROM pm_intake_development_analysis da
             WHERE da.intake_id = pm_intake_record.id
             ORDER BY da.id DESC LIMIT 1) AS total_estimated_effort,
            (SELECT JSON_UNQUOTE(JSON_EXTRACT(da.draft_json, '$.developmentEstimatedEffort'))
             FROM pm_intake_development_analysis da
             WHERE da.intake_id = pm_intake_record.id
             ORDER BY da.id DESC LIMIT 1) AS development_estimated_effort,
            (SELECT JSON_UNQUOTE(JSON_EXTRACT(da.draft_json, '$.testingEstimatedEffort'))
             FROM pm_intake_development_analysis da
             WHERE da.intake_id = pm_intake_record.id
             ORDER BY da.id DESC LIMIT 1) AS testing_estimated_effort,
            (SELECT CASE WHEN JSON_VALID(da.draft_json) THEN JSON_SET(JSON_REMOVE(da.draft_json, '$.businessLine'), '$.businessLineCode', da.business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(da.business_line_code AS BINARY) LIMIT 1)) ELSE da.draft_json END
             FROM pm_intake_development_analysis da
             WHERE da.intake_id = pm_intake_record.id
             ORDER BY da.id DESC LIMIT 1) AS latest_development_draft_json,
            (SELECT COUNT(1)
             FROM pm_intake_todo todo
             WHERE todo.intake_id = pm_intake_record.id
               AND todo.todo_status IN ('待处理', '处理中')) AS active_todo_count,
            deleted,
            deleted_at,
            deleted_by,
            created_at,
            updated_at
            """;

    String LIST_ORDER_BY = """
            ORDER BY CASE TRIM(demand_status)
            WHEN '待澄清' THEN 0 WHEN '待处理' THEN 1 WHEN '处理中' THEN 2 WHEN '待评估' THEN 3
            WHEN '待排期' THEN 4 WHEN '待设计' THEN 5 WHEN '开发中' THEN 6 WHEN '测试中' THEN 7
            WHEN '待验收' THEN 8 WHEN '待上线' THEN 9 WHEN '已收录' THEN 10 WHEN '已暂停' THEN 11
            WHEN '已完成' THEN 12 WHEN '终止关闭' THEN 13 ELSE 14 END,
            CASE TRIM(priority) WHEN '高' THEN 0 WHEN '中' THEN 1 WHEN '低' THEN 2 ELSE 3 END,
            received_at DESC, id DESC
            """;

    // 总数与分页共用条件；所有输入都使用参数绑定，关键词按字面子串匹配。
    String LIST_WHERE = """
            <where>
                deleted = 0
                <if test="query.status != null">
                    AND CAST(intake_status AS BINARY) = CAST(#{query.status} AS BINARY)
                </if>
                <if test="query.requirementName != null">
                    AND (LOCATE(CAST(#{query.requirementName} AS BINARY), CAST(requirement_name AS BINARY)) &gt; 0
                         OR LOCATE(CAST(#{query.requirementName} AS BINARY), CAST(requirement_digest AS BINARY)) &gt; 0)
                </if>
                <if test="query.approvalCode != null">
                    AND LOCATE(CAST(#{query.approvalCode} AS BINARY), CAST(approval_code AS BINARY)) &gt; 0
                </if>
                <if test="query.proposerName != null">
                    AND LOCATE(CAST(#{query.proposerName} AS BINARY), CAST(proposer_name AS BINARY)) &gt; 0
                </if>
                <if test="query.keyword != null">
                    AND (LOCATE(CAST(LOWER(#{query.keyword}) AS BINARY), CAST(LOWER(approval_code) AS BINARY)) &gt; 0
                         OR LOCATE(CAST(LOWER(#{query.keyword}) AS BINARY), CAST(LOWER(requirement_name) AS BINARY)) &gt; 0
                         OR LOCATE(CAST(LOWER(#{query.keyword}) AS BINARY), CAST(LOWER(requirement_digest) AS BINARY)) &gt; 0
                         OR LOCATE(CAST(LOWER(#{query.keyword}) AS BINARY), CAST(LOWER(requirement_summary) AS BINARY)) &gt; 0
                         OR LOCATE(CAST(LOWER(#{query.keyword}) AS BINARY), CAST(LOWER(remark) AS BINARY)) &gt; 0)
                </if>
                <if test="query.businessLine != null">
                    AND (CAST(business_line_code AS BINARY) = CAST(#{query.businessLine} AS BINARY)
                         OR EXISTS (SELECT 1 FROM pm_business_line bl
                                    WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY)
                                      AND CAST(bl.business_line_name AS BINARY) = CAST(#{query.businessLine} AS BINARY)))
                </if>
                <if test="query.requirementType != null">
                    AND CAST(requirement_type AS BINARY) = CAST(#{query.requirementType} AS BINARY)
                </if>
                <if test="query.demandStatus != null">
                    AND CAST(demand_status AS BINARY) = CAST(#{query.demandStatus} AS BINARY)
                </if>
                <if test="query.releasedStartDate != null">
                    AND released_date &gt;= #{query.releasedStartDate}
                </if>
                <if test="query.releasedEndDate != null">
                    AND released_date &lt;= #{query.releasedEndDate}
                </if>
            </where>
            """;

    String PAGE_RECORDS_SQL = "SELECT * FROM pm_intake_record " + LIST_WHERE + LIST_ORDER_BY
            + " LIMIT #{query.pageSize} OFFSET #{query.offset} ";

    @Select({"<script>", "SELECT COUNT(*) FROM pm_intake_record", LIST_WHERE, "</script>"})
    long count(@Param("query") IntakeListQuery query);

    // 先在主表分页，再读取本页关联的待办、评估和展示字段。
    @Select({"<script>", "SELECT", SUMMARY_COLUMNS,
            "FROM (", PAGE_RECORDS_SQL, ") pm_intake_record", LIST_ORDER_BY, "</script>"})
    List<IntakeRecordEntity> findPage(@Param("query") IntakeListQuery query);

    /**
     * 工作台聚合所需的完整记录；分页列表使用 findPage。
     */
    @Select({"<script>", "SELECT", SUMMARY_COLUMNS, "FROM pm_intake_record",
            "<where>deleted = 0 <if test='status != null and status != \"\"'>AND intake_status = #{status}</if></where>",
            LIST_ORDER_BY, "</script>"})
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
                   CASE WHEN JSON_VALID(structured_data_json) THEN JSON_SET(JSON_REMOVE(structured_data_json, '$.businessLine'), '$.businessLineCode', business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1)) ELSE structured_data_json END AS structured_data_json,
                   CASE WHEN JSON_VALID(ai_draft_json) THEN JSON_SET(JSON_REMOVE(ai_draft_json, '$.businessLine'), '$.businessLineCode', business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1)) ELSE ai_draft_json END AS ai_draft_json,
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
                   (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1) AS business_line,
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
                   process_estimated_effort,
                   actual_effort,
                   actual_testing_effort,
                   priority,
                   urgency,
                   (SELECT CASE WHEN JSON_VALID(da.draft_json) THEN JSON_SET(JSON_REMOVE(da.draft_json, '$.businessLine'), '$.businessLineCode', da.business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(da.business_line_code AS BINARY) LIMIT 1)) ELSE da.draft_json END
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

    @Select("SELECT id FROM pm_intake_record WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long lockProcessInfo(Long id);

    @Select("SELECT id FROM pm_intake_development_analysis WHERE intake_id = #{id} ORDER BY id DESC LIMIT 1 FOR UPDATE")
    Long lockProcessAnalysis(Long id);

    // formalChanges 的列名只由服务中的固定字段白名单生成，不能接收客户端列名。
    @Update({
            "<script>",
            "UPDATE pm_intake_record SET",
            "<foreach collection='formalChanges' index='column' item='value'>",
            "${column} = #{value},",
            "</foreach>",
            "structured_data_json = #{structuredJson},",
            "process_estimated_effort = #{estimatedJson},",
            "updated_at = CURRENT_TIMESTAMP",
            "WHERE id = #{id} AND deleted = 0",
            "</script>"
    })
    int updateProcessInfo(@Param("id") Long id,
                          @Param("formalChanges") Map<String, Object> formalChanges,
                          @Param("structuredJson") String structuredJson,
                          @Param("estimatedJson") String estimatedJson);

    @Select("""
            SELECT id,
                   source_type,
                   source_channel,
                   external_message_id,
                   sender_name,
                   received_at,
                   raw_content,
                   development_owner_user_name,
                   CASE WHEN JSON_VALID(structured_data_json) THEN JSON_SET(JSON_REMOVE(structured_data_json, '$.businessLine'), '$.businessLineCode', business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1)) ELSE structured_data_json END AS structured_data_json,
                   CASE WHEN JSON_VALID(ai_draft_json) THEN JSON_SET(JSON_REMOVE(ai_draft_json, '$.businessLine'), '$.businessLineCode', business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1)) ELSE ai_draft_json END AS ai_draft_json,
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
                   (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(pm_intake_record.business_line_code AS BINARY) LIMIT 1) AS business_line,
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
                   process_estimated_effort,
                   actual_effort,
                   actual_testing_effort,
                   priority,
                   urgency,
                   (SELECT CASE WHEN JSON_VALID(da.draft_json) THEN JSON_SET(JSON_REMOVE(da.draft_json, '$.businessLine'), '$.businessLineCode', da.business_line_code, '$.businessLine', (SELECT bl.business_line_name FROM pm_business_line bl WHERE CAST(bl.business_line_code AS BINARY) = CAST(da.business_line_code AS BINARY) LIMIT 1)) ELSE da.draft_json END
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
                process_estimated_effort,
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
                CASE WHEN JSON_VALID(#{structuredDataJson}) THEN JSON_REMOVE(#{structuredDataJson}, '$.businessLine') ELSE #{structuredDataJson} END,
                CASE WHEN JSON_VALID(#{aiDraftJson}) THEN JSON_REMOVE(#{aiDraftJson}, '$.businessLine') ELSE #{aiDraftJson} END,
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
                #{processEstimatedEffort},
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
            SET ai_draft_json = CASE WHEN JSON_VALID(#{aiDraftJson}) THEN JSON_REMOVE(#{aiDraftJson}, '$.businessLine') ELSE #{aiDraftJson} END,
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
            SET structured_data_json = CASE WHEN JSON_VALID(#{structuredDataJson}) THEN JSON_REMOVE(#{structuredDataJson}, '$.businessLine') ELSE #{structuredDataJson} END,
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
            SET structured_data_json = CASE WHEN JSON_VALID(#{structuredDataJson}) THEN JSON_REMOVE(#{structuredDataJson}, '$.businessLine') ELSE #{structuredDataJson} END,
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
            SET structured_data_json = CASE WHEN JSON_VALID(#{structuredDataJson}) THEN JSON_REMOVE(#{structuredDataJson}, '$.businessLine') ELSE #{structuredDataJson} END,
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
            SET priority = #{priority},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updatePriority(@Param("id") Long id,
                       @Param("priority") String priority);

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
