package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.dao.ops.AccountingExecutionEvidenceMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingExecutionEvidenceEntity;
import cn.aslight.workhub.model.ops.AccountingExecutionSnapshot;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-d227e09d：执行器不再写凭据；本测试守住历史凭据读取校验与 Agent 来源读取链路
 *  （配置表按迁移后的场景结构建表）。 */
class AccountingExecutionEvidenceServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private Connection connection;
    private JdbcTemplate jdbc;
    private AccountingRunMapper runs;
    private AccountingExecutionEvidenceMapper records;
    private AgentMonitorReadMapper history;
    private AccountingExecutionEvidenceService evidence;
    private final LocalDateTime start = LocalDateTime.of(2026, 11, 4, 10, 0);

    @BeforeEach void setup() throws Exception {
        var db = new JdbcDataSource(); db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        connection = db.getConnection(); jdbc = new JdbcTemplate(db);
        String prefix = "../workhub-bootstrap/src/main/resources/db/schema/mysql/";
        create(Files.readString(Path.of(prefix + "V1__init.sql")), "pm_business_line");
        String old = Files.readString(Path.of(prefix + "V20260723_210000__add_accounting_monitoring.sql"));
        for (String table : List.of("ops_account_reconcile_run", "ops_account_reconcile_result")) create(old, table);
        jdbc.execute(Files.readString(Path.of(prefix + "V20261104_132500__add_accounting_execution_evidence.sql")));
        // 场景化改造后的配置表结构（对应 V20261118_210000 迁移结果）
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
        jdbc.update("INSERT INTO pm_business_line(business_line_code,business_line_name) VALUES ('BL000012','工作台'),('BL000001','其他')");
        jdbc.update("""
                INSERT INTO ops_account_monitor_config(id,business_line_code,environment_code,system_name,display_name,
                    scenario_type,expected_time,check_time,expected_count_sql_code,expected_fixed_count,
                    actual_count_sql_code,success_count_sql_code,tolerance_rate,alert_enabled,enabled)
                VALUES (1,'BL000012','TEST','withhold-system','批量代扣','BATCH_WITHHOLD','08:00:00','08:30:00',
                    'OPS_SCN_WITHHOLD_EXPECTED',NULL,'OPS_SCN_WITHHOLD_ACTUAL','OPS_SCN_WITHHOLD_SUCCESS',0.0050,1,1)
                """);
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(AccountingRunMapper.class, AccountingExecutionEvidenceMapper.class, AgentMonitorReadMapper.class)) configuration.addMapper(type);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(db); factory.setConfiguration(configuration);
        var sql = new SqlSessionTemplate(factory.getObject());
        runs = sql.getMapper(AccountingRunMapper.class);
        records = sql.getMapper(AccountingExecutionEvidenceMapper.class);
        history = sql.getMapper(AgentMonitorReadMapper.class);
        evidence = new AccountingExecutionEvidenceService(records, json);
    }
    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }

    @Test void runsWithoutEvidenceStayUnverifiedButHistoryReadsScenarioConfigIdentity() {
        long id = run("no-evidence");
        var row = runs.findById(id);
        assertNull(evidence.read(row));
        assertFalse(evidence.describe(row).path("executionEvidence").path("scopeVerified").asBoolean());
        assertEquals(List.of(id), history.accountingRuns("BL000012", "TEST", null, 0, 10).stream().map(AccountingRunEntity::id).toList());
        assertTrue(history.accountingRuns("BL000001", "TEST", null, 0, 10).isEmpty());
        assertEquals(id, history.accountingUpper("BL000012", "TEST"));
        // 配置删除后历史运行仍可经凭据兜底读取；无凭据的历史运行业务线为空但不报错。
        jdbc.update("DELETE FROM ops_account_monitor_config WHERE id=1");
        assertNull(runs.findById(id).businessLineCode());
        assertEquals(1, runs.countRuns(null, null, null));
    }

    @Test void runsFilterByConfigFollowLatestRunEvidenceGuard() throws Exception {
        long mine = run("config-1");
        seedEvidence(mine);
        jdbc.update("""
                INSERT INTO ops_account_monitor_config(id,business_line_code,environment_code,system_name,display_name,
                    scenario_type,expected_time,check_time,expected_count_sql_code,expected_fixed_count,
                    actual_count_sql_code,success_count_sql_code,tolerance_rate,alert_enabled,enabled)
                VALUES (2,'BL000001','TEST','other-system','其他配置','BATCH_WITHHOLD','08:00:00','08:30:00',
                    NULL,0,'OPS_OTHER_ACTUAL',NULL,0,0,1)
                """);
        runs.insertRun("config-2", "config-2", 2L, "MANUAL", start, start.plusHours(1), null, "PENDING", "fixture");

        // 按配置过滤：只含该配置的批次；不传 configId 时行为不变。
        assertEquals(List.of(mine), runs.findRuns(null, null, 1L, 0, 10).stream().map(AccountingRunEntity::id).toList());
        assertEquals(1, runs.countRuns(null, null, 1L));
        assertEquals(2, runs.countRuns(null, null, null));

        // 凭据环境与配置不一致时按 findLatestByConfigId 同口径排除。
        jdbc.update("UPDATE ops_account_execution_evidence SET environment_code='PROD' WHERE run_id=?", mine);
        assertEquals(0, runs.countRuns(null, null, 1L));
    }

    @Test void seededEvidenceReadsVerifiedAndTamperingFails() throws Exception {
        long id = run("verified");
        seedEvidence(id);
        var row = runs.findById(id);
        var verified = evidence.read(row);
        assertNotNull(verified);
        assertEquals("a".repeat(64), verified.snapshot().targetBindingHash());
        assertEquals("TEST", verified.snapshot().configuration().environmentCode());
        assertTrue(evidence.describe(row).path("executionEvidence").path("scopeVerified").asBoolean());

        jdbc.update("UPDATE ops_account_execution_evidence SET content_hash='broken' WHERE run_id=?", id);
        assertThrows(IllegalStateException.class, () -> evidence.read(runs.findById(id)));
        jdbc.update("UPDATE ops_account_execution_evidence SET content_hash=? WHERE run_id=?",
                AccountingExecutionEvidenceService.hash(records.find(id).snapshotJson()), id);

        jdbc.update("UPDATE ops_account_execution_evidence SET environment_code='PROD' WHERE run_id=?", id);
        assertThrows(IllegalStateException.class, () -> evidence.read(runs.findById(id)));
        jdbc.update("UPDATE ops_account_execution_evidence SET environment_code='TEST' WHERE run_id=?", id);

        jdbc.update("UPDATE ops_account_reconcile_run SET end_time=? WHERE id=?", start.plusHours(2), id);
        assertThrows(IllegalStateException.class, () -> evidence.read(runs.findById(id)));
    }

    private long run(String key) {
        runs.insertRun(key, key, 1L, "MANUAL", start, start.plusHours(1), null, "PENDING", "fixture");
        return runs.findIdByRunNo(key);
    }

    /** 按读校验口径构造一份合法历史凭据（快照各哈希与再序列化一致）。 */
    private void seedEvidence(long runId) throws Exception {
        jdbc.update("UPDATE ops_account_reconcile_run SET status='RUNNING' WHERE id=?", runId);
        var configuration = new AccountingExecutionSnapshot.Configuration(1L, "BL000012", "TEST",
                "withhold-system", "批量代扣", null, null, null, true);
        var rules = List.<AccountingExecutionSnapshot.Rule>of();
        var snapshot = new AccountingExecutionSnapshot(1, runId, configuration,
                AccountingExecutionEvidenceService.hash(json.writeValueAsString(configuration)),
                rules, AccountingExecutionEvidenceService.hash(json.writeValueAsString(rules)),
                start, start.plusHours(1), "Asia/Shanghai", "a".repeat(64));
        String payload = json.writeValueAsString(snapshot);
        assertEquals(1, records.insert(new AccountingExecutionEvidenceEntity(runId, "BL000012", "TEST",
                "withhold-system", "批量代扣", payload,
                AccountingExecutionEvidenceService.hash(payload), LocalDateTime.now())));
    }

    private void create(String source, String name) {
        var pattern = Pattern.compile("(?s)CREATE TABLE(?: IF NOT EXISTS)? `" + Pattern.quote(name) + "` \\(.*?;");
        var match = pattern.matcher(source); assertTrue(match.find(), name); jdbc.execute(match.group());
    }
}
