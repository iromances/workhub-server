package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统集成台账数据访问接口。
 */
@Mapper
public interface IntegrationSystemMapper {

    @Select("""
            SELECT id, system_code, system_name, integration_method, auth_type, config_groups, enabled, sort_order, remark, created_at, updated_at, created_by, updated_by
            FROM sys_integration_system
            ORDER BY sort_order ASC, id ASC
            """)
    List<IntegrationSystemEntity> findAll();

    @Select("""
            SELECT id, system_code, system_name, integration_method, auth_type, config_groups, enabled, sort_order, remark, created_at, updated_at, created_by, updated_by
            FROM sys_integration_system
            WHERE id = #{id}
            """)
    IntegrationSystemEntity findById(Long id);

    @Select("""
            SELECT id, system_code, system_name, integration_method, auth_type, config_groups, enabled, sort_order, remark, created_at, updated_at, created_by, updated_by
            FROM sys_integration_system
            WHERE system_code = #{systemCode}
            LIMIT 1
            """)
    IntegrationSystemEntity findByCode(String systemCode);

    @Insert("""
            INSERT INTO sys_integration_system (
                system_code, system_name, integration_method, auth_type, config_groups, enabled, sort_order, remark, created_by, updated_by
            ) VALUES (
                #{systemCode}, #{systemName}, #{integrationMethod}, #{authType}, #{configGroups}, #{enabled}, #{sortOrder}, #{remark}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntegrationSystemEntity entity);

    @Update("""
            UPDATE sys_integration_system
            SET system_name = #{systemName},
                integration_method = #{integrationMethod},
                auth_type = #{authType},
                config_groups = #{configGroups},
                enabled = #{enabled},
                sort_order = #{sortOrder},
                remark = #{remark},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(IntegrationSystemEntity entity);
}
