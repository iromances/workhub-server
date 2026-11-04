package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 外部写入前提交意图；网络调用不占用数据库锁，回执按版本登记。 */
@Service
public class AgentDeliveryStore {
    static final String TOOL = "change.publish";
    static final Set<String> PERMISSIONS = Set.of("agent:code:deliver");
    private final AgentDeliveryMapper mapper;
    private final AgentMapper agents;
    private final AgentRunStore runs;
    private final AgentChangeService changes;
    private final AgentGitLabPolicy policies;
    private final AgentAccessService access;
    private final ObjectMapper json;
    public AgentDeliveryStore(AgentDeliveryMapper mapper, AgentMapper agents, AgentRunStore runs, AgentChangeService changes,
                               AgentGitLabPolicy policies, AgentAccessService access, ObjectMapper json) {
        this.mapper = mapper; this.agents = agents; this.runs = runs; this.changes = changes; this.policies = policies; this.access = access; this.json = json;
    }

    @Transactional
    public AgentDeliveryEntity reserve(AgentToolContext context, String changeId, String repositoryId) {
        active(context); access.requireExecution(context, TOOL, PERMISSIONS);
        var value = changes.detail(context, changeId);
        var repo = value.manifest().repositories().stream().filter(item -> item.repositoryId().equals(repositoryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("仓库不属于候选清单"));
        access.requireScope(context, repo.scope());
        var binding = policies.binding(repositoryId);
        if (!repo.baseBranch().equals(binding.binding().targetBranch())) throw new IllegalArgumentException("GitLab 目标分支必须与清单基线一致");
        var prior = mapper.byRepository(changeId, repositoryId);
        if (prior != null) {
            if (!binding.hash().equals(prior.bindingHash())) throw new IllegalArgumentException("远端交付绑定已变化，旧请求未重新发送");
            return prior;
        }
        if (!value.current()) throw new IllegalArgumentException("候选清单已失效，不能开始新的远端交付");
        if (mapper.unresolved(context.runId(), repositoryId) > 0) throw new IllegalArgumentException("该仓库旧版本仍有未知写入，请先核对原请求，不能换版本重发");
        String branch = repo.changedPaths().isEmpty() ? repo.baseBranch() : repo.sourceBranch() + "-r" + value.revision();
        AgentCodePolicy.branch(branch);
        var record = new AgentDeliveryEntity(UUID.randomUUID().toString(), changeId, context.runId(), repositoryId,
                json.writeValueAsString(binding), binding.hash(), branch, repo.baseBranch(), repo.resultSha(), "NEW", null, null, null, null, 0, now(), now());
        mapper.insert(record); journal(record); return record;
    }

    @Transactional
    public AgentDeliveryEntity intent(AgentToolContext context, AgentDeliveryEntity expected, String next) {
        active(context); access.requireExecution(context, TOOL, PERMISSIONS);
        var change = changes.detail(context, expected.changeSetId());
        if (!change.current()) throw new IllegalArgumentException("候选清单已变化，未发起新的远端写入");
        policies.requireCurrent(json.readValue(expected.bindingJson(), AgentGitLabPolicy.Snapshot.class));
        if (!("NEW".equals(expected.status()) && "PUSHING".equals(next)
                || "BRANCH_READY".equals(expected.status()) && "MR_CREATING".equals(next))) throw new IllegalArgumentException("交付写入意图不符合当前阶段");
        return update(context, expected, next, null, null, null, null);
    }

    @Transactional
    public AgentDeliveryEntity receipt(AgentToolContext context, AgentDeliveryEntity expected, String status,
                                        Long iid, String url, String error, LocalDateTime verified) {
        active(context);
        if (!Set.of("NEW", "BRANCH_READY", "PUSH_UNKNOWN", "MR_UNKNOWN", "REMOTE_CONFIRMED", "UNCHANGED_CONFIRMED", "CONFLICT", "REJECTED").contains(status)) {
            throw new IllegalArgumentException("交付回执状态非法");
        }
        return update(context, expected, status, iid, url, error, verified);
    }
    private AgentDeliveryEntity update(AgentToolContext context, AgentDeliveryEntity expected, String status, Long iid, String url, String error, LocalDateTime verified) {
        if (!expected.runId().equals(context.runId())) throw new IllegalArgumentException("交付请求不属于本次运行");
        if (mapper.update(expected.id(), expected.version(), status, iid, url, error, verified, now()) != 1) throw new IllegalStateException("交付请求已被其他执行更新，请读取已有结果");
        var saved = mapper.find(expected.id()); journal(saved); return saved;
    }
    private void journal(AgentDeliveryEntity value) {
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), value.runId(), "delivery:" + value.id() + ":" + value.version(),
                agents.nextEventOrdinal(value.runId()), "CODE_DELIVERY", json.writeValueAsString(new AgentToolData(json).sanitize(AgentDeliveryService.view(value))), "agent-runtime", now()));
    }
    private void active(AgentToolContext context) { agents.lockRun(context.runId()); runs.requireActive(context.runId(), context.ownerToken()); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
