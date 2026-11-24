package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ops.AccountingExecutionSnapshot;
import cn.aslight.workhub.dao.ops.AccountingExecutionEvidenceMapper;
import cn.aslight.workhub.service.ops.AccountingExecutionEvidenceService;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import cn.aslight.workhub.service.ai.AiAgentApiRequest;
import cn.aslight.workhub.service.auth.PermissionContextService;
import cn.aslight.workhub.service.autonomy.*;
import cn.aslight.workhub.model.autonomy.AutonomyProposal;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static cn.aslight.workhub.service.agent.AgentInspectionService.Source.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实迁移、Mapper、授权、运行句柄及事务；来源为本地 H2 固定数据，不连接线上。 */
class AgentInspectionServiceTest {
    private static final String SCHEMA = "../workhub-bootstrap/src/main/resources/db/schema/mysql/";
    private final ObjectMapper json = new ObjectMapper();
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T04:00:00Z"), ZoneOffset.UTC);
    private final AtomicInteger sequence = new AtomicInteger();
    private final Set<String> grants = new HashSet<>();
    private Connection keepAlive;
    private ValidatorFactory validation;
    private JdbcTemplate jdbc;
    private TransactionInterceptor transactions;
    private AgentMapper agents;
    private AgentInspectionMapper mapper;
    private AgentMonitorReadMapper sources;
    private AutonomyMapper autonomy;
    private AgentRunStore runs;
    private AgentWorkbenchService workbench;
    private AgentObservationService observations;
    private AutonomyCaseService cases;
    private AgentInspectionService inspections;
    private AccountingExecutionEvidenceService executionEvidence;
    private AgentAccessService access;
    private AgentDefinitionResponse agent;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = ds.getConnection(); jdbc = new JdbcTemplate(ds);
        String baseline = Files.readString(Path.of(SCHEMA + "V1__init.sql"));
        for (String table : List.of("pm_business_line", "pm_project", "pm_work_item", "ops_system_alert_event")) createTable(baseline, table);
        String account = Files.readString(Path.of(SCHEMA + "V20260723_210000__add_accounting_monitoring.sql"));
        for (String table : List.of("ops_account_monitor_config", "ops_account_reconcile_run", "ops_account_reconcile_result")) createTable(account, table);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        jdbc.update("INSERT INTO sys_role VALUES (1,'SUPER_ADMIN')");
        // H2 不支持旧迁移在单条 ALTER 中同时 ADD COLUMN/ADD KEY；只应用原文中的字段定义，索引留给真实 MySQL 验证。
        String category = Files.readString(Path.of(SCHEMA + "V20260805_173000__categorize_system_alert_slow_sql.sql"));
        jdbc.execute(category.substring(0, category.indexOf(",\n  ADD KEY")));
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql",
                "V20261104_035107__add_agent_observations_and_cases.sql", "V20261104_174329__add_autonomy_case_resolution.sql", "V20261104_094232__add_agent_inspection_progress.sql",
                "V20261104_103540__add_autonomy_bug_links.sql", "V20261104_132500__add_accounting_execution_evidence.sql"))
            ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource(SCHEMA + file));
        jdbc.update("INSERT INTO pm_business_line(business_line_code,business_line_name) VALUES ('BL000012','工作台'),('BL000001','其他业务线')");
        jdbc.update("INSERT INTO pm_project(id,project_code,project_name,project_type,business_line_code,project_status,owner_user_name) VALUES (1,'test','测试','SYSTEM','BL000012','ACTIVE','owner'),(2,'other','其他','SYSTEM','BL000001','ACTIVE','owner')");
        jdbc.update("INSERT INTO ops_account_monitor_config(id,business_line_code,environment_code,system_name,display_name,schema_name,rule_profile) VALUES (1,'BL000012','TEST','test','测试','fixture','fixture'),(2,'BL000012','PROD','prod','生产','fixture','fixture')");
        jdbc.execute("CREATE TABLE dict(id BIGINT AUTO_INCREMENT PRIMARY KEY, dict_type VARCHAR(64), dict_desc VARCHAR(128), enum_code VARCHAR(128), enum_desc VARCHAR(256), parent_code VARCHAR(128), root_code VARCHAR(128), sort_order INT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, create_by VARCHAR(64), modify_time DATETIME, delete_time DATETIME, delete_by VARCHAR(64), is_delete TINYINT DEFAULT 0, version INT DEFAULT 0, remark VARCHAR(512))");
        jdbc.update("INSERT INTO dict(dict_type,enum_code,enum_desc) VALUES ('agentPosition','OPS','运维')");
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(AgentMapper.class, AgentInspectionMapper.class, AgentMonitorReadMapper.class, AutonomyMapper.class, AccountingExecutionEvidenceMapper.class, cn.aslight.workhub.dao.system.DictMapper.class)) configuration.addMapper(type);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject());
        agents = sessions.getMapper(AgentMapper.class); mapper = sessions.getMapper(AgentInspectionMapper.class);
        sources = sessions.getMapper(AgentMonitorReadMapper.class); autonomy = sessions.getMapper(AutonomyMapper.class);
        executionEvidence = new AccountingExecutionEvidenceService(sessions.getMapper(AccountingExecutionEvidenceMapper.class), json);
        transactions = new TransactionInterceptor(); transactions.setTransactionManager(new DataSourceTransactionManager(ds));
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var permissions = mock(PermissionContextService.class); var user = new SysUserEntity(); user.setId(1L); user.setUserName("owner");
        when(permissions.requireActiveUser("owner")).thenReturn(user);
        grants.addAll(Set.of("agent:view", "agent:manage", "agent:task:assign", "agent:task:view", "agent:task:operate",
                "autonomy:case:view", "autonomy:case:operate", "ops:accounting:view", "ops:system-alert:view", "work-item:view"));
        when(permissions.permissionCodes(1L)).thenAnswer(call -> List.copyOf(grants));
        var lines = mock(BusinessLineMapper.class); var line = new BusinessLineEntity(); line.setEnabled(true); line.setSupportedEnvironments("TEST,PROD");
        when(lines.findByCode("BL000012")).thenReturn(line);
        access = new AgentAccessService(permissions, lines, agents, json);
        var providers = mock(AiProviderConfigService.class); var provider = new AiProviderConfigEntity();
        provider.setId(1L); provider.setEnabled(true); provider.setChannelType("API"); provider.setDefaultModel("fixture");
        when(providers.requireExisting(1L)).thenReturn(provider); when(providers.getEnabledById(1L)).thenReturn(provider);
        var data = new AgentToolData(json); runs = proxy(new AgentRunStore(agents, json, access, data, providers, clock, AgentAcceptanceTestSupport.withoutCode(agents, access, json, clock)));
        observations = proxy(new AgentObservationService(autonomy, agents, runs, access, json, data));
        validation = Validation.buildDefaultValidatorFactory();
        cases = proxy(new AutonomyCaseService(autonomy, agents, runs, access, new AutonomyEvidence(json), data, json, validation.getValidator(), sources));
        inspections = service(mapper);
        var cryptoProperties = new cn.aslight.workhub.config.PaymentProperties();
        cryptoProperties.setMasterKey("test-master-key");
        workbench = proxy(new AgentWorkbenchService(agents, access, providers,
                new cn.aslight.workhub.service.system.DictService(sessions.getMapper(cn.aslight.workhub.dao.system.DictMapper.class)),
                new cn.aslight.workhub.service.payment.PaymentCryptoService(cryptoProperties), json, clock));
        agent = workbench.create(new AgentSaveRequest(null, new AgentConfiguration("巡检","OPS", "主动巡检", 1L, "fixture", List.of(scope), 200, 3600, 8)), "owner");
        agent = workbench.setEnabled(agent.id(), agent.version(), true, "owner");
    }
    @AfterEach void close() throws Exception { if (validation != null) validation.close(); if (keepAlive != null) keepAlive.close(); }

    @Test void sameCallAndPendingRecordReplayUntilExplicitAnalysis() {
        bug(1, 1, "原始描述"); bug(2, 1, "后续");
        var run = run(null); var args = nextArgs(BUG);
        String key = begin(run, "inspection.next", args);
        var first = (JsonNode) raw(run, "inspection.next", args);
        assertEquals(json.writeValueAsString(first), json.writeValueAsString(raw(run, "inspection.next", args)));
        assertThrows(IllegalArgumentException.class, () -> inspections.next(run, BUG, "BL000012", null, 999L));
        endTool(run, key, first);
        assertEquals(first.path("itemId"), next(run, BUG).path("itemId"));
        ack(run, first, "NO_FINDING");
        var second = next(run, BUG); assertEquals(2L, second.path("sourceId").asLong());
        // 恢复原始已提交调用只回放旧回执，不把下一条当作旧调用结果。
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(Map.of("kind", "TOOL", "key", key, "tool", "inspection.next")), run.runId());
        assertEquals(json.writeValueAsString(first), json.writeValueAsString(inspections.next(run, BUG, "BL000012", null, null)));
        assertEquals(2, count("pm_agent_inspection_item")); assertEquals(1, count("pm_agent_inspection_analysis"));
    }

    @Test void finiteSweepRescansLateLowerIdsAndChangedRecords() {
        for (int id = 10; id <= 30; id++) bug(id, 1, "原始" + id);
        var run = run(null); var first = next(run, BUG);
        assertEquals(30, first.at("/progress/upperId").asLong());
        bug(1, 1, "迟到"); bug(100, 1, "下一轮");
        for (int id = 10; id <= 29; id++) { var item = id == 10 ? first : next(run, BUG); assertEquals(id, item.path("sourceId").asLong()); ack(run, item, "NO_FINDING"); }
        var last = next(run, BUG); assertEquals(30, last.path("sourceId").asLong()); ack(run, last, "NO_FINDING");
        assertEquals(1, last.at("/progress/sweepCount").asLong());
        var late = next(run, BUG); assertEquals(1, late.path("sourceId").asLong()); ack(run, late, "NO_FINDING");
        var newer = next(run, BUG); assertEquals(100, newer.path("sourceId").asLong()); ack(run, newer, "NO_FINDING");
        jdbc.update("UPDATE pm_work_item SET description='修订后的描述' WHERE id=10");
        var changed = next(run, BUG); assertEquals(10, changed.path("sourceId").asLong());
        assertNotEquals(first.path("itemId"), changed.path("itemId"));
        assertEquals(24, count("pm_agent_inspection_item"));
    }

    @Test void needsInputRemainsVisibleAndRetryKeepsAnalysisHistory() {
        bug(1, 1, "环境待核实"); var run = run(null); var item = next(run, BUG);
        assertEquals("UNRESOLVED", item.at("/observation/environmentState").asText());
        assertFalse(item.at("/observation").has("observationId"));
        ack(run, item, "NEEDS_INPUT");
        var pending = invoke(run, "inspection.pending", nextArgs(BUG));
        assertEquals(1, pending.path("total").asInt()); assertEquals("NEEDS_INPUT", pending.at("/items/0/analysis/outcome").asText());
        var args = nextArgs(BUG).deepCopy(); args.put("retryItemId", item.path("itemId").asLong());
        var retried = invoke(run, "inspection.next", args);
        assertEquals(item.path("itemId"), retried.path("itemId")); assertEquals(0, retried.path("waitingForInput").asInt());
        ack(run, item, "NO_FINDING");
        assertEquals(2, count("pm_agent_inspection_analysis"));
        assertEquals(0, invoke(run, "inspection.pending", nextArgs(BUG)).path("total").asInt());
    }

    @Test void concurrentRunsHaveOneOwnerAndUnknownExecutionCannotBeStolen() throws Exception {
        alert(1, "TEST", "异常"); var first = run(null); var second = run(first.taskId());
        JsonNode a, b;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var fa = pool.submit(() -> next(first, SYSTEM_ALERT)); var fb = pool.submit(() -> next(second, SYSTEM_ALERT));
            a = fa.get(10, TimeUnit.SECONDS); b = fb.get(10, TimeUnit.SECONDS);
        }
        assertNotEquals(a.path("state"), b.path("state")); assertEquals(1, count("pm_agent_inspection_item"));
        var owner = "AWAITING_ANALYSIS".equals(a.path("state").asText()) ? first : second;
        var other = owner == first ? second : first;
        jdbc.update("UPDATE pm_agent_run SET status='WAITING_INPUT',owner_token=NULL,external_handle=NULL WHERE id=?", owner.runId());
        assertEquals("WAITING_OTHER_RUN", next(other, SYSTEM_ALERT).path("state").asText());
        jdbc.update("UPDATE pm_agent_run SET status='FAILED',owner_token=NULL,external_handle='{}' WHERE id=?", owner.runId());
        assertEquals("WAITING_OTHER_RUN", next(other, SYSTEM_ALERT).path("state").asText());
        jdbc.update("UPDATE pm_agent_run SET external_handle=NULL WHERE id=?", owner.runId());
        inspections = service(mapper); // 模拟服务重建，状态完全来自数据库。
        String key = begin(other, "inspection.next", nextArgs(SYSTEM_ALERT));
        var recovered = inspections.next(other, SYSTEM_ALERT, "BL000012", "TEST", null);
        endTool(other, key, recovered);
        assertEquals("AWAITING_ANALYSIS", recovered.path("state").asText());
        assertEquals(other.runId(), autonomy.observation(recovered.at("/observation/observationId").asText()).runId());
        assertEquals(2, count("pm_agent_observation"));
    }

    @Test void sourcePermissionsScopeAndTaskCannotBeBypassedByGenericTools() {
        bug(1, 2, "其他业务线"); bug(2, 1, "本业务线"); alert(1, "PROD", "生产日志");
        var run = run(null); var item = next(run, BUG); assertEquals(2, item.path("sourceId").asInt());
        assertEquals("SCAN_ADVANCED", next(run, SYSTEM_ALERT).path("state").asText());
        var invalid = nextArgs(BUG); invalid.put("environmentCode", "TEST");
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "inspection.next", invalid));
        var otherTask = run(null);
        assertThrows(IllegalArgumentException.class, () -> ack(otherTask, item, "NO_FINDING"));
        grants.remove("ops:system-alert:view");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> next(run, SYSTEM_ALERT));
        assertNull(agents.findRun(run.runId()).externalHandle());
    }

    @Test void receiptFailureRollsBackSnapshotsCursorAndObservationTogether() {
        alert(1, "TEST", "失败回滚样本"); var run = run(null);
        var broken = spy(mapper); doThrow(new IllegalStateException("回执写入失败")).when(broken).insertCall(any());
        var service = service(broken); String key = begin(run, "inspection.next", nextArgs(SYSTEM_ALERT));
        assertThrows(IllegalStateException.class, () -> service.next(run, SYSTEM_ALERT, "BL000012", "TEST", null));
        for (String table : List.of("pm_agent_inspection_stream", "pm_agent_inspection_item", "pm_agent_inspection_call", "pm_agent_observation")) assertEquals(0, count(table), table);
        var retried = inspections.next(run, SYSTEM_ALERT, "BL000012", "TEST", null);
        endTool(run, key, retried);
        assertEquals(1, retried.path("sourceId").asInt());
    }

    @Test void findingMustMatchActualSignalAndDeduplicatedSignalsRemainUsable() {
        alert(1, "TEST", "同一条异常"); var run = run(null); var item = next(run, SYSTEM_ALERT);
        var args = ackArgs(item, "FINDING"); args.put("caseId", "missing");
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "inspection.ack", args));
        String caseId = record(run, item.at("/observation/observationId").asText(), "/data/events/0", "APPLICATION_ERROR");
        // 未确认的记录交给后续运行，再登记将复用同一个 signal，而不强制 signal 的原 observation 等于新 observation。
        jdbc.update("UPDATE pm_agent_run SET status='COMPLETED',owner_token=NULL,external_handle=NULL WHERE id=?", run.runId());
        var nextRun = run(run.taskId()); var replayed = next(nextRun, SYSTEM_ALERT);
        assertEquals(caseId, record(nextRun, replayed.at("/observation/observationId").asText(), "/data/events/0", "APPLICATION_ERROR"));
        var acceptedArgs = ackArgs(replayed, "FINDING"); acceptedArgs.put("caseId", caseId);
        var accepted = invoke(nextRun, "inspection.ack", acceptedArgs);
        assertFalse(accepted.path("resolved").asBoolean()); assertEquals(1, count("pm_autonomy_signal"));
        assertEquals("DISCOVERED", autonomy.find(caseId).status());
    }

    @Test void acknowledgmentAndHistoryRollbackTogetherAndCommittedCallIsIdempotent() {
        bug(1, 1, "确认事务"); var run = run(null); var item = next(run, BUG);
        String key = begin(run, "inspection.ack", ackArgs(item, "NEEDS_INPUT"));
        long id = item.path("itemId").asLong();
        var broken = spy(mapper); doThrow(new IllegalStateException("保存回执失败")).when(broken).insertCall(any());
        assertThrows(IllegalStateException.class, () -> service(broken).acknowledge(run, id, "NEEDS_INPUT", "需补充环境", null, null, null));
        assertEquals("PENDING", mapper.item(id).status()); assertEquals(0, count("pm_agent_inspection_analysis"));
        var saved = inspections.acknowledge(run, id, "NEEDS_INPUT", "需补充环境", null, null, null);
        var replay = inspections.acknowledge(run, id, "NEEDS_INPUT", "需补充环境", null, null, null);
        assertEquals(json.writeValueAsString(saved), json.writeValueAsString(replay));
        assertThrows(IllegalArgumentException.class, () -> inspections.acknowledge(run, id, "NO_FINDING", "更改结论", null, null, null));
        assertEquals(1, count("pm_agent_inspection_analysis"));
        endTool(run, key, saved);
    }

    @Test void completedAccountingRunsAreScopedAndLateCompletionIsRescanned() {
        accountRun(1, 1, false); accountRun(2, 1, true); accountRun(3, 2, true);
        var run = run(null); var first = next(run, ACCOUNTING_RUN);
        assertEquals(2, first.path("sourceId").asInt());
        assertFalse(first.at("/observation/historicalEnvironmentVerified").asBoolean());
        ack(run, first, "NO_FINDING");
        jdbc.update("UPDATE ops_account_reconcile_run SET finished_at=CURRENT_TIMESTAMP,status='WARNING' WHERE id=1");
        var late = next(run, ACCOUNTING_RUN); assertEquals(1, late.path("sourceId").asInt());
        assertEquals(2, count("pm_agent_inspection_item"));
    }

    @Test void inspectionUsesRecordedScopeAndTimeZoneAfterConfigurationMoves() {
        accountRun(1, 1, true);
        jdbc.update("UPDATE ops_account_reconcile_run SET status='FAILED',finished_at=? WHERE id=1", LocalDateTime.of(2026, 11, 4, 10, 0));
        var source = sources.scanAccounting("BL000012", "TEST", 0, 1, 20).getFirst();
        var config = new AccountingExecutionSnapshot.Configuration(1L, "BL000012", "TEST", "test", "测试", "db-test", "fixture", "fixture", true);
        var snapshot = new AccountingExecutionSnapshot(1, 1L, config, AccountingExecutionEvidenceService.hash(json.writeValueAsString(config)),
                List.of(), AccountingExecutionEvidenceService.hash("[]"), source.startTime(), source.endTime(), "Asia/Shanghai", "a".repeat(64));
        String payload = json.writeValueAsString(snapshot);
        jdbc.update("INSERT INTO ops_account_execution_evidence(run_id,business_line_code,environment_code,system_name,display_name,snapshot_json,content_hash,captured_at) VALUES (1,'BL000012','TEST','test','测试',?,?,CURRENT_TIMESTAMP)",
                payload, AccountingExecutionEvidenceService.hash(payload));
        jdbc.update("UPDATE ops_account_monitor_config SET business_line_code='BL000001',environment_code='PROD' WHERE id=1");

        var context = run(null); var item = next(context, ACCOUNTING_RUN);
        assertEquals(1, item.path("sourceId").asInt());
        assertTrue(item.at("/observation/historicalEnvironmentVerified").asBoolean());
        assertEquals("RUN_EXECUTION_SNAPSHOT", item.at("/observation/scopeBasis").asText());
        assertEquals("Asia/Shanghai", item.at("/observation/recordTimeZone").asText());
        assertEquals("TEST", item.at("/observation/data/runs/0/executionEvidence/snapshot/configuration/environmentCode").asText());
        String caseId = record(context, item.at("/observation/observationId").asText(), "/data/runs/0", "EXECUTION_FAILURE");
        assertEquals("TEST", autonomy.find(caseId).environmentCode());
        var args = ackArgs(item, "FINDING"); args.put("caseId", caseId);
        assertEquals("FINDING", invoke(context, "inspection.ack", args).at("/analysis/outcome").asText());
        assertEquals("DISCOVERED", autonomy.find(caseId).status());
    }

    @Test void accountingFindingCanReferenceSameRunsRealRuleEvidence() {
        accountRun(1, 1, true); accountRun(2, 1, true); var run = run(null); var item = next(run, ACCOUNTING_RUN);
        var wrong = ruleObservation(run, 2); var right = ruleObservation(run, 1);
        String wrongCase = record(run, wrong.path("observationId").asText(), "/data/results/0", "DATA_DISCREPANCY");
        String rightCase = record(run, right.path("observationId").asText(), "/data/results/0", "DATA_DISCREPANCY");
        var args = ackArgs(item, "FINDING"); args.put("caseId", wrongCase).put("findingObservationId", wrong.path("observationId").asText()).put("findingPointer", "/data/results/0");
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "inspection.ack", args));
        args.put("caseId", rightCase).put("findingObservationId", right.path("observationId").asText());
        assertEquals("FINDING", invoke(run, "inspection.ack", args).at("/analysis/outcome").asText());
    }

    @Test void oversizedRecordKeepsItsQueueIdentityButCannotBeAcknowledgedAsFullyRead() {
        bug(1, 1, "Authorization: Bearer private-inspection-secret\n" + "x".repeat(44_000));
        var run = run(null); var item = next(run, BUG);
        assertTrue(item.path("itemId").asLong() > 0); assertTrue(item.at("/observation/truncated").asBoolean());
        assertFalse(json.writeValueAsString(item).contains("private-inspection-secret"));
        assertFalse(jdbc.queryForObject("SELECT payload_json FROM pm_agent_inspection_item", String.class).contains("private-inspection-secret"));
        assertThrows(IllegalArgumentException.class, () -> ack(run, item, "NO_FINDING"));
        ack(run, item, "NEEDS_INPUT");
    }

    @Test void nativeCompositeHandleUsesSameTransactionAndReceiptIdentity() {
        alert(1, "TEST", "原生工具调用"); var run = run(null);
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(Map.of("kind", "CLI", "key", "native", "activeTool",
                Map.of("kind", "TOOL", "key", "native:rpc:1", "tool", "inspection.next"))), run.runId());
        var result = inspections.next(run, SYSTEM_ALERT, "BL000012", "TEST", null);
        assertEquals("native:rpc:1", autonomy.observation(result.at("/observation/observationId").asText()).toolKey());
        assertNotNull(mapper.call(run.runId(), "native:rpc:1"));
    }

    @Test void maskingExpansionIsRejectedBeforeAnyAnalysisWrite() {
        bug(1, 1, "确认理由"); var run = run(null); var item = next(run, BUG);
        String reason = "Authorization: Bearer x\n".repeat(80);
        assertTrue(reason.length() <= 2000); assertTrue(new AgentToolData(json).sanitize(reason).asText().length() > 2000);
        var args = ackArgs(item, "NEEDS_INPUT"); args.put("reason", reason);
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "inspection.ack", args));
        assertEquals("PENDING", mapper.item(item.path("itemId").asLong()).status());
        assertEquals(0, count("pm_agent_inspection_analysis"));
    }

    @Test void pendingPagesKeepLongReasonsAndStableContinuationBelowOutputLimit() {
        for (int id = 1; id <= 11; id++) bug(id, 1, "待补充" + id);
        var run = run(null);
        for (int id = 1; id <= 11; id++) {
            var args = ackArgs(next(run, BUG), "NEEDS_INPUT"); args.put("reason", "说明".repeat(1000));
            invoke(run, "inspection.ack", args);
        }
        var first = invoke(run, "inspection.pending", nextArgs(BUG));
        assertEquals(10, first.path("items").size()); assertTrue(first.path("hasMore").asBoolean());
        assertFalse(new AgentToolData(json).sanitize(first).path("truncated").asBoolean());
        var args = nextArgs(BUG); args.put("afterItemId", first.path("nextAfterItemId").asLong());
        var second = invoke(run, "inspection.pending", args);
        assertEquals(1, second.path("items").size()); assertFalse(second.path("hasMore").asBoolean());
    }

    @Test void committedReadIsRecoveredOnceAndApiConsumesItWithoutRepeatingTheTool() throws Exception {
        bug(1, 1, "已提交的巡检快照"); var run = run(null);
        var lost = committedWithoutCallback(run, "inspection.next", nextArgs(BUG));
        assertNull(runs.event(run.runId(), lost.key() + ":result"));
        expire(run);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> runs.recoverExpired(run.runId()));
            var b = workers.submit(() -> runs.recoverExpired(run.runId()));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertEquals("QUEUED", runs.get(run.runId()).status()); assertNull(runs.get(run.runId()).externalHandle());
        var receipt = json.readTree(runs.event(run.runId(), lost.key() + ":result").payloadJson());
        assertTrue(receipt.path("succeeded").asBoolean());
        assertEquals(json.writeValueAsString(lost.result()), json.writeValueAsString(receipt.path("value")));
        assertEquals(1, runs.events(run.runId()).stream().filter(event -> "TOOL_RECEIPT_RECOVERED".equals(event.kind())).count());
        var next = runs.claim(); assertEquals(run.runId(), next.id());
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenAnswer(call -> {
            String prompt = ((AiAgentApiRequest) call.getArgument(0)).prompt();
            assertTrue(prompt.contains("已提交的巡检快照")); assertTrue(prompt.contains("TOOL_RECEIPT_RECOVERED"));
            return AiGatewayResult.succeeded("{\"type\":\"NEEDS_INPUT\",\"plan\":\"基于原回执继续定位\",\"summary\":\"缺陷环境待补充\"}", "fixture", "fixture", 1);
        });
        new AgentApiExecutionAdapter(runs, new AgentToolData(json), gateway,
                cn.aslight.workhub.service.ai.TestPromptTemplates.stubEnabledConfigService(),
                new cn.aslight.workhub.service.ai.AiPromptTemplateRenderer(json), plainGuide(), json).execute(run.runId(), next.ownerToken());
        verify(gateway, times(1)).executeAgentApi(any());
        assertEquals("WAITING_INPUT", runs.get(run.runId()).status());
        assertEquals(1, count("pm_agent_inspection_call")); assertEquals(1, count("pm_agent_inspection_item"));
    }

    @Test void cancellationKeepsCommittedAcknowledgmentAndDoesNotRepeatAnalysis() {
        bug(1, 1, "确认回执中断"); var run = run(null); var item = next(run, BUG);
        var lost = committedWithoutCallback(run, "inspection.ack", ackArgs(item, "NEEDS_INPUT"));
        workbench.cancel(run.taskId(), run.runId(), runs.get(run.runId()).version(), "owner");
        expire(run); runs.recoverExpired(run.runId()); runs.recoverExpired(run.runId());
        assertEquals("CANCELLED", runs.get(run.runId()).status()); assertNull(runs.get(run.runId()).ownerToken());
        var result = json.readTree(runs.event(run.runId(), lost.key() + ":result").payloadJson());
        assertEquals("NEEDS_INPUT", result.at("/value/analysis/outcome").asText());
        assertEquals(1, count("pm_agent_inspection_analysis"));
        assertEquals("NEEDS_INPUT", mapper.item(item.path("itemId").asLong()).status());
    }

    @Test void nativeRecoveryNeedsBothCommittedToolResultAndProcessStopProof() {
        bug(1, 1, "原生巡检已提交");
        for (boolean stoppedFirst : List.of(false, true)) {
            var run = run(null); String attempt = "cli:inspection", key = attempt + ":rpc:1";
            runs.beginNative(run.runId(), run.ownerToken(), attempt, "fixture-host");
            begin(run, "inspection.next", nextArgs(BUG), key);
            var value = raw(run, "inspection.next", nextArgs(BUG));
            var stopProof = Map.of("processTreeStopped", true, "exitCode", 0, "result", Map.of("type", "NEEDS_INPUT", "summary", "需补充环境"));
            expire(run);
            if (stoppedFirst) runs.endNative(run.runId(), run.ownerToken(), attempt, stopProof);
            runs.recoverExpired(run.runId());
            var response = json.readTree(runs.event(run.runId(), key + ":result").payloadJson());
            assertFalse(response.path("isError").asBoolean());
            assertEquals(json.writeValueAsString(value), json.writeValueAsString(json.readTree(response.at("/content/0/text").asText()).path("result")));
            if (!stoppedFirst) {
                assertEquals("WAITING_RESOURCE", runs.get(run.runId()).status()); assertNotNull(runs.get(run.runId()).ownerToken());
                assertFalse(json.readTree(runs.get(run.runId()).externalHandle()).has("activeTool"));
                assertNull(runs.claim());
                runs.endNative(run.runId(), run.ownerToken(), attempt, stopProof);
                runs.recoverExpired(run.runId());
            }
            assertEquals("QUEUED", runs.get(run.runId()).status());
            var reclaimed = runs.claim();
            assertTrue(runs.finishSavedNative(run.runId(), reclaimed.ownerToken()));
            assertEquals("WAITING_INPUT", runs.get(run.runId()).status());
            assertEquals(1, runs.events(run.runId()).stream().filter(event -> "TOOL_RECEIPT_RECOVERED".equals(event.kind())).count());
        }
        assertEquals(2, count("pm_agent_inspection_call"));
    }

    @Test void missingReceiptOrOtherRunsReceiptDoesNotProveThisCallCompleted() {
        bug(1, 1, "其他运行的事实"); var first = run(null);
        var committed = committedWithoutCallback(first, "inspection.next", nextArgs(BUG));
        var other = run(null);
        begin(other, "inspection.next", nextArgs(BUG), committed.key());
        expire(other); runs.recoverExpired(other.runId());
        assertEquals("WAITING_RESOURCE", runs.get(other.runId()).status());
        assertEquals(other.ownerToken(), runs.get(other.runId()).ownerToken());
        assertNull(runs.event(other.runId(), committed.key() + ":result"));
        String originalHandle = runs.get(first.runId()).externalHandle();
        var wrongKey = (tools.jackson.databind.node.ObjectNode) json.readTree(originalHandle); wrongKey.put("key", "step:999:tool");
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(wrongKey), first.runId());
        expire(first); runs.recoverExpired(first.runId());
        assertEquals("WAITING_RESOURCE", runs.get(first.runId()).status());
        assertNull(runs.event(first.runId(), "step:999:tool:result"));
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", originalHandle, first.runId());
        runs.recoverExpired(first.runId()); assertEquals("QUEUED", runs.get(first.runId()).status());
    }

    @Test void mismatchedToolIntentCannotBorrowPersistedInspectionResult() {
        bug(1, 1, "核对原意图"); var run = run(null); var lost = committedWithoutCallback(run, "inspection.next", nextArgs(BUG));
        jdbc.update("UPDATE pm_agent_event SET payload_json=? WHERE run_id=? AND event_key=?",
                json.writeValueAsString(Map.of("tool", "inspection.ack", "arguments", Map.of())), run.runId(), lost.key() + ":request");
        expire(run); runs.recoverExpired(run.runId());
        assertEquals("WAITING_RESOURCE", runs.get(run.runId()).status()); assertNotNull(runs.get(run.runId()).externalHandle());
        assertNull(runs.event(run.runId(), lost.key() + ":result"));
    }

    @Test void newHumanInputAfterCommittedCallReplansFromFactsInsteadOfClaimingNothingRan() {
        bug(1, 1, "补充前已取得的事实"); var run = run(null);
        var lost = committedWithoutCallback(run, "inspection.next", nextArgs(BUG));
        workbench.message(run.taskId(), new AgentMessageRequest(run.runId(), "clarify", runs.get(run.runId()).version(), "请先保留证据，等待环境确认"), "owner");
        expire(run); runs.recoverExpired(run.runId()); var reclaimed = runs.claim();
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenAnswer(call -> {
            var prompt = ((AiAgentApiRequest) call.getArgument(0)).prompt();
            assertTrue(prompt.contains("补充前已取得的事实")); assertTrue(prompt.contains("等待环境确认"));
            return AiGatewayResult.succeeded("{\"type\":\"NEEDS_INPUT\",\"plan\":\"保留事实\",\"summary\":\"等待环境确认\"}", "fixture", "fixture", 1);
        });
        new AgentApiExecutionAdapter(runs, new AgentToolData(json), gateway,
                cn.aslight.workhub.service.ai.TestPromptTemplates.stubEnabledConfigService(),
                new cn.aslight.workhub.service.ai.AiPromptTemplateRenderer(json), plainGuide(), json).execute(run.runId(), reclaimed.ownerToken());
        // 恢复的旧回执没有携带决策，按格式错误丢弃后依据新指令重新规划；已提交事实保留在轨迹中。
        var changed = runs.event(run.runId(), lost.key().replace(":tool", ":handled"));
        assertEquals("FORMAT_ERROR", changed.kind());
        assertEquals("WAITING_INPUT", runs.get(run.runId()).status());
        assertEquals(1, count("pm_agent_inspection_call"));
    }

    private LostCall committedWithoutCallback(AgentToolContext context, String code, JsonNode arguments) {
        int step = runs.beginModel(context.runId(), context.ownerToken(), Map.of("fixture", true));
        runs.endModel(context.runId(), context.ownerToken(), step, Map.of("succeeded", true));
        String key = "step:" + step + ":tool";
        begin(context, code, arguments, key);
        return new LostCall(key, json.valueToTree(raw(context, code, arguments)));
    }
    private void expire(AgentToolContext run) {
        jdbc.update("UPDATE pm_agent_run SET lease_until=? WHERE id=?", LocalDateTime.ofInstant(clock.instant().minusSeconds(1), ZoneOffset.UTC), run.runId());
    }
    private record LostCall(String key, JsonNode result) { }

    @Test void registeredBugLinksActualRuntimeEvidenceThenAcknowledgesWithoutChangingWorkItem() {
        bug(1, 1, "Authorization: Bearer hidden-bug-token\n相同故障"); alert(1, "TEST", "相同故障");
        var run = run(null); var item = next(run, BUG); var observed = next(run, SYSTEM_ALERT);
        var detail = invoke(run, "workhub.bugs.detail", json.valueToTree(Map.of("businessLineCode", "BL000012", "workItemId", 1)));
        assertEquals(item.path("sourceVersion"), detail.path("workItemVersion"));
        assertEquals("UNRESOLVED", item.at("/observation/environmentState").asText());
        String caseId = record(run, observed.at("/observation/observationId").asText(), "/data/events/0", "APPLICATION_ERROR");
        var finding = ackArgs(item, "FINDING"); finding.put("caseId", caseId);
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "inspection.ack", finding));
        claim(run, caseId);
        var args = linkArgs(caseId, item, observed, "link-1");
        var linked = invoke(run, "case.link_bug", args);
        var entity = autonomy.latestBugLink(caseId, 1);
        assertEquals("AI_SUGGESTED_RELATION", linked.path("assessment").asText());
        assertEquals("CURRENT", linked.at("/association/sourceState").asText());
        assertEquals("TEST", json.readTree(entity.snapshotJson()).at("/scope/environmentCode").asText());
        assertFalse(entity.snapshotJson().contains("hidden-bug-token"));
        assertEquals("处理中", jdbc.queryForObject("SELECT status FROM pm_work_item WHERE id=1", String.class));
        var accepted = invoke(run, "inspection.ack", finding);
        assertEquals(entity.observationId(), accepted.at("/analysis/observationId").asText());
        assertEquals(entity.evidencePointer(), accepted.at("/analysis/evidencePointer").asText());
        assertFalse(accepted.path("resolved").asBoolean());
        propose(run, caseId, "proposal-1");
        var planned = autonomy.find(caseId);
        invoke(run, "case.link_bug", args); // 原请求回放，不能作废后续方案。
        var duplicate = args.deepCopy(); duplicate.put("requestKey", "link-2").put("version", planned.version());
        invoke(run, "case.link_bug", duplicate);
        assertEquals(planned, autonomy.find(caseId));
        assertEquals(1, count("pm_autonomy_bug_link")); assertEquals(2, count("pm_autonomy_bug_link_request"));
        assertEquals(1, cases.bugsForUser(agent.id(), caseId, "owner", 1, 5).total());
        var conflict = args.deepCopy(); conflict.put("reason", "更改原请求依据");
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "case.link_bug", conflict));
    }

    @Test void editedBugInvalidatesPlanningAndNewAssociationRetainsHistory() {
        bug(1, 1, "原始复现步骤"); alert(1, "TEST", "错误");
        var run = run(null); var item = next(run, BUG); var observed = next(run, SYSTEM_ALERT);
        String caseId = record(run, observed.at("/observation/observationId").asText(), "/data/events/0", "APPLICATION_ERROR");
        claim(run, caseId); invoke(run, "case.link_bug", linkArgs(caseId, item, observed, "original"));
        var original = autonomy.latestBugLink(caseId, 1);
        propose(run, caseId, "original-plan");
        jdbc.update("UPDATE pm_work_item SET description='新的复现步骤与验收范围' WHERE id=1");
        assertTrue(cases.bugContextChanged(autonomy.find(caseId)));
        var staleSummary = cases.detailForUser(agent.id(), caseId, "owner", 1, 1, 1).bugContext();
        assertEquals(1, staleSummary.bugCount()); assertEquals(1, staleSummary.changedCount()); assertEquals(0, staleSummary.unavailableCount());
        assertEquals("CHANGED", invoke(run, "case.bugs", json.valueToTree(Map.of("caseId", caseId))).at("/items/0/sourceState").asText());
        assertThrows(IllegalArgumentException.class, () -> propose(run, caseId, "stale-plan"));
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "case.link_bug", linkArgs(caseId, item, observed, "stale-link")));
        var changed = invoke(run, "workhub.bugs.detail", json.valueToTree(Map.of("businessLineCode", "BL000012", "workItemId", 1)));
        var newLink = linkArgs(caseId, item, observed, "updated"); newLink.put("workItemVersion", changed.path("workItemVersion").asText());
        invoke(run, "case.link_bug", newLink);
        assertNull(autonomy.find(caseId).latestProposalId());
        assertFalse(cases.bugContextChanged(autonomy.find(caseId)));
        var history = cases.bugsForUser(agent.id(), caseId, "owner", 1, 5);
        assertEquals(2, history.total()); assertTrue(history.items().getFirst().latest()); assertFalse(history.items().getLast().latest());
        assertEquals(original.snapshotJson(), history.items().getLast().link().snapshotJson());
        var revisedSummary = cases.detailForUser(agent.id(), caseId, "owner", 1, 1, 1).bugContext();
        assertEquals(2, revisedSummary.historyCount()); assertEquals(1, revisedSummary.bugCount());
        assertEquals(0, revisedSummary.changedCount()); // 旧关联原文不同不应令最新已重核的方案继续失效。
        var finding = ackArgs(item, "FINDING"); finding.put("caseId", caseId);
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "inspection.ack", finding));
        ack(run, item, "NEEDS_INPUT");
        var newItem = next(run, BUG);
        finding.put("itemId", newItem.path("itemId").asLong());
        invoke(run, "inspection.ack", finding);
        propose(run, caseId, "revised-plan");
        jdbc.update("UPDATE pm_work_item SET project_id=2 WHERE id=1");
        assertTrue(cases.bugContextChanged(autonomy.find(caseId)));
        assertEquals("UNAVAILABLE", cases.bugsForUser(agent.id(), caseId, "owner", 1, 1).items().getFirst().sourceState());
        assertEquals(1, cases.detailForUser(agent.id(), caseId, "owner", 1, 1, 1).bugContext().unavailableCount());
        assertThrows(IllegalArgumentException.class, () -> propose(run, caseId, "moved-plan"));
    }

    @Test void bugContextSummaryCoversLatestAssociationsBeyondVisibleHistoryPage() {
        for (int id = 1; id <= 6; id++) bug(id, 1, "缺陷" + id);
        alert(1, "TEST", "同一运行问题"); var run = run(null); var observed = next(run, SYSTEM_ALERT);
        String caseId = record(run, observed.at("/observation/observationId").asText(), "/data/events/0", "APPLICATION_ERROR");
        claim(run, caseId);
        assertEquals(0, cases.detailForUser(agent.id(), caseId, "owner", 1, 1, 1).bugContext().historyCount());
        for (int id = 1; id <= 6; id++) {
            var item = next(run, BUG);
            invoke(run, "case.link_bug", linkArgs(caseId, item, observed, "link-" + id));
            var finding = ackArgs(item, "FINDING"); finding.put("caseId", caseId); invoke(run, "inspection.ack", finding);
        }
        jdbc.update("UPDATE pm_work_item SET description='已修改' WHERE id=1");
        jdbc.update("UPDATE pm_work_item SET work_item_type='需求' WHERE id=2");
        assertEquals(6, cases.bugsForUser(agent.id(), caseId, "owner", 1, 1).items().getFirst().link().workItemId());
        var summary = cases.detailForUser(agent.id(), caseId, "owner", 1, 1, 1).bugContext();
        assertEquals(6, summary.bugCount()); assertEquals(6, summary.historyCount());
        assertEquals(1, summary.changedCount()); assertEquals(1, summary.unavailableCount());
    }

    @Test void bugAssociationRejectsForeignSourceAndRechecksBothPermissions() {
        bug(1, 1, "本业务线"); bug(2, 2, "其他业务线"); alert(1, "TEST", "错误");
        var run = run(null); var item = next(run, BUG); var observed = next(run, SYSTEM_ALERT);
        String caseId = record(run, observed.at("/observation/observationId").asText(), "/data/events/0", "APPLICATION_ERROR");
        claim(run, caseId);
        var valid = linkArgs(caseId, item, observed, "valid");
        var foreign = valid.deepCopy(); foreign.put("workItemId", 2);
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "case.link_bug", foreign));
        var other = run(null); var otherEvidence = next(other, SYSTEM_ALERT);
        var wrongRun = valid.deepCopy(); wrongRun.put("observationId", otherEvidence.at("/observation/observationId").asText());
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "case.link_bug", wrongRun));
        var wrongPointer = valid.deepCopy(); wrongPointer.put("pointer", "/data/events/1");
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "case.link_bug", wrongPointer));
        grants.remove("work-item:view");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> invoke(run, "case.link_bug", valid));
        assertEquals(0, count("pm_autonomy_bug_link"));
        grants.add("work-item:view"); invoke(run, "case.link_bug", valid);
        grants.remove("work-item:view");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> cases.detailForUser(agent.id(), caseId, "owner", 1, 1, 1));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> invoke(run, "case.bugs", json.valueToTree(Map.of("caseId", caseId))));
        grants.add("work-item:view"); grants.remove("ops:system-alert:view");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> invoke(run, "case.bugs", json.valueToTree(Map.of("caseId", caseId))));
    }

    @Test void concurrentBugAssociationReplaysOneCommittedRevision() throws Exception {
        bug(1, 1, "并发关联"); alert(1, "TEST", "错误");
        var run = run(null); var item = next(run, BUG); var observed = next(run, SYSTEM_ALERT);
        String caseId = record(run, observed.at("/observation/observationId").asText(), "/data/events/0", "APPLICATION_ERROR");
        claim(run, caseId); long version = autonomy.find(caseId).version();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Map<String, Object>> action = () -> cases.linkBug(run, caseId, version, "parallel", 1, item.path("sourceVersion").asText(),
                    observed.at("/observation/observationId").asText(), "/data/events/0", "相同实际错误", null);
            var first = executor.submit(action); var second = executor.submit(action);
            assertEquals(json.valueToTree(first.get(10, TimeUnit.SECONDS)), json.valueToTree(second.get(10, TimeUnit.SECONDS)));
        }
        assertEquals(1, count("pm_autonomy_bug_link")); assertEquals(1, count("pm_autonomy_bug_link_request"));
        assertEquals(version + 1, autonomy.find(caseId).version());
    }

    @Test void bugAssociationKeepsAccountingScopeUncertaintyAndRefusesOversizedSnapshot() {
        bug(1, 1, "账务差异线索"); accountRun(1, 1, true);
        var run = run(null); var item = next(run, BUG); var observed = next(run, ACCOUNTING_RUN);
        var detail = ruleObservation(run, 1);
        String caseId = record(run, detail.path("observationId").asText(), "/data/results/0", "DATA_DISCREPANCY");
        claim(run, caseId);
        // 账务规则详情中的历史环境限制保留到关联快照。
        var args = linkArgs(caseId, item, observed, "accounting"); args.put("observationId", detail.path("observationId").asText()).put("pointer", "/data/results/0");
        invoke(run, "case.link_bug", args);
        var snapshot = json.readTree(autonomy.latestBugLink(caseId, 1).snapshotJson());
        assertEquals("CURRENT_MONITOR_CONFIGURATION", snapshot.path("scopeBasis").asText());
        assertFalse(snapshot.path("historicalEnvironmentVerified").asBoolean(true));
        jdbc.update("UPDATE pm_work_item SET description=? WHERE id=1", "x".repeat(29_000));
        var source = sources.bug("BL000012", 1);
        var large = args.deepCopy(); large.put("requestKey", "large").put("version", autonomy.find(caseId).version())
                .put("workItemVersion", AutonomyEvidence.digest(json.writeValueAsString(source)));
        assertThrows(IllegalArgumentException.class, () -> invoke(run, "case.link_bug", large));
        assertEquals(1, count("pm_autonomy_bug_link")); assertTrue(cases.bugContextChanged(autonomy.find(caseId)));
    }

    private void claim(AgentToolContext run, String caseId) {
        invoke(run, "case.claim", json.valueToTree(Map.of("caseId", caseId, "version", autonomy.find(caseId).version())));
    }
    private tools.jackson.databind.node.ObjectNode linkArgs(String caseId, JsonNode item, JsonNode observed, String key) {
        return json.createObjectNode().put("caseId", caseId).put("version", autonomy.find(caseId).version()).put("requestKey", key)
                .put("workItemId", item.path("sourceId").asLong()).put("workItemVersion", item.path("sourceVersion").asText())
                .put("observationId", observed.at("/observation/observationId").asText()).put("pointer", "/data/events/0").put("reason", "缺陷描述与实际错误对应，建议人工复核");
    }
    private void propose(AgentToolContext run, String caseId, String key) {
        var args = json.createObjectNode().put("caseId", caseId).put("version", autonomy.find(caseId).version()).put("requestKey", key)
                .put("facts", "已关联实际缺陷与运行证据").put("hypothesis", "待验证的根因").put("impact", "测试环境")
                .put("plan", "按当前缺陷版本修复").put("acceptance", "独立测试").put("rollback", "恢复基线").put("unknowns", "尚未验证修复").put("changeType", "CODE");
        invoke(run, "case.propose", args);
    }

    private AgentPlatformGuide plainGuide() {
        var configs = mock(cn.aslight.workhub.service.system.SysConfigService.class);
        return new AgentPlatformGuide(configs);
    }

    private AgentInspectionService service(AgentInspectionMapper repository) {
        return proxy(new AgentInspectionService(repository, sources, agents, autonomy, runs, access, observations, new AutonomyEvidence(json), new AgentToolData(json), json, cases, executionEvidence));
    }
    private JsonNode next(AgentToolContext run, AgentInspectionService.Source source) { return invoke(run, "inspection.next", nextArgs(source)); }
    private tools.jackson.databind.node.ObjectNode nextArgs(AgentInspectionService.Source source) {
        var args = json.createObjectNode().put("source", source.name()).put("businessLineCode", "BL000012");
        if (source != BUG) args.put("environmentCode", "TEST");
        return args;
    }
    private JsonNode ack(AgentToolContext run, JsonNode item, String outcome) { return invoke(run, "inspection.ack", ackArgs(item, outcome)); }
    private tools.jackson.databind.node.ObjectNode ackArgs(JsonNode item, String outcome) {
        return json.createObjectNode().put("itemId", item.path("itemId").asLong()).put("outcome", outcome).put("reason", "本地固定分析理由");
    }
    private String record(AgentToolContext run, String observation, String pointer, String category) {
        var result = invoke(run, "case.record", json.valueToTree(Map.of("observationId", observation, "pointer", pointer, "category", category, "title", "固定问题")));
        return result.at("/caseInfo/id").asText();
    }
    private JsonNode ruleObservation(AgentToolContext run, long id) {
        return invoke(run, "monitor.accounting.detail", json.valueToTree(Map.of("scope", scope, "observedAt", Instant.now().toString(), "recordTimeZone", "Asia/Shanghai",
                "scopeBasis", "CURRENT_MONITOR_CONFIGURATION", "historicalEnvironmentVerified", false,
                "data", Map.of("run", Map.of("id", id, "configId", 1), "results", List.of(Map.of("id", id * 10, "runId", id, "ruleCode", "BALANCE", "status", "WARN", "differenceAmount", "12.34", "createdAt", "2026-11-04T10:00:00"))))));
    }
    /** 历史工具调用按原服务分发：手柄与回执直接落库，业务语义与原工具包装一致。 */
    private JsonNode invoke(AgentToolContext run, String code, JsonNode args) {
        String key = begin(run, code, args, null);
        try {
            var result = json.valueToTree(raw(run, code, args));
            endTool(run, key, result); return result;
        } catch (RuntimeException ex) { endTool(run, key, Map.of("fixtureFailure", true)); throw ex; }
    }
    private Object raw(AgentToolContext run, String code, JsonNode args) {
        return switch (code) {
            case "inspection.next" -> inspections.next(run, AgentInspectionService.Source.valueOf(args.path("source").asText()),
                    args.path("businessLineCode").asText(), args.hasNonNull("environmentCode") ? args.path("environmentCode").asText() : null,
                    args.hasNonNull("retryItemId") ? args.path("retryItemId").asLong() : null);
            case "inspection.ack" -> inspections.acknowledge(run, args.path("itemId").asLong(), args.path("outcome").asText(),
                    args.path("reason").asText(), args.hasNonNull("caseId") ? args.path("caseId").asText() : null,
                    args.hasNonNull("findingObservationId") ? args.path("findingObservationId").asText() : null,
                    args.hasNonNull("findingPointer") ? args.path("findingPointer").asText() : null);
            case "inspection.pending" -> inspections.pending(run, AgentInspectionService.Source.valueOf(args.path("source").asText()),
                    args.path("businessLineCode").asText(), args.hasNonNull("environmentCode") ? args.path("environmentCode").asText() : null,
                    args.hasNonNull("afterItemId") ? args.path("afterItemId").asLong() : 0L);
            case "case.record" -> cases.record(run, args.path("observationId").asText(), args.path("pointer").asText(),
                    args.path("category").asText(), args.path("title").asText(), null);
            case "case.claim" -> cases.claim(run, args.path("caseId").asText(), args.path("version").asLong());
            case "case.propose" -> cases.propose(run, args.path("caseId").asText(), args.path("version").asLong(), args.path("requestKey").asText(),
                    new AutonomyProposal(args.path("facts").asText(), args.path("hypothesis").asText(), args.path("impact").asText(),
                            args.path("plan").asText(), args.path("acceptance").asText(), args.path("rollback").asText(),
                            args.path("unknowns").asText(), args.path("changeType").asText()));
            case "case.link_bug" -> cases.linkBug(run, args.path("caseId").asText(), args.path("version").asLong(), args.path("requestKey").asText(),
                    args.path("workItemId").asLong(), args.path("workItemVersion").asText(), args.path("observationId").asText(),
                    args.path("pointer").asText(), args.path("reason").asText(), null);
            case "case.bugs" -> cases.bugs(run, args.path("caseId").asText(), 1, 5);
            case "monitor.accounting.detail" -> observations.capture(run, code, args);
            case "workhub.bugs.detail" -> {
                var record = sources.bug(args.path("businessLineCode").asText(), args.path("workItemId").asLong());
                if (record == null) throw new IllegalArgumentException("该业务线中不存在所指定的缺陷工作项");
                yield json.createObjectNode().put("workItemVersion", AutonomyEvidence.digest(json.writeValueAsString(record)));
            }
            default -> throw new IllegalArgumentException("未知调用: " + code);
        };
    }
    private String begin(AgentToolContext run, String code, JsonNode args) { return begin(run, code, args, null); }
    private String begin(AgentToolContext run, String code, JsonNode args, String explicitKey) {
        String key = explicitKey != null ? explicitKey : "inspection-test:" + sequence.incrementAndGet();
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), run.runId(), key + ":request", agents.nextEventOrdinal(run.runId()),
                "TOOL_REQUEST", json.writeValueAsString(Map.of("tool", code, "arguments", args)), "agent-runtime",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        var tool = json.createObjectNode().put("kind", "TOOL").put("key", key).put("tool", code).put("readOnly", true);
        String current = agents.findRun(run.runId()).externalHandle();
        if (current == null) {
            jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(tool), run.runId());
        } else {
            var handle = (tools.jackson.databind.node.ObjectNode) json.readTree(current);
            handle.set("activeTool", tool);
            jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(handle), run.runId());
        }
        return key;
    }
    private void endTool(AgentToolContext run, String key, Object result) {
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), run.runId(), key + ":result", agents.nextEventOrdinal(run.runId()),
                "TOOL_RESULT", json.writeValueAsString(result), "agent-runtime", LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        String current = agents.findRun(run.runId()).externalHandle();
        if (current != null && "CLI".equals(json.readTree(current).path("kind").asText())) {
            var handle = (tools.jackson.databind.node.ObjectNode) json.readTree(current);
            handle.remove("activeTool");
            jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(handle), run.runId());
        } else {
            jdbc.update("UPDATE pm_agent_run SET external_handle=NULL WHERE id=?", run.runId());
        }
    }
    private AgentToolContext run(String taskId) {
        if (taskId == null) {
            var specification = new AgentTaskSpecification("巡检", "本地固定来源", "保存结论", List.of("REPORT"), List.of(scope));
            workbench.createTask(agent.id(), new AgentTaskCreateRequest(UUID.randomUUID().toString(), specification,
                    new AgentScheduleRequest(null, "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false), true), "owner");
        } else workbench.run(taskId, new AgentRunRequest(UUID.randomUUID().toString(), 0L), "owner");
        var claimed = runs.claim(); assertNotNull(claimed); return runs.context(claimed);
    }
    private void bug(long id, long project, String description) {
        jdbc.update("INSERT INTO pm_work_item(id,work_item_no,project_id,work_item_type,title,description,source_type,source_channel,priority,status,creator_user_name,owner_user_name,follower_user_name,acceptance_criteria) VALUES (?,?,?,'缺陷','样本',?,'MANUAL','fixture','P1','处理中','owner','owner','owner','固定验收')", id, "BUG" + id, project, description);
    }
    private void alert(long id, String environment, String message) {
        jdbc.update("INSERT INTO ops_system_alert_event(id,business_line_code,environment_code,service_name,log_level,message,occurred_at) VALUES (?,'BL000012',?,'test-service','ERROR',?,CURRENT_TIMESTAMP)", id, environment, message);
    }
    private void accountRun(long id, long config, boolean finished) {
        jdbc.update("INSERT INTO ops_account_reconcile_run(id,run_no,idempotency_key,monitor_config_id,trigger_type,start_time,end_time,status,requested_by,finished_at) VALUES (?,?,?,?,'MANUAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'fixture',?)", id, "run" + id, "key" + id, config, finished ? "WARNING" : "RUNNING", finished ? LocalDateTime.now() : null);
    }
    private void createTable(String sql, String table) {
        var matcher = Pattern.compile("(?s)CREATE TABLE(?: IF NOT EXISTS)? `" + Pattern.quote(table) + "` \\(.*?;").matcher(sql);
        assertTrue(matcher.find(), table); jdbc.execute(matcher.group());
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var factory = new ProxyFactory(target); factory.addAdvice(transactions); return (T) factory.getProxy();
    }
}
