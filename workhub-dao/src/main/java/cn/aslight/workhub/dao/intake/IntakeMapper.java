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
            "structured_data_json,",
            "ai_draft_json,",
            "intake_status,",
            "demand_status,",
            "enrichment_status,",
            "enrichment_error_summary,",
            "enrichment_updated_at,",
            "converted_work_item_id,",
            "created_at,",
            "updated_at",
            "FROM pm_intake_record",
            "<where>",
            "<if test='status != null and status != \"\"'>",
            "AND intake_status = #{status}",
            "</if>",
            "<if test='sourceType != null and sourceType != \"\"'>",
            "AND source_type = #{sourceType}",
            "</if>",
            "<if test='enrichmentStatus != null and enrichmentStatus != \"\"'>",
            "AND enrichment_status = #{enrichmentStatus}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (sender_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR raw_content LIKE CONCAT('%', #{keyword}, '%')",
            "OR external_message_id LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY received_at DESC, id DESC",
            "</script>"
    })
    List<IntakeRecordEntity> findAll(@Param("status") String status,
                                     @Param("sourceType") String sourceType,
                                     @Param("keyword") String keyword,
                                     @Param("enrichmentStatus") String enrichmentStatus);

    @Select("""
            SELECT id,
                   source_type,
                   source_channel,
                   external_message_id,
                   sender_name,
                   received_at,
                   raw_content,
                   structured_data_json,
                   ai_draft_json,
                   intake_status,
                   demand_status,
                   enrichment_status,
                   enrichment_error_summary,
                   enrichment_updated_at,
                   converted_work_item_id,
                   created_at,
                   updated_at
            FROM pm_intake_record
            WHERE id = #{id}
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
                   structured_data_json,
                   ai_draft_json,
                   intake_status,
                   demand_status,
                   enrichment_status,
                   enrichment_error_summary,
                   enrichment_updated_at,
                   converted_work_item_id,
                   created_at,
                   updated_at
            FROM pm_intake_record
            WHERE external_message_id = #{externalMessageId}
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
                structured_data_json,
                ai_draft_json,
                intake_status,
                demand_status,
                enrichment_status,
                enrichment_error_summary,
                enrichment_updated_at,
                converted_work_item_id
            ) VALUES (
                #{sourceType},
                #{sourceChannel},
                #{externalMessageId},
                #{senderName},
                #{receivedAt},
                #{rawContent},
                #{structuredDataJson},
                #{aiDraftJson},
                #{intakeStatus},
                #{demandStatus},
                #{enrichmentStatus},
                #{enrichmentErrorSummary},
                #{enrichmentUpdatedAt},
                #{convertedWorkItemId}
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
                demand_status = #{demandStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateManagementFields(@Param("id") Long id,
                               @Param("structuredDataJson") String structuredDataJson,
                               @Param("demandStatus") String demandStatus);
}
