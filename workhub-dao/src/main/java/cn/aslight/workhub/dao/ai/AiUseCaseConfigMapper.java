package cn.aslight.workhub.dao.ai;

import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigResponse;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AiUseCaseConfigMapper {
    String MANAGE_COLUMNS = "u.id, u.use_case_code AS useCaseCode, u.use_case_name AS useCaseName, u.domain, u.description, "
            + "u.provider_config_id AS providerConfigId, p.provider_code AS providerCode, p.provider_name AS providerName, "
            + "p.channel_type AS channelType, p.vendor, p.model_provider AS modelProvider, u.model, "
            + "u.reasoning_level AS reasoningLevel, u.speed_mode AS speedMode, u.timeout_seconds AS timeoutSeconds, "
            + "COALESCE(NULLIF(u.model,''), p.default_model) AS effectiveModel, "
            + "COALESCE(NULLIF(u.reasoning_level,''), p.default_reasoning_level) AS effectiveReasoningLevel, "
            + "COALESCE(NULLIF(u.speed_mode,''), p.default_speed_mode) AS effectiveSpeedMode, "
            + "CASE WHEN u.timeout_seconds>0 THEN u.timeout_seconds WHEN p.call_timeout_seconds>0 THEN p.call_timeout_seconds ELSE NULL END AS effectiveTimeoutSeconds, "
            + "CASE WHEN NULLIF(u.model,'') IS NOT NULL THEN 'USE_CASE' WHEN NULLIF(p.default_model,'') IS NOT NULL THEN 'PROVIDER' ELSE 'NONE' END AS modelSource, "
            + "CASE WHEN NULLIF(u.reasoning_level,'') IS NOT NULL THEN 'USE_CASE' WHEN NULLIF(p.default_reasoning_level,'') IS NOT NULL THEN 'PROVIDER' ELSE 'NONE' END AS reasoningLevelSource, "
            + "CASE WHEN NULLIF(u.speed_mode,'') IS NOT NULL THEN 'USE_CASE' WHEN NULLIF(p.default_speed_mode,'') IS NOT NULL THEN 'PROVIDER' ELSE 'NONE' END AS speedModeSource, "
            + "CASE WHEN u.timeout_seconds IS NOT NULL AND u.timeout_seconds>0 THEN 'USE_CASE' WHEN p.call_timeout_seconds IS NOT NULL AND p.call_timeout_seconds>0 THEN 'PROVIDER' ELSE 'NONE' END AS timeoutSecondsSource, "
            + "u.json_schema_enabled AS jsonSchemaEnabled, u.schema_classpath AS schemaClasspath, "
            + "u.prompt_template AS promptTemplate, u.prompt_variables_desc AS promptVariablesDesc, "
            + "u.prompt_version AS promptVersion, u.prompt_checksum AS promptChecksum, u.enabled, u.remark";
    String ENTITY_COLUMNS = "id, use_case_code, use_case_name, domain, description, provider_config_id, model, "
            + "reasoning_level, speed_mode, timeout_seconds, json_schema_enabled, schema_classpath, prompt_template, "
            + "prompt_variables_desc, prompt_version, prompt_checksum, enabled, remark, create_time, create_by, "
            + "modify_time, modify_by, delete_time, delete_by, is_delete AS deleted, version";

    @Select({"<script>", "SELECT " + MANAGE_COLUMNS + " FROM ai_use_case_config u LEFT JOIN ai_provider_config p ON p.id=u.provider_config_id AND p.is_delete=0",
            "<where>u.is_delete=0",
            "<if test='domain != null and domain != \"\"'>AND u.domain=#{domain}</if>",
            "<if test='providerConfigId != null'>AND u.provider_config_id=#{providerConfigId}</if>",
            "<if test='channelType != null and channelType != \"\"'>AND p.channel_type=#{channelType}</if>",
            "<if test='vendor != null and vendor != \"\"'>AND p.vendor=#{vendor}</if>",
            "<if test='modelProvider != null and modelProvider != \"\"'>AND p.model_provider=#{modelProvider}</if>",
            "<if test='enabled != null'>AND u.enabled=#{enabled}</if>",
            "<if test='keyword != null and keyword != \"\"'>AND (u.use_case_code LIKE CONCAT('%',#{keyword},'%') OR u.use_case_name LIKE CONCAT('%',#{keyword},'%') OR u.description LIKE CONCAT('%',#{keyword},'%'))</if>",
            "</where> ORDER BY u.domain,u.use_case_code", "</script>"})
    List<AiUseCaseConfigResponse> findAll(@Param("domain") String domain, @Param("providerConfigId") Long providerConfigId,
        @Param("channelType") String channelType, @Param("vendor") String vendor,
        @Param("modelProvider") String modelProvider, @Param("enabled") Boolean enabled, @Param("keyword") String keyword);

    @Select("SELECT " + MANAGE_COLUMNS + " FROM ai_use_case_config u LEFT JOIN ai_provider_config p ON p.id=u.provider_config_id AND p.is_delete=0 WHERE u.id=#{id} AND u.is_delete=0")
    AiUseCaseConfigResponse findDetailById(Long id);
    @Select("SELECT " + ENTITY_COLUMNS + " FROM ai_use_case_config WHERE id=#{id} AND is_delete=0")
    AiUseCaseConfigEntity findEntityById(Long id);
    @Select("SELECT " + ENTITY_COLUMNS + " FROM ai_use_case_config WHERE use_case_code=#{useCaseCode} AND is_delete=0 LIMIT 1")
    AiUseCaseConfigEntity findEntityByCode(String useCaseCode);

    @Insert("""
      INSERT INTO ai_use_case_config(use_case_code,use_case_name,domain,description,provider_config_id,model,
        reasoning_level,speed_mode,timeout_seconds,json_schema_enabled,schema_classpath,prompt_template,
        prompt_variables_desc,prompt_version,prompt_checksum,enabled,remark,create_time,create_by,modify_time,modify_by,is_delete,version)
      VALUES(#{useCaseCode},#{useCaseName},#{domain},#{description},#{providerConfigId},#{model},#{reasoningLevel},
        #{speedMode},#{timeoutSeconds},#{jsonSchemaEnabled},#{schemaClasspath},#{promptTemplate},#{promptVariablesDesc},
        #{promptVersion},#{promptChecksum},#{enabled},#{remark},CURRENT_TIMESTAMP,#{createBy},CURRENT_TIMESTAMP,#{modifyBy},0,0)
      """)
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(AiUseCaseConfigEntity entity);

    @Update("""
      UPDATE ai_use_case_config SET use_case_name=#{useCaseName},domain=#{domain},description=#{description},
        provider_config_id=#{providerConfigId},model=#{model},reasoning_level=#{reasoningLevel},speed_mode=#{speedMode},
        timeout_seconds=#{timeoutSeconds},json_schema_enabled=#{jsonSchemaEnabled},schema_classpath=#{schemaClasspath},
        prompt_template=#{promptTemplate},prompt_variables_desc=#{promptVariablesDesc},prompt_version=#{promptVersion},
        prompt_checksum=#{promptChecksum},enabled=#{enabled},remark=#{remark},modify_time=CURRENT_TIMESTAMP,
        modify_by=#{modifyBy},updated_at=CURRENT_TIMESTAMP,version=version+1
      WHERE id=#{id} AND is_delete=0 AND version=#{version}
      """)
    int update(AiUseCaseConfigEntity entity);
}
