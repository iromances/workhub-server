package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentReleaseCoordinator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentReleaseJob {
    private final AgentReleaseCoordinator coordinator;
    public AgentReleaseJob(AgentReleaseCoordinator coordinator) { this.coordinator = coordinator; }
    @Scheduled(fixedDelay = 5000, initialDelay = 30000)
    public void dispatch() { coordinator.tick(); }
}
