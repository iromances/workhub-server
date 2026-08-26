package cn.aslight.workhub.model.ops;

import java.util.Locale;

public enum SystemAlertWatchMode {
    ALL,
    SELECTED;

    public static SystemAlertWatchMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("关注范围不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的关注范围：" + value);
        }
    }
}
