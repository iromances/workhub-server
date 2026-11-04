package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.mcp.db.DatabaseDiagnosticService;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.service.mcp.McpResourceService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class McpAccountingDatabaseQueryClientTest {
    @Test void missingMalformedAndTruncatedResponsesAreNotEmptySuccess() {
        for (var response : List.<Map<String, Object>>of(Map.of(), Map.of("result", Map.of()), Map.of("result", Map.of("rows", List.of())),
                Map.of("result", Map.of("truncatedByBytes", true, "rows", List.of())),
                Map.of("result", Map.of("truncatedByBytes", false, "rows", List.of("not-a-row"))))) {
            assertThrows(IllegalArgumentException.class, () -> McpAccountingDatabaseQueryClient.rows(response));
        }
        assertThrows(IllegalArgumentException.class, () -> McpAccountingDatabaseQueryClient.rows(null));
        assertEquals(List.of(), McpAccountingDatabaseQueryClient.rows(reply(List.of())));
        assertEquals(1, McpAccountingDatabaseQueryClient.rows(reply(List.of(Map.of("anomaly_count", 0)))).size());
    }

    @Test void sessionPinsCatalogAndTargetForAllQueriesAndChecksScopeBeforeCreatingQueryService() {
        var resources = mock(McpResourceService.class);
        when(resources.catalog()).thenReturn(catalog("first-host", "TEST"), catalog("second-host", "TEST"), catalog("second-host", "PROD"));
        var client = new McpAccountingDatabaseQueryClient(resources);
        try (var constructed = mockConstruction(DatabaseDiagnosticService.class, (service, context) ->
                when(service.runReadonlyQuery(anyString(), anyString(), anyString())).thenReturn(reply(List.of(Map.of("value", 1)))))) {
            var first = client.open(config());
            String hash = first.targetHash();
            first.runRows("SELECT 1"); first.runRows("SELECT 2");
            verify(resources, times(1)).catalog();
            assertEquals(64, hash.length()); assertEquals(hash, first.targetHash());
            assertEquals(1, constructed.constructed().size());
            verify(constructed.constructed().getFirst(), times(2)).runReadonlyQuery(eq("db-test"), eq("readonly"), anyString());
            assertNotEquals(hash, client.open(config()).targetHash());
            assertThrows(IllegalArgumentException.class, () -> client.open(config()));
            assertEquals(2, constructed.constructed().size(), "环境不符不得创建查询会话");
        }
    }

    private static Map<String, Object> reply(List<?> rows) { return Map.of("result", Map.of("truncatedByBytes", false, "rows", rows)); }
    private cn.aslight.workhub.model.mcp.McpCatalogResponse catalog(String host, String environment) {
        var target = new LinkedHashMap<String, Object>();
        target.put("key", "db-test"); target.put("businessLineCodes", List.of("BL000012")); target.put("environmentCode", environment);
        target.put("host", host); target.put("port", 3306); target.put("schema", "fixture"); target.put("username", "readonly"); target.put("password", "fixture-secret");
        target.put("profiles", List.of(Map.of("key", "readonly", "maxRows", 100, "queryTimeoutSeconds", 5, "maxResultBytes", 65536)));
        return new cn.aslight.workhub.model.mcp.McpCatalogResponse(List.of(), List.of(), List.of(target), List.of(), Map.of(), Map.of());
    }
    private AccountingMonitorConfigEntity config() {
        return new AccountingMonitorConfigEntity(1L, "BL000012", "工作台", "TEST", "system", "监测", "db-test", "fixture", "COMMON_SAPS", true, false, null, null, null);
    }
}
