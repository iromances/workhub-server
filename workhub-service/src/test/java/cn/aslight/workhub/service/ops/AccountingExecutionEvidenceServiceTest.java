package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.dao.ops.*;
import cn.aslight.workhub.model.ops.*;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际迁移、Mapper、执行器和来源读取；仅业务数据库查询使用桩。 */
class AccountingExecutionEvidenceServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private Connection connection;
    private JdbcTemplate jdbc;
    private AccountingRunMapper runs;
    private AccountingMonitorMapper configs;
    private AccountingExecutionEvidenceMapper records;
    private AgentMonitorReadMapper history;
    private AccountingExecutionEvidenceService evidence;
    private AccountingReconcileExecutor executor;
    private AccountingDatabaseQueryClient.Session session;
    private final LocalDateTime start = LocalDateTime.of(2026, 11, 4, 10, 0);

    @BeforeEach void setup() throws Exception {
        var db = new JdbcDataSource(); db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        connection = db.getConnection(); jdbc = new JdbcTemplate(db);
        String prefix = "../workhub-bootstrap/src/main/resources/db/schema/mysql/";
        create(Files.readString(Path.of(prefix + "V1__init.sql")), "pm_business_line");
        String old = Files.readString(Path.of(prefix + "V20260723_210000__add_accounting_monitoring.sql"));
        for (String table : List.of("ops_account_monitor_config", "ops_account_rule_config", "ops_account_reconcile_run", "ops_account_reconcile_result")) create(old, table);
        jdbc.execute(Files.readString(Path.of(prefix + "V20261104_132500__add_accounting_execution_evidence.sql")));
        jdbc.update("INSERT INTO pm_business_line(business_line_code,business_line_name) VALUES ('BL000012','工作台'),('BL000001','其他')");
        jdbc.update("INSERT INTO ops_account_monitor_config(id,business_line_code,environment_code,system_name,display_name,database_target_key,schema_name,rule_profile,enabled) VALUES (1,'BL000012','TEST','original-system','原监测','db-test','original_schema','COMMON_SAPS',1)");
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(AccountingRunMapper.class, AccountingMonitorMapper.class, AccountingExecutionEvidenceMapper.class, AgentMonitorReadMapper.class)) configuration.addMapper(type);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(db); factory.setConfiguration(configuration);
        var sql = new SqlSessionTemplate(factory.getObject());
        runs = sql.getMapper(AccountingRunMapper.class); configs = sql.getMapper(AccountingMonitorMapper.class);
        records = sql.getMapper(AccountingExecutionEvidenceMapper.class); history = sql.getMapper(AgentMonitorReadMapper.class);
        evidence = new AccountingExecutionEvidenceService(records, json);
        session = mock(AccountingDatabaseQueryClient.Session.class); when(session.targetHash()).thenReturn("a".repeat(64));
        var queries = mock(AccountingDatabaseQueryClient.class); when(queries.open(any())).thenReturn(session);
        executor = new AccountingReconcileExecutor(configs, runs, new AccountingRuleCatalog(), queries, evidence);
        when(session.runRows(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).contains("information_schema.tables")
                ? List.of(Map.of("table_name", "customer_transaction")) : List.of(Map.of("anomaly_count", 0, "difference_amount", "0.00")));
    }
    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }

    @Test void realExecutionSavesExactContextBeforeFirstQueryAndKeepsItAfterConfigMovesOrIsDeleted() {
        long id = run("current");
        when(session.runRows(anyString())).thenAnswer(call -> {
            var row = records.find(id); assertNotNull(row, "第一条业务查询前必须落盘");
            assertEquals("TEST", row.environmentCode());
            jdbc.update("UPDATE ops_account_monitor_config SET business_line_code='BL000001',environment_code='PROD',system_name='changed',database_target_key='db-prod',schema_name='other_schema' WHERE id=1");
            String query = call.getArgument(0);
            assertFalse(query.contains("other_schema"));
            return query.contains("information_schema.tables") ? List.of(Map.of("table_name", "customer_transaction"))
                    : List.of(Map.of("anomaly_count", 0, "difference_amount", "0.00"));
        });
        executor.execute(id);
        var result = runs.findById(id); assertEquals("SUCCESS", result.status());
        var saved = evidence.read(result); assertNotNull(saved);
        assertEquals("TEST", saved.snapshot().configuration().environmentCode());
        assertEquals("db-test", saved.snapshot().configuration().databaseTargetKey());
        assertEquals("BL000012", result.businessLineCode()); assertEquals("original-system", result.systemName());
        assertEquals(start, saved.snapshot().startTime()); assertEquals("a".repeat(64), saved.snapshot().targetBindingHash());
        assertEquals(1, saved.snapshot().rules().size());
        var rule = new AccountingRuleCatalog().rulesForProfile("COMMON_SAPS").stream().filter(r -> "TX_INVALID_AMOUNT".equals(r.ruleCode())).findFirst().orElseThrow();
        assertEquals(AccountingExecutionEvidenceService.hash(rule.buildSql("original_schema", start, start.plusHours(1)).summarySql()), saved.snapshot().rules().getFirst().summarySqlHash());
        assertEquals(saved.contentHash(), AccountingExecutionEvidenceService.hash(records.find(id).snapshotJson()));
        assertEquals(List.of(id), history.accountingRuns("BL000012", "TEST", null, 0, 10).stream().map(AccountingRunEntity::id).toList());
        assertTrue(history.accountingRuns("BL000001", "PROD", null, 0, 10).isEmpty());
        assertEquals(id, history.accountingUpper("BL000012", "TEST"));
        assertEquals(1, history.scanAccounting("BL000012", "TEST", 0, id, 10).size());
        assertNull(runs.findLatestByConfigId(1L), "当前配置不能拿旧环境结果充当最新运行");
        jdbc.update("DELETE FROM ops_account_monitor_config WHERE id=1");
        assertEquals("BL000012", runs.findById(id).businessLineCode());
        assertEquals(1, runs.countRuns("BL000012", null));
        assertEquals(1, runs.findRuns("BL000012", null, 0, 10).size());
        assertEquals(saved, evidence.read(runs.findById(id)));
        executor.execute(id);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ops_account_execution_evidence", Integer.class));
    }

    @Test void evidenceWriteFailurePreventsAnyDatabaseQuery() {
        long id = run("write-failure");
        jdbc.execute("ALTER TABLE ops_account_execution_evidence ADD CONSTRAINT reject_capture CHECK(run_id < 0)");
        executor.execute(id);
        assertEquals("FAILED", runs.findById(id).status());
        verify(session, never()).runRows(anyString());
        assertNull(records.find(id)); assertTrue(runs.findResults(id).isEmpty());
    }

    @Test void missingSummaryDoesNotBecomeZeroAnomalySuccess() {
        long id = run("no-summary");
        when(session.runRows(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).contains("information_schema.tables")
                ? List.of(Map.of("table_name", "customer_transaction")) : List.of());
        executor.execute(id);
        assertEquals("FAILED", runs.findById(id).status());
        assertEquals("FAILED", runs.findResults(id).getFirst().status());
        assertNotNull(evidence.read(runs.findById(id)), "来源凭据不能被解释成业务查询成功");
    }

    @Test void oldRowsStayUnverifiedAndCorruptEvidenceIsNeverTreatedAsLegacy() {
        long old = run("legacy"); jdbc.update("UPDATE ops_account_reconcile_run SET status='SUCCESS' WHERE id=?", old);
        assertNull(evidence.read(runs.findById(old)));
        assertFalse(evidence.describe(runs.findById(old)).path("executionEvidence").path("scopeVerified").asBoolean());
        long current = run("valid"); executor.execute(current);
        assertTrue(evidence.describe(runs.findById(current)).path("executionEvidence").path("scopeVerified").asBoolean());
        jdbc.update("UPDATE ops_account_execution_evidence SET content_hash='broken' WHERE run_id=?", current);
        assertThrows(IllegalStateException.class, () -> evidence.read(runs.findById(current)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ops_account_execution_evidence", Integer.class));
    }

    @Test void dataWindowOrIndexedScopeTamperingCannotPassEvidenceValidation() {
        long id = run("tamper"); executor.execute(id);
        jdbc.update("UPDATE ops_account_execution_evidence SET environment_code='PROD' WHERE run_id=?", id);
        assertThrows(IllegalStateException.class, () -> evidence.read(runs.findById(id)));
        jdbc.update("UPDATE ops_account_execution_evidence SET environment_code='TEST' WHERE run_id=?", id);
        jdbc.update("UPDATE ops_account_reconcile_run SET end_time=? WHERE id=?", start.plusHours(2), id);
        assertThrows(IllegalStateException.class, () -> evidence.read(runs.findById(id)));
    }

    private long run(String key) {
        runs.insertRun(key, key, 1L, "MANUAL", start, start.plusHours(1), "TX_INVALID_AMOUNT", "PENDING", "fixture");
        return runs.findIdByRunNo(key);
    }
    private void create(String source, String name) {
        var pattern = Pattern.compile("(?s)CREATE TABLE(?: IF NOT EXISTS)? `" + Pattern.quote(name) + "` \\(.*?;");
        var match = pattern.matcher(source); assertTrue(match.find(), name); jdbc.execute(match.group());
    }
}
