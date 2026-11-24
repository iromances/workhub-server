package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMonitorRunMapper;
import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.service.ops.AccountingMonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentMonitorSubmissionServiceTest {
    final AgentRunStore store = mock(AgentRunStore.class);
    final AgentAccessService access = mock(AgentAccessService.class);
    final AccountingMonitorMapper configs = mock(AccountingMonitorMapper.class);
    final AccountingMonitorService monitors = mock(AccountingMonitorService.class);
    final AgentMonitorRunMapper links = mock(AgentMonitorRunMapper.class);
    final AgentMonitorSubmissionService service = new AgentMonitorSubmissionService(store, access, configs, monitors, links);
    final AgentScope scope = new AgentScope("BL000005", "PROD");

    AgentToolContext setup(String mode, List<Long> ids) {
        when(links.find(anyString(), anyLong())).thenReturn(null);
        var spec = new AgentTaskSpecification("巡检", null, "", List.of("REPORT"), List.of(scope), List.of(),
                "INCREMENTAL", null, mode, ids, null);
        var context = new AgentToolContext("run", "task", "agent", "admin", "lease", null, spec);
        when(store.lockActiveContext("run", "lease")).thenReturn(context);
        var config = mock(AccountingMonitorConfigEntity.class);
        when(config.businessLineCode()).thenReturn(scope.businessLineCode());
        when(config.environmentCode()).thenReturn(scope.environmentCode());
        when(configs.lockConfig(15L)).thenReturn(15L);
        when(configs.findById(15L)).thenReturn(config);
        return context;
    }

    @Test void observationNeverSubmitsAndExecutionMustSelectConfig() {
        setup("OBSERVE", List.of());
        assertThrows(IllegalArgumentException.class, () -> service.submit("run", "lease", 15L));
        verifyNoInteractions(monitors, links);
        setup("EXECUTE_CONFIGURED", List.of(16L));
        assertThrows(IllegalArgumentException.class, () -> service.submit("run", "lease", 15L));
    }

    @Test void linkedRunIsReusedAfterLeaseRecovery() {
        setup("EXECUTE_CONFIGURED", List.of(15L));
        when(links.find("run", 15L)).thenReturn(31L);
        assertEquals(31L, service.submit("run", "lease", 15L));
        verifyNoInteractions(monitors);
        verify(access).requireScope(any(), eq(scope));
    }

    @Test void revokedPermissionAndForeignScopeCannotCreateRun() {
        var context = setup("EXECUTE_CONFIGURED", List.of(15L));
        doThrow(new AccessDeniedException("scope revoked")).when(access).requireScope(context, scope);
        assertThrows(AccessDeniedException.class, () -> service.submit("run", "lease", 15L));
        verifyNoInteractions(monitors, links);
    }

    @Test void usesCanonicalExecutorAndPersistsItsActualRun() {
        setup("EXECUTE_CONFIGURED", List.of(15L));
        var run = mock(AccountingRunEntity.class);
        when(run.id()).thenReturn(31L);
        when(monitors.submitManual(any(), eq("admin"))).thenReturn(run);
        assertEquals(31L, service.submit("run", "lease", 15L));
        verify(monitors).submitManual(new AccountingManualRunRequest(15L, null, "agent:run:15"), "admin");
        verify(links).insert("run", 15L, 31L);
    }

    @Test void expiredLeaseStopsBeforeConfigOrExecution() {
        doThrow(new IllegalStateException("lease expired")).when(store).lockActiveContext("run", "lease");
        assertThrows(IllegalStateException.class, () -> service.submit("run", "lease", 15L));
        verifyNoInteractions(configs, monitors, links);
    }
}
