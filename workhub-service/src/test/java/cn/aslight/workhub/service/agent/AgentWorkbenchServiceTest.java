package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import cn.aslight.workhub.service.auth.PermissionContextService;
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

import java.nio.file.Path;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用真实迁移、生产 Mapper 和 Spring 事务验证任务一致性；不连接业务数据库或调用 AI。 */
class AgentWorkbenchServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T18:00:00Z"), ZoneOffset.UTC);
    private final AgentScope testScope = new AgentScope("BL000012", "TEST");
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private AgentMapper mapper;
    private AgentWorkbenchService service;
    private AgentAccessService access;
    private final Map<Long, List<String>> grants = new ConcurrentHashMap<>();
    private AgentTool tool;
    private AgentRunStore store;
    private AgentToolCatalog catalog;
    private AiProviderConfigService providers;
    private final AtomicInteger toolCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = dataSource.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        jdbc.update("INSERT INTO sys_role VALUES (1,'SUPER_ADMIN')");
        Path migration = Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261104_014200__add_agent_workbench.sql");
        ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource(migration));
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(AgentMapper.class);

        var permissions = mock(PermissionContextService.class);
        when(permissions.requireActiveUser(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            var user = new SysUserEntity();
            user.setId(name.equals("owner") ? 1L : 2L);
            user.setUserName(name);
            return user;
        });
        grants.put(1L, List.of("agent:view", "agent:manage", "agent:task:assign", "agent:task:view", "agent:task:operate", "ops:monitor:view"));
        grants.put(2L, List.of("agent:view", "agent:task:assign", "agent:task:view", "agent:task:operate", "ops:monitor:view"));
        when(permissions.permissionCodes(anyLong())).thenAnswer(invocation -> grants.get(invocation.getArgument(0)));
        var lines = mock(BusinessLineMapper.class);
        var line = new BusinessLineEntity();
        line.setBusinessLineCode("BL000012");
        line.setEnabled(true);
        line.setSupportedEnvironments("TEST,PROD");
        when(lines.findByCode("BL000012")).thenReturn(line);
        access = new AgentAccessService(permissions, lines, mapper, json);
        providers = mock(AiProviderConfigService.class);
        var provider = new AiProviderConfigEntity();
        provider.setId(1L);
        provider.setEnabled(true);
        provider.setChannelType("API");
        provider.setDefaultModel("configured-model");
        when(providers.requireExisting(1L)).thenReturn(provider);
        when(providers.getEnabledById(1L)).thenReturn(provider);
        tool = new AgentTool() {
            public String code() { return "monitor.read"; }
            public String description() { return "监测查询"; }
            public JsonNode inputSchema() { return json.readTree("{\"type\":\"object\"}"); }
            public Set<String> permissions() { return Set.of("ops:monitor:view"); }
            public boolean readOnly() { return true; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                access.requireExecution(context, this);
                access.requireScope(context, testScope);
                toolCalls.incrementAndGet();
                return Map.of("dataState", "NEVER_RUN", "source", "test-fixture");
            }
        };
        catalog = new AgentToolCatalog(List.of(tool));
        var target = new AgentWorkbenchService(mapper, access, providers, catalog, json, clock);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(interceptor);
        service = (AgentWorkbenchService) proxy.getProxy();
        var storeProxy = new ProxyFactory(new AgentRunStore(mapper, json, access, new AgentToolData(json), providers, clock));
        storeProxy.addAdvice(interceptor);
        store = (AgentRunStore) storeProxy.getProxy();
    }

    @AfterEach
    void close() throws Exception { keepAlive.close(); }

    @Test
    void genericTaskNeedsNoAlertAndKeepsSnapshotWhenAgentChanges() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("generic", true), "owner");
        var run = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        assertEquals("QUEUED", run.status());
        service.update(agent.id(), new AgentSaveRequest(agent.version(), config("新的职责", "second-model")), "owner");
        var stored = service.runDetail(task.id(), run.id(), "owner");
        assertEquals("first-model", stored.configuration().model());
        assertEquals("整理我提供的资料", stored.specification().objective());
        assertEquals(1, stored.events().size());
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission", Integer.class));
    }

    @Test
    void duplicateConcurrentTaskRequestsCreateOneTaskAndOneRun() throws Exception {
        var agent = enabledAgent();
        var request = taskRequest("same-request", true);
        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Future<AgentTaskResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 12; i++) futures.add(pool.submit(() -> service.createTask(agent.id(), request, "owner")));
            Set<String> ids = new HashSet<>();
            for (var future : futures) ids.add(future.get(10, TimeUnit.SECONDS).id());
            assertEquals(1, ids.size());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_task", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_run", Integer.class));
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentTaskContent() {
        var agent = enabledAgent();
        service.createTask(agent.id(), taskRequest("same", false), "owner");
        var changed = new AgentTaskCreateRequest("same", taskRequest("same", false).specification(), manual(), true);
        assertThrows(IllegalArgumentException.class, () -> service.createTask(agent.id(), changed, "owner"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_run", Integer.class));
    }

    @Test
    void disabledAgentRollsBackTaskAndAuditWhenImmediateRunCannotStart() {
        var agent = service.create(new AgentSaveRequest(null, config("巡检", "first-model")), "owner");
        assertThrows(IllegalArgumentException.class, () -> service.createTask(agent.id(), taskRequest("rollback", true), "owner"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_task", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_audit WHERE object_type='TASK'", Integer.class));
    }

    @Test
    void staleConfigUpdateCannotOverwriteNewerVersion() {
        var agent = enabledAgent();
        service.update(agent.id(), new AgentSaveRequest(agent.version(), config("已更新", "second-model")), "owner");
        assertThrows(IllegalStateException.class, () -> service.update(agent.id(), new AgentSaveRequest(agent.version(), config("旧页面", "first-model")), "owner"));
        assertEquals("已更新", service.detail(agent.id(), "owner").configuration().name());
    }

    @Test
    void disableCancelsQueuedRunButKeepsInFlightOwnershipUntilExecutorStops() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("cancel", true), "owner");
        var queued = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        var running = service.run(task.id(), new AgentRunRequest("second", 0L), "owner");
        jdbc.update("UPDATE pm_agent_run SET status='RUNNING',owner_token='worker-1',external_handle='process-1' WHERE id=?", running.id());
        service.setEnabled(agent.id(), agent.version(), false, "owner");
        assertEquals("CANCELLED", mapper.findRun(queued.id()).status());
        assertEquals("CANCELLING", mapper.findRun(running.id()).status());
        assertEquals("worker-1", mapper.findRun(running.id()).ownerToken());
        assertEquals("process-1", mapper.findRun(running.id()).externalHandle());
    }

    @Test
    void tasksCannotExpandScopesOrViewAnotherOwnersAgent() {
        var agent = enabledAgent();
        var spec = new AgentTaskSpecification("巡检", "", "返回证据", List.of("REPORT"), List.of(new AgentScope("BL000012", "PROD")), List.of("monitor.read"));
        var request = new AgentTaskCreateRequest("scope", spec, manual(), false);
        assertThrows(IllegalArgumentException.class, () -> service.createTask(agent.id(), request, "owner"));
        assertThrows(AccessDeniedException.class, () -> service.detail(agent.id(), "outsider"));
        assertEquals(0, service.list("outsider", 1, 20).total());
    }

    @Test
    void oldSnapshotCannotKeepRevokedToolOrScopePermissions() {
        var agent = enabledAgent();
        var spec = new AgentTaskSpecification("巡检", "", "返回证据", List.of("REPORT"), List.of(testScope), List.of("monitor.read"));
        var context = new AgentToolContext("run", "task", agent.id(), "owner", agent.configuration(), spec);
        access.requireExecution(context, tool);
        service.update(agent.id(), new AgentSaveRequest(agent.version(),
                new AgentConfiguration("收窄", "检查", 1L, "first-model", List.of(), List.of(), 20, 60, 2)), "owner");
        assertThrows(AccessDeniedException.class, () -> access.requireExecution(context, tool));
        assertThrows(AccessDeniedException.class, () -> access.requireScope(context, testScope));
    }

    @Test
    void taskReplyIsIdempotentAndDoesNotRestartUnreconciledExternalExecution() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("reply", true), "owner");
        var run = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        jdbc.update("UPDATE pm_agent_run SET status='WAITING_INPUT' WHERE id=?", run.id());
        var request = new AgentMessageRequest(run.id(), "answer", 0L, "先输出一份分析报告");
        var resumed = service.message(task.id(), request, "owner");
        assertEquals("QUEUED", resumed.status());
        service.message(task.id(), request, "owner");
        assertEquals(2, mapper.listEvents(run.id()).size());
        jdbc.update("UPDATE pm_agent_run SET status='WAITING_INPUT',external_handle='unconfirmed-execution' WHERE id=?", run.id());
        assertThrows(IllegalStateException.class, () -> service.message(task.id(),
                new AgentMessageRequest(run.id(), "different-answer", resumed.version(), "继续"), "owner"));
    }

    @Test
    void scheduledCatchUpIsBoundedAndRepeatedDispatchDoesNotDuplicateRun() {
        var agent = enabledAgent();
        var schedule = new AgentScheduleRequest(null, "CRON", "0 */5 * * * *", "Asia/Shanghai", "RUN_ONCE", true);
        var task = service.createTask(agent.id(), new AgentTaskCreateRequest("schedule", taskRequest("x", false).specification(), schedule, false), "owner");
        jdbc.update("UPDATE pm_agent_task SET next_run_at='2026-11-03 15:00:00' WHERE id=?", task.id());
        service.dispatchScheduledTask(task.id());
        service.dispatchScheduledTask(task.id());
        assertEquals(1, mapper.countRuns(task.id()));
        assertEquals(LocalDateTime.parse("2026-11-03T18:05:00"), mapper.findTask(task.id()).nextRunAt());
    }

    @Test
    void skippedMissedScheduleRecordsDecisionWithoutCreatingRun() {
        var agent = enabledAgent();
        var schedule = new AgentScheduleRequest(null, "CRON", "0 */5 * * * *", "Asia/Shanghai", "SKIP", true);
        var task = service.createTask(agent.id(), new AgentTaskCreateRequest("skip", taskRequest("x", false).specification(), schedule, false), "owner");
        jdbc.update("UPDATE pm_agent_task SET next_run_at='2026-11-03 15:00:00' WHERE id=?", task.id());
        service.dispatchScheduledTask(task.id());
        assertEquals(0, mapper.countRuns(task.id()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_audit WHERE action='MISFIRE_SKIPPED'", Integer.class));
    }

    private AgentDefinitionResponse enabledAgent() {
        var agent = service.create(new AgentSaveRequest(null, config("Agent", "first-model")), "owner");
        return service.setEnabled(agent.id(), agent.version(), true, "owner");
    }

    @Test
    void apiTaskActivelyCallsToolThenUsesPersistedObservation() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenReturn(answer("{\"type\":\"TOOL_CALL\",\"plan\":\"主动检查监测状态\",\"tool\":\"monitor.read\",\"arguments\":{}}"),
                answer("{\"type\":\"RESULT\",\"plan\":\"整理证据\",\"summary\":\"监测从未运行，不能认定健康\"}"));
        adapter(gateway).execute(run.id(), claim.ownerToken());
        assertEquals("COMPLETED", store.get(run.id()).status());
        assertNull(store.get(run.id()).ownerToken());
        assertEquals(1, toolCalls.get());
        var requests = org.mockito.ArgumentCaptor.forClass(cn.aslight.workhub.service.ai.AiAgentApiRequest.class);
        verify(gateway, times(2)).executeAgentApi(requests.capture());
        assertTrue(requests.getAllValues().get(1).prompt().contains("NEVER_RUN"));
        assertTrue(requests.getAllValues().stream().allMatch(request -> request.providerConfigId() == 1L && "first-model".equals(request.model())));
        assertNotNull(store.event(run.id(), "step:1:tool:result"));
        assertEquals("AI_REPORT", mapper.listArtifacts(run.id()).getFirst().provenance());
        assertEquals("REPORT", mapper.listArtifacts(run.id()).getFirst().kind());
    }

    @Test
    void concurrentWorkersRespectGlobalAndPerAgentLimits() throws Exception {
        var agent = enabledAgent();
        for (int i = 0; i < 6; i++) service.createTask(agent.id(), taskRequest("queued-" + i, true), "owner");
        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Future<AgentRunEntity>> claims = new ArrayList<>();
            for (int i = 0; i < 8; i++) claims.add(pool.submit(() -> store.claim(4)));
            Set<String> ids = new HashSet<>();
            for (var future : claims) { var claim = future.get(10, TimeUnit.SECONDS); if (claim != null) ids.add(claim.id()); }
            assertEquals(2, ids.size());
        }
        assertEquals(2, mapper.countOwnedRuns(null));
        var another = enabledAgent();
        service.createTask(another.id(), taskRequest("another", true), "owner");
        assertNotNull(store.claim(3));
        assertNull(store.claim(3));
    }

    @Test
    void persistedToolReceiptIsNotRepeatedAfterRecoveryAndOldWorkerCannotWrite() {
        var run = inspectionRun(List.of("REPORT"));
        var old = store.claim(4);
        int step = store.beginModel(run.id(), old.ownerToken(), Map.of("providerConfigId", 1));
        var decision = AgentApiDecision.parse("{\"type\":\"TOOL_CALL\",\"plan\":\"巡检\",\"tool\":\"monitor.read\",\"arguments\":{}}", json);
        store.endModel(run.id(), old.ownerToken(), step, Map.of("succeeded", true, "decision", decision));
        store.beginTool(run.id(), old.ownerToken(), "step:1:tool", tool, json.createObjectNode(), false);
        store.endTool(run.id(), old.ownerToken(), "step:1:tool", Map.of("succeeded", true, "value", "known-result"));
        expire(run.id());
        store.recoverExpired(run.id());
        var newClaim = store.claim(4);
        assertNotEquals(old.ownerToken(), newClaim.ownerToken());
        assertThrows(AgentRunStore.LeaseLost.class, () -> store.finish(run.id(), old.ownerToken(), "COMPLETED", "stale", null));
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenReturn(answer("{\"type\":\"RESULT\",\"plan\":\"使用原回执\",\"summary\":\"结果已确认\"}"));
        adapter(gateway).execute(run.id(), newClaim.ownerToken());
        assertEquals(0, toolCalls.get());
        assertEquals("COMPLETED", store.get(run.id()).status());
    }

    @Test
    void unresolvedExternalCallKeepsHandleAndConcurrencySlotInsteadOfRestarting() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        store.beginModel(run.id(), claim.ownerToken(), Map.of("providerConfigId", 1));
        expire(run.id());
        store.recoverExpired(run.id());
        var unresolved = store.get(run.id());
        assertEquals("WAITING_RESOURCE", unresolved.status());
        assertNotNull(unresolved.externalHandle());
        assertEquals(1, mapper.countOwnedRuns(null));
        assertNull(store.claim(4));
        var cancelled = service.cancel(run.taskId(), run.id(), unresolved.version(), "owner");
        assertEquals("CANCELLING", cancelled.status());
        assertNotNull(store.get(run.id()).externalHandle());
    }

    @Test
    void lateModelReceiptIsImmutableAndRecoveryConsumesItWithoutCallingModelAgain() throws Exception {
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        int step = store.beginModel(run.id(), old.ownerToken(), Map.of());
        expire(run.id()); store.recoverExpired(run.id());
        long version = store.get(run.id()).version();
        var decision = AgentApiDecision.parse("{\"type\":\"RESULT\",\"plan\":\"完成\",\"summary\":\"迟到的实际结果\"}", json);
        var receipt = Map.of("succeeded", true, "decision", decision);
        store.endModel(run.id(), old.ownerToken(), step, receipt);
        store.endModel(run.id(), old.ownerToken(), step, receipt);
        assertEquals(version, store.get(run.id()).version());
        assertEquals("WAITING_RESOURCE", store.get(run.id()).status()); assertNotNull(store.get(run.id()).externalHandle());
        assertThrows(IllegalArgumentException.class, () -> store.endModel(run.id(), old.ownerToken(), step, Map.of("succeeded", false)));
        assertThrows(AgentRunStore.LeaseLost.class, () -> store.endModel(run.id(), "foreign-owner", step, receipt));
        assertTrue(mapper.findExpiredRunIds(LocalDateTime.now(clock)).contains(run.id()));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> store.recoverExpired(run.id())); var b = workers.submit(() -> store.recoverExpired(run.id()));
            a.get(5, TimeUnit.SECONDS); b.get(5, TimeUnit.SECONDS);
        }
        assertEquals("QUEUED", store.get(run.id()).status()); assertEquals(0, mapper.countOwnedRuns(null));
        var next = store.claim(4); var gateway = mock(AiGatewayClient.class);
        assertThrows(AgentRunStore.LeaseLost.class, () -> store.endModel(run.id(), old.ownerToken(), step, receipt));
        adapter(gateway).execute(run.id(), next.ownerToken());
        verifyNoInteractions(gateway); assertEquals("COMPLETED", store.get(run.id()).status());
        assertEquals("迟到的实际结果", store.get(run.id()).resultText());
        assertEquals(1, store.events(run.id()).stream().filter(event -> "MODEL_RESPONSE".equals(event.kind())).count());
    }

    @Test
    void lateToolReceiptReleasesOnlyTheOriginalCallAndIsNotExecutedAgain() {
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        int step = store.beginModel(run.id(), old.ownerToken(), Map.of());
        var decision = AgentApiDecision.parse("{\"type\":\"TOOL_CALL\",\"plan\":\"读取监测\",\"tool\":\"monitor.read\",\"arguments\":{}}", json);
        store.endModel(run.id(), old.ownerToken(), step, Map.of("succeeded", true, "decision", decision));
        store.beginApiTool(run.id(), old.ownerToken(), "step:1:tool", tool, json.createObjectNode(), "step:1:request");
        var result = tool.execute(store.contextForOwner(run.id(), old.ownerToken()), json.createObjectNode());
        expire(run.id()); store.recoverExpired(run.id());
        assertThrows(IllegalStateException.class, () -> store.endTool(run.id(), old.ownerToken(), "wrong-key", result));
        store.endTool(run.id(), old.ownerToken(), "step:1:tool", Map.of("succeeded", true, "value", result));
        assertNotNull(store.get(run.id()).externalHandle());
        store.recoverExpired(run.id()); var next = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenAnswer(invocation -> {
            assertTrue(((cn.aslight.workhub.service.ai.AiAgentApiRequest) invocation.getArgument(0)).prompt().contains("NEVER_RUN"));
            return answer("{\"type\":\"RESULT\",\"plan\":\"整理已有观察\",\"summary\":\"监测尚未运行\"}");
        });
        adapter(gateway).execute(run.id(), next.ownerToken());
        assertEquals(1, toolCalls.get()); assertEquals("COMPLETED", store.get(run.id()).status());
    }

    @Test
    void cliAndToolLateReceiptsMustBothArriveBeforeReleasingTheRun() {
        providers.getEnabledById(1L).setChannelType("CLI");
        for (boolean cliFirst : List.of(true, false)) {
            var run = inspectionRun(List.of("REPORT")); var old = store.claim(4); String attempt = "cli:late";
            store.beginNative(run.id(), old.ownerToken(), attempt, "fixture-host");
            store.beginTool(run.id(), old.ownerToken(), attempt + ":tool", tool, json.createObjectNode(), true);
            expire(run.id()); store.recoverExpired(run.id());
            Runnable cliReceipt = () -> store.endNative(run.id(), old.ownerToken(), attempt, nativeResult("RESULT", "已查询的报告"));
            Runnable toolReceipt = () -> store.endTool(run.id(), old.ownerToken(), attempt + ":tool", Map.of("succeeded", true, "value", "known-result"));
            (cliFirst ? cliReceipt : toolReceipt).run(); store.recoverExpired(run.id());
            assertEquals("WAITING_RESOURCE", store.get(run.id()).status()); assertNotNull(store.get(run.id()).externalHandle());
            assertNull(store.claim(4)); assertEquals(1, mapper.countOwnedRuns(null));
            (cliFirst ? toolReceipt : cliReceipt).run(); store.recoverExpired(run.id());
            assertEquals("QUEUED", store.get(run.id()).status()); assertNull(store.get(run.id()).externalHandle());
            var next = store.claim(4); var protocol = mock(AgentCliProtocol.class);
            new AgentNativeCliAdapter(store, access, catalog, new AgentToolData(json), providers, List.of(protocol), json).execute(run.id(), next.ownerToken());
            verifyNoInteractions(protocol); assertEquals("COMPLETED", store.get(run.id()).status()); assertEquals(0, toolCalls.get());
        }
    }

    @Test
    void cliReceiptWithoutProcessTreeStopProofCannotReleaseTheRun() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginNative(run.id(), old.ownerToken(), "cli:unknown", "fixture-host");
        store.nativeStarted(run.id(), old.ownerToken(), "cli:unknown", Long.MAX_VALUE, "2026-11-03T18:00:00Z");
        expire(run.id()); store.recoverExpired(run.id()); long version = store.get(run.id()).version();
        assertThrows(IllegalArgumentException.class, () -> store.endNative(run.id(), old.ownerToken(), "cli:unknown", Map.of("stopped", true)));
        store.recoverExpired(run.id());
        assertEquals(version, store.get(run.id()).version()); assertEquals("WAITING_RESOURCE", store.get(run.id()).status());
        assertNotNull(store.get(run.id()).externalHandle()); assertEquals(1, mapper.countOwnedRuns(null));
    }

    @Test
    void cancellationAcceptsLateProofButNeverResumesTheCancelledTask() {
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        int step = store.beginModel(run.id(), old.ownerToken(), Map.of());
        expire(run.id()); service.cancel(run.taskId(), run.id(), store.get(run.id()).version(), "owner");
        store.endModel(run.id(), old.ownerToken(), step, Map.of("succeeded", false, "error", "cancelled response"));
        store.recoverExpired(run.id());
        assertEquals("CANCELLED", store.get(run.id()).status()); assertNull(store.get(run.id()).externalHandle());
        assertEquals(0, mapper.countOwnedRuns(null)); assertNull(store.claim(4));
    }

    @Test
    void savedCliResultAndConsumptionMarkerCommitTogether() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginNative(run.id(), old.ownerToken(), "cli:atomic", "fixture-host");
        store.endNative(run.id(), old.ownerToken(), "cli:atomic", nativeResult("RESULT", "已完成的原生报告"));
        expire(run.id()); store.recoverExpired(run.id()); var next = store.claim(4);
        jdbc.execute("ALTER TABLE pm_agent_event ADD CONSTRAINT fail_consumption CHECK(event_key <> 'cli:atomic:handled')");
        assertThrows(RuntimeException.class, () -> store.finishSavedNative(run.id(), next.ownerToken()));
        assertEquals("RUNNING", store.get(run.id()).status()); assertTrue(mapper.listArtifacts(run.id()).isEmpty());
        assertNull(store.event(run.id(), "cli:atomic:handled"));
        jdbc.execute("ALTER TABLE pm_agent_event DROP CONSTRAINT fail_consumption");
        var protocol = mock(AgentCliProtocol.class);
        new AgentNativeCliAdapter(store, access, catalog, new AgentToolData(json), providers, List.of(protocol), json).execute(run.id(), next.ownerToken());
        verifyNoInteractions(protocol); assertEquals("COMPLETED", store.get(run.id()).status()); assertEquals(1, mapper.listArtifacts(run.id()).size());
        assertNotNull(store.event(run.id(), "cli:atomic:handled"));
    }

    @Test
    void stoppedCliWithoutResultDoesNotRestartAutomatically() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginNative(run.id(), old.ownerToken(), "cli:stopped", "fixture-host"); expire(run.id());
        store.endNative(run.id(), old.ownerToken(), "cli:stopped", Map.of("processTreeStopped", true, "resultAvailable", false));
        store.recoverExpired(run.id()); var next = store.claim(4); var protocol = mock(AgentCliProtocol.class);
        new AgentNativeCliAdapter(store, access, catalog, new AgentToolData(json), providers, List.of(protocol), json).execute(run.id(), next.ownerToken());
        verifyNoInteractions(protocol); assertEquals("FAILED", store.get(run.id()).status()); assertTrue(mapper.listArtifacts(run.id()).isEmpty());
    }

    @Test
    void newInputIsCheckedInsideModelToolAndFinishTransactions() {
        var run = inspectionRun(List.of("REPORT")); var claim = store.claim(4);
        long watermark = store.events(run.id()).getLast().ordinal();
        service.message(run.taskId(), new AgentMessageRequest(run.id(), "before-start", store.get(run.id()).version(), "改为只做报告"), "owner");
        assertThrows(AgentRunStore.InputChanged.class, () -> store.beginModel(run.id(), claim.ownerToken(), Map.of(), watermark));
        assertThrows(AgentRunStore.InputChanged.class, () -> store.beginNative(run.id(), claim.ownerToken(), "cli:old-input", "fixture-host", watermark));
        assertEquals(0, store.get(run.id()).stepCount()); assertNull(store.get(run.id()).externalHandle());
        int step = store.beginModel(run.id(), claim.ownerToken(), Map.of());
        store.endModel(run.id(), claim.ownerToken(), step, Map.of("succeeded", true));
        service.message(run.taskId(), new AgentMessageRequest(run.id(), "before-action", store.get(run.id()).version(), "先等我核对"), "owner");
        assertThrows(AgentRunStore.InputChanged.class, () -> store.beginApiTool(run.id(), claim.ownerToken(), "step:1:tool", tool, json.createObjectNode(), "step:1:request"));
        assertThrows(AgentRunStore.InputChanged.class, () -> store.finishDecision(run.id(), claim.ownerToken(), "step:1:request", "COMPLETED", "旧结论"));
        assertNull(store.event(run.id(), "step:1:tool:request")); assertTrue(mapper.listArtifacts(run.id()).isEmpty()); assertEquals("RUNNING", store.get(run.id()).status());
    }

    private Map<String, Object> nativeResult(String type, String summary) {
        return Map.of("processTreeStopped", true, "exitCode", 0, "result", Map.of("type", type, "summary", summary));
    }

    @Test
    void humanReplyContinuesAfterWaitingDecisionRatherThanRepeatingOldQuestion() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenReturn(answer("{\"type\":\"NEEDS_INPUT\",\"plan\":\"等待业务口径\",\"summary\":\"请补充口径\"}"),
                answer("{\"type\":\"RESULT\",\"plan\":\"完成\",\"summary\":\"已按补充口径整理\"}"));
        adapter(gateway).execute(run.id(), claim.ownerToken());
        assertEquals("WAITING_INPUT", store.get(run.id()).status());
        service.message(run.taskId(), new AgentMessageRequest(run.id(), "reply", store.get(run.id()).version(), "仅根据提供资料整理"), "owner");
        var resumed = store.claim(4);
        adapter(gateway).execute(run.id(), resumed.ownerToken());
        assertEquals("COMPLETED", store.get(run.id()).status());
        verify(gateway, times(2)).executeAgentApi(any());
    }

    @Test
    void modelReportCannotClaimCodeAndActualTestArtifactsExist() {
        var run = inspectionRun(List.of("REPORT", "CHANGE_SET", "TEST_REPORT"));
        var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenReturn(answer("{\"type\":\"RESULT\",\"plan\":\"完成\",\"summary\":\"声称已经编码并测试\"}"));
        adapter(gateway).execute(run.id(), claim.ownerToken());
        assertEquals("WAITING_INPUT", store.get(run.id()).status());
        assertTrue(store.get(run.id()).errorMessage().contains("尚未取得"));
    }

    @Test
    void malformedModelOutputHasBoundedCorrectionAndCannotExecuteHiddenTools() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenReturn(answer("{\"type\":\"RESULT\",\"plan\":\"完成\",\"summary\":\"报告\"} {\"tool\":\"shell\"}"));
        adapter(gateway).execute(run.id(), claim.ownerToken());
        assertEquals("FAILED", store.get(run.id()).status());
        verify(gateway, times(3)).executeAgentApi(any());
        assertEquals(0, toolCalls.get());
    }

    @Test
    void cancellationAndBudgetAreCheckedBeforeNextExternalAction() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        service.cancel(run.taskId(), run.id(), claim.version(), "owner");
        assertThrows(AgentRunStore.StopRequested.class, () -> store.beginModel(run.id(), claim.ownerToken(), Map.of()));
        store.finish(run.id(), claim.ownerToken(), "FAILED", null, "stopped");
        assertEquals("CANCELLED", store.get(run.id()).status());
        var second = inspectionRun(List.of("REPORT"));
        var secondClaim = store.claim(4);
        store.heartbeat(second.id(), secondClaim.ownerToken(), 60001);
        assertThrows(AgentRunStore.BudgetExceeded.class, () -> store.beginModel(second.id(), secondClaim.ownerToken(), Map.of()));
    }

    private AgentRunEntity inspectionRun(List<String> artifacts) {
        var agent = enabledAgent();
        var specification = new AgentTaskSpecification("主动巡检", "", "返回证据", artifacts, List.of(testScope), List.of(tool.code()));
        var task = service.createTask(agent.id(), new AgentTaskCreateRequest(UUID.randomUUID().toString(), specification, manual(), true), "owner");
        return mapper.findRun(service.runs(task.id(), "owner", 1, 20).items().getFirst().id());
    }

    private AgentApiExecutionAdapter adapter(AiGatewayClient gateway) {
        return new AgentApiExecutionAdapter(store, catalog, new AgentToolData(json), gateway, json);
    }

    @Test
    void runScopedMcpRejectsForeignTokensAndDeduplicatesToolRequests() throws Exception {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        store.beginNative(run.id(), claim.ownerToken(), "cli:test", "test-host");
        try (var bridge = new AgentMcpBridge(store, access, catalog, new AgentToolData(json), json, run.id(), claim.ownerToken(), "cli:test");
             var client = java.net.http.HttpClient.newHttpClient()) {
            String call = "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"monitor_read\",\"arguments\":{}}}";
            assertEquals(401, rpc(client, bridge, "foreign-token", call, false).statusCode());
            assertEquals(403, rpc(client, bridge, bridge.token(), call, true).statusCode());
            var discovery = rpc(client, bridge, bridge.token(), "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"server/discover\"}", false);
            assertEquals(-32601, json.readTree(discovery.body()).path("error").path("code").asInt());
            assertEquals(0, toolCalls.get());
            var result = rpc(client, bridge, bridge.token(), call, false);
            assertEquals(200, result.statusCode());
            assertTrue(result.body().contains("NEVER_RUN"));
            assertEquals(result.body(), rpc(client, bridge, bridge.token(), call, false).body());
            assertEquals(1, toolCalls.get());
            String changed = call.replace("\"arguments\":{}", "\"arguments\":{\"different\":true}");
            assertTrue(rpc(client, bridge, bridge.token(), changed, false).body().contains("不同工具参数"));
            assertTrue(rpc(client, bridge, bridge.token(), call.replace("monitor_read", "unknown_tool"), false).body().contains("未授权"));
            assertEquals("CLI", json.readTree(store.get(run.id()).externalHandle()).path("kind").asText());
            assertFalse(json.readTree(store.get(run.id()).externalHandle()).has("activeTool"));
            service.cancel(run.taskId(), run.id(), store.get(run.id()).version(), "owner");
            assertTrue(rpc(client, bridge, bridge.token(), call.replace("10", "11"), false).body().contains("请求取消"));
            assertEquals(1, toolCalls.get());
        }
    }

    @Test
    void nativeCliUsesWholeTaskAndRealProcessWithScopedToolsWithoutModelStepProtocol() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        nativeAdapter("normal").execute(run.id(), claim.ownerToken());
        assertEquals("COMPLETED", store.get(run.id()).status());
        assertEquals(1, toolCalls.get());
        assertNull(store.get(run.id()).externalHandle());
        assertTrue(store.events(run.id()).stream().anyMatch(event -> "CLI_STARTED".equals(event.kind())));
        assertFalse(store.events(run.id()).stream().anyMatch(event -> "MODEL_REQUEST".equals(event.kind())));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "workhub.test.zcode.command", matches = ".+")
    void installedZcodeCompletesTaskThroughProductionMcpAndStoresObservedResult(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 8);
        var requests = new CopyOnWriteArrayList<JsonNode>();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(executor);
            server.createContext("/v1/chat/completions", exchange -> {
                try (exchange) {
                    var request = json.readTree(exchange.getRequestBody().readNBytes(1_000_001));
                    requests.add(request);
                    assertEquals("glm-4.6", request.path("model").asText());
                    var names = new TreeSet<String>();
                    request.path("tools").forEach(item -> names.add(item.path("function").path("name").asText()));
                    assertEquals(Set.of("mcp__workhub_task__monitor_read"), names);
                    Object delta;
                    String reason;
                    if (requests.size() == 1) {
                        assertTrue(request.path("messages").toString().contains("主动巡检"));
                        delta = Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0, "id", "workhub-call", "type", "function",
                                "function", Map.of("name", "mcp__workhub_task__monitor_read", "arguments", "{}"))));
                        reason = "tool_calls";
                    } else {
                        assertEquals(1, toolCalls.get());
                        assertTrue(request.path("messages").toString().contains("NEVER_RUN"));
                        delta = Map.of("role", "assistant", "content", "{\"type\":\"RESULT\",\"summary\":\"根据真实工具回执，该监测尚未执行\"}");
                        reason = "stop";
                    }
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    for (var chunk : List.of(Map.of("index", 0, "delta", delta), Map.of("index", 0, "delta", Map.of(), "finish_reason", reason))) {
                        String event = json.writeValueAsString(Map.of("id", "local-model", "object", "chat.completion.chunk", "model", "glm-4.6", "created", 1, "choices", List.of(chunk)));
                        exchange.getResponseBody().write(("data: " + event + "\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Throwable ex) { failure.compareAndSet(null, ex); }
            });
            server.start();
            var provider = providers.getEnabledById(1L);
            provider.setChannelType("CLI"); provider.setCliType("ZCODE");
            provider.setCliCommand(System.getProperty("workhub.test.zcode.command"));
            provider.setCliRuntimeCommand(System.getProperty("workhub.test.zcode.node"));
            provider.setCliBuiltinConfigPath(System.getProperty("workhub.test.zcode.builtin"));
            provider.setDefaultModel("fixture/glm-4.6"); provider.setDefaultReasoningLevel("disabled"); provider.setDefaultSpeedMode("STANDARD");
            provider.setCallTimeoutSeconds(20);
            Path personal = directory.resolve("provider.json");
            java.nio.file.Files.writeString(personal, json.writeValueAsString(Map.of("schemaVersion", 1, "config", Map.of(
                    "providerConfigRules", Map.of("providerRules", List.of(Map.of("providerId", "fixture", "providerName", "Local Fixture", "config", Map.of(
                            "group", "standard-personal", "access", Map.of("type", "api-key", "apiKey", "local-test-not-a-secret"),
                            "api", Map.of("type", "openai-chat-completions", "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), "personalModelIds", List.of("glm-4.6"))))),
                    "modelConfigRules", Map.of("providerModelRules", List.of(), "manualProviderModelRules", List.of()),
                    "defaultModelSelection", cn.aslight.workhub.service.ai.AiCliType.selection("fixture/glm-4.6", "disabled")))));
            provider.setCliPersonalConfigPath(personal.toString());
            var agent = service.create(new AgentSaveRequest(null, config("ZCode 本机联通验证", "fixture/glm-4.6")), "owner");
            service.setEnabled(agent.id(), agent.version(), true, "owner");
            var specification = new AgentTaskSpecification("主动巡检", "", "返回证据", List.of("REPORT"), List.of(testScope), List.of(tool.code()));
            var task = service.createTask(agent.id(), new AgentTaskCreateRequest("zcode-actual", specification, manual(), true), "owner");
            var claim = store.claim(4);
            var adapter = new AgentNativeCliAdapter(store, access, catalog, new AgentToolData(json), providers,
                    List.of(new ZCodeAgentCliProtocol(new cn.aslight.workhub.service.ai.ZCodeCliClient(json))), json);
            adapter.execute(claim.id(), claim.ownerToken());
            assertNull(failure.get(), () -> "本机模型桩失败：" + failure.get());
            var detail = service.runDetail(task.id(), claim.id(), "owner");
            assertEquals("COMPLETED", detail.run().status());
            assertEquals(2, requests.size()); assertEquals(1, toolCalls.get());
            assertNull(store.get(claim.id()).externalHandle());
            assertTrue(detail.events().stream().anyMatch(event -> "TOOL_RESULT".equals(event.kind()) && event.payloadJson().contains("NEVER_RUN")));
            assertEquals(1, mapper.listArtifacts(claim.id()).size());
            assertTrue(mapper.listArtifacts(claim.id()).getFirst().payloadJson().contains("该监测尚未执行"));
        } finally { server.stop(0); }
    }

    @Test
    void cancellingNativeCliStopsActualProcessBeforeClaimingCancellationComplete() throws Exception {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = pool.submit(() -> nativeAdapter("hang").execute(run.id(), claim.ownerToken()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            JsonNode handle = null;
            while (System.nanoTime() < deadline) {
                String text = store.get(run.id()).externalHandle();
                if (text != null) { handle = json.readTree(text); if (handle.has("pid")) break; }
                Thread.sleep(20);
            }
            assertNotNull(handle);
            assertTrue(handle.has("pid"));
            long pid = handle.path("pid").asLong();
            service.cancel(run.taskId(), run.id(), store.get(run.id()).version(), "owner");
            var stopped = assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
            assertInstanceOf(AgentRunStore.StopRequested.class, stopped.getCause());
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
            assertNull(store.get(run.id()).externalHandle());
            store.finish(run.id(), claim.ownerToken(), "FAILED", null, "cancelled");
            assertEquals("CANCELLED", store.get(run.id()).status());
        }
    }

    @Test
    void inputArrivingDuringModelCallInvalidatesItsOldFinalAnswer() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        var calls = new AtomicInteger();
        when(gateway.executeAgentApi(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                service.message(run.taskId(), new AgentMessageRequest(run.id(), "new-input", store.get(run.id()).version(), "请补充数据来源"), "owner");
                return answer("{\"type\":\"RESULT\",\"plan\":\"完成\",\"summary\":\"旧答案\"}");
            }
            assertTrue(((cn.aslight.workhub.service.ai.AiAgentApiRequest) invocation.getArgument(0)).prompt().contains("请补充数据来源"));
            return answer("{\"type\":\"RESULT\",\"plan\":\"补充来源\",\"summary\":\"已根据新指令给出来源\"}");
        });
        adapter(gateway).execute(run.id(), claim.ownerToken());
        assertEquals(2, calls.get());
        assertEquals("已根据新指令给出来源", store.get(run.id()).resultText());
    }

    @Test
    void inputArrivingDuringModelCallAlsoPreventsItsOldToolAction() {
        var run = inspectionRun(List.of("REPORT")); var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class); var calls = new AtomicInteger();
        when(gateway.executeAgentApi(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                service.message(run.taskId(), new AgentMessageRequest(run.id(), "skip-tool", store.get(run.id()).version(), "不要继续查询，先解释现有资料"), "owner");
                return answer("{\"type\":\"TOOL_CALL\",\"plan\":\"旧巡检行动\",\"tool\":\"monitor.read\",\"arguments\":{}}");
            }
            assertTrue(((cn.aslight.workhub.service.ai.AiAgentApiRequest) invocation.getArgument(0)).prompt().contains("不要继续查询"));
            return answer("{\"type\":\"RESULT\",\"plan\":\"按新指令解释\",\"summary\":\"只解释已有资料，没有发出查询\"}");
        });
        adapter(gateway).execute(run.id(), claim.ownerToken());
        assertEquals(2, calls.get()); assertEquals(0, toolCalls.get()); assertEquals("COMPLETED", store.get(run.id()).status());
        assertFalse(store.events(run.id()).stream().anyMatch(event -> "TOOL_REQUEST".equals(event.kind())));
    }

    @Test
    void cliIsStoppedBeforeReplanningWithNewInstructions() throws Exception {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var claim = store.claim(4); var attempts = new AtomicInteger();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = pool.submit(() -> nativeAdapter(() -> attempts.incrementAndGet() == 1 ? "hang" : "expect-new-input").execute(run.id(), claim.ownerToken()));
            JsonNode handle = null; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                String raw = store.get(run.id()).externalHandle();
                if (raw != null) { handle = json.readTree(raw); if (handle.has("pid")) break; }
                Thread.sleep(20);
            }
            assertNotNull(handle); assertTrue(handle.has("pid")); long originalPid = handle.path("pid").asLong();
            String originalAttempt = handle.path("key").asText();
            service.message(run.taskId(), new AgentMessageRequest(run.id(), "cli-new-input", store.get(run.id()).version(), "新的巡检要求：明确数据时间"), "owner");
            running.get(10, TimeUnit.SECONDS);
            assertFalse(ProcessHandle.of(originalPid).map(ProcessHandle::isAlive).orElse(false));
            assertEquals(2, attempts.get()); assertEquals(1, toolCalls.get()); assertEquals("COMPLETED", store.get(run.id()).status());
            assertEquals("INPUT_CHANGED", store.event(run.id(), originalAttempt + ":handled").kind());
            assertTrue(json.readTree(store.event(run.id(), originalAttempt + ":result").payloadJson()).path("processTreeStopped").asBoolean());
        }
    }

    @Test
    void recoveredCliQuestionIsConsumedOnceAndHumanReplyStartsTheNextTask() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginNative(run.id(), old.ownerToken(), "cli:question", "fixture-host");
        store.endNative(run.id(), old.ownerToken(), "cli:question", nativeResult("NEEDS_INPUT", "请补充时间窗口"));
        expire(run.id()); store.recoverExpired(run.id()); var next = store.claim(4);
        assertTrue(store.finishSavedNative(run.id(), next.ownerToken())); assertEquals("WAITING_INPUT", store.get(run.id()).status());
        service.message(run.taskId(), new AgentMessageRequest(run.id(), "answer-question", store.get(run.id()).version(), "查询最近一小时"), "owner");
        var resumed = store.claim(4); nativeAdapter("normal").execute(run.id(), resumed.ownerToken());
        assertEquals("COMPLETED", store.get(run.id()).status()); assertEquals(1, toolCalls.get());
        assertEquals(2, store.events(run.id()).stream().filter(event -> "CLI_REQUEST".equals(event.kind())).count());
    }

    private java.net.http.HttpResponse<String> rpc(java.net.http.HttpClient client, AgentMcpBridge bridge,
                                                  String token, String body, boolean browser) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(bridge.url())).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
        if (browser) request.header("Origin", "https://untrusted.example");
        return client.send(request.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private AgentNativeCliAdapter nativeAdapter(String mode) {
        return nativeAdapter(() -> mode);
    }

    private AgentNativeCliAdapter nativeAdapter(java.util.function.Supplier<String> mode) {
        var protocol = new AgentCliProtocol() {
            public boolean supports(AiProviderConfigEntity provider) { return true; }
            public void preflight(AiProviderConfigEntity provider) { }
            public cn.aslight.workhub.service.ai.CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, Path directory, Path schema, Path output,
                    String mcpUrl, String token, List<String> names, String prompt) {
                var builder = new ProcessBuilder(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                        FakeAgentCliProcess.class.getName(), mcpUrl, output.toString(), mode.get()));
                builder.environment().put("WORKHUB_AGENT_RUN_TOKEN", token);
                return new cn.aslight.workhub.service.ai.CliTaskLaunch(builder, new cn.aslight.workhub.service.ai.CliTaskConversation() {
                    public void start(java.io.OutputStream input) throws java.io.IOException { try (input) { input.write(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } }
                    public String accept(tools.jackson.databind.JsonNode event) { return null; }
                });
            }
        };
        return new AgentNativeCliAdapter(store, access, catalog, new AgentToolData(json), providers, List.of(protocol), json);
    }

    private AiGatewayResult answer(String output) { return AiGatewayResult.succeeded(output, "configured", "first-model", 10); }
    @Test
    void changingProviderChannelOrVersionCannotSilentlySwitchAnExistingTaskToCli() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        providers.getEnabledById(1L).setChannelType("CLI");
        assertThrows(IllegalStateException.class, () -> store.requireActive(run.id(), claim.ownerToken()));
        providers.getEnabledById(1L).setChannelType("API");
        providers.getEnabledById(1L).setVersion(2);
        assertThrows(IllegalStateException.class, () -> store.beginModel(run.id(), claim.ownerToken(), Map.of()));
        assertEquals(0, store.get(run.id()).stepCount());
    }

    @Test
    void retryCreatesOneNewSnapshotAndKeepsOriginalEvidence() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("retry", true), "owner");
        var original = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        jdbc.update("UPDATE pm_agent_run SET status='FAILED',error_message='previous failure' WHERE id=?", original.id());
        service.update(agent.id(), new AgentSaveRequest(agent.version(), config("调整后的 Agent", "second-model")), "owner");
        var request = new AgentRunRequest("retry-once", original.version());
        var retried = service.retry(task.id(), original.id(), request, "owner");
        assertEquals(retried.id(), service.retry(task.id(), original.id(), request, "owner").id());
        assertNotEquals(original.id(), retried.id());
        assertEquals("QUEUED", retried.status());
        assertEquals("second-model", service.runDetail(task.id(), retried.id(), "owner").configuration().model());
        assertEquals("first-model", service.runDetail(task.id(), original.id(), "owner").configuration().model());
        assertEquals("previous failure", mapper.findRun(original.id()).errorMessage());
        assertEquals(2, service.runs(task.id(), "owner", 1, 20).total());
        assertTrue(service.runDetail(task.id(), original.id(), "owner").run().retryAllowed());
        assertFalse(retried.retryAllowed());
        assertTrue(mapper.listEvents(retried.id()).stream().anyMatch(event -> "RETRY".equals(event.kind()) && event.payloadJson().contains(original.id())));
    }

    @Test
    void retryCannotDuplicateUnreconciledExternalWork() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("retry-unknown", true), "owner");
        var original = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        jdbc.update("UPDATE pm_agent_run SET status='WAITING_RESOURCE',owner_token='old-worker',external_handle='unknown-process' WHERE id=?", original.id());
        assertFalse(service.runDetail(task.id(), original.id(), "owner").run().retryAllowed());
        assertThrows(IllegalStateException.class, () -> service.retry(task.id(), original.id(), new AgentRunRequest("retry-unknown", original.version()), "owner"));
        assertEquals(1, service.runs(task.id(), "owner", 1, 20).total());
        assertEquals("unknown-process", mapper.findRun(original.id()).externalHandle());
    }
    private void expire(String runId) { jdbc.update("UPDATE pm_agent_run SET lease_until='2026-11-03 17:59:00' WHERE id=?", runId); }

    private AgentConfiguration config(String name, String model) {
        return new AgentConfiguration(name, "按分派目标执行", 1L, model, List.of(testScope), List.of("monitor.read"), 20, 60, 2);
    }

    private AgentTaskCreateRequest taskRequest(String key, boolean immediate) {
        return new AgentTaskCreateRequest(key, new AgentTaskSpecification("整理我提供的资料", "输入文本", "返回分析报告", List.of("REPORT"), List.of(), List.of()), manual(), immediate);
    }

    private AgentScheduleRequest manual() { return new AgentScheduleRequest(null, "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false); }
}
