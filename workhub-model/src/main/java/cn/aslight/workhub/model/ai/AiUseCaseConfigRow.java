package cn.aslight.workhub.model.ai;

/**
 * AI 任务配置管理查询行，字段与 AiUseCaseConfigMapper.MANAGE_COLUMNS 一一对应；
 * 场景能力字段由服务层按 AiUseCaseDefinitions 填充为 AiUseCaseConfigResponse。
 */
public record AiUseCaseConfigRow(Long id,
                                 String useCaseCode,
                                 String useCaseName,
                                 String domain,
                                 String description,
                                 Long providerConfigId,
                                 String providerCode,
                                 String providerName,
                                 String channelType,
                                 String vendor,
                                 String modelProvider,
                                 String model,
                                 String reasoningLevel,
                                 String speedMode,
                                 Integer timeoutSeconds,
                                 String effectiveModel,
                                 String effectiveReasoningLevel,
                                 String effectiveSpeedMode,
                                 Integer effectiveTimeoutSeconds,
                                 String modelSource,
                                 String reasoningLevelSource,
                                 String speedModeSource,
                                 String timeoutSecondsSource,
                                 Boolean jsonSchemaEnabled,
                                 String schemaClasspath,
                                 String promptTemplate,
                                 String promptVariablesDesc,
                                 Integer promptVersion,
                                 String promptChecksum,
                                 Boolean enabled,
                                 String remark) {
}
