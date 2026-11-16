package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.AccountingMonitorService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 账务对账；未开启 workhub.ops.accounting.enabled 时不装配、不调度。 */
@Component
@ConditionalOnProperty(
        prefix = "workhub.ops.accounting",
        name = "enabled",
        havingValue = "true"
)
public class AccountingReconcileJob implements WorkhubJob {

    private final AccountingMonitorService accountingMonitorService;

    public AccountingReconcileJob(AccountingMonitorService accountingMonitorService) {
        this.accountingMonitorService = accountingMonitorService;
    }

    @Override
    public String code() {
        return "OPS_ACCOUNTING_RECONCILE";
    }

    @Override
    public String name() {
        return "账务每日对账";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.CRON;
    }

    @Override
    public String defaultScheduleValue() {
        return "0 30 6 * * *";
    }

    @Override
    public void run(String params) {
        accountingMonitorService.submitPreviousDay();
    }
}
