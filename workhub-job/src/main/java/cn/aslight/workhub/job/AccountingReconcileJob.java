package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.AccountingMonitorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "workhub.ops.accounting",
        name = "enabled",
        havingValue = "true"
)
public class AccountingReconcileJob {

    private final AccountingMonitorService accountingMonitorService;

    public AccountingReconcileJob(AccountingMonitorService accountingMonitorService) {
        this.accountingMonitorService = accountingMonitorService;
    }

    @Scheduled(
            cron = "${workhub.ops.accounting.cron:0 30 6 * * *}",
            zone = "${workhub.ops.accounting.zone:Asia/Shanghai}"
    )
    public void reconcilePreviousDay() {
        accountingMonitorService.submitPreviousDay();
    }
}
