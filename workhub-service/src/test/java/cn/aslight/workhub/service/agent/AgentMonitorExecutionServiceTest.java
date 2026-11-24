package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.service.ops.AccountingMonitorService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentMonitorExecutionServiceTest {
    private final AgentRunStore store = mock(AgentRunStore.class);
    private final AgentMonitorSubmissionService submissions = mock(AgentMonitorSubmissionService.class);
    private final AccountingMonitorService monitors = mock(AccountingMonitorService.class);
    private final AgentMonitorExecutionService service = new AgentMonitorExecutionService(store, submissions, monitors);

    private void setup(String mode) {
        var spec = new AgentTaskSpecification("核验", null, "", List.of("REPORT"), List.of(), List.of(),
                "FULL", null, mode, "OBSERVE".equals(mode) ? List.of() : List.of(15L), null);
        var run = mock(AgentRunEntity.class);
        when(store.get("run")).thenReturn(run);
        when(store.context(run)).thenReturn(new AgentToolContext("run", "task", "agent", "admin", "lease", null, spec));
        when(submissions.submit("run", "lease", 15L)).thenReturn(4L);
    }

    @Test void observationDoesNotExecuteScenario() {
        setup("OBSERVE");
        service.prepare("run", "lease");
        verifyNoInteractions(submissions, monitors);
    }

    @Test void partialResultKeepsFailedRulesAndAmountsInReportEvidence() {
        setup("EXECUTE_CONFIGURED");
        var run = mock(AccountingRunEntity.class);
        when(run.status()).thenReturn("PARTIAL");
        var rule = new AccountingResultEntity(1L, 4L, "rule", "规则", "单据", "SCENARIO", "ERROR",
                "FAILED", 4, new BigDecimal("150000.00"), "[]", "未完成", 10L, null);
        when(monitors.detail(4L)).thenReturn(new AccountingRunDetailResponse(run, List.of(rule)));
        service.prepare("run", "lease");
        verify(store).record(eq("run"), eq("lease"), eq("monitor:15:result"), eq("BUSINESS_MONITOR_RESULT"),
                argThat(payload -> {
                    Map<?, ?> result = (Map<?, ?>) ((List<?>) ((Map<?, ?>) payload).get("rules")).getFirst();
                    return "FAILED".equals(result.get("status"))
                            && new BigDecimal("150000.00").equals(result.get("differenceAmount"));
                }));
    }

    @Test void interruptedWaitKeepsIndependentBatchAndNeverResubmits() {
        setup("EXECUTE_CONFIGURED");
        var run = mock(AccountingRunEntity.class);
        when(run.status()).thenReturn("RUNNING");
        when(monitors.detail(4L)).thenReturn(new AccountingRunDetailResponse(run, List.of()));
        Thread.currentThread().interrupt();
        try {
            assertThrows(AgentRunStore.StopRequested.class, () -> service.prepare("run", "lease"));
            verify(submissions, times(1)).submit("run", "lease", 15L);
            verify(store, never()).record(anyString(), anyString(), anyString(), eq("BUSINESS_MONITOR_RESULT"), any());
        } finally {
            Thread.interrupted();
        }
    }
}
