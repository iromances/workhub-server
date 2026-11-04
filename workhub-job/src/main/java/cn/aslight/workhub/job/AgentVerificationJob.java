package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 只唤醒由真实工具调用显式登记的复查等待，不自行创建任务或发布。 */
@Component
public class AgentVerificationJob {
    private static final Logger log = LoggerFactory.getLogger(AgentVerificationJob.class);
    private final AgentVerificationService service;
    public AgentVerificationJob(AgentVerificationService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${workhub.agent.dispatch-interval-ms:5000}", initialDelayString = "${workhub.agent.initial-delay-ms:30000}")
    public void wake() {
        for (String id : service.due()) {
            try { service.wake(id); }
            catch (RuntimeException ex) { log.warn("Agent verification wake failed, verificationId={}", id, ex); }
        }
    }
}
