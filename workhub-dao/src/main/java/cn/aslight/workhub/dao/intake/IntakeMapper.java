package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
            "enrichment_status,",
            "enrichment_error_summary,",
            "enrichment_updated_at,",
            "converted_work_item_id,",
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
                   development_owner_user_name,
                   demand_status,
                   structured_data_json,
                   ai_draft_json
            FROM pm_intake_record
            WHERE structured_data_json IS NOT NULL
               OR ai_draft_json IS NOT NULL
            ORDER BY id ASC
            """)
    List<IntakeRecordEntity> findAllForEffortNormalization();

    @Select("""
            SELECT id,
                   development_owner_user_name,
                   demand_status,
                   structured_data_json
            FROM pm_intake_record
            ORDER BY id ASC
            """)
    List<IntakeRecordEntity> findAllForLifecycleMigration();

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
                   enrichment_status,
                   enrichment_error_summary,
                   enrichment_updated_at,
                   converted_work_item_id,
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
                   enrichment_status,
                   enrichment_error_summary,
                   enrichment_updated_at,
                   converted_work_item_id,
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
            SET structured_data_json = #{structuredDataJson},
                development_owner_user_name = #{developmentOwnerUserName},
                demand_status = #{demandStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateLifecycleFields(@Param("id") Long id,
                              @Param("structuredDataJson") String structuredDataJson,
                              @Param("developmentOwnerUserName") String developmentOwnerUserName,
                              @Param("demandStatus") String demandStatus);

    @Update("""
            UPDATE pm_intake_record
            SET structured_data_json = #{structuredDataJson},
                ai_draft_json = #{aiDraftJson},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateEffortPayloads(@Param("id") Long id,
                             @Param("structuredDataJson") String structuredDataJson,
                             @Param("aiDraftJson") String aiDraftJson);

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
