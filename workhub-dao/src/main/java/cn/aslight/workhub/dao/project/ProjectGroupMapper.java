package cn.aslight.workhub.dao.project;

import cn.aslight.workhub.model.project.ProjectGroupEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 项目组数据访问接口。
 */
@Mapper
public interface ProjectGroupMapper {

    /**
     * 查询项目组列表。
     *
     * @param keyword 关键字，可为空
     * @return 项目组列表
     */
    @Select({
            "<script>",
            "SELECT id, group_name, gitlab_group_name, description, enabled, created_at, updated_at",
            "FROM pm_project_group",
            "<where>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (group_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR gitlab_group_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR description LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY enabled DESC, group_name ASC",
            "</script>"
    })
    List<ProjectGroupEntity> findAll(@Param("keyword") String keyword);

    /**
     * 按 ID 查询项目组。
     *
     * @param id 项目组 ID
     * @return 项目组实体
     */
    @Select("""
            SELECT id, group_name, gitlab_group_name, description, enabled, created_at, updated_at
            FROM pm_project_group
            WHERE id = #{id}
            """)
    ProjectGroupEntity findById(Long id);

    /**
     * 按项目组名称查询项目组。
     *
     * @param groupName 项目组名称
     * @return 项目组实体
     */
    @Select("""
            SELECT id, group_name, gitlab_group_name, description, enabled, created_at, updated_at
            FROM pm_project_group
            WHERE group_name = #{groupName}
            LIMIT 1
            """)
    ProjectGroupEntity findByName(String groupName);

    /**
     * 新增项目组。
     *
     * @param entity 项目组实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO pm_project_group (
                group_name, gitlab_group_name, description, enabled
            ) VALUES (
                #{groupName}, #{gitlabGroupName}, #{description}, #{enabled}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProjectGroupEntity entity);

    /**
     * 更新项目组。
     *
     * @param entity 项目组实体
     * @return 影响行数
     */
    @Update("""
            UPDATE pm_project_group
            SET group_name = #{groupName},
                gitlab_group_name = #{gitlabGroupName},
                description = #{description},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(ProjectGroupEntity entity);

    /**
     * 删除项目组。
     *
     * @param id 项目组 ID
     * @return 影响行数
     */
    @Delete("""
            DELETE FROM pm_project_group
            WHERE id = #{id}
            """)
    int deleteById(Long id);

    /**
     * 统计项目组成员数。
     *
     * @param groupName 项目组名称
     * @return 成员数
     */
    @Select("""
            SELECT COUNT(1)
            FROM pm_project_group_member
            WHERE project_group = #{groupName}
            """)
    int countMembers(String groupName);
}
