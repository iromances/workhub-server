package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentScheduleRequest;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AgentScheduleTest {
    @Test
    void storesLocalScheduleAsUtcAndDoesNotRepeatTriggerAtAnchor() {
        var schedule = new AgentScheduleRequest(null, "CRON", "0 30 6 * * *", "Asia/Shanghai", "RUN_ONCE", true);
        AgentSchedule.validate(schedule);
        assertEquals(LocalDateTime.parse("2026-11-04T22:30:00"), AgentSchedule.next(schedule, Instant.parse("2026-11-03T22:30:00Z")));
    }

    @Test
    void daylightSavingGapUsesNextRealOccurrence() {
        var schedule = new AgentScheduleRequest(null, "CRON", "0 30 2 * * *", "America/New_York", "RUN_ONCE", true);
        assertEquals(LocalDateTime.parse("2026-03-09T06:30:00"), AgentSchedule.next(schedule, Instant.parse("2026-03-08T06:00:00Z")));
    }

    @Test
    void rejectsInvalidZoneAndInconsistentManualSchedule() {
        assertThrows(IllegalArgumentException.class, () -> AgentSchedule.validate(new AgentScheduleRequest(null, "CRON", "0 * * * * *", "missing", "SKIP", true)));
        assertThrows(IllegalArgumentException.class, () -> AgentSchedule.validate(new AgentScheduleRequest(null, "MANUAL", null, "UTC", "SKIP", true)));
        assertThrows(IllegalArgumentException.class, () -> AgentSchedule.validate(new AgentScheduleRequest(null, "CRON", "invalid", "UTC", "SKIP", true)));
    }
}
