package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 从仓库真实 DDL 建内存表，验证范围过滤、字段映射和续页，而非模拟 Mapper 输出。 */
class AgentMonitorReadMapperTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private AgentMonitorReadMapper mapper;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        keepAlive = ds.getConnection(); jdbc = new JdbcTemplate(ds);
        String baseline = Files.readString(Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql/V1__init.sql"));
        String accounting = Files.readString(Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20260723_210000__add_accounting_monitoring.sql"));
        for (String table : List.of("pm_business_line", "pm_project", "pm_work_item")) createTable(baseline, table);
        for (String table : List.of("ops_account_monitor_config", "ops_account_reconcile_run", "ops_account_reconcile_result")) createTable(accounting, table);
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true); configuration.addMapper(AgentMonitorReadMapper.class);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(AgentMonitorReadMapper.class);
        jdbc.update("INSERT INTO pm_business_line(business_line_code,business_line_name) VALUES ('BL000012','工作台'),('BL000001','其他业务线')");
        config(1, "BL000012", "TEST"); config(2, "BL000012", "PROD"); config(3, "BL000001", "TEST");
    }
    @AfterEach void close() throws Exception { if (keepAlive != null) keepAlive.close(); }

    @Test void historicalRunPagesIncludeOldFailureAndCannotCrossEnvironmentOrBusinessLine() {
        run(1, 1, "FAILED"); run(2, 1, "SUCCESS"); run(3, 2, "FAILED"); run(4, 3, "FAILED"); run(5, 1, "SUCCESS");
        var first = mapper.accountingRuns("BL000012", "TEST", null, 0, 2);
        assertEquals(List.of(5L, 2L), first.stream().map(value -> value.id()).toList());
        run(9, 1, "SUCCESS");
        var next = mapper.accountingRuns("BL000012", "TEST", 1L, first.getLast().id(), 2);
        assertEquals(1, next.size()); assertEquals("FAILED", next.getFirst().status());
        assertEquals("BL000012", next.getFirst().businessLineCode()); assertEquals("工作台", next.getFirst().businessLineName());
        assertTrue(mapper.accountingRuns("BL000012", "TEST", 2L, 0, 20).isEmpty());
        assertTrue(mapper.accountingRuns("BL000012' OR 1=1 --", "TEST", null, 0, 20).isEmpty());
    }

    @Test void rulePagesRemainBoundToRunAndRetainDecimalEvidence() {
        run(1, 1, "SUCCESS"); run(2, 2, "SUCCESS");
        rule(21, 1); rule(22, 1); rule(23, 1); rule(24, 2);
        var first = mapper.accountingResults(1, 20, 2);
        assertEquals(List.of(21L, 22L), first.stream().map(value -> value.id()).toList());
        assertEquals("123.45", first.getFirst().differenceAmount().toPlainString());
        var next = mapper.accountingResults(1, first.getLast().id(), 2);
        assertEquals(List.of(23L), next.stream().map(value -> value.id()).toList());
        assertTrue(mapper.accountingResults(1, 23, 2).isEmpty());
    }

    @Test void bugQueriesUseActualProjectLineAndTypeBeforeReturningDescription() {
        project(1, "BL000012"); project(2, "BL000001");
        bug(1, 1, "缺陷", "处理中"); bug(2, 2, "缺陷", "处理中"); bug(3, 1, "需求", "处理中"); bug(4, 1, "缺陷", "已完成");
        var first = mapper.bugs("BL000012", null, 0, 1);
        assertEquals(4L, first.getFirst().id()); assertNull(first.getFirst().description());
        assertEquals(List.of(1L), mapper.bugs("BL000012", null, 4, 20).stream().map(value -> value.id()).toList());
        assertEquals(4L, mapper.bugs("BL000012", "已完成", 0, 10).getFirst().id());
        var detail = mapper.bug("BL000012", 1);
        assertEquals("实际缺陷描述", detail.description()); assertEquals("验收条件", detail.acceptanceCriteria());
        assertEquals("BL000012", detail.businessLineCode()); assertNotNull(detail.createdAt());
        assertNull(mapper.bug("BL000012", 2)); assertNull(mapper.bug("BL000012", 3));
    }

    private void createTable(String schema, String table) {
        var match = Pattern.compile("(?s)CREATE TABLE(?: IF NOT EXISTS)? `" + Pattern.quote(table) + "` \\(.*?;").matcher(schema);
        assertTrue(match.find(), table); jdbc.execute(match.group());
    }
    private void config(long id, String line, String environment) {
        jdbc.update("INSERT INTO ops_account_monitor_config(id,business_line_code,environment_code,system_name,display_name,schema_name,rule_profile) VALUES (?,?,?,?,?,'fixture','fixture')", id, line, environment, "system" + id, "监测" + id);
    }
    private void run(long id, long config, String status) {
        jdbc.update("INSERT INTO ops_account_reconcile_run(id,run_no,idempotency_key,monitor_config_id,trigger_type,start_time,end_time,status,requested_by) VALUES (?,?,?,?,'MANUAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'fixture')", id, "run" + id, "key" + id, config, status);
    }
    private void rule(long id, long run) {
        jdbc.update("INSERT INTO ops_account_reconcile_result(id,run_id,rule_code,rule_name,category,granularity,severity,status,difference_amount) VALUES (?,?,?,?,'fixture','ORDER','WARN','WARN',123.45)", id, run, "rule" + id, "规则" + id);
    }
    private void project(long id, String line) {
        jdbc.update("INSERT INTO pm_project(id,project_code,project_name,project_type,business_line_code,project_status,owner_user_name) VALUES (?,?,?,'SYSTEM',?,'ACTIVE','fixture')", id, "project" + id, "项目" + id, line);
    }
    private void bug(long id, long project, String type, String status) {
        jdbc.update("INSERT INTO pm_work_item(id,work_item_no,project_id,work_item_type,title,description,source_type,source_channel,priority,status,creator_user_name,owner_user_name,follower_user_name,acceptance_criteria) VALUES (?,?,?,?,?,'实际缺陷描述','MANUAL','fixture','P1',?,'fixture','fixture','fixture','验收条件')", id, "item" + id, project, type, "标题" + id, status);
    }
}
