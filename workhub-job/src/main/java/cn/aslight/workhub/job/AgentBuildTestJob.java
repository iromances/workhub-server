package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentBuildTestDispatcher;
import cn.aslight.workhub.service.agent.AgentBuildTestStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 仅处理工具实际登记的独立测试，不自动创建任务或生产构建。 */
@Component
public class AgentBuildTestJob {
    private static final Logger log=LoggerFactory.getLogger(AgentBuildTestJob.class);
    private final AgentBuildTestDispatcher dispatcher;
    private final AgentBuildTestStore store;
    public AgentBuildTestJob(AgentBuildTestDispatcher dispatcher,AgentBuildTestStore store){this.dispatcher=dispatcher;this.store=store;}
    @Scheduled(fixedDelayString="${workhub.agent.dispatch-interval-ms:5000}",initialDelayString="${workhub.agent.initial-delay-ms:30000}")
    public void dispatch(){
        for(String id:dispatcher.due())try{dispatcher.advance(id);}catch(RuntimeException ex){log.warn("Agent build test polling failed, requestId={}",id,ex);}
        for(String id:dispatcher.wakeDue())try{store.wake(id);}catch(RuntimeException ex){log.warn("Agent build test wake failed, requestId={}",id,ex);}
    }
}
