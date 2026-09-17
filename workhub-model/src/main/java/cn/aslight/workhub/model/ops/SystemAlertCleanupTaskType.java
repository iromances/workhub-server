package cn.aslight.workhub.model.ops;

import java.util.Locale;

public enum SystemAlertCleanupTaskType {
    DELETE,
    DELETE_EXACT_MESSAGE,
    DELETE_AND_FILTER;

    public static SystemAlertCleanupTaskType parse(String value) {
        if (value == null || value.isBlank()) {
            return DELETE_AND_FILTER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的系统预警清理任务类型: " + value);
        }
    }

    public boolean createsFilterRule() {
        return this == DELETE_AND_FILTER;
    }

    public boolean exactMessageMatch() {
        return this == DELETE_EXACT_MESSAGE;
    }
}
