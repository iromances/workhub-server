package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingManualRunRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingMonitorServiceTest {

    private static final ZoneId ZONE = AccountingExecutionEvidenceService.RECORD_ZONE;

    private AccountingMonitorMapper monitorMapper;
    private AccountingRunMapper runMapper;
    private FunctionCallService functionCallService;
    private AccountingMonitorService service;

    @BeforeEach
    void setUp() {
        monitorMapper = mock(AccountingMonitorMapper.class);
        runMapper = mock(AccountingRunMapper.class);
        when(runMapper.findActive(any(), any())).thenReturn(null);
        functionCallService = mock(FunctionCallService.class);
        service = new AccountingMonitorService(
                monitorMapper,
                runMapper,
                mock(AccountingScenarioCheckExecutor.class),
                functionCallService,
                new SyncTaskExecutor()
        );
        when(functionCallService.isCodeEnabled(anyString())).thenReturn(true);
        when(monitorMapper.findById(10L)).thenReturn(enabledConfig(10L, LocalTime.of(8, 30)));
    }

    @Test
    void shouldRejectExpectedCountConfiguredBothWays() {
        AccountingMonitorSaveRequest request = saveRequest("OPS_SCN_WITHHOLD_EXPECTED", 500);

        assertThrows(IllegalArgumentException.class, () -> service.createConfig(request));
        verify(monitorMapper, never()).insert(any());
    }

    @Test
    void shouldRejectExpectedCountMissing() {
        AccountingMonitorSaveRequest request = saveRequest(null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createConfig(request));
        assertEquals("应发起笔数必须且只能配置 SQL 编码或固定值之一", ex.getMessage());
    }

    @Test
    void shouldRejectToleranceOutOfRange() {
        AccountingMonitorSaveRequest request = new AccountingMonitorSaveRequest(
                "BL000008", "PROD", "lease-saps", "批量代扣", "BATCH_WITHHOLD",
                LocalTime.of(8, 0), LocalTime.of(8, 30),
                "OPS_SCN_WITHHOLD_EXPECTED", null, "OPS_SCN_WITHHOLD_ACTUAL", null,
                new BigDecimal("1.5"), false, false, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createConfig(request));
        assertEquals("容差比例必须在 0 到 1 之间", ex.getMessage());
    }

    @Test
    void shouldRejectEnableWhenSqlCodeNotRegistered() {
        when(functionCallService.isCodeEnabled("OPS_SCN_WITHHOLD_ACTUAL")).thenReturn(false);
        AccountingMonitorSaveRequest request = saveRequest("OPS_SCN_WITHHOLD_EXPECTED", null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createConfig(request));
        assertEquals("实际发起笔数 SQL 编码未登记或已停用：OPS_SCN_WITHHOLD_ACTUAL", ex.getMessage());
        verify(monitorMapper, never()).insert(any());
    }

    @Test
    void shouldCreateConfigWithFixedExpectedWhenSqlCodesEnabled() {
        AccountingMonitorSaveRequest request = saveRequest(null, 500);

        service.createConfig(request);

        verify(functionCallService).isCodeEnabled("OPS_SCN_WITHHOLD_ACTUAL");
        verify(monitorMapper).insert(any(AccountingMonitorConfigEntity.class));
    }

    @Test
    void shouldRejectManualRunForFutureCheckDateOrDisabledConfig() {
        AccountingManualRunRequest future = new AccountingManualRunRequest(
                10L, LocalDate.now(ZONE).plusDays(1), "request-1");
        assertThrows(IllegalArgumentException.class, () -> service.submitManual(future, "tester"));

        when(monitorMapper.findById(11L)).thenReturn(new AccountingMonitorConfigEntity(
                11L, "BL000008", "叉车", "PROD", "lease-saps", "批量代扣", "BATCH_WITHHOLD",
                LocalTime.of(8, 0), LocalTime.of(8, 30),
                "OPS_SCN_WITHHOLD_EXPECTED", null, "OPS_SCN_WITHHOLD_ACTUAL", "OPS_SCN_WITHHOLD_SUCCESS",
                new BigDecimal("0.005"), true, false, null, null, null));
        AccountingManualRunRequest onDisabled = new AccountingManualRunRequest(11L, LocalDate.now(ZONE), "request-2");
        assertThrows(IllegalArgumentException.class, () -> service.submitManual(onDisabled, "tester"));

        verify(runMapper, never()).insertRun(anyString(), anyString(), any(), anyString(),
                any(), any(), any(), anyString(), anyString());
    }

    @Test
    void shouldSubmitManualRunWithWholeDayWindow() {
        LocalDate checkDate = LocalDate.now(ZONE).minusDays(1);
        AccountingManualRunRequest request = new AccountingManualRunRequest(10L, checkDate, "request-3");

        service.submitManual(request, "tester");

        verify(runMapper).insertRun(
                org.mockito.ArgumentMatchers.startsWith("AS"),
                eq("MANUAL:10:" + checkDate + ":request-3"),
                eq(10L),
                eq("MANUAL"),
                eq(checkDate.atStartOfDay()),
                eq(checkDate.plusDays(1).atStartOfDay()),
                eq(null),
                eq("PENDING"),
                eq("tester"));
    }

    @Test
    void activeWindowIsReusedWithoutDuplicateSubmission() {
        LocalDate date = LocalDate.now(ZONE).minusDays(1);
        var active = mock(cn.aslight.workhub.model.ops.AccountingRunEntity.class);
        when(runMapper.findActive(10L, date.atStartOfDay())).thenReturn(99L);
        when(runMapper.findById(99L)).thenReturn(active);
        assertEquals(active, service.submitManual(new AccountingManualRunRequest(10L, date, "new-key"), "tester"));
        verify(monitorMapper).lockConfig(10L);
        verify(runMapper, never()).insertRun(anyString(), anyString(), any(), anyString(),
                any(), any(), any(), anyString(), anyString());
    }

    @Test
    void executorStartsOnlyAfterSubmissionTransactionCommits() {
        var executor = mock(AccountingScenarioCheckExecutor.class);
        var transactional = new AccountingMonitorService(monitorMapper, runMapper, executor,
                functionCallService, new SyncTaskExecutor());
        when(runMapper.findIdByRunNo(anyString())).thenReturn(99L);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            transactional.submitManual(new AccountingManualRunRequest(10L, LocalDate.now(ZONE), "tx"), "tester");
            verify(executor, never()).execute(any());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(executor).execute(99L);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldMarkRunFailedWhenAccountingQueueRejectsTask() {
        AccountingMonitorService rejectedService = new AccountingMonitorService(
                monitorMapper,
                runMapper,
                mock(AccountingScenarioCheckExecutor.class),
                functionCallService,
                task -> {
                    throw new TaskRejectedException("queue full");
                }
        );
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var transaction = mock(org.springframework.transaction.TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(transaction);
        rejectedService.setTransactionManager(transactions);
        when(runMapper.findIdByRunNo(anyString())).thenReturn(99L);

        LocalDate checkDate = LocalDate.now(ZONE).minusDays(1);
        rejectedService.submitManual(new AccountingManualRunRequest(10L, checkDate, "request-queue-full"), "tester");

        verify(transactions).getTransaction(org.mockito.ArgumentMatchers.argThat(definition ->
                definition.getPropagationBehavior() == org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactions).commit(transaction);

        verify(runMapper).finishRun(
                eq(99L),
                eq("FAILED"),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("队列已满"));
    }

    @Test
    void submitDueScheduledSkipsBeforeCheckTimeAndSubmitsAfter() {
        when(monitorMapper.findAll(null, true)).thenReturn(java.util.List.of(
                enabledConfig(10L, LocalTime.now(ZONE).plusMinutes(10)),
                enabledConfig(11L, LocalTime.now(ZONE).minusMinutes(10))));

        service.submitDueScheduled();

        verify(runMapper).insertRun(
                org.mockito.ArgumentMatchers.startsWith("AS"),
                eq("SCENARIO:11:" + LocalDate.now(ZONE)),
                eq(11L),
                eq("SCHEDULED"),
                any(),
                any(),
                eq(null),
                eq("PENDING"),
                eq("SYSTEM"));
    }

    @Test
    void integrityAllowsFullRangeAndNeverSchedules() {
        var config = new AccountingMonitorConfigEntity(10L, "BL000008", "叉车", "PROD", "lease-saps", "一致性",
                AccountingIntegrityCatalog.TYPE, null, LocalTime.MIDNIGHT, null, null, null, null,
                BigDecimal.ZERO, false, true, null, null, null);
        when(monitorMapper.findById(10L)).thenReturn(config);
        when(runMapper.findIdByRunNo(anyString())).thenReturn(99L);
        service.submitManual(new AccountingManualRunRequest(10L, null, "all"), "tester");
        verify(runMapper).insertRun(anyString(), eq("MANUAL:10:null:all"), eq(10L), eq("MANUAL"),
                eq(null), eq(null), eq(null), eq("PENDING"), eq("tester"));
        when(monitorMapper.findAll(null, true)).thenReturn(java.util.List.of(config));
        service.submitDueScheduled();
        verify(runMapper, never()).insertRun(anyString(), anyString(), any(), eq("SCHEDULED"),
                any(), any(), any(), anyString(), anyString());
    }

    @Test
    void workflowStillRequiresDate() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitManual(new AccountingManualRunRequest(10L, null, "all"), "tester"));
    }

    /** REQ-372e4ce1：family 过滤——不传不过滤；逗号分隔去空白去重后精确匹配；维度 SQL 查看用。 */
    @Test
    void sqlEvidenceFiltersByFamilyAndKeepsUnfilteredBehavior() {
        var audits = mock(cn.aslight.workhub.dao.ops.FuncCallAuditMapper.class);
        var links = mock(cn.aslight.workhub.dao.agent.AgentMonitorRunMapper.class);
        service.setEvidenceMappers(audits, links);
        var run = mock(cn.aslight.workhub.model.ops.AccountingRunEntity.class);
        when(runMapper.findById(30L)).thenReturn(run);
        when(audits.forMonitor(eq(30L), any(), eq(50), eq(0))).thenReturn(java.util.List.of());
        when(audits.countMonitor(eq(30L), any())).thenReturn(0L);

        service.sqlEvidence(30L, null, 1, 50);
        service.sqlEvidence(30L, "  ", 1, 50);
        verify(audits, org.mockito.Mockito.times(2)).countMonitor(eq(30L), eq(java.util.List.of()));

        // 9 个去重值截取前 8 个：末位"多余7"被忽略
        var capped = java.util.List.of(
                "实际发起笔数", "应发起笔数", "多余1", "多余2", "多余3", "多余4", "多余5", "多余6");
        service.sqlEvidence(30L, " 实际发起笔数 ,应发起笔数,实际发起笔数,,多余1,多余2,多余3,多余4,多余5,多余6,多余7", 1, 50);
        verify(audits).countMonitor(eq(30L), eq(capped));
        verify(audits).forMonitor(eq(30L), eq(capped), eq(50), eq(0));

        when(runMapper.findById(31L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.sqlEvidence(31L, "x", 1, 20));
    }

    // ===== REQ-7f26a063：一致性提交校验、配置业务线放开与创建物化 =====

    @Test
    void integritySubmitRejectedWithoutEffectiveRules() {
        var ruleInitializer = mock(AccountingRuleInitializer.class);
        service.setRuleStore(mock(cn.aslight.workhub.dao.ops.AccountingRuleMapper.class), ruleInitializer);
        when(monitorMapper.findById(20L)).thenReturn(integrityConfig(20L));
        when(ruleInitializer.hasEffectiveRules(20L)).thenReturn(false);

        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitManual(new AccountingManualRunRequest(20L, null, "none"), "tester"));
        assertEquals("该配置未启用任何监测场景规则", ex.getMessage());
        verify(runMapper, never()).insertRun(anyString(), anyString(), any(), anyString(),
                any(), any(), any(), anyString(), anyString());
    }

    @Test
    void integritySubmitProceedsWithEffectiveRules() {
        var ruleInitializer = mock(AccountingRuleInitializer.class);
        service.setRuleStore(mock(cn.aslight.workhub.dao.ops.AccountingRuleMapper.class), ruleInitializer);
        when(monitorMapper.findById(20L)).thenReturn(integrityConfig(20L));
        when(ruleInitializer.hasEffectiveRules(20L)).thenReturn(true);
        when(runMapper.findIdByRunNo(anyString())).thenReturn(99L);

        service.submitManual(new AccountingManualRunRequest(20L, null, "ok"), "tester");

        verify(runMapper).insertRun(anyString(), eq("MANUAL:20:null:ok"), eq(20L), eq("MANUAL"),
                eq(null), eq(null), eq(null), eq("PENDING"), eq("tester"));
    }

    @Test
    void integrityConfigAllowsAnyBusinessLineAndMaterializesOnCreate() {
        var ruleInitializer = mock(AccountingRuleInitializer.class);
        service.setRuleStore(mock(cn.aslight.workhub.dao.ops.AccountingRuleMapper.class), ruleInitializer);
        var request = new AccountingMonitorSaveRequest(
                "BL000001", "PROD", "scf-saps", "自定义一致性", AccountingIntegrityCatalog.TYPE,
                null, LocalTime.of(8, 30), null, null, null, null,
                BigDecimal.ZERO, false, true, null);
        var created = new AccountingMonitorConfigEntity(21L, "BL000001", null, "PROD", "scf-saps", "自定义一致性",
                AccountingIntegrityCatalog.TYPE, null, LocalTime.of(8, 30), null, null, null, null,
                BigDecimal.ZERO, false, true, null, null, null);
        when(monitorMapper.findByIdentity("BL000001", "PROD", "scf-saps", AccountingIntegrityCatalog.TYPE))
                .thenReturn(null, created);

        service.createConfig(request);

        verify(monitorMapper).insert(any(AccountingMonitorConfigEntity.class));
        verify(ruleInitializer).ensureMaterialized(created);
    }

    @Test
    void integrityConfigStillRejectsAlertEnabled() {
        var request = new AccountingMonitorSaveRequest(
                "BL000005", "PROD", "ol-saps", "一致性", AccountingIntegrityCatalog.TYPE,
                null, LocalTime.of(8, 30), null, null, null, null,
                BigDecimal.ZERO, true, false, null);
        var ex = assertThrows(IllegalArgumentException.class, () -> service.createConfig(request));
        assertEquals("单据及支付一致性当前仅手动核验，通知保持关闭", ex.getMessage());
    }

    private AccountingMonitorConfigEntity integrityConfig(Long id) {
        return new AccountingMonitorConfigEntity(id, "BL000005", "智能柜", "PROD", "ol-saps", "一致性",
                AccountingIntegrityCatalog.TYPE, null, LocalTime.MIDNIGHT, null, null, null, null,
                BigDecimal.ZERO, false, true, null, null, null);
    }

    private AccountingMonitorSaveRequest saveRequest(String expectedSqlCode, Integer fixedExpected) {
        return new AccountingMonitorSaveRequest(
                "BL000008", "PROD", "lease-saps", "批量代扣", "BATCH_WITHHOLD",
                LocalTime.of(8, 0), LocalTime.of(8, 30),
                expectedSqlCode, fixedExpected, "OPS_SCN_WITHHOLD_ACTUAL", "OPS_SCN_WITHHOLD_SUCCESS",
                new BigDecimal("0.005"), true, true, null);
    }

    private AccountingMonitorConfigEntity enabledConfig(Long id, LocalTime checkTime) {
        return new AccountingMonitorConfigEntity(
                id, "BL000008", "叉车", "PROD", "lease-saps", "批量代扣", "BATCH_WITHHOLD",
                LocalTime.of(8, 0), checkTime,
                "OPS_SCN_WITHHOLD_EXPECTED", null, "OPS_SCN_WITHHOLD_ACTUAL", "OPS_SCN_WITHHOLD_SUCCESS",
                new BigDecimal("0.005"), true, true, null, null, null);
    }
}
