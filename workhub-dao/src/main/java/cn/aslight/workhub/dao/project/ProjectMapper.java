package cn.aslight.workhub.dao.project;

import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.ProjectSummaryResponse;
import cn.aslight.workhub.model.project.ProjectEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 项目数据访问接口。
 */
@Mapper
public interface ProjectMapper {

    @Select({
            "<script>",
            "SELECT id,",
            "project_code AS code,",
            "project_name AS name,",
            "project_type AS type,",
            "owner_user_name AS ownerUserName,",
            "project_status AS status",
            "FROM pm_project",
            "<where>",
            "<if test='status != null and status != \"\"'>",
            "AND project_status = #{status}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (project_code LIKE CONCAT('%', #{keyword}, '%')",
            "OR project_name LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY id DESC",
            "</script>"
    })
    List<ProjectSummaryResponse> findAll(@Param("status") String status, @Param("keyword") String keyword);

    @Select("""
            SELECT id,
                   project_code AS code,
                   project_name AS name,
                   project_type AS type,
                   owner_user_name AS ownerUserName,
                   project_status AS status,
                   description,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pm_project
            WHERE id = #{id}
            """)
    ProjectDetailResponse findDetailById(Long id);

    @Select("""
            SELECT id,
                   project_code,
                   project_name,
                   project_type,
                   project_status,
                   owner_user_name,
                   description
            FROM pm_project
            WHERE id = #{id}
            """)
    ProjectEntity findEntityById(Long id);

    @Select("""
            SELECT id,
                   project_code,
                   project_name,
                   project_type,
                   project_status,
                   owner_user_name,
                   description
            FROM pm_project
            WHERE project_code = #{projectCode}
            """)
    ProjectEntity findEntityByCode(String projectCode);

    @Insert("""
            INSERT INTO pm_project (
                project_code,
                project_name,
                project_type,
                project_status,
                owner_user_name,
                description
            ) VALUES (
                #{projectCode},
                #{projectName},
                #{projectType},
                #{projectStatus},
                #{ownerUserName},
                #{description}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProjectEntity entity);

    @Update("""
            UPDATE pm_project
            SET project_code = #{projectCode},
                project_name = #{projectName},
                project_type = #{projectType},
                project_status = #{projectStatus},
                owner_user_name = #{ownerUserName},
                description = #{description}
            WHERE id = #{id}
            """)
    int update(ProjectEntity entity);
}
