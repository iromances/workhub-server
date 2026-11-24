package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.autonomy.*;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import cn.aslight.workhub.service.auth.PermissionContextService;
import cn.aslight.workhub.service.autonomy.*;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 生产迁移、Mapper、权限与事务联测；监测输入为本地固定样本，不访问业务系统。 */
class AutonomyCaseServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T04:00:00Z"), ZoneOffset.UTC);
    private final AtomicInteger sequence = new AtomicInteger();
    private Connection keepAlive;
    private ValidatorFactory validation;
    private JdbcTemplate jdbc;
    private TransactionInterceptor transactions;
    private AgentMapper agents;
    private AutonomyMapper mapper;
    private AgentRunStore runs;
    private AgentWorkbenchService workbench;
    private AgentObservationService observations;
    private AutonomyCaseService cases;
    private AgentDefinitionResponse agent;
    private final Map<String, Set<String>> grants = new ConcurrentHashMap<>();

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = source.getConnection(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        jdbc.update("INSERT INTO sys_role VALUES (1,'SUPER_ADMIN')");
        jdbc.execute("CREATE TABLE dict(id BIGINT AUTO_INCREMENT PRIMARY KEY, dict_type VARCHAR(64), dict_desc VARCHAR(128), enum_code VARCHAR(128), enum_desc VARCHAR(256), parent_code VARCHAR(128), root_code VARCHAR(128), sort_order INT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, create_by VARCHAR(64), modify_time DATETIME, delete_time DATETIME, delete_by VARCHAR(64), is_delete TINYINT DEFAULT 0, version INT DEFAULT 0, remark VARCHAR(512))");
        jdbc.update("INSERT INTO dict(dict_type,enum_code,enum_desc) VALUES ('agentPosition','OPS','运维')");
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_035107__add_agent_observations_and_cases.sql", "V20261104_174329__add_autonomy_case_resolution.sql",
                "V20261104_103540__add_autonomy_bug_links.sql")) {
            ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + file));
        }
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class); configuration.addMapper(AutonomyMapper.class);
        configuration.addMapper(cn.aslight.workhub.dao.system.DictMapper.class);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject()); agents = sessions.getMapper(AgentMapper.class); mapper = sessions.getMapper(AutonomyMapper.class);
        transactions = new TransactionInterceptor(); transactions.setTransactionManager(new DataSourceTransactionManager(source));
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var permissions = mock(PermissionContextService.class);
        when(permissions.requireActiveUser(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0); var user = new SysUserEntity(); user.setId("owner".equals(name) ? 1L : 2L); user.setUserName(name); return user;
        });
        grants.put("owner", new HashSet<>(Set.of("agent:view", "agent:manage", "agent:task:assign", "agent:task:view", "agent:task:operate",
                "autonomy:case:view", "autonomy:case:operate", "ops:monitor:view", "ops:accounting:view", "ops:system-alert:view")));
        grants.put("other", Set.of("agent:view", "autonomy:case:view", "ops:monitor:view"));
        when(permissions.permissionCodes(anyLong())).thenAnswer(invocation -> List.copyOf(grants.get(Long.valueOf(1).equals(invocation.getArgument(0)) ? "owner" : "other")));
        var lines = mock(BusinessLineMapper.class); var line = new BusinessLineEntity(); line.setEnabled(true); line.setSupportedEnvironments("TEST,PROD");
        when(lines.findByCode("BL000012")).thenReturn(line);
        var access = new AgentAccessService(permissions, lines, agents, json);
        var providers = mock(AiProviderConfigService.class); var provider = new AiProviderConfigEntity();
        provider.setId(1L); provider.setEnabled(true); provider.setChannelType("API"); provider.setDefaultModel("fixture-model");
        when(providers.requireExisting(1L)).thenReturn(provider); when(providers.getEnabledById(1L)).thenReturn(provider);
        var data = new AgentToolData(json); runs = proxy(new AgentRunStore(agents, json, access, data, providers, clock, AgentAcceptanceTestSupport.withoutCode(agents, access, json, clock)));
        observations = proxy(new AgentObservationService(mapper, agents, runs, access, json, data));
        validation = Validation.buildDefaultValidatorFactory();
        cases = proxy(new AutonomyCaseService(mapper, agents, runs, access, new AutonomyEvidence(json), data, json, validation.getValidator(), mock(AgentMonitorReadMapper.class)));
        var cryptoProperties = new cn.aslight.workhub.config.PaymentProperties();
        cryptoProperties.setMasterKey("test-master-key");
        workbench = proxy(new AgentWorkbenchService(agents, access, providers,
                new cn.aslight.workhub.service.system.DictService(sessions.getMapper(cn.aslight.workhub.dao.system.DictMapper.class)),
                new cn.aslight.workhub.service.payment.PaymentCryptoService(cryptoProperties), json, clock));
        var config = new AgentConfiguration("巡检 Agent","OPS", "主动巡检", 1L, "fixture-model", List.of(scope), 200, 3600, 8);
        agent = workbench.create(new AgentSaveRequest(null, config), "owner"); agent = workbench.setEnabled(agent.id(), agent.version(), true, "owner");
    }
    @AfterEach void close() throws Exception { if (validation != null) validation.close(); if (keepAlive != null) keepAlive.close(); }

    @Test void observationRequiresActualToolHandleMasksSecretsAndFreezesDuplicateCall() {
        var context = run();
        Object payload = business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "Authorization: Bearer private-value", "2026-11-04T04:00:00Z");
        assertThrows(IllegalStateException.class, () -> observations.capture(context, "monitor.business.status", payload));
        String key = begin(context, "monitor.business.status");
        JsonNode first = observations.capture(context, "monitor.business.status", payload);
        JsonNode again = observations.capture(context, "monitor.business.status", business("2026-11-04T10:01:00", "UP", "HAS_RESULT", "changed", "2026-11-04T04:01:00Z"));
        assertEquals(first, again);
        var stored = mapper.observation(first.path("observationId").asText());
        assertEquals("BUSINESS_MONITOR", stored.sourceType()); assertFalse(stored.payloadJson().contains("private-value"));
        assertEquals(1, count("pm_agent_observation")); assertEquals(8, count("sys_role_permission"));
        assertThrows(IllegalStateException.class, () -> observations.capture(context, "workhub.logs", payload));
        endTool(context, key);
    }

    @Test void cliCompositeHandleProducesTheSameEvidenceContract() {
        var context = run();
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(Map.of("kind", "CLI", "key", "native", "activeTool",
                Map.of("kind", "TOOL", "key", "native:rpc:1", "tool", "monitor.business.status"))), context.runId());
        var result = observations.capture(context, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failed", "2026-11-04T04:00:00Z"));
        assertEquals("native:rpc:1", mapper.observation(result.path("observationId").asText()).toolKey());
    }

    @Test void truncatedResultsKeepReferenceButCannotBecomeCompleteEvidence() {
        var context = run();
        var result = capture(context, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "x".repeat(60000), "2026-11-04T04:00:00Z"));
        JsonNode delivered = new AgentToolData(json).sanitize(result);
        assertTrue(delivered.path("truncated").asBoolean()); assertTrue(delivered.hasNonNull("observationId"));
        assertThrows(IllegalArgumentException.class, () -> record(context, result)); assertEquals(0, count("pm_autonomy_case"));
    }

    @Test void repeatPollingDoesNotInventOccurrencesOrChangeProcessingVersion() {
        var first = run(); var second = run();
        var original = capture(first, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failed", "2026-11-04T04:00:00Z"));
        var a = record(first, original); String id = caseId(a);
        var polled = capture(second, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failed", "2026-11-04T05:00:00Z"));
        var b = record(second, polled);
        assertEquals(id, caseId(b)); assertEquals(false, b.get("newEvidence"));
        assertEquals(1, mapper.find(id).occurrenceCount()); assertEquals(1, mapper.find(id).evidenceVersion()); assertEquals(0, mapper.find(id).version());
        assertEquals(LocalDateTime.parse("2026-11-04T05:00:00"), mapper.find(id).lastObservedAt());
        assertEquals(2, mapper.countRuns(id));
        var detail = cases.detail(first, id, 1, 1, 1); assertEquals(1, detail.signals().total());
        assertEquals(1, cases.list(first, scope, 1, 20).total());
    }

    @Test void concurrentAgentsCreateOneCaseAndOneActiveAnalysis() throws Exception {
        var a = run(); var b = run();
        Object payload = business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failed", "2026-11-04T04:00:00Z");
        JsonNode oa = capture(a, "monitor.business.status", payload), ob = capture(b, "monitor.business.status", payload);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> record(a, oa)); var second = pool.submit(() -> record(b, ob));
            String id = caseId(first.get(10, TimeUnit.SECONDS)); assertEquals(id, caseId(second.get(10, TimeUnit.SECONDS)));
            var ca = pool.submit(() -> cases.claim(a, id, 0)); var cb = pool.submit(() -> cases.claim(b, id, 0));
            assertNotEquals(ca.get(10, TimeUnit.SECONDS).get("claimed"), cb.get(10, TimeUnit.SECONDS).get("claimed"));
            assertEquals(1, mapper.find(id).version());
        }
        assertEquals(1, count("pm_autonomy_case")); assertEquals(1, count("pm_autonomy_signal"));
    }

    @Test void crossRunScopeAndRevokedSourceAccessCannotReadOrAttachEvidence() {
        var a = run(); var b = run(); var observation = capture(a, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failed", "2026-11-04T04:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> record(b, observation));
        var outside = json.valueToTree(business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failed", "2026-11-04T04:00:00Z"));
        ((tools.jackson.databind.node.ObjectNode) outside.path("scope")).put("environmentCode", "PROD");
        assertThrows(AccessDeniedException.class, () -> capture(b, "monitor.business.status", outside));
        String id = caseId(record(a, observation));
        assertThrows(AccessDeniedException.class, () -> cases.detailForUser(agent.id(), id, "other", 1, 1, 1));
        grants.get("owner").remove("ops:monitor:view");
        assertEquals(0, cases.listForUser(agent.id(), scope, "owner", 1, 20).total());
        assertThrows(AccessDeniedException.class, () -> cases.detailForUser(agent.id(), id, "owner", 1, 1, 1));
        assertThrows(AccessDeniedException.class, () -> record(a, observation));
    }

    @Test void historicalBackfillDoesNotReopenButFreshRecurrenceDoes() {
        var context = run();
        String id = caseId(record(context, capture(context, "monitor.business.status", business("2026-11-04T08:00:00", "ERROR", "HAS_RESULT", "failure", "2026-11-04T04:00:00Z"))));
        jdbc.update("UPDATE pm_autonomy_case SET status='RESOLVED',resolved_at='2026-11-04 02:00:00',version=7 WHERE id=?", id);
        var old = record(context, capture(context, "monitor.business.status", business("2026-11-04T09:00:00", "ERROR", "HAS_RESULT", "failure", "2026-11-04T04:00:00Z")));
        assertEquals(false, old.get("reopened")); assertEquals("RESOLVED", mapper.find(id).status()); assertEquals(7, mapper.find(id).version());
        var future = record(context, capture(context, "monitor.business.status", business("2026-11-05T11:00:00", "ERROR", "HAS_RESULT", "failure", "2026-11-04T04:00:00Z")));
        assertEquals(false, future.get("reopened")); assertEquals("RESOLVED", mapper.find(id).status()); assertEquals(7, mapper.find(id).version());
        var fresh = record(context, capture(context, "monitor.business.status", business("2026-11-04T11:00:00", "ERROR", "HAS_RESULT", "failure", "2026-11-04T04:00:00Z")));
        assertEquals(true, fresh.get("reopened")); assertEquals("DISCOVERED", mapper.find(id).status());
        assertEquals(8, mapper.find(id).version()); assertNull(mapper.find(id).resolvedAt()); assertEquals(4, mapper.find(id).occurrenceCount());
    }

    @Test void lateFailureInsideVerifiedWindowReopensWithoutOverwritingResolutionHistory() {
        var context=run(); String id=closedCase(context);
        var old=record(context,capture(context,"monitor.business.status",business("2026-11-04T10:05:00","ERROR","HAS_RESULT","old backfill","2026-11-04T04:00:00Z")));
        assertEquals(false,old.get("reopened")); assertEquals("RESOLVED",mapper.find(id).status());
        var delayed=record(context,capture(context,"monitor.business.status",business("2026-11-04T10:15:00","ERROR","HAS_RESULT","late error","2026-11-04T04:00:00Z")));
        assertEquals(true,delayed.get("reopened")); assertEquals("DISCOVERED",mapper.find(id).status());
        assertNull(mapper.find(id).latestProposalId()); assertNull(mapper.find(id).activeRunId()); assertNull(mapper.find(id).resolvedAt());
        assertEquals(8,mapper.find(id).version()); assertEquals(1,mapper.countResolutions(id));
        var repeated=record(context,capture(context,"monitor.business.status",business("2026-11-04T10:15:00","ERROR","HAS_RESULT","late error","2026-11-04T04:00:00Z")));
        assertEquals(false,repeated.get("newEvidence")); assertEquals(8,mapper.find(id).version());
        var history=cases.resolutionsForUser(agent.id(),id,"owner",1,5);
        assertEquals(1,history.total()); assertEquals(7,history.items().getFirst().resolvedVersion());
        grants.get("owner").remove("ops:monitor:view");
        assertThrows(AccessDeniedException.class,()->cases.resolutionsForUser(agent.id(),id,"owner",1,5));
    }
    @Test void failureExactlyAtRecoveredWindowStartReopensResolvedCase() {
        var context=run(); String id=closedCase(context);
        var result=record(context,capture(context,"monitor.business.status",business("2026-11-04T10:10:00","ERROR","HAS_RESULT","boundary error","2026-11-04T04:00:00Z")));
        assertEquals(true,result.get("reopened")); assertEquals("DISCOVERED",mapper.find(id).status());
        assertEquals(8,mapper.find(id).version()); assertEquals(1,mapper.countResolutions(id));
    }
    @Test void unverifiedOccurrenceTimeReopensForClarificationWithoutPollutingLatestKnownTime() {
        var context=run(); String id=closedCase(context); var before=mapper.find(id).lastSeenAt();
        var result=record(context,capture(context,"monitor.business.status",business("2026-11-04T13:00:00","ERROR","HAS_RESULT","bad clock","2026-11-04T04:00:00Z")));
        assertEquals(true,result.get("reopened")); assertEquals("NEEDS_INPUT",mapper.find(id).status());
        assertEquals(before,mapper.find(id).lastSeenAt()); assertEquals(8,mapper.find(id).version());
        assertEquals(1,mapper.countResolutions(id)); assertNull(mapper.find(id).latestProposalId());
    }
    private String closedCase(AgentToolContext context) {
        String id=caseId(record(context,capture(context,"monitor.business.status",business("2026-11-04T09:00:00","ERROR","HAS_RESULT","initial error","2026-11-04T04:00:00Z"))));
        var at=LocalDateTime.parse("2026-11-04T03:00:00");
        jdbc.update("UPDATE pm_autonomy_case SET status='RESOLVED',resolved_at=?,version=7,latest_proposal_id='old-proposal',active_run_id=NULL WHERE id=?",at,id);
        mapper.insertResolution(new AutonomyCaseResolution(UUID.randomUUID().toString(),id,6,7,1,context.runId(),"verification","plan","change","a".repeat(64),
                "old-proposal","b".repeat(64),"artifact","c".repeat(64),"[\"health\"]",at.minusMinutes(50),at.minusMinutes(30),"原问题已验证",at));
        return id;
    }

    @Test void analysisHistoryIsImmutableIdempotentAndVersionBound() {
        var context = run(); String id = problem(context);
        assertThrows(IllegalArgumentException.class, () -> cases.propose(context, id, 0, "proposal", proposal("fix")));
        cases.claim(context, id, 0);
        var saved = cases.propose(context, id, 1, "proposal", proposal("fix"));
        var repeated = cases.propose(context, id, 1, "proposal", proposal("fix")); assertEquals(saved.get("proposal"), repeated.get("proposal"));
        assertEquals(false, saved.get("approved")); assertEquals("ANALYZING", mapper.find(id).status());
        assertThrows(IllegalArgumentException.class, () -> cases.propose(context, id, 1, "proposal", proposal("different")));
        assertThrows(IllegalArgumentException.class, () -> cases.propose(context, id, 1, "stale", proposal("different")));
        cases.propose(context, id, 2, "revised", proposal("new plan"));
        assertEquals(2, mapper.countProposals(id)); assertEquals(3, mapper.find(id).version());
        var history = mapper.proposals(id, 0, 10);
        assertTrue(history.stream().anyMatch(p -> p.proposalJson().contains("new plan")));
        assertTrue(history.stream().anyMatch(p -> p.proposalJson().contains("fix")));
        assertTrue(history.stream().noneMatch(p -> p.proposalJson().contains("private-analysis-secret")));
    }

    @Test void unknownExternalWorkCannotBeTakenOverAndSafeFailureCan() {
        var a = run(); var b = run(); String id = problem(a); cases.claim(a, id, 0);
        jdbc.update("UPDATE pm_agent_run SET status='WAITING_RESOURCE',owner_token=NULL,external_handle='unknown-operation' WHERE id=?", a.runId());
        assertEquals(false, cases.claim(b, id, 1).get("claimed")); assertEquals(a.runId(), mapper.find(id).activeRunId());
        jdbc.update("UPDATE pm_agent_run SET external_handle=NULL WHERE id=?", a.runId());
        assertEquals(true, cases.claim(b, id, 1).get("claimed")); assertEquals(b.runId(), mapper.find(id).activeRunId());
    }

    @Test void cancelledOrStaleWorkersCannotRegisterEvidenceOrAdvanceAnalysis() {
        var context = run(); String id = problem(context);
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id=?", context.runId());
        assertThrows(AgentRunStore.StopRequested.class, () -> cases.claim(context, id, 0));
        assertEquals(0, mapper.find(id).version());
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=0,owner_token='new-owner' WHERE id=?", context.runId());
        assertThrows(AgentRunStore.LeaseLost.class, () -> cases.claim(context, id, 0));
        assertEquals(0, mapper.find(id).version());
    }

    @Test void sharedCaseOperationsCompleteAnInspectionReportWithoutClosingTheProblem() {
        var context = run();
        var observed = capture(context, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failure", "2026-11-04T04:00:00Z"));
        Map<String, Object> result = cases.record(context, observed.path("observationId").asText(), "/data/0", "EXECUTION_FAILURE", "巡检故障", null);
        assertTrue((Boolean) result.get("created"));
        String id = caseId(result);
        cases.claim(context, id, 0);
        cases.propose(context, id, 1, "tool-proposal", proposal("investigate"));
        assertEquals(1, mapper.countProposals(id));
        runs.finish(context.runId(), context.ownerToken(), "COMPLETED", "已登记证据并形成分析建议，尚未修复", null);
        assertEquals("COMPLETED", agents.findRun(context.runId()).status()); assertEquals("ANALYZING", mapper.find(id).status());
        assertFalse(agents.listArtifacts(context.runId()).isEmpty());
    }

    @Test void boundedDetailSectionsKeepLargeEvidenceAndAnalysisIndividuallyReadable() {
        var context = run();
        var observed = capture(context, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "x".repeat(36_000), "2026-11-04T04:00:00Z"));
        String id = caseId(record(context, observed)); cases.claim(context, id, 0);
        cases.propose(context, id, 1, "large", new AutonomyProposal("a".repeat(4000), "b".repeat(4000), "c".repeat(2000), "d".repeat(6000),
                "e".repeat(3000), "f".repeat(2000), "g".repeat(2000), "CODE"));
        var detail = cases.detail(context, id, 1, 1, 1);
        List<Map<String, Object>> sections = List.of(
                Map.of("caseInfo", detail.caseInfo(), "evidenceTotal", detail.signals().total(), "proposalTotal", detail.proposals().total(),
                        "relatedRuns", detail.relatedRuns(), "relatedRunTotal", detail.relatedRunTotal(), "bugContext", detail.bugContext()),
                Map.of("caseId", detail.caseInfo().id(), "signals", detail.signals()),
                Map.of("caseId", detail.caseInfo().id(), "proposals", detail.proposals(), "source", "AI_ANALYSIS"));
        for (Map<String, Object> output : sections) {
            assertFalse(new AgentToolData(json).sanitize(output).path("truncated").asBoolean(), String.valueOf(output.containsKey("signals")));
        }
    }

    @Test void apiStepFailsRunWithSceneCodeWhenPromptConfigMissing() {
        var context = run();
        var gateway = mock(AiGatewayClient.class);
        new AgentApiExecutionAdapter(runs, new AgentToolData(json), gateway,
                cn.aslight.workhub.service.ai.TestPromptTemplates.missingConfigService(),
                new cn.aslight.workhub.service.ai.AiPromptTemplateRenderer(json), plainGuide(), json).execute(context.runId(), context.ownerToken());
        assertEquals("FAILED", agents.findRun(context.runId()).status());
        var finish = runs.event(context.runId(), "finish:" + context.ownerToken());
        assertTrue(finish.payloadJson().contains("agent.api.step"), finish.payloadJson());
        verifyNoInteractions(gateway);
    }

    private String problem(AgentToolContext context) {
        return caseId(record(context, capture(context, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failure", "2026-11-04T04:00:00Z"))));
    }
    private String caseId(Map<String, Object> result) { return ((AutonomyCaseEntity) result.get("caseInfo")).id(); }
    private Map<String, Object> record(AgentToolContext context, JsonNode observation) {
        return cases.record(context, observation.path("observationId").asText(), "/data/0", "EXECUTION_FAILURE", "任务执行失败", null);
    }
    private JsonNode capture(AgentToolContext context, String code, Object payload) {
        String key = begin(context, code);
        try { return observations.capture(context, code, payload); }
        finally { endTool(context, key); }
    }
    /** 历史工具调用手柄直接落库；观测服务按原样关联证据到实际调用。 */
    private String begin(AgentToolContext context, String code) {
        String key = "test:" + sequence.incrementAndGet();
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), context.runId(), key + ":request", agents.nextEventOrdinal(context.runId()),
                "TOOL_REQUEST", json.writeValueAsString(Map.of("tool", code, "arguments", Map.of())), "agent-runtime",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?",
                json.writeValueAsString(Map.of("kind", "TOOL", "key", key, "tool", code, "readOnly", true)), context.runId());
        return key;
    }
    private void endTool(AgentToolContext context, String key) {
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), context.runId(), key + ":result", agents.nextEventOrdinal(context.runId()),
                "TOOL_RESULT", json.writeValueAsString(Map.of("fixture", true)), "agent-runtime",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        jdbc.update("UPDATE pm_agent_run SET external_handle=NULL WHERE id=?", context.runId());
    }
    private Object business(String checkedAt, String status, String state, String message, String observedAt) {
        return Map.of("scope", scope, "observedAt", observedAt, "recordTimeZone", "Asia/Shanghai", "coverage", "QUERY_SUCCEEDED", "data", List.of(
                Map.of("id", 7, "name", "同步任务", "enabled", true, "type", "XXL_JOB", "dataState", state, "status", status, "message", message,
                        "lastCheckedAt", checkedAt, "dataAgeSeconds", Duration.between(LocalDateTime.parse(checkedAt).atZone(ZoneId.of("Asia/Shanghai")).toInstant(), Instant.parse(observedAt)).getSeconds())));
    }
    private AutonomyProposal proposal(String plan) {
        return new AutonomyProposal("已见执行错误 Authorization: Bearer private-analysis-secret", "原因待代码确认", "影响待核实", plan, "原始监测恢复且回归通过", "恢复上个经审核版本", "无实测结果", "CODE");
    }
    private AgentPlatformGuide plainGuide() {
        return new AgentPlatformGuide(mock(cn.aslight.workhub.service.system.SysConfigService.class));
    }

    private AgentToolContext run() {
        var specification = new AgentTaskSpecification("巡检", "固定样本", "输出问题和分析", List.of("REPORT"), List.of(scope));
        workbench.createTask(agent.id(), new AgentTaskCreateRequest(UUID.randomUUID().toString(), specification,
                new AgentScheduleRequest(null, "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false), true), "owner");
        var run = runs.claim(); assertNotNull(run); return runs.context(run);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var proxy = new ProxyFactory(target); proxy.addAdvice(transactions); return (T) proxy.getProxy();
    }
}
