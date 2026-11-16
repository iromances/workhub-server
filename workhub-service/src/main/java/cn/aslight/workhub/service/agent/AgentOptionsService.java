package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.AgentOptionsResponse;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
public class AgentOptionsService {
    private final AgentAccessService access;
    private final AiProviderConfigService providers;
    private final BusinessLineMapper businessLines;

    public AgentOptionsService(AgentAccessService access, AiProviderConfigService providers,
                                BusinessLineMapper businessLines) {
        this.access = access;
        this.providers = providers;
        this.businessLines = businessLines;
    }

    public AgentOptionsResponse options(String user) {
        var actor = access.actor(user);
        actor.require("agent:view");
        var providerOptions = providers.list().stream().map(p -> new AgentOptionsResponse.Provider(p.id(), p.providerCode(),
                p.providerName(), p.channelType(), p.defaultModel(), Boolean.TRUE.equals(p.enabled()))).toList();
        var scopes = businessLines.findAll(null).stream().filter(line -> Boolean.TRUE.equals(line.getEnabled()))
                .map(line -> new AgentOptionsResponse.Scope(line.getBusinessLineCode(), line.getBusinessLineName(),
                        line.getSupportedEnvironments() == null ? List.of() : Arrays.stream(line.getSupportedEnvironments().split(","))
                                .map(String::trim).filter(value -> !value.isEmpty()).toList())).toList();
        return new AgentOptionsResponse(providerOptions, scopes);
    }
}
