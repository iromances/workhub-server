package cn.aslight.workhub.dao.project;

import cn.aslight.workhub.model.project.ProjectInvolvedSystemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 研发涉及系统清单数据访问接口。
 */
@Mapper
public interface ProjectInvolvedSystemMapper {

    @Select({
            "<script>",
            "SELECT s.id, s.system_scope, COALESCE(bl.business_line_name, '') AS business_line,",
            "s.business_line_code, s.system_name, s.description, s.enabled, s.sort_order, s.created_at, s.updated_at",
            "FROM pm_project_involved_system s",
            "LEFT JOIN pm_business_line bl ON bl.business_line_code = s.business_line_code",
            "<where>",
            "<if test='systemScope != null and systemScope != \"\"'>",
            "AND s.system_scope = #{systemScope}",
            "</if>",
            "<if test='businessLine != null'>",
            "AND s.business_line_code = #{businessLine}",
            "</if>",
            "<if test='enabledOnly'>",
            "AND s.enabled = 1",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (s.system_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR s.description LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY s.system_scope ASC, business_line ASC, s.enabled DESC, s.sort_order ASC, s.system_name ASC, s.id ASC",
            "</script>"
    })
    List<ProjectInvolvedSystemEntity> findAll(@Param("systemScope") String systemScope,
                                              @Param("businessLine") String businessLine,
                                              @Param("enabledOnly") boolean enabledOnly,
                                              @Param("keyword") String keyword);

    @Select("""
            SELECT s.id,
                   s.system_scope,
                   COALESCE(bl.business_line_name, '') AS business_line,
                   s.business_line_code,
                   s.system_name,
                   s.description,
                   s.enabled,
                   s.sort_order,
                   s.created_at,
                   s.updated_at
            FROM pm_project_involved_system s
            LEFT JOIN pm_business_line bl ON bl.business_line_code = s.business_line_code
            WHERE s.id = #{id}
            """)
    ProjectInvolvedSystemEntity findById(Long id);

    @Select("""
            SELECT s.id, s.system_scope, COALESCE(bl.business_line_name, '') AS business_line,
                   s.business_line_code, s.system_name, s.description, s.enabled, s.sort_order, s.created_at, s.updated_at
            FROM pm_project_involved_system s
            LEFT JOIN pm_business_line bl ON bl.business_line_code = s.business_line_code
            WHERE s.system_scope = #{systemScope}
              AND s.business_line_code = #{businessLine}
              AND s.system_name = #{systemName}
            LIMIT 1
            """)
    ProjectInvolvedSystemEntity findByIdentity(@Param("systemScope") String systemScope,
                                               @Param("businessLine") String businessLine,
                                               @Param("systemName") String systemName);

    @Select("""
            SELECT s.id,
                   s.system_scope,
                   COALESCE(bl.business_line_name, '') AS business_line,
                   s.business_line_code,
                   s.system_name,
                   s.description,
                   s.enabled,
                   s.sort_order,
                   s.created_at,
                   s.updated_at
            FROM pm_project_involved_system s
            LEFT JOIN pm_business_line bl ON bl.business_line_code = s.business_line_code
            WHERE s.enabled = 1
              AND (
                    (s.system_scope = 'BUSINESS_LINE'
                     AND (
                            s.business_line_code = #{businessLine}
                         OR bl.business_line_name = #{businessLine}
                     ))
                 OR (s.system_scope = 'MIDDLE_PLATFORM' AND s.business_line_code = '')
              )
            ORDER BY CASE s.system_scope WHEN 'BUSINESS_LINE' THEN 0 ELSE 1 END,
                     s.sort_order ASC,
                     s.system_name ASC,
                     s.id ASC
            """)
    List<ProjectInvolvedSystemEntity> findSelectableByBusinessLineCode(String businessLine);

    default List<ProjectInvolvedSystemEntity> findSelectableByBusinessLine(String businessLine) {
        return findSelectableByBusinessLineCode(businessLine);
    }

    @Insert("""
            INSERT INTO pm_project_involved_system (
                system_scope, business_line_code, system_name, description, enabled, sort_order
            ) VALUES (
                #{systemScope}, #{businessLineCode}, #{systemName}, #{description}, #{enabled}, #{sortOrder}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProjectInvolvedSystemEntity entity);

    @Update("""
            UPDATE pm_project_involved_system
            SET system_scope = #{systemScope},
                business_line_code = #{businessLineCode},
                system_name = #{systemName},
                description = #{description},
                enabled = #{enabled},
                sort_order = #{sortOrder},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(ProjectInvolvedSystemEntity entity);

    @Delete("""
            DELETE FROM pm_project_involved_system
            WHERE id = #{id}
            """)
    int deleteById(Long id);

    @Select("""
            SELECT COUNT(1)
            FROM pm_intake_development_analysis
            WHERE draft_json IS NOT NULL
              AND JSON_SEARCH(draft_json, 'one', #{systemName}, NULL, '$.workItems[*].systemTags[*]') IS NOT NULL
            """)
    int countDevelopmentAnalysisUsage(String systemName);

    @Select("""
            SELECT COUNT(1)
            FROM pm_project_involved_system
            WHERE system_scope = 'BUSINESS_LINE'
              AND business_line_code = #{businessLine}
            """)
    int countByBusinessLine(String businessLine);
}
