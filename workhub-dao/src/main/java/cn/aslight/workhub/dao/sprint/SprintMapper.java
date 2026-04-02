package cn.aslight.workhub.dao.sprint;

import cn.aslight.workhub.model.sprint.SprintDetailResponse;
import cn.aslight.workhub.model.sprint.SprintSummaryResponse;
import cn.aslight.workhub.model.sprint.SprintEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 迭代数据访问接口。
 */
@Mapper
public interface SprintMapper {

    @Select({
            "<script>",
            "SELECT s.id,",
            "s.project_id AS projectId,",
            "p.project_name AS projectName,",
            "s.sprint_name AS name,",
            "s.sprint_status AS status,",
            "s.start_date AS startDate,",
            "s.end_date AS endDate",
            "FROM pm_sprint s",
            "JOIN pm_project p ON p.id = s.project_id",
            "<where>",
            "<if test='projectId != null'>",
            "AND s.project_id = #{projectId}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND s.sprint_status = #{status}",
            "</if>",
            "</where>",
            "ORDER BY s.id DESC",
            "</script>"
    })
    List<SprintSummaryResponse> findAll(@Param("projectId") Long projectId, @Param("status") String status);

    @Select("""
            SELECT s.id,
                   s.project_id AS projectId,
                   p.project_name AS projectName,
                   s.sprint_name AS name,
                   s.sprint_status AS status,
                   s.start_date AS startDate,
                   s.end_date AS endDate,
                   s.created_at AS createdAt,
                   s.updated_at AS updatedAt
            FROM pm_sprint s
            JOIN pm_project p ON p.id = s.project_id
            WHERE s.id = #{id}
            """)
    SprintDetailResponse findDetailById(Long id);

    @Select("""
            SELECT id,
                   project_id,
                   sprint_name,
                   sprint_status,
                   start_date,
                   end_date
            FROM pm_sprint
            WHERE id = #{id}
            """)
    SprintEntity findEntityById(Long id);

    @Insert("""
            INSERT INTO pm_sprint (
                project_id,
                sprint_name,
                sprint_status,
                start_date,
                end_date
            ) VALUES (
                #{projectId},
                #{sprintName},
                #{sprintStatus},
                #{startDate},
                #{endDate}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SprintEntity entity);

    @Update("""
            UPDATE pm_sprint
            SET project_id = #{projectId},
                sprint_name = #{sprintName},
                sprint_status = #{sprintStatus},
                start_date = #{startDate},
                end_date = #{endDate}
            WHERE id = #{id}
            """)
    int update(SprintEntity entity);
}
