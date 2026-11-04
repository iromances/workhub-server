package cn.aslight.workhub.service.autonomy;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.autonomy.*;
import cn.aslight.workhub.service.agent.*;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

/** 问题共享的是受控证据和分析历史，来源账号/业务范围权限仍每次重新验证。 */
@Service
public class AutonomyCaseService {
    private static final Map<String, Set<String>> SOURCE_PERMISSIONS = Map.of(
            "BUSINESS_MONITOR", Set.of("ops:monitor:view", "ops:monitor:manage"),
            "ACCOUNTING", Set.of("ops:accounting:view", "ops:accounting:manage"),
            "SYSTEM_ALERT", Set.of("ops:system-alert:view", "ops:system-alert:manage"),
            "SELF_LOG", Set.of("ops:system-alert:view", "ops:system-alert:manage"),
            "INSPECTION", Set.of("ops:system-alert:view", "ops:system-alert:manage"));
    private final AutonomyMapper mapper;
    private final AgentMapper agents;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final AutonomyEvidence evidence;
    private final AgentToolData data;
    private final ObjectMapper json;
    private final Validator validator;
    private final AgentMonitorReadMapper bugSources;

    public AutonomyCaseService(AutonomyMapper mapper, AgentMapper agents, AgentRunStore runs, AgentAccessService access,
                               AutonomyEvidence evidence, AgentToolData data, ObjectMapper json, Validator validator,
                               AgentMonitorReadMapper bugSources) {
        this.mapper = mapper; this.agents = agents; this.runs = runs; this.access = access;
        this.evidence = evidence; this.data = data; this.json = json; this.validator = validator;
        this.bugSources = bugSources;
    }

    @Transactional
    public Map<String, Object> record(AgentToolContext context, String observationId, String pointer,
                                      String category, String title, Long staleAfterSeconds) {
        active(context);
        if (title == null || title.isBlank() || title.length() > 200) throw new IllegalArgumentException("问题标题须为1至200字");
        var observation = mapper.observation(observationId);
        if (observation == null || !observation.runId().equals(context.runId()) || !observation.agentId().equals(context.agentId())) {
            throw new IllegalArgumentException("只能使用本次运行实际取得的巡检证据");
        }
        scope(context, observation.businessLineCode(), observation.environmentCode(), observation.sourceType());
        var extracted = evidence.extract(observation, pointer, category, staleAfterSeconds);
        String fingerprint = digest(List.of(observation.businessLineCode(), observation.environmentCode(), observation.sourceType(), extracted.subjectKey(), category));
        String signalKey = digest(List.of(fingerprint, extracted.sourceEventId(), extracted.sourceVersion()));
        if (mapper.lockSignals() == null) throw new IllegalStateException("巡检问题表尚未就绪");
        var found = mapper.byFingerprint(fingerprint);
        var now = now();
        boolean created = found == null;
        if (created) {
            found = new AutonomyCaseEntity(UUID.randomUUID().toString(), fingerprint, observation.businessLineCode(), observation.environmentCode(),
                    observation.sourceType(), extracted.subjectKey(), category, data.sanitize(title.trim()).asText(), "DISCOVERED", null, null,
                    0, 0, 0, null, null, observation.observedAt(), null, now, now);
            mapper.insertCase(found);
        } else found = mapper.lockCase(found.id());
        var previous = mapper.signal(signalKey);
        boolean added = previous == null;
        if (added) mapper.insertSignal(new AutonomySignalEntity(UUID.randomUUID().toString(), signalKey, found.id(), observation.id(), pointer,
                extracted.sourceEventId(), extracted.sourceVersion(), json.writeValueAsString(extracted.evidence()), extracted.occurredAt(), observation.observedAt()));
        int reopen = 0;
        if (added && "RESOLVED".equals(found.status()) && found.resolvedAt() != null) {
            var resolution = mapper.resolutionForVersion(found.id(), found.version() - 1);
            var boundary = resolution == null ? found.resolvedAt() : resolution.windowStart();
            if (extracted.occurredAt() == null || extracted.occurredAt().isAfter(observation.observedAt())) {
                // 旧的无凭据历史保留原行为；本功能关闭的问题不能以未知时间维持已验证状态。
                if (resolution != null) reopen = 2;
            } else if (resolution == null ? extracted.occurredAt().isAfter(boundary) : !extracted.occurredAt().isBefore(boundary)) reopen = 1;
        }
        boolean reopened = reopen > 0;
        var indexedTime = extracted.occurredAt() != null && !extracted.occurredAt().isAfter(observation.observedAt()) ? extracted.occurredAt() : null;
        mapper.observe(found.id(), added ? 1 : 0, indexedTime, observation.observedAt(), reopen, now);
        link(found.id(), context.runId(), "OBSERVATION");
        return Map.of("caseInfo", mapper.find(found.id()), "created", created, "newEvidence", added, "reopened", reopened,
                "assessment", "AI_CANDIDATE_REQUIRES_REVIEW");
    }

    @Transactional
    public Map<String, Object> claim(AgentToolContext context, String caseId, long version) {
        active(context);
        var value = required(mapper.lockCase(caseId));
        requireCaseAccess(context, value);
        if (!Set.of("DISCOVERED", "ANALYZING", "NEEDS_INPUT", "BLOCKED", "FAILED").contains(value.status())) {
            throw new IllegalArgumentException("问题处于验证、审核、发布或已关闭阶段，不能重新领取分析");
        }
        if (context.runId().equals(value.activeRunId())) return Map.of("claimed", true, "caseInfo", value);
        if (value.activeRunId() != null) {
            var prior = agents.findRun(value.activeRunId());
            // 未知外部执行仍占有问题，不能以租约过期或 WAITING_RESOURCE 推断操作已停止。
            if (prior == null || prior.ownerToken() != null || prior.externalHandle() != null
                    || !Set.of("COMPLETED", "FAILED", "CANCELLED", "WAITING_RESOURCE").contains(prior.status())) {
                return Map.of("claimed", false, "reason", "EXISTING_ACTIVE_RUN", "caseInfo", value);
            }
        }
        requireVersion(value, version);
        changed(mapper.claim(caseId, context.runId(), version, now()));
        link(caseId, context.runId(), "ANALYSIS");
        mapper.linkAnalysis(caseId, context.runId());
        return Map.of("claimed", true, "caseInfo", mapper.find(caseId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> propose(AgentToolContext context, String caseId, long version, String key, AutonomyProposal proposal) {
        active(context);
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,64}")) throw new IllegalArgumentException("方案请求标识无效");
        if (proposal == null || !validator.validate(proposal).isEmpty()) throw new IllegalArgumentException("分析须包含事实、推断、影响、方案、验收、回退、未知项和有效变更类型，且不超出长度限制");
        var value = required(mapper.lockCase(caseId));
        requireCaseAccess(context, value);
        requireCurrentBugLinks(value);
        String hash = digest(new ProposalRequest(version, proposal));
        var prior = mapper.proposalByKey(caseId, context.runId(), key);
        if (prior != null) {
            if (!prior.requestHash().equals(hash)) throw new IllegalArgumentException("方案请求标识已被不同内容使用");
            return Map.of("proposal", prior, "caseInfo", value, "source", "AI_ANALYSIS", "approved", false);
        }
        if (!context.runId().equals(value.activeRunId()) || !"ANALYZING".equals(value.status())) throw new IllegalArgumentException("只有当前领取问题的分析运行可以提交方案");
        requireVersion(value, version);
        var safeProposal = data.sanitize(proposal);
        if (safeProposal.path("truncated").asBoolean()) throw new IllegalArgumentException("方案编码后超过记录上限，请精简后提交");
        var saved = new AutonomyProposalEntity(UUID.randomUUID().toString(), caseId, context.runId(), key, hash, version + 1,
                json.writeValueAsString(safeProposal), now());
        mapper.insertProposal(saved);
        changed(mapper.setProposal(caseId, context.runId(), saved.id(), version, now()));
        return Map.of("proposal", saved, "caseInfo", mapper.find(caseId), "source", "AI_ANALYSIS", "approved", false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> linkBug(AgentToolContext context, String caseId, long version, String key,
            long workItemId, String workItemVersion, String observationId, String pointer, String reason, Long staleAfterSeconds) {
        active(context);
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,64}") || workItemId < 1
                || workItemVersion == null || !workItemVersion.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("缺陷关联的请求标识或原文版本无效");
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw new IllegalArgumentException("请提供1至2000字的关联依据");
        String safeReason = data.sanitize(reason.trim()).asText();
        if (safeReason.length() > 2000) throw new IllegalArgumentException("关联依据脱敏后超过2000字，请精简");
        var problem = required(mapper.lockCase(caseId));
        requireCaseAccess(context, problem);
        bugPermission(context.requestedBy(), context.agentId());
        String hash = digest(Arrays.asList(version, workItemId, workItemVersion, observationId, pointer, reason, staleAfterSeconds));
        var replay = mapper.bugLinkRequest(caseId, context.runId(), key);
        if (replay != null) {
            if (!replay.requestHash().equals(hash)) throw new IllegalArgumentException("关联请求标识已被不同内容使用");
            return bugLinkResult(problem, mapper.bugLink(replay.linkId()));
        }
        if (!context.runId().equals(problem.activeRunId()) || !"ANALYZING".equals(problem.status()))
            throw new IllegalArgumentException("请先领取问题，再关联缺陷证据");
        requireVersion(problem, version);
        var bug = bugSources.bug(problem.businessLineCode(), workItemId);
        if (bug == null || !workItemVersion.equals(digest(bug)))
            throw new IllegalArgumentException("缺陷原文或所属业务线已变化，请重新读取并分析");
        var observation = mapper.observation(observationId);
        if (observation == null || !context.runId().equals(observation.runId()) || !context.agentId().equals(observation.agentId())
                || !problem.businessLineCode().equals(observation.businessLineCode())
                || !problem.environmentCode().equals(observation.environmentCode()) || !problem.sourceType().equals(observation.sourceType()))
            throw new IllegalArgumentException("关联证据必须来自本次运行、该问题的业务线、环境与来源");
        var extracted = evidence.extract(observation, pointer, problem.category(), staleAfterSeconds);
        String fingerprint = digest(List.of(observation.businessLineCode(), observation.environmentCode(), observation.sourceType(), extracted.subjectKey(), problem.category()));
        var signal = mapper.signal(digest(List.of(fingerprint, extracted.sourceEventId(), extracted.sourceVersion())));
        if (signal == null || !problem.id().equals(signal.caseId()))
            throw new IllegalArgumentException("请先用该记录位置登记实际问题证据，再关联缺陷");
        var latest = mapper.latestBugLink(caseId, workItemId);
        if (latest == null || !latest.workItemVersion().equals(workItemVersion)) {
            if (latest == null && mapper.currentBugLinks(caseId).size() >= 20)
                throw new IllegalArgumentException("单个问题最多关联20个不同缺陷，请拆分分析");
            var snapshot = json.createObjectNode();
            snapshot.set("workItem", json.valueToTree(bug));
            snapshot.set("scope", json.valueToTree(new AgentScope(problem.businessLineCode(), problem.environmentCode())));
            snapshot.put("assessment", "AI_SUGGESTED_RELATION").put("environmentBasis", "REFERENCED_RUNTIME_EVIDENCE");
            var payload = json.readTree(observation.payloadJson());
            for (String field : List.of("scopeBasis", "historicalEnvironmentVerified"))
                if (payload.has(field)) snapshot.set(field, payload.get(field));
            var safe = data.sanitize(snapshot);
            String encoded = json.writeValueAsString(safe);
            if (safe.path("truncated").asBoolean() || encoded.length() > 28_000)
                throw new IllegalArgumentException("缺陷完整快照过长，暂不能关联，请标为待补充");
            latest = new AutonomyBugLinkEntity(UUID.randomUUID().toString(), caseId, workItemId, workItemVersion,
                    encoded, observationId, pointer, signal.id(), safeReason, context.runId(), version + 1, now());
            mapper.insertBugLink(latest);
            changed(mapper.reviseBugContext(caseId, version, now()));
        }
        mapper.insertBugLinkRequest(new AutonomyBugLinkRequest(caseId, context.runId(), key, hash, latest.id(), now()));
        return bugLinkResult(mapper.find(caseId), latest);
    }

    public AgentPage<AutonomyBugLinkView> bugs(AgentToolContext context, String caseId, int page, int size) {
        runs.requireActive(context.runId(), context.ownerToken());
        var problem = required(mapper.find(caseId));
        requireCaseAccess(context, problem);
        bugPermission(context.requestedBy(), context.agentId());
        return bugs(problem, page, size);
    }
    public AgentPage<AutonomyBugLinkView> bugsForUser(String agentId, String caseId, String user, int page, int size) {
        var problem = requireCaseForUser(agentId, caseId, user);
        bugPermission(user, agentId);
        return bugs(problem, page, size);
    }
    private AgentPage<AutonomyBugLinkView> bugs(AutonomyCaseEntity problem, int page, int size) {
        int start = offset(page, size, 5);
        return new AgentPage<>(mapper.countBugLinks(problem.id()), mapper.bugLinks(problem.id(), start, size).stream()
                .map(link -> bugLinkView(problem, link)).toList());
    }
    /** 巡检确认引用持久关联，不能由模型为无环境的工作项伪造 Observation。 */
    public AutonomyBugLinkEntity requireBugFinding(AgentToolContext context, String caseId, long workItemId, String sourceVersion) {
        var problem = required(mapper.find(caseId));
        requireCaseAccess(context, problem);
        bugPermission(context.requestedBy(), context.agentId());
        var linked = mapper.latestBugLink(caseId, workItemId);
        if (linked == null || !linked.workItemVersion().equals(sourceVersion) || !"CURRENT".equals(bugSourceState(problem, linked)))
            throw new IllegalArgumentException("缺陷尚未关联当前原文版本的运行证据，请先定位关联或标记待补充");
        return linked;
    }
    public void requireCurrentBugLinks(AutonomyCaseEntity problem) {
        if (bugContextChanged(problem)) throw new IllegalArgumentException("关联缺陷的需求依据已变化，请重新读取、关联和分析");
    }
    public boolean bugContextChanged(AutonomyCaseEntity problem) {
        return mapper.currentBugLinks(problem.id()).stream().anyMatch(link -> !"CURRENT".equals(bugSourceState(problem, link)));
    }
    private Map<String, Object> bugLinkResult(AutonomyCaseEntity problem, AutonomyBugLinkEntity link) {
        return Map.of("caseInfo", problem, "association", bugLinkView(problem, link), "assessment", "AI_SUGGESTED_RELATION", "resolved", false);
    }
    private AutonomyBugLinkView bugLinkView(AutonomyCaseEntity problem, AutonomyBugLinkEntity link) {
        return new AutonomyBugLinkView(link, link.id().equals(mapper.latestBugLink(problem.id(), link.workItemId()).id()), bugSourceState(problem, link));
    }
    private String bugSourceState(AutonomyCaseEntity problem, AutonomyBugLinkEntity link) {
        var current = bugSources.bug(problem.businessLineCode(), link.workItemId());
        return current == null ? "UNAVAILABLE" : link.workItemVersion().equals(digest(current)) ? "CURRENT" : "CHANGED";
    }
    private void bugPermission(String user, String agentId) {
        var actor = access.actor(user);
        var agent = agents.findAgent(agentId);
        access.requireAgent(actor, agent);
        actor.require("work-item:view");
        access.actor(agent.ownerName()).require("work-item:view");
    }

    /** 只承认由这份候选的持久复查凭据造成的关闭版本变化；复发或另一个方案不能沿用。 */
    public boolean matchesReference(AutonomyCaseEntity problem,AgentChangeSetEntity change,AgentChangeManifest.CaseReference reference) {
        if (problem == null || !Objects.equals(problem.latestProposalId(),reference.proposalId())) return false;
        var proposal = mapper.proposal(reference.proposalId());
        if (proposal == null || !problem.id().equals(proposal.caseId())
                || !AutonomyEvidence.digest(proposal.proposalJson()).equals(reference.proposalHash())) return false;
        if (problem.version() == reference.caseVersion()) return true;
        if (!"RESOLVED".equals(problem.status()) || problem.version() != reference.caseVersion() + 1 || problem.activeRunId() != null) return false;
        var resolved = mapper.resolutionForVersion(problem.id(),reference.caseVersion());
        if (resolved == null || resolved.resolvedVersion() != problem.version() || !Objects.equals(resolved.createdAt(),problem.resolvedAt())
                || !change.id().equals(resolved.changeSetId()) || !change.manifestHash().equals(resolved.manifestHash())
                || !change.runId().equals(resolved.runId()) || !reference.proposalId().equals(resolved.proposalId())
                || !reference.proposalHash().equals(resolved.proposalHash()) || mapper.resolutionProofMatches(resolved) != 1) return false;
        var artifact = agents.findArtifact(resolved.runId(),"verification:" + resolved.verificationId());
        return artifact != null && "VERIFICATION".equals(artifact.kind()) && "PLATFORM_VERIFICATION".equals(artifact.provenance())
                && resolved.artifactId().equals(artifact.id()) && resolved.artifactHash().equals(artifact.contentHash())
                && resolved.artifactHash().equals(AutonomyEvidence.digest(artifact.payloadJson()));
    }

    public AgentPage<AutonomyCaseResolution> resolutions(AgentToolContext context,String id,int page,int size) {
        runs.requireActive(context.runId(),context.ownerToken());
        requireCaseAccess(context,required(mapper.find(id)));
        return resolutionPage(id,page,size);
    }
    public AgentPage<AutonomyCaseResolution> resolutionsForUser(String agentId,String id,String user,int page,int size) {
        requireCaseForUser(agentId,id,user);
        return resolutionPage(id,page,size);
    }
    private AgentPage<AutonomyCaseResolution> resolutionPage(String id,int page,int size) {
        return new AgentPage<>(mapper.countResolutions(id),mapper.resolutions(id,offset(page,size,10),size));
    }

    public AgentPage<AutonomyCaseEntity> list(AgentToolContext context, AgentScope scope, int page, int size) {
        runs.requireActive(context.runId(), context.ownerToken());
        access.requireScope(context, scope);
        return list(scope, sources(context.requestedBy(), context.agentId()), page, size);
    }
    public AutonomyCaseDetailResponse detail(AgentToolContext context, String id, int evidencePage, int proposalPage, int size) {
        runs.requireActive(context.runId(), context.ownerToken());
        var value = required(mapper.find(id));
        requireCaseAccess(context, value);
        return detail(value, evidencePage, proposalPage, size);
    }
    public AgentPage<AutonomyCaseEntity> listForUser(String agentId, AgentScope scope, String user, int page, int size) {
        humanScope(agentId, scope, user);
        return list(scope, sources(user, agentId), page, size);
    }
    public AutonomyCaseDetailResponse detailForUser(String agentId, String id, String user, int evidencePage, int proposalPage, int size) {
        return detail(requireCaseForUser(agentId, id, user), evidencePage, proposalPage, size);
    }
    public AutonomyCaseEntity requireCaseForUser(String agentId, String id, String user) {
        var value = required(mapper.find(id));
        humanScope(agentId, new AgentScope(value.businessLineCode(), value.environmentCode()), user);
        if (!sources(user, agentId).contains(value.sourceType())) throw new AccessDeniedException("无权查看该问题的来源证据");
        if (mapper.countBugLinks(id) > 0) bugPermission(user, agentId);
        return value;
    }

    private AgentPage<AutonomyCaseEntity> list(AgentScope scope, List<String> sources, int page, int size) {
        int offset = offset(page, size, 50);
        if (sources.isEmpty()) return new AgentPage<>(0, List.of());
        return new AgentPage<>(mapper.count(scope.businessLineCode(), scope.environmentCode(), sources),
                mapper.list(scope.businessLineCode(), scope.environmentCode(), sources, offset, size));
    }
    private AutonomyCaseDetailResponse detail(AutonomyCaseEntity value, int evidencePage, int proposalPage, int size) {
        int evidenceOffset = offset(evidencePage, size, 10), proposalOffset = offset(proposalPage, size, 10);
        return new AutonomyCaseDetailResponse(value,
                new AgentPage<>(mapper.countSignals(value.id()), mapper.signals(value.id(), evidenceOffset, size)),
                new AgentPage<>(mapper.countProposals(value.id()), mapper.proposals(value.id(), proposalOffset, size)),
                mapper.relatedRuns(value.id()), mapper.countRuns(value.id()), bugContext(value));
    }
    private AutonomyBugContext bugContext(AutonomyCaseEntity problem) {
        var links = mapper.currentBugLinks(problem.id());
        long changed = 0, unavailable = 0;
        for (var link : links) {
            String state = bugSourceState(problem, link);
            if ("CHANGED".equals(state)) changed++;
            else if ("UNAVAILABLE".equals(state)) unavailable++;
        }
        return new AutonomyBugContext(mapper.countBugLinks(problem.id()), links.size(), changed, unavailable);
    }
    private List<String> sources(String user, String agentId) {
        var requester = access.actor(user);
        var agent = agents.findAgent(agentId);
        access.requireAgent(requester, agent);
        var owner = access.actor(agent.ownerName());
        return SOURCE_PERMISSIONS.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(requester::has)
                && entry.getValue().stream().anyMatch(owner::has)).map(Map.Entry::getKey).sorted().toList();
    }
    private void humanScope(String id, AgentScope scope, String user) {
        var actor = access.actor(user); actor.require("autonomy:case:view"); actor.require("agent:view");
        var agent = agents.findAgent(id); access.requireAgent(actor, agent);
        var configuration = json.readValue(agent.configurationJson(), AgentConfiguration.class);
        if (!configuration.scopes().contains(scope)) throw new AccessDeniedException("问题超出该 Agent 当前授权范围");
    }
    private void scope(AgentToolContext context, String line, String environment, String source) {
        access.requireScope(context, new AgentScope(line, environment));
        if (!sources(context.requestedBy(), context.agentId()).contains(source)) throw new AccessDeniedException("缺少原监测来源权限");
    }
    /** 交付清单引用问题时继续校验原监测来源权限，问题领取不永久冻结授权。 */
    public void requireCaseAccess(AgentToolContext context, AutonomyCaseEntity value) {
        scope(context, value.businessLineCode(), value.environmentCode(), value.sourceType());
        if (mapper.countBugLinks(value.id()) > 0) bugPermission(context.requestedBy(), context.agentId());
    }
    private void active(AgentToolContext context) {
        agents.lockRun(context.runId()); runs.requireActive(context.runId(), context.ownerToken());
    }
    private void link(String caseId, String runId, String relation) {
        if (mapper.linked(caseId, runId) == 0) mapper.link(caseId, runId, relation, now());
    }
    private AutonomyCaseEntity required(AutonomyCaseEntity value) {
        if (value == null) throw new IllegalArgumentException("问题不存在");
        return value;
    }
    private void requireVersion(AutonomyCaseEntity value, long version) {
        if (version < 0 || value.version() != version) throw new IllegalArgumentException("问题处理版本已变化，请重新读取后分析");
    }
    private static int offset(int page, int size, int maxSize) {
        if (page < 1 || page > 10000 || size < 1 || size > maxSize) throw new IllegalArgumentException("分页参数超出范围");
        return (page - 1) * size;
    }
    private String digest(Object value) { return AutonomyEvidence.digest(json.writeValueAsString(value)); }
    private static void changed(int count) { if (count != 1) throw new IllegalStateException("问题处理版本已变化"); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private record ProposalRequest(long caseVersion, AutonomyProposal proposal) { }
}
