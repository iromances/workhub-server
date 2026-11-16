package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.XxlJobFailureAlertService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

/** XXL-JOB 失败巡检（REQ-9f3c2a84 起默认每 30 分钟，调度值以 sys_job_config 为准）。 */
@Component
public class XxlJobFailureMonitorJob implements WorkhubJob {

    private final XxlJobFailureAlertService xxlJobFailureAlertService;

    public XxlJobFailureMonitorJob(XxlJobFailureAlertService xxlJobFailureAlertService) {
        this.xxlJobFailureAlertService = xxlJobFailureAlertService;
    }

    @Override
    public String code() {
        return "OPS_XXL_JOB_FAILURE_MONITOR";
    }

    @Override
    public String name() {
        return "XXL-JOB失败巡检";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.CRON;
    }

    @Override
    public String defaultScheduleValue() {
        return "0 */30 * * * *";
    }

    @Override
    public void run(String params) {
        xxlJobFailureAlertService.scanAndAlert();
    }
}
