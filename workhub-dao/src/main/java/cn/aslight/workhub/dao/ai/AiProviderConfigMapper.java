package cn.aslight.workhub.dao.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AiProviderConfigMapper {
    String COLUMNS = "id, provider_code, provider_name, channel_type, vendor, model_provider, "
            + "default_model, default_reasoning_level, default_speed_mode, api_protocol, api_base_url, api_key, "
            + "cli_command, cli_working_directory, connect_timeout_seconds, read_timeout_seconds, call_timeout_seconds, "
            + "site_url, app_name, enabled, remark, create_time, create_by, modify_time, modify_by, "
            + "delete_time, delete_by, is_delete AS deleted, version";

    @Select("SELECT " + COLUMNS + " FROM ai_provider_config WHERE is_delete = 0 ORDER BY channel_type, vendor, model_provider, provider_code")
    List<AiProviderConfigEntity> findAll();

    @Select("SELECT " + COLUMNS + " FROM ai_provider_config WHERE id = #{id} AND is_delete = 0")
    AiProviderConfigEntity findById(Long id);

    @Select("SELECT " + COLUMNS + " FROM ai_provider_config WHERE provider_code = #{providerCode} AND is_delete = 0 LIMIT 1")
    AiProviderConfigEntity findByCode(String providerCode);

    @Insert("""
        INSERT INTO ai_provider_config(provider_code, provider_name, channel_type, vendor, model_provider,
          default_model, default_reasoning_level, default_speed_mode, api_protocol, api_base_url, api_key,
          cli_command, cli_working_directory, connect_timeout_seconds, read_timeout_seconds, call_timeout_seconds,
          site_url, app_name, enabled, remark, create_time, create_by, modify_time, modify_by, is_delete, version)
        VALUES(#{providerCode}, #{providerName}, #{channelType}, #{vendor}, #{modelProvider},
          #{defaultModel}, #{defaultReasoningLevel}, #{defaultSpeedMode}, #{apiProtocol}, #{apiBaseUrl}, #{apiKey},
          #{cliCommand}, #{cliWorkingDirectory}, #{connectTimeoutSeconds}, #{readTimeoutSeconds}, #{callTimeoutSeconds},
          #{siteUrl}, #{appName}, #{enabled}, #{remark}, CURRENT_TIMESTAMP, #{createBy}, CURRENT_TIMESTAMP, #{modifyBy}, 0, 0)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiProviderConfigEntity entity);

    @Update("""
        UPDATE ai_provider_config SET provider_name=#{providerName}, channel_type=#{channelType}, vendor=#{vendor},
          model_provider=#{modelProvider}, default_model=#{defaultModel}, default_reasoning_level=#{defaultReasoningLevel},
          default_speed_mode=#{defaultSpeedMode}, api_protocol=#{apiProtocol}, api_base_url=#{apiBaseUrl}, api_key=#{apiKey},
          cli_command=#{cliCommand}, cli_working_directory=#{cliWorkingDirectory},
          connect_timeout_seconds=#{connectTimeoutSeconds}, read_timeout_seconds=#{readTimeoutSeconds},
          call_timeout_seconds=#{callTimeoutSeconds}, site_url=#{siteUrl}, app_name=#{appName}, enabled=#{enabled},
          remark=#{remark}, modify_time=CURRENT_TIMESTAMP, modify_by=#{modifyBy}, updated_at=CURRENT_TIMESTAMP,
          version=version+1 WHERE id=#{id} AND is_delete=0 AND version=#{version}
        """)
    int update(AiProviderConfigEntity entity);

    @Select("SELECT COUNT(1) FROM ai_use_case_config WHERE provider_config_id=#{providerConfigId} AND is_delete=0")
    int countUseCases(@Param("providerConfigId") Long providerConfigId);
}
