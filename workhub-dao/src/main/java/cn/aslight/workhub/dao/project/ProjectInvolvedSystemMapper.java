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
            "SELECT id, system_scope, business_line, system_name, description, enabled, sort_order, created_at, updated_at",
            "FROM pm_project_involved_system",
            "<where>",
            "<if test='systemScope != null and systemScope != \"\"'>",
            "AND system_scope = #{systemScope}",
            "</if>",
            "<if test='businessLine != null'>",
            "AND business_line = #{businessLine}",
            "</if>",
            "<if test='enabledOnly'>",
            "AND enabled = 1",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (system_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR description LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY system_scope ASC, business_line ASC, enabled DESC, sort_order ASC, system_name ASC, id ASC",
            "</script>"
    })
    List<ProjectInvolvedSystemEntity> findAll(@Param("systemScope") String systemScope,
                                              @Param("businessLine") String businessLine,
                                              @Param("enabledOnly") boolean enabledOnly,
                                              @Param("keyword") String keyword);

    @Select("""
            SELECT id, system_scope, business_line, system_name, description, enabled, sort_order, created_at, updated_at
            FROM pm_project_involved_system
            WHERE id = #{id}
            """)
    ProjectInvolvedSystemEntity findById(Long id);

    @Select("""
            SELECT id, system_scope, business_line, system_name, description, enabled, sort_order, created_at, updated_at
            FROM pm_project_involved_system
            WHERE system_scope = #{systemScope}
              AND business_line = #{businessLine}
              AND system_name = #{systemName}
            LIMIT 1
            """)
    ProjectInvolvedSystemEntity findByIdentity(@Param("systemScope") String systemScope,
                                               @Param("businessLine") String businessLine,
                                               @Param("systemName") String systemName);

    @Select("""
            SELECT id, system_scope, business_line, system_name, description, enabled, sort_order, created_at, updated_at
            FROM pm_project_involved_system
            WHERE enabled = 1
              AND (
                    (system_scope = 'BUSINESS_LINE' AND business_line = #{businessLine})
                 OR (system_scope = 'MIDDLE_PLATFORM' AND business_line = '')
              )
            ORDER BY CASE system_scope WHEN 'BUSINESS_LINE' THEN 0 ELSE 1 END,
                     sort_order ASC,
                     system_name ASC,
                     id ASC
            """)
    List<ProjectInvolvedSystemEntity> findSelectableByBusinessLine(String businessLine);

    @Insert("""
            INSERT INTO pm_project_involved_system (
                system_scope, business_line, system_name, description, enabled, sort_order
            ) VALUES (
                #{systemScope}, #{businessLine}, #{systemName}, #{description}, #{enabled}, #{sortOrder}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProjectInvolvedSystemEntity entity);

    @Update("""
            UPDATE pm_project_involved_system
            SET system_scope = #{systemScope},
                business_line = #{businessLine},
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
              AND business_line = #{businessLine}
            """)
    int countByBusinessLine(String businessLine);
}
