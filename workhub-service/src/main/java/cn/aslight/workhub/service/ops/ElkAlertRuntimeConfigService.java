package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * 从系统配置生成 ELK 采集配置；未启用系统配置模式时兼容原环境变量。
 */
@Service
public class ElkAlertRuntimeConfigService {

    static final String CONFIG_GROUP = "elk.alert";
    private static final String AUTH_NONE = "NONE";
    private static final String AUTH_BASIC = "BASIC";
    private static final String AUTH_API_KEY = "API_KEY";

    private final ElkAlertProperties properties;
    private final SysConfigService sysConfigService;

    @Autowired
    public ElkAlertRuntimeConfigService(ElkAlertProperties properties, SysConfigService sysConfigService) {
        this.properties = properties;
        this.sysConfigService = sysConfigService;
    }

    ElkAlertRuntimeConfigService(ElkAlertProperties properties) {
        this(properties, null);
    }

    public ElkAlertRuntimeConfig current(String serviceName) {
        return current(serviceName, null);
    }

    public ElkAlertRuntimeConfig current(String serviceName, String subsystemIndexPattern) {
        Map<String, String> values = configuredValues();
        String configuredEnabled = configured(values, "enabled");
        if (configuredEnabled == null) {
            return legacyConfig(subsystemIndexPattern);
        }
        boolean enabled = booleanValue(configuredEnabled, "enabled");
        if (!enabled) {
            return disabledConfig();
        }

        String indexPattern = trim(subsystemIndexPattern);
        if (indexPattern == null) {
            indexPattern = configured(values, serviceIndexKey(serviceName));
        }
        if (indexPattern == null) {
            indexPattern = configured(values, "indexPattern");
        }
        if (indexPattern == null && trim(serviceName) != null) {
            throw new IllegalStateException("子系统未配置日志索引模式：" + serviceName);
        }
        String authType = required(values, "authType").toUpperCase(Locale.ROOT);
        return new ElkAlertRuntimeConfig(
                true,
                required(values, "baseUrl"),
                indexPattern,
                authType,
                configured(values, "apiKey"),
                configured(values, "username"),
                configured(values, "password"),
                positiveInt(values, "connectTimeoutSeconds", 5, 1, 120),
                positiveInt(values, "requestTimeoutSeconds", 30, 1, 300),
                positiveInt(values, "pageSize", 200, 1, 1_000),
                positiveInt(values, "maxEventsPerRun", 1_000, 1, 10_000),
                positiveInt(values, "initialLookbackMinutes", 5, 1, 10_080),
                defaultValue(configured(values, "pitKeepAlive"), "1m"),
                defaultValue(configured(values, "timestampField"), "@timestamp"),
                defaultValue(configured(values, "serviceQueryField"), "appName.keyword"),
                defaultValue(configured(values, "serviceSourceField"), "appName"),
                configured(values, "environmentQueryField"),
                defaultValue(configured(values, "levelQueryField"), "level.keyword"),
                defaultValue(configured(values, "levelSourceField"), "level"),
                defaultValue(configured(values, "errorLevel"), "ERROR"),
                defaultValue(configured(values, "messageSourceField"), "message"),
                defaultValue(configured(values, "loggerSourceField"), "logger_name"),
                configured(values, "errorTypeSourceField"),
                defaultValue(configured(values, "stackTraceSourceField"), "stack_trace"),
                defaultValue(configured(values, "traceIdSourceField"), "TID"),
                configured(values, "requestIdSourceField")
        );
    }

    private ElkAlertRuntimeConfig legacyConfig(String subsystemIndexPattern) {
        String authType = trim(properties.getApiKey()) != null
                ? AUTH_API_KEY
                : trim(properties.getUsername()) != null ? AUTH_BASIC : AUTH_NONE;
        return new ElkAlertRuntimeConfig(
                properties.isEnabled(),
                properties.getBaseUrl(),
                defaultValue(trim(subsystemIndexPattern), properties.getIndexPattern()),
                authType,
                properties.getApiKey(),
                properties.getUsername(),
                properties.getPassword(),
                properties.getConnectTimeoutSeconds(),
                properties.getRequestTimeoutSeconds(),
                properties.getPageSize(),
                1_000,
                properties.getInitialLookbackMinutes(),
                properties.getPitKeepAlive(),
                "@timestamp",
                "service.name",
                "service.name",
                "service.environment",
                "log.level",
                "log.level",
                "ERROR",
                "error.message",
                "log.logger",
                "error.type",
                "error.stack_trace",
                "trace.id",
                "http.request.id"
        );
    }

    private ElkAlertRuntimeConfig disabledConfig() {
        return new ElkAlertRuntimeConfig(
                false, null, null, AUTH_NONE, null, null, null,
                5, 30, 200, 1_000, 5, "1m", "@timestamp",
                "appName.keyword", "appName", null,
                "level.keyword", "level", "ERROR", "message",
                "logger_name", null, "stack_trace", "TID", null
        );
    }

    private String required(Map<String, String> values, String key) {
        String value = configured(values, key);
        if (value == null) {
            throw new IllegalStateException("ELK系统配置缺失：" + CONFIG_GROUP + "." + key);
        }
        return value;
    }

    private int positiveInt(Map<String, String> values, String key, int defaultValue, int min, int max) {
        String value = configured(values, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("ELK系统配置非法：" + CONFIG_GROUP + "." + key);
        }
    }

    private boolean booleanValue(String value, String key) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalStateException("ELK系统配置非法：" + CONFIG_GROUP + "." + key);
    }

    private Map<String, String> configuredValues() {
        return sysConfigService == null ? Map.of() : sysConfigService.findPlainValues(CONFIG_GROUP);
    }

    private String configured(Map<String, String> values, String key) {
        if (key == null) {
            return null;
        }
        return trim(values.get(key));
    }

    private String serviceIndexKey(String serviceName) {
        String normalized = trim(serviceName);
        return normalized == null ? null : "indexPattern." + normalized;
    }

    private String defaultValue(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
