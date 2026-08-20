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
            "SELECT p.id,",
            "p.project_code AS code,",
            "p.project_name AS name,",
            "p.project_type AS type,",
            "p.business_line_code AS businessLineCode,",
            "bl.business_line_name AS businessLine,",
            "p.owner_user_name AS ownerUserName,",
            "p.project_status AS status",
            "FROM pm_project p",
            "LEFT JOIN pm_business_line bl ON bl.business_line_code = p.business_line_code",
            "<where>",
            "<if test='status != null and status != \"\"'>",
            "AND p.project_status = #{status}",
            "</if>",
            "<if test='businessLine != null and businessLine != \"\"'>",
            "AND p.business_line_code = #{businessLine}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (p.project_code LIKE CONCAT('%', #{keyword}, '%')",
            "OR p.project_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR bl.business_line_name LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY p.id DESC",
            "</script>"
    })
    List<ProjectSummaryResponse> findAll(@Param("status") String status,
                                         @Param("keyword") String keyword,
                                         @Param("businessLine") String businessLine);

    @Select("""
            SELECT p.id,
                   p.project_code AS code,
                   p.project_name AS name,
                   p.project_type AS type,
                   p.business_line_code AS businessLineCode,
                   bl.business_line_name AS businessLine,
                   p.owner_user_name AS ownerUserName,
                   p.project_status AS status,
                   p.description,
                   p.created_at AS createdAt,
                   p.updated_at AS updatedAt
            FROM pm_project p
            LEFT JOIN pm_business_line bl ON bl.business_line_code = p.business_line_code
            WHERE p.id = #{id}
            """)
    ProjectDetailResponse findDetailById(Long id);

    @Select("""
            SELECT p.id,
                   p.project_code,
                   p.project_name,
                   p.project_type,
                   bl.business_line_name AS business_line,
                   p.business_line_code,
                   p.project_status,
                   p.owner_user_name,
                   p.description
            FROM pm_project p
            LEFT JOIN pm_business_line bl ON bl.business_line_code = p.business_line_code
            WHERE p.id = #{id}
            """)
    ProjectEntity findEntityById(Long id);

    @Select("""
            SELECT p.id,
                   p.project_code,
                   p.project_name,
                   p.project_type,
                   bl.business_line_name AS business_line,
                   p.business_line_code,
                   p.project_status,
                   p.owner_user_name,
                   p.description
            FROM pm_project p
            LEFT JOIN pm_business_line bl ON bl.business_line_code = p.business_line_code
            WHERE p.project_code = #{projectCode}
            """)
    ProjectEntity findEntityByCode(String projectCode);

    @Select("""
            SELECT p.id,
                   p.project_code AS code,
                   p.project_name AS name,
                   p.project_type AS type,
                   p.business_line_code AS businessLineCode,
                   bl.business_line_name AS businessLine,
                   p.owner_user_name AS ownerUserName,
                   p.project_status AS status,
                   p.description,
                   p.created_at AS createdAt,
                   p.updated_at AS updatedAt
            FROM pm_project p
            LEFT JOIN pm_business_line bl ON bl.business_line_code = p.business_line_code
            WHERE p.business_line_code = #{businessLine}
               OR bl.business_line_name = #{businessLine}
            ORDER BY p.id ASC
            LIMIT 1
            """)
    ProjectDetailResponse findFirstDetailByBusinessLine(String businessLine);

    @Insert("""
            INSERT INTO pm_project (
                project_code,
                project_name,
                project_type,
                business_line_code,
                project_status,
                owner_user_name,
                description
            ) VALUES (
                #{projectCode},
                #{projectName},
                #{projectType},
                #{businessLineCode},
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
                business_line_code = #{businessLineCode},
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
            WHERE business_line_code = #{businessLineName}
            """)
    int countByBusinessLine(String businessLineName);
}
