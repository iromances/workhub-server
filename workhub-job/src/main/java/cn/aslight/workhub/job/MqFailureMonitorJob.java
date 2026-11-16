package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.MqFailureAlertService;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

/** MQ 失败巡检（REQ-9f3c2a84 起默认每 30 分钟，调度值以 sys_job_config 为准）。 */
@Component
public class MqFailureMonitorJob implements WorkhubJob {

    private final MqFailureAlertService mqFailureAlertService;

    public MqFailureMonitorJob(MqFailureAlertService mqFailureAlertService) {
        this.mqFailureAlertService = mqFailureAlertService;
    }

    @Override
    public String code() {
        return "OPS_MQ_FAILURE_MONITOR";
    }

    @Override
    public String name() {
        return "MQ失败巡检";
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
        mqFailureAlertService.scanAndAlert();
    }
}
