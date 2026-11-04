package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.*;
import java.util.*;

/** 人工批准与出站登记同事务；网络调用在事务外，过期执行者不能更新回执。 */
@Service
public class AgentDeploymentStore {
    public static final int LEASE_SECONDS = 120;
    private static final Set<String> ACTIVE = Set.of("PREPARED", "SUBMITTING", "QUEUED", "BUILDING", "DEPLOYING", "UNKNOWN");
    private final AgentDeploymentMapper mapper;
    private final AgentMapper agents;
    private final AgentDeploymentApproval approval;
    private final AgentReleasePlanGate plans;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired public AgentDeploymentStore(AgentDeploymentMapper mapper, AgentMapper agents, AgentDeploymentApproval approval, AgentReleasePlanGate plans, ObjectMapper json) {
        this(mapper, agents, approval, plans, json, Clock.systemUTC());
    }
    AgentDeploymentStore(AgentDeploymentMapper mapper, AgentMapper agents, AgentDeploymentApproval approval, AgentReleasePlanGate plans, ObjectMapper json, Clock clock) {
        this.mapper = mapper; this.agents = agents; this.approval = approval; this.plans = plans; this.json = json; this.clock = clock;
    }

    /** 与整组人工审核在同一事务登记，任何一步失败都会撤销全部登记。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public AgentDeploymentEntity enqueue(String approvalId) {
        mapper.lockDispatcher();
        var accepted = mapper.approval(approvalId);
        if (accepted == null) throw new IllegalArgumentException("人工审核不存在");
        var prior = mapper.find(accepted.requestId());
        if (prior != null) {
            if (!prior.approvalId().equals(accepted.id())) throw new IllegalArgumentException("发布请求标识已被使用");
            return prior;
        }
        agents.lockRun(accepted.runId());
        String resourceOwner = plans.resourceOwner(accepted);
        var binding = approval.validate(accepted);
        var keys = resources(binding);
        for (String key : keys) if (mapper.resourceOwner(key) != null && !resourceOwner.equals(mapper.resourceOwner(key))) throw new IllegalStateException("同一 Job、系统环境或仓库仍有在途发布，请先核对原请求");
        var now = now();
        var value = new AgentDeploymentEntity(accepted.requestId(), accepted.id(), accepted.runId(), accepted.changeSetId(), accepted.manifestHash(),
                accepted.bindingJson(), accepted.bindingHash(), accepted.parametersJson(), accepted.parametersHash(), "PREPARED", null, null, null, null, null, null, null, now, 0, now, now);
        mapper.insert(value); for (String key : keys) if (mapper.resourceOwner(key) == null) mapper.acquire(key, resourceOwner, now);
        journal(value); return value;
    }

    @Transactional
    public AgentDeploymentEntity claim(String id) {
        mapper.lockDispatcher(); var row = mapper.lock(id); var now = now();
        if (row == null || !ACTIVE.contains(row.status()) || row.nextCheckAt().isAfter(now)
                || row.ownerToken() != null && row.leaseUntil().isAfter(now)) return null;
        if (mapper.countOwned(now) >= 4) return null;
        if ("PREPARED".equals(row.status()) && !plans.mayStart(row, now)) return null;
        changed(mapper.claim(id, row.version(), UUID.randomUUID().toString(), now.plusSeconds(LEASE_SECONDS), now));
        return mapper.find(id);
    }

    @Transactional
    public AgentDeploymentEntity intent(AgentDeploymentEntity expected) {
        mapper.lockDispatcher(); var value = owned(expected); agents.lockRun(value.runId());
        if (!plans.mayStart(value, now())) throw new IllegalStateException("整组发布已停止或前置步骤尚未成功");
        approval.validate(mapper.approval(value.approvalId()));
        owned(value); // 材料复核可能读取仓库；复核结束后租约仍须有效。
        changed(mapper.intent(value.id(), value.version(), value.ownerToken(), now()));
        var saved = mapper.find(value.id()); journal(saved); return saved;
    }

    @Transactional
    public AgentDeploymentEntity finish(AgentDeploymentEntity expected, String state, Long queue, Long build,
            String result, String receipt, String error) {
        mapper.lockDispatcher(); var row = owned(expected);
        if (!Set.of("PREPARED", "QUEUED", "BUILDING", "DEPLOYING", "UNKNOWN", "SUCCEEDED", "FAILED", "ABORTED", "REJECTED", "BLOCKED").contains(state)) throw new IllegalArgumentException("发布回执状态非法");
        if (Set.of("PREPARED", "BLOCKED").contains(state) && !"PREPARED".equals(row.status()) || "REJECTED".equals(state) && !"SUBMITTING".equals(row.status())) throw new IllegalArgumentException("不能把已发出的未知发布标成未发送");
        if (row.queueId() != null && queue != null && !row.queueId().equals(queue) || row.buildNumber() != null && build != null && !row.buildNumber().equals(build)) throw new IllegalArgumentException("不能替换已固定的 Jenkins 句柄");
        queue = queue == null ? row.queueId() : queue; build = build == null ? row.buildNumber() : build;
        if (Set.of("SUCCEEDED", "FAILED").contains(state) && build == null) throw new IllegalArgumentException("发布终态必须有实际构建编号");
        if ("SUCCEEDED".equals(state) && (receipt == null || !"SUCCESS".equals(result))) throw new IllegalArgumentException("发布成功必须有实际版本回执及成功构建");
        changed(mapper.finish(row.id(), row.version(), row.ownerToken(), state, queue, build, result, receipt, error,
                now().plusSeconds("UNKNOWN".equals(state) ? 60 : 15), now()));
        var saved = mapper.find(row.id()); journal(saved); plans.finished(saved, now()); return saved;
    }

    private AgentDeploymentEntity owned(AgentDeploymentEntity expected) {
        var row = mapper.lock(expected.id());
        if (row == null || row.version() != expected.version() || expected.ownerToken() == null || !expected.ownerToken().equals(row.ownerToken())
                || row.leaseUntil() == null || !row.leaseUntil().isAfter(now())) throw new LeaseLost();
        return row;
    }
    private void journal(AgentDeploymentEntity row) {
        agents.lockRun(row.runId());
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), row.runId(), "deployment:" + row.id() + ":" + row.version(),
                agents.nextEventOrdinal(row.runId()), "DEPLOYMENT", json.writeValueAsString(AgentDeploymentDispatcher.view(row)), "release-runtime", now()));
    }
    static SortedSet<String> resources(AgentJenkinsPolicy.BindingSnapshot target) {
        var binding = target.binding(); var keys = new TreeSet<String>();
        var job = new AgentJenkinsClient.Target(URI.create(target.instance().baseUrl()), binding.jobSegments(), "identity", "identity").jobUri("");
        int port = job.getPort();
        if (port == 80 && "http".equals(job.getScheme()) || port == 443 && "https".equals(job.getScheme())) port = -1;
        try { job = new URI(job.getScheme(), null, job.getHost().toLowerCase(Locale.ROOT), port, job.getPath(), null, null); }
        catch (java.net.URISyntaxException ex) { throw new IllegalArgumentException("Jenkins Job 身份无法规范化", ex); }
        keys.add(AgentCodePolicy.hash("job:" + job));
        keys.add(AgentCodePolicy.hash("system:" + binding.scope().businessLineCode() + ":" + binding.scope().environmentCode() + ":" + binding.systemName()));
        binding.commitParameters().keySet().forEach(repo -> keys.add(AgentCodePolicy.hash("repository:" + binding.scope().businessLineCode() + ":" + binding.scope().environmentCode() + ":" + repo)));
        return keys;
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static void changed(int count) { if (count != 1) throw new IllegalStateException("发布记录已变化"); }
    public static final class LeaseLost extends IllegalStateException { LeaseLost() { super("发布执行租约或版本已失效，请读取原请求"); } }
}
