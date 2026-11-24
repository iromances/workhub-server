package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.FuncCallAuditMapper;
import cn.aslight.workhub.model.ops.FuncCallAuditEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-b4d7e6f2：Function Call 执行审计——逐条落库、来源/操作人、组合码、错误留痕。 */
class FunctionCallAuditTest {

    private static class RecordingAuditMapper implements FuncCallAuditMapper {
        final List<FuncCallAuditEntity> inserted = new ArrayList<>();

        @Override
        public int insert(FuncCallAuditEntity entity) {
            inserted.add(entity);
            return 1;
        }

        @Override
        public long count(String sqlCode, String source, String status,
                          java.time.LocalDateTime from, java.time.LocalDateTime to) {
            return inserted.size();
        }

        @Override
        public List<FuncCallAuditEntity> findPage(String sqlCode, String source, String status,
                                                  java.time.LocalDateTime from, java.time.LocalDateTime to,
                                                  int limit, int offset) {
            return inserted.stream().skip(offset).limit(limit).toList();
        }

        public List<FuncCallAuditEntity> forMonitor(Long id, List<String> families, int limit, int offset) {
            return inserted.stream().filter(a -> id.equals(a.getMonitorRunId()))
                    .filter(a -> families == null || families.isEmpty() || families.contains(a.getRuleFamily()))
                    .skip(offset).limit(limit).toList();
        }
        public long countMonitor(Long id, List<String> families) {
            return inserted.stream().filter(a -> id.equals(a.getMonitorRunId()))
                    .filter(a -> families == null || families.isEmpty() || families.contains(a.getRuleFamily())).count();
        }

        @Override
        public int deleteBefore(java.time.LocalDateTime cutoff) {
            return 0;
        }
    }

    private static class FixedClient implements XxlJobDatabaseQueryClient {
        final List<String> sqls = new ArrayList<>();
        boolean fail;

        @Override
        public List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<List<Map<String, Object>>> runRowsBatch(String businessLineCode, String environmentCode, List<String> sqls) {
            this.sqls.addAll(sqls);
            if (fail) {
                throw new IllegalArgumentException("数据库查询失败：mock");
            }
            return sqls.stream().map(sql -> List.<Map<String, Object>>of(Map.of("ok", 1))).toList();
        }

        @Override
        public VerifiedBatch runVerifiedBatch(String businessLineCode, String environmentCode, List<String> sqls) {
            this.sqls.addAll(sqls);
            if (fail) {
                throw new IllegalArgumentException("数据库查询失败：mock");
            }
            var started = java.time.Instant.parse("2026-11-14T09:00:00Z");
            return new VerifiedBatch("a".repeat(64), started, started.plusSeconds(2),
                    sqls.stream().map(sql -> List.<Map<String, Object>>of(Map.of("ok", 1))).toList());
        }
    }

    @Test
    void executeStatementsAuditsEachStatementWithSystemSource() {
        FixedClient client = new FixedClient();
        RecordingAuditMapper auditMapper = new RecordingAuditMapper();
        FunctionCallService service = new FunctionCallService(new SeedsTemplateStore(), client, auditMapper);

        List<List<Map<String, Object>>> results = service.executeStatements("BL000004", "PROD", List.of(
                FunctionCallService.Statement.of("OPS_XXL_EXECUTORS", "SELECT 1"),
                FunctionCallService.Statement.of("OPS_XXL_HEALTH_STATS", "SELECT 2")));

        assertEquals(2, results.size());
        assertEquals(2, auditMapper.inserted.size());
        FuncCallAuditEntity first = auditMapper.inserted.getFirst();
        assertEquals("OPS_XXL_EXECUTORS", first.getSqlCode());
        assertEquals("OPS-XXL-EXECUTORS", first.getScenarioCode());
        assertEquals("SYSTEM", first.getSource());
        assertEquals("SYSTEM", first.getOperator());
        assertEquals("BL000004", first.getBusinessLineCode());
        assertEquals("PROD", first.getEnvironmentCode());
        assertEquals("SELECT 1", first.getRenderedSql());
        assertEquals("SUCCESS", first.getStatus());
        assertEquals(1, first.getRowCount());
        assertTrue(first.getDurationMs() != null && first.getDurationMs() >= 0);
        assertNull(first.getErrorMessage());
        assertEquals("OPS_XXL_HEALTH_STATS", auditMapper.inserted.get(1).getSqlCode());
    }

    @Test
    void compositeCodeSkipsScenarioLookupAndErrorIsRecordedAndRethrown() {
        FixedClient client = new FixedClient();
        client.fail = true;
        RecordingAuditMapper auditMapper = new RecordingAuditMapper();
        FunctionCallService service = new FunctionCallService(new SeedsTemplateStore(), client, auditMapper);

        assertThrows(IllegalArgumentException.class, () -> service.executeVerified("BL000001", "PROD", List.of(
                FunctionCallService.Statement.of("OPS_XXL_LOG_STATS+OPS_XXL_FAILED_PAGE", "SELECT stats.*, logs.*"))));

        assertEquals(1, auditMapper.inserted.size());
        FuncCallAuditEntity audit = auditMapper.inserted.getFirst();
        assertEquals("OPS_XXL_LOG_STATS+OPS_XXL_FAILED_PAGE", audit.getSqlCode());
        assertNull(audit.getScenarioCode());
        assertEquals("ERROR", audit.getStatus());
        assertNull(audit.getRowCount());
        assertTrue(audit.getErrorMessage().contains("mock"));
    }

    @Test
    void manualRunUsesManualSourceAndOperatorFallsBackWhenUnauthenticated() {
        FixedClient client = new FixedClient();
        RecordingAuditMapper auditMapper = new RecordingAuditMapper();
        FunctionCallService service = new FunctionCallService(new SeedsTemplateStore(), client, auditMapper);

        List<Map<String, Object>> rows = service.run("OPS_MQ_DISCOVER_DATABASES", FunctionCallService.params(),
                "BL000006", "PROD");

        assertEquals(1, rows.size());
        assertEquals(1, auditMapper.inserted.size());
        FuncCallAuditEntity audit = auditMapper.inserted.getFirst();
        assertEquals("MANUAL", audit.getSource());
        // 无登录上下文（单测环境）回落 UNKNOWN
        assertEquals("UNKNOWN", audit.getOperator());
        assertTrue(audit.getRenderedSql().contains("message_sender_log"));
    }

    @Test
    void nullAuditMapperSkipsAuditWithoutBreakingExecution() {
        FixedClient client = new FixedClient();
        FunctionCallService service = new FunctionCallService(new SeedsTemplateStore(), client, null);
        assertEquals(1, service.executeStatements("BL000004", "PROD",
                List.of(FunctionCallService.Statement.of("OPS_XXL_EXECUTORS", "SELECT 1"))).size());
    }
    @Test
    void monitorFamilyFilterScriptParsesAndBinds() {
        // REQ-372e4ce1：验证 @Select 动态脚本可解析，family 集合进入 IN 绑定、空集合不过滤。
        // 文本块每行行尾空白会被剥掉，SELECT 与 COLUMNS 拼接必须各自成行，否则 SQL 粘连（线上 BadSqlGrammar）。
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.addMapper(FuncCallAuditMapper.class);
        var statement = configuration.getMappedStatement(
                "cn.aslight.workhub.dao.ops.FuncCallAuditMapper.forMonitor");
        var bound = statement.getBoundSql(Map.of("runId", 30L,
                "families", List.of("实际发起笔数", "应发起笔数"), "limit", 20, "offset", 0));
        assertTrue(bound.getSql().contains("rule_family IN"));
        assertEquals(5, bound.getParameterMappings().size());
        assertTrue(bound.getSql().replaceAll("\\s+", " ").startsWith("SELECT monitor_run_id AS monitorRunId"));
        var unfiltered = statement.getBoundSql(Map.of("runId", 30L,
                "families", List.of(), "limit", 20, "offset", 0));
        assertTrue(!unfiltered.getSql().contains("rule_family IN"));

        var page = configuration.getMappedStatement(
                "cn.aslight.workhub.dao.ops.FuncCallAuditMapper.findPage");
        java.util.Map<String, Object> pageParams = new java.util.HashMap<>();
        pageParams.put("sqlCode", null);
        pageParams.put("source", null);
        pageParams.put("status", null);
        pageParams.put("from", null);
        pageParams.put("to", null);
        pageParams.put("limit", 20);
        pageParams.put("offset", 0);
        var pageSql = page.getBoundSql(pageParams).getSql();
        assertTrue(pageSql.replaceAll("\\s+", " ").startsWith("SELECT monitor_run_id AS monitorRunId"));
    }

    @Test
    void monitorEvidenceFreezesTemplateParametersAndRunAssociation() {
        var client = new FixedClient();
        var audits = new RecordingAuditMapper();
        var template = new cn.aslight.workhub.model.ops.FuncCallSqlEntity();
        template.setSqlCode("MONITOR"); template.setEnabled(true); template.setSqlTemplate("SELECT :startId LIMIT 1");
        var templates = new FunctionCallService.TemplateStore() {
            public cn.aslight.workhub.model.ops.FuncCallSqlEntity byCode(String code) { return template; }
            public List<cn.aslight.workhub.model.ops.FuncCallSqlEntity> byScenario(String code) { return List.of(template); }
        };
        var service = new FunctionCallService(templates, client, audits);
        var statement = service.monitorStatement(31L, "payments", "MONITOR", Map.of("startId", "42"));
        template.setSqlTemplate("SELECT 999 LIMIT 1");
        service.executeStatements("BL000005", "PROD", List.of(statement));
        var evidence = audits.inserted.getFirst();
        assertEquals(31L, evidence.getMonitorRunId());
        assertEquals("payments", evidence.getRuleFamily());
        assertEquals("SELECT 42 LIMIT 1", evidence.getRenderedSql());
        assertEquals(64, evidence.getTemplateHash().length());
        assertTrue(evidence.getParametersJson().contains("42"));
        var brokenAudits = org.mockito.Mockito.mock(FuncCallAuditMapper.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit down")).when(brokenAudits).insert(org.mockito.ArgumentMatchers.any());
        var strict = new FunctionCallService(templates, client, brokenAudits);
        assertThrows(IllegalStateException.class, () -> strict.executeStatements("BL000005", "PROD", List.of(statement)));
    }

}
