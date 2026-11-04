package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.system.SysConfigService;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import tools.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 生产迁移/Mapper/事务/状态机；批准材料与实测就绪状态是隔离夹具，不代表真实测试或人工审批。 */
class AgentDeploymentDispatcherTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock();
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final String manifestHash = "a".repeat(64), sha = "b".repeat(40);
    private final AtomicReference<String> policyText = new AtomicReference<>();
    private final AtomicReference<AgentChangeView> current = new AtomicReference<>();
    private final Set<String> reviewerPermissions = new HashSet<>(Set.of("autonomy:release:review"));
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private AgentMapper agents;
    private AgentDeploymentMapper mapper;
    private AgentDeploymentStore store;
    private AgentDeploymentApproval approvalGate;
    private AgentDeploymentDispatcher dispatcher;
    private AgentJenkinsPolicy policies;
    private SysConfigService configs;
    private AgentJenkinsClient client;
    private AgentAccessService access;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = source.getConnection(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_054000__add_agent_deployment_outbox.sql", "V20261104_061500__add_agent_release_plans.sql")) {
            ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + file));
        }
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class); configuration.addMapper(AgentDeploymentMapper.class); configuration.addMapper(AgentReleasePlanMapper.class);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject()); agents = sessions.getMapper(AgentMapper.class); mapper = sessions.getMapper(AgentDeploymentMapper.class);
        var manager = new DataSourceTransactionManager(source); transaction = new TransactionTemplate(manager);
        configs = mock(SysConfigService.class);
        when(configs.findPlainValue("agent.jenkins", "policy")).thenAnswer(invocation -> policyText.get());
        when(configs.requireSecretValue("jenkins", "token")).thenReturn("fixture-secret");
        policies = new AgentJenkinsPolicy(configs, json); configure("https://jenkins.example.invalid/", "TEST_DEPLOY", scope);
        var task = new AgentTaskSpecification("修复测试问题", "", "实际测试与人工审核", List.of("DEPLOYMENT"), List.of(scope, new AgentScope("BL000012", "PROD")), List.of());
        var config = new AgentConfiguration("Agent", "repair", 1L, "fixture", task.scopes(), List.of(), 50, 600, 1);
        var context = new AgentToolContext("run", "task", "agent", "owner", null, config, task);
        agents.insertRun(new AgentRunEntity("run", "task", "agent", "manual", "owner", json.writeValueAsString(config), json.writeValueAsString(task), "{}",
                "WAITING_INPUT", 1, 0, false, null, null, null, null, null, 0, now(), now()));
        var runs = mock(AgentRunStore.class); when(runs.get("run")).thenAnswer(invocation -> agents.findRun("run")); when(runs.context(any())).thenReturn(context);
        access = mock(AgentAccessService.class);
        when(access.actor(anyString())).thenAnswer(invocation -> new AgentAccessService.Actor(invocation.getArgument(0), Set.copyOf(reviewerPermissions)));
        doAnswer(invocation -> { if (!task.scopes().contains(invocation.getArgument(1))) throw new AccessDeniedException("scope"); return null; }).when(access).requireScope(any(), any());
        var repo = new AgentRepositoryChange("server", "Server", scope, "workspace", 1, "p".repeat(64), "development", "c".repeat(40), "candidate", sha, List.of("src/Main.java"), "d".repeat(64), "diff", false);
        var manifest = new AgentChangeManifest(1, "run", "task", task, new AgentChangePlan("fix", "API", "regression", "restore", List.of()), null, List.of(repo), List.of("server"));
        current.set(new AgentChangeView("change", "run", "task", 1, manifestHash, now(), true, true, List.of(), manifest));
        var changes = mock(AgentChangeService.class); when(changes.detailForUser(eq("change"), anyString())).thenAnswer(invocation -> current.get());
        addArtifact("test-proof", "TEST_REPORT", "PLATFORM_EXECUTION", "{\"fixture\":\"trusted platform test record\"}");
        approvalGate = new AgentDeploymentApproval(mapper, agents, changes, runs, access, policies, mock(AgentVerificationMapper.class), json);
        var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(manager); interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new AgentDeploymentStore(mapper, agents, approvalGate, new AgentReleasePlanGate(sessions.getMapper(AgentReleasePlanMapper.class), mapper, agents, json), json, clock)); proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor);
        store = (AgentDeploymentStore) proxy.getProxy();
        client = mock(AgentJenkinsClient.class);
        when(client.probe(any())).thenAnswer(invocation -> { outsideTransaction(); return new AgentJenkinsClient.Probe("team/app", true, policies.requiredParameters(policies.binding("deploy").binding())); });
        when(client.submit(any(), any())).thenAnswer(invocation -> {
            outsideTransaction(); Map<String, String> parameters = invocation.getArgument(1);
            assertEquals("SUBMITTING", mapper.find(parameters.get("REQUEST_ID")).status()); return 11L;
        });
        dispatcher = dispatcher();
    }
    @AfterEach void close() throws Exception { keepAlive.close(); }

    @Test void approvedRequestPersistsBeforePostAndCompletesOnlyWithActualVersionReceipt() {
        var accepted = approved(); var initial = enqueue(accepted);
        assertEquals(initial.id(), transaction.execute(tx -> store.enqueue(accepted.id())).id());
        assertEquals("QUEUED", dispatcher.advance(initial.id()).status());
        when(client.queue(any(), eq(11L), any())).thenReturn(new AgentJenkinsClient.QueueState("STARTED", 11, 22L, true));
        tick(); assertEquals("BUILDING", dispatcher.advance(initial.id()).status());
        successBuild(); when(client.receipt(any(), eq(22L), anyString())).thenReturn(receipt(initial.id(), "SUCCEEDED", Map.of("server", sha)));
        tick(); var result = dispatcher.advance(initial.id());
        assertEquals("SUCCEEDED", result.status()); assertEquals(22, result.buildNumber()); assertEquals(0, locks());
        assertEquals(false, AgentDeploymentDispatcher.view(result).get("businessVerified")); assertNotNull(result.receiptJson());
        assertEquals("SUCCEEDED", dispatcher.advance(initial.id()).status()); verify(client, times(1)).submit(any(), any());
        String events = json.writeValueAsString(agents.listEvents("run")); assertTrue(events.contains("SUBMITTING")); assertTrue(events.contains("SUCCEEDED")); assertFalse(events.contains("fixture-secret"));
    }

    @Test void approvalAndOutboxMustShareTransactionAndRollbackTogether() {
        var accepted = approved();
        assertThrows(IllegalTransactionStateException.class, () -> store.enqueue(accepted.id()));
        assertThrows(IllegalStateException.class, () -> transaction.execute(tx -> { mapper.insertApproval(accepted); store.enqueue(accepted.id()); throw new IllegalStateException("rollback"); }));
        assertNull(mapper.approval(accepted.id())); assertNull(mapper.find(accepted.requestId())); assertEquals(0, locks()); verifyNoInteractions(client);
    }
    @Test void leaseExpiryDuringMaterialRevalidationCannotPersistSubmissionIntent() {
        var request = enqueue(approved()); var claimed = store.claim(request.id());
        doAnswer(invocation -> { clock.advance(AgentDeploymentStore.LEASE_SECONDS + 1); return null; }).when(access).requireRunAuthorization(any());
        assertThrows(AgentDeploymentStore.LeaseLost.class, () -> store.intent(claimed));
        assertEquals("PREPARED", mapper.find(request.id()).status()); verifyNoInteractions(client);
    }

    @Test void unapprovedOrUntestedMaterialCannotEnterOutbox() {
        var accepted = approved(); mapper.insertApproval(accepted);
        jdbc.update("UPDATE pm_agent_release_approval SET decision='REJECTED' WHERE id=?", accepted.id());
        assertThrows(IllegalArgumentException.class, () -> transaction.execute(tx -> store.enqueue(accepted.id())));
        jdbc.update("UPDATE pm_agent_release_approval SET decision='APPROVED' WHERE id=?", accepted.id());
        var old = current.get(); current.set(new AgentChangeView(old.id(), old.runId(), old.taskId(), old.revision(), old.manifestHash(), old.createdAt(), true, false, List.of("PLATFORM_TEST_EVIDENCE_MISSING"), old.manifest()));
        assertThrows(IllegalArgumentException.class, () -> transaction.execute(tx -> store.enqueue(accepted.id())));
        assertEquals(0, locks()); verifyNoInteractions(client);
    }

    @Test void materialCannotSubstituteModelReportOrChangeApprovedParameters() {
        var accepted = approved(); mapper.insertApproval(accepted);
        jdbc.update("UPDATE pm_agent_artifact SET provenance='AI_REPORT' WHERE id='test-proof'");
        assertThrows(IllegalArgumentException.class, () -> approvalGate.validate(accepted));
        jdbc.update("UPDATE pm_agent_artifact SET provenance='PLATFORM_EXECUTION' WHERE id='test-proof'");
        jdbc.update("UPDATE pm_agent_release_approval SET parameters_json=? WHERE id=?", accepted.parametersJson().replace(sha, "d".repeat(40)), accepted.id());
        assertThrows(IllegalArgumentException.class, () -> transaction.execute(tx -> store.enqueue(accepted.id()))); verifyNoInteractions(client);
    }

    @Test void newEvidenceOrCodeInvalidatesOldSnapshotBeforeSending() {
        var request = enqueue(approved()); addArtifact("later-proof", "TEST_REPORT", "PLATFORM_EXECUTION", "{\"fixture\":\"new evidence\"}");
        assertEquals("BLOCKED", dispatcher.advance(request.id()).status()); assertEquals(0, locks()); verify(client, never()).submit(any(), any());
    }

    @Test void permissionRevokedDuringProbePreventsPost() {
        var request = enqueue(approved());
        when(client.probe(any())).thenAnswer(invocation -> { reviewerPermissions.clear(); return new AgentJenkinsClient.Probe("team/app", true, policies.requiredParameters(policies.binding("deploy").binding())); });
        assertEquals("BLOCKED", dispatcher.advance(request.id()).status()); verify(client, never()).submit(any(), any()); assertEquals(0, locks());
    }

    @Test void changedBindingCannotRedirectRequestOrReadNewCredentials() {
        var request = enqueue(approved()); configure("https://different.example.invalid/", "TEST_DEPLOY", scope);
        assertEquals("BLOCKED", dispatcher.advance(request.id()).status()); verifyNoInteractions(client); assertEquals(0, locks());
    }

    @Test void credentialRevokedDuringProbeCannotBeUsedForSubmission() {
        var request = enqueue(approved());
        when(client.probe(any())).thenAnswer(invocation -> {
            doThrow(new IllegalStateException("credential disabled")).when(configs).requireSecretValue("jenkins", "token");
            return new AgentJenkinsClient.Probe("team/app", true, policies.requiredParameters(policies.binding("deploy").binding()));
        });
        assertEquals("BLOCKED", dispatcher.advance(request.id()).status()); verify(client, never()).submit(any(), any()); assertEquals(0, locks());
    }

    @Test void lostSubmitResponseRecoversPersistedBuildWithoutPostingAgain() {
        var request = enqueue(approved()); doThrow(new AgentJenkinsClient.SubmissionUnknown("lost", null)).when(client).submit(any(), any());
        assertEquals("UNKNOWN", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0);
        when(client.find(any(), anyString(), eq(request.id()), any())).thenReturn(new AgentJenkinsClient.SearchResult("FOUND", List.of(11L), List.of(22L), 100));
        clock.advance(61); dispatcher = dispatcher(); assertEquals("BUILDING", dispatcher.advance(request.id()).status());
        successBuild(); when(client.receipt(any(), eq(22L), anyString())).thenReturn(receipt(request.id(), "SUCCEEDED", Map.of("server", sha)));
        tick(); assertEquals("SUCCEEDED", dispatcher.advance(request.id()).status()); verify(client, times(1)).submit(any(), any());
    }

    @Test void crashAfterIntentOnlyReconcilesAndFencesLateWorker() {
        var request = enqueue(approved()); var oldWorker = store.intent(store.claim(request.id()));
        when(client.find(any(), anyString(), eq(request.id()), any())).thenReturn(new AgentJenkinsClient.SearchResult("NOT_FOUND_IN_WINDOW", List.of(), List.of(), 100));
        clock.advance(121); assertEquals("UNKNOWN", dispatcher().advance(request.id()).status());
        assertThrows(AgentDeploymentStore.LeaseLost.class, () -> store.finish(oldWorker, "QUEUED", 11L, null, null, null, null));
        verify(client, never()).submit(any(), any()); assertTrue(locks() > 0);
    }

    @Test void unknownSearchResultsKeepLocksAndCannotBeBypassedWithNewApproval() {
        var request = enqueue(approved()); doThrow(new AgentJenkinsClient.SubmissionUnknown("lost", null)).when(client).submit(any(), any()); dispatcher.advance(request.id());
        for (String state : List.of("NOT_FOUND_IN_WINDOW", "DUPLICATE", "PARAMETER_MISMATCH")) {
            when(client.find(any(), anyString(), eq(request.id()), any())).thenReturn(new AgentJenkinsClient.SearchResult(state, List.of(), List.of(), 100));
            clock.advance(61); assertEquals("UNKNOWN", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0);
        }
        assertThrows(IllegalStateException.class, () -> enqueue(approved())); verify(client, times(1)).submit(any(), any());
    }

    @Test void concurrentWorkersCannotSubmitTwice() throws Exception {
        var request = enqueue(approved()); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return 11L; }).when(client).submit(any(), any());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> dispatcher.advance(request.id())); assertTrue(entered.await(5, TimeUnit.SECONDS));
            try { assertEquals("SUBMITTING", dispatcher.advance(request.id()).status()); } finally { release.countDown(); }
            assertEquals("QUEUED", first.get(5, TimeUnit.SECONDS).status());
        }
        verify(client, times(1)).submit(any(), any());
    }

    @Test void cancelledTaskDoesNotPretendAnExistingBuildHasStopped() {
        var request = enqueue(approved()); dispatcher.advance(request.id());
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=1,status='CANCELLING' WHERE id='run'");
        when(client.queue(any(), anyLong(), any())).thenReturn(new AgentJenkinsClient.QueueState("STARTED", 11, 22L, true));
        tick(); assertEquals("BUILDING", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0); verify(client, times(1)).submit(any(), any());
    }

    @Test void cancellationBeforeIntentBlocksNewRelease() {
        var request = enqueue(approved()); jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'");
        assertEquals("BLOCKED", dispatcher.advance(request.id()).status()); verifyNoInteractions(client); assertEquals(0, locks());
    }

    @Test void expiredQueueRecoversOriginalBuildButCannotReplaceKnownQueue() {
        var request = enqueue(approved()); dispatcher.advance(request.id());
        when(client.queue(any(), anyLong(), any())).thenReturn(new AgentJenkinsClient.QueueState("EXPIRED", 11, null, false));
        when(client.find(any(), anyString(), anyString(), any())).thenReturn(new AgentJenkinsClient.SearchResult("FOUND", List.of(12L), List.of(22L), 100));
        tick(); var unknown = dispatcher.advance(request.id()); assertEquals("UNKNOWN", unknown.status()); assertEquals(11, unknown.queueId()); assertNull(unknown.buildNumber());
        when(client.find(any(), anyString(), anyString(), any())).thenReturn(new AgentJenkinsClient.SearchResult("FOUND", List.of(11L), List.of(22L), 100));
        clock.advance(61); assertEquals("BUILDING", dispatcher.advance(request.id()).status()); verify(client, times(1)).submit(any(), any());
    }

    @Test void buildSuccessWithoutReceiptOrWithWrongShaCannotCountAsDeployment() {
        var request = building(); successBuild();
        tick(); assertEquals("DEPLOYING", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0);
        when(client.receipt(any(), eq(22L), anyString())).thenReturn(receipt(request.id(), "SUCCEEDED", Map.of("server", "f".repeat(40))));
        tick(); assertEquals("UNKNOWN", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0);
    }

    @Test void partialReceiptAndFailedBuildAreTerminalFailuresWithNoBusinessSuccess() {
        var request = building(); successBuild();
        when(client.receipt(any(), eq(22L), anyString())).thenReturn(receipt(request.id(), "PARTIAL", Map.of()));
        tick(); var failed = dispatcher.advance(request.id()); assertEquals("FAILED", failed.status()); assertTrue(failed.receiptJson().contains("PARTIAL")); assertEquals(0, locks());
        assertEquals(false, AgentDeploymentDispatcher.view(failed).get("businessVerified"));
        var next = building(); when(client.build(any(), eq(22L), any())).thenReturn(new AgentJenkinsClient.BuildState("FINISHED", 22, "FAILURE", true, 11L));
        tick(); assertEquals("FAILED", dispatcher.advance(next.id()).status()); assertEquals(0, locks());
    }

    @Test void missingOrMismatchedBuildHoldsResourceUntilActualTerminalState() {
        var request = building();
        when(client.build(any(), eq(22L), any())).thenReturn(new AgentJenkinsClient.BuildState("MISSING", 22, null, false, null));
        tick(); assertEquals("UNKNOWN", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0);
        when(client.build(any(), eq(22L), any())).thenReturn(new AgentJenkinsClient.BuildState("FINISHED", 22, "SUCCESS", false, 11L));
        clock.advance(61); assertEquals("UNKNOWN", dispatcher.advance(request.id()).status()); verify(client, never()).receipt(any(), anyLong(), anyString());
    }

    @Test void explicitRejectionOrQueueCancellationDoesNotRetry() {
        var request = enqueue(approved()); doThrow(new AgentJenkinsClient.SubmissionRejected("403")).when(client).submit(any(), any());
        assertEquals("REJECTED", dispatcher.advance(request.id()).status()); assertEquals(0, locks());
        tick(); assertEquals("REJECTED", dispatcher.advance(request.id()).status()); verify(client, times(1)).submit(any(), any());
        doReturn(11L).when(client).submit(any(), any()); var next = enqueue(approved()); dispatcher.advance(next.id());
        when(client.queue(any(), anyLong(), any())).thenReturn(new AgentJenkinsClient.QueueState("CANCELLED", 11, null, true));
        tick(); assertEquals("ABORTED", dispatcher.advance(next.id()).status()); assertEquals(0, locks());
    }

    @Test void readFailureBeforeSendingRemainsRetryableWithoutUnknownWrite() {
        var request = enqueue(approved()); doThrow(new AgentJenkinsClient.ClientFailure("offline")).when(client).probe(any());
        assertEquals("PREPARED", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0); verify(client, never()).submit(any(), any());
    }

    @Test void policyChangeAfterSendingKeepsUnknownStateAndResourceReservation() {
        var request = enqueue(approved()); dispatcher.advance(request.id()); configure("https://changed.example.invalid/", "TEST_DEPLOY", scope);
        tick(); assertEquals("UNKNOWN", dispatcher.advance(request.id()).status()); assertTrue(locks() > 0); verify(client, times(1)).submit(any(), any()); verify(client, never()).queue(any(), anyLong(), any());
    }

    @Test void productionNeedsVerifiedTestEnvironmentCoveringEntireManifest() {
        configure("https://jenkins.example.invalid/", "PROD_DEPLOY", new AgentScope("BL000012", "PROD"));
        assertThrows(IllegalArgumentException.class, () -> enqueue(approved()));
        addArtifact("verification", "VERIFICATION", "PLATFORM_VERIFICATION", json.writeValueAsString(Map.of("phase", "TEST_ENVIRONMENT", "passed", true, "changeSetId", "change", "manifestHash", manifestHash, "deploymentIds", List.of("missing"))));
        assertThrows(IllegalArgumentException.class, () -> enqueue(approved())); verifyNoInteractions(client);
    }

    @Test void jobResourceIdentityNormalizesTrailingSlashHostCaseAndDefaultPort() {
        configure("https://JENKINS.example.invalid:443", "TEST_DEPLOY", scope); var first = AgentDeploymentStore.resources(policies.binding("deploy"));
        configure("https://jenkins.example.invalid/", "TEST_DEPLOY", scope); assertEquals(first, AgentDeploymentStore.resources(policies.binding("deploy")));
    }

    @Test void changedCodeDuringProbeInvalidatesApprovalBeforePost() {
        var request = enqueue(approved());
        when(client.probe(any())).thenAnswer(invocation -> {
            var old = current.get(); current.set(new AgentChangeView(old.id(), old.runId(), old.taskId(), old.revision(), old.manifestHash(), old.createdAt(), false, false, List.of("CODE_CHANGED:server"), old.manifest()));
            return new AgentJenkinsClient.Probe("team/app", true, policies.requiredParameters(policies.binding("deploy").binding()));
        });
        assertEquals("BLOCKED", dispatcher.advance(request.id()).status()); verify(client, never()).submit(any(), any()); assertEquals(0, locks());
    }

    @Test void productionRejectsVerificationArtifactWithoutPersistedCertificateEvenWithSuccessfulTestDeployment() {
        var test = building(); successBuild(); when(client.receipt(any(), eq(22L), anyString())).thenReturn(receipt(test.id(), "SUCCEEDED", Map.of("server", sha)));
        tick(); assertEquals("SUCCEEDED", dispatcher.advance(test.id()).status());
        addArtifact("verification", "VERIFICATION", "PLATFORM_VERIFICATION", json.writeValueAsString(Map.of("phase", "TEST_ENVIRONMENT", "passed", true, "changeSetId", "change", "manifestHash", manifestHash, "deploymentIds", List.of(test.id()))));
        configure("https://jenkins.example.invalid/", "PROD_DEPLOY", new AgentScope("BL000012", "PROD"));
        var production = approved(); assertThrows(IllegalArgumentException.class, () -> approvalGate.validate(production));
    }

    @Test void receiptRejectsForeignIdentityWrongTypesTimesAndIncompleteSuccess() {
        var request = enqueue(approved()); var binding = policies.binding("deploy");
        var params = policies.parameters(binding, request.id(), manifestHash, Map.of("server", sha));
        var valid = receipt(request.id(), "SUCCEEDED", Map.of("server", sha));
        assertEquals(sha, AgentDeploymentReceipt.verify(valid, request, binding, 22, params, clock.instant()).deployedCommits().get("server"));
        var mutations = List.<java.util.function.Consumer<tools.jackson.databind.node.ObjectNode>>of(
                value -> value.put("requestId", "other"), value -> value.put("manifestHash", "f".repeat(64)),
                value -> value.put("schemaVersion", "1"), value -> value.put("buildNumber", "22"),
                value -> value.put("stage", "PROD_DEPLOY"), value -> value.put("jobFullName", "other/job"),
                value -> value.set("scope", json.valueToTree(new AgentScope("BL000012", "PROD"))),
                value -> value.put("finishedAt", clock.instant().plusSeconds(600).toString()),
                value -> value.put("startedAt", clock.instant().minusSeconds(600).toString()),
                value -> value.set("deployedCommits", json.createObjectNode()),
                value -> value.set("deployedCommits", json.valueToTree(Map.of("unbound", sha))));
        for (var mutation : mutations) {
            var candidate = (tools.jackson.databind.node.ObjectNode) valid.deepCopy(); mutation.accept(candidate);
            assertThrows(IllegalArgumentException.class, () -> AgentDeploymentReceipt.verify(candidate, request, binding, 22, params, clock.instant()));
        }
        var extra = (tools.jackson.databind.node.ObjectNode) valid.deepCopy(); extra.put("token", "must-not-be-saved");
        assertFalse(json.writeValueAsString(AgentDeploymentReceipt.verify(extra, request, binding, 22, params, clock.instant())).contains("must-not-be-saved"));
    }

    @Test void actualHttpClientAndDurableStoreCompleteOneRequestWithoutDatabaseTransactionAcrossPost() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/jenkins/", job = base + "job/team/job/app/";
        configure(base, "TEST_DEPLOY", scope); var accepted = approved(); var request = enqueue(accepted);
        @SuppressWarnings("unchecked") Map<String, String> params = json.readValue(accepted.parametersJson(), Map.class);
        var actions = List.of(Map.of("parameters", params.entrySet().stream().map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue())).toList()));
        var posts = new AtomicInteger(); var intentAtPost = new AtomicReference<String>(); var bodyAtPost = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            Object response; String path = exchange.getRequestURI().getPath(); int status = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                posts.incrementAndGet(); intentAtPost.set(mapper.find(request.id()).status());
                bodyAtPost.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.getResponseHeaders().set("Location", base + "queue/item/11/"); status = 201; response = Map.of();
            } else if (path.endsWith("/queue/item/11/api/json")) response = Map.of("id", 11, "task", Map.of("url", job), "executable", Map.of("number", 22, "url", job + "22/"), "actions", actions);
            else if (path.endsWith("/22/api/json")) response = Map.of("number", 22, "url", job + "22/", "building", false, "result", "SUCCESS", "queueId", 11, "actions", actions);
            else if (path.endsWith("/artifact/workhub/deployment.json")) response = receipt(request.id(), "SUCCEEDED", Map.of("server", sha));
            else response = Map.of("fullName", "team/app", "buildable", true, "actions", List.of(Map.of("parameterDefinitions", params.keySet().stream().map(name -> Map.of("name", name)).toList())));
            byte[] bytes = json.writeValueAsBytes(response); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); var realClient = new AgentJenkinsClient(json, Duration.ofSeconds(2));
        try {
            var actual = new AgentDeploymentDispatcher(mapper, store, approvalGate, policies, realClient, json, clock);
            assertEquals("QUEUED", actual.advance(request.id()).status()); tick(); assertEquals("BUILDING", actual.advance(request.id()).status());
            tick(); assertEquals("SUCCEEDED", actual.advance(request.id()).status()); assertEquals(1, posts.get()); assertEquals("SUBMITTING", intentAtPost.get());
            assertTrue(bodyAtPost.get().contains("SERVER_SHA=" + sha)); assertTrue(bodyAtPost.get().contains("REQUEST_ID=" + request.id())); assertEquals(0, locks());
        } finally { realClient.close(); server.stop(0); }
    }

    private AgentDeploymentEntity building() {
        var request = enqueue(approved()); dispatcher.advance(request.id());
        when(client.queue(any(), anyLong(), any())).thenReturn(new AgentJenkinsClient.QueueState("STARTED", 11, 22L, true));
        tick(); return dispatcher.advance(request.id());
    }
    private void successBuild() { when(client.build(any(), eq(22L), any())).thenReturn(new AgentJenkinsClient.BuildState("FINISHED", 22, "SUCCESS", true, 11L)); }
    private AgentReleaseApprovalEntity approved() {
        var binding = policies.binding("deploy"); String id = UUID.randomUUID().toString();
        var params = policies.parameters(binding, id, manifestHash, Map.of("server", sha));
        var evidence = agents.listArtifacts("run").stream().sorted(Comparator.comparing(AgentArtifactEntity::id)).toList();
        String snapshot = json.writeValueAsString(Map.of("schemaVersion", 1, "change", current.get(), "evidence", evidence, "binding", binding, "parameters", params));
        return new AgentReleaseApprovalEntity(UUID.randomUUID().toString(), id, "run", "change", manifestHash, json.writeValueAsString(binding), binding.hash(),
                json.writeValueAsString(params), AgentCodePolicy.hash(json.writeValueAsString(params)), "APPROVED", "reviewer", "fixture approval", snapshot, now());
    }
    private AgentDeploymentEntity enqueue(AgentReleaseApprovalEntity value) { return transaction.execute(tx -> { mapper.insertApproval(value); return store.enqueue(value.id()); }); }
    private void addArtifact(String id, String kind, String provenance, String payload) { agents.insertArtifact(new AgentArtifactEntity(id, "run", id, kind, provenance, payload, AgentCodePolicy.hash(payload), now())); }
    private tools.jackson.databind.JsonNode receipt(String requestId, String status, Map<String, String> commits) {
        var configured = json.readValue(policyText.get(), AgentJenkinsPolicy.Policy.class).bindings().getFirst();
        var value = new LinkedHashMap<String, Object>(); value.put("schemaVersion", 1); value.put("type", "WORKHUB_DEPLOYMENT"); value.put("requestId", requestId);
        value.put("manifestHash", manifestHash); value.put("stage", configured.stage()); value.put("scope", configured.scope());
        value.put("jobFullName", "team/app"); value.put("buildNumber", 22); value.put("deploymentStatus", status); value.put("deployedCommits", commits);
        value.put("startedAt", clock.instant().minusSeconds(15).toString()); value.put("finishedAt", clock.instant().toString()); return json.valueToTree(value);
    }
    private void configure(String base, String stage, AgentScope scope) {
        var instance = new AgentJenkinsPolicy.Instance("instance", "Jenkins", base, "fixture", "jenkins", "token", true);
        var binding = new AgentJenkinsPolicy.JobBinding("deploy", "Release", "instance", scope, "workhub-server", stage, List.of("team", "app"), Map.of("server", "SERVER_SHA"), "REQUEST_ID", "MANIFEST", "ENVIRONMENT", Map.of(), "workhub/deployment.json", true);
        policyText.set(json.writeValueAsString(new AgentJenkinsPolicy.Policy(List.of(instance), List.of(binding))));
    }
    private AgentDeploymentDispatcher dispatcher() { return new AgentDeploymentDispatcher(mapper, store, approvalGate, policies, client, json, clock); }
    private int locks() { return jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_release_resource", Integer.class); }
    private void tick() { clock.advance(16); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static void outsideTransaction() { assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "网络不能跨数据库事务"); }
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T06:00:00Z"));
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    }
}
