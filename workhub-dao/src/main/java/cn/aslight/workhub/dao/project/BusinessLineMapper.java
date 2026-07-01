package cn.aslight.workhub.dao.project;

import cn.aslight.workhub.model.project.BusinessLineEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 业务线数据访问接口。
 */
@Mapper
public interface BusinessLineMapper {

    /**
     * 查询业务线列表。
     *
     * @param keyword 关键字，可为空
     * @return 业务线列表
     */
    @Select({
            "<script>",
            "SELECT id, business_line_code, business_line_name, gitlab_group_name, description, enabled, created_at, updated_at",
            "FROM pm_business_line",
            "<where>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (business_line_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR gitlab_group_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR description LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY enabled DESC, business_line_name ASC",
            "</script>"
    })
    List<BusinessLineEntity> findAll(@Param("keyword") String keyword);

    /**
     * 按 ID 查询业务线。
     *
     * @param id 业务线 ID
     * @return 业务线实体
     */
    @Select("""
            SELECT id, business_line_code, business_line_name, gitlab_group_name, description, enabled, created_at, updated_at
            FROM pm_business_line
            WHERE id = #{id}
            """)
    BusinessLineEntity findById(Long id);

    /**
     * 按业务线名称查询业务线。
     *
     * @param businessLineName 业务线名称
     * @return 业务线实体
     */
    @Select("""
            SELECT id, business_line_code, business_line_name, gitlab_group_name, description, enabled, created_at, updated_at
            FROM pm_business_line
            WHERE business_line_name = #{businessLineName}
            LIMIT 1
            """)
    BusinessLineEntity findByName(String businessLineName);

    /**
     * 按业务线稳定编码查询业务线。
     *
     * @param businessLineCode 业务线稳定编码
     * @return 业务线实体
     */
    @Select("""
            SELECT id, business_line_code, business_line_name, gitlab_group_name, description, enabled, created_at, updated_at
            FROM pm_business_line
            WHERE business_line_code = #{businessLineCode}
            LIMIT 1
            """)
    BusinessLineEntity findByCode(String businessLineCode);

    /**
     * 查询当前最大自动流水编码。
     *
     * @return 最大业务线稳定编码
     */
    @Select("""
            SELECT MAX(business_line_code)
            FROM pm_business_line
            WHERE business_line_code REGEXP '^BL[0-9]{6}$'
            """)
    String findMaxBusinessLineCode();

    /**
     * 新增业务线。
     *
     * @param entity 业务线实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO pm_business_line (
                business_line_code, business_line_name, gitlab_group_name, description, enabled
            ) VALUES (
                #{businessLineCode}, #{businessLineName}, #{gitlabGroupName}, #{description}, #{enabled}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BusinessLineEntity entity);

    /**
     * 更新业务线。
     *
     * @param entity 业务线实体
     * @return 影响行数
     */
    @Update("""
            UPDATE pm_business_line
            SET business_line_name = #{businessLineName},
                gitlab_group_name = #{gitlabGroupName},
                description = #{description},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(BusinessLineEntity entity);

    /**
     * 删除业务线。
     *
     * @param id 业务线 ID
     * @return 影响行数
     */
    @Delete("""
            DELETE FROM pm_business_line
            WHERE id = #{id}
            """)
    int deleteById(Long id);

    /**
     * 统计业务线成员数。
     *
     * @param businessLineName 业务线名称
     * @return 成员数
     */
    @Select("""
            SELECT COUNT(1)
            FROM pm_business_line_member
            WHERE business_line = #{businessLineName}
            """)
    int countMembers(String businessLineName);
}
