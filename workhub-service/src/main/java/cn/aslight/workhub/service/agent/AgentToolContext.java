package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentConfiguration;
import cn.aslight.workhub.model.agent.AgentTaskSpecification;

/** 只传递任务身份和不可变配置，不向工具或模型传递通用平台凭据。 */
public record AgentToolContext(String runId, String taskId, String agentId, String requestedBy, String ownerToken,
                               AgentConfiguration configuration, AgentTaskSpecification specification) {
    public AgentToolContext(String runId, String taskId, String agentId, String requestedBy,
                            AgentConfiguration configuration, AgentTaskSpecification specification) {
        this(runId, taskId, agentId, requestedBy, null, configuration, specification);
    }
}
