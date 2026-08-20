package cn.aslight.workhub.job;

import cn.aslight.workhub.service.ops.ElkSystemAlertCollector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ElkSystemAlertJob {

    private final ElkSystemAlertCollector collector;

    public ElkSystemAlertJob(ElkSystemAlertCollector collector) {
        this.collector = collector;
    }

    @Scheduled(cron = "${workhub.ops.elk-alert.cron:0 * * * * *}")
    public void collectErrors() {
        collector.collect();
    }
}
