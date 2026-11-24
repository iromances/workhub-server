package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.AccountingMonitorService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

/**
 * REQ-d227e09d：账务场景核对——扫描核对时间已到且当日未核对的启用配置。
 * 默认停用（种子 enabled=0），在任务管理页显式开启后按 cron 生效；
 * 单配置核对窗口为其核对日 00:00 至次日 00:00，幂等键防当日重复核对。
 */
@Component
public class AccountingScenarioCheckJob implements WorkhubJob {

    private final AccountingMonitorService accountingMonitorService;

    public AccountingScenarioCheckJob(AccountingMonitorService accountingMonitorService) {
        this.accountingMonitorService = accountingMonitorService;
    }

    @Override
    public String code() {
        return "OPS_ACCOUNTING_SCENARIO_CHECK";
    }

    @Override
    public String name() {
        return "账务场景核对";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.CRON;
    }

    @Override
    public String defaultScheduleValue() {
        return "0 */10 * * * *";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public void run(String params) {
        accountingMonitorService.submitDueScheduled();
    }
}
