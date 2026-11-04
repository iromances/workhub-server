package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** AI 可以准备审核材料；唯一的批准路径由控制器绑定当前登录用户。 */
@Service
public class AgentReleasePlanService {
    private final AgentReleasePlanMapper plans;
    private final AgentDeploymentMapper deployments;
    private final AgentMapper agents;
    private final AgentChangeService changes;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final AgentJenkinsPolicy policies;
    private final AgentDeploymentApproval approval;
    private final AgentDeploymentStore outbox;
    private final AgentReleasePlanGate gate;
    private final ObjectMapper json;
    private final Validator validator;
    private final Clock clock;
    @Autowired public AgentReleasePlanService(AgentReleasePlanMapper plans, AgentDeploymentMapper deployments, AgentMapper agents,
            AgentChangeService changes, AgentRunStore runs, AgentAccessService access, AgentJenkinsPolicy policies,
            AgentDeploymentApproval approval, AgentDeploymentStore outbox, AgentReleasePlanGate gate, ObjectMapper json, Validator validator) {
        this(plans, deployments, agents, changes, runs, access, policies, approval, outbox, gate, json, validator, Clock.systemUTC());
    }
    AgentReleasePlanService(AgentReleasePlanMapper plans, AgentDeploymentMapper deployments, AgentMapper agents,
            AgentChangeService changes, AgentRunStore runs, AgentAccessService access, AgentJenkinsPolicy policies,
            AgentDeploymentApproval approval, AgentDeploymentStore outbox, AgentReleasePlanGate gate, ObjectMapper json, Validator validator, Clock clock) {
        this.plans = plans; this.deployments = deployments; this.agents = agents; this.changes = changes; this.runs = runs;
        this.access = access; this.policies = policies; this.approval = approval; this.outbox = outbox; this.gate = gate;
        this.json = json; this.validator = validator; this.clock = clock;
    }

    @Transactional
    public Map<String, Object> prepare(AgentReleasePlanRequest request, String user) {
        valid(request); deployments.lockDispatcher();
        access.actor(user).require("agent:code:deliver");
        var change = changes.detailForUser(request.changeSetId(), user); agents.lockRun(change.runId());
        var specification = runs.context(runs.get(change.runId())).specification();
        var requiredRepositories = AgentReleaseTargets.repositories(specification, change.manifest(), request.stage());
        if (new HashSet<>(request.bindingIds()).size() != request.bindingIds().size()) throw new IllegalArgumentException("发布 Job 绑定重复");
        var definitionValue = new TreeMap<String, Object>(Map.of("changeSetId", request.changeSetId(), "stage", request.stage(),
                "bindings", request.bindingIds().stream().sorted().toList(), "dependencies", request.dependencies().stream()
                        .sorted(Comparator.comparing(AgentReleasePlanRequest.Dependency::bindingId).thenComparing(AgentReleasePlanRequest.Dependency::dependsOn)).distinct().toList()));
        if (request.verificationPlan() != null) definitionValue.put("verificationPlan", request.verificationPlan());
        String definition = AgentCodePolicy.hash(json.writeValueAsString(definitionValue));
        var previous = plans.byKey(change.runId(), request.requestKey());
        if (previous != null) {
            if (!previous.definitionHash().equals(definition) || !previous.createdBy().equals(user)) throw new IllegalArgumentException("发布计划请求标识已使用，内容或创建人不一致");
            return detail(previous.id(), user);
        }
        if (plans.list(change.id()).size() >= 100) throw new IllegalArgumentException("单个候选版本的发布计划已达上限");
        var steps = new ArrayList<AgentReleasePlanSnapshot.Step>();
        var blockers = new TreeSet<String>(); List<AgentArtifactEntity> evidence = null;
        for (String bindingId : request.bindingIds().stream().sorted().toList()) {
            String requestId = id(); var material = approval.material(change.id(), bindingId, requestId, user);
            if (!request.stage().equals(material.binding().binding().stage())) throw new IllegalArgumentException("整组发布必须属于同一阶段");
            if (!change.equals(material.change()) || evidence != null && !evidence.equals(material.evidence())) throw new IllegalStateException("准备期间候选材料已变化，请重新准备");
            evidence = material.evidence(); blockers.addAll(material.blockers());
            steps.add(new AgentReleasePlanSnapshot.Step(requestId, id(), material.binding(), material.parameters(), List.of()));
        }
        AgentReleaseTargets.requireCoverage(specification, change.manifest(), request.stage(), steps);
        if (request.verificationPlan() == null) blockers.add("VERIFICATION_PLAN_MISSING");
        else validateVerification(request.verificationPlan(), steps);
        var snapshot = new AgentReleasePlanSnapshot(2, change, evidence, List.copyOf(blockers), AgentReleasePlanGraph.order(change, steps, request.dependencies(), requiredRepositories), request.verificationPlan());
        String payload = json.writeValueAsString(snapshot);
        if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4 * 1024 * 1024) throw new IllegalArgumentException("整组审核材料超过4 MiB，请减少本次变更规模");
        var plan = new AgentReleasePlanEntity(id(), change.runId(), change.id(), request.requestKey(), definition, change.manifestHash(), request.stage(), payload,
                AgentCodePolicy.hash(payload), "WAITING_REVIEW", user, 0, now(), now());
        plans.insert(plan); int order = 0;
        for (var step : snapshot.steps()) {
            plans.step(step.requestId(), step.approvalId(), plan.id(), order++);
            for (String dependency : step.dependsOn()) plans.dependency(step.requestId(), snapshot.steps().stream().filter(value -> value.bindingId().equals(dependency)).findFirst().orElseThrow().requestId());
        }
        event(plan, "prepared", "RELEASE_PLAN", Map.of("planId", plan.id(), "status", plan.status(), "snapshotHash", plan.snapshotHash(), "blockers", snapshot.blockers()), user);
        return detail(plan.id(), user);
    }

    @Transactional
    public Map<String, Object> review(String id, AgentReleaseReviewRequest request, String user) {
        valid(request); access.actor(user).require("autonomy:release:review"); deployments.lockDispatcher();
        var plan = visible(id, user); agents.lockRun(plan.runId());
        var previous = plans.review(id, request.requestKey());
        if (previous != null) {
            if (!previous.reviewedBy().equals(user) || !previous.decision().equals(request.decision()) || !previous.comment().equals(request.comment())
                    || !previous.snapshotHash().equals(request.snapshotHash()) || previous.expectedVersion() != request.expectedVersion()) throw new IllegalArgumentException("审核请求标识已使用，材料或意见不一致");
            return detail(id, user);
        }
        if (!"WAITING_REVIEW".equals(plan.status()) || plan.version() != request.expectedVersion() || !plan.snapshotHash().equals(request.snapshotHash())) throw new IllegalStateException("审核材料或状态已变化，请重新打开当前计划");
        var snapshot = gate.snapshot(plan);
        if ("APPROVED".equals(request.decision())) {
            AgentReleaseTargets.requireCoverage(runs.context(runs.get(plan.runId())).specification(), snapshot.change().manifest(), plan.stage(), snapshot.steps());
            validateVerification(snapshot.verificationPlan(), snapshot.steps());
            for (var step : snapshot.steps()) {
                var current = approval.material(plan.changeSetId(), step.bindingId(), step.requestId(), user); approval.requireReady(current);
                if (!json.readTree(json.writeValueAsString(snapshot.material(step))).equals(json.readTree(json.writeValueAsString(current.snapshot())))) throw new IllegalStateException("审核材料已过期，请基于当前证据准备新计划");
            }
        }
        changed(plans.status(id, plan.version(), request.decision(), now()));
        plans.insertReview(new AgentReleaseReviewEntity(id(), id, request.requestKey(), request.decision(), user, request.comment(), plan.snapshotHash(), plan.version(), now()));
        if ("APPROVED".equals(request.decision())) for (var step : snapshot.steps()) {
            String parameters = json.writeValueAsString(step.parameters());
            deployments.insertApproval(new AgentReleaseApprovalEntity(step.approvalId(), step.requestId(), plan.runId(), plan.changeSetId(), plan.manifestHash(),
                    json.writeValueAsString(step.binding()), step.binding().hash(), parameters, AgentCodePolicy.hash(parameters), "APPROVED", user, request.comment(),
                    json.writeValueAsString(snapshot.material(step)), now()));
            outbox.enqueue(step.approvalId());
        }
        event(plan, "review:" + request.requestKey(), "HUMAN_INPUT", Map.of("message", "发布计划 " + id + " 人工审核 " + request.decision() + "：" + request.comment(),
                "planId", id, "decision", request.decision(), "snapshotHash", plan.snapshotHash()), user);
        var run = agents.findRun(plan.runId());
        if ("WAITING_INPUT".equals(run.status()) && run.externalHandle() == null && !run.cancelRequested()) changed(agents.acceptMessage(run.id(), run.version(), now()));
        return detail(id, user);
    }

    public List<Map<String, Object>> list(String changeId, String user) {
        changes.detailForUser(changeId, user);
        return plans.list(changeId).stream().map(plan -> summary(visible(plan.id(), user))).toList();
    }
    public Map<String, Object> detail(String id, String user) {
        var plan = visible(id, user); var snapshot = gate.snapshot(plan); var value = new LinkedHashMap<>(summary(plan));
        var blockers = new LinkedHashSet<>(snapshot.blockers());
        if (snapshot.verificationPlan() == null) blockers.add("VERIFICATION_PLAN_MISSING");
        boolean current = true;
        for (var step : snapshot.steps()) {
            try {
                var material = approval.material(plan.changeSetId(), step.bindingId(), step.requestId(), user);
                blockers.addAll(material.blockers());
                if (!json.readTree(json.writeValueAsString(snapshot.material(step))).equals(json.readTree(json.writeValueAsString(material.snapshot())))) current = false;
            } catch (AccessDeniedException ex) { current = false; blockers.add("RELEASE_AUTHORIZATION_UNAVAILABLE"); }
            catch (IllegalArgumentException | IllegalStateException ex) { current = false; blockers.add("RELEASE_MATERIAL_UNAVAILABLE"); }
        }
        if (!current) blockers.add("RELEASE_MATERIAL_CHANGED");
        value.put("snapshot", snapshot); value.put("current", current); value.put("blockers", List.copyOf(blockers));
        value.put("readyForReview", current && blockers.isEmpty() && "WAITING_REVIEW".equals(plan.status()));
        value.put("reviews", plans.reviews(id));
        value.put("deployments", snapshot.steps().stream().map(step -> deployments.find(step.requestId())).filter(Objects::nonNull).map(AgentDeploymentDispatcher::view).toList());
        return value;
    }
    public List<Map<String, Object>> bindings(String changeId, String user) {
        var change = changes.detailForUser(changeId, user); var context = runs.context(runs.get(change.runId()));
        AgentReleaseTargets.requireSpecification(context.specification());
        return policies.current().bindings().stream().filter(AgentJenkinsPolicy.JobBinding::enabled).filter(binding -> AgentReleaseTargets.allows(context.specification(), binding)).filter(binding -> {
            try { access.requireScope(context, binding.scope()); policies.binding(binding.id()); return true; }
            catch (AccessDeniedException | IllegalArgumentException ex) { return false; }
        }).map(binding -> Map.<String, Object>of("id", binding.id(), "name", binding.name(), "stage", binding.stage(), "scope", binding.scope(),
                "systemName", binding.systemName(), "repositories", binding.commitParameters().keySet())).toList();
    }
    private AgentReleasePlanEntity visible(String id, String user) {
        var plan = plans.find(id); if (plan == null) throw new IllegalArgumentException("发布计划不存在");
        changes.detailForUser(plan.changeSetId(), user);
        var context = runs.context(runs.get(plan.runId()));
        var agent = agents.findAgent(context.agentId());
        var config = json.readValue(agent.configurationJson(), AgentConfiguration.class);
        for (var step : gate.snapshot(plan).steps()) if (!config.scopes().contains(step.binding().binding().scope())
                || !context.configuration().scopes().contains(step.binding().binding().scope()) || !context.specification().scopes().contains(step.binding().binding().scope())) {
            throw new AccessDeniedException("发布目标超出当前 Agent 或原任务的授权范围");
        }
        return plan;
    }
    private Map<String, Object> summary(AgentReleasePlanEntity plan) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", plan.id()); value.put("runId", plan.runId()); value.put("changeSetId", plan.changeSetId()); value.put("manifestHash", plan.manifestHash());
        value.put("snapshotHash", plan.snapshotHash()); value.put("stage", plan.stage()); value.put("status", plan.status()); value.put("version", plan.version());
        value.put("createdBy", plan.createdBy()); value.put("createdAt", plan.createdAt()); value.put("updatedAt", plan.updatedAt()); value.put("businessVerified", false);
        return value;
    }
    private void event(AgentReleasePlanEntity plan, String suffix, String kind, Object payload, String actor) {
        agents.insertEvent(new AgentEventEntity(id(), plan.runId(), "release:" + plan.id() + ":" + suffix, agents.nextEventOrdinal(plan.runId()), kind,
                json.writeValueAsString(payload), actor, now()));
    }
    private void validateVerification(AgentReleaseVerificationPlan plan, List<AgentReleasePlanSnapshot.Step> steps) {
        if (plan == null) throw new IllegalArgumentException("缺少业务复查标准，请由 Agent 补充后重新准备发布计划");
        valid(plan);
        if (json.writeValueAsString(plan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 24 * 1024)
            throw new IllegalArgumentException("业务复查标准超过24 KiB，请精简描述后重新准备");
        var ids = new HashSet<String>(); var scopes = new HashSet<AgentScope>();
        for (var check : plan.checks()) {
            if (!ids.add(check.id().trim())) throw new IllegalArgumentException("业务复查项标识重复");
            if (!AgentReleaseVerificationPlan.TOOL_CODES.contains(check.toolCode())) throw new IllegalArgumentException("复查必须使用受支持的 WorkHub 监测工具");
            if (check.sampleIntervalSeconds() > check.observationSeconds()
                    || check.minimumSamples() > check.observationSeconds() / check.sampleIntervalSeconds() + 1)
                throw new IllegalArgumentException("复查窗口、采样间隔与最少样本数量不一致");
            scopes.add(check.scope());
        }
        var targets = new HashSet<>(steps.stream().map(step -> step.binding().binding().scope()).toList());
        if (!scopes.equals(targets)) throw new IllegalArgumentException("业务复查必须覆盖本组全部目标范围，且不能添加其他环境");
    }
    private void valid(Object value) { if (value == null || !validator.validate(value).isEmpty()) throw new IllegalArgumentException("发布请求字段缺失或不符合约束"); }
    private static void changed(int count) { if (count != 1) throw new IllegalStateException("发布计划已变化"); }
    private static String id() { return UUID.randomUUID().toString(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
