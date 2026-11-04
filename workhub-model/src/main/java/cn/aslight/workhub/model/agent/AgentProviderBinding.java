package cn.aslight.workhub.model.agent;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;

/** 不含凭据的接入快照。修改接入类型/协议/配置后旧任务不能暗中使用新配置。 */
public record AgentProviderBinding(Long providerConfigId, String channelType, String apiProtocol, int configurationVersion) {
    public static AgentProviderBinding from(AiProviderConfigEntity provider) {
        return new AgentProviderBinding(provider.getId(), provider.getChannelType(), provider.getApiProtocol(),
                provider.getVersion() == null ? 0 : provider.getVersion());
    }
}
