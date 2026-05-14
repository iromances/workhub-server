package cn.aslight.workhub.dao.project;

import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.ProjectSummaryResponse;
import cn.aslight.workhub.model.project.ProjectEntity;
import org.apache.ibatis.annotations.Delete;
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
            "business_line_code AS businessLineCode,",
            "business_line_name AS businessLineName,",
            "project_code AS code,",
            "project_name AS name,",
            "project_type AS type,",
            "project_group AS `group`,",
            "owner_user_name AS ownerUserName,",
            "project_status AS status",
            "FROM pm_project",
            "<where>",
            "<if test='status != null and status != \"\"'>",
            "AND project_status = #{status}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (business_line_code LIKE CONCAT('%', #{keyword}, '%')",
            "OR business_line_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR project_code LIKE CONCAT('%', #{keyword}, '%')",
            "OR project_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR project_group LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY id DESC",
            "</script>"
    })
    List<ProjectSummaryResponse> findAll(@Param("status") String status, @Param("keyword") String keyword);

    @Select("""
            SELECT id,
                   business_line_code AS businessLineCode,
                   business_line_name AS businessLineName,
                   project_code AS code,
                   project_name AS name,
                   project_type AS type,
                   project_group AS `group`,
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
                   business_line_code,
                   business_line_name,
                   project_code,
                   project_name,
                   project_type,
                   project_group,
                   project_status,
                   owner_user_name,
                   description
            FROM pm_project
            WHERE id = #{id}
            """)
    ProjectEntity findEntityById(Long id);

    @Select("""
            SELECT id,
                   business_line_code,
                   business_line_name,
                   project_code,
                   project_name,
                   project_type,
                   project_group,
                   project_status,
                   owner_user_name,
                   description
            FROM pm_project
            WHERE project_code = #{projectCode}
            """)
    ProjectEntity findEntityByCode(String projectCode);

    @Select("""
            SELECT id,
                   business_line_code AS businessLineCode,
                   business_line_name AS businessLineName,
                   project_code AS code,
                   project_name AS name,
                   project_type AS type,
                   project_group AS `group`,
                   owner_user_name AS ownerUserName,
                   project_status AS status,
                   description,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pm_project
            WHERE project_group = #{projectGroup}
            ORDER BY id ASC
            LIMIT 1
            """)
    ProjectDetailResponse findFirstDetailByGroup(String projectGroup);

    @Insert("""
            INSERT INTO pm_project (
                project_code,
                project_name,
                business_line_code,
                business_line_name,
                project_type,
                project_group,
                project_status,
                owner_user_name,
                description
            ) VALUES (
                #{projectCode},
                #{projectName},
                #{businessLineCode},
                #{businessLineName},
                #{projectType},
                #{projectGroup},
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
                business_line_code = #{businessLineCode},
                business_line_name = #{businessLineName},
                project_type = #{projectType},
                project_group = #{projectGroup},
                project_status = #{projectStatus},
                owner_user_name = #{ownerUserName},
                description = #{description}
            WHERE id = #{id}
            """)
    int update(ProjectEntity entity);

    @Delete("""
            DELETE FROM pm_project
            WHERE id = #{id}
            """)
    int deleteById(Long id);

    @Select("""
            SELECT COUNT(1)
            FROM pm_work_item
            WHERE project_id = #{id}
            """)
    int countWorkItems(Long id);

    @Select("""
            SELECT COUNT(1)
            FROM pm_sprint
            WHERE project_id = #{id}
            """)
    int countSprints(Long id);

    @Select("""
            SELECT COUNT(1)
            FROM pm_release
            WHERE project_id = #{id}
            """)
    int countReleases(Long id);

    @Select("""
            SELECT COUNT(1)
            FROM pay_project_merchant_binding
            WHERE project_id = #{id}
            """)
    int countPaymentBindings(Long id);

    @Select("""
            SELECT COUNT(1)
            FROM pm_intake_development_analysis
            WHERE project_id = #{id}
            """)
    int countDevelopmentAnalyses(Long id);

    @Select("""
            SELECT COUNT(1)
            FROM pm_project
            WHERE project_group = #{groupName}
            """)
    int countByProjectGroup(String groupName);
}
