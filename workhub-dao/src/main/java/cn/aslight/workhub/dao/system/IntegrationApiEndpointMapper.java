package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.IntegrationApiEndpointEntity;
import cn.aslight.workhub.model.system.IntegrationApiGroupSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统集成官方 API 接口目录数据访问接口（只读查询；数据由迁移种子维护）。
 */
@Mapper
public interface IntegrationApiEndpointMapper {

    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_integration_api_endpoint
            WHERE system_code = #{systemCode}
            <if test='resourceGroup != null and resourceGroup != ""'>AND resource_group = #{resourceGroup}</if>
            <if test='httpMethod != null and httpMethod != ""'>AND http_method = #{httpMethod}</if>
            <if test='keyword != null and keyword != ""'>
              AND (api_name LIKE CONCAT('%', #{keyword}, '%') OR api_path LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long count(@Param("systemCode") String systemCode,
               @Param("resourceGroup") String resourceGroup,
               @Param("httpMethod") String httpMethod,
               @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT id, system_code, resource_group, resource_group_name, api_name, http_method, api_path,
                   summary, platform_used, source_url, source_note, sort_order, created_at, updated_at
            FROM sys_integration_api_endpoint
            WHERE system_code = #{systemCode}
            <if test='resourceGroup != null and resourceGroup != ""'>AND resource_group = #{resourceGroup}</if>
            <if test='httpMethod != null and httpMethod != ""'>AND http_method = #{httpMethod}</if>
            <if test='keyword != null and keyword != ""'>
              AND (api_name LIKE CONCAT('%', #{keyword}, '%') OR api_path LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY resource_group ASC, sort_order ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<IntegrationApiEndpointEntity> findPage(@Param("systemCode") String systemCode,
                                                @Param("resourceGroup") String resourceGroup,
                                                @Param("httpMethod") String httpMethod,
                                                @Param("keyword") String keyword,
                                                @Param("offset") long offset,
                                                @Param("pageSize") int pageSize);

    /** 资源组汇总按系统全量统计（供过滤下拉，不受 method/keyword 影响）。 */
    @Select("""
            SELECT resource_group AS resourceGroup, MAX(resource_group_name) AS resourceGroupName,
                   COUNT(*) AS endpointCount, SUM(platform_used) AS platformUsedCount, MIN(sort_order) AS sortOrder
            FROM sys_integration_api_endpoint
            WHERE system_code = #{systemCode}
            GROUP BY resource_group
            ORDER BY sortOrder ASC
            """)
    List<IntegrationApiGroupSummary> groupSummaries(@Param("systemCode") String systemCode);

    /** 来源文档去重口径（url + 任意一条 note）。 */
    @Select("""
            SELECT source_url AS sourceUrl, MIN(source_note) AS sourceNote
            FROM sys_integration_api_endpoint
            WHERE system_code = #{systemCode} AND source_url IS NOT NULL
            GROUP BY source_url
            """)
    List<Map<String, Object>> sourceEntries(@Param("systemCode") String systemCode);

    @Select("""
            SELECT MAX(created_at) FROM sys_integration_api_endpoint WHERE system_code = #{systemCode}
            """)
    LocalDateTime latestCreatedAt(@Param("systemCode") String systemCode);
}
