package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.autonomy.AutonomyCaseService;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** 多仓库代码、说明和可选问题方案的共同版本；尚无实测/远端交付凭据时不能进入发布审核。 */
@Service
public class AgentChangeService {
    private final AgentChangeMapper mapper;
    private final AgentCodeMapper workspaces;
    private final AgentMapper agents;
    private final AutonomyMapper autonomy;
    private final AgentChangeStore store;
    private final AgentCodeWorkspaceService code;
    private final AgentCodePolicy policies;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final AutonomyCaseService cases;
    private final AgentToolData data;
    private final ObjectMapper json;
    private final Validator validator;
    private final AgentDeliveryMapper deliveries;
    private final AgentGitLabPolicy gitLab;

    public AgentChangeService(AgentChangeMapper mapper, AgentCodeMapper workspaces, AgentMapper agents, AutonomyMapper autonomy,
                               AgentChangeStore store, AgentCodeWorkspaceService code, AgentCodePolicy policies, AgentRunStore runs,
                               AgentAccessService access, AutonomyCaseService cases, AgentToolData data, ObjectMapper json, Validator validator,
                               AgentDeliveryMapper deliveries, AgentGitLabPolicy gitLab) {
        this.mapper = mapper; this.workspaces = workspaces; this.agents = agents; this.autonomy = autonomy;
        this.store = store; this.code = code; this.policies = policies; this.runs = runs; this.access = access; this.cases = cases;
        this.data = data; this.json = json; this.validator = validator;
        this.deliveries = deliveries; this.gitLab = gitLab;
    }

    public AgentChangeView prepare(AgentToolContext context, String key, String caseId, AgentChangePlan plan) {
        runs.requireActive(context.runId(), context.ownerToken());
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("交付请求标识非法");
        if (plan == null || !validator.validate(plan).isEmpty()) throw new IllegalArgumentException("请填写有效的变更说明、影响、验收、回退和仓库依赖");
        var ordered = new AgentChangePlan(plan.summary(), plan.impact(), plan.acceptance(), plan.rollback(), plan.dependencies().stream()
                .sorted(Comparator.comparing(AgentChangePlan.Dependency::repositoryId).thenComparing(AgentChangePlan.Dependency::dependsOn)).toList());
        String requestHash = AgentCodePolicy.hash(json.writeValueAsString(new Request(caseId, ordered)));
        var prior = mapper.byRequest(context.runId(), key);
        if (prior != null) return detail(context, store.previous(prior, requestHash).id());
        if (caseId == null && autonomy.activeCaseCount(context.runId()) > 0) throw new IllegalArgumentException("本次运行已领取巡检问题，请指定 caseId");
        var safe = data.sanitize(ordered);
        if (safe.path("truncated").asBoolean() || json.writeValueAsString(safe).length() > 40_000) throw new IllegalArgumentException("交付说明过长，请精简");
        var safePlan = json.treeToValue(safe, AgentChangePlan.class);
        var current = workspaces.list(context.runId());
        if (current.isEmpty() || current.size() > 20) throw new IllegalArgumentException("交付清单需要本次运行的1到20个实际工作区");
        var changes = current.stream().map(workspace -> code.snapshot(context, workspace.id())).sorted(Comparator.comparing(AgentRepositoryChange::repositoryId)).toList();
        if (changes.stream().allMatch(change -> change.changedPaths().isEmpty())) throw new IllegalArgumentException("没有实际代码差异，不能生成代码交付清单");
        var order = dependencyOrder(changes.stream().map(AgentRepositoryChange::repositoryId).toList(), safePlan.dependencies());
        AgentChangeManifest.CaseReference reference = null;
        if (caseId != null) {
            var value = autonomy.find(caseId);
            if (value == null || !context.runId().equals(value.activeRunId()) || value.latestProposalId() == null || !"ANALYZING".equals(value.status())) {
                throw new IllegalArgumentException("请先领取该问题并形成分析方案");
            }
            cases.requireCaseAccess(context, value);
            cases.requireCurrentBugLinks(value);
            var proposal = autonomy.proposal(value.latestProposalId());
            if (proposal == null || !value.id().equals(proposal.caseId())) throw new IllegalArgumentException("问题缺少对应的已保存方案");
            reference = new AgentChangeManifest.CaseReference(caseId, value.version(), proposal.id(), AgentCodePolicy.hash(proposal.proposalJson()));
        }
        var safeTask = data.sanitize(context.specification());
        if (safeTask.path("truncated").asBoolean()) throw new IllegalArgumentException("任务上下文过长，无法完整形成交付快照");
        var task = json.treeToValue(safeTask, AgentTaskSpecification.class);
        var manifest = new AgentChangeManifest(1, context.runId(), context.taskId(), task, safePlan, reference, changes, order);
        return detail(context, store.save(context, key, requestHash, manifest).id());
    }

    public AgentChangeView detail(AgentToolContext context, String id) {
        runs.requireActive(context.runId(), context.ownerToken());
        var value = required(id);
        if (!value.runId().equals(context.runId())) throw new IllegalArgumentException("交付清单不属于本次运行");
        var manifest = manifest(value);
        requireVisible(context, manifest, false);
        return view(value, manifest);
    }
    /** 完成验收不消耗行动预算；运行锁由调用方持有，关联问题在同事务内固定。 */
    AgentChangeView forAcceptance(AgentToolContext context, String id) {
        access.actor(context.requestedBy()).require("agent:code:use");
        access.actor(agents.findAgent(context.agentId()).ownerName()).require("agent:code:use");
        var value = required(id);
        var manifest = manifest(value);
        if (manifest.caseReference() != null) autonomy.lockCase(manifest.caseReference().caseId());
        requireVisible(context, manifest, false);
        for (var repo : manifest.repositories()) access.requireScope(context, repo.scope());
        return view(value, manifest);
    }
    public AgentPage<Map<String, Object>> list(AgentToolContext context, int page) {
        runs.requireActive(context.runId(), context.ownerToken());
        return new AgentPage<>(mapper.count(context.runId()), mapper.list(context.runId(), offset(page), 20).stream()
                .map(value -> summary(detail(context, value.id()))).toList());
    }
    public AgentChangeView detailForUser(String id, String user) {
        var value = required(id);
        var context = humanContext(value.runId(), user);
        var manifest = manifest(value); requireVisible(context, manifest, true);
        return view(value, manifest);
    }
    public AgentPage<Map<String, Object>> listForUser(String runId, String user, int page) {
        humanContext(runId, user);
        return new AgentPage<>(mapper.count(runId), mapper.list(runId, offset(page), 20).stream().map(value -> summary(detailForUser(value.id(), user))).toList());
    }

    private AgentToolContext humanContext(String runId, String user) {
        var actor = access.actor(user); actor.require("agent:task:view"); actor.require("agent:code:use");
        var run = runs.get(runId); access.requireAgent(actor, agents.findAgent(run.agentId()));
        var context = runs.context(run);
        // 只读历史无需运行仍活跃；当前 Agent 范围、文件范围和来源权限仍必须保留。
        return new AgentToolContext(context.runId(), context.taskId(), context.agentId(), user, context.ownerToken(), context.configuration(), context.specification());
    }
    private void requireVisible(AgentToolContext context, AgentChangeManifest manifest, boolean human) {
        var policy = policies.current();
        var agent = agents.findAgent(context.agentId());
        var configuration = json.readValue(agent.configurationJson(), AgentConfiguration.class);
        for (var change : manifest.repositories()) {
            var repo = policy.require(change.repositoryId());
            if (!repo.scope().equals(change.scope()) || !configuration.scopes().contains(change.scope())
                    || !context.configuration().scopes().contains(change.scope()) || !context.specification().scopes().contains(change.scope())) {
                throw new AccessDeniedException("交付内容超出当前 Agent 或任务授权范围");
            }
            for (String path : change.changedPaths()) if (AgentCodePolicy.sensitive(path) || !AgentCodePolicy.within(path, repo.readablePaths())) {
                throw new AccessDeniedException("交付内容的文件范围授权已撤销");
            }
        }
        if (manifest.caseReference() != null) {
            var value = autonomy.find(manifest.caseReference().caseId());
            if (value == null) throw new IllegalArgumentException("交付清单关联的问题不存在");
            if (human) cases.requireCaseForUser(context.agentId(), value.id(), context.requestedBy());
            else cases.requireCaseAccess(context, value);
        }
    }

    private AgentChangeView view(AgentChangeSetEntity value, AgentChangeManifest manifest) {
        var blockers = new ArrayList<String>();
        if (mapper.lastRevision(value.runId()) != value.revision()) blockers.add("NEWER_MANIFEST_EXISTS");
        var policy = policies.current();
        var current = workspaces.list(value.runId());
        if (!current.stream().map(AgentCodeWorkspaceEntity::id).sorted().toList().equals(manifest.repositories().stream().map(AgentRepositoryChange::workspaceId).sorted().toList())) blockers.add("WORKSPACE_SET_CHANGED");
        for (var repo : manifest.repositories()) {
            var workspace = current.stream().filter(item -> item.id().equals(repo.workspaceId())).findFirst().orElse(null);
            if (workspace == null || !AgentChangeStore.matches(workspace, repo)) blockers.add("CODE_CHANGED:" + repo.repositoryId());
            if (!policy.hash().equals(repo.policyHash())) blockers.add("CODE_POLICY_CHANGED:" + repo.repositoryId());
        }
        if (manifest.caseReference() != null) {
            var reference = manifest.caseReference(); var problem = autonomy.find(reference.caseId());
            if (!cases.matchesReference(problem,value,reference)) blockers.add("CASE_PLAN_CHANGED");
            if (problem != null && cases.bugContextChanged(problem)) blockers.add("BUG_CONTEXT_CHANGED");
        } else if (autonomy.activeCaseCount(value.runId()) > 0) blockers.add("CASE_REFERENCE_REQUIRED");
        boolean currentVersion = blockers.isEmpty();
        blockers.add("PLATFORM_TEST_EVIDENCE_MISSING");
        var remote = deliveries.list(value.id());
        boolean allDelivered = remote.size() == manifest.repositories().size();
        for (var repo : manifest.repositories()) {
            var receipt = remote.stream().filter(item -> item.repositoryId().equals(repo.repositoryId())).findFirst().orElse(null);
            if (receipt == null || !AgentDeliveryService.confirmed(receipt) || !repo.resultSha().equals(receipt.resultSha())) { allDelivered = false; continue; }
            try { if (!gitLab.binding(repo.repositoryId()).hash().equals(receipt.bindingHash())) allDelivered = false; }
            catch (AgentCodePolicy.Unavailable ex) { allDelivered = false; }
        }
        if (!allDelivered) blockers.add("REMOTE_DELIVERY_EVIDENCE_MISSING");
        return new AgentChangeView(value.id(), value.runId(), value.taskId(), value.revision(), value.manifestHash(), value.createdAt(), currentVersion, false, List.copyOf(blockers), manifest);
    }

    public static Map<String, Object> summary(AgentChangeView value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", value.id()); result.put("runId", value.runId()); result.put("revision", value.revision()); result.put("manifestHash", value.manifestHash());
        result.put("createdAt", value.createdAt()); result.put("current", value.current()); result.put("readyForReview", value.readyForReview()); result.put("blockers", value.blockers());
        result.put("summary", value.manifest().plan().summary()); result.put("caseReference", value.manifest().caseReference()); result.put("dependencyOrder", value.manifest().dependencyOrder());
        result.put("repositories", value.manifest().repositories().stream().map(repo -> Map.of("repositoryId", repo.repositoryId(), "name", repo.repositoryName(),
                "workspaceVersion", repo.workspaceVersion(), "baseSha", repo.baseSha(), "resultSha", repo.resultSha(), "plannedBranch", repo.sourceBranch(), "changedFileCount", repo.changedPaths().size())).toList());
        return result;
    }
    private AgentChangeSetEntity required(String id) {
        var value = mapper.find(id); if (value == null) throw new IllegalArgumentException("交付清单不存在"); return value;
    }
    private AgentChangeManifest manifest(AgentChangeSetEntity value) { return json.readValue(value.manifestJson(), AgentChangeManifest.class); }
    private static int offset(int page) { if (page < 1 || page > 5) throw new IllegalArgumentException("清单页码超出范围"); return (page - 1) * 20; }

    static List<String> dependencyOrder(List<String> repositories, List<AgentChangePlan.Dependency> dependencies) {
        var pending = new TreeMap<String, Set<String>>(); repositories.forEach(id -> pending.put(id, new TreeSet<>()));
        for (var edge : dependencies) {
            if (!pending.containsKey(edge.repositoryId()) || !pending.containsKey(edge.dependsOn()) || edge.repositoryId().equals(edge.dependsOn())
                    || !pending.get(edge.repositoryId()).add(edge.dependsOn())) throw new IllegalArgumentException("仓库依赖不存在、重复或依赖自身");
        }
        var result = new ArrayList<String>();
        while (!pending.isEmpty()) {
            String next = pending.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).findFirst().orElse(null);
            if (next == null) throw new IllegalArgumentException("仓库依赖存在循环");
            result.add(next); pending.remove(next); pending.values().forEach(edges -> edges.remove(next));
        }
        return List.copyOf(result);
    }
    private record Request(String caseId, AgentChangePlan plan) { }
}
