package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeRelatedWorkItemResponse;
import cn.aslight.workhub.model.intake.IntakeWorkItemRelationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 需求与正式工作项关联数据访问接口。
 */
@Mapper
public interface IntakeWorkItemRelationMapper {

    @Insert("""
            INSERT INTO pm_intake_work_item_relation (
                intake_id,
                work_item_id,
                draft_index,
                relation_type
            ) VALUES (
                #{intakeId},
                #{workItemId},
                #{draftIndex},
                #{relationType}
            )
            ON DUPLICATE KEY UPDATE
                draft_index = VALUES(draft_index),
                relation_type = VALUES(relation_type)
            """)
    int upsert(IntakeWorkItemRelationEntity entity);

    @Select("""
            SELECT w.id,
                   w.work_item_no AS no,
                   w.title,
                   w.work_item_type AS type,
                   p.project_name AS projectName,
                   w.owner_user_name AS ownerUserName,
                   w.status,
                   w.priority,
                   r.draft_index AS draftIndex,
                   r.relation_type AS relationType
            FROM pm_intake_work_item_relation r
            JOIN pm_work_item w ON w.id = r.work_item_id
            JOIN pm_project p ON p.id = w.project_id
            WHERE r.intake_id = #{intakeId}
            ORDER BY r.draft_index ASC, r.id ASC
            """)
    List<IntakeRelatedWorkItemResponse> findByIntakeId(@Param("intakeId") Long intakeId);

    @Select("""
            SELECT r.intake_id AS intakeId,
                   r.work_item_id AS workItemId,
                   r.draft_index AS draftIndex,
                   r.relation_type AS relationType,
                   r.created_at AS createdAt
            FROM pm_intake_work_item_relation r
            WHERE r.work_item_id = #{workItemId}
            ORDER BY r.id DESC
            LIMIT 1
            """)
    IntakeWorkItemRelationEntity findLatestByWorkItemId(@Param("workItemId") Long workItemId);
}
