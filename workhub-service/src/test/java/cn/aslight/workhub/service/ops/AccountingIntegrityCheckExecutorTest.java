package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountingIntegrityCheckExecutorTest {
    private final FunctionCallService calls = mock(FunctionCallService.class);
    private final AccountingIntegrityCheckExecutor executor =
            new AccountingIntegrityCheckExecutor(calls, mock(AccountingRunMapper.class));
    private final AccountingMonitorConfigEntity config = mock(AccountingMonitorConfigEntity.class);
    private final AccountingRunEntity run = mock(AccountingRunEntity.class);
    private final AccountingIntegrityCatalog.Family family = new AccountingIntegrityCatalog.Family(
            "test", "BL000005", "核验", "测试", 2, "bounds", "summary", "sample",
            List.of(new AccountingIntegrityCatalog.Check("TOTAL", "金额", "ERROR", "金额差异")));

    private void setup() {
        when(config.businessLineCode()).thenReturn("BL000005");
        when(config.environmentCode()).thenReturn("PROD");
        when(run.id()).thenReturn(1L);
        when(calls.monitorStatement(anyLong(), anyString(), anyString(), anyMap())).thenAnswer(inv ->
                FunctionCallService.Statement.of(inv.getArgument(2), calls.render(inv.getArgument(2), inv.getArgument(3))));
        when(calls.render(anyString(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void freezesBoundsBatchesPreciselyAndKeepsDecimalDifferences() {
        setup();
        List<Map<String, String>> parameters = new ArrayList<>();
        when(calls.render(eq("AI_TEST_SUMMARY"), anyMap())).thenAnswer(inv -> {
            parameters.add(Map.copyOf(inv.getArgument(1)));
            return "summary";
        });
        when(calls.executeStatements(anyString(), anyString(), anyList())).thenAnswer(inv -> {
            List<FunctionCallService.Statement> statements = inv.getArgument(2);
            String code = statements.getFirst().sqlCode();
            return List.of(List.of(code.endsWith("BOUNDS") ? Map.of("min_id", 1, "max_id", 5)
                    : code.endsWith("SAMPLE") ? Map.of("id", parameters.size(), "delta", "0.01")
                    : Map.of("checked_count", 1, "TOTAL_n", 1, "TOTAL_diff", "0.01")));
        });
        var result = executor.executeFamily(config, run, family).getFirst();
        assertEquals(List.of("1", "3", "5"), parameters.stream().map(p -> p.get("startId")).toList());
        assertEquals(List.of("2", "4", "5"), parameters.stream().map(p -> p.get("endId")).toList());
        assertTrue(parameters.stream().allMatch(p -> "NULL".equals(p.get("startDateTime"))));
        assertEquals("ERROR", result.status());
        assertEquals(3, result.anomalyCount());
        assertEquals(new BigDecimal("0.03"), result.differenceAmount());
        assertTrue(result.message().contains("高水位 5，已完成至 5"));
    }

    @Test
    void partialQueryFailureNeverPasses() {
        setup();
        AtomicInteger queries = new AtomicInteger();
        when(calls.executeStatements(anyString(), anyString(), anyList())).thenAnswer(inv -> {
            int step = queries.incrementAndGet();
            if (step == 1) return List.of(List.of(Map.of("min_id", 1, "max_id", 5)));
            if (step == 2) return List.of(List.of(Map.of("checked_count", 2, "TOTAL_n", 0, "TOTAL_diff", 0)));
            throw new IllegalStateException("查询超时");
        });
        var result = executor.executeFamily(config, run, family).getFirst();
        assertEquals("FAILED", result.status());
        assertTrue(result.message().contains("已完成至 2"));
        assertTrue(result.message().contains("查询超时"));
    }

    @Test
    void emptySourceIsSkippedAndMalformedCountsFail() {
        setup();
        when(calls.executeStatements(anyString(), anyString(), anyList()))
                .thenReturn(List.of(List.of(Map.of("min_id", 0, "max_id", 0))));
        assertEquals("SKIPPED", executor.executeFamily(config, run, family).getFirst().status());
        assertThrows(IllegalArgumentException.class, () -> AccountingIntegrityCheckExecutor.integer(Map.of(), "n"));
        assertThrows(ArithmeticException.class, () -> AccountingIntegrityCheckExecutor.integer(Map.of("n", "1.2"), "n"));
        assertThrows(IllegalArgumentException.class, () -> AccountingIntegrityCheckExecutor.decimal(Map.of("n", -1), "n"));
    }

    @Test
    void catalogHasBoundedAuditableTemplatesAndUniqueCodes() {
        var seeds = AccountingIntegrityCatalog.seeds();
        assertEquals(seeds.size(), seeds.stream().map(FuncCallSqlSeeds.Seed::sqlCode).distinct().count());
        assertFalse(AccountingIntegrityCatalog.families("BL000005").isEmpty());
        assertFalse(AccountingIntegrityCatalog.families("BL000008").isEmpty());
        for (var seed : seeds) {
            assertTrue(seed.sqlCode().length() <= 64);
            assertTrue(seed.sqlTemplate().contains("LIMIT"));
            if (!seed.sqlCode().endsWith("BOUNDS")) {
                assertTrue(seed.sqlTemplate().contains("p.id BETWEEN :startId AND :endId"));
            }
        }
    }
}
