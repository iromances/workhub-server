package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentIssueMapper;
import cn.aslight.workhub.dao.agent.AgentMapper;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 用真实迁移与生产 Mapper 验证运行完成后的问题摄取：result_text 只存 summary 时从 CLI_RESULT 事件取 problems。 */
class AgentIssueServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private AgentIssueMapper issues;
    private AgentIssueService service;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = dataSource.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id, permission_code))");
        jdbc.update("INSERT INTO sys_role VALUES (1,'SUPER_ADMIN')");
        for (String script : new String[]{
                "../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261104_014200__add_agent_workbench.sql",
                "../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261114_200000__add_agent_issue.sql"}) {
            ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource(Path.of(script)));
        }
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class);
        configuration.addMapper(AgentIssueMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var mapper = session.getMapper(AgentMapper.class);
        issues = session.getMapper(AgentIssueMapper.class);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new AgentIssueService(issues, mapper, json));
        proxy.addAdvice(interceptor);
        service = (AgentIssueService) proxy.getProxy();
    }

    @AfterEach
    void close() throws Exception { keepAlive.close(); }

    @Test
    void ingestReadsProblemsFromCliResultEventWhenResultTextIsOnlySummary() {
        String run = completedRun("summary-only", "【总体结论】本轮巡检最高优先级【P1】。");
        jdbc.update("INSERT INTO pm_agent_event(id,run_id,event_key,ordinal,kind,payload_json,actor,created_at) VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), run, "cli:result", 3, "CLI_RESULT", json.writeValueAsString(java.util.Map.of(
                        "exitCode", 0,
                        "result", java.util.Map.of(
                                "type", "RESULT",
                                "summary", "【总体结论】本轮巡检最高优先级【P1】。",
                                "problems", java.util.List.of(
                                        java.util.Map.of("domain", "SYSTEM_ALERT", "signature", "scf-prod-xxl-fail", "title", "XXL 任务连续失败",
                                                "severity", "P1", "evidence", "5 次失败", "window", "10:00-11:00",
                                                "analysis", java.util.Map.of("codeLocation", "scf-saps/XxlJob.java:80", "repro", "调度触发即失败",
                                                        "rootCause", "数据源配置缺失", "impactVerification", "账单未生成", "fixDirection", "补配置并重跑",
                                                        "repos", java.util.List.of(java.util.Map.of("system", "scf-saps", "repository", "scf_group/scf-saps", "path", "src/main")))),
                                        java.util.Map.of("domain", "ACCOUNTING", "signature", "scf-prod-reconcile-miss", "title", "对账缺运行", "severity", "P2"))))),
                "agent-runtime", LocalDateTime.now(ZoneOffset.UTC));
        service.ingest(run);
        service.ingest(run); // 同一运行重放不得累计两次观测。
        var all = issues.list(null, null, null, null, null, null, 50);
        assertEquals(2, all.size());
        var analyzed = all.stream().filter(issue -> "scf-prod-xxl-fail".equals(issue.signature())).findFirst().orElseThrow();
        assertEquals("SYSTEM_ALERT", analyzed.domain());
        assertEquals("P1", analyzed.severity());
        assertEquals("ANALYZED", analyzed.status());
        assertEquals("BL000012", analyzed.businessLineCode());
        assertEquals("TEST", analyzed.environmentCode());
        assertEquals("数据源配置缺失", json.readTree(analyzed.analysisJson()).path("rootCause").asText());
        assertEquals(1, json.readTree(analyzed.analysisJson()).path("repos").size());
        var plain = all.stream().filter(issue -> "scf-prod-reconcile-miss".equals(issue.signature())).findFirst().orElseThrow();
        assertEquals("ACCOUNTING", plain.domain());
        assertEquals("P2", plain.severity());
        // 复发：同任务同 signature 第二轮再出现只累计观测并清零未复现
        String next = anotherRunOnSameTask(run, "summary-only-2");
        jdbc.update("INSERT INTO pm_agent_event(id,run_id,event_key,ordinal,kind,payload_json,actor,created_at) VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), next, "cli:result", 3, "CLI_RESULT", json.writeValueAsString(java.util.Map.of(
                        "exitCode", 0,
                        "result", java.util.Map.of("type", "RESULT", "summary", "无变化",
                                "problems", java.util.List.of(java.util.Map.of("domain", "SYSTEM_ALERT", "signature", "scf-prod-xxl-fail",
                                        "title", "XXL 任务连续失败", "severity", "P1", "evidence", "6 次失败", "window", "11:00-12:00"))))),
                "agent-runtime", LocalDateTime.now(ZoneOffset.UTC));
        service.ingest(next);
        assertEquals(2, issues.list(null, null, null, null, null, null, 50).size());
        var recurring = issues.findBySignature(taskId(), "scf-prod-xxl-fail");
        assertEquals(2, recurring.occurrenceCount());
        assertEquals(0, recurring.unseenRounds());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_audit WHERE object_type='ISSUE'", Integer.class));
    }

    @Test
    void ingestStillParsesProblemsEmbeddedInResultTextAndSkipsBadRows() {
        String embedded = "{\"type\":\"RESULT\",\"summary\":\"汇总\",\"problems\":["
                + "{\"domain\":\"BUSINESS_MONITOR\",\"signature\":\"ol-test-monitor-miss\",\"title\":\"无监测配置\",\"severity\":\"P2\"},"
                + "{\"domain\":\"UNKNOWN\",\"signature\":\"bad-domain\",\"title\":\"坏域\",\"severity\":\"P0\"}]}";
        String run = completedRun("embedded-json", embedded);
        service.ingest(run);
        var all = issues.list(null, null, null, null, null, null, 50);
        assertEquals(1, all.size());
        assertEquals("BUSINESS_MONITOR", all.getFirst().domain());
        assertEquals(0, all.getFirst().unseenRounds());
        // 未运行完成或运行不存在的摄取是空操作
        service.ingest(UUID.randomUUID().toString());
        assertEquals(1, issues.list(null, null, null, null, null, null, 50).size());
    }

    private String taskId() { return jdbc.queryForObject("SELECT id FROM pm_agent_task", String.class); }

    /** 同一任务下再造一轮 COMPLETED 运行：复用首轮的任务/Agent 与配置快照，仅换结果文本。 */
    private String anotherRunOnSameTask(String firstRunId, String resultText) {
        var row = jdbc.queryForMap("SELECT task_id, agent_id, configuration_json, specification_json, provider_binding_json FROM pm_agent_run WHERE id=?", firstRunId);
        String run = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO pm_agent_run(id,task_id,agent_id,trigger_key,requested_by,configuration_json,specification_json,provider_binding_json,"
                        + "status,step_count,elapsed_millis,version,created_at,updated_at,result_text) VALUES(?,?,?,?,?,?,?,?, 'COMPLETED',2,120,0,?,?,?)",
                run, row.get("task_id"), row.get("agent_id"), "run-" + UUID.randomUUID(), "admin",
                row.get("configuration_json"), row.get("specification_json"), row.get("provider_binding_json"), now, now, resultText);
        return run;
    }

    /** 建一个启用 Agent、单任务与一条 COMPLETED 运行；requestKey 唯一以便多次造数。 */
    private String completedRun(String requestKey, String resultText) {
        String agent = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO pm_agent_definition(id,name,configuration_json,enabled,owner_name,version,created_at,updated_at) VALUES(?,?,?,0,'owner',0,?,?)",
                agent, "巡检助手", json.writeValueAsString(java.util.Map.of("name", "巡检助手", "instructions", "按口径巡检", "providerConfigId", 1,
                        "model", "model-x", "scopes", java.util.List.of(), "maxSteps", 20, "maxRunSeconds", 600, "maxConcurrentRuns", 1)), now, now);
        String task = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO pm_agent_task(id,agent_id,request_key,request_hash,specification_json,requested_by,schedule_mode,zone_id,misfire_policy,schedule_enabled,version,created_at,updated_at) "
                        + "VALUES(?,?,?,?,'{\"objective\":\"巡检\",\"context\":null,\"acceptanceCriteria\":\"出报告\",\"expectedArtifacts\":[\"REPORT\"],"
                        + "\"scopes\":[{\"businessLineCode\":\"BL000012\",\"environmentCode\":\"TEST\"}],\"completionTargets\":[],\"analysisScope\":\"INCREMENTAL\"}',"
                        + "'admin','MANUAL','Asia/Shanghai','RUN_ONCE',0,0,?,?)",
                task, agent, requestKey, UUID.randomUUID().toString().replace("-", ""), now, now);
        String run = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO pm_agent_run(id,task_id,agent_id,trigger_key,requested_by,configuration_json,specification_json,provider_binding_json,"
                        + "status,step_count,elapsed_millis,version,created_at,updated_at,result_text) VALUES(?,?,?,?,?,?,?,?, 'COMPLETED',2,120,0,?,?,?)",
                run, task, agent, "run-" + requestKey, "admin",
                json.writeValueAsString(java.util.Map.of("name", "巡检助手", "instructions", "按口径巡检", "providerConfigId", 1,
                        "model", "model-x", "scopes", java.util.List.of(), "maxSteps", 20, "maxRunSeconds", 600, "maxConcurrentRuns", 1)),
                "{\"objective\":\"巡检\",\"scopes\":[{\"businessLineCode\":\"BL000012\",\"environmentCode\":\"TEST\"}],\"expectedArtifacts\":[\"REPORT\"]}",
                "{}", now, now, resultText);
        return run;
    }
}
