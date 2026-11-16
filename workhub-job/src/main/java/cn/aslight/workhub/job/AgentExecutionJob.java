package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentExecutionCoordinator;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 只消费明确启用和分派的任务；不自行创建 Agent、修改 AI 配置或触发发布。 */
@Component
public class AgentExecutionJob implements WorkhubJob {
    private final AgentExecutionCoordinator coordinator;
    private final int globalLimit;

    public AgentExecutionJob(AgentExecutionCoordinator coordinator, @Value("${workhub.agent.max-concurrent-runs:20}") int globalLimit) {
        if (globalLimit < 1 || globalLimit > 32) throw new IllegalArgumentException("Agent 全局并发数必须在 1 到 32 之间");
        this.coordinator = coordinator;
        this.globalLimit = globalLimit;
    }

    @Override
    public String code() {
        return "AGENT_EXECUTION_DISPATCH";
    }

    @Override
    public String name() {
        return "Agent执行派发";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }

    @Override
    public String defaultScheduleValue() {
        return "5000";
    }

    @Override
    public String paramDescription() {
        return "无参数；手动触发即执行一次派发轮询（幂等）";
    }

    @Override
    public void run(String params) { coordinator.tick(globalLimit); }
}
