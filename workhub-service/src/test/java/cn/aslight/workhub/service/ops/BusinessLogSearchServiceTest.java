package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.security.SensitiveDataMasker;
import cn.aslight.workhub.model.ops.BusinessLogSearchRequest;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertScopeResponse;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessLogSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Test
    void shouldSearchSelectedScopeAndMaskSensitiveLogContent() {
        SystemAlertScopeService scopeService = mock(SystemAlertScopeService.class);
        ElasticsearchSystemAlertClient client = mock(ElasticsearchSystemAlertClient.class);
        McpAuditLogger auditLogger = mock(McpAuditLogger.class);
        when(scopeService.list("BL000001", "prod", true)).thenReturn(List.of(selectedScope()));
        when(client.searchLogs(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(new ElasticsearchSystemAlertClient.LogSearchResult(List.of(new ElkSystemAlertLog(
                        "event-1", "scf-payment", "INFO", "BcmEventServer",
                        "phone=13800138000 password=plain Authorization: Bearer abc123 accountNo=6222021234567890 remote=10.20.30.40",
                        null, null, "trace-1", "req-1", Instant.parse("2026-08-22T23:58:00Z")
                )), false));
        BusinessLogSearchService service = new BusinessLogSearchService(
                scopeService, client, Clock.fixed(NOW, ZoneOffset.UTC), new SensitiveDataMasker(), auditLogger);

        var response = service.search(new BusinessLogSearchRequest(
                "BL000001", "prod", List.of("scf-payment"), List.of("INFO"),
                "2026-08-22T23:50:00Z", "2026-08-23T00:00:00Z",
                "查询当日交易明细", null, null, 20));

        assertEquals(1, response.count());
        assertTrue(response.dataMasked());
        String message = response.items().getFirst().message();
        assertTrue(message.contains("138****8000"));
        assertTrue(message.contains("password=******"));
        assertTrue(message.contains("Authorization: ******"));
        assertTrue(!message.contains("abc123"));
        assertTrue(message.contains("************7890"));
        assertTrue(message.contains("10.20.30.***"));
        verify(client).searchLogs(
                eq("scf-payment-*,scf-saps-*"), eq(List.of("scf-payment")), eq("prod"), eq(List.of("INFO")),
                eq(Instant.parse("2026-08-22T23:50:00Z")), eq(NOW), eq("查询当日交易明细"),
                eq(null), eq(null), eq(20));
        verify(auditLogger).record(any());
    }

    @Test
    void shouldRejectServiceOutsideSelectedScopeAndLongInfoRange() {
        SystemAlertScopeService scopeService = mock(SystemAlertScopeService.class);
        when(scopeService.list("BL000001", "prod", true)).thenReturn(List.of(selectedScope()));
        BusinessLogSearchService service = new BusinessLogSearchService(
                scopeService, mock(ElasticsearchSystemAlertClient.class), Clock.fixed(NOW, ZoneOffset.UTC),
                new SensitiveDataMasker(), mock(McpAuditLogger.class));

        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class, () -> service.search(
                new BusinessLogSearchRequest("BL000001", "prod", List.of("other-service"), List.of("INFO"),
                        "2026-08-22T23:50:00Z", "2026-08-23T00:00:00Z", null, null, null, 20)));
        assertTrue(denied.getMessage().contains("不在业务线ELK关注范围"));

        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class, () -> service.search(
                new BusinessLogSearchRequest("BL000001", "prod", List.of("scf-payment"), List.of("INFO"),
                        "2026-08-21T23:00:00Z", "2026-08-23T00:00:00Z", null, null, null, 20)));
        assertTrue(tooLong.getMessage().contains("24小时"));
    }

    private SystemAlertScopeResponse selectedScope() {
        return new SystemAlertScopeResponse(
                1L, "BL000001", "prod", "SELECTED",
                List.of(
                        new SystemAlertScopeServiceResponse(1L, "支付", "scf-payment", true),
                        new SystemAlertScopeServiceResponse(2L, "账务", "scf-saps", true),
                        new SystemAlertScopeServiceResponse(3L, "停用", "disabled", false)
                ),
                List.of("scf-payment-*", "scf-saps-*"), true, null, null, null);
    }
}
