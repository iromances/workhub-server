package cn.aslight.workhub.model.ai;

import java.util.List;

/**
 * AI 任务配置响应；capability/allowedChannelTypes/allowedCliTypes 由服务层按
 * AiUseCaseDefinitions 填充，未注册的场景编码三者均为 null。
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
                                      String remark,
                                      String capability,
                                      List<String> allowedChannelTypes,
                                      List<String> allowedCliTypes) {
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
                promptVersion, promptChecksum, enabled, remark,
                null, null, null);
    }

    public static AiUseCaseConfigResponse fromRow(AiUseCaseConfigRow row,
                                                  String capability,
                                                  List<String> allowedChannelTypes,
                                                  List<String> allowedCliTypes) {
        return new AiUseCaseConfigResponse(row.id(), row.useCaseCode(), row.useCaseName(), row.domain(),
                row.description(), row.providerConfigId(), row.providerCode(), row.providerName(),
                row.channelType(), row.vendor(), row.modelProvider(), row.model(), row.reasoningLevel(),
                row.speedMode(), row.timeoutSeconds(), row.effectiveModel(), row.effectiveReasoningLevel(),
                row.effectiveSpeedMode(), row.effectiveTimeoutSeconds(), row.modelSource(),
                row.reasoningLevelSource(), row.speedModeSource(), row.timeoutSecondsSource(),
                row.jsonSchemaEnabled(), row.schemaClasspath(), row.promptTemplate(), row.promptVariablesDesc(),
                row.promptVersion(), row.promptChecksum(), row.enabled(), row.remark(),
                capability, allowedChannelTypes, allowedCliTypes);
    }
}
