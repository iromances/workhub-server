package cn.aslight.workhub.service.ai;

/** 来自受信任任务运行器的请求；Provider 和模型来自已保存的 Agent 快照。 */
public record AiAgentApiRequest(Long providerConfigId, String model, String prompt, int timeoutSeconds,
                                int expectedProviderVersion, String expectedApiProtocol) {
}
