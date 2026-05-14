package cn.aslight.workhub.service.intake;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChinaWorkdayCalendarTest {

    private final ChinaWorkdayCalendar calendar = new ChinaWorkdayCalendar();

    @Test
    void isWorkday_shouldRespect2026HolidayAndAdjustedWorkday() {
        assertFalse(calendar.isWorkday(LocalDate.of(2026, 5, 1)));
        assertTrue(calendar.isWorkday(LocalDate.of(2026, 5, 9)));
    }

    @Test
    void estimateFinishDate_shouldSkipWeekendAndHoliday() {
        assertEquals("2026/05/06", calendar.estimateFinishDate(LocalDate.of(2026, 4, 30), 16));
    }
}
