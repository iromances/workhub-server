package cn.aslight.workhub.model.ops;

import java.util.Locale;

public enum SystemAlertEventCategory {
    SYSTEM_ERROR,
    SLOW_SQL;

    public static SystemAlertEventCategory parseNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("系统预警事件分类非法：" + value);
        }
    }
}
