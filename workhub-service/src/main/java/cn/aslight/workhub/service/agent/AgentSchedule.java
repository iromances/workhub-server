package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentScheduleRequest;
import org.springframework.scheduling.support.CronExpression;
import java.time.*;

final class AgentSchedule {
    private AgentSchedule() { }

    static void validate(AgentScheduleRequest schedule) {
        try {
            ZoneId.of(schedule.zoneId());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("任务时区无效", ex);
        }
        if (!"SKIP".equals(schedule.misfirePolicy()) && !"RUN_ONCE".equals(schedule.misfirePolicy())) {
            throw new IllegalArgumentException("错过触发策略必须为 SKIP 或 RUN_ONCE");
        }
        if ("MANUAL".equals(schedule.mode())) {
            if (schedule.enabled() || (schedule.cronExpression() != null && !schedule.cronExpression().isBlank())) {
                throw new IllegalArgumentException("手动任务不能启用周期或携带 cron");
            }
            return;
        }
        if (!"CRON".equals(schedule.mode()) || !CronExpression.isValidExpression(schedule.cronExpression())) {
            throw new IllegalArgumentException("请输入有效的六字段 cron 表达式");
        }
    }

    static LocalDateTime next(AgentScheduleRequest schedule, Instant after) {
        if (!schedule.enabled()) return null;
        ZonedDateTime next = CronExpression.parse(schedule.cronExpression()).next(after.atZone(ZoneId.of(schedule.zoneId())));
        if (next == null) throw new IllegalArgumentException("周期表达式没有下一次执行时间");
        return LocalDateTime.ofInstant(next.toInstant(), ZoneOffset.UTC);
    }
}
