package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.system.SysConfigService;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 生产迁移、Mapper、事务、调度器和凭据核验；Job 与代码交付为夹具，不访问真实 Jenkins。 */
class AgentBuildTestDispatcherTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock();
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final String manifestHash = "a".repeat(64), sha = "b".repeat(40);
    private final AtomicReference<String> policyText = new AtomicReference<>();
    private final AtomicReference<AgentChangeView> current = new AtomicReference<>();
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private AgentMapper agents;
    private AgentBuildTestMapper tests;
    private AgentDeploymentMapper resources;
    private AgentBuildTestStore store;
    private AgentBuildTestDispatcher dispatcher;
    private AgentBuildTestEvidence evidence;
    private AgentBuildTestPolicy policy;
    private AgentJenkinsPolicy jenkins;
    private SysConfigService configs;
    private AgentJenkinsClient client;
    private AgentAccessService access;
    private AgentRunStore runs;
    private AgentChangeService changes;
    private AgentToolContext context;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = source.getConnection(); jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_054000__add_agent_deployment_outbox.sql", "V20261104_203039__add_agent_build_test_outbox.sql"))
            ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + file));
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class); configuration.addMapper(AgentBuildTestMapper.class); configuration.addMapper(AgentDeploymentMapper.class);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject()); agents = sessions.getMapper(AgentMapper.class);
        tests = sessions.getMapper(AgentBuildTestMapper.class); resources = sessions.getMapper(AgentDeploymentMapper.class);
        configs = mock(SysConfigService.class);
        when(configs.findPlainValue("agent.jenkins", "policy")).thenAnswer(invocation -> policyText.get());
        when(configs.requireSecretValue("jenkins", "token")).thenReturn("fixture-secret");
        jenkins = new AgentJenkinsPolicy(configs, json); configure("https://jenkins.example.invalid/", List.of(job("test", scope, Map.of("server", "SERVER_SHA"))));
        policy = new AgentBuildTestPolicy(jenkins); evidence = new AgentBuildTestEvidence(tests, agents, policy, jenkins, json, clock);
        var task = new AgentTaskSpecification("修复问题", "", "实际测试", List.of("TEST_REPORT"), List.of(scope));
        var config = new AgentConfiguration("Agent","OPS", "repair", 1L, "fixture", task.scopes(), 50, 600, 1);
        context = new AgentToolContext("run", "task", "agent", "owner", "runtime-owner", config, task);
        agents.insertRun(new AgentRunEntity("run", "task", "agent", "manual", "owner", json.writeValueAsString(config), json.writeValueAsString(task), "{}",
                "WAITING_INPUT", 1, 0, false, null, null, null, null, null, 0, now(), now()));
        runs = mock(AgentRunStore.class); when(runs.get("run")).thenAnswer(invocation -> agents.findRun("run")); when(runs.context(any())).thenReturn(context);
        access = mock(AgentAccessService.class);
        doAnswer(invocation -> { if (!task.scopes().contains(invocation.getArgument(1))) throw new AccessDeniedException("scope"); return null; }).when(access).requireScope(any(), any());
        var repo = new AgentRepositoryChange("server", "Server", scope, "workspace", 1, "p".repeat(64), "development", "c".repeat(40), "candidate", sha, List.of("src/Main.java"), "d".repeat(64), "diff", false);
        var manifest = new AgentChangeManifest(1, "run", "task", task, new AgentChangePlan("fix", "API", "regression", "restore", List.of()), null, List.of(repo), List.of("server"));
        current.set(new AgentChangeView("change", "run", "task", 1, manifestHash, now(), true, false, List.of("PLATFORM_TEST_EVIDENCE_MISSING"), manifest));
        changes = mock(AgentChangeService.class);
        when(changes.detail(any(), eq("change"))).thenAnswer(invocation -> current.get());
        when(changes.detailForUser(eq("change"), anyString())).thenAnswer(invocation -> current.get());
        store = store(agents, tests);
        client = mock(AgentJenkinsClient.class);
        when(client.probe(any())).thenAnswer(invocation -> {
            outsideTransaction(); var target = (AgentJenkinsClient.Target) invocation.getArgument(0);
            return new AgentJenkinsClient.Probe(String.join("/", target.jobSegments()), true, jenkins.requiredParameters(jenkins.binding("test").binding()));
        });
        when(client.submit(any(), any())).thenAnswer(invocation -> {
            outsideTransaction(); Map<String, String> params = invocation.getArgument(1);
            assertEquals("SUBMITTING", tests.find(params.get("REQUEST_ID")).status()); return 11L;
        });
        dispatcher = dispatcher(client);
    }
    @AfterEach void close() throws Exception { keepAlive.close(); }

    @Test void actualTestReceiptUnlocksEvidenceAndWakesOriginalRunWithoutResettingBudget() {
        var row = start("request", "test");
        assertEquals(List.of(row.id()), dispatcher.due()); assertFalse(evaluate().satisfied()); verifyNoInteractions(client);
        assertEquals("QUEUED", dispatcher.advance(row.id()).status()); queueStarts(); tick();
        assertEquals("BUILDING", dispatcher.advance(row.id()).status()); successBuild(); tick(); report(row, "tests", 7);
        assertEquals("SUCCEEDED", dispatcher.advance(row.id()).status()); assertEquals(0, locks());
        var result = evaluate(); assertTrue(result.satisfied()); assertEquals(1, result.artifacts().size());
        assertEquals("PLATFORM_EXECUTION", result.artifacts().getFirst().provenance());
        jdbc.update("UPDATE pm_agent_run SET external_handle=NULL,step_count=8,elapsed_millis=1234 WHERE id='run'");
        assertEquals(List.of(row.id()), dispatcher.wakeDue()); store.wake(row.id()); store.wake(row.id());
        assertEquals("QUEUED", agents.findRun("run").status()); assertEquals(8, agents.findRun("run").stepCount()); assertEquals(1234, agents.findRun("run").elapsedMillis());
        assertFalse(tests.find(row.id()).wakePending()); assertEquals(1, agents.listEvents("run").stream().filter(event -> "BUILD_TEST_WAKE".equals(event.kind())).count());
        assertEquals("SUCCEEDED", dispatcher.advance(row.id()).status()); verify(client, times(1)).submit(any(), any());
    }
    @Test void oneRequestIsJournaledAndIdempotentAcrossToolCallsWithoutPost() {
        var row = start("request", "test");
        assertEquals(row.id(), store.start(context, "change", "test", "request").path("id").asText());
        tool("second"); assertEquals(row.id(), store.start(context, "change", "test", "request").path("id").asText());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_build_test_call", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_build_test", Integer.class));
        assertEquals(tests.call("run", "second").resultJson(), agents.findBuildTestCallResult("run", "second", AgentBuildTestStore.START));
        assertNull(agents.findBuildTestCallResult("run", "second", "other.tool"));
        assertThrows(IllegalArgumentException.class, () -> store.start(context, "change", "test", "other")); verifyNoInteractions(client);
    }
    @Test void startingRequiresRealToolHandleAndRemoteDelivery() {
        assertThrows(IllegalStateException.class, () -> store.start(context, "change", "test", "request"));
        tool("first"); var old = current.get();
        current.set(new AgentChangeView(old.id(), old.runId(), old.taskId(), old.revision(), old.manifestHash(), old.createdAt(), true, false, List.of("REMOTE_DELIVERY_EVIDENCE_MISSING"), old.manifest()));
        assertThrows(IllegalArgumentException.class, () -> store.start(context, "change", "test", "request")); assertEquals(0, locks());
    }
    @Test void cliActiveToolUsesSameDurableStartLedger() {
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id='run'", json.writeValueAsString(Map.of("kind", "CLI", "activeTool", Map.of("kind", "TOOL", "key", "cli:1", "tool", AgentBuildTestStore.START))));
        var result = store.start(context, "change", "test", "request"); assertNotNull(tests.find(result.path("id").asText())); assertNotNull(tests.call("run", "cli:1"));
    }
    @Test void journalOrCallFailureRollsBackRequestResourcesAndEvidence() {
        var brokenAgents = spy(agents); doThrow(new IllegalStateException("journal failed")).when(brokenAgents).insertEvent(any());
        tool("first"); assertThrows(IllegalStateException.class, () -> store(brokenAgents, tests).start(context, "change", "test", "request"));
        assertNull(tests.byKey("run", "request")); assertEquals(0, locks());
        var brokenTests = spy(tests); doThrow(new IllegalStateException("call failed")).when(brokenTests).insertCall(any());
        assertThrows(IllegalStateException.class, () -> store(agents, brokenTests).start(context, "change", "test", "request"));
        assertNull(tests.byKey("run", "request")); assertEquals(0, locks()); assertTrue(agents.listEvents("run").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"zero", "skipped", "buildExit", "testExit", "failures", "errors"})
    void nominalJenkinsSuccessCannotReplaceExecutedPassingTests(String kind) {
        var row = building(); successBuild(); tick(); var value = receipt(row.id());
        switch (kind) {
            case "zero" -> value.put("tests", 0);
            case "skipped" -> value.put("skipped", 7);
            case "buildExit" -> value.put("buildExitCode", 1);
            case "testExit" -> value.put("testExitCode", 1);
            case "failures" -> value.put("failures", 1);
            case "errors" -> value.put("errors", 1);
        }
        when(client.receipt(any(), eq(22L), anyString())).thenReturn(value);
        assertEquals("FAILED", dispatcher.advance(row.id()).status()); assertFalse(evaluate().satisfied());
        assertNotNull(agents.findArtifact("run", "test:" + row.id())); assertEquals(0, locks());
    }
    @ParameterizedTest @ValueSource(strings = {"commit", "scope", "manifest", "request", "job", "build", "overflow", "counts", "future", "old", "string"})
    void invalidFinishedReceiptCannotProducePlatformProof(String kind) {
        var row = building(); successBuild(); tick(); var value = receipt(row.id());
        switch (kind) {
            case "commit" -> value.putObject("testedCommits").put("server", "e".repeat(40));
            case "scope" -> value.putObject("scope").put("businessLineCode", "BL000012").put("environmentCode", "PROD");
            case "manifest" -> value.put("manifestHash", "e".repeat(64));
            case "request" -> value.put("requestId", "different");
            case "job" -> value.put("jobFullName", "another/job");
            case "build" -> value.put("buildNumber", 23);
            case "overflow" -> value.put("testExitCode", new java.math.BigInteger("18446744073709551616"));
            case "counts" -> value.put("failures", 8);
            case "future" -> value.put("finishedAt", clock.instant().plusSeconds(1).toString());
            case "old" -> value.put("startedAt", row.createdAt().toInstant(ZoneOffset.UTC).minusSeconds(121).toString());
            case "string" -> value.put("tests", "7");
        }
        when(client.receipt(any(), eq(22L), anyString())).thenReturn(value);
        assertEquals("FAILED", dispatcher.advance(row.id()).status()); assertNull(agents.findArtifact("run", "test:" + row.id())); assertFalse(evaluate().satisfied());
    }
    @Test void missingReportWaitsAndModelReportNeverSatisfiesTestGate() {
        var row = building(); successBuild(); tick();
        agents.insertArtifact(new AgentArtifactEntity("model", "run", "model", "TEST_REPORT", "AI_REPORT", "{}", AgentCodePolicy.hash("{}"), now()));
        assertEquals("AWAITING_REPORT", dispatcher.advance(row.id()).status()); assertFalse(evaluate().satisfied()); assertTrue(locks() > 0);
        tick(); report(row, "tests", 7); assertEquals("SUCCEEDED", dispatcher.advance(row.id()).status()); assertTrue(evaluate().satisfied());
        jdbc.update("UPDATE pm_agent_artifact SET provenance='AI_REPORT' WHERE artifact_key=?", "test:" + row.id());
        assertFalse(evaluate().satisfied());
    }
    @Test void failedJenkinsResultIsNotUpgradedByPassingReceipt() {
        var row = building(); tick(); when(client.build(any(), eq(22L), any())).thenReturn(new AgentJenkinsClient.BuildState("FINISHED", 22, "FAILURE", true, 11L));
        report(row, "tests", 7); assertEquals("FAILED", dispatcher.advance(row.id()).status()); assertFalse(evaluate().satisfied());
    }
    @Test void lostPostResponseSearchesOriginalWithoutResubmissionOrReleasingLocks() {
        var row = start("request", "test"); doThrow(new AgentJenkinsClient.SubmissionUnknown("lost", null)).when(client).submit(any(), any());
        assertEquals("UNKNOWN", dispatcher.advance(row.id()).status()); tool("other");
        assertThrows(IllegalStateException.class, () -> store.start(context, "change", "test", "new-request"));
        when(client.find(any(), anyString(), eq(row.id()), any())).thenReturn(new AgentJenkinsClient.SearchResult("NOT_FOUND", List.of(), List.of(), 100));
        clock.advance(61); assertEquals("UNKNOWN", dispatcher(client).advance(row.id()).status()); assertTrue(locks() > 0);
        when(client.find(any(), anyString(), eq(row.id()), any())).thenReturn(new AgentJenkinsClient.SearchResult("FOUND", List.of(11L), List.of(22L), 100));
        clock.advance(61); assertEquals("BUILDING", dispatcher(client).advance(row.id()).status());
        successBuild(); tick(); report(row, "tests", 7); assertEquals("SUCCEEDED", dispatcher.advance(row.id()).status()); verify(client, times(1)).submit(any(), any());
    }
    @Test void processCrashAfterIntentRecoversOnlyOriginalRequest() {
        var row = start("request", "test"); doThrow(new AssertionError("simulated crash after intent")).when(client).submit(any(), any());
        assertThrows(AssertionError.class, () -> dispatcher.advance(row.id())); assertEquals("SUBMITTING", tests.find(row.id()).status());
        when(client.find(any(), anyString(), eq(row.id()), any())).thenReturn(new AgentJenkinsClient.SearchResult("FOUND", List.of(11L), List.of(22L), 100));
        clock.advance(121); assertEquals("BUILDING", dispatcher(client).advance(row.id()).status()); verify(client, times(1)).submit(any(), any());
    }
    @Test void expiredOwnerCannotSubmitOrOverwriteNewOwner() {
        var row = start("request", "test"); var first = store.claim(row.id()); clock.advance(121); var second = store.claim(row.id());
        assertNotNull(second); assertThrows(AgentBuildTestStore.LeaseLost.class, () -> store.intent(first));
        assertThrows(AgentBuildTestStore.LeaseLost.class, () -> store.finish(first, "BLOCKED", null, null, null, null, "old"));
        assertEquals(second.ownerToken(), tests.find(row.id()).ownerToken()); verifyNoInteractions(client);
    }
    @Test void leaseExpiringDuringRevalidationCannotPersistIntent() {
        var row = start("request", "test"); var claimed = store.claim(row.id());
        doAnswer(invocation -> { clock.advance(121); return null; }).when(access).requireExecution(any(), eq(AgentBuildTestStore.START), anySet());
        assertThrows(AgentBuildTestStore.LeaseLost.class, () -> store.intent(claimed)); assertEquals("PREPARED", tests.find(row.id()).status());
    }
    @ParameterizedTest @ValueSource(strings = {"cancel", "permission", "candidate", "binding", "credential"})
    void revokedPrerequisiteDuringProbeBlocksPost(String kind) {
        var row = start("request", "test");
        doAnswer(invocation -> {
            switch (kind) {
                case "cancel" -> jdbc.update("UPDATE pm_agent_run SET cancel_requested=TRUE WHERE id='run'");
                case "permission" -> doThrow(new AccessDeniedException("revoked")).when(access).requireExecution(any(), eq(AgentBuildTestStore.START), anySet());
                case "candidate" -> { var old = current.get(); current.set(new AgentChangeView(old.id(), old.runId(), old.taskId(), old.revision(), old.manifestHash(), old.createdAt(), false, false, old.blockers(), old.manifest())); }
                case "binding" -> configure("https://other.example.invalid/", List.of(job("test", scope, Map.of("server", "SERVER_SHA"))));
                case "credential" -> doThrow(new IllegalStateException("disabled")).when(configs).requireSecretValue("jenkins", "token");
            }
            return new AgentJenkinsClient.Probe("team/test", true, List.of("REQUEST_ID", "MANIFEST", "ENVIRONMENT", "SERVER_SHA"));
        }).when(client).probe(any());
        assertEquals("BLOCKED", dispatcher.advance(row.id()).status()); verify(client, never()).submit(any(), any()); assertEquals(0, locks());
    }
    @Test void cancelledRunStillObservesOriginalBuildButDoesNotWake() {
        var row = building(); jdbc.update("UPDATE pm_agent_run SET cancel_requested=TRUE WHERE id='run'");
        successBuild(); tick(); report(row, "tests", 7); assertEquals("SUCCEEDED", dispatcher.advance(row.id()).status());
        store.wake(row.id()); assertFalse(tests.find(row.id()).wakePending()); assertEquals("WAITING_INPUT", agents.findRun("run").status());
        verify(client, times(1)).submit(any(), any());
    }
    @Test void exhaustedBudgetIsNotResetByCompletionWake() {
        var row = complete(); jdbc.update("UPDATE pm_agent_run SET external_handle=NULL,step_count=50 WHERE id='run'");
        store.wake(row.id()); assertEquals("WAITING_INPUT", agents.findRun("run").status()); assertFalse(tests.find(row.id()).wakePending());
        assertTrue(agents.listEvents("run").stream().anyMatch(event -> "BUILD_TEST_WAKE_BLOCKED".equals(event.kind())));
    }
    @Test void queueOrBuildMismatchKeepsOriginalHandleAndLocks() {
        var row = start("request", "test"); dispatcher.advance(row.id()); tick();
        when(client.queue(any(), eq(11L), any())).thenReturn(new AgentJenkinsClient.QueueState("STARTED", 11, 99L, false));
        assertEquals("UNKNOWN", dispatcher.advance(row.id()).status()); assertNull(tests.find(row.id()).buildNumber()); assertTrue(locks() > 0);
    }
    @Test void artifactFailureRollsBackTerminalResultResourceReleaseAndJournal() {
        var row = building(); tick(); var claimed = store.claim(row.id());
        var broken = spy(agents); doThrow(new IllegalStateException("artifact failed")).when(broken).insertArtifact(any());
        assertThrows(IllegalStateException.class, () -> store(broken, tests).finish(claimed, "SUCCEEDED", null, null, "SUCCESS", json.writeValueAsString(receipt(row.id())), null));
        assertEquals("BUILDING", tests.find(row.id()).status()); assertEquals(claimed.version(), tests.find(row.id()).version());
        assertTrue(locks() > 0); assertNull(agents.findArtifact("run", "test:" + row.id()));
    }
    @Test void latestAttemptSupersedesEarlierPassAndEveryRelevantBindingIsRequired() {
        configure("https://jenkins.example.invalid/", List.of(job("test", scope, Map.of("server", "SERVER_SHA")), job("integration", scope, Map.of("server", "SERVER_SHA"))));
        complete(); assertFalse(evaluate().satisfied());
        var next = start("integration", "integration"); assertEquals("QUEUED", dispatcher.advance(next.id()).status()); queueStarts(); tick(); dispatcher.advance(next.id());
        successBuild(); tick(); var receipt = receipt(next.id()); receipt.put("jobFullName", "team/integration"); when(client.receipt(any(), eq(22L), anyString())).thenReturn(receipt);
        assertEquals("SUCCEEDED", dispatcher.advance(next.id()).status()); assertTrue(evaluate().satisfied());
        var retry = start("retry", "test"); assertEquals(2, retry.attempt()); assertFalse(evaluate().satisfied()); assertTrue(locks() > 0);
    }
    @Test void missingRepositoryCoverageAndProductionTestBindingAreRejected() {
        configure("https://jenkins.example.invalid/", List.of()); assertThrows(AgentCodePolicy.Unavailable.class, () -> policy.required(current.get().manifest()));
        configure("https://jenkins.example.invalid/", List.of(job("test", scope, Map.of("server", "SERVER_SHA", "web", "WEB_SHA"))));
        assertThrows(AgentCodePolicy.Unavailable.class, () -> policy.required(current.get().manifest()));
        configure("https://jenkins.example.invalid/", List.of(job("test", new AgentScope("BL000012", "PROD"), Map.of("server", "SERVER_SHA"))));
        assertThrows(IllegalArgumentException.class, jenkins::current); verifyNoInteractions(client);
    }
    @Test void realHttpClientPostsOnceAfterPersistedIntentAndConsumesFixedCommitReceipt() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/jenkins/", job = base + "job/team/job/test/";
        configure(base, List.of(job("test", scope, Map.of("server", "SERVER_SHA")))); var row = start("request", "test");
        var params = store.parameters(row); var actions = List.of(Map.of("parameters", params.entrySet().stream().map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue())).toList()));
        var posts = new AtomicInteger(); var intentAtPost = new AtomicReference<String>(); var bodyAtPost = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            Object response; String path = exchange.getRequestURI().getPath(); int status = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                posts.incrementAndGet(); intentAtPost.set(tests.find(row.id()).status()); bodyAtPost.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.getResponseHeaders().set("Location", base + "queue/item/11/"); status = 201; response = Map.of();
            } else if (path.endsWith("/queue/item/11/api/json")) response = Map.of("id", 11, "task", Map.of("url", job), "executable", Map.of("number", 22, "url", job + "22/"), "actions", actions);
            else if (path.endsWith("/22/api/json")) response = Map.of("number", 22, "url", job + "22/", "building", false, "result", "SUCCESS", "queueId", 11, "actions", actions);
            else if (path.endsWith("/artifact/workhub/test.json")) response = receipt(row.id());
            else response = Map.of("fullName", "team/test", "buildable", true, "actions", List.of(Map.of("parameterDefinitions", params.keySet().stream().map(name -> Map.of("name", name)).toList())));
            byte[] bytes = json.writeValueAsBytes(response); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); var actualClient = new AgentJenkinsClient(json, Duration.ofSeconds(2));
        try {
            var actual = dispatcher(actualClient); assertEquals("QUEUED", actual.advance(row.id()).status()); tick(); assertEquals("BUILDING", actual.advance(row.id()).status());
            tick(); assertEquals("SUCCEEDED", actual.advance(row.id()).status()); assertEquals(1, posts.get()); assertEquals("SUBMITTING", intentAtPost.get());
            assertTrue(bodyAtPost.get().contains("SERVER_SHA=" + sha)); assertTrue(bodyAtPost.get().contains("ENVIRONMENT=TEST")); assertTrue(evaluate().satisfied());
        } finally { actualClient.close(); server.stop(0); }
    }

    private AgentBuildTestStore store(AgentMapper agentMapper, AgentBuildTestMapper mapper) {
        var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(manager); interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new AgentBuildTestStore(mapper, resources, agentMapper, runs, changes, access, policy, jenkins, new AgentToolData(json), json, clock));
        proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor); return (AgentBuildTestStore) proxy.getProxy();
    }
    private AgentBuildTestDispatcher dispatcher(AgentJenkinsClient connector) { return new AgentBuildTestDispatcher(tests, store, jenkins, connector, new AgentToolData(json), json, clock); }
    private AgentBuildTestEntity start(String key, String binding) { tool("tool:" + key); return tests.find(store.start(context, "change", binding, key).path("id").asText()); }
    private void tool(String key) { jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id='run'", json.writeValueAsString(Map.of("kind", "TOOL", "key", key, "tool", AgentBuildTestStore.START))); }
    private AgentBuildTestEntity building() { var row = start("request", "test"); dispatcher.advance(row.id()); queueStarts(); tick(); return dispatcher.advance(row.id()); }
    private AgentBuildTestEntity complete() { var row = building(); successBuild(); tick(); report(row, "tests", 7); return dispatcher.advance(row.id()); }
    private void queueStarts() { when(client.queue(any(), anyLong(), any())).thenAnswer(invocation -> { outsideTransaction(); return new AgentJenkinsClient.QueueState("STARTED", 11, 22L, true); }); }
    private void successBuild() { when(client.build(any(), eq(22L), any())).thenAnswer(invocation -> { outsideTransaction(); return new AgentJenkinsClient.BuildState("FINISHED", 22, "SUCCESS", true, 11L); }); }
    private void report(AgentBuildTestEntity row, String name, int value) { var report = receipt(row.id()); report.put(name, value); when(client.receipt(any(), eq(22L), anyString())).thenReturn(report); }
    private AgentBuildTestEvidence.Evaluation evaluate() {
        var value = current.get(); return evidence.check(new AgentChangeSetEntity(value.id(), "run", "task", 1, "key", "h".repeat(64), manifestHash, json.writeValueAsString(value.manifest()), value.createdAt()), value.manifest());
    }
    private ObjectNode receipt(String id) {
        var value = json.createObjectNode(); value.put("schemaVersion", 1); value.put("type", "WORKHUB_BUILD_TEST"); value.put("requestId", id); value.put("manifestHash", manifestHash);
        value.set("scope", json.valueToTree(scope)); value.put("jobFullName", "team/test"); value.put("buildNumber", 22); value.putObject("testedCommits").put("server", sha);
        value.put("buildExitCode", 0); value.put("testExitCode", 0); value.put("tests", 7); value.put("failures", 0); value.put("errors", 0); value.put("skipped", 0);
        value.put("startedAt", clock.instant().minusSeconds(15).toString()); value.put("finishedAt", clock.instant().toString()); value.put("summary", "local fixture executed 7 checks"); return value;
    }
    private AgentJenkinsPolicy.JobBinding job(String id, AgentScope target, Map<String, String> repositories) {
        return new AgentJenkinsPolicy.JobBinding(id, "Independent test", "instance", target, "workhub-server", "BUILD_TEST", List.of("team", id), repositories,
                "REQUEST_ID", "MANIFEST", "ENVIRONMENT", Map.of(), "workhub/test.json", true);
    }
    private void configure(String base, List<AgentJenkinsPolicy.JobBinding> jobs) {
        var instance = new AgentJenkinsPolicy.Instance("instance", "Jenkins", base, "fixture", "jenkins", "token", true);
        policyText.set(json.writeValueAsString(new AgentJenkinsPolicy.Policy(List.of(instance), jobs)));
    }
    private int locks() { return jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_release_resource", Integer.class); }
    private void tick() { clock.advance(16); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static void outsideTransaction() { assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Jenkins HTTP 不应持有数据库事务"); }
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T12:00:00Z"));
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    }
}
