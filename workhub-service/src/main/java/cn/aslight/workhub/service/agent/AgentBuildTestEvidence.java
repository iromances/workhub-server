package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 候选审核和任务验收使用同一持久证据，模型提供的 TEST_REPORT 不参与核验。 */
@Service
public class AgentBuildTestEvidence {
    private final AgentBuildTestMapper tests;
    private final AgentMapper agents;
    private final AgentBuildTestPolicy policy;
    private final AgentJenkinsPolicy jenkins;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired public AgentBuildTestEvidence(AgentBuildTestMapper tests,AgentMapper agents,AgentBuildTestPolicy policy,AgentJenkinsPolicy jenkins,ObjectMapper json) {
        this(tests,agents,policy,jenkins,json,Clock.systemUTC());
    }
    AgentBuildTestEvidence(AgentBuildTestMapper tests,AgentMapper agents,AgentBuildTestPolicy policy,AgentJenkinsPolicy jenkins,ObjectMapper json,Clock clock) {
        this.tests=tests; this.agents=agents; this.policy=policy; this.jenkins=jenkins; this.json=json; this.clock=clock;
    }
    public Evaluation check(AgentChangeSetEntity change,AgentChangeManifest manifest) {
        List<AgentJenkinsPolicy.BindingSnapshot> required;
        try { required=policy.required(manifest); }
        catch (AgentCodePolicy.Unavailable ex) { return new Evaluation(false,List.of(),List.of(ex.getMessage())); }
        var artifacts=new ArrayList<AgentArtifactEntity>();var missing=new ArrayList<String>();
        for (var binding : required) {
            var row=tests.latest(change.id(),binding.binding().id());
            if (row==null || !"SUCCEEDED".equals(row.status()) || !change.runId().equals(row.runId()) || !change.manifestHash().equals(row.manifestHash())
                    || !binding.hash().equals(row.bindingHash()) || !"SUCCESS".equals(row.buildResult()) || row.buildNumber()==null || row.receiptJson()==null) {
                missing.add(binding.binding().id()+":缺少当前准确提交的通过记录");continue;
            }
            if (!binding.equals(json.readValue(row.bindingJson(),AgentJenkinsPolicy.BindingSnapshot.class))) throw new IllegalStateException("测试绑定快照不一致");
            var parameters=jenkins.parameters(binding,row.id(),change.manifestHash(),policy.commits(binding,manifest));
            if (!AgentCodePolicy.hash(json.writeValueAsString(parameters)).equals(row.parametersHash())) throw new IllegalStateException("测试请求参数摘要不一致");
            var receipt=AgentBuildTestReceipt.verify(json.readTree(row.receiptJson()),row,binding,row.buildNumber(),parameters,clock.instant());
            var artifact=agents.findArtifact(row.runId(),"test:"+row.id());
            String payload=json.writeValueAsString(new Proof(1,row.id(),row.changeSetId(),row.manifestHash(),row.bindingHash(),receipt));
            if (!receipt.passed() || artifact==null || !"TEST_REPORT".equals(artifact.kind()) || !"PLATFORM_EXECUTION".equals(artifact.provenance())
                    || !payload.equals(artifact.payloadJson()) || !AgentCodePolicy.hash(payload).equals(artifact.contentHash())) {
                missing.add(binding.binding().id()+":缺少对应实际测试产物");continue;
            }
            artifacts.add(artifact);
        }
        return new Evaluation(missing.isEmpty()&&!required.isEmpty(),List.copyOf(artifacts),List.copyOf(missing));
    }
    public record Evaluation(boolean satisfied,List<AgentArtifactEntity> artifacts,List<String> missing) { }
    record Proof(int schemaVersion,String requestId,String changeSetId,String manifestHash,String bindingHash,AgentBuildTestReceipt.Verified receipt) { }
}
