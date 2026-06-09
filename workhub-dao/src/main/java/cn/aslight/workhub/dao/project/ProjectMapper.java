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
            "project_code AS code,",
            "project_name AS name,",
            "project_type AS type,",
            "business_line AS businessLine,",
            "owner_user_name AS ownerUserName,",
            "project_status AS status",
            "FROM pm_project",
            "<where>",
            "<if test='status != null and status != \"\"'>",
            "AND project_status = #{status}",
            "</if>",
            "<if test='businessLine != null and businessLine != \"\"'>",
            "AND business_line = #{businessLine}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (project_code LIKE CONCAT('%', #{keyword}, '%')",
            "OR project_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR business_line LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY id DESC",
            "</script>"
    })
    List<ProjectSummaryResponse> findAll(@Param("status") String status,
                                         @Param("keyword") String keyword,
                                         @Param("businessLine") String businessLine);

    @Select("""
            SELECT id,
                   project_code AS code,
                   project_name AS name,
                   project_type AS type,
                   business_line AS businessLine,
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
                   business_line,
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
                   business_line,
                   project_status,
                   owner_user_name,
                   description
            FROM pm_project
            WHERE project_code = #{projectCode}
            """)
    ProjectEntity findEntityByCode(String projectCode);

    @Select("""
            SELECT id,
                   project_code AS code,
                   project_name AS name,
                   project_type AS type,
                   business_line AS businessLine,
                   owner_user_name AS ownerUserName,
                   project_status AS status,
                   description,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pm_project
            WHERE business_line = #{businessLine}
            ORDER BY id ASC
            LIMIT 1
            """)
    ProjectDetailResponse findFirstDetailByBusinessLine(String businessLine);

    @Insert("""
            INSERT INTO pm_project (
                project_code,
                project_name,
                project_type,
                business_line,
                project_status,
                owner_user_name,
                description
            ) VALUES (
                #{projectCode},
                #{projectName},
                #{projectType},
                #{businessLine},
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
                business_line = #{businessLine},
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
            WHERE business_line = #{businessLineName}
            """)
    int countByBusinessLine(String businessLineName);
}
