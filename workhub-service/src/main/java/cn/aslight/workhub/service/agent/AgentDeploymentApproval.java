package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** 发布出站前重新核对人工授权、实测门禁及固定材料；不提供 AI 批准能力。 */
@Service
public class AgentDeploymentApproval {
    private final AgentDeploymentMapper deployments;
    private final AgentMapper agents;
    private final AgentChangeService changes;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final AgentJenkinsPolicy policies;
    private final ObjectMapper json;
    public AgentDeploymentApproval(AgentDeploymentMapper deployments, AgentMapper agents, AgentChangeService changes,
            AgentRunStore runs, AgentAccessService access, AgentJenkinsPolicy policies, ObjectMapper json) {
        this.deployments = deployments; this.agents = agents; this.changes = changes; this.runs = runs;
        this.access = access; this.policies = policies; this.json = json;
    }

    public AgentJenkinsPolicy.BindingSnapshot validate(AgentReleaseApprovalEntity approval) {
        if (approval == null || !"APPROVED".equals(approval.decision()) || approval.reviewedAt() == null) throw new IllegalArgumentException("缺少有效人工发布审核");
        access.actor(approval.reviewedBy()).require("autonomy:release:review");
        var binding = json.readValue(approval.bindingJson(), AgentJenkinsPolicy.BindingSnapshot.class);
        policies.requireCurrent(binding);
        if (!binding.hash().equals(approval.bindingHash())) throw new IllegalArgumentException("审核绑定摘要不一致");
        var material = material(approval.changeSetId(), binding.binding().id(), approval.requestId(), approval.reviewedBy());
        requireReady(material);
        if (!material.change().runId().equals(approval.runId()) || !material.change().manifestHash().equals(approval.manifestHash())) {
            throw new IllegalArgumentException("审核与候选清单不一致");
        }
        if (!json.valueToTree(material.parameters()).equals(json.readTree(approval.parametersJson()))
                || !AgentCodePolicy.hash(json.writeValueAsString(material.parameters())).equals(approval.parametersHash())) throw new IllegalArgumentException("发布参数与人工审核的准确提交不一致");
        // 同经 JSON 落盘再比较，避免 long/int 在 valueToTree 与文本读取时形成不同节点类型。
        if (!json.readTree(json.writeValueAsString(material.snapshot())).equals(json.readTree(approval.snapshotJson()))) throw new IllegalArgumentException("审核材料已变化，请重新审核当前版本");
        return binding;
    }

    /** 准备与批准复用同一份材料构造；准备可保留缺证据的草案，批准必须逐项就绪。 */
    public Material material(String changeId, String bindingId, String requestId, String user) {
        var change = changes.detailForUser(changeId, user);
        var run = runs.get(change.runId());
        if (run.cancelRequested() || "CANCELLED".equals(run.status())) throw new IllegalArgumentException("原任务已请求停止，不能开始发布");
        var context = runs.context(run); access.requireRunAuthorization(context);
        var binding = policies.binding(bindingId); access.requireScope(context, binding.binding().scope());
        var commits = new TreeMap<String, String>();
        for (var repository : change.manifest().repositories()) if (binding.binding().commitParameters().containsKey(repository.repositoryId())) {
            if (!repository.scope().businessLineCode().equals(binding.binding().scope().businessLineCode())) throw new IllegalArgumentException("发布仓库不属于目标业务线");
            commits.put(repository.repositoryId(), repository.resultSha());
        }
        var parameters = policies.parameters(binding, requestId, change.manifestHash(), commits);
        var evidence = agents.listArtifacts(run.id()).stream().filter(value -> Set.of("TEST_REPORT", "VERIFICATION").contains(value.kind())
                && Set.of("PLATFORM_EXECUTION", "PLATFORM_VERIFICATION").contains(value.provenance()))
                .sorted(Comparator.comparing(AgentArtifactEntity::id)).toList();
        for (var artifact : evidence) if (!AgentCodePolicy.hash(artifact.payloadJson()).equals(artifact.contentHash())) throw new IllegalArgumentException("平台验证证据摘要不一致");
        var blockers = new LinkedHashSet<>(change.blockers());
        if (!change.current()) blockers.add("MANIFEST_NOT_CURRENT");
        if (!change.readyForReview()) blockers.add("MANIFEST_NOT_READY");
        if (evidence.stream().noneMatch(value -> "TEST_REPORT".equals(value.kind()))) blockers.add("PLATFORM_TEST_EVIDENCE_MISSING");
        if ("PROD_DEPLOY".equals(binding.binding().stage()) && !hasTestEnvironmentVerification(change, evidence)) blockers.add("TEST_ENVIRONMENT_VERIFICATION_MISSING");
        return new Material(change, evidence, binding, parameters, List.copyOf(blockers));
    }
    public void requireReady(Material material) {
        if (!material.blockers().isEmpty()) throw new IllegalArgumentException("缺少实际验证或候选版本已变化，不能批准发布：" + String.join(", ", material.blockers()));
    }
    public record Material(AgentChangeView change, List<AgentArtifactEntity> evidence, AgentJenkinsPolicy.BindingSnapshot binding,
                           Map<String, String> parameters, List<String> blockers) {
        public Map<String, Object> snapshot() {
            return Map.of("schemaVersion", 1, "change", change, "evidence", evidence, "binding", binding, "parameters", parameters);
        }
    }

    private boolean hasTestEnvironmentVerification(AgentChangeView change, List<AgentArtifactEntity> evidence) {
        for (var artifact : evidence) {
            if (!"VERIFICATION".equals(artifact.kind()) || !"PLATFORM_VERIFICATION".equals(artifact.provenance())) continue;
            var value = json.readTree(artifact.payloadJson());
            if (!"TEST_ENVIRONMENT".equals(value.path("phase").asText()) || !value.path("passed").isBoolean() || !value.path("passed").asBoolean()
                    || !change.id().equals(value.path("changeSetId").asText()) || !change.manifestHash().equals(value.path("manifestHash").asText())
                    || !value.path("deploymentIds").isArray() || value.path("deploymentIds").isEmpty()) continue;
            var covered = new HashSet<String>(); boolean valid = true;
            for (var id : value.path("deploymentIds")) {
                var deployment = id.isString() ? deployments.find(id.asText()) : null;
                if (deployment == null || !"SUCCEEDED".equals(deployment.status()) || !change.id().equals(deployment.changeSetId())
                        || !change.manifestHash().equals(deployment.manifestHash())) { valid = false; break; }
                var target = json.readValue(deployment.bindingJson(), AgentJenkinsPolicy.BindingSnapshot.class);
                if (!"TEST_DEPLOY".equals(target.binding().stage())) { valid = false; break; }
                covered.addAll(target.binding().commitParameters().keySet());
            }
            if (valid && covered.equals(new HashSet<>(change.manifest().repositories().stream().map(AgentRepositoryChange::repositoryId).toList()))) return true;
        }
        return false;
    }
}
