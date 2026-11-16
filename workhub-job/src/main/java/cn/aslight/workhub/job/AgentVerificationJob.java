package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentVerificationService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 只唤醒由真实工具调用显式登记的复查等待，不自行创建任务或发布。 */
@Component
public class AgentVerificationJob implements WorkhubJob {
    private static final Logger log = LoggerFactory.getLogger(AgentVerificationJob.class);
    private final AgentVerificationService service;
    public AgentVerificationJob(AgentVerificationService service) { this.service = service; }

    @Override
    public String code() {
        return "AGENT_VERIFICATION_WAKE";
    }

    @Override
    public String name() {
        return "Agent复查唤醒";
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
        return "无参数；手动触发即唤醒一次到期复查（幂等）";
    }

    @Override
    public void run(String params) {
        for (String id : service.due()) {
            try { service.wake(id); }
            catch (RuntimeException ex) { log.warn("Agent verification wake failed, verificationId={}", id, ex); }
        }
    }
}
