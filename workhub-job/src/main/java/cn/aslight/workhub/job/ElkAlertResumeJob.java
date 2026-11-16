package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.ElkSystemAlertCollector;
import cn.aslight.workhub.service.system.WorkhubJob;
import org.springframework.stereotype.Component;

/** ELK 续读：仅续读已有区间，避免每分钟调度间隔耗尽默认 PIT 有效期（原 ElkSystemAlertJob.resumeIncompleteCollections）。 */
@Component
public class ElkAlertResumeJob implements WorkhubJob {

    private final ElkSystemAlertCollector collector;

    public ElkAlertResumeJob(ElkSystemAlertCollector collector) {
        this.collector = collector;
    }

    @Override
    public String code() {
        return "OPS_ELK_ALERT_RESUME";
    }

    @Override
    public String name() {
        return "ELK预警续读";
    }

    @Override
    public ScheduleType scheduleType() {
        return ScheduleType.FIXED_DELAY;
    }

    @Override
    public String defaultScheduleValue() {
        return "10000";
    }

    @Override
    public String paramDescription() {
        return "无参数；手动触发即续读一次未完成区间";
    }

    @Override
    public void run(String params) {
        collector.resume();
    }
}
