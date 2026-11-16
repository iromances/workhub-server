package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.IntegrationCapabilityEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统集成接入能力数据访问接口。
 */
@Mapper
public interface IntegrationCapabilityMapper {

    @Select("""
            SELECT id, system_id, capability_code, capability_name, capability_type, impl_status, description, enabled, sort_order, remark, created_at, updated_at
            FROM sys_integration_capability
            WHERE system_id = #{systemId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<IntegrationCapabilityEntity> findBySystemId(Long systemId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_integration_capability
            WHERE system_id = #{systemId}
            """)
    long countBySystemId(Long systemId);

    @Insert("""
            INSERT INTO sys_integration_capability (
                system_id, capability_code, capability_name, capability_type, impl_status, description, enabled, sort_order, remark
            ) VALUES (
                #{systemId}, #{capabilityCode}, #{capabilityName}, #{capabilityType}, #{implStatus}, #{description}, #{enabled}, #{sortOrder}, #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IntegrationCapabilityEntity entity);

    @Delete("DELETE FROM sys_integration_capability WHERE system_id = #{systemId}")
    int deleteBySystemId(@Param("systemId") Long systemId);
}
