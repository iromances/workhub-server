package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentBuildTestMapper;
import cn.aslight.workhub.model.agent.AgentBuildTestEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 只推进已登记的 TEST 独立测试；跨重启沿原句柄查询，不重发未知请求。 */
@Service
public class AgentBuildTestDispatcher {
    private final AgentBuildTestMapper tests;
    private final AgentBuildTestStore store;
    private final AgentJenkinsPolicy policy;
    private final AgentJenkinsClient client;
    private final AgentToolData data;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired public AgentBuildTestDispatcher(AgentBuildTestMapper tests,AgentBuildTestStore store,AgentJenkinsPolicy policy,
            AgentJenkinsClient client,AgentToolData data,ObjectMapper json) {
        this(tests,store,policy,client,data,json,Clock.systemUTC());
    }
    AgentBuildTestDispatcher(AgentBuildTestMapper tests,AgentBuildTestStore store,AgentJenkinsPolicy policy,
            AgentJenkinsClient client,AgentToolData data,ObjectMapper json,Clock clock) {
        this.tests=tests;this.store=store;this.policy=policy;this.client=client;this.data=data;this.json=json;this.clock=clock;
    }
    public List<String> due(){return tests.due(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));}
    public List<String> wakeDue(){return tests.wakeDue();}
    public AgentBuildTestEntity advance(String id) {
        var row=store.claim(id);if(row==null)return tests.find(id);
        try {
            var binding=store.binding(row);var parameters=store.parameters(row);
            if("PREPARED".equals(row.status())) {
                store.validate(row);var target=policy.target(binding);var probe=client.probe(target);
                if(!probe.buildable() || !String.join("/",binding.binding().jobSegments()).equals(probe.jobName())
                        || !probe.parameterNames().containsAll(policy.requiredParameters(binding.binding())))
                    return save(row,"BLOCKED",null,null,null,null,"独立测试 Job 身份、可构建状态或参数不符合固定绑定");
                target=policy.target(binding);row=store.intent(row);
                try{return save(row,"QUEUED",client.submit(target,parameters),null,null,null,null);}
                catch(AgentJenkinsClient.SubmissionRejected ex){return save(row,"REJECTED",null,null,null,null,ex.getMessage());}
                catch(AgentJenkinsClient.SubmissionUnknown ex){return save(row,"UNKNOWN",null,null,null,null,ex.getMessage());}
            }
            var target=policy.target(binding);
            if(row.buildNumber()!=null)return observe(row,binding,target,parameters);
            if(row.queueId()!=null) {
                var queue=client.queue(target,row.queueId(),parameters);
                if(!"EXPIRED".equals(queue.state())) {
                    if(!queue.parametersMatch())return save(row,"UNKNOWN",null,null,null,null,"队列参数与原测试请求不一致");
                    return switch(queue.state()) {
                        case "CANCELLED"->save(row,"ABORTED",null,null,"ABORTED",null,"Jenkins 确认队列已取消，未重发");
                        case "STARTED"->save(row,"BUILDING",null,queue.buildNumber(),null,null,null);
                        case "QUEUED"->save(row,"QUEUED",null,null,null,null,null);
                        default->save(row,"UNKNOWN",null,null,null,null,"队列状态未确认");
                    };
                }
            }
            var found=client.find(target,binding.binding().correlationParameter(),row.id(),parameters);
            if(!"FOUND".equals(found.state()))return save(row,"UNKNOWN",null,null,null,null,"原测试请求核对结果："+found.state()+"；未重发");
            Long queue=found.queueIds().isEmpty()?null:found.queueIds().getFirst(),build=found.buildNumbers().isEmpty()?null:found.buildNumbers().getFirst();
            if(row.queueId()!=null&&queue!=null&&!row.queueId().equals(queue))return save(row,"UNKNOWN",null,null,null,null,"不得替换原队列句柄");
            return save(row,build==null?"QUEUED":"BUILDING",queue,build,null,null,null);
        }catch(AgentBuildTestStore.LeaseLost ex){throw ex;}
        catch(AgentJenkinsClient.ClientFailure ex){return save(row,"PREPARED".equals(row.status())?"PREPARED":"UNKNOWN",null,null,row.buildResult(),row.receiptJson(),ex.getMessage());}
        catch(IllegalArgumentException|IllegalStateException|AccessDeniedException ex){
            return save(row,"PREPARED".equals(row.status())?"BLOCKED":"UNKNOWN",null,null,row.buildResult(),row.receiptJson(),
                    "测试授权、候选、绑定或实际回执不符合固定内容；未重复触发");
        }
    }
    private AgentBuildTestEntity observe(AgentBuildTestEntity row,AgentJenkinsPolicy.BindingSnapshot binding,
            AgentJenkinsClient.Target target,Map<String,String> parameters) {
        var build=client.build(target,row.buildNumber(),parameters);
        if(!build.parametersMatch() || row.queueId()!=null&&build.queueId()!=null&&!row.queueId().equals(build.queueId()))
            return save(row,"UNKNOWN",null,null,build.result(),null,"构建参数或队列关联不一致");
        if("RUNNING".equals(build.state()))return save(row,"BUILDING",null,null,null,null,null);
        if(!"FINISHED".equals(build.state()))return save(row,"UNKNOWN",null,null,build.result(),null,"原构建没有确定终态，未重发");
        if("ABORTED".equals(build.result()))return save(row,"ABORTED",null,null,build.result(),null,"Jenkins 确认构建已终止");
        if(!Set.of("SUCCESS","FAILURE","UNSTABLE","NOT_BUILT").contains(Objects.toString(build.result(),"")))
            return save(row,"UNKNOWN",null,null,build.result(),null,"构建结果无法识别");
        var receipt=client.receipt(target,row.buildNumber(),binding.binding().receiptPath());
        if(receipt==null)return save(row,"SUCCESS".equals(build.result())?"AWAITING_REPORT":"FAILED",null,null,build.result(),null,"尚未取得实际构建测试回执");
        AgentBuildTestReceipt.Verified verified;
        try{verified=AgentBuildTestReceipt.verify(receipt,row,binding,row.buildNumber(),parameters,clock.instant());}
        catch(IllegalArgumentException ex){return save(row,"FAILED",null,null,build.result(),null,"原构建已结束，但独立测试回执无效；不能用于审核");}
        var safe=(tools.jackson.databind.node.ObjectNode)json.valueToTree(verified);
        String summary=data.sanitize(verified.summary()).asText();safe.put("summary",summary.substring(0,Math.min(16000,summary.length())));
        boolean passed="SUCCESS".equals(build.result())&&verified.passed();
        return save(row,passed?"SUCCEEDED":"FAILED",null,null,build.result(),json.writeValueAsString(safe),passed?null:"实际构建或测试未通过，或没有执行有效测试");
    }
    private AgentBuildTestEntity save(AgentBuildTestEntity row,String status,Long queue,Long build,String result,String receipt,String error){return store.finish(row,status,queue,build,result,receipt,error);}
}
