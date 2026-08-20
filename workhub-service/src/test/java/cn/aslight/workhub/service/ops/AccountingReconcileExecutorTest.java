package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingResultEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingReconcileExecutorTest {

    private AccountingMonitorMapper monitorMapper;
    private AccountingRunMapper runMapper;
    private AccountingDatabaseQueryClient queryClient;
    private AccountingReconcileExecutor executor;

    @BeforeEach
    void setUp() {
        monitorMapper = mock(AccountingMonitorMapper.class);
        runMapper = mock(AccountingRunMapper.class);
        queryClient = mock(AccountingDatabaseQueryClient.class);
        executor = new AccountingReconcileExecutor(
                monitorMapper,
                runMapper,
                new AccountingRuleCatalog(),
                queryClient,
                new ObjectMapper()
        );
        when(runMapper.markRunning(1L)).thenReturn(1);
        when(monitorMapper.findById(10L)).thenReturn(config("JIATAI_AMP_SAPS"));
    }

    @Test
    void shouldSkipDailyRuleForHourlyWindow() {
        when(runMapper.findById(1L)).thenReturn(run(
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                "PLAN_INVALID_AMOUNT"));
        when(queryClient.runRows(any(), anyString())).thenReturn(
                List.of(Map.of("table_name", "repayment_plan")));

        executor.execute(1L);

        ArgumentCaptor<AccountingResultEntity> captor = ArgumentCaptor.forClass(AccountingResultEntity.class);
        verify(runMapper).insertResult(captor.capture());
        assertEquals("SKIPPED", captor.getValue().status());
        assertTrue(captor.getValue().message().contains("非整日区间"));
        verify(runMapper).finishRun(eq(1L), eq("PARTIAL"), eq(1), eq(0), eq(0),
                eq(0), eq(1), eq(0), anyString());
    }

    @Test
    void shouldPersistMaskedSamplesAndWarningRunWhenAnomalyFound() {
        when(runMapper.findById(1L)).thenReturn(run(
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                "TX_INVALID_AMOUNT"));
        when(queryClient.runRows(any(), anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(1);
            if (sql.contains("information_schema.tables")) {
                return List.of(Map.of("table_name", "customer_transaction"));
            }
            if (sql.contains("COUNT(*) AS anomaly_count")) {
                return List.of(Map.of("anomaly_count", 2, "difference_amount", "12.50"));
            }
            return List.of(Map.of("business_key", "****1234", "actual_amount", "-12.50"));
        });

        executor.execute(1L);

        ArgumentCaptor<AccountingResultEntity> captor = ArgumentCaptor.forClass(AccountingResultEntity.class);
        verify(runMapper).insertResult(captor.capture());
        AccountingResultEntity result = captor.getValue();
        assertEquals("ERROR", result.status());
        assertEquals(2, result.anomalyCount());
        assertEquals("12.50", result.differenceAmount().toPlainString());
        assertTrue(result.sampleJson().contains("****1234"));
        verify(runMapper).finishRun(eq(1L), eq("WARNING"), eq(1), eq(0), eq(0),
                eq(1), eq(0), eq(2), eq(null));
    }

    @Test
    void shouldNotFallbackToAllRulesWhenEveryRuleOverrideIsDisabled() {
        when(runMapper.findById(1L)).thenReturn(run(
                LocalDateTime.of(2026, 7, 22, 0, 0),
                LocalDateTime.of(2026, 7, 23, 0, 0),
                null));
        when(monitorMapper.findEnabledRuleCodes(10L)).thenReturn(List.of());
        when(monitorMapper.countRuleConfigs(10L)).thenReturn(2);

        executor.execute(1L);

        verify(queryClient, never()).runRows(any(), anyString());
        verify(runMapper).finishRun(eq(1L), eq("FAILED"), eq(0), eq(0), eq(0),
                eq(0), eq(0), eq(0), anyString());
    }

    private AccountingMonitorConfigEntity config(String profile) {
        return new AccountingMonitorConfigEntity(
                10L, "BL000003", "嘉泰消费分期", "prod", "jiatai-amp-saps",
                "嘉泰消费分期账务", "jiatai-amp-db-prod", "jiatai_amp_saps",
                profile, true, false, null, null, null
        );
    }

    private AccountingRunEntity run(LocalDateTime start, LocalDateTime end, String ruleCodes) {
        return new AccountingRunEntity(
                1L, "AR20260722100000", "key", 10L,
                "BL000003", "嘉泰消费分期", "jiatai-amp-saps", "嘉泰消费分期账务",
                "MANUAL", start, end, ruleCodes, "RUNNING",
                0, 0, 0, 0, 0, 0, "tester",
                null, null, null, LocalDateTime.now()
        );
    }
}
