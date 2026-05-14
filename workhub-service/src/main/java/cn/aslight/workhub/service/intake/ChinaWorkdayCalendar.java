package cn.aslight.workhub.service.intake;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 中国工作日历。
 *
 * <p>当前内置 2026 年国务院办公厅公布的法定节假日和调休工作日；
 * 未配置年份按普通周末规则降级计算。</p>
 */
@Component
public class ChinaWorkdayCalendar {

    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final Set<LocalDate> HOLIDAYS_2026 = Set.of(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 2, 15),
            LocalDate.of(2026, 2, 16),
            LocalDate.of(2026, 2, 17),
            LocalDate.of(2026, 2, 18),
            LocalDate.of(2026, 2, 19),
            LocalDate.of(2026, 2, 20),
            LocalDate.of(2026, 2, 21),
            LocalDate.of(2026, 2, 22),
            LocalDate.of(2026, 2, 23),
            LocalDate.of(2026, 4, 4),
            LocalDate.of(2026, 4, 5),
            LocalDate.of(2026, 4, 6),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 2),
            LocalDate.of(2026, 5, 3),
            LocalDate.of(2026, 5, 4),
            LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 6, 19),
            LocalDate.of(2026, 6, 20),
            LocalDate.of(2026, 6, 21),
            LocalDate.of(2026, 9, 25),
            LocalDate.of(2026, 9, 26),
            LocalDate.of(2026, 9, 27),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 4),
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 7)
    );

    private static final Set<LocalDate> ADJUSTED_WORKDAYS_2026 = Set.of(
            LocalDate.of(2026, 1, 4),
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 5, 9),
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 10, 10)
    );

    public String estimateFinishDate(LocalDate startDate, int effortHours) {
        if (effortHours <= 0) {
            return null;
        }
        int remainingDays = Math.max(1, (int) Math.ceil(effortHours / 8.0));
        LocalDate cursor = startDate == null ? LocalDate.now() : startDate;
        while (true) {
            if (isWorkday(cursor)) {
                remainingDays--;
                if (remainingDays == 0) {
                    return cursor.format(DISPLAY_DATE_FORMATTER);
                }
            }
            cursor = cursor.plusDays(1);
        }
    }

    public boolean isWorkday(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (date.getYear() == 2026) {
            if (ADJUSTED_WORKDAYS_2026.contains(date)) {
                return true;
            }
            if (HOLIDAYS_2026.contains(date)) {
                return false;
            }
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
