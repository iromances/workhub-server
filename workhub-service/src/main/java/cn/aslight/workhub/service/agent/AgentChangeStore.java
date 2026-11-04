package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.autonomy.AutonomyCaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

/** 只做短事务的版本核对与不可变登记；不持锁读 Git、执行测试或访问 Jenkins。 */
@Service
public class AgentChangeStore {
    private final AgentChangeMapper mapper;
    private final AgentCodeMapper workspaces;
    private final AgentMapper agents;
    private final AutonomyMapper autonomy;
    private final AgentRunStore runs;
    private final AgentCodePolicy policies;
    private final AgentAccessService access;
    private final AutonomyCaseService cases;
    private final ObjectMapper json;

    public AgentChangeStore(AgentChangeMapper mapper, AgentCodeMapper workspaces, AgentMapper agents, AutonomyMapper autonomy,
                             AgentRunStore runs, AgentCodePolicy policies, AgentAccessService access, AutonomyCaseService cases, ObjectMapper json) {
        this.mapper = mapper; this.workspaces = workspaces; this.agents = agents; this.autonomy = autonomy;
        this.runs = runs; this.policies = policies; this.access = access; this.cases = cases; this.json = json;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentChangeSetEntity save(AgentToolContext context, String key, String requestHash, AgentChangeManifest manifest) {
        agents.lockRun(context.runId()); runs.requireActive(context.runId(), context.ownerToken());
        var existing = mapper.byRequest(context.runId(), key);
        if (existing != null) return previous(existing, requestHash);
        if (!manifest.runId().equals(context.runId()) || !manifest.taskId().equals(context.taskId())) throw new IllegalArgumentException("交付清单与本次任务不符");
        if (manifest.caseReference() == null && autonomy.activeCaseCount(context.runId()) > 0) throw new IllegalArgumentException("本次运行已领取巡检问题，交付清单必须关联对应的问题方案");
        if (manifest.caseReference() != null) {
            var reference = manifest.caseReference();
            var value = autonomy.lockCase(reference.caseId());
            if (value == null || value.version() != reference.caseVersion() || !context.runId().equals(value.activeRunId())
                    || !Objects.equals(value.latestProposalId(), reference.proposalId()) || !"ANALYZING".equals(value.status())) {
                throw new IllegalArgumentException("巡检问题或方案已变化，请重新整理交付清单");
            }
            cases.requireCaseAccess(context, value);
            cases.requireCurrentBugLinks(value);
            var proposal = autonomy.proposal(reference.proposalId());
            if (proposal == null || !proposal.caseId().equals(value.id()) || !AgentCodePolicy.hash(proposal.proposalJson()).equals(reference.proposalHash())) {
                throw new IllegalArgumentException("交付清单引用的方案不一致");
            }
        }
        var all = workspaces.list(context.runId());
        if (!all.stream().map(AgentCodeWorkspaceEntity::id).sorted().toList().equals(manifest.repositories().stream().map(AgentRepositoryChange::workspaceId).sorted().toList())) {
            throw new IllegalArgumentException("代码工作区集合已变化，请重新准备完整交付清单");
        }
        var policy = policies.current();
        for (var expected : manifest.repositories()) {
            var actual = workspaces.lock(expected.workspaceId());
            if (actual == null || !matches(actual, expected) || !policy.hash().equals(expected.policyHash())) {
                throw new IllegalArgumentException("代码工作区或授权策略已变化，清单未保存");
            }
            var repo = policy.require(actual.repositoryId());
            access.requireScope(context, repo.scope());
            if (!repo.scope().equals(expected.scope())) throw new IllegalArgumentException("仓库范围与清单不符");
        }
        long revision = mapper.lastRevision(context.runId()) + 1;
        if (revision > 100) throw new IllegalArgumentException("单次运行的交付清单已达100版，请分派后续任务");
        String encoded = json.writeValueAsString(manifest);
        if (encoded.length() > 1_000_000) throw new IllegalArgumentException("交付清单超过单次记录上限，请拆分任务");
        var saved = new AgentChangeSetEntity(UUID.randomUUID().toString(), context.runId(), context.taskId(), revision, key, requestHash,
                AgentCodePolicy.hash(encoded), encoded, LocalDateTime.now(ZoneOffset.UTC));
        mapper.insert(saved);
        return saved;
    }

    public AgentChangeSetEntity previous(AgentChangeSetEntity existing, String hash) {
        if (!existing.requestHash().equals(hash)) throw new IllegalArgumentException("交付请求标识已被不同内容使用");
        return existing;
    }
    static boolean matches(AgentCodeWorkspaceEntity actual, AgentRepositoryChange expected) {
        return "READY".equals(actual.status()) && actual.version() == expected.workspaceVersion()
                && actual.repositoryId().equals(expected.repositoryId()) && actual.policyHash().equals(expected.policyHash())
                && actual.baseBranch().equals(expected.baseBranch()) && actual.baseSha().equals(expected.baseSha())
                && actual.candidateSha().equals(expected.resultSha()) && actual.sourceBranch().equals(expected.sourceBranch());
    }
}
