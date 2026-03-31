package cn.aslight.workhub.domain.workitem.mapper;

import cn.aslight.workhub.domain.workitem.dto.WorkItemDetailResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemReminderCandidate;
import cn.aslight.workhub.domain.workitem.dto.WorkItemSummaryResponse;
import cn.aslight.workhub.domain.workitem.model.WorkItemEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkItemMapper {

    @Select({
            "<script>",
            "SELECT w.id,",
            "w.work_item_no AS no,",
            "w.title,",
            "w.work_item_type AS type,",
            "p.project_name AS projectName,",
            "w.owner_user_name AS ownerUserName,",
            "w.status,",
            "w.priority",
            "FROM pm_work_item w",
            "JOIN pm_project p ON p.id = w.project_id",
            "<where>",
            "<if test='projectId != null'>",
            "AND w.project_id = #{projectId}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND w.status = #{status}",
            "</if>",
            "<if test='type != null and type != \"\"'>",
            "AND w.work_item_type = #{type}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (w.work_item_no LIKE CONCAT('%', #{keyword}, '%')",
            "OR w.title LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY w.id DESC",
            "</script>"
    })
    List<WorkItemSummaryResponse> findAll(@Param("projectId") Long projectId,
                                          @Param("status") String status,
                                          @Param("type") String type,
                                          @Param("keyword") String keyword);

    @Select("""
            SELECT w.id,
                   w.work_item_no AS no,
                   w.project_id AS projectId,
                   p.project_name AS projectName,
                   w.sprint_id AS sprintId,
                   s.sprint_name AS sprintName,
                   w.release_id AS releaseId,
                   r.release_name AS releaseName,
                   w.work_item_type AS type,
                   w.title,
                   w.description,
                   w.source_type AS sourceType,
                   w.source_channel AS sourceChannel,
                   w.priority,
                   w.urgency,
                   w.status,
                   w.creator_user_name AS creatorUserName,
                   w.owner_user_name AS ownerUserName,
                   w.follower_user_name AS followerUserName,
                   w.proposer_name AS proposerName,
                   w.acceptance_criteria AS acceptanceCriteria,
                   w.planned_start_at AS plannedStartAt,
                   w.planned_end_at AS plannedEndAt,
                   w.finished_at AS finishedAt,
                   w.created_at AS createdAt,
                   w.updated_at AS updatedAt
            FROM pm_work_item w
            JOIN pm_project p ON p.id = w.project_id
            LEFT JOIN pm_sprint s ON s.id = w.sprint_id
            LEFT JOIN pm_release r ON r.id = w.release_id
            WHERE w.id = #{id}
            """)
    WorkItemDetailResponse findDetailById(Long id);

    @Select("""
            SELECT id,
                   work_item_no,
                   project_id,
                   sprint_id,
                   release_id,
                   work_item_type,
                   title,
                   description,
                   source_type,
                   source_channel,
                   priority,
                   urgency,
                   status,
                   creator_user_name,
                   owner_user_name,
                   follower_user_name,
                   proposer_name,
                   acceptance_criteria,
                   planned_start_at,
                   planned_end_at,
                   finished_at
            FROM pm_work_item
            WHERE id = #{id}
            """)
    WorkItemEntity findEntityById(Long id);

    @Insert("""
            INSERT INTO pm_work_item (
                work_item_no,
                project_id,
                sprint_id,
                release_id,
                work_item_type,
                title,
                description,
                source_type,
                source_channel,
                priority,
                urgency,
                status,
                creator_user_name,
                owner_user_name,
                follower_user_name,
                proposer_name,
                acceptance_criteria,
                planned_start_at,
                planned_end_at,
                finished_at
            ) VALUES (
                #{workItemNo},
                #{projectId},
                #{sprintId},
                #{releaseId},
                #{workItemType},
                #{title},
                #{description},
                #{sourceType},
                #{sourceChannel},
                #{priority},
                #{urgency},
                #{status},
                #{creatorUserName},
                #{ownerUserName},
                #{followerUserName},
                #{proposerName},
                #{acceptanceCriteria},
                #{plannedStartAt},
                #{plannedEndAt},
                #{finishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkItemEntity entity);

    @Update("""
            UPDATE pm_work_item
            SET title = #{title},
                description = #{description},
                priority = #{priority},
                urgency = #{urgency},
                proposer_name = #{proposerName},
                acceptance_criteria = #{acceptanceCriteria},
                planned_start_at = #{plannedStartAt},
                planned_end_at = #{plannedEndAt}
            WHERE id = #{id}
            """)
    int updateEditableFields(WorkItemEntity entity);

    @Update("""
            UPDATE pm_work_item
            SET status = #{status},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE pm_work_item
            SET owner_user_name = #{ownerUserName},
                follower_user_name = #{followerUserName}
            WHERE id = #{id}
            """)
    int updateAssignment(@Param("id") Long id,
                         @Param("ownerUserName") String ownerUserName,
                         @Param("followerUserName") String followerUserName);

    @Select("""
            SELECT w.id,
                   w.work_item_no AS no,
                   w.title,
                   p.project_name AS projectName,
                   w.owner_user_name AS ownerUserName,
                   w.status,
                   w.priority,
                   w.planned_end_at AS plannedEndAt
            FROM pm_work_item w
            JOIN pm_project p ON p.id = w.project_id
            WHERE w.planned_end_at IS NOT NULL
              AND w.planned_end_at <= #{deadline}
              AND w.status NOT IN ('已完成', '已拒绝', '已挂起')
            ORDER BY w.planned_end_at ASC, w.id ASC
            """)
    List<WorkItemReminderCandidate> findDueSoonItems(@Param("deadline") LocalDateTime deadline);
}
