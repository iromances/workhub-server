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
    private AgentRunStore store;
    private AgentRunReconciliationService reconciliation;
    private AgentCliProcessProbe processes;
    private AiProviderConfigService providers;
    private com.sun.net.httpserver.HttpServer mcpStub;

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
        // 删除前核对涉及的过程数据表；本测试只建最小结构供 COUNT 关联。
        for (String[] table : new String[][]{
                {"pm_agent_observation", "id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36), agent_id VARCHAR(36)"},
                {"pm_agent_change_set", "id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36)"},
                {"pm_agent_code_workspace", "id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36)"},
                {"pm_agent_build_test", "id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36)"},
                {"pm_agent_release_plan", "id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36)"},
                {"pm_agent_deployment", "id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36)"},
                {"pm_agent_verification", "id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36)"},
                {"pm_agent_inspection_item", "id VARCHAR(36) PRIMARY KEY, assigned_run_id VARCHAR(36)"},
                {"pm_autonomy_case_run", "case_id VARCHAR(36), run_id VARCHAR(36), PRIMARY KEY(case_id, run_id)"}})
            jdbc.execute("CREATE TABLE " + table[0] + "(" + table[1] + ")");
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
        var target = new AgentWorkbenchService(mapper, access, providers, json, clock);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(interceptor);
        service = (AgentWorkbenchService) proxy.getProxy();
        var storeProxy = new ProxyFactory(new AgentRunStore(mapper, json, access, new AgentToolData(json), providers, clock, AgentAcceptanceTestSupport.withoutCode(mapper, access, json, clock)));
        storeProxy.addAdvice(interceptor);
        store = (AgentRunStore) storeProxy.getProxy();
        processes = mock(AgentCliProcessProbe.class);
        when(processes.observe(any(), any(), any())).thenReturn("REMOTE_HOST");
        var reconciliationProxy = new ProxyFactory(new AgentRunReconciliationService(mapper, access, store,
                new AgentToolData(json), processes, json, clock));
        reconciliationProxy.addAdvice(interceptor);
        reconciliation = (AgentRunReconciliationService) reconciliationProxy.getProxy();
        // 平台标准 MCP 入口的本地桩：CLI 子进程经适配器注入的地址与 Token 调用。
        mcpStub = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        mcpStub.createContext("/api/mcp/runtime", exchange -> {
            try (exchange) {
                boolean authorized = ("Bearer test-mcp-token").equals(exchange.getRequestHeaders().getFirst("Authorization"));
                String body = authorized
                        ? "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"dataState\\\":\\\"NEVER_RUN\\\",\\\"source\\\":\\\"platform-mcp-stub\\\"}\"}]}}"
                        : "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"unauthorized\"}}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(authorized ? 200 : 401, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        mcpStub.start();
    }

    @AfterEach
    void close() throws Exception {
        if (mcpStub != null) mcpStub.stop(0);
        keepAlive.close();
    }

    @Test
    void completionTargetIsExplicitAndFrozenWithTaskAndRun() {
        var agent=enabledAgent(); var prod=new AgentScope("BL000012","PROD");
        var original=agent.configuration();
        var configured=service.update(agent.id(),new AgentSaveRequest(agent.version(),new AgentConfiguration(original.name(),original.instructions(),
                original.providerConfigId(),original.model(),List.of(testScope,prod),original.maxSteps(),original.maxRunSeconds(),original.maxConcurrentRuns())),"owner");
        var spec=new AgentTaskSpecification("修复并发布","","测试环境通过",List.of("DEPLOYMENT","VERIFICATION"),List.of(testScope,prod),List.of(testScope));
        var task=service.createTask(agent.id(),new AgentTaskCreateRequest("target",spec,manual(),true),"owner");
        var run=service.runs(task.id(),"owner",1,20).items().getFirst();
        assertEquals(List.of(testScope),task.specification().completionTargets());
        assertEquals(List.of(testScope),service.runDetail(task.id(),run.id(),"owner").specification().completionTargets());
        service.update(agent.id(),new AgentSaveRequest(configured.version(),config("调整职责","model")),"owner");
        assertEquals(List.of(testScope),service.runDetail(task.id(),run.id(),"owner").specification().completionTargets());
        var changed=new AgentTaskSpecification(spec.objective(),spec.context(),spec.acceptanceCriteria(),spec.expectedArtifacts(),spec.scopes(),List.of(prod));
        assertThrows(IllegalArgumentException.class,()->service.createTask(agent.id(),new AgentTaskCreateRequest("target",changed,manual(),true),"owner"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_run",Integer.class));
    }

    @Test
    void missingDuplicateCrossScopeOrNonReleaseTargetsCannotCreateTask() {
        var agent=enabledAgent();
        var targets=List.of(List.<AgentScope>of(),List.of(testScope,testScope),List.of(new AgentScope("BL000012","PROD")),List.of(new AgentScope("BL000099","TEST")));
        for(var target:targets){
            var spec=new AgentTaskSpecification("发布","","通过",List.of("DEPLOYMENT"),List.of(testScope),target);
            assertThrows(IllegalArgumentException.class,()->service.createTask(agent.id(),new AgentTaskCreateRequest(UUID.randomUUID().toString(),spec,manual(),true),"owner"));
        }
        var report=new AgentTaskSpecification("报告","","通过",List.of("REPORT"),List.of(testScope),List.of(testScope));
        assertThrows(IllegalArgumentException.class,()->service.createTask(agent.id(),new AgentTaskCreateRequest("report-target",report,manual(),false),"owner"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_task",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_run",Integer.class));
    }

    @Test
    void legacyReleaseTaskCannotInferCompletionFromAuthorizedScope() {
        var agent=enabledAgent();
        var spec=new AgentTaskSpecification("发布","","通过",List.of("DEPLOYMENT","VERIFICATION"),List.of(testScope),List.of(testScope));
        var task=service.createTask(agent.id(),new AgentTaskCreateRequest("old-target",spec,manual(),true),"owner");
        var run=service.runs(task.id(),"owner",1,20).items().getFirst();
        var old=(tools.jackson.databind.node.ObjectNode)json.valueToTree(spec);old.remove("completionTargets");
        jdbc.update("UPDATE pm_agent_task SET specification_json=? WHERE id=?",json.writeValueAsString(old),task.id());
        jdbc.update("UPDATE pm_agent_run SET specification_json=? WHERE id=?",json.writeValueAsString(old),run.id());
        assertTrue(service.runDetail(task.id(),run.id(),"owner").specification().completionTargets().isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.run(task.id(),new AgentRunRequest("legacy-start",task.version()),"owner"));
        var check=AgentAcceptanceTestSupport.withoutCode(mapper,access,json,clock).preview(task.id(),run.id(),"owner");
        assertFalse(check.satisfied());assertTrue(check.items().stream().allMatch(item->"BLOCKED".equals(item.status())));
        var claim=store.claim(4);store.finish(run.id(),claim.ownerToken(),"COMPLETED","声称完成",null);
        assertEquals("WAITING_INPUT",store.get(run.id()).status());
    }

    @Test
    void completionTargetsRemainSeparateFromArtifactEvidence() {
        var agent=enabledAgent();var prod=new AgentScope("BL000012","PROD");var original=agent.configuration();
        service.update(agent.id(),new AgentSaveRequest(agent.version(),new AgentConfiguration(original.name(),original.instructions(),original.providerConfigId(),
                original.model(),List.of(testScope,prod),original.maxSteps(),original.maxRunSeconds(),original.maxConcurrentRuns())),"owner");
        var spec=new AgentTaskSpecification("发布及复查","","逐环境通过",List.of("DEPLOYMENT","VERIFICATION"),List.of(testScope,prod),List.of(testScope,prod));
        var task=service.createTask(agent.id(),new AgentTaskCreateRequest("both-targets",spec,manual(),true),"owner");
        var run=service.runs(task.id(),"owner",1,20).items().getFirst();
        var check=AgentAcceptanceTestSupport.withoutCode(mapper,access,json,clock).preview(task.id(),run.id(),"owner");
        assertFalse(check.satisfied());
        for(var item:check.items()){
            assertEquals(List.of(testScope,prod),item.targets().stream().map(AgentRunAcceptance.Target::scope).toList());
            assertTrue(item.targets().stream().allMatch(target->"UNAVAILABLE".equals(target.status()) && target.evidence().isEmpty()));
        }
    }

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
        var spec = new AgentTaskSpecification("巡检", "", "返回证据", List.of("REPORT"), List.of(new AgentScope("BL000012", "PROD")));
        var request = new AgentTaskCreateRequest("scope", spec, manual(), false);
        assertThrows(IllegalArgumentException.class, () -> service.createTask(agent.id(), request, "owner"));
        assertThrows(AccessDeniedException.class, () -> service.detail(agent.id(), "outsider"));
        assertEquals(0, service.list("outsider", 1, 20).total());
    }

    @Test
    void oldSnapshotCannotKeepRevokedOperationOrScopePermissions() {
        var agent = enabledAgent();
        var spec = new AgentTaskSpecification("巡检", "", "返回证据", List.of("REPORT"), List.of(testScope));
        var context = new AgentToolContext("run", "task", agent.id(), "owner", agent.configuration(), spec);
        access.requireExecution(context, "monitor.read", Set.of("ops:monitor:view"));
        grants.put(1L, List.of("agent:view", "agent:manage", "agent:task:assign", "agent:task:view", "agent:task:operate"));
        assertThrows(AccessDeniedException.class, () -> access.requireExecution(context, "monitor.read", Set.of("ops:monitor:view")));
        service.update(agent.id(), new AgentSaveRequest(agent.version(),
                new AgentConfiguration("收窄", "检查", 1L, "first-model", List.of(), 20, 60, 2)), "owner");
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

    @Test
    void taskSpecificationUpdateAffectsFutureRunsWhileOldRunsKeepSnapshot() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("spec-update", true), "owner");
        var run = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        var changed = new AgentTaskSpecification("整理新的资料目标", "新的输入", "返回新版报告", List.of("REPORT"), List.of());
        var updated = service.updateTask(task.id(), new AgentTaskUpdateRequest(task.version(), changed, null), "owner");
        assertEquals("整理新的资料目标", updated.specification().objective());
        assertEquals(task.version() + 1, updated.version());
        assertEquals("MANUAL", updated.schedule().mode());
        assertEquals("整理我提供的资料", service.runDetail(task.id(), run.id(), "owner").specification().objective());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_audit WHERE object_type='TASK' AND action='UPDATE_SPEC'", Integer.class));
        var outOfScope = new AgentTaskSpecification("越权", "", "报告", List.of("REPORT"), List.of(new AgentScope("BL000012", "PROD")));
        assertThrows(IllegalArgumentException.class, () -> service.updateTask(task.id(), new AgentTaskUpdateRequest(updated.version(), outOfScope, null), "owner"));
        assertThrows(IllegalStateException.class, () -> service.updateTask(task.id(), new AgentTaskUpdateRequest(task.version(), changed, null), "owner"));
        assertEquals("整理新的资料目标", service.tasks(agent.id(), "owner", 1, 20).items().getFirst().specification().objective());
    }

    @Test
    void incrementalTasksReceivePlatformComputedDataWindow() {
        var agent = enabledAgent();
        var specification = new AgentTaskSpecification("整理我提供的资料", "输入文本", "返回分析报告", List.of("REPORT"), List.of(), List.of(), "INCREMENTAL");
        var task = service.createTask(agent.id(), new AgentTaskCreateRequest("incremental", specification, manual(), true), "owner");
        var first = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        List<String> prompts = new CopyOnWriteArrayList<>();
        when(gateway.executeAgentApi(any())).thenAnswer(invocation -> {
            prompts.add(((cn.aslight.workhub.service.ai.AiAgentApiRequest) invocation.getArgument(0)).prompt());
            return answer("{\"type\":\"RESULT\",\"plan\":\"完成\",\"summary\":\"本轮分析报告\"}");
        });
        adapter(gateway).execute(first.id(), claim.ownerToken());
        assertEquals("COMPLETED", store.get(first.id()).status());
        assertTrue(prompts.getFirst().contains("INCREMENTAL"));
        assertTrue(prompts.getFirst().contains("无上一轮基线"), prompts.getFirst());
        var second = service.run(first.taskId(), new AgentRunRequest("incremental-second", 0L), "owner");
        var claim2 = store.claim(4);
        adapter(gateway).execute(second.id(), claim2.ownerToken());
        assertEquals("COMPLETED", store.get(second.id()).status());
        assertTrue(prompts.getLast().contains("2026-11-03T18:00"), prompts.getLast());
        assertFalse(prompts.getLast().contains("无上一轮基线"));
    }

    @Test
    void taskUpdateCanChangeScheduleInSameVersion() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("schedule-update", false), "owner");
        var cron = new AgentScheduleRequest(null, "CRON", "0 0 * * * *", "Asia/Shanghai", "SKIP", true);
        var updated = service.updateTask(task.id(), new AgentTaskUpdateRequest(task.version(), taskRequest("s", false).specification(), cron), "owner");
        assertEquals("CRON", updated.schedule().mode());
        assertTrue(updated.schedule().enabled());
        assertNotNull(updated.nextRunAt());
        var paused = new AgentScheduleRequest(null, "CRON", "0 0 * * * *", "Asia/Shanghai", "SKIP", false);
        var updatedAgain = service.updateTask(task.id(), new AgentTaskUpdateRequest(updated.version(), taskRequest("s", false).specification(), paused), "owner");
        assertFalse(updatedAgain.schedule().enabled());
        assertThrows(IllegalArgumentException.class, () -> service.updateTask(task.id(),
                new AgentTaskUpdateRequest(updatedAgain.version(), taskRequest("s", false).specification(),
                        new AgentScheduleRequest(null, "CRON", "not-cron", "Asia/Shanghai", "SKIP", true)), "owner"));
    }

    @Test
    void taskDeletionRequiresPausedScheduleAndFinishedRuns() {
        var agent = enabledAgent();
        var schedule = new AgentScheduleRequest(null, "CRON", "0 */5 * * * *", "Asia/Shanghai", "RUN_ONCE", true);
        var cron = service.createTask(agent.id(), new AgentTaskCreateRequest("cron-keep", taskRequest("x", false).specification(), schedule, false), "owner");
        var blocked = assertThrows(IllegalArgumentException.class, () -> service.deleteTask(cron.id(), cron.version(), "owner"));
        assertTrue(blocked.getMessage().contains("暂停"));
        assertEquals(1, service.tasks(agent.id(), "owner", 1, 20).total());
        var queued = service.createTask(agent.id(), taskRequest("queued", true), "owner");
        var unfinished = assertThrows(IllegalArgumentException.class, () -> service.deleteTask(queued.id(), queued.version(), "owner"));
        assertTrue(unfinished.getMessage().contains("未结束"));
        assertEquals(2, service.tasks(agent.id(), "owner", 1, 20).total());
    }

    @Test
    void taskDeletionRequiresNoProcessDataAndThenCascadesWithAudit() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("cleanup", true), "owner");
        var run = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        var claim = store.claim(4);
        store.finish(run.id(), claim.ownerToken(), "COMPLETED", "最终报告", null);
        jdbc.update("INSERT INTO pm_agent_observation(id,run_id) VALUES ('obs-1',?)", run.id());
        var process = assertThrows(IllegalArgumentException.class, () -> service.deleteTask(task.id(), task.version(), "owner"));
        assertTrue(process.getMessage().contains("巡检观测"));
        jdbc.update("DELETE FROM pm_agent_observation WHERE id='obs-1'");
        service.deleteTask(task.id(), task.version(), "owner");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_task WHERE id=?", Integer.class, task.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_run WHERE task_id=?", Integer.class, task.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_event WHERE run_id=?", Integer.class, run.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_artifact WHERE run_id=?", Integer.class, run.id()));
        var audit = jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_audit WHERE object_type='TASK' AND action='DELETE' AND object_id=?", Integer.class, task.id());
        assertEquals(1, audit);
        var payload = jdbc.queryForObject("SELECT payload_json FROM pm_agent_audit WHERE action='DELETE' AND object_id=?", String.class, task.id());
        assertTrue(payload.contains("deletedRuns") && payload.contains("整理我提供的资料"), payload);
    }

    @Test
    void taskUpdateAndDeleteRequireOwnershipOrManagePermission() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("permission", true), "owner");
        assertThrows(AccessDeniedException.class, () -> service.updateTask(task.id(),
                new AgentTaskUpdateRequest(task.version(), taskRequest("p", false).specification(), null), "other"));
        assertThrows(AccessDeniedException.class, () -> service.deleteTask(task.id(), task.version(), "other"));
        assertEquals(1, service.tasks(agent.id(), "owner", 1, 20).total());
    }

    @Test
    void agentDeletionRequiresDisabledAgentPausedSchedulesAndFinishedRuns() {
        var agent = enabledAgent();
        var schedule = new AgentScheduleRequest(null, "CRON", "0 */5 * * * *", "Asia/Shanghai", "RUN_ONCE", true);
        var cron = service.createTask(agent.id(), new AgentTaskCreateRequest("agent-cron", taskRequest("x", false).specification(), schedule, false), "owner");
        var queued = service.createTask(agent.id(), taskRequest("agent-queued", true), "owner");
        var run = service.runs(queued.id(), "owner", 1, 20).items().getFirst();
        jdbc.update("UPDATE pm_agent_run SET status='RUNNING',owner_token='worker-1' WHERE id=?", run.id());
        var enabledBlock = assertThrows(IllegalArgumentException.class, () -> service.deleteAgent(agent.id(), agent.version(), "owner"));
        assertTrue(enabledBlock.getMessage().contains("停用"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_definition", Integer.class));
        var disabled = service.setEnabled(agent.id(), agent.version(), false, "owner");
        var scheduleBlock = assertThrows(IllegalArgumentException.class, () -> service.deleteAgent(agent.id(), disabled.version(), "owner"));
        assertTrue(scheduleBlock.getMessage().contains("暂停"));
        service.updateTask(cron.id(), new AgentTaskUpdateRequest(cron.version(), taskRequest("x", false).specification(),
                new AgentScheduleRequest(null, "CRON", "0 */5 * * * *", "Asia/Shanghai", "RUN_ONCE", false)), "owner");
        var unfinished = assertThrows(IllegalArgumentException.class, () -> service.deleteAgent(agent.id(), disabled.version(), "owner"));
        assertTrue(unfinished.getMessage().contains("未结束"));
        assertEquals(2, service.tasks(agent.id(), "owner", 1, 20).total());
        jdbc.update("UPDATE pm_agent_run SET status='CANCELLED',owner_token=NULL WHERE id=?", run.id());
        service.deleteAgent(agent.id(), disabled.version(), "owner");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_definition", Integer.class));
    }

    @Test
    void agentDeletionRequiresNoProcessDataAndThenCascadesWithAudit() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("agent-cleanup", true), "owner");
        var run = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        var claim = store.claim(4);
        store.finish(run.id(), claim.ownerToken(), "COMPLETED", "最终报告", null);
        jdbc.update("INSERT INTO pm_agent_observation(id,run_id,agent_id) VALUES ('agent-obs-1',?,?)", run.id(), agent.id());
        var disabled = service.setEnabled(agent.id(), agent.version(), false, "owner");
        var process = assertThrows(IllegalArgumentException.class, () -> service.deleteAgent(agent.id(), disabled.version(), "owner"));
        assertTrue(process.getMessage().contains("巡检观测"));
        assertEquals(1, service.list("owner", 1, 20).total());
        jdbc.update("DELETE FROM pm_agent_observation WHERE id='agent-obs-1'");
        service.deleteAgent(agent.id(), disabled.version(), "owner");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_definition WHERE id=?", Integer.class, agent.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_task WHERE agent_id=?", Integer.class, agent.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_run WHERE agent_id=?", Integer.class, agent.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_event WHERE run_id=?", Integer.class, run.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_artifact WHERE run_id=?", Integer.class, run.id()));
        var audit = jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_audit WHERE object_type='AGENT' AND action='DELETE' AND object_id=?", Integer.class, agent.id());
        assertEquals(1, audit);
        var payload = jdbc.queryForObject("SELECT payload_json FROM pm_agent_audit WHERE action='DELETE' AND object_id=?", String.class, agent.id());
        assertTrue(payload.contains("deletedTasks") && payload.contains("Agent"), payload);
        assertEquals(0, service.list("owner", 1, 20).total());
    }

    @Test
    void agentDeletionRequiresOwnershipAndCurrentVersion() {
        var agent = enabledAgent();
        var disabled = service.setEnabled(agent.id(), agent.version(), false, "owner");
        service.update(agent.id(), new AgentSaveRequest(disabled.version(), config("已改名", "second-model")), "owner");
        assertThrows(IllegalStateException.class, () -> service.deleteAgent(agent.id(), disabled.version(), "owner"));
        assertThrows(AccessDeniedException.class, () -> service.deleteAgent(agent.id(), service.detail(agent.id(), "owner").version(), "other"));
        assertEquals(1, service.list("owner", 1, 20).total());
    }

    private AgentDefinitionResponse enabledAgent() {
        var agent = service.create(new AgentSaveRequest(null, config("Agent", "first-model")), "owner");
        return service.setEnabled(agent.id(), agent.version(), true, "owner");
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
        assertEquals(2, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        var another = enabledAgent();
        service.createTask(another.id(), taskRequest("another", true), "owner");
        assertNotNull(store.claim(3));
        assertNull(store.claim(3));
    }

    @Test
    void unresolvedExternalCallKeepsHandleWithoutBlockingNewDispatch() {
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        store.beginModel(run.id(), claim.ownerToken(), Map.of("providerConfigId", 1));
        expire(run.id());
        store.recoverExpired(run.id());
        var unresolved = store.get(run.id());
        assertEquals("WAITING_RESOURCE", unresolved.status());
        assertNotNull(unresolved.externalHandle());
        // 失联恢复后的残留 owner（租约已过期）不再占用并发额度，避免巡检等周期任务被永久堵死
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        assertNull(store.claim(4));
        var cancelled = service.cancel(run.taskId(), run.id(), unresolved.version(), "owner");
        assertEquals("CANCELLING", cancelled.status());
        assertNotNull(store.get(run.id()).externalHandle());
    }

    @Test
    void expiredRecoveryDoesNotBlockNewerQueuedRunOfSameAgent() {
        var agent = enabledAgent();
        var specification = new AgentTaskSpecification("主动巡检", "", "返回证据", List.of("REPORT"), List.of(testScope));
        var first = service.createTask(agent.id(), new AgentTaskCreateRequest(UUID.randomUUID().toString(), specification, manual(), true), "owner");
        var stale = mapper.findRun(service.runs(first.id(), "owner", 1, 20).items().getFirst().id());
        var claim = store.claim(4);
        store.beginModel(stale.id(), claim.ownerToken(), Map.of());
        expire(stale.id());
        store.recoverExpired(stale.id());
        assertEquals("WAITING_RESOURCE", store.get(stale.id()).status());
        // 单 agent 并发压到 1：若按 owner_token 计数（旧口径），过期租约的残留会永久堵死该 agent 后续轮次
        var view = service.detail(agent.id(), "owner");
        var original = view.configuration();
        service.update(agent.id(), new AgentSaveRequest(view.version(), new AgentConfiguration(original.name(), original.instructions(),
                original.providerConfigId(), original.model(), original.scopes(), original.maxSteps(), original.maxRunSeconds(), 1)), "owner");
        var second = service.createTask(agent.id(), new AgentTaskCreateRequest(UUID.randomUUID().toString(), specification, manual(), true), "owner");
        var queued = mapper.findRun(service.runs(second.id(), "owner", 1, 20).items().getFirst().id());
        assertEquals("QUEUED", queued.status());
        var next = store.claim(4);
        assertNotNull(next);
        assertEquals(queued.id(), next.id());
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
        assertEquals("QUEUED", store.get(run.id()).status()); assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        var next = store.claim(4); var gateway = mock(AiGatewayClient.class);
        assertThrows(AgentRunStore.LeaseLost.class, () -> store.endModel(run.id(), old.ownerToken(), step, receipt));
        adapter(gateway).execute(run.id(), next.ownerToken());
        verifyNoInteractions(gateway); assertEquals("COMPLETED", store.get(run.id()).status());
        assertEquals("迟到的实际结果", store.get(run.id()).resultText());
        assertEquals(1, store.events(run.id()).stream().filter(event -> "MODEL_RESPONSE".equals(event.kind())).count());
    }

    @Test
    void lateCliReceiptMustArriveBeforeReleasingTheRun() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4); String attempt = "cli:late";
        store.beginNative(run.id(), old.ownerToken(), attempt, "fixture-host");
        expire(run.id()); store.recoverExpired(run.id());
        assertEquals("WAITING_RESOURCE", store.get(run.id()).status()); assertNotNull(store.get(run.id()).externalHandle());
        assertNull(store.claim(4)); assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        store.endNative(run.id(), old.ownerToken(), attempt, nativeResult("RESULT", "已查询的报告"));
        store.recoverExpired(run.id());
        assertEquals("QUEUED", store.get(run.id()).status()); assertNull(store.get(run.id()).externalHandle());
        var next = store.claim(4); var protocol = mock(AgentCliProtocol.class);
        nativeAdapter(protocol).execute(run.id(), next.ownerToken());
        verifyNoInteractions(protocol); assertEquals("COMPLETED", store.get(run.id()).status());
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
        assertNotNull(store.get(run.id()).externalHandle()); assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
    }

    @Test
    void cancellationAcceptsLateProofButNeverResumesTheCancelledTask() {
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        int step = store.beginModel(run.id(), old.ownerToken(), Map.of());
        expire(run.id()); service.cancel(run.taskId(), run.id(), store.get(run.id()).version(), "owner");
        store.endModel(run.id(), old.ownerToken(), step, Map.of("succeeded", false, "error", "cancelled response"));
        store.recoverExpired(run.id());
        assertEquals("CANCELLED", store.get(run.id()).status()); assertNull(store.get(run.id()).externalHandle());
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock))); assertNull(store.claim(4));
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
        nativeAdapter(protocol).execute(run.id(), next.ownerToken());
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
        nativeAdapter(protocol).execute(run.id(), next.ownerToken());
        verifyNoInteractions(protocol); assertEquals("FAILED", store.get(run.id()).status()); assertTrue(mapper.listArtifacts(run.id()).isEmpty());
    }

    @Test
    void nativeCliStepFailsRunWithSceneCodeWhenPromptConfigMissing() throws Exception {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var next = store.claim(4);
        var protocol = mock(AgentCliProtocol.class);
        when(protocol.supports(any())).thenReturn(true);
        var properties = new cn.aslight.workhub.config.McpProperties();
        properties.setAccessToken("test-mcp-token");
        new AgentNativeCliAdapter(store, new AgentToolData(json), providers,
                cn.aslight.workhub.service.ai.TestPromptTemplates.missingConfigService(), promptRenderer(), platformGuide(),
                List.of(protocol), json, properties, "", "8080", mcpStubUrl()).execute(run.id(), next.ownerToken());
        assertEquals("FAILED", store.get(run.id()).status());
        var finish = store.event(run.id(), "finish:" + next.ownerToken());
        assertTrue(finish.payloadJson().contains("agent.cli.step"), finish.payloadJson());
        verify(protocol, never()).prepare(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void newInputIsCheckedInsideModelAndFinishTransactions() {
        var run = inspectionRun(List.of("REPORT")); var claim = store.claim(4);
        long watermark = store.events(run.id()).getLast().ordinal();
        service.message(run.taskId(), new AgentMessageRequest(run.id(), "before-start", store.get(run.id()).version(), "改为只做报告"), "owner");
        assertThrows(AgentRunStore.InputChanged.class, () -> store.beginModel(run.id(), claim.ownerToken(), Map.of(), watermark));
        assertThrows(AgentRunStore.InputChanged.class, () -> store.beginNative(run.id(), claim.ownerToken(), "cli:old-input", "fixture-host", watermark));
        assertEquals(0, store.get(run.id()).stepCount()); assertNull(store.get(run.id()).externalHandle());
        int step = store.beginModel(run.id(), claim.ownerToken(), Map.of());
        store.endModel(run.id(), claim.ownerToken(), step, Map.of("succeeded", true));
        service.message(run.taskId(), new AgentMessageRequest(run.id(), "before-action", store.get(run.id()).version(), "先等我核对"), "owner");
        assertThrows(AgentRunStore.InputChanged.class, () -> store.finishDecision(run.id(), claim.ownerToken(), "step:1:request", "COMPLETED", "旧结论"));
        assertTrue(mapper.listArtifacts(run.id()).isEmpty()); assertEquals("RUNNING", store.get(run.id()).status());
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
    void savedNativeResultUsesSameArtifactAcceptanceGate() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT", "CHANGE_SET", "TEST_REPORT"));
        var claim = store.claim(4);
        store.beginNative(run.id(), claim.ownerToken(), "cli:acceptance", "fixture-host");
        store.endNative(run.id(), claim.ownerToken(), "cli:acceptance", nativeResult("RESULT", "声称代码测试全部完成"));
        assertTrue(store.finishSavedNative(run.id(), claim.ownerToken()));
        assertEquals("WAITING_INPUT", store.get(run.id()).status());
        var checked = mapper.findEventByKey(run.id(), "acceptance:" + claim.ownerToken());
        var items = json.readTree(checked.payloadJson()).path("items");
        assertEquals("SATISFIED", items.get(0).path("status").asText());
        assertNotEquals("SATISFIED", items.get(1).path("status").asText());
        assertEquals("BLOCKED", items.get(2).path("status").asText(), "未授予代码权限时不得验收测试产物");
        assertNotNull(mapper.findEventByKey(run.id(), "cli:acceptance:handled"));
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
        var specification = new AgentTaskSpecification("主动巡检", "", "返回证据", artifacts, List.of(testScope));
        var task = service.createTask(agent.id(), new AgentTaskCreateRequest(UUID.randomUUID().toString(), specification, manual(), true), "owner");
        return mapper.findRun(service.runs(task.id(), "owner", 1, 20).items().getFirst().id());
    }

    private AgentPlatformGuide platformGuide() {
        var configs = mock(cn.aslight.workhub.service.system.SysConfigService.class);
        when(configs.findPlainValue(AgentPlatformGuide.CONFIG_GROUP, AgentPlatformGuide.CONFIG_KEY)).thenReturn("指南测试标记GUIDE-X：工作台运行库目标与表目录。");
        return new AgentPlatformGuide(configs);
    }

    @Test
    void platformGuideInjectedIntoPromptWithFallbackWhenAbsent() {
        var agent = enabledAgent();
        var task = service.createTask(agent.id(), taskRequest("guide", true), "owner");
        var run = service.runs(task.id(), "owner", 1, 20).items().getFirst();
        var claim = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        List<String> prompts = new CopyOnWriteArrayList<>();
        when(gateway.executeAgentApi(any())).thenAnswer(invocation -> {
            prompts.add(((cn.aslight.workhub.service.ai.AiAgentApiRequest) invocation.getArgument(0)).prompt());
            return answer("{\"type\":\"RESULT\",\"plan\":\"完成\",\"summary\":\"报告\"}");
        });
        adapter(gateway).execute(run.id(), claim.ownerToken());
        assertTrue(prompts.getFirst().contains("平台数据指南"));
        assertTrue(prompts.getFirst().contains("GUIDE-X"));
        var configs = mock(cn.aslight.workhub.service.system.SysConfigService.class);
        when(configs.findPlainValue(AgentPlatformGuide.CONFIG_GROUP, AgentPlatformGuide.CONFIG_KEY)).thenReturn(null);
        var fallbackAdapter = new AgentApiExecutionAdapter(store, new AgentToolData(json), gateway, promptTemplates(),
                promptRenderer(), new AgentPlatformGuide(configs), json);
        var second = service.run(task.id(), new AgentRunRequest("guide-second", 0L), "owner");
        var claim2 = store.claim(4);
        fallbackAdapter.execute(second.id(), claim2.ownerToken());
        assertTrue(prompts.getLast().contains("未配置平台数据指南"));
    }

    private AgentApiExecutionAdapter adapter(AiGatewayClient gateway) {
        return new AgentApiExecutionAdapter(store, new AgentToolData(json), gateway, promptTemplates(), promptRenderer(), platformGuide(), json);
    }

    private cn.aslight.workhub.service.ai.AiUseCaseConfigService promptTemplates() {
        return cn.aslight.workhub.service.ai.TestPromptTemplates.stubEnabledConfigService();
    }

    private cn.aslight.workhub.service.ai.AiPromptTemplateRenderer promptRenderer() {
        return new cn.aslight.workhub.service.ai.AiPromptTemplateRenderer(json);
    }

    @Test
    void nativeCliUsesWholeTaskAndRealProcessWithPlatformMcpWithoutModelStepProtocol() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT"));
        var claim = store.claim(4);
        nativeAdapter("normal").execute(run.id(), claim.ownerToken());
        assertEquals("COMPLETED", store.get(run.id()).status());
        assertNull(store.get(run.id()).externalHandle());
        assertTrue(store.events(run.id()).stream().anyMatch(event -> "CLI_STARTED".equals(event.kind())));
        assertFalse(store.events(run.id()).stream().anyMatch(event -> "MODEL_REQUEST".equals(event.kind())));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "workhub.test.zcode.command", matches = ".+")
    void installedZcodeCompletesTaskWithPlatformMcpAllowlist(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
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
                    assertTrue(request.path("messages").toString().contains("主动巡检"));
                    var delta = Map.of("role", "assistant", "content", "{\"type\":\"RESULT\",\"summary\":\"按职责描述整理的巡检报告\"}");
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    for (var chunk : List.of(Map.of("index", 0, "delta", delta), Map.of("index", 0, "delta", Map.of(), "finish_reason", "stop"))) {
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
            var specification = new AgentTaskSpecification("主动巡检", "", "返回证据", List.of("REPORT"), List.of(testScope));
            var task = service.createTask(agent.id(), new AgentTaskCreateRequest("zcode-actual", specification, manual(), true), "owner");
            var claim = store.claim(4);
            var runtime = mock(cn.aslight.workhub.service.mcp.McpRuntimeService.class);
            when(runtime.toolNames()).thenReturn(List.of("list_mcp_targets", "run_readonly_query"));
            var properties = new cn.aslight.workhub.config.McpProperties();
            properties.setAccessToken("test-mcp-token");
            var adapter = new AgentNativeCliAdapter(store, new AgentToolData(json), providers,
                    promptTemplates(), promptRenderer(), platformGuide(),
                    List.of(new ZCodeAgentCliProtocol(new cn.aslight.workhub.service.ai.ZCodeCliClient(json), runtime)),
                    json, properties, "", "8080", mcpStubUrl());
            adapter.execute(claim.id(), claim.ownerToken());
            assertNull(failure.get(), () -> "本机模型桩失败：" + failure.get());
            var detail = service.runDetail(task.id(), claim.id(), "owner");
            assertEquals("COMPLETED", detail.run().status());
            assertEquals(1, requests.size());
            assertNull(store.get(claim.id()).externalHandle());
            assertEquals(1, mapper.listArtifacts(claim.id()).size());
            assertTrue(mapper.listArtifacts(claim.id()).getFirst().payloadJson().contains("按职责描述整理的巡检报告"));
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
            assertEquals(2, attempts.get()); assertEquals("COMPLETED", store.get(run.id()).status());
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
        assertEquals("COMPLETED", store.get(run.id()).status());
        assertEquals(2, store.events(run.id()).stream().filter(event -> "CLI_REQUEST".equals(event.kind())).count());
    }

    private String mcpStubUrl() {
        return "http://127.0.0.1:" + mcpStub.getAddress().getPort() + "/api/mcp/runtime";
    }

    private AgentNativeCliAdapter nativeAdapter(String mode) {
        return nativeAdapter(() -> mode);
    }

    private AgentNativeCliAdapter nativeAdapter(java.util.function.Supplier<String> mode) {
        return nativeAdapter(new AgentCliProtocol() {
            public boolean supports(AiProviderConfigEntity provider) { return true; }
            public void preflight(AiProviderConfigEntity provider) { }
            public cn.aslight.workhub.service.ai.CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, Path directory, Path schema, Path output,
                    String mcpUrl, String token, String prompt) {
                var builder = new ProcessBuilder(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                        FakeAgentCliProcess.class.getName(), mcpUrl == null ? "none" : mcpUrl, output.toString(), mode.get()));
                if (token != null) builder.environment().put("WORKHUB_AGENT_RUN_TOKEN", token);
                return new cn.aslight.workhub.service.ai.CliTaskLaunch(builder, new cn.aslight.workhub.service.ai.CliTaskConversation() {
                    public void start(java.io.OutputStream input) throws java.io.IOException { try (input) { input.write(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } }
                    public String accept(tools.jackson.databind.JsonNode event) { return null; }
                });
            }
        });
    }

    private AgentNativeCliAdapter nativeAdapter(AgentCliProtocol protocol) {
        var properties = new cn.aslight.workhub.config.McpProperties();
        properties.setAccessToken("test-mcp-token");
        return new AgentNativeCliAdapter(store, new AgentToolData(json), providers, promptTemplates(), promptRenderer(), platformGuide(),
                List.of(protocol), json, properties, "", "8080", mcpStubUrl());
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
    @Test
    void manualReconciliationReplansWithoutReplayingUnknownApiToolAndKeepsBudgets() {
        var run = unknownTool();
        jdbc.update("UPDATE pm_agent_run SET elapsed_millis=13000 WHERE id=?", run.id());
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertTrue(view.canReconcile());
        assertEquals("monitor.read", view.calls().getFirst().toolCode());
        assertTrue(view.calls().getFirst().requestPayloadJson().contains("monitor.read"));
        var result = reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "replan", "RESUME"), "owner");
        assertEquals("QUEUED", result.status());
        assertEquals(1, store.get(run.id()).stepCount());
        assertEquals(13000, store.get(run.id()).elapsedMillis());
        assertNull(store.event(run.id(), "step:1:tool:result"));
        assertTrue(mapper.listArtifacts(run.id()).isEmpty());
        assertEquals("MANUAL_RECONCILIATION_HANDLED", store.event(run.id(), "step:1:handled").kind());
        var next = store.claim(4);
        var gateway = mock(AiGatewayClient.class);
        when(gateway.executeAgentApi(any())).thenAnswer(invocation -> {
            String prompt = ((cn.aslight.workhub.service.ai.AiAgentApiRequest) invocation.getArgument(0)).prompt();
            assertTrue(prompt.contains("MANUAL_RECONCILIATION"));
            assertTrue(prompt.contains("核对日志及原调用记录"));
            return answer("{\"type\":\"RESULT\",\"plan\":\"按人工补充重新分析\",\"summary\":\"已重新分析，未宣称实测完成\"}");
        });
        adapter(gateway).execute(run.id(), next.ownerToken());
        verify(gateway, times(1)).executeAgentApi(any());
        assertEquals(2, store.get(run.id()).stepCount());
        assertEquals("COMPLETED", store.get(run.id()).status());
    }

    @Test
    void manualApiResolutionCreatesNoMachineReceiptAndCanWaitForInput() {
        var run = unknownApi();
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        var result = reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "waiting", "WAIT_INPUT"), "owner");
        assertEquals("WAITING_INPUT", result.status());
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        assertNull(store.get(run.id()).externalHandle());
        assertNull(store.event(run.id(), "step:1:response"));
        assertTrue(mapper.listArtifacts(run.id()).isEmpty());
        assertEquals("QUEUED", service.message(run.taskId(), new AgentMessageRequest(run.id(), "resume-after-check",
                result.version(), "继续完成报告"), "owner").status());
    }

    @Test
    void manualReconciliationRefusesActiveLeaseWrongTaskAndUnauthorizedActor() {
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginModel(run.id(), old.ownerToken(), Map.of());
        var live = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertFalse(live.canReconcile());
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(live, "live", "CANCEL"), "owner"));
        expire(run.id()); store.recoverExpired(run.id());
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertThrows(IllegalArgumentException.class, () -> reconciliation.preview("foreign-task", run.id(), "owner"));
        assertThrows(AccessDeniedException.class, () -> reconciliation.preview(run.taskId(), run.id(), "other"));
        assertThrows(AccessDeniedException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "forbidden", "CANCEL"), "other"));
        grants.put(2L, List.of("agent:manage", "agent:task:view"));
        assertFalse(reconciliation.preview(run.taskId(), run.id(), "other").canReconcile());
        assertThrows(AccessDeniedException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "no-operate", "CANCEL"), "other"));
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
    }

    @Test
    void manualReconciliationUsesExactVersionAndReceiptSnapshot() {
        var run = unknownApi(); var old = store.get(run.id());
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        store.endModel(run.id(), old.ownerToken(), 1, Map.of("succeeded", false, "error", "actual response"));
        assertEquals(view.version(), store.get(run.id()).version());
        var refreshed = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertNotEquals(view.snapshotHash(), refreshed.snapshotHash());
        assertFalse(refreshed.canReconcile());
        assertEquals("MODEL_RESPONSE", refreshed.calls().getFirst().receiptSource());
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "stale-receipt", "CANCEL"), "owner"));
        assertNotNull(store.get(run.id()).externalHandle());
        store.recoverExpired(run.id());
        assertEquals("QUEUED", store.get(run.id()).status());
        assertNull(store.event(run.id(), "reconcile:stale-receipt"));
    }

    @Test
    void newInputInvalidatesManualSnapshotWithoutDroppingTheOriginalCall() {
        var run = unknownApi(); var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        service.message(run.taskId(), new AgentMessageRequest(run.id(), "new-fact", view.version(), "发现新证据"), "owner");
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "stale-version", "CANCEL"), "owner"));
        assertNotNull(store.get(run.id()).externalHandle());
    }

    @Test
    void concurrentManualRequestsCommitOnceAndReturnOriginalResultAfterRunChanges() throws Exception {
        var run = unknownApi(); var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        var request = resolution(view, "same-resolution", "RESUME");
        AgentRunReconciliationResult result;
        try (var pool = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<AgentRunReconciliationResult>>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> reconciliation.reconcile(run.taskId(), run.id(), request, "owner")));
            result = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (var future : futures) assertEquals(result, future.get(5, TimeUnit.SECONDS));
        }
        store.claim(4);
        assertEquals(result, reconciliation.reconcile(run.taskId(), run.id(), request, "owner"));
        assertEquals(1, store.events(run.id()).stream().filter(event -> "MANUAL_RECONCILIATION".equals(event.kind())).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_audit WHERE action='MANUAL_RECONCILIATION'", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "same-resolution", "CANCEL"), "owner"));
        grants.put(2L, grants.get(1L));
        assertThrows(IllegalArgumentException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), request, "other"));
    }

    @Test
    void competingManualDecisionsCannotBothReleaseTheSameCall() throws Exception {
        var run = unknownApi(); var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "first", "CANCEL"), "owner"));
            var b = pool.submit(() -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "second", "RESUME"), "owner"));
            int succeeded = 0;
            for (var future : List.of(a, b)) {
                try { future.get(5, TimeUnit.SECONDS); succeeded++; }
                catch (ExecutionException ex) { assertInstanceOf(IllegalStateException.class, ex.getCause()); }
            }
            assertEquals(1, succeeded);
        }
        assertEquals(1, store.events(run.id()).stream().filter(event -> "MANUAL_RECONCILIATION".equals(event.kind())).count());
    }

    @Test
    void cancellationCannotBeUndoneByManualReconciliation() {
        var run = unknownApi();
        service.cancel(run.taskId(), run.id(), store.get(run.id()).version(), "owner");
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        for (String action : List.of("RESUME", "WAIT_INPUT")) {
            assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, action, action), "owner"));
        }
        assertEquals("CANCELLED", reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "cancel-final", "CANCEL"), "owner").status());
        assertTrue(store.get(run.id()).cancelRequested());
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        assertNull(store.claim(4));
    }

    @Test
    void resumeRequiresCurrentAuthorizationProviderScopesAndBudgetButCancellationStillWorks() {
        var run = unknownTool();
        grants.put(1L, List.of("agent:view", "agent:manage", "agent:task:view", "agent:task:operate", "agent:task:assign"));
        var provider = providers.getEnabledById(1L);
        provider.setChannelType("CLI");
        var changed = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(changed, "provider-change", "RESUME"), "owner"));
        provider.setChannelType("API");
        var agent = service.detail(run.agentId(), "owner");
        service.update(agent.id(), new AgentSaveRequest(agent.version(), new AgentConfiguration("变更范围", "巡检", 1L,
                "first-model", List.of(), 20, 60, 2)), "owner");
        var scopeChanged = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertThrows(AccessDeniedException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(scopeChanged, "scope-change", "RESUME"), "owner"));
        agent = service.detail(run.agentId(), "owner");
        service.update(agent.id(), new AgentSaveRequest(agent.version(), config("恢复范围", "first-model")), "owner");
        jdbc.update("UPDATE pm_agent_run SET elapsed_millis=60000 WHERE id=?", run.id());
        var budget = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(budget, "spent", "RESUME"), "owner"));
        assertEquals("CANCELLED", reconciliation.reconcile(run.taskId(), run.id(), resolution(budget, "end-spent", "CANCEL"), "owner").status());
        assertEquals(60000, store.get(run.id()).elapsedMillis());
    }

    @Test
    void cliRequiresExactChecksAndCannotClaimAStartedCliWasNeverStarted() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginNative(run.id(), old.ownerToken(), "cli:manual", "fixture-host");
        store.nativeStarted(run.id(), old.ownerToken(), "cli:manual", 789, "2026-11-03T17:59:50Z");
        expire(run.id()); store.recoverExpired(run.id());
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertEquals(1, view.calls().size());
        assertEquals("2026-11-03T17:59:50Z", view.calls().getFirst().startInstant());
        verify(processes).observe("fixture-host", 789L, "2026-11-03T17:59:50Z");
        var request = resolution(view, "cli-complete", "WAIT_INPUT");
        var falseStart = new AgentRunReconciliationRequest("cli-not-started", view.version(), view.snapshotHash(), request.nextAction(), List.of(
                new AgentRunReconciliationRequest.Decision("cli:manual", AgentRunReconciliationRequest.Outcome.NOT_STARTED, "仅看到父进程已消失")));
        assertThrows(IllegalArgumentException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), falseStart, "owner"));
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        reconciliation.reconcile(run.taskId(), run.id(), request, "owner");
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
        assertNull(store.event(run.id(), "cli:manual:result"));
        assertNotNull(store.event(run.id(), "cli:manual:handled"));
        assertTrue(mapper.listArtifacts(run.id()).isEmpty());
    }

    @Test
    void liveCliBlocksReconciliationEvenWhenLeaseExpiredAndFormWasAlreadyRead() {
        providers.getEnabledById(1L).setChannelType("CLI");
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginNative(run.id(), old.ownerToken(), "cli:alive", "fixture-host");
        expire(run.id()); store.recoverExpired(run.id());
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        for (String state : List.of("RUNNING", "LIVE_IDENTITY_UNAVAILABLE")) {
            when(processes.observe(any(), any(), any())).thenReturn(state);
            assertFalse(reconciliation.preview(run.taskId(), run.id(), "owner").canReconcile());
            assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(view, state, "CANCEL"), "owner"));
        }
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
    }

    @Test
    void committedInspectionResultOverridesManualClaimAndMissingRecordIsNotStopProof() {
        var run = unknownTool();
        jdbc.execute("CREATE TABLE pm_agent_inspection_call(run_id VARCHAR(36),tool_key VARCHAR(160),result_json TEXT)");
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", "{\"kind\":\"TOOL\",\"key\":\"step:1:tool\",\"tool\":\"inspection.ack\",\"readOnly\":false}", run.id());
        jdbc.update("UPDATE pm_agent_event SET payload_json=? WHERE run_id=? AND event_key='step:1:tool:request'", "{\"tool\":\"inspection.ack\",\"arguments\":{}}", run.id());
        var missing = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertTrue(missing.canReconcile());
        jdbc.update("INSERT INTO pm_agent_inspection_call VALUES(?,?,?)", run.id(), "step:1:tool", "{\"state\":\"FINDING\"}");
        var actual = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertFalse(actual.canReconcile());
        assertEquals("COMMITTED_INSPECTION_TRANSACTION", actual.calls().getFirst().receiptSource());
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(missing, "late-commit", "CANCEL"), "owner"));
        store.recoverExpired(run.id());
        assertNotNull(store.event(run.id(), "step:1:tool:result"));
        assertEquals("QUEUED", store.get(run.id()).status());
    }

    @Test
    void committedVerificationReceiptAlsoPreventsManualOverwrite() {
        var run = unknownTool();
        jdbc.execute("CREATE TABLE pm_agent_verification_call(run_id VARCHAR(36),tool_key VARCHAR(180),tool_code VARCHAR(80),result_json TEXT)");
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?", json.writeValueAsString(Map.of("kind", "TOOL", "key", "step:1:tool", "tool", "verification.record", "readOnly", false)), run.id());
        jdbc.update("UPDATE pm_agent_event SET payload_json=? WHERE run_id=? AND event_key='step:1:tool:request'", json.writeValueAsString(Map.of("tool", "verification.record", "arguments", Map.of())), run.id());
        var missing = reconciliation.preview(run.taskId(), run.id(), "owner"); assertTrue(missing.canReconcile());
        jdbc.update("INSERT INTO pm_agent_verification_call VALUES(?,?,?,?)", run.id(), "step:1:tool", "verification.record", "{\"status\":\"OBSERVING\"}");
        var actual = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertFalse(actual.canReconcile()); assertEquals("COMMITTED_VERIFICATION_TRANSACTION", actual.calls().getFirst().receiptSource());
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(missing, "late-verification", "CANCEL"), "owner"));
        store.recoverExpired(run.id()); assertNotNull(store.event(run.id(), "step:1:tool:result")); assertEquals("QUEUED", store.get(run.id()).status());
    }

    @Test
    void manualAuditFailureRollsBackReleaseAndAllEvents() {
        var run = unknownApi(); var before = store.get(run.id()); var count = store.events(run.id()).size();
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        jdbc.execute("ALTER TABLE pm_agent_audit ADD CONSTRAINT fail_manual CHECK(action <> 'MANUAL_RECONCILIATION')");
        var request = resolution(view, "rollback", "CANCEL");
        assertThrows(RuntimeException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), request, "owner"));
        assertEquals(before, store.get(run.id()));
        assertEquals(count, store.events(run.id()).size());
        assertNull(store.event(run.id(), "step:1:handled"));
        jdbc.execute("ALTER TABLE pm_agent_audit DROP CONSTRAINT fail_manual");
        assertEquals("CANCELLED", reconciliation.reconcile(run.taskId(), run.id(), request, "owner").status());
    }

    @Test
    void manualEvidenceIsRequiredAndSensitiveValuesAreMaskedBeforeEventsAndAudit() {
        var run = unknownApi(); var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        var request = resolution(view, "masked", "CANCEL");
        var blank = new AgentRunReconciliationRequest("blank", view.version(), view.snapshotHash(), request.nextAction(), List.of(
                new AgentRunReconciliationRequest.Decision("step:1", AgentRunReconciliationRequest.Outcome.ENDED, "   ")));
        assertThrows(IllegalArgumentException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), blank, "owner"));
        var sensitive = new AgentRunReconciliationRequest("masked", view.version(), view.snapshotHash(), request.nextAction(), List.of(
                new AgentRunReconciliationRequest.Decision("step:1", AgentRunReconciliationRequest.Outcome.ENDED, "核对日志，password=secret-test-value，调用已结束")));
        reconciliation.reconcile(run.taskId(), run.id(), sensitive, "owner");
        assertFalse(store.event(run.id(), "reconcile:masked").payloadJson().contains("secret-test-value"));
        assertFalse(jdbc.queryForObject("SELECT payload_json FROM pm_agent_audit WHERE action='MANUAL_RECONCILIATION'", String.class).contains("secret-test-value"));
        assertEquals("owner", store.event(run.id(), "reconcile:masked").actor());
    }

    @Test
    void realReceiptRacingManualDecisionHasExactlyOneWinner() throws Exception {
        var run = unknownApi(); var owner = store.get(run.id()).ownerToken();
        var view = reconciliation.preview(run.taskId(), run.id(), "owner");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var human = pool.submit(() -> { gate.await(); return reconciliation.reconcile(run.taskId(), run.id(), resolution(view, "race-receipt", "CANCEL"), "owner"); });
            var machine = pool.submit(() -> { gate.await(); store.endModel(run.id(), owner, 1, Map.of("succeeded", false, "error", "original receipt")); return true; });
            gate.countDown();
            int succeeded = 0;
            for (var future : List.of(human, machine)) {
                try { future.get(5, TimeUnit.SECONDS); succeeded++; }
                catch (ExecutionException ex) { assertInstanceOf(IllegalStateException.class, ex.getCause()); }
            }
            assertEquals(1, succeeded);
        }
        boolean machineWon = store.event(run.id(), "step:1:response") != null;
        assertEquals(!machineWon, store.event(run.id(), "reconcile:race-receipt") != null);
        if (machineWon) { assertNotNull(store.get(run.id()).externalHandle()); store.recoverExpired(run.id()); }
        assertNull(store.get(run.id()).externalHandle());
        assertTrue(mapper.listArtifacts(run.id()).isEmpty());
    }

    @Test
    void disabledAgentAndSpentStepBudgetCannotBeResumedByReconciliation() {
        var disabledRun = unknownApi(); var agent = service.detail(disabledRun.agentId(), "owner");
        service.setEnabled(agent.id(), agent.version(), false, "owner");
        var disabled = reconciliation.preview(disabledRun.taskId(), disabledRun.id(), "owner");
        assertTrue(disabled.canReconcile());
        assertTrue(disabled.cancelRequested());
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(disabledRun.taskId(), disabledRun.id(), resolution(disabled, "disabled-agent", "RESUME"), "owner"));
        assertEquals("CANCELLED", reconciliation.reconcile(disabledRun.taskId(), disabledRun.id(), resolution(disabled, "stop-disabled", "CANCEL"), "owner").status());
        var run = unknownApi();
        var frozen = json.readTree(store.get(run.id()).configurationJson());
        ((tools.jackson.databind.node.ObjectNode) frozen).put("maxSteps", 1);
        jdbc.update("UPDATE pm_agent_run SET configuration_json=? WHERE id=?", json.writeValueAsString(frozen), run.id());
        var spent = reconciliation.preview(run.taskId(), run.id(), "owner");
        assertThrows(IllegalStateException.class, () -> reconciliation.reconcile(run.taskId(), run.id(), resolution(spent, "spent-steps", "RESUME"), "owner"));
        assertEquals("WAITING_INPUT", reconciliation.reconcile(run.taskId(), run.id(), resolution(spent, "wait-spent-steps", "WAIT_INPUT"), "owner").status());
        assertEquals(1, store.get(run.id()).stepCount());
        assertEquals(0, mapper.countOwnedRuns(null, LocalDateTime.now(clock)));
    }

    private AgentRunEntity unknownApi() {
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        store.beginModel(run.id(), old.ownerToken(), Map.of());
        expire(run.id()); store.recoverExpired(run.id());
        return store.get(run.id());
    }

    private AgentRunEntity unknownTool() {
        var run = inspectionRun(List.of("REPORT")); var old = store.claim(4);
        int step = store.beginModel(run.id(), old.ownerToken(), Map.of());
        store.endModel(run.id(), old.ownerToken(), step, Map.of("succeeded", true));
        // 历史运行可能仍持有工具调用句柄；对账按原样识别 TOOL 句柄而不重放调用。
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id=?",
                json.writeValueAsString(Map.of("kind", "TOOL", "key", "step:1:tool", "tool", "monitor.read", "readOnly", true)), run.id());
        mapper.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), run.id(), "step:1:tool:request", mapper.nextEventOrdinal(run.id()),
                "TOOL_REQUEST", json.writeValueAsString(Map.of("tool", "monitor.read", "arguments", Map.of())), "agent-runtime",
                LocalDateTime.now(clock)));
        expire(run.id()); store.recoverExpired(run.id());
        return store.get(run.id());
    }

    private AgentRunReconciliationRequest resolution(AgentRunReconciliationView view, String key, String action) {
        return new AgentRunReconciliationRequest(key, view.version(), view.snapshotHash(), AgentRunReconciliationRequest.NextAction.valueOf(action),
                view.calls().stream().filter(AgentRunReconciliationView.Call::needsConfirmation)
                        .map(call -> new AgentRunReconciliationRequest.Decision(call.key(), AgentRunReconciliationRequest.Outcome.ENDED,
                                "核对日志及原调用记录，已确认执行结束、无遗留子进程；领域发布状态另行核实")).toList());
    }

    private void expire(String runId) { jdbc.update("UPDATE pm_agent_run SET lease_until='2026-11-03 17:59:00' WHERE id=?", runId); }

    private AgentConfiguration config(String name, String model) {
        return new AgentConfiguration(name, "按分派目标执行", 1L, model, List.of(testScope), 20, 60, 2);
    }

    private AgentTaskCreateRequest taskRequest(String key, boolean immediate) {
        return new AgentTaskCreateRequest(key, new AgentTaskSpecification("整理我提供的资料", "输入文本", "返回分析报告", List.of("REPORT"), List.of()), manual(), immediate);
    }

    private AgentScheduleRequest manual() { return new AgentScheduleRequest(null, "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false); }
}
