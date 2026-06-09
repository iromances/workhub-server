package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.XxlJobFailureAlertService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class XxlJobFailureMonitorJob {

    private final XxlJobFailureAlertService xxlJobFailureAlertService;

    public XxlJobFailureMonitorJob(XxlJobFailureAlertService xxlJobFailureAlertService) {
        this.xxlJobFailureAlertService = xxlJobFailureAlertService;
    }

    @Scheduled(cron = "${workhub.ops.xxl-job.failure-monitor.cron:0 */10 * * * *}")
    public void scanFailedJobs() {
        xxlJobFailureAlertService.scanAndAlert();
    }
}
