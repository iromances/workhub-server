package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMonitorRunMapper;
import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.model.ops.AccountingManualRunRequest;
import cn.aslight.workhub.service.ops.AccountingMonitorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

/** 身份来自执行器持有的租约，而非模型参数；关联与提交同事务，失联后只重读。 */
@Service
public class AgentMonitorSubmissionService {
    private final AgentRunStore store;
    private final AgentAccessService access;
    private final AccountingMonitorMapper configs;
    private final AccountingMonitorService monitors;
    private final AgentMonitorRunMapper links;

    public AgentMonitorSubmissionService(AgentRunStore store, AgentAccessService access, AccountingMonitorMapper configs,
                                        AccountingMonitorService monitors, AgentMonitorRunMapper links) {
        this.store = store; this.access = access; this.configs = configs; this.monitors = monitors; this.links = links;
    }

    @Transactional
    public Long submit(String runId, String owner, Long configId) {
        var context = store.lockActiveContext(runId, owner);
        if (!"EXECUTE_CONFIGURED".equals(context.specification().monitorMode())
                || !context.specification().monitorConfigIds().contains(configId))
            throw new IllegalArgumentException("该场景不在本轮明确执行范围内");
        access.requireExecution(context, "monitor.scenario.execute", Set.of("ops:accounting:run", "ops:accounting:manage"));
        if (configs.lockConfig(configId) == null) throw new IllegalArgumentException("业务监测场景不存在");
        var config = configs.findById(configId);
        access.requireScope(context, new AgentScope(config.businessLineCode(), config.environmentCode()));
        Long saved = links.find(runId, configId);
        if (saved != null) return saved;
        var result = monitors.submitManual(new AccountingManualRunRequest(configId,
                context.specification().monitorCheckDate(), "agent:" + runId + ":" + configId), context.requestedBy());
        links.insert(runId, configId, result.id());
        return result.id();
    }
}
