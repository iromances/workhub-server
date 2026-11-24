package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.service.ops.AccountingMonitorService;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Set;

/** 在 AI 撰写报告前取得真实场景批次；所有 AI 通道使用同一执行链。 */
@Service
public class AgentMonitorExecutionService {
    private final AgentRunStore store;
    private final AgentMonitorSubmissionService submissions;
    private final AccountingMonitorService monitors;

    public AgentMonitorExecutionService(AgentRunStore store, AgentMonitorSubmissionService submissions, AccountingMonitorService monitors) {
        this.store = store; this.submissions = submissions; this.monitors = monitors;
    }

    public void prepare(String runId, String owner) {
        var spec = store.context(store.get(runId)).specification();
        if (!"EXECUTE_CONFIGURED".equals(spec.monitorMode())) return;
        for (Long configId : spec.monitorConfigIds()) {
            Long monitorId = submissions.submit(runId, owner, configId);
            store.record(runId, owner, "monitor:" + configId + ":submitted", "BUSINESS_MONITOR_SUBMITTED",
                    Map.of("configId", configId, "monitorRunId", monitorId));
            while (true) {
                store.requireActive(runId, owner);
                var detail = monitors.detail(monitorId);
                if (!Set.of("PENDING", "RUNNING").contains(detail.run().status())) {
                    var rules = detail.results().stream().map(r -> Map.of(
                            "ruleCode", r.ruleCode(), "ruleName", r.ruleName(), "status", r.status(),
                            "anomalyCount", r.anomalyCount(), "differenceAmount", r.differenceAmount(),
                            "message", r.message() == null ? "" : r.message())).toList();
                    store.record(runId, owner, "monitor:" + configId + ":result", "BUSINESS_MONITOR_RESULT",
                            Map.of("configId", configId, "monitorRunId", monitorId, "run", detail.run(), "rules", rules,
                                    "reportInstruction", "业务监测按本批次实际规则结果报告，FAILED/未执行/无样本不能判为正常；规则间差额不相加。"));
                    break;
                }
                try { Thread.sleep(2000); }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AgentRunStore.StopRequested("等待业务监测批次已中断；批次 " + monitorId + " 独立执行，关联已保存，不重复提交");
                }
            }
        }
    }
}
