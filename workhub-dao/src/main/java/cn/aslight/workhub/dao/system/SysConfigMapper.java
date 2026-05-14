package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.SysConfigItemEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统配置数据访问接口。
 */
@Mapper
public interface SysConfigMapper {

    @Select({
            "<script>",
            "SELECT id, config_group, config_key, config_name, value_type, plain_value, encrypted_value, masked_value, enabled, remark, created_at, updated_at",
            "FROM sys_config_item",
            "<where>",
            "<if test='configGroup != null and configGroup != \"\"'>",
            "AND config_group = #{configGroup}",
            "</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (config_key LIKE CONCAT('%', #{keyword}, '%')",
            "OR config_name LIKE CONCAT('%', #{keyword}, '%')",
            "OR remark LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</where>",
            "ORDER BY config_group ASC, config_key ASC",
            "</script>"
    })
    List<SysConfigItemEntity> findAll(@Param("configGroup") String configGroup,
                                      @Param("keyword") String keyword);

    @Select("""
            SELECT id, config_group, config_key, config_name, value_type, plain_value, encrypted_value, masked_value, enabled, remark, created_at, updated_at
            FROM sys_config_item
            WHERE id = #{id}
            """)
    SysConfigItemEntity findById(Long id);

    @Select("""
            SELECT id, config_group, config_key, config_name, value_type, plain_value, encrypted_value, masked_value, enabled, remark, created_at, updated_at
            FROM sys_config_item
            WHERE config_group = #{configGroup}
              AND config_key = #{configKey}
              AND enabled = 1
            LIMIT 1
            """)
    SysConfigItemEntity findEnabledByKey(@Param("configGroup") String configGroup,
                                         @Param("configKey") String configKey);

    @Insert("""
            INSERT INTO sys_config_item (
                config_group, config_key, config_name, value_type, plain_value, encrypted_value, masked_value, enabled, remark
            ) VALUES (
                #{configGroup}, #{configKey}, #{configName}, #{valueType}, #{plainValue}, #{encryptedValue}, #{maskedValue}, #{enabled}, #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysConfigItemEntity entity);

    @Update("""
            UPDATE sys_config_item
            SET config_group = #{configGroup},
                config_key = #{configKey},
                config_name = #{configName},
                value_type = #{valueType},
                plain_value = #{plainValue},
                encrypted_value = #{encryptedValue},
                masked_value = #{maskedValue},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(SysConfigItemEntity entity);
}
