package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.ElkSystemAlertCollector;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

/** ELK 系统预警采集（原 ElkSystemAlertJob.collectErrors，REQ-9f3c2a84 拆分为独立调度入口）。 */
@Component
public class ElkAlertCollectJob implements WorkhubJob {

    private final ElkSystemAlertCollector collector;

    public ElkAlertCollectJob(ElkSystemAlertCollector collector) {
        this.collector = collector;
    }

    @Override
    public String code() {
        return "OPS_ELK_ALERT_COLLECT";
    }

    @Override
    public String name() {
        return "ELK系统预警采集";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.CRON;
    }

    @Override
    public String defaultScheduleValue() {
        return "0 * * * * *";
    }

    @Override
    public void run(String params) {
        collector.collect();
    }
}
