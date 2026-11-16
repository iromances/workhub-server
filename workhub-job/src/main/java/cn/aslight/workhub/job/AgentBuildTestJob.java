package cn.aslight.workhub.job;

import cn.aslight.workhub.service.agent.AgentBuildTestDispatcher;
import cn.aslight.workhub.service.agent.AgentBuildTestStore;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 仅处理工具实际登记的独立测试，不自动创建任务或生产构建。 */
@Component
public class AgentBuildTestJob implements WorkhubJob {
    private static final Logger log=LoggerFactory.getLogger(AgentBuildTestJob.class);
    private final AgentBuildTestDispatcher dispatcher;
    private final AgentBuildTestStore store;
    public AgentBuildTestJob(AgentBuildTestDispatcher dispatcher,AgentBuildTestStore store){this.dispatcher=dispatcher;this.store=store;}

    @Override
    public String code() {
        return "AGENT_BUILD_TEST_DISPATCH";
    }

    @Override
    public String name() {
        return "Agent构建测试轮询";
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
        return "无参数；手动触发即执行一次构建测试轮询（幂等）";
    }

    @Override
    public void run(String params){
        for(String id:dispatcher.due())try{dispatcher.advance(id);}catch(RuntimeException ex){log.warn("Agent build test polling failed, requestId={}",id,ex);}
        for(String id:dispatcher.wakeDue())try{store.wake(id);}catch(RuntimeException ex){log.warn("Agent build test wake failed, requestId={}",id,ex);}
    }
}
