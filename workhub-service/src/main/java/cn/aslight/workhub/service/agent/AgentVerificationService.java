package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.model.agent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** Agent 判断业务含义；平台约束版本、来源、完整采样窗口和可恢复的真实调用。 */
@Service
public class AgentVerificationService {
    public static final Set<String> MUTATIONS = Set.of("verification.start", "verification.record", "verification.finish", "verification.wait", "verification.resolve_case");
    private final AgentVerificationMapper mapper;
    private final AgentReleasePlanMapper plans;
    private final AgentDeploymentMapper deployments;
    private final AgentMapper agents;
    private final AutonomyMapper observations;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final AgentChangeService changes;
    private final AgentReleasePlanGate gate;
    private final AgentVerificationEvidence inspector;
    private final AgentToolData data;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public AgentVerificationService(AgentVerificationMapper mapper, AgentReleasePlanMapper plans, AgentDeploymentMapper deployments,
            AgentMapper agents, AutonomyMapper observations, AgentRunStore runs, AgentAccessService access, AgentChangeService changes,
            AgentReleasePlanGate gate, AgentVerificationEvidence inspector, AgentToolData data, ObjectMapper json) {
        this(mapper, plans, deployments, agents, observations, runs, access, changes, gate, inspector, data, json, Clock.systemUTC());
    }
    AgentVerificationService(AgentVerificationMapper mapper, AgentReleasePlanMapper plans, AgentDeploymentMapper deployments,
            AgentMapper agents, AutonomyMapper observations, AgentRunStore runs, AgentAccessService access, AgentChangeService changes,
            AgentReleasePlanGate gate, AgentVerificationEvidence inspector, AgentToolData data, ObjectMapper json, Clock clock) {
        this.mapper = mapper; this.plans = plans; this.deployments = deployments; this.agents = agents; this.observations = observations;
        this.runs = runs; this.access = access; this.changes = changes; this.gate = gate; this.inspector = inspector; this.data = data; this.json = json; this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode start(AgentToolContext context, String planId) {
        String code = "verification.start", key = callKey(context, code), hash = hash(List.of(code, planId));
        var plan = requiredPlan(context, planId); var approved = gate.snapshot(plan);
        authorize(context, code, approved.verificationPlan());
        var previous = replay(context, key, code, hash); if (previous != null) return previous;
        var existing = mapper.forPlan(planId);
        if (existing != null) return save(context, key, code, hash, view(context, existing));
        var change = changes.detail(context, plan.changeSetId());
        if (!change.current() || !change.readyForReview() || !plan.manifestHash().equals(change.manifestHash()))
            throw new IllegalArgumentException("候选清单已变化或未就绪，不能开始复查");
        if (!"SUCCEEDED".equals(plan.status())) throw new IllegalArgumentException("整组部署尚未全部完成，不能开始业务复查");
        var reviews = plans.reviews(plan.id());
        if (reviews.isEmpty()) throw new IllegalArgumentException("缺少整组人工审核");
        var review = reviews.getLast();
        if (!"APPROVED".equals(review.decision()) || !plan.snapshotHash().equals(review.snapshotHash()))
            throw new IllegalArgumentException("人工审核与复查标准不一致");
        var receipts = new ArrayList<DeploymentProof>(); Instant deployedAt = Instant.EPOCH;
        for (var step : approved.steps()) {
            var deployment = deployments.find(step.requestId());
            if (deployment == null || !"SUCCEEDED".equals(deployment.status()) || !context.runId().equals(deployment.runId())
                    || !plan.changeSetId().equals(deployment.changeSetId()) || !plan.manifestHash().equals(deployment.manifestHash())
                    || !step.approvalId().equals(deployment.approvalId()) || !step.binding().hash().equals(deployment.bindingHash())
                    || !hash(step.parameters()).equals(deployment.parametersHash()) || deployment.buildNumber() == null || deployment.receiptJson() == null)
                throw new IllegalArgumentException("部署记录与已审核的步骤不一致");
            var verified = AgentDeploymentReceipt.verify(json.readTree(deployment.receiptJson()), deployment, step.binding(), deployment.buildNumber(), step.parameters(), clock.instant());
            Instant finished = Instant.parse(verified.finishedAt());
            if (!"SUCCEEDED".equals(verified.deploymentStatus()) || finished.isAfter(clock.instant())) throw new IllegalArgumentException("实际部署尚未完成");
            receipts.add(new DeploymentProof(deployment.id(), AgentCodePolicy.hash(deployment.receiptJson()), finished));
            if (finished.isAfter(deployedAt)) deployedAt = finished;
        }
        if (receipts.isEmpty()) throw new IllegalArgumentException("没有实际部署步骤");
        String frozen = json.writeValueAsString(new Frozen(review.id(), approved.verificationPlan(), List.copyOf(receipts), deployedAt));
        var row = new AgentVerificationEntity(id(), plan.id(), context.runId(), plan.changeSetId(), plan.manifestHash(), plan.stage(),
                plan.snapshotHash(), frozen, AgentCodePolicy.hash(frozen), "OBSERVING", null, null, null, null, 0, now(), now());
        mapper.insert(row); event(row, "STARTED", Map.of("planId", plan.id(), "deployedAt", deployedAt, "businessVerified", false));
        return save(context, key, code, hash, view(context, row));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode record(AgentToolContext context, String verificationId, String requestKey, String checkId, String assessment,
                           String reason, boolean historicalDataPending, List<AgentVerificationEvidenceReference> references) {
        String code = "verification.record", key = callKey(context, code);
        var row = required(context, verificationId); Frozen frozen = frozen(row); authorize(context, code, frozen.plan());
        validateAssessment(assessment); String safeReason = reason(reason);
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 64 || references == null || references.isEmpty() || references.size() > 10)
            throw new IllegalArgumentException("每次采样须提供唯一请求标识及1至10个真实证据引用");
        String hash = hash(List.of(code, verificationId, requestKey, checkId, assessment, reason, historicalDataPending, references));
        var previous = replay(context, key, code, hash); if (previous != null) return previous;
        var old = mapper.sampleByKey(row.id(), requestKey);
        if (old != null) {
            if (!old.requestHash().equals(hash)) throw new IllegalArgumentException("采样请求标识已用于不同判断或证据");
            return save(context, key, code, hash, view(context, row));
        }
        observing(row);
        var check = frozen.plan().checks().stream().filter(value -> value.id().equals(checkId)).findFirst().orElseThrow(() -> new IllegalArgumentException("复查项不属于已获批标准"));
        if (mapper.samples(row.id(), checkId).size() >= 10000) throw new IllegalArgumentException("单项采样记录已达上限，请人工核对复查计划");
        var facts = references.stream().map(ref -> inspector.inspect(observations.observation(ref.observationId()), context, check,
                ref.pointer(), frozen.deployedAt(), clock.instant())).toList();
        if (facts.stream().map(AgentVerificationEvidence.Fact::sourceKey).distinct().count() != facts.size())
            throw new IllegalArgumentException("同一次采样不能重复引用同一监测对象");
        String evidence = json.writeValueAsString(facts);
        mapper.insertSample(new AgentVerificationSample(id(), row.id(), checkId, row.version() + 1, requestKey, hash, assessment, safeReason,
                historicalDataPending, evidence, AgentCodePolicy.hash(evidence), now()));
        update(row, row.status(), null, null, row.conclusionJson(), null);
        event(row, "SAMPLE", Map.of("checkId", checkId, "assessment", assessment, "historicalDataPending", historicalDataPending));
        return save(context, key, code, hash, view(context, mapper.find(row.id())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode finish(AgentToolContext context, String verificationId, String assessment, String reason, boolean historicalDataPending) {
        String code = "verification.finish", key = callKey(context, code);
        var row = required(context, verificationId); var frozen = frozen(row); authorize(context, code, frozen.plan());
        validateAssessment(assessment); String safeReason = reason(reason);
        String hash = hash(List.of(code, verificationId, assessment, reason, historicalDataPending));
        var previous = replay(context, key, code, hash); if (previous != null) return previous;
        observing(row); requireDeploymentUnchanged(row, frozen);
        var change = changes.detail(context, row.changeSetId());
        if (!change.current() || !change.readyForReview() || !row.manifestHash().equals(change.manifestHash()))
            throw new IllegalArgumentException("候选版本或原问题已变化，请重新核对发布材料");
        var progress = progress(context, row, frozen);
        boolean constraintsSatisfied = progress.stream().allMatch(value -> value.constraints().isEmpty());
        boolean passed = "PASS".equals(assessment) && !historicalDataPending && constraintsSatisfied;
        String status = passed ? "PASSED" : "FAIL".equals(assessment) ? "FAILED" : "OBSERVING";
        var conclusion = new LinkedHashMap<String, Object>();
        conclusion.put("assessment", assessment); conclusion.put("reason", safeReason); conclusion.put("historicalDataPending", historicalDataPending);
        conclusion.put("conclusionSource", "AI_ASSESSMENT"); conclusion.put("platformConstraintsSatisfied", constraintsSatisfied);
        conclusion.put("checks", progress); conclusion.put("recordedAt", clock.instant());
        String artifactId = null, artifactHash = null;
        if (passed) {
            var payload = new LinkedHashMap<String, Object>(conclusion);
            payload.put("schemaVersion", 1); payload.put("verificationId", row.id()); payload.put("planId", row.planId());
            payload.put("approvedSnapshotHash", row.approvedSnapshotHash()); payload.put("verificationSnapshotHash", row.snapshotHash());
            payload.put("phase", "TEST_DEPLOY".equals(row.stage()) ? "TEST_ENVIRONMENT" : "PRODUCTION"); payload.put("passed", true);
            payload.put("changeSetId", row.changeSetId()); payload.put("manifestHash", row.manifestHash());
            payload.put("deploymentIds", frozen.deployments().stream().map(DeploymentProof::requestId).toList());
            payload.put("deployedAt", frozen.deployedAt());
            String encoded = json.writeValueAsString(payload); artifactId = id(); artifactHash = AgentCodePolicy.hash(encoded);
            agents.insertArtifact(new AgentArtifactEntity(artifactId, row.runId(), "verification:" + row.id(), "VERIFICATION", "PLATFORM_VERIFICATION", encoded, artifactHash, now()));
        }
        update(row, status, artifactId, artifactHash, json.writeValueAsString(conclusion), null);
        event(row, "CONCLUSION", Map.of("assessment", assessment, "status", status, "platformConstraintsSatisfied", constraintsSatisfied, "caseClosed", false));
        return save(context, key, code, hash, view(context, mapper.find(row.id())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode resolveCase(AgentToolContext context,String verificationId,long caseVersion,long evidenceVersion,String reason) {
        String code = "verification.resolve_case", key = callKey(context,code);
        var row = required(context,verificationId); var frozen = frozen(row); authorize(context,code,frozen.plan());
        access.requireExecution(context,code,Set.of("autonomy:case:operate"));
        String safeReason = reason(reason);
        if (caseVersion < 0 || evidenceVersion < 0) throw new IllegalArgumentException("问题处理版本和证据版本无效");
        String requestHash = hash(List.of(code,verificationId,caseVersion,evidenceVersion,reason));
        var previous = replay(context,key,code,requestHash); if (previous != null) return previous;
        var approved = gate.snapshot(requiredPlan(context,row.planId()));
        var reference = approved.change().manifest().caseReference();
        if (reference == null) throw new IllegalArgumentException("本发布没有关联原问题，不能登记关闭");
        var problem = observations.lockCase(reference.caseId());
        var change = changes.detail(context,row.changeSetId());
        var existing = observations.resolutionForVerification(row.id());
        if (existing != null) {
            if (existing.caseVersion() != caseVersion || existing.evidenceVersion() != evidenceVersion || !existing.reason().equals(safeReason))
                throw new IllegalArgumentException("该复查已有不同的关闭判断，不能覆盖");
            return save(context,key,code,requestHash,view(context,row));
        }
        if (problem == null || !"ANALYZING".equals(problem.status()) || !context.runId().equals(problem.activeRunId())
                || problem.version() != caseVersion || reference.caseVersion() != caseVersion || problem.evidenceVersion() != evidenceVersion)
            throw new IllegalArgumentException("原问题处理或证据版本已变化，请重新读取并分析");
        var proposal = observations.proposal(reference.proposalId());
        if (!Objects.equals(problem.latestProposalId(),reference.proposalId()) || proposal == null || !problem.id().equals(proposal.caseId())
                || !reference.proposalHash().equals(AgentCodePolicy.hash(proposal.proposalJson()))
                || !change.current() || !change.readyForReview() || !row.manifestHash().equals(change.manifestHash()))
            throw new IllegalArgumentException("候选或原问题方案已变化，不能沿用该复查关闭");
        requireDeploymentUnchanged(row,frozen);
        var artifact = agents.findArtifact(row.runId(),"verification:" + row.id());
        if (!"PASSED".equals(row.status()) || artifact == null || !"VERIFICATION".equals(artifact.kind())
                || !"PLATFORM_VERIFICATION".equals(artifact.provenance()) || !artifact.id().equals(row.artifactId())
                || !artifact.contentHash().equals(row.artifactHash()) || !AgentCodePolicy.hash(artifact.payloadJson()).equals(row.artifactHash()))
            throw new IllegalArgumentException("缺少关联实际复查的通过凭据");
        var certificate = json.readTree(artifact.payloadJson());
        if (!certificate.path("passed").asBoolean() || !certificate.path("platformConstraintsSatisfied").asBoolean()
                || certificate.path("historicalDataPending").asBoolean() || !"PASS".equals(certificate.path("assessment").asText())
                || !row.id().equals(certificate.path("verificationId").asText()) || !row.planId().equals(certificate.path("planId").asText())
                || !row.manifestHash().equals(certificate.path("manifestHash").asText())
                || !row.snapshotHash().equals(certificate.path("verificationSnapshotHash").asText())
                || !row.approvedSnapshotHash().equals(certificate.path("approvedSnapshotHash").asText()))
            throw new IllegalArgumentException("复查凭据不完整或仍有历史数据待处置");
        var progress = progress(context,row,frozen);
        if (progress.stream().anyMatch(check -> !check.constraints().isEmpty())) throw new IllegalArgumentException("当前复查证据已失效或观察条件未满足，不能关闭");
        var covered = new ArrayList<CheckProgress>();
        for (var check : frozen.plan().checks()) {
            var current = progress.stream().filter(value -> value.checkId().equals(check.id())).findFirst().orElseThrow();
            var sample = mapper.sample(row.id(),current.latestSampleId());
            if (facts(context,row,frozen,check,sample).stream().anyMatch(fact ->
                    inspector.coversCase(problem,observations.observation(fact.observationId()),fact.pointer()))) covered.add(current);
        }
        if (covered.isEmpty()) throw new IllegalArgumentException("获批复查证据未覆盖原问题的环境和监测对象");
        var start = covered.stream().map(CheckProgress::windowStart).max(Comparator.naturalOrder()).orElseThrow();
        var end = covered.stream().map(CheckProgress::windowEnd).min(Comparator.naturalOrder()).orElseThrow();
        if (start.isAfter(end) || problem.lastSeenAt() != null && !problem.lastSeenAt().toInstant(ZoneOffset.UTC).isBefore(start))
            throw new IllegalArgumentException("已登记故障发生在恢复观察窗口内，请继续分析和复查");
        var closedAt = now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var resolution = new cn.aslight.workhub.model.autonomy.AutonomyCaseResolution(id(),problem.id(),caseVersion,caseVersion + 1,evidenceVersion,
                context.runId(),row.id(),row.planId(),row.changeSetId(),row.manifestHash(),reference.proposalId(),reference.proposalHash(),
                row.artifactId(),row.artifactHash(),json.writeValueAsString(covered.stream().map(CheckProgress::checkId).toList()),
                LocalDateTime.ofInstant(start,ZoneOffset.UTC),LocalDateTime.ofInstant(end,ZoneOffset.UTC),safeReason,closedAt);
        observations.insertResolution(resolution);
        if (observations.resolve(problem.id(),context.runId(),caseVersion,evidenceVersion,closedAt) != 1)
            throw new IllegalStateException("关闭时问题版本已变化，凭据未提交");
        event(row,"CASE_RESOLVED",Map.of("caseId",problem.id(),"resolutionId",resolution.id(),"caseVersion",caseVersion + 1,"verificationId",row.id()));
        return save(context,key,code,requestHash,view(context,row));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode waitForNext(AgentToolContext context, String verificationId, long seconds) {
        String code = "verification.wait", key = callKey(context, code);
        var row = required(context, verificationId); var frozen = frozen(row); authorize(context, code, frozen.plan());
        if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("下次复查等待须为1至86400秒，不能修改已获批观察标准");
        String hash = hash(List.of(code, verificationId, seconds));
        var previous = replay(context, key, code, hash); if (previous != null) return previous;
        observing(row);
        update(row, row.status(), null, null, row.conclusionJson(), now().plusSeconds(seconds));
        event(row, "WAIT_REGISTERED", Map.of("nextCheckAt", now().plusSeconds(seconds), "instruction", "请将本次运行结束为 WAITING_INPUT，平台到时唤醒原运行；预算不重置"));
        return save(context, key, code, hash, view(context, mapper.find(row.id())));
    }

    public JsonNode status(AgentToolContext context, String verificationId) {
        runs.requireActive(context.runId(), context.ownerToken());
        var row = required(context, verificationId); authorize(context, "verification.status", frozen(row).plan());
        return view(context, row);
    }

    /** 人类可查看原运行历史；不要求运行仍持有租约，但当前来源查看权及范围仍需保留。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JsonNode detailForUser(String planId, String user) {
        var read = authorizeUser(planId, user);
        return read.row() == null ? json.nullNode() : view(read.context(), read.row(), true);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JsonNode samplesForUser(String planId,String user,String checkId,long afterOrder,Long throughOrder,int pageSize) {
        var read = authorizeUser(planId,user); var row = requireHistory(read); var frozen = frozen(row);
        var check = check(frozen,checkId);
        long upper = throughOrder == null ? row.version() : throughOrder;
        if (afterOrder < 0 || upper < afterOrder || upper > row.version() || pageSize < 1 || pageSize > 20)
            throw new IllegalArgumentException("采样历史游标或分页无效");
        var found = mapper.samplePage(row.id(),checkId,afterOrder,upper,pageSize+1);
        boolean more = found.size() > pageSize;
        var page = found.subList(0,Math.min(found.size(),pageSize));
        var items = page.stream().map(sample -> new SampleView(sample.id(),sample.checkId(),sample.sampleOrder(),sample.assessment(),
                sample.reason(),sample.historicalDataPending(),sample.createdAt().toInstant(ZoneOffset.UTC),
                facts(read.context(),row,frozen,check,sample))).toList();
        return json.valueToTree(new SamplePage(planId,row.id(),checkId,afterOrder,upper,pageSize,items,more,
                page.isEmpty()?afterOrder:page.getLast().sampleOrder()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JsonNode evidenceForUser(String planId,String user,String sampleId,String observationId,String pointer) {
        var read = authorizeUser(planId,user); var row = requireHistory(read); var frozen = frozen(row);
        var sample = mapper.sample(row.id(),sampleId);
        if (sample == null) throw new IllegalArgumentException("采样记录不属于本发布计划");
        var check = check(frozen,sample.checkId());
        var matched = facts(read.context(),row,frozen,check,sample).stream()
                .filter(fact -> Objects.equals(observationId,fact.observationId()) && Objects.equals(pointer,fact.pointer())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("监测证据不属于指定采样引用"));
        var observation = observations.observation(matched.observationId());
        return json.valueToTree(new EvidenceView(planId,row.id(),sample.id(),sample.checkId(),observation.id(),matched.pointer(),
                observation.contentHash(),observation.toolCode(),observation.sourceType(),check.scope(),
                observation.observedAt().toInstant(ZoneOffset.UTC),json.readTree(observation.payloadJson())));
    }

    private UserRead authorizeUser(String planId, String user) {
        var plan = plans.find(planId); if (plan == null) throw new IllegalArgumentException("发布计划不存在");
        var actor = access.actor(user); actor.require("agent:task:view"); actor.require("agent:code:use");
        var run = runs.get(plan.runId()); var agent = agents.findAgent(run.agentId()); access.requireAgent(actor, agent);
        changes.detailForUser(plan.changeSetId(), user);
        var context = runs.context(run); var current = json.readValue(agent.configurationJson(), AgentConfiguration.class);
        var standards = gate.snapshot(plan).verificationPlan();
        if (standards != null) for (var check : standards.checks()) {
            if (!current.scopes().contains(check.scope()) || !context.configuration().scopes().contains(check.scope())
                    || !context.specification().scopes().contains(check.scope())) throw new AccessDeniedException("复查范围超出当前 Agent 或原任务授权");
            if (permissions(check.toolCode()).stream().noneMatch(actor::has)) throw new AccessDeniedException("缺少该复查监测来源的查看权限");
        }
        var row = mapper.forPlan(planId);
        return new UserRead(context,row);
    }
    private AgentVerificationEntity requireHistory(UserRead read) {
        if (read.row() == null) throw new IllegalArgumentException("本发布计划尚未开始复查");
        return read.row();
    }
    private AgentReleaseVerificationPlan.Check check(Frozen frozen,String id) {
        return frozen.plan().checks().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("复查项不属于本发布计划"));
    }

    public List<String> due() { return mapper.due(now()); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean wake(String verificationId) {
        var initial = mapper.find(verificationId); if (initial == null) return false;
        var run = agents.lockRun(initial.runId()); var row = mapper.find(verificationId);
        if (!"OBSERVING".equals(row.status()) || row.nextCheckAt() == null || row.nextCheckAt().isAfter(now())) return false;
        if (run.cancelRequested() || Set.of("COMPLETED", "FAILED", "CANCELLED").contains(run.status())) {
            update(row, row.status(), row.artifactId(), row.artifactHash(), row.conclusionJson(), null);
            event(row, "WAIT_STOPPED", Map.of("reason", "原运行已停止或结束")); return false;
        }
        if (!"WAITING_INPUT".equals(run.status()) || run.externalHandle() != null || run.ownerToken() != null) return false;
        var context = runs.context(run); String blocked = null;
        try {
            authorize(context, "verification.wait", frozen(row).plan()); runs.requireProvider(run);
            if (run.stepCount() >= context.configuration().maxSteps() || run.elapsedMillis() >= context.configuration().maxRunSeconds() * 1000L)
                blocked = "原运行预算已耗尽，需人工调整任务；未重置预算";
        } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException ex) { blocked = reason(ex.getMessage()); }
        update(row, row.status(), null, null, row.conclusionJson(), null);
        if (blocked != null) { event(row, "WAIT_BLOCKED", Map.of("reason", blocked)); return false; }
        if (agents.acceptMessage(run.id(), run.version(), now()) != 1) throw new IllegalStateException("复查等待状态已变化");
        event(row, "WAKE", Map.of("verificationId", row.id(), "instruction", "按已获批标准继续取得新监测证据；先前采样和预算保留"));
        return true;
    }

    private JsonNode view(AgentToolContext context, AgentVerificationEntity row) { return view(context,row,false); }
    private JsonNode view(AgentToolContext context, AgentVerificationEntity row,boolean human) {
        var frozen = frozen(row);
        var checks = progress(context,row,frozen);
        var value = new LinkedHashMap<String, Object>();
        value.put("id", row.id()); value.put("planId", row.planId()); value.put("status", row.status()); value.put("version", row.version());
        value.put("runId",row.runId()); value.put("changeSetId",row.changeSetId()); value.put("stage",row.stage());
        value.put("manifestHash",row.manifestHash()); value.put("approvedSnapshotHash",row.approvedSnapshotHash());
        value.put("constraintsCheckedAt", clock.instant()); value.put("currentConstraintsSatisfied",checks.stream().allMatch(check -> check.constraints().isEmpty()));
        value.put("deployedAt", frozen.deployedAt()); value.put("verificationPlan", frozen.plan()); value.put("nextCheckAt", row.nextCheckAt());
        value.put("artifactId", row.artifactId()); value.put("checks", checks.stream().map(this::compact).toList());
        JsonNode conclusion = row.conclusionJson() == null ? json.nullNode() : json.readTree(row.conclusionJson());
        if (!human && conclusion.isObject()) {
            var copy = (tools.jackson.databind.node.ObjectNode) conclusion;
            copy.remove("checks");
            String reason = copy.path("reason").asText(); copy.put("reason", preview(reason, 1000)); copy.put("reasonTruncated", reason.length() > 1000);
        }
        value.put("conclusion", conclusion);
        var resolution = observations.resolutionForVerification(row.id());
        var problem = resolution == null ? null : observations.find(resolution.caseId());
        if (resolution != null && !human) changes.detail(context,row.changeSetId());
        value.put("caseResolution",resolution);
        value.put("caseStatus",problem == null ? null : problem.status());
        value.put("caseClosed",problem != null && "RESOLVED".equals(problem.status()) && problem.version() == resolution.resolvedVersion());
        return json.valueToTree(value);
    }
    private JsonNode compact(CheckProgress progress) {
        var value = (tools.jackson.databind.node.ObjectNode) json.valueToTree(progress);
        value.put("reason", preview(progress.reason(), 240)); value.put("reasonTruncated", progress.reason().length() > 240);
        return value;
    }
    private String preview(String value, int maximum) {
        int points = value.codePointCount(0, value.length());
        return points <= maximum ? value : value.substring(0, value.offsetByCodePoints(0, maximum));
    }
    private List<CheckProgress> progress(AgentToolContext context, AgentVerificationEntity row, Frozen frozen) {
        return frozen.plan().checks().stream().map(check -> checkProgress(context, row, frozen, check)).toList();
    }
    private CheckProgress checkProgress(AgentToolContext context, AgentVerificationEntity row, Frozen frozen, AgentReleaseVerificationPlan.Check check) {
        var samples = mapper.samples(row.id(), check.id());
        var window = new ArrayList<AgentVerificationSample>(); Map<String, AgentVerificationEvidence.Fact> previous = Map.of();
        Instant start = null, end = null; AgentVerificationSample latest = null;
        var issues = new LinkedHashSet<String>();
        for (var sample : samples) {
            latest = sample;
            var facts = facts(context,row,frozen,check,sample);
            var current = new TreeMap<String, AgentVerificationEvidence.Fact>(); issues.clear();
            for (var fact : facts) {
                current.put(fact.sourceKey(), fact); issues.addAll(fact.problems());
            }
            if (!"PASS".equals(sample.assessment())) issues.add("AGENT_NOT_PASS");
            if (sample.historicalDataPending()) issues.add("HISTORICAL_DATA_PENDING");
            if (facts.isEmpty()) issues.add("EVIDENCE_MISSING");
            if (!issues.isEmpty()) { window.clear(); previous = Map.of(); start = null; end = null; continue; }
            Instant dataAt = facts.stream().map(AgentVerificationEvidence.Fact::dataAt).min(Comparator.naturalOrder()).orElseThrow();
            var preceding = previous;
            boolean sameSources = preceding.keySet().equals(current.keySet());
            if (!previous.isEmpty() && sameSources && current.entrySet().stream().anyMatch(entry ->
                    !entry.getValue().dataAt().isAfter(previousTime(preceding, entry.getKey())))) continue;
            boolean gap = !previous.isEmpty() && sameSources && current.entrySet().stream().anyMatch(entry ->
                    Duration.between(previousTime(preceding, entry.getKey()), entry.getValue().dataAt()).compareTo(Duration.ofSeconds(check.sampleIntervalSeconds())) > 0
                            || (entry.getValue().coverageFrom() != null && preceding.get(entry.getKey()).coverageThrough() != null
                            && entry.getValue().coverageFrom().isAfter(preceding.get(entry.getKey()).coverageThrough())));
            if (!sameSources || gap) { window.clear(); start = null; }
            if (start == null) start = facts.stream().map(AgentVerificationEvidence.Fact::dataAt).max(Comparator.naturalOrder()).orElseThrow();
            end = dataAt; previous = current; window.add(sample);
        }
        if (latest == null) issues.add("EVIDENCE_MISSING");
        if (window.size() < check.minimumSamples()) issues.add("INSUFFICIENT_DISTINCT_SAMPLES");
        long observedSeconds = start == null || end == null ? 0 : Math.max(0, Duration.between(start, end).getSeconds());
        if (observedSeconds < check.observationSeconds()) issues.add("OBSERVATION_WINDOW_INCOMPLETE");
        if (end == null || end.plusSeconds(check.maxDataAgeSeconds()).isBefore(clock.instant())) issues.add("LATEST_DATA_STALE");
        String evidenceRoot = hash(window.stream().map(sample -> List.of(sample.id(), sample.evidenceHash(), sample.assessment(), sample.reason(), sample.historicalDataPending())).toList());
        return new CheckProgress(check.id(), latest == null ? "INCONCLUSIVE" : latest.assessment(), latest == null ? "" : latest.reason(),
                latest != null && latest.historicalDataPending(), window.size(), observedSeconds, start, end, List.copyOf(issues),
                latest == null ? null : latest.id(), evidenceRoot);
    }
    private List<AgentVerificationEvidence.Fact> facts(AgentToolContext context,AgentVerificationEntity row,Frozen frozen,
            AgentReleaseVerificationPlan.Check check,AgentVerificationSample sample) {
        if (!row.id().equals(sample.verificationId()) || !check.id().equals(sample.checkId())
                || !AgentCodePolicy.hash(sample.evidenceJson()).equals(sample.evidenceHash())) throw new IllegalStateException("复查采样证据摘要或归属不一致");
        var facts = Arrays.asList(json.readValue(sample.evidenceJson(),AgentVerificationEvidence.Fact[].class));
        for (var fact : facts) {
            var observation = observations.observation(fact.observationId());
            if (observation == null || !fact.contentHash().equals(observation.contentHash())) throw new IllegalStateException("复查原始监测证据已缺失或变化");
            var verified = inspector.inspect(observation,context,check,fact.pointer(),frozen.deployedAt(),sample.createdAt().toInstant(ZoneOffset.UTC));
            if (!verified.equals(fact)) throw new IllegalStateException("复查采样与原始监测证据不一致");
        }
        return facts;
    }
    private static Instant previousTime(Map<String, AgentVerificationEvidence.Fact> previous, String key) { return previous.get(key).dataAt(); }

    private void requireDeploymentUnchanged(AgentVerificationEntity row, Frozen frozen) {
        var plan = plans.find(row.planId());
        if (plan == null || !"SUCCEEDED".equals(plan.status()) || !row.approvedSnapshotHash().equals(plan.snapshotHash())) throw new IllegalArgumentException("获批发布材料已变化");
        gate.snapshot(plan);
        for (var proof : frozen.deployments()) {
            var deployment = deployments.find(proof.requestId());
            if (deployment == null || !"SUCCEEDED".equals(deployment.status()) || deployment.receiptJson() == null
                    || !proof.receiptHash().equals(AgentCodePolicy.hash(deployment.receiptJson()))) throw new IllegalArgumentException("实际部署凭据已变化");
        }
    }
    private void authorize(AgentToolContext context, String code, AgentReleaseVerificationPlan plan) {
        access.requireExecution(context, code, Set.of("agent:code:deliver"));
        if (plan == null) throw new IllegalArgumentException("发布未包含已获批的业务复查标准");
        for (var check : plan.checks()) {
            access.requireScope(context, check.scope());
            access.requireExecution(context, check.toolCode(), permissions(check.toolCode()));
        }
    }
    private Set<String> permissions(String code) {
        if (code.startsWith("monitor.accounting.")) return Set.of("ops:accounting:view", "ops:accounting:manage");
        if (code.startsWith("monitor.business.")) return Set.of("ops:monitor:view", "ops:monitor:manage");
        return Set.of("ops:system-alert:view", "ops:system-alert:manage");
    }
    private String callKey(AgentToolContext context, String code) {
        var run = agents.lockRun(context.runId()); runs.requireActive(context.runId(), context.ownerToken());
        if (!context.taskId().equals(run.taskId()) || !context.agentId().equals(run.agentId())) throw new IllegalStateException("复查身份与运行不一致");
        var handle = run.externalHandle() == null ? json.nullNode() : json.readTree(run.externalHandle());
        if ("CLI".equals(handle.path("kind").asText())) handle = handle.path("activeTool");
        if (!"TOOL".equals(handle.path("kind").asText()) || !code.equals(handle.path("tool").asText()) || handle.path("key").asText().isBlank())
            throw new IllegalStateException("复查写入必须关联当前实际工具调用");
        return handle.path("key").asText();
    }
    private AgentReleasePlanEntity requiredPlan(AgentToolContext context, String id) {
        var plan = plans.find(id);
        if (plan == null || !context.runId().equals(plan.runId())) throw new IllegalArgumentException("发布计划不属于本次运行");
        return plan;
    }
    private AgentVerificationEntity required(AgentToolContext context, String id) {
        var row = mapper.find(id);
        if (row == null || !context.runId().equals(row.runId())) throw new IllegalArgumentException("复查不属于本次运行");
        return row;
    }
    private Frozen frozen(AgentVerificationEntity row) {
        if (!AgentCodePolicy.hash(row.snapshotJson()).equals(row.snapshotHash())) throw new IllegalStateException("复查固定材料摘要不一致");
        return json.readValue(row.snapshotJson(), Frozen.class);
    }
    private JsonNode replay(AgentToolContext context, String key, String code, String hash) {
        var saved = mapper.call(context.runId(), key);
        if (saved == null) return null;
        if (!code.equals(saved.toolCode()) || !hash.equals(saved.requestHash())) throw new IllegalArgumentException("工具调用标识已用于不同复查请求");
        return json.readTree(saved.resultJson());
    }
    private JsonNode save(AgentToolContext context, String key, String code, String hash, JsonNode result) {
        var safe = data.sanitize(result);
        mapper.insertCall(new AgentVerificationCall(context.runId(), key, code, hash, json.writeValueAsString(safe), now()));
        return safe;
    }
    private void update(AgentVerificationEntity row, String status, String artifactId, String artifactHash, String conclusion, LocalDateTime nextCheckAt) {
        if (mapper.update(new AgentVerificationEntity(row.id(), row.planId(), row.runId(), row.changeSetId(), row.manifestHash(), row.stage(),
                row.approvedSnapshotHash(), row.snapshotJson(), row.snapshotHash(), status, artifactId, artifactHash, conclusion, nextCheckAt, row.version(), row.createdAt(), now())) != 1)
            throw new IllegalStateException("复查状态已变化");
    }
    private void event(AgentVerificationEntity row, String action, Object value) {
        agents.insertEvent(new AgentEventEntity(id(), row.runId(), "verification:" + row.id() + ":" + action + ":" + row.version(),
                agents.nextEventOrdinal(row.runId()), "VERIFICATION_" + action, json.writeValueAsString(value), "verification-runtime", now()));
    }
    private void observing(AgentVerificationEntity row) { if (!"OBSERVING".equals(row.status())) throw new IllegalArgumentException("该复查已终结，不能覆盖既有结论"); }
    private void validateAssessment(String value) { if (!Set.of("PASS", "FAIL", "INCONCLUSIVE").contains(value)) throw new IllegalArgumentException("复查判断须为 PASS、FAIL 或 INCONCLUSIVE"); }
    private String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 4000) throw new IllegalArgumentException("请提供1至4000字的业务判断理由");
        String safe = data.sanitize(reason.trim()).asText(); if (safe.length() > 4000) throw new IllegalArgumentException("判断理由脱敏后过长");
        return safe;
    }
    private String hash(Object value) { return AgentCodePolicy.hash(json.writeValueAsString(value)); }
    private static String id() { return UUID.randomUUID().toString(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private record UserRead(AgentToolContext context,AgentVerificationEntity row) { }
    public record SampleView(String id,String checkId,long sampleOrder,String assessment,String reason,boolean historicalDataPending,
            Instant createdAt,List<AgentVerificationEvidence.Fact> evidence) { }
    public record SamplePage(String planId,String verificationId,String checkId,long afterOrder,long throughOrder,int pageSize,
            List<SampleView> items,boolean hasMore,long nextAfterOrder) { }
    public record EvidenceView(String planId,String verificationId,String sampleId,String checkId,String observationId,String pointer,
            String contentHash,String toolCode,String sourceType,AgentScope scope,Instant observedAt,JsonNode payload) { }
    public record DeploymentProof(String requestId, String receiptHash, Instant finishedAt) { }
    public record Frozen(String reviewId, AgentReleaseVerificationPlan plan, List<DeploymentProof> deployments, Instant deployedAt) { }
    public record CheckProgress(String checkId, String assessment, String reason, boolean historicalDataPending,
            int distinctSamples, long observedSeconds, Instant windowStart, Instant windowEnd, List<String> constraints,
            String latestSampleId, String evidenceRoot) { }
}
