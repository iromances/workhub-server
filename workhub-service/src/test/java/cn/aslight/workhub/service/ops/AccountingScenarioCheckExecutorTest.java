package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.integration.wecom.WecomRobotNotifier;
import cn.aslight.workhub.model.ops.AccountingResultEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 真实迁移建表 + Mapper 落库；FunctionCallService 与 WeCom 通知用桩（REQ-d227e09d）。
 *  桩行为经可变字段控制，避免重复 stub 时执行旧 answer。 */
class AccountingScenarioCheckExecutorTest {
    private Connection connection;
    private JdbcTemplate jdbc;
    private AccountingRunMapper runs;
    private AccountingMonitorMapper configs;
    private FunctionCallService functionCalls;
    private WecomRobotNotifier wecom;
    private AccountingScenarioCheckExecutor executor;
    private final Map<String, Long> counts = new HashMap<>();
    private String renderFailureCode;
    private String renderFailureMessage;
    private boolean actualQueryError;
    private final LocalDateTime start = LocalDate.of(2026, 11, 17).atStartOfDay();

    @BeforeEach
    void setup() throws Exception {
        var db = new JdbcDataSource(); db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        connection = db.getConnection(); jdbc = new JdbcTemplate(db);
        String prefix = "../workhub-bootstrap/src/main/resources/db/schema/mysql/";
        create(Files.readString(Path.of(prefix + "V1__init.sql")), "pm_business_line");
        String old = Files.readString(Path.of(prefix + "V20260723_210000__add_accounting_monitoring.sql"));
        for (String table : List.of("ops_account_reconcile_run", "ops_account_reconcile_result")) create(old, table);
        jdbc.execute(Files.readString(Path.of(prefix + "V20261104_132500__add_accounting_execution_evidence.sql")));
        jdbc.execute("""
                CREATE TABLE `ops_account_monitor_config` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                  `business_line_code` VARCHAR(32) NOT NULL,
                  `environment_code` VARCHAR(32) NOT NULL DEFAULT 'prod',
                  `system_name` VARCHAR(128) NOT NULL,
                  `display_name` VARCHAR(128) NOT NULL,
                  `scenario_type` VARCHAR(32) NOT NULL,
                  `expected_time` TIME NULL,
                  `check_time` TIME NOT NULL,
                  `expected_count_sql_code` VARCHAR(64) NULL,
                  `expected_fixed_count` INT NULL,
                  `actual_count_sql_code` VARCHAR(64) NOT NULL,
                  `success_count_sql_code` VARCHAR(64) NULL,
                  `tolerance_rate` DECIMAL(6,4) NOT NULL DEFAULT 0.0000,
                  `alert_enabled` TINYINT(1) NOT NULL DEFAULT 0,
                  `enabled` TINYINT(1) NOT NULL DEFAULT 0,
                  `remark` VARCHAR(500) NULL,
                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AccountingRunMapper.class);
        configuration.addMapper(AccountingMonitorMapper.class);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(db); factory.setConfiguration(configuration);
        var sql = new SqlSessionTemplate(factory.getObject());
        runs = sql.getMapper(AccountingRunMapper.class);
        configs = sql.getMapper(AccountingMonitorMapper.class);
        functionCalls = mock(FunctionCallService.class);
        wecom = mock(WecomRobotNotifier.class);
        executor = new AccountingScenarioCheckExecutor(configs, runs, functionCalls, wecom, true);
        when(functionCalls.monitorStatement(anyLong(), anyString(), anyString(), anyMap())).thenAnswer(inv ->
                FunctionCallService.Statement.of(inv.getArgument(2), functionCalls.render(inv.getArgument(2), inv.getArgument(3))));
        when(functionCalls.render(anyString(), any())).thenAnswer(call -> {
            String code = call.getArgument(0);
            if (code.equals(renderFailureCode)) {
                throw new IllegalArgumentException(renderFailureMessage);
            }
            return "SELECT " + code;
        });
        when(functionCalls.executeStatements(anyString(), anyString(), anyList())).thenAnswer(call -> {
            if (actualQueryError) {
                throw new IllegalArgumentException("只读网关不可用");
            }
            List<FunctionCallService.Statement> statements = call.getArgument(2);
            long value = counts.getOrDefault(statements.getFirst().sqlCode(), 0L);
            return List.of(List.of(Map.of("cnt", value)));
        });
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void withholdScenarioPassesWithinToleranceWithSqlExpectedAndAuditedCodes() {
        counts.put("OPS_SCN_WITHHOLD_EXPECTED", 520L);
        counts.put("OPS_SCN_WITHHOLD_ACTUAL", 518L);
        counts.put("OPS_SCN_WITHHOLD_SUCCESS", 515L);
        long configId = insertConfig("OPS_SCN_WITHHOLD_EXPECTED", null, "OPS_SCN_WITHHOLD_SUCCESS", "0.0100", 0);
        long runId = insertRun(configId);

        executor.execute(runId);

        AccountingRunEntity run = runs.findById(runId);
        assertEquals("SUCCESS", run.status());
        assertEquals(3, run.ruleTotal());
        assertEquals(3, run.passedCount());
        assertEquals(0, run.anomalyCount());
        List<AccountingResultEntity> results = runs.findResults(runId);
        assertEquals(List.of(
                "BATCH_WITHHOLD_STARTED_ON_TIME",
                "BATCH_WITHHOLD_EXPECTED_VS_ACTUAL",
                "BATCH_WITHHOLD_SUCCESS_RATE"), results.stream().map(AccountingResultEntity::ruleCode).toList());
        assertEquals("批量代扣", results.getFirst().category());
        assertEquals("SCENARIO", results.getFirst().granularity());
        assertTrue(results.get(1).message().contains("应发起 520 笔，实际发起 518 笔，差 2 笔"));
        assertTrue(results.get(2).message().contains("发起 518 笔，成功 515 笔，未成功 3 笔"));
        // 审计编码 = func_call_sql 登记编码，逐条执行
        verify(functionCalls).executeStatements(eq("BL000008"), eq("PROD"),
                eq(List.of(FunctionCallService.Statement.of("OPS_SCN_WITHHOLD_ACTUAL", "SELECT OPS_SCN_WITHHOLD_ACTUAL"))));
        verify(functionCalls).executeStatements(eq("BL000008"), eq("PROD"),
                eq(List.of(FunctionCallService.Statement.of("OPS_SCN_WITHHOLD_EXPECTED", "SELECT OPS_SCN_WITHHOLD_EXPECTED"))));
        verify(functionCalls).executeStatements(eq("BL000008"), eq("PROD"),
                eq(List.of(FunctionCallService.Statement.of("OPS_SCN_WITHHOLD_SUCCESS", "SELECT OPS_SCN_WITHHOLD_SUCCESS"))));
        verify(wecom, never()).sendMarkdownMessage(anyString(), anyString());
    }

    @Test
    void notStartedDayFlagsStartedAndExpectedDimensions() {
        counts.put("OPS_SCN_WITHHOLD_EXPECTED", 520L);
        counts.put("OPS_SCN_WITHHOLD_ACTUAL", 0L);
        long configId = insertConfig("OPS_SCN_WITHHOLD_EXPECTED", null, null, "0.0100", 0);
        long runId = insertRun(configId);

        executor.execute(runId);

        AccountingRunEntity run = runs.findById(runId);
        assertEquals("WARNING", run.status());
        assertEquals(2, run.failedCount());
        assertEquals(521, run.anomalyCount());
        List<AccountingResultEntity> results = runs.findResults(runId);
        assertEquals("ERROR", results.get(0).status());
        assertEquals(1, results.get(0).anomalyCount());
        assertTrue(results.get(0).message().contains("当日未发起任何批量代扣记录"));
        assertEquals("ERROR", results.get(1).status());
        assertEquals(520, results.get(1).anomalyCount());
        assertEquals("SKIPPED", results.get(2).status());
        assertEquals(0, results.get(2).anomalyCount());
    }

    @Test
    void zeroSuccessFlagsErrorEvenWithinTolerance() {
        counts.put("OPS_SCN_WITHHOLD_ACTUAL", 10L);
        counts.put("OPS_SCN_WITHHOLD_SUCCESS", 0L);
        long configId = insertConfig(null, 10, "OPS_SCN_WITHHOLD_SUCCESS", "0.5000", 0);
        long runId = insertRun(configId);

        executor.execute(runId);

        List<AccountingResultEntity> results = runs.findResults(runId);
        assertEquals("PASS", results.get(1).status());
        assertEquals("ERROR", results.get(2).status());
        assertEquals(10, results.get(2).anomalyCount());
        assertTrue(results.get(2).message().contains("成功笔数为 0"));
        // 固定预期不渲染 expected SQL
        verify(functionCalls, never()).executeStatements(anyString(), anyString(),
                org.mockito.ArgumentMatchers.argThat(argThatStatements("OPS_SCN_WITHHOLD_EXPECTED")));
    }

    @Test
    void expectedSqlFailureMarksDimensionFailedWithoutKillingRun() {
        renderFailureCode = "OPS_SCN_WITHHOLD_EXPECTED";
        renderFailureMessage = "SQL 编码已停用：OPS_SCN_WITHHOLD_EXPECTED";
        counts.put("OPS_SCN_WITHHOLD_ACTUAL", 5L);
        long configId = insertConfig("OPS_SCN_WITHHOLD_EXPECTED", null, null, "0.0000", 0);
        long runId = insertRun(configId);

        executor.execute(runId);

        AccountingRunEntity run = runs.findById(runId);
        assertEquals("PARTIAL", run.status());
        List<AccountingResultEntity> results = runs.findResults(runId);
        assertEquals("FAILED", results.get(1).status());
        assertTrue(results.get(1).message().contains("SQL 编码已停用"));
    }

    @Test
    void actualQueryFailureFailsWholeRun() {
        actualQueryError = true;
        long configId = insertConfig(null, 10, null, "0.0000", 0);
        long runId = insertRun(configId);

        executor.execute(runId);

        AccountingRunEntity run = runs.findById(runId);
        assertEquals("FAILED", run.status());
        assertTrue(run.errorMessage().contains("只读网关不可用"));
        assertTrue(runs.findResults(runId).isEmpty());
    }

    @Test
    void alertEnabledPushesWecomOnAbnormalResults() {
        counts.put("OPS_SCN_WITHHOLD_EXPECTED", 100L);
        counts.put("OPS_SCN_WITHHOLD_ACTUAL", 60L);
        long configId = insertConfig("OPS_SCN_WITHHOLD_EXPECTED", null, null, "0.0500", 1);
        long runId = insertRun(configId);

        executor.execute(runId);

        verify(wecom).sendMarkdownMessage(contains("accounting-scenario-"), contains("应发起 100 笔，实际发起 60 笔"));
    }

    private static org.mockito.ArgumentMatcher<List<FunctionCallService.Statement>> argThatStatements(String sqlCode) {
        return statements -> statements.stream().anyMatch(statement -> sqlCode.equals(statement.sqlCode()));
    }

    private long insertConfig(String expectedSqlCode, Integer fixedExpected, String successSqlCode,
                              String tolerance, int alertEnabled) {
        jdbc.update("""
                INSERT INTO ops_account_monitor_config(business_line_code,environment_code,system_name,display_name,
                    scenario_type,expected_time,check_time,expected_count_sql_code,expected_fixed_count,
                    actual_count_sql_code,success_count_sql_code,tolerance_rate,alert_enabled,enabled)
                VALUES ('BL000008','PROD','lease-saps','批量代扣','BATCH_WITHHOLD','08:00:00','08:30:00',
                    ?,?,'OPS_SCN_WITHHOLD_ACTUAL',?,?,?,1)
                """, expectedSqlCode, fixedExpected, successSqlCode, new BigDecimal(tolerance), alertEnabled);
        return configs.findByIdentity("BL000008", "PROD", "lease-saps", "BATCH_WITHHOLD").id();
    }

    private long insertRun(long configId) {
        String runNo = "AS" + System.nanoTime();
        runs.insertRun(runNo, runNo, configId, "MANUAL", start, start.plusDays(1), null, "PENDING", "tester");
        return runs.findIdByRunNo(runNo);
    }

    private void create(String source, String name) {
        var pattern = Pattern.compile("(?s)CREATE TABLE(?: IF NOT EXISTS)? `" + Pattern.quote(name) + "` \\(.*?;");
        var match = pattern.matcher(source);
        assertTrue(match.find(), name);
        jdbc.execute(match.group());
    }
}
