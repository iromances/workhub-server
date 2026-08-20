package cn.aslight.workhub.model.ai;

/**
 * AI 任务配置响应。
 */
public record AiUseCaseConfigResponse(Long id,
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
    public AiUseCaseConfigResponse(Long id, String useCaseCode, String useCaseName, String domain,
                                   String description, Long providerConfigId, String providerCode,
                                   String providerName, String channelType, String vendor, String modelProvider,
                                   String model, String reasoningLevel, String speedMode, Integer timeoutSeconds,
                                   Boolean jsonSchemaEnabled, String schemaClasspath, String promptTemplate,
                                   String promptVariablesDesc, Integer promptVersion, String promptChecksum,
                                   Boolean enabled, String remark) {
        this(id, useCaseCode, useCaseName, domain, description, providerConfigId, providerCode, providerName,
                channelType, vendor, modelProvider, model, reasoningLevel, speedMode, timeoutSeconds,
                model, reasoningLevel, speedMode, timeoutSeconds,
                "USE_CASE", "USE_CASE", "USE_CASE", "USE_CASE",
                jsonSchemaEnabled, schemaClasspath, promptTemplate, promptVariablesDesc,
                promptVersion, promptChecksum, enabled, remark);
    }
}
