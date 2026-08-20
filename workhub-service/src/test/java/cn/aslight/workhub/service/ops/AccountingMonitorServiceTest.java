package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingManualRunRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingMonitorServiceTest {

    private AccountingMonitorMapper monitorMapper;
    private AccountingRunMapper runMapper;
    private AccountingMonitorService service;

    @BeforeEach
    void setUp() {
        monitorMapper = mock(AccountingMonitorMapper.class);
        runMapper = mock(AccountingRunMapper.class);
        service = new AccountingMonitorService(
                monitorMapper,
                runMapper,
                new AccountingRuleCatalog(),
                mock(AccountingReconcileExecutor.class),
                new SyncTaskExecutor()
        );
        when(monitorMapper.findById(10L)).thenReturn(new AccountingMonitorConfigEntity(
                10L, "BL000003", "嘉泰消费分期", "prod", "jiatai-amp-saps",
                "嘉泰消费分期账务", "jiatai-amp-db-prod", "jiatai_amp_saps",
                "JIATAI_AMP_SAPS", true, false, null, null, null
        ));
    }

    @Test
    void shouldRejectManualWindowThatIsNotAlignedToHour() {
        AccountingManualRunRequest request = new AccountingManualRunRequest(
                10L,
                LocalDateTime.of(2026, 7, 22, 10, 30),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                List.of("TX_INVALID_AMOUNT"),
                "request-1"
        );

        assertThrows(IllegalArgumentException.class, () -> service.submitManual(request, "tester"));
        verify(runMapper, never()).insertRun(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void shouldRejectManualRangeLongerThanThirtyOneDays() {
        AccountingManualRunRequest request = new AccountingManualRunRequest(
                10L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 7, 3, 0, 0),
                null,
                "request-2"
        );

        assertThrows(IllegalArgumentException.class, () -> service.submitManual(request, "tester"));
    }

    @Test
    void shouldMarkRunFailedWhenAccountingQueueRejectsTask() {
        AccountingMonitorService rejectedService = new AccountingMonitorService(
                monitorMapper,
                runMapper,
                new AccountingRuleCatalog(),
                mock(AccountingReconcileExecutor.class),
                task -> {
                    throw new TaskRejectedException("queue full");
                }
        );
        when(runMapper.findIdByRunNo(org.mockito.ArgumentMatchers.anyString())).thenReturn(99L);

        AccountingManualRunRequest request = new AccountingManualRunRequest(
                10L,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                List.of("TX_INVALID_AMOUNT"),
                "request-queue-full"
        );

        rejectedService.submitManual(request, "tester");

        verify(runMapper).finishRun(
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("FAILED"),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.contains("队列已满")
        );
    }
}
