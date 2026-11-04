package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

/** 在 RELEASE 行锁持有期间核对整组状态，包含直接调用执行器的路径。 */
@Service
public class AgentReleasePlanGate {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "ABORTED", "REJECTED", "BLOCKED");
    private static final Set<String> OPEN = Set.of("APPROVED", "EXECUTING", "NEEDS_ATTENTION", "STOPPING");
    private final AgentReleasePlanMapper plans;
    private final AgentDeploymentMapper deployments;
    private final AgentMapper agents;
    private final ObjectMapper json;
    public AgentReleasePlanGate(AgentReleasePlanMapper plans, AgentDeploymentMapper deployments, AgentMapper agents, ObjectMapper json) {
        this.plans = plans; this.deployments = deployments; this.agents = agents; this.json = json;
    }
    public AgentReleasePlanSnapshot snapshot(AgentReleasePlanEntity plan) {
        if (!AgentCodePolicy.hash(plan.snapshotJson()).equals(plan.snapshotHash())) throw new IllegalArgumentException("整组发布材料摘要不一致");
        var snapshot = json.readValue(plan.snapshotJson(), AgentReleasePlanSnapshot.class);
        if (snapshot.schemaVersion() != 1 || !snapshot.change().id().equals(plan.changeSetId()) || !snapshot.change().runId().equals(plan.runId())
                || !snapshot.change().manifestHash().equals(plan.manifestHash())) throw new IllegalArgumentException("整组发布材料身份不一致");
        return snapshot;
    }
    public String resourceOwner(AgentReleaseApprovalEntity approval) {
        var plan = plans.forRequest(approval.requestId());
        if (plan == null) return approval.requestId();
        if (!"APPROVED".equals(plan.status())) throw new IllegalArgumentException("整组发布尚未获人工批准");
        var snapshot = snapshot(plan); var step = step(snapshot, approval.requestId());
        if (!step.approvalId().equals(approval.id()) || !plan.runId().equals(approval.runId())
                || !json.readTree(json.writeValueAsString(snapshot.material(step))).equals(json.readTree(approval.snapshotJson()))) throw new IllegalArgumentException("单项审核与整组批准材料不一致");
        return plan.id();
    }
    public boolean mayStart(AgentDeploymentEntity request, LocalDateTime now) {
        var plan = plans.forRequest(request.id());
        if (plan == null) return true;
        reconcile(plan, now); plan = plans.find(plan.id());
        if (!Set.of("APPROVED", "EXECUTING", "NEEDS_ATTENTION").contains(plan.status())) return false;
        if (!"PREPARED".equals(deployments.find(request.id()).status())) return false;
        var snapshot = snapshot(plan); var step = step(snapshot, request.id());
        if (!step.approvalId().equals(request.approvalId())) throw new IllegalArgumentException("发布请求与整组步骤不一致");
        for (String id : step.dependsOn()) {
            var dependency = snapshot.steps().stream().filter(value -> value.bindingId().equals(id)).findFirst().orElseThrow();
            var row = deployments.find(dependency.requestId());
            if (row == null || !"SUCCEEDED".equals(row.status())) return false;
        }
        return true;
    }
    public void finished(AgentDeploymentEntity request, LocalDateTime now) {
        var plan = plans.forRequest(request.id());
        if (plan == null) { if (TERMINAL.contains(request.status())) deployments.release(request.id()); return; }
        reconcile(plan, now);
    }
    private void reconcile(AgentReleasePlanEntity plan, LocalDateTime now) {
        if (!OPEN.contains(plan.status())) return;
        var snapshot = snapshot(plan);
        var rows = snapshot.steps().stream().map(step -> deployments.find(step.requestId())).toList();
        if (rows.stream().anyMatch(Objects::isNull)) throw new IllegalStateException("整组出站登记不完整");
        var run = agents.lockRun(plan.runId());
        boolean cancelled = run.cancelRequested() || "CANCELLED".equals(run.status());
        boolean failed = rows.stream().anyMatch(row -> TERMINAL.contains(row.status()) && !"SUCCEEDED".equals(row.status()));
        if (cancelled || failed) {
            for (var row : rows) if (plans.blockUnsent(row.id(), cancelled ? "原任务已停止，未发出此 Job" : "整组已有失败步骤，未发出此 Job", now) == 1) {
                journal(plan.runId(), "release-blocked:" + row.id(), "DEPLOYMENT", AgentDeploymentDispatcher.view(deployments.find(row.id())), now);
            }
            rows = snapshot.steps().stream().map(step -> deployments.find(step.requestId())).toList();
        }
        String status;
        if (rows.stream().allMatch(row -> TERMINAL.contains(row.status()))) {
            status = rows.stream().allMatch(row -> "SUCCEEDED".equals(row.status())) ? "SUCCEEDED" : cancelled ? "CANCELLED" : "FAILED";
            deployments.release(plan.id());
        } else if (rows.stream().anyMatch(row -> "UNKNOWN".equals(row.status()))) status = "NEEDS_ATTENTION";
        else if (cancelled || failed) status = "STOPPING";
        else status = rows.stream().allMatch(row -> "PREPARED".equals(row.status())) ? "APPROVED" : "EXECUTING";
        if (!status.equals(plan.status())) {
            if (plans.status(plan.id(), plan.version(), status, now) != 1) throw new IllegalStateException("整组发布状态已变化");
            journal(plan.runId(), "release-state:" + plan.id() + ":" + (plan.version() + 1), "RELEASE_PLAN",
                    Map.of("planId", plan.id(), "status", status, "businessVerified", false), now);
            if (Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(status)) {
                String payload = json.writeValueAsString(Map.of("schemaVersion", 1, "planId", plan.id(), "changeSetId", plan.changeSetId(),
                        "manifestHash", plan.manifestHash(), "stage", plan.stage(), "status", status, "businessVerified", false,
                        "deployments", rows.stream().map(AgentDeploymentDispatcher::view).toList()));
                agents.insertArtifact(new AgentArtifactEntity(UUID.randomUUID().toString(), plan.runId(), "release:" + plan.id(), "DEPLOYMENT", "PLATFORM_EXECUTION",
                        payload, AgentCodePolicy.hash(payload), now));
                if ("WAITING_INPUT".equals(run.status()) && run.externalHandle() == null && !run.cancelRequested()) agents.acceptMessage(run.id(), run.version(), now);
            }
        }
    }
    private AgentReleasePlanSnapshot.Step step(AgentReleasePlanSnapshot snapshot, String id) {
        return snapshot.steps().stream().filter(value -> value.requestId().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("出站请求不属于审核步骤"));
    }
    private void journal(String run, String key, String kind, Object payload, LocalDateTime now) {
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), run, key, agents.nextEventOrdinal(run), kind,
                json.writeValueAsString(payload), "release-runtime", now));
    }
}
