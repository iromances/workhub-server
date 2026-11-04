package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentExecutionCoordinator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 只消费明确启用和分派的任务；不自行创建 Agent、修改 AI 配置或触发发布。 */
@Component
public class AgentExecutionJob {
    private final AgentExecutionCoordinator coordinator;
    private final int globalLimit;

    public AgentExecutionJob(AgentExecutionCoordinator coordinator, @Value("${workhub.agent.max-concurrent-runs:4}") int globalLimit) {
        if (globalLimit < 1 || globalLimit > 32) throw new IllegalArgumentException("Agent 全局并发数必须在 1 到 32 之间");
        this.coordinator = coordinator;
        this.globalLimit = globalLimit;
    }

    @Scheduled(fixedDelayString = "${workhub.agent.dispatch-interval-ms:5000}", initialDelayString = "${workhub.agent.initial-delay-ms:30000}")
    public void dispatch() { coordinator.tick(globalLimit); }
}
