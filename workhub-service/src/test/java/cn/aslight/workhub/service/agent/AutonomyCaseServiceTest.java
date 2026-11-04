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
    private AgentToolCatalog catalog;
    private AgentDefinitionResponse agent;
    private final Map<String, Set<String>> grants = new ConcurrentHashMap<>();

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = source.getConnection(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        jdbc.update("INSERT INTO sys_role VALUES (1,'SUPER_ADMIN')");
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_035107__add_agent_observations_and_cases.sql",
                "V20261104_103540__add_autonomy_bug_links.sql")) {
            ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + file));
        }
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class); configuration.addMapper(AutonomyMapper.class);
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
        var caseTools = new AutonomyCaseTools(cases, access, json);
        List<AgentTool> tools = new ArrayList<>(List.of(caseTools.autonomyCaseRecordTool(), caseTools.autonomyCaseClaimTool(),
                caseTools.autonomyCaseDetailTool(), caseTools.autonomyCaseListTool(), caseTools.autonomyCaseProposeTool()));
        for (String code : List.of("monitor.business.status", "monitor.business.detail", "monitor.accounting.status", "monitor.accounting.detail", "monitor.system.alerts", "workhub.logs")) {
            tools.add(new AgentTool() {
                public String code() { return code; }
                public String description() { return "本地固定样本"; }
                public JsonNode inputSchema() { return json.readTree("{\"type\":\"object\"}"); }
                public Set<String> permissions() { return Set.of("ops:monitor:view"); }
                public boolean readOnly() { return true; }
                public Object execute(AgentToolContext context, JsonNode args) {
                    if (!"monitor.business.status".equals(code)) throw new UnsupportedOperationException("测试仅使用已固定查询样本");
                    return observations.capture(context, code, business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "本地固定故障样本", "2026-11-04T04:00:00Z"));
                }
            });
        }
        catalog = new AgentToolCatalog(tools);
        workbench = proxy(new AgentWorkbenchService(agents, access, providers, catalog, json, clock));
        var config = new AgentConfiguration("巡检 Agent", "主动巡检", 1L, "fixture-model", List.of(scope), tools.stream().map(AgentTool::code).toList(), 200, 3600, 8);
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
        runs.endTool(context.runId(), context.ownerToken(), key, first);
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

    @Test void sharedCaseToolsCompleteAnInspectionReportWithoutClosingTheProblem() {
        var context = run();
        var observed = capture(context, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "failure", "2026-11-04T04:00:00Z"));
        var record = catalog.require("case.record");
        JsonNode args = json.valueToTree(Map.of("observationId", observed.path("observationId").asText(), "pointer", "/data/0", "category", "EXECUTION_FAILURE", "title", "巡检故障"));
        String key = begin(context, record.code());
        JsonNode result = json.valueToTree(record.execute(context, args)); runs.endTool(context.runId(), context.ownerToken(), key, result);
        assertTrue(result.path("created").asBoolean());
        String id = result.path("caseInfo").path("id").asText();
        key = begin(context, "case.claim");
        Object claim = catalog.require("case.claim").execute(context, json.valueToTree(Map.of("caseId", id, "version", 0)));
        runs.endTool(context.runId(), context.ownerToken(), key, claim);
        key = begin(context, "case.propose");
        var proposal = (tools.jackson.databind.node.ObjectNode) json.valueToTree(proposal("investigate"));
        proposal.put("caseId", id); proposal.put("version", 1); proposal.put("requestKey", "tool-proposal");
        Object output = catalog.require("case.propose").execute(context, proposal);
        runs.endTool(context.runId(), context.ownerToken(), key, output);
        assertEquals(1, mapper.countProposals(id));
        runs.finish(context.runId(), context.ownerToken(), "COMPLETED", "已登记证据并形成分析建议，尚未修复", null);
        assertEquals("COMPLETED", agents.findRun(context.runId()).status()); assertEquals("ANALYZING", mapper.find(id).status());
        assertFalse(agents.listArtifacts(context.runId()).isEmpty());
    }

    @Test void apiExecutionReceivesObservedEvidenceThenRegistersClaimsAndAnalysesTheProblem() {
        var context = run();
        var gateway = mock(AiGatewayClient.class);
        var step = new AtomicInteger();
        when(gateway.executeAgentApi(any())).thenAnswer(invocation -> {
            String output;
            int current = step.incrementAndGet();
            if (current == 1) output = decision("monitor.business.status", Map.of("businessLineCode", "BL000012", "environmentCode", "TEST"));
            else if (current == 2) {
                var observed = mapper.observationByCall(context.runId(), "step:1:tool"); assertNotNull(observed);
                var request = (cn.aslight.workhub.service.ai.AiAgentApiRequest) invocation.getArgument(0);
                assertTrue(request.prompt().contains(observed.id())); assertTrue(request.prompt().contains("本地固定故障样本"));
                output = decision("case.record", Map.of("observationId", observed.id(), "pointer", "/data/0", "category", "EXECUTION_FAILURE", "title", "固定任务失败"));
            } else if (current == 3) output = decision("case.claim", Map.of("caseId", jdbc.queryForObject("SELECT id FROM pm_autonomy_case", String.class), "version", 0));
            else if (current == 4) {
                var value = (tools.jackson.databind.node.ObjectNode) json.valueToTree(proposal("定位调度失败原因"));
                value.put("caseId", jdbc.queryForObject("SELECT id FROM pm_autonomy_case", String.class)); value.put("version", 1); value.put("requestKey", "api-plan");
                output = decision("case.propose", value);
            } else if (current == 5) output = "{\"type\":\"RESULT\",\"plan\":\"巡检报告\",\"summary\":\"已保存实际监测证据和分析建议，尚未修改代码或发布\"}";
            else throw new AssertionError("AI 桥接未按工具回执前进");
            return AiGatewayResult.succeeded(output, "configured", "fixture-model", 10);
        });
        new AgentApiExecutionAdapter(runs, catalog, new AgentToolData(json), gateway, json).execute(context.runId(), context.ownerToken());
        assertEquals(5, step.get()); assertEquals("COMPLETED", agents.findRun(context.runId()).status());
        assertEquals(1, count("pm_agent_observation")); assertEquals(1, count("pm_autonomy_case")); assertEquals(1, count("pm_autonomy_proposal"));
        assertEquals("ANALYZING", jdbc.queryForObject("SELECT status FROM pm_autonomy_case", String.class));
    }

    @Test void boundedDetailSectionsKeepLargeEvidenceAndAnalysisIndividuallyReadable() {
        var context = run();
        var observed = capture(context, "monitor.business.status", business("2026-11-04T10:00:00", "ERROR", "HAS_RESULT", "x".repeat(36_000), "2026-11-04T04:00:00Z"));
        String id = caseId(record(context, observed)); cases.claim(context, id, 0);
        cases.propose(context, id, 1, "large", new AutonomyProposal("a".repeat(4000), "b".repeat(4000), "c".repeat(2000), "d".repeat(6000),
                "e".repeat(3000), "f".repeat(2000), "g".repeat(2000), "CODE"));
        for (String section : List.of("SUMMARY", "EVIDENCE", "PROPOSALS")) {
            Object result = catalog.require("case.detail").execute(context, json.valueToTree(Map.of("caseId", id, "section", section, "pageSize", 1)));
            assertFalse(new AgentToolData(json).sanitize(result).path("truncated").asBoolean(), section);
        }
    }

    private String decision(String tool, Object arguments) {
        return json.writeValueAsString(Map.of("type", "TOOL_CALL", "plan", "依据实际证据继续", "tool", tool, "arguments", arguments));
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
        finally { runs.endTool(context.runId(), context.ownerToken(), key, Map.of("fixture", true)); }
    }
    private String begin(AgentToolContext context, String code) {
        String key = "test:" + sequence.incrementAndGet(); runs.beginTool(context.runId(), context.ownerToken(), key, catalog.require(code), json.createObjectNode(), true); return key;
    }
    private Object business(String checkedAt, String status, String state, String message, String observedAt) {
        return Map.of("scope", scope, "observedAt", observedAt, "recordTimeZone", "Asia/Shanghai", "coverage", "QUERY_SUCCEEDED", "data", List.of(
                Map.of("id", 7, "name", "同步任务", "enabled", true, "type", "XXL_JOB", "dataState", state, "status", status, "message", message,
                        "lastCheckedAt", checkedAt, "dataAgeSeconds", Duration.between(LocalDateTime.parse(checkedAt).atZone(ZoneId.of("Asia/Shanghai")).toInstant(), Instant.parse(observedAt)).getSeconds())));
    }
    private AutonomyProposal proposal(String plan) {
        return new AutonomyProposal("已见执行错误 Authorization: Bearer private-analysis-secret", "原因待代码确认", "影响待核实", plan, "原始监测恢复且回归通过", "恢复上个经审核版本", "无实测结果", "CODE");
    }
    private AgentToolContext run() {
        var specification = new AgentTaskSpecification("巡检", "固定样本", "输出问题和分析", List.of("REPORT"), List.of(scope), catalog.all().stream().map(AgentTool::code).toList());
        workbench.createTask(agent.id(), new AgentTaskCreateRequest(UUID.randomUUID().toString(), specification,
                new AgentScheduleRequest(null, "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false), true), "owner");
        var run = runs.claim(8); assertNotNull(run); return runs.context(run);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var proxy = new ProxyFactory(target); proxy.addAdvice(transactions); return (T) proxy.getProxy();
    }
}
