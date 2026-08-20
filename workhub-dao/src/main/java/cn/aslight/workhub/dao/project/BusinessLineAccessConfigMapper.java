package cn.aslight.workhub.dao.project;

import cn.aslight.workhub.model.project.BusinessLineAccessConfigEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 业务线访问地址配置数据访问接口。
 */
@Mapper
public interface BusinessLineAccessConfigMapper {

    @Select("""
            <script>
            SELECT id, business_line_code, environment_code, endpoint_type, endpoint_name,
                   endpoint_url, path_prefix, sort_order, enabled, created_at, updated_at
            FROM pm_business_line_access_config
            WHERE business_line_code IN
            <foreach collection="businessLineCodes" item="code" open="(" separator="," close=")">
                #{code}
            </foreach>
            ORDER BY business_line_code, environment_code, sort_order, id
            </script>
            """)
    List<BusinessLineAccessConfigEntity> findByBusinessLineCodes(@Param("businessLineCodes") List<String> businessLineCodes);

    @Delete("DELETE FROM pm_business_line_access_config WHERE business_line_code = #{businessLineCode}")
    int deleteByBusinessLineCode(String businessLineCode);

    @Insert("""
            INSERT INTO pm_business_line_access_config (
                business_line_code, environment_code, endpoint_type, endpoint_name,
                endpoint_url, path_prefix, sort_order, enabled
            ) VALUES (
                #{businessLineCode}, #{environmentCode}, #{endpointType}, #{endpointName},
                #{endpointUrl}, #{pathPrefix}, #{sortOrder}, #{enabled}
            )
            """)
    int insert(BusinessLineAccessConfigEntity entity);
}
