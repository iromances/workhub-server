package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentCodeMapper;
import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.AgentCodeOperationEntity;
import cn.aslight.workhub.model.agent.AgentCodeWorkspaceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** 仅登记已生成的不可变对象；事务内不启动进程或访问外部系统。 */
@Service
public class AgentCodeStore {
    private final AgentCodeMapper mapper;
    private final AgentMapper agents;
    private final AgentRunStore runs;
    private final ObjectMapper json;

    public AgentCodeStore(AgentCodeMapper mapper, AgentMapper agents, AgentRunStore runs, ObjectMapper json) {
        this.mapper = mapper; this.agents = agents; this.runs = runs; this.json = json;
    }

    @Transactional
    public AgentCodeWorkspaceEntity reserve(AgentToolContext context, AgentCodePolicy.Snapshot policy,
                                            AgentCodePolicy.Repository repo, String baseSha, String host) {
        active(context);
        var existing = mapper.findByRepository(context.runId(), repo.id());
        if (existing != null) {
            if (!existing.baseSha().equals(baseSha) || !existing.policyHash().equals(policy.hash()) || !existing.hostId().equals(host)) {
                throw new IllegalArgumentException("本次运行已固定该仓库基线或执行策略，请查看原工作区或重新分派");
            }
            return existing;
        }
        if (mapper.list(context.runId()).size() >= 20) throw new IllegalArgumentException("单次运行最多关联20个仓库");
        String id = UUID.randomUUID().toString();
        var workspace = new AgentCodeWorkspaceEntity(id, context.runId(), repo.id(), policy.hash(), host,
                repo.baseBranch(), baseSha, baseSha, "codex/agent-" + id, "PREPARING", 0, now(), now());
        mapper.insert(workspace);
        return workspace;
    }

    @Transactional
    public AgentCodeWorkspaceEntity ready(AgentToolContext context, String id) {
        active(context);
        var workspace = require(context, id);
        if ("PREPARING".equals(workspace.status())) changed(mapper.ready(id, now()));
        return mapper.find(id);
    }

    @Transactional
    public Map<String, Object> revise(AgentToolContext context, AgentCodeWorkspaceEntity expected, String key,
                                      String requestHash, String candidateSha, String path, String blob) {
        active(context);
        var workspace = require(context, expected.id());
        var existing = mapper.operation(workspace.id(), key);
        if (existing != null) return previous(existing, requestHash);
        if (workspace.version() != expected.version() || !workspace.candidateSha().equals(expected.candidateSha())) {
            throw new IllegalStateException("工作区版本已变化，请重新读取文件后修改");
        }
        changed(mapper.revise(workspace.id(), expected.version(), candidateSha, now()));
        Map<String, Object> result = Map.of("workspaceId", workspace.id(), "repositoryId", workspace.repositoryId(),
                "version", workspace.version() + 1, "candidateSha", candidateSha, "path", path,
                "blob", blob, "tested", false, "delivered", false);
        mapper.record(new AgentCodeOperationEntity(workspace.id(), key, requestHash, json.writeValueAsString(result), now()));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> previous(AgentCodeOperationEntity operation, String hash) {
        if (!operation.requestHash().equals(hash)) throw new IllegalArgumentException("代码操作标识已被不同内容使用");
        return json.readValue(operation.resultJson(), Map.class);
    }

    private AgentCodeWorkspaceEntity require(AgentToolContext context, String id) {
        var workspace = mapper.lock(id);
        if (workspace == null || !workspace.runId().equals(context.runId())) throw new IllegalArgumentException("工作区不属于本次运行");
        return workspace;
    }

    private void active(AgentToolContext context) {
        agents.lockRun(context.runId());
        runs.requireActive(context.runId(), context.ownerToken());
    }
    private static void changed(int count) { if (count != 1) throw new IllegalStateException("代码工作区版本已变化"); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
