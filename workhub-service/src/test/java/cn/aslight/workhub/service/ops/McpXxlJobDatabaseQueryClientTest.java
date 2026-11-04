package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.mcp.db.DatabaseDiagnosticService;
import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.service.mcp.McpResourceService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 使用真实协议形状和查询服务构造桩，不连接数据库或 SSH。 */
class McpXxlJobDatabaseQueryClientTest {
    @Test void missingMalformedAndTruncatedBatchResultsCannotBecomeEmptySuccess() {
        for (var value : List.<Map<String,Object>>of(Map.of(), Map.of("rows",List.of()), Map.of("result",reply(List.of())),
                Map.of("truncatedByBytes",true,"rows",List.of()), Map.of("truncatedByBytes",false,"rows",List.of("not-a-row")),
                Map.of("truncatedByBytes",false,"rows",List.of(Map.of(1,"not-a-column"))))) {
            assertThrows(IllegalArgumentException.class, () -> McpXxlJobDatabaseQueryClient.rows(value));
        }
        assertThrows(IllegalArgumentException.class, () -> McpXxlJobDatabaseQueryClient.rows(null));
        assertEquals(List.of(),McpXxlJobDatabaseQueryClient.rows(reply(List.of())));
        assertEquals(1,McpXxlJobDatabaseQueryClient.rows(reply(List.of(Map.of("count",0)))).size());
    }
    @Test void wholeBatchUsesOneFixedTargetAndNamedReadonlyProfile() {
        var resources = mock(McpResourceService.class);
        when(resources.catalog()).thenReturn(catalog("first","TEST","readonly"),catalog("second","TEST","readonly"),catalog("second","PROD","readonly"));
        var client = new McpXxlJobDatabaseQueryClient(resources);
        try (var constructed = mockConstruction(DatabaseDiagnosticService.class, (service,context) ->
                when(service.runReadonlyQueries(anyString(),anyString(),anyList())).thenReturn(List.of(reply(List.of(Map.of("count",0))),reply(List.of()))))) {
            var batch = client.runVerifiedBatch("BL000012","TEST",List.of("SELECT 1","SELECT 2"));
            verify(resources,times(1)).catalog(); assertEquals(64,batch.targetHash().length());
            assertFalse(batch.finishedAt().isBefore(batch.startedAt())); assertEquals(2,batch.rows().size());
            verify(constructed.constructed().getFirst()).runReadonlyQueries("db","readonly",List.of("SELECT 1","SELECT 2"));
            var next = client.runVerifiedBatch("BL000012","TEST",List.of("SELECT 1","SELECT 2")); assertNotEquals(batch.targetHash(),next.targetHash());
            assertThrows(IllegalArgumentException.class,() -> client.runVerifiedBatch("BL000012","TEST",List.of("SELECT 1")));
            assertEquals(2,constructed.constructed().size());
        }
    }
    @Test void missingReadonlyProfileAndAmbiguousScopeCannotOpenQueryService() {
        var resources = mock(McpResourceService.class);
        var one = catalog("first","TEST","other");
        var duplicate = new McpCatalogResponse(List.of(),List.of(),List.of(one.databaseTargets().getFirst(),one.databaseTargets().getFirst()),List.of(),Map.of(),Map.of());
        when(resources.catalog()).thenReturn(one,duplicate);
        var client = new McpXxlJobDatabaseQueryClient(resources);
        try (var constructed = mockConstruction(DatabaseDiagnosticService.class)) {
            assertThrows(IllegalArgumentException.class,() -> client.runVerifiedBatch("BL000012","TEST",List.of("SELECT 1")));
            assertThrows(IllegalArgumentException.class,() -> client.runVerifiedBatch("BL000012","TEST",List.of("SELECT 1")));
            assertEquals(0,constructed.constructed().size());
        }
    }
    @Test void missingBatchMemberAndTruncatedMemberRejectTheWholeReceipt() {
        var resources = mock(McpResourceService.class); when(resources.catalog()).thenReturn(catalog("first","TEST","readonly"));
        var client = new McpXxlJobDatabaseQueryClient(resources);
        try (var ignored = mockConstruction(DatabaseDiagnosticService.class,(service,context) ->
                when(service.runReadonlyQueries(anyString(),anyString(),anyList())).thenReturn(List.of(reply(List.of()))))) {
            assertThrows(IllegalArgumentException.class,() -> client.runVerifiedBatch("BL000012","TEST",List.of("SELECT 1","SELECT 2")));
        }
        try (var ignored = mockConstruction(DatabaseDiagnosticService.class,(service,context) ->
                when(service.runReadonlyQueries(anyString(),anyString(),anyList())).thenReturn(List.of(reply(List.of()),Map.of("truncatedByBytes",true,"rows",List.of()))))) {
            assertThrows(IllegalArgumentException.class,() -> client.runVerifiedBatch("BL000012","TEST",List.of("SELECT 1","SELECT 2")));
        }
    }
    private static Map<String,Object> reply(List<?> rows) { return Map.of("truncatedByBytes",false,"rows",rows); }
    private McpCatalogResponse catalog(String host,String environment,String profile) {
        var target = new LinkedHashMap<String,Object>();
        target.put("key","db"); target.put("businessLineCodes",List.of("BL000012")); target.put("environmentCode",environment);
        target.put("host",host); target.put("port",3306); target.put("schema","fixture"); target.put("username","readonly"); target.put("password","fixture-only");
        target.put("profiles",List.of(Map.of("key",profile,"maxRows",100,"queryTimeoutSeconds",5,"maxResultBytes",65536)));
        return new McpCatalogResponse(List.of(),List.of(),List.of(target),List.of(),Map.of(),Map.of());
    }
}
