package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRuleMapper;
import cn.aslight.workhub.dao.ops.FuncCallSqlMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorCheckEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorFamilyEntity;
import cn.aslight.workhub.model.ops.AccountingRuleModels.CheckSaveItem;
import cn.aslight.workhub.model.ops.AccountingRuleModels.ChecksSaveRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyCheckInput;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyCreateRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyEditRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyEnabledRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilySqlInput;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilySqlSaveRequest;
import cn.aslight.workhub.model.ops.FuncCallSqlEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-7f26a063：规则组管理——启停、检查项保存、自定义规则组事务性、三段模板原子保存。 */
class AccountingRuleAdminServiceTest {

    private static final String BOUNDS = "SELECT COALESCE(MIN(id),0) AS min_id,COALESCE(MAX(id),0) AS max_id FROM ol_saps.customer_bill LIMIT 1";
    private static final String SUMMARY = "SELECT COUNT(*) AS checked_count,0 AS TOTAL_n,0 AS TOTAL_diff FROM ol_saps.customer_bill p WHERE p.id BETWEEN :startId AND :endId LIMIT 1";
    private static final String SAMPLE = "SELECT id FROM ol_saps.customer_bill p WHERE p.id BETWEEN :startId AND :endId AND :ruleCode='TOTAL' ORDER BY id LIMIT :sampleLimit";

    private AccountingMonitorMapper monitors;
    private AccountingRuleMapper rules;
    private FuncCallSqlMapper templates;
    private AccountingRuleAdminService service;

    @BeforeEach
    void setUp() {
        monitors = mock(AccountingMonitorMapper.class);
        rules = mock(AccountingRuleMapper.class);
        templates = mock(FuncCallSqlMapper.class);
        service = new AccountingRuleAdminService(monitors, rules, templates, mock(AccountingRuleInitializer.class));
        when(monitors.findById(15L)).thenReturn(integrityConfig());
    }

    @Test
    void setEnabledRejectsDeletedFamilyAndFamilyWithoutEnabledChecks() {
        when(rules.findFamily(15L, "demo")).thenReturn(family(100L, "demo", "BUILTIN", true, true));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.setEnabled(15L, "demo", new FamilyEnabledRequest(true), "tester"));
        assertTrue(ex.getMessage().contains("已删除的规则组不能启停"));

        when(rules.findFamily(15L, "demo")).thenReturn(family(100L, "demo", "BUILTIN", true, false));
        when(rules.findChecksByFamily(100L)).thenReturn(List.of(check(1L, "TOTAL", "ERROR", false)));
        ex = assertThrows(IllegalArgumentException.class,
                () -> service.setEnabled(15L, "demo", new FamilyEnabledRequest(true), "tester"));
        assertTrue(ex.getMessage().contains("没有启用的检查项"));
        verify(rules, never()).updateFamilyEnabled(any(), anyBoolean(), anyString());
    }

    @Test
    void saveChecksRejectsUnknownCheckId() {
        when(rules.findFamily(15L, "demo")).thenReturn(family(100L, "demo", "BUILTIN", true, false));
        when(rules.findChecksByFamily(100L)).thenReturn(List.of(check(1L, "TOTAL", "ERROR", true)));

        var ex = assertThrows(IllegalArgumentException.class, () -> service.saveChecks(15L, "demo",
                new ChecksSaveRequest(List.of(new CheckSaveItem("MISSING", true, "WARN"))), "tester"));
        assertTrue(ex.getMessage().contains("未知检查项：MISSING"));
        verify(rules, never()).updateCheckState(any(), anyBoolean(), anyString(), anyString());
    }

    @Test
    void createFamilyRegistersFamilyChecksAndThreeTemplates() {
        when(rules.findFamily(15L, "demo")).thenReturn(null, family(77L, "demo", "CUSTOM", true, false));
        when(rules.findFamilies(15L)).thenReturn(List.of());

        service.createFamily(15L, createRequest(), "tester");

        verify(rules).insertFamily(any(AccountingMonitorFamilyEntity.class));
        verify(rules).insertCheck(any(AccountingMonitorCheckEntity.class));
        for (String suffix : List.of("BOUNDS", "SUMMARY", "SAMPLE")) {
            verify(templates).insert(org.mockito.ArgumentMatchers.argThat(entity ->
                    entity.getSqlCode().equals("AI_DEMO_" + suffix)
                            && Boolean.TRUE.equals(entity.getEnabled())
                            && "OPS_ACCOUNTING_INTEGRITY".equals(entity.getSourceModule())));
        }
    }

    @Test
    void createFamilyValidatesCodeNameChecksAndSqlWithoutResidue() {
        when(rules.findFamily(15L, "demo")).thenReturn(null);
        // 编码非法
        assertThrows(IllegalArgumentException.class,
                () -> service.createFamily(15L, createRequestWith("Demo", "演示", "TOTAL", "合计", SUMMARY), "t"));
        // 编码已占用（含已删除）
        when(rules.findFamily(15L, "gone")).thenReturn(family(1L, "gone", "CUSTOM", false, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.createFamily(15L, createRequestWith("gone", "演示", "TOTAL", "合计", SUMMARY), "t"));
        // 名称含「·」（v1.1：批次详情按「·」切分）
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.createFamily(15L, createRequestWith("demo", "智能柜·演示", "TOTAL", "合计", SUMMARY), "t"));
        assertTrue(ex.getMessage().contains("不能包含「·」"));
        // 检查项重复
        var duplicated = new FamilyCreateRequest("demo", "演示", null, 1000,
                List.of(new FamilyCheckInput("TOTAL", "合计", "ERROR", null),
                        new FamilyCheckInput("TOTAL", "合计", "ERROR", null)),
                new FamilySqlInput(BOUNDS, SUMMARY, SAMPLE));
        assertThrows(IllegalArgumentException.class, () -> service.createFamily(15L, duplicated, "t"));
        // 汇总缺 checked_count
        var badSummary = "SELECT COUNT(*) FROM ol_saps.customer_bill p WHERE p.id BETWEEN :startId AND :endId LIMIT 1";
        assertThrows(IllegalArgumentException.class,
                () -> service.createFamily(15L, createRequestWith("demo", "演示", "TOTAL", "合计", badSummary), "t"));
        // 模板编码已占用
        when(templates.findByCode("AI_DEMO_BOUNDS")).thenReturn(new FuncCallSqlEntity());
        assertThrows(IllegalArgumentException.class,
                () -> service.createFamily(15L, createRequest(), "t"));
        verify(rules, never()).insertFamily(any());
        verify(templates, never()).insert(any());
    }

    @Test
    void editAndDeleteOnlyApplyToCustomFamilies() {
        when(rules.findFamily(15L, "builtin")).thenReturn(family(100L, "builtin", "BUILTIN", true, false));
        var edit = new FamilyEditRequest("改名", null, 1000,
                List.of(new FamilyCheckInput("TOTAL", "合计", "ERROR", null)));
        assertThrows(IllegalArgumentException.class, () -> service.editFamily(15L, "builtin", edit, "tester"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteFamily(15L, "builtin", "tester"));

        when(rules.findFamily(15L, "demo")).thenReturn(family(101L, "demo", "CUSTOM", true, false));
        when(rules.findChecksByFamily(101L)).thenReturn(List.of(check(9L, "OLD", "WARN", false)));
        var template = new FuncCallSqlEntity();
        template.setId(500L);
        template.setEnabled(true);
        when(templates.findByCode("AI_DEMO_SUMMARY")).thenReturn(template);

        service.deleteFamily(15L, "demo", "tester");

        verify(rules).markFamilyDeleted(101L, "tester");
        verify(templates).updateEnabled(500L, false);
    }

    @Test
    void saveFamilySqlIsAtomicAndValidated() {
        when(rules.findFamily(15L, "demo")).thenReturn(family(100L, "demo", "BUILTIN", true, false));
        // 模板未登记
        assertThrows(IllegalArgumentException.class, () -> service.saveFamilySql(15L, "demo",
                new FamilySqlSaveRequest(BOUNDS, SUMMARY, SAMPLE)));
        verify(templates, never()).updateSqlTemplate(any(), anyString());

        var bounds = template(601L, "AI_DEMO_BOUNDS");
        var summary = template(602L, "AI_DEMO_SUMMARY");
        var sample = template(603L, "AI_DEMO_SAMPLE");
        when(templates.findByCode("AI_DEMO_BOUNDS")).thenReturn(bounds);
        when(templates.findByCode("AI_DEMO_SUMMARY")).thenReturn(summary);
        when(templates.findByCode("AI_DEMO_SAMPLE")).thenReturn(sample);

        // 任一段契约失败：三条都不更新
        var bad = new FamilySqlSaveRequest(BOUNDS, "SELECT COUNT(*) FROM t WHERE id BETWEEN :startId AND :endId LIMIT 1", SAMPLE);
        assertThrows(IllegalArgumentException.class, () -> service.saveFamilySql(15L, "demo", bad));
        verify(templates, never()).updateSqlTemplate(any(), anyString());

        service.saveFamilySql(15L, "demo", new FamilySqlSaveRequest(BOUNDS, SUMMARY, SAMPLE));
        verify(templates).updateSqlTemplate(601L, BOUNDS);
        verify(templates).updateSqlTemplate(602L, SUMMARY);
        verify(templates).updateSqlTemplate(603L, SAMPLE);
    }

    @Test
    void sqlTemplatesRequiresFamilyForIntegrityAndSupportsFlowConfigs() {
        when(rules.findFamily(15L, "demo")).thenReturn(family(100L, "demo", "BUILTIN", true, false));
        when(templates.findByCode("AI_DEMO_BOUNDS")).thenReturn(template(601L, "AI_DEMO_BOUNDS"));
        var items = service.sqlTemplates(15L, "demo");
        assertEquals(3, items.size());
        assertEquals("边界", items.getFirst().purpose());
        assertEquals("AI_DEMO_BOUNDS", items.getFirst().sqlCode());
        assertEquals(601L, items.getFirst().templateId());
        assertTrue(!items.get(1).enabled());

        assertThrows(IllegalArgumentException.class, () -> service.sqlTemplates(15L, null));

        when(monitors.findById(30L)).thenReturn(new AccountingMonitorConfigEntity(30L, "BL000008", "叉车", "PROD",
                "lease-saps", "批量代扣", "BATCH_WITHHOLD", LocalTime.of(8, 0), LocalTime.of(8, 30),
                "OPS_SCN_WITHHOLD_EXPECTED", null, "OPS_SCN_WITHHOLD_ACTUAL", "OPS_SCN_WITHHOLD_SUCCESS",
                BigDecimal.ZERO, false, true, null, null, null));
        var flow = service.sqlTemplates(30L, null);
        assertEquals(List.of("应发起笔数", "实际发起笔数", "成功笔数"), flow.stream()
                .map(item -> item.purpose()).toList());
    }

    private FamilyCreateRequest createRequest() {
        return createRequestWith("demo", "演示", "TOTAL", "合计", SUMMARY);
    }

    private FamilyCreateRequest createRequestWith(String code, String name, String checkId, String checkName, String summary) {
        return new FamilyCreateRequest(code, name, null, 1000,
                List.of(new FamilyCheckInput(checkId, checkName, "ERROR", null)),
                new FamilySqlInput(BOUNDS, summary, SAMPLE));
    }

    private FuncCallSqlEntity template(long id, String sqlCode) {
        var entity = new FuncCallSqlEntity();
        entity.setId(id);
        entity.setSqlCode(sqlCode);
        entity.setEnabled(true);
        return entity;
    }

    private AccountingMonitorConfigEntity integrityConfig() {
        return new AccountingMonitorConfigEntity(15L, "BL000005", "智能柜", "PROD", "ol-saps", "一致性",
                AccountingIntegrityCatalog.TYPE, null, LocalTime.of(8, 30), null, null, null, null,
                BigDecimal.ZERO, false, true, null, null, null);
    }

    private AccountingMonitorFamilyEntity family(long id, String code, String sourceType, boolean enabled, boolean deleted) {
        return new AccountingMonitorFamilyEntity(id, 15L, code, "演示", null, 1000, sourceType, enabled, deleted,
                1, "system", "system", null, null);
    }

    private AccountingMonitorCheckEntity check(long id, String checkId, String severity, boolean enabled) {
        return new AccountingMonitorCheckEntity(id, 100L, checkId, checkId.toLowerCase(), severity, null, enabled,
                1, "system", "system", null, null);
    }
}
