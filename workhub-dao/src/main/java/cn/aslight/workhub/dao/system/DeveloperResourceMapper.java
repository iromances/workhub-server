package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.DeveloperResourceEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 研发人员资源数据访问接口。
 */
@Mapper
public interface DeveloperResourceMapper {

    @Select("""
            <script>
            SELECT id,
                   user_name AS userName,
                   display_name AS displayName,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pm_developer_resource
            WHERE 1 = 1
            <if test="enabledOnly">
              AND enabled = 1
            </if>
            <if test="keyword != null and keyword != ''">
              AND (
                user_name LIKE CONCAT('%', #{keyword}, '%')
                OR display_name LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY enabled DESC, user_name ASC
            </script>
            """)
    List<DeveloperResourceEntity> findAll(@Param("enabledOnly") boolean enabledOnly,
                                          @Param("keyword") String keyword);

    @Select("""
            SELECT id,
                   user_name AS userName,
                   display_name AS displayName,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pm_developer_resource
            WHERE id = #{id}
            """)
    DeveloperResourceEntity findById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   user_name AS userName,
                   display_name AS displayName,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pm_developer_resource
            WHERE user_name = #{userName}
            """)
    DeveloperResourceEntity findByUserName(@Param("userName") String userName);

    @Select("""
            SELECT user_name AS userName,
                   display_name AS displayName,
                   NULL AS businessLine
            FROM pm_developer_resource
            WHERE enabled = 1
            ORDER BY user_name ASC
            """)
    List<UserOptionResponse> findEnabledOptions();

    @Insert("""
            INSERT INTO pm_developer_resource (
                user_name,
                display_name,
                enabled,
                remark
            ) VALUES (
                #{userName},
                #{displayName},
                #{enabled},
                #{remark}
            )
            """)
    void insert(DeveloperResourceEntity entity);

    @Update("""
            UPDATE pm_developer_resource
            SET user_name = #{userName},
                display_name = #{displayName},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(DeveloperResourceEntity entity);

    @Delete("DELETE FROM pm_developer_resource WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
