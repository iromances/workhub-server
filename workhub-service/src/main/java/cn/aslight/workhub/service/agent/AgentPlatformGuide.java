package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;

/** 平台数据指南：库表坐标等数据路由信息由平台统一维护并注入每轮运行，职责与任务描述只写业务语义。 */
@Service
public class AgentPlatformGuide {
    public static final String CONFIG_GROUP = "agent.runtime";
    public static final String CONFIG_KEY = "dataGuide";
    private static final String FALLBACK = "（未配置平台数据指南：可用 list_mcp_targets 与 get_business_line_context 发现数据库目标与上下文；"
            + "结论必须说明数据来源受限，不得虚构查询。）";

    private final SysConfigService configs;
    private cn.aslight.workhub.service.ops.AccountingMonitorService monitors;
    @org.springframework.beans.factory.annotation.Autowired
    public void setMonitors(cn.aslight.workhub.service.ops.AccountingMonitorService service) { monitors = service; }
    static final String MONITOR_GUIDE = """
            【现行运维口径，覆盖旧指南中的名称和旧规则表描述】
            系统预警：系统异常与慢 SQL；服务监测：XXL-JOB、MQ 等（ops_monitor_config）；
            业务监测：场景配置 ops_account_monitor_config、批次 ops_account_reconcile_run、规则结果 ops_account_reconcile_result。
            ACCOUNTING_INTEGRITY 使用内置规则目录及 func_call_sql 的 AI_* 模板，不能用旧 ops_account_rule_config 为空判定无规则。
            一致性场景仅手动或明确任务执行，不按 check_time 自动调度；未运行不能推断调度中断；告警关闭不等于未核验。
            本轮 BUSINESS_MONITOR_SUBMITTED/RESULT 事件含正式批次及实际规则结果，必须引用该批次、核验范围和时间。
            OBSERVE 仅观察已有结果，不得声称执行了 SQL；EXECUTE_CONFIGURED 由平台先执行明确选择的场景，再撰写报告。
            场景全量与系统预警增量窗口不同，必须分别说明；FAILED、未执行、SKIPPED 不得判正常。规则异常数量及差额可能重叠，不跨规则相加为资金损失。
            """;


    public AgentPlatformGuide(SysConfigService configs) {
        this.configs = configs;
    }

    public String guide(AgentToolContext context) {
        String configured = configs.findPlainValue(CONFIG_GROUP, CONFIG_KEY);
        String guide = (configured == null || configured.isBlank() ? FALLBACK : configured.strip()) + "\n" + MONITOR_GUIDE;
        if (monitors != null) {
            for (var scope : context.specification().scopes()) {
                for (var config : monitors.listConfigs(scope.businessLineCode(), false)) {
                    if (!scope.environmentCode().equals(config.environmentCode())) continue;
                    var families = cn.aslight.workhub.service.ops.AccountingIntegrityCatalog.families(scope.businessLineCode());
                    boolean integrity = "ACCOUNTING_INTEGRITY".equals(config.scenarioType());
                    guide += "\n业务监测配置 " + config.id() + " / " + config.displayName() + " / " + config.environmentCode()
                            + " / 类型=" + config.scenarioType() + " / 启用=" + config.enabled()
                            + (integrity ? " / 仅手动核验 / 内置规则=" + families.stream().mapToInt(f -> f.checks().size()).sum() : " / 流程计数场景");
                }
            }
        }
        return guide;
    }
}
