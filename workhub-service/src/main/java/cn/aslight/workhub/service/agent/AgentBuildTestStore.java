package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 工具意图先落库；共享 Job/仓库资源与短租约，不在数据库事务内访问 Jenkins。 */
@Service
public class AgentBuildTestStore {
    public static final String START = "build.test.start";
    static final Set<String> ACTIVE = Set.of("PREPARED","SUBMITTING","QUEUED","BUILDING","AWAITING_REPORT","UNKNOWN");
    static final Set<String> TERMINAL = Set.of("SUCCEEDED","FAILED","ABORTED","REJECTED","BLOCKED");
    private final AgentBuildTestMapper tests;
    private final AgentDeploymentMapper resources;
    private final AgentMapper agents;
    private final AgentRunStore runs;
    private final AgentChangeService changes;
    private final AgentAccessService access;
    private final AgentBuildTestPolicy policy;
    private final AgentJenkinsPolicy jenkins;
    private final AgentToolData data;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired public AgentBuildTestStore(AgentBuildTestMapper tests,AgentDeploymentMapper resources,AgentMapper agents,
            AgentRunStore runs,AgentChangeService changes,AgentAccessService access,AgentBuildTestPolicy policy,AgentJenkinsPolicy jenkins,
            AgentToolData data,ObjectMapper json) {
        this(tests,resources,agents,runs,changes,access,policy,jenkins,data,json,Clock.systemUTC());
    }
    AgentBuildTestStore(AgentBuildTestMapper tests,AgentDeploymentMapper resources,AgentMapper agents,AgentRunStore runs,
            AgentChangeService changes,AgentAccessService access,AgentBuildTestPolicy policy,AgentJenkinsPolicy jenkins,AgentToolData data,ObjectMapper json,Clock clock) {
        this.tests=tests;this.resources=resources;this.agents=agents;this.runs=runs;this.changes=changes;this.access=access;
        this.policy=policy;this.jenkins=jenkins;this.data=data;this.json=json;this.clock=clock;
    }

    @Transactional
    public JsonNode start(AgentToolContext context,String changeId,String bindingId,String requestKey) {
        resources.lockDispatcher(); var run=agents.lockRun(context.runId()); runs.requireActive(context.runId(),context.ownerToken());
        access.requireExecution(context,START,Set.of("agent:code:deliver"));
        if (!run.taskId().equals(context.taskId()) || !run.agentId().equals(context.agentId())) throw new IllegalArgumentException("测试调用不属于当前运行");
        var handle=run.externalHandle()==null?json.nullNode():json.readTree(run.externalHandle());
        if ("CLI".equals(handle.path("kind").asText())) handle=handle.path("activeTool");
        String toolKey=handle.path("key").asText();
        if (!"TOOL".equals(handle.path("kind").asText()) || !START.equals(handle.path("tool").asText()) || toolKey.isBlank())
            throw new IllegalStateException("测试请求须关联当前实际工具调用");
        if (requestKey==null || !requestKey.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("测试请求标识非法");
        String requestHash=hash(List.of(changeId,bindingId,requestKey));
        var change=changes.detail(context,changeId);
        var binding=policy.required(change.manifest()).stream().filter(value->value.binding().id().equals(bindingId)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Job 不属于当前候选的必需独立测试"));
        access.requireScope(context,binding.binding().scope());
        var call=tests.call(context.runId(),toolKey);
        if (call!=null) {
            if (!START.equals(call.toolCode()) || !requestHash.equals(call.requestHash())) throw new IllegalArgumentException("测试调用键已用于其他内容");
            return json.readTree(call.resultJson());
        }
        var row=tests.byKey(context.runId(),requestKey);
        if (row!=null) {
            if (!requestHash.equals(row.requestHash()) || !binding.hash().equals(row.bindingHash())) throw new IllegalArgumentException("测试请求键已使用或绑定已改变");
        } else {
            requireCandidate(change,context.runId());
            for (String resource : AgentDeploymentStore.resources(binding)) if (resources.resourceOwner(resource)!=null)
                throw new IllegalStateException("同一 Job、系统或仓库已有在途操作，请先核对原请求");
            String id=UUID.randomUUID().toString();var parameters=jenkins.parameters(binding,id,change.manifestHash(),policy.commits(binding,change.manifest()));
            var previous=tests.latest(change.id(),bindingId);
            row=new AgentBuildTestEntity(id,context.runId(),change.id(),bindingId,previous==null?1:previous.attempt()+1,requestKey,requestHash,
                    change.manifestHash(),json.writeValueAsString(binding),binding.hash(),json.writeValueAsString(parameters),hash(parameters),
                    "PREPARED",null,null,null,null,null,null,null,now(),true,0,now(),now());
            tests.insert(row);for(String resource:AgentDeploymentStore.resources(binding))resources.acquire(resource,row.id(),now());
            journal(row);
        }
        String result=json.writeValueAsString(view(row));
        tests.insertCall(new AgentBuildTestCall(context.runId(),toolKey,START,requestHash,result,now()));
        return json.readTree(result);
    }

    public List<Map<String,Object>> bindings(AgentToolContext context,String changeId) {
        runs.requireActive(context.runId(),context.ownerToken());access.requireExecution(context,"build.test.bindings",Set.of("agent:code:use"));
        var change=changes.detail(context,changeId);
        return policy.required(change.manifest()).stream().map(binding->{
            access.requireScope(context,binding.binding().scope());var row=tests.latest(change.id(),binding.binding().id());
            var result=new LinkedHashMap<String,Object>();result.put("bindingId",binding.binding().id());result.put("name",binding.binding().name());
            result.put("scope",binding.binding().scope());result.put("repositories",binding.binding().commitParameters().keySet());
            result.put("bindingHash",binding.hash());result.put("required",true);result.put("latest",row==null?null:view(row));return (Map<String,Object>)result;
        }).toList();
    }
    public Map<String,Object> status(AgentToolContext context,String id) {
        runs.requireActive(context.runId(),context.ownerToken());access.requireExecution(context,"build.test.status",Set.of("agent:code:use"));
        var row=tests.find(id);if(row==null || !context.runId().equals(row.runId()))throw new IllegalArgumentException("测试请求不属于本次运行");
        changes.detail(context,row.changeSetId());access.requireScope(context,binding(row).binding().scope());return view(row);
    }

    @Transactional
    public AgentBuildTestEntity claim(String id) {
        resources.lockDispatcher();var row=tests.lock(id);var now=now();
        if(row==null || !ACTIVE.contains(row.status()) || row.nextCheckAt().isAfter(now)
                || row.ownerToken()!=null && row.leaseUntil().isAfter(now) || tests.countOwned(now)>=4)return null;
        changed(tests.claim(id,row.version(),UUID.randomUUID().toString(),now.plusSeconds(120),now));return tests.find(id);
    }
    public void validate(AgentBuildTestEntity row) {
        var run=runs.get(row.runId());var context=runs.context(run);
        if(run.cancelRequested() || Set.of("COMPLETED","FAILED","CANCELLED").contains(run.status()))throw new IllegalArgumentException("原运行已停止，未提交测试");
        access.requireExecution(context,START,Set.of("agent:code:deliver"));runs.requireProvider(run);
        var binding=binding(row);jenkins.requireCurrent(binding);access.requireScope(context,binding.binding().scope());
        var change=changes.detailForUser(row.changeSetId(),run.requestedBy());requireCandidate(change,row.runId());
        if(!row.manifestHash().equals(change.manifestHash()) || policy.required(change.manifest()).stream().noneMatch(binding::equals))
            throw new IllegalArgumentException("测试候选或必需 Job 绑定已变化");
        var parameters=jenkins.parameters(binding,row.id(),row.manifestHash(),policy.commits(binding,change.manifest()));
        if(!hash(parameters).equals(row.parametersHash()) || !json.readTree(json.writeValueAsString(parameters)).equals(json.readTree(row.parametersJson())))
            throw new IllegalArgumentException("测试参数与固定的准确提交不一致");
    }
    @Transactional
    public AgentBuildTestEntity intent(AgentBuildTestEntity expected) {
        resources.lockDispatcher();var row=owned(expected);agents.lockRun(row.runId());validate(row);owned(row);
        changed(tests.intent(row.id(),row.version(),row.ownerToken(),now()));var saved=tests.find(row.id());journal(saved);return saved;
    }
    @Transactional
    public AgentBuildTestEntity finish(AgentBuildTestEntity expected,String status,Long queue,Long build,String result,String receipt,String error) {
        resources.lockDispatcher();var row=owned(expected);
        if(!ACTIVE.contains(status)&&!TERMINAL.contains(status) || "SUBMITTING".equals(status))throw new IllegalArgumentException("测试回执状态非法");
        if(Set.of("PREPARED","BLOCKED").contains(status)&&!"PREPARED".equals(row.status()) || "REJECTED".equals(status)&&!"SUBMITTING".equals(row.status()))
            throw new IllegalArgumentException("不能把已发出的未知测试改成未提交");
        if(row.queueId()!=null&&queue!=null&&!row.queueId().equals(queue) || row.buildNumber()!=null&&build!=null&&!row.buildNumber().equals(build))
            throw new IllegalArgumentException("不能替换原 Jenkins 队列或构建句柄");
        queue=queue==null?row.queueId():queue;build=build==null?row.buildNumber():build;
        if(Set.of("SUCCEEDED","FAILED").contains(status)&&build==null)throw new IllegalArgumentException("测试终态须有实际构建编号");
        AgentBuildTestReceipt.Verified verified=null;
        if(receipt!=null&&build==null)throw new IllegalArgumentException("测试回执缺少固定构建编号");
        if(receipt!=null)verified=AgentBuildTestReceipt.verify(json.readTree(receipt),row,binding(row),build,parameters(row),clock.instant());
        if("SUCCEEDED".equals(status)&&(!"SUCCESS".equals(result)||verified==null||!verified.passed()))throw new IllegalArgumentException("缺少真实执行通过的测试回执");
        String safeError=error==null?null:data.sanitize(error).asText();if(safeError!=null&&safeError.length()>2000)safeError=safeError.substring(0,2000);
        changed(tests.finish(row.id(),row.version(),row.ownerToken(),status,queue,build,result,receipt,safeError,now().plusSeconds("UNKNOWN".equals(status)?60:15),now()));
        var saved=tests.find(row.id());journal(saved);
        if(TERMINAL.contains(status)) {
            resources.release(row.id());
            if(verified!=null) {
                String payload=json.writeValueAsString(new AgentBuildTestEvidence.Proof(1,row.id(),row.changeSetId(),row.manifestHash(),row.bindingHash(),verified));
                agents.insertArtifact(new AgentArtifactEntity(UUID.randomUUID().toString(),row.runId(),"test:"+row.id(),"TEST_REPORT","PLATFORM_EXECUTION",payload,AgentCodePolicy.hash(payload),now()));
            }
        }
        return saved;
    }
    @Transactional
    public void wake(String id) {
        resources.lockDispatcher();var row=tests.lock(id);if(row==null||!row.wakePending()||!TERMINAL.contains(row.status()))return;
        var run=agents.lockRun(row.runId());
        if(run.cancelRequested()||Set.of("COMPLETED","FAILED","CANCELLED").contains(run.status())){tests.acknowledgeWake(id);return;}
        if(!"WAITING_INPUT".equals(run.status())||run.ownerToken()!=null||run.externalHandle()!=null)return;
        String blocked=null;
        try {
            var context=runs.context(run);access.requireExecution(context,START,Set.of("agent:code:deliver"));access.requireScope(context,binding(row).binding().scope());runs.requireProvider(run);
            if(run.stepCount()>=context.configuration().maxSteps()||run.elapsedMillis()>=context.configuration().maxRunSeconds()*1000L)blocked="原运行预算已耗尽，需人工处理";
        }catch(IllegalArgumentException|IllegalStateException|AccessDeniedException ex){blocked="当前授权或 AI 接入不可继续，请人工核对";}
        if(blocked==null)changed(agents.acceptMessage(run.id(),run.version(),now()));
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(),run.id(),"build-test-wake:"+id,agents.nextEventOrdinal(run.id()),
                blocked==null?"BUILD_TEST_WAKE":"BUILD_TEST_WAKE_BLOCKED",json.writeValueAsString(Map.of("requestId",id,"status",row.status(),"message",blocked==null?"独立测试已有结果，请核对实际报告并继续；预算不重置":blocked)),"build-test-runtime",now()));
        tests.acknowledgeWake(id);
    }
    private static void requireCandidate(AgentChangeView change,String runId) {
        if(!change.runId().equals(runId)||!change.current()||change.blockers().contains("REMOTE_DELIVERY_EVIDENCE_MISSING"))
            throw new IllegalArgumentException("须先取得当前候选全部仓库的准确远端交付凭据");
    }
    AgentJenkinsPolicy.BindingSnapshot binding(AgentBuildTestEntity row) {
        var binding=json.readValue(row.bindingJson(),AgentJenkinsPolicy.BindingSnapshot.class);
        if(!binding.hash().equals(row.bindingHash()) || !row.bindingId().equals(binding.binding().id())
                || !"BUILD_TEST".equals(binding.binding().stage()) || !"TEST".equals(binding.binding().scope().environmentCode()))throw new IllegalArgumentException("独立测试绑定身份不一致");
        return binding;
    }
    @SuppressWarnings("unchecked") Map<String,String> parameters(AgentBuildTestEntity row) {
        Map<String,String> parameters=new TreeMap<>(json.readValue(row.parametersJson(),Map.class));AgentJenkinsClient.validateParameters(parameters);
        if(!hash(parameters).equals(row.parametersHash()))throw new IllegalArgumentException("独立测试参数摘要不一致");return parameters;
    }
    Map<String,Object> view(AgentBuildTestEntity row) {
        var value=new LinkedHashMap<String,Object>();value.put("id",row.id());value.put("runId",row.runId());value.put("changeSetId",row.changeSetId());
        value.put("bindingId",row.bindingId());value.put("attempt",row.attempt());value.put("manifestHash",row.manifestHash());value.put("status",row.status());
        value.put("queueId",row.queueId());value.put("buildNumber",row.buildNumber());value.put("buildResult",row.buildResult());value.put("errorMessage",row.errorMessage());
        value.put("report",row.receiptJson()==null?null:json.readTree(row.receiptJson()));value.put("version",row.version());value.put("updatedAt",row.updatedAt());
        value.put("instruction",ACTIVE.contains(row.status())?"平台继续跟踪原请求；等待时返回 NEEDS_INPUT，完成后唤醒原运行；UNKNOWN 不得改键重发":"核对真实测试结果后继续任务");return value;
    }
    private AgentBuildTestEntity owned(AgentBuildTestEntity expected) {
        var row=tests.lock(expected.id());if(row==null||row.version()!=expected.version()||expected.ownerToken()==null||!expected.ownerToken().equals(row.ownerToken())
                ||row.leaseUntil()==null||!row.leaseUntil().isAfter(now()))throw new LeaseLost();return row;
    }
    private void journal(AgentBuildTestEntity row) {
        agents.lockRun(row.runId());agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(),row.runId(),"build-test:"+row.id()+":"+row.version(),
                agents.nextEventOrdinal(row.runId()),"BUILD_TEST",json.writeValueAsString(view(row)),"build-test-runtime",now()));
    }
    private String hash(Object value){return AgentCodePolicy.hash(json.writeValueAsString(value));}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private static void changed(int count){if(count!=1)throw new IllegalStateException("独立测试记录已变化");}
    public static final class LeaseLost extends IllegalStateException { LeaseLost(){super("独立测试执行租约或版本已失效");} }
}
