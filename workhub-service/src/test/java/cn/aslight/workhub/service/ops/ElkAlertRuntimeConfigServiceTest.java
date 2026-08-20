package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElkAlertRuntimeConfigServiceTest {

    @Test
    void shouldUseSystemConfigAndSubsystemMultipleIndices() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        Map<String, String> values = Map.ofEntries(
                Map.entry("enabled", "true"),
                Map.entry("baseUrl", "http://elasticsearch:9200"),
                Map.entry("indexPattern", "all-logs-*"),
                Map.entry("indexPattern.amp-saps", "amp-saps-*"),
                Map.entry("authType", "BASIC"),
                Map.entry("username", "reader"),
                Map.entry("password", "secret")
        );
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP)).thenReturn(values);

        ElkAlertRuntimeConfig config = new ElkAlertRuntimeConfigService(
                new ElkAlertProperties(), sysConfigService)
                .current("amp-saps", "amp-saps-*,amp-saps-history-*");

        assertTrue(config.enabled());
        assertEquals("amp-saps-*,amp-saps-history-*", config.indexPattern());
        assertEquals("appName.keyword", config.serviceQueryField());
        assertEquals("appName", config.serviceSourceField());
        assertEquals("level.keyword", config.levelQueryField());
        assertEquals("stack_trace", config.stackTraceSourceField());
        assertEquals("TID", config.traceIdSourceField());
        assertEquals(30, config.requestTimeoutSeconds());
    }

    @Test
    void shouldTreatSystemEnabledFalseAsAuthoritative() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP))
                .thenReturn(Map.of("enabled", "false"));

        ElkAlertRuntimeConfig config = new ElkAlertRuntimeConfigService(
                properties, sysConfigService).current(null);

        assertFalse(config.enabled());
    }

    @Test
    void shouldResolveCollectorSettingsWithoutGlobalIndexPattern() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP)).thenReturn(Map.of(
                "enabled", "true",
                "baseUrl", "http://elasticsearch:9200",
                "authType", "NONE"
        ));

        ElkAlertRuntimeConfig config = new ElkAlertRuntimeConfigService(
                new ElkAlertProperties(), sysConfigService).current(null);

        assertTrue(config.enabled());
        assertNull(config.indexPattern());
    }

    @Test
    void shouldFallbackToGlobalSystemIndexForHistoricalSubsystem() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP)).thenReturn(Map.of(
                "enabled", "true",
                "baseUrl", "http://elasticsearch:9200",
                "authType", "NONE",
                "indexPattern", "legacy-logs-*"
        ));

        ElkAlertRuntimeConfig config = new ElkAlertRuntimeConfigService(
                new ElkAlertProperties(), sysConfigService).current("legacy-service", null);

        assertEquals("legacy-logs-*", config.indexPattern());
    }

    @Test
    void shouldFallbackToLegacyEcsConfigurationWhenSystemSwitchIsMissing() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://legacy:9200");
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP)).thenReturn(Map.of());

        ElkAlertRuntimeConfig config = new ElkAlertRuntimeConfigService(
                properties, sysConfigService).current("asset-payment");

        assertTrue(config.enabled());
        assertEquals("service.name", config.serviceQueryField());
        assertEquals("service.environment", config.environmentQueryField());
        assertEquals("log.level", config.levelQueryField());
    }

    @Test
    void shouldRejectInvalidNumericConfig() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        Map<String, String> values = Map.of(
                "enabled", "true",
                "baseUrl", "http://elasticsearch:9200",
                "indexPattern", "amp-saps-*",
                "authType", "NONE",
                "pageSize", "1001"
        );
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP)).thenReturn(values);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ElkAlertRuntimeConfigService(
                        new ElkAlertProperties(), sysConfigService).current("amp-saps"));

        assertEquals("ELK系统配置非法：elk.alert.pageSize", error.getMessage());
    }
}
