package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentReleaseCoordinator;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

@Component
public class AgentReleaseJob implements WorkhubJob {
    private final AgentReleaseCoordinator coordinator;
    public AgentReleaseJob(AgentReleaseCoordinator coordinator) { this.coordinator = coordinator; }

    @Override
    public String code() {
        return "AGENT_RELEASE_DISPATCH";
    }

    @Override
    public String name() {
        return "Agent发布派发";
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
        return "无参数；手动触发即执行一次发布轮询（幂等）";
    }

    @Override
    public void run(String params) { coordinator.tick(); }
}
