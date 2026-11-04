package cn.aslight.workhub.model.agent;

import java.util.List;

public record AgentRunDetailResponse(AgentRunResponse run, AgentConfiguration configuration,
                                     AgentTaskSpecification specification, AgentProviderBinding providerBinding,
                                     List<AgentEventEntity> events, List<AgentArtifactEntity> artifacts) {
}
