package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.system.SysConfigService;
import jakarta.validation.Validation;
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
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 生产 SQL/Mapper/事务，实际测试就绪由隔离夹具声明；不访问 AI、GitLab 或 Jenkins。 */
class AgentReleasePlanServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T07:00:00Z"), ZoneOffset.UTC);
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final String hash = "a".repeat(64), sha = "b".repeat(40);
    private final Set<String> permissions = new HashSet<>(Set.of("agent:code:deliver", "autonomy:release:review"));
    private final AtomicReference<AgentChangeView> current = new AtomicReference<>();
    private final AtomicReference<String> policyText = new AtomicReference<>();
    private Connection connection;
    private JdbcTemplate jdbc;
    private AgentMapper agents;
    private AgentDeploymentMapper deployments;
    private AgentReleasePlanMapper plans;
    private AgentJenkinsPolicy policies;
    private AgentReleasePlanGate gate;
    private AgentDeploymentStore outbox;
    private AgentReleasePlanService service;
    private AgentAccessService access;
    private AgentRunStore runs;
    private TransactionTemplate tx;
    private AgentConfiguration configuration;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        connection = source.getConnection(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_054000__add_agent_deployment_outbox.sql", "V20261104_061500__add_agent_release_plans.sql")) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + file));
        }
        var mybatis = new Configuration(); mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.addMapper(AgentMapper.class); mybatis.addMapper(AgentDeploymentMapper.class); mybatis.addMapper(AgentReleasePlanMapper.class);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(mybatis);
        var sessions = new SqlSessionTemplate(factory.getObject());
        agents = sessions.getMapper(AgentMapper.class); deployments = sessions.getMapper(AgentDeploymentMapper.class); plans = sessions.getMapper(AgentReleasePlanMapper.class);
        var manager = new DataSourceTransactionManager(source); tx = new TransactionTemplate(manager);
        var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(manager); interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var configs = mock(SysConfigService.class); when(configs.findPlainValue("agent.jenkins", "policy")).thenAnswer(i -> policyText.get());
        policies = new AgentJenkinsPolicy(configs, json); setBindings(List.of(binding("server", "server", "server"), binding("web", "web", "web")));
        var task = new AgentTaskSpecification("修复", "", "真实验证", List.of("DEPLOYMENT"), List.of(scope, new AgentScope("BL000012", "PROD")), List.of(scope, new AgentScope("BL000012", "PROD")));
        configuration = new AgentConfiguration("Agent", "repair", 1L, "fixture", task.scopes(), 50, 600, 1);
        agents.insertAgent(new AgentDefinitionEntity("agent", "Agent", json.writeValueAsString(configuration), true, "owner", 0, now(), now()));
        agents.insertRun(new AgentRunEntity("run", "task", "agent", "manual", "owner", json.writeValueAsString(configuration), json.writeValueAsString(task), "{}", "WAITING_INPUT", 1, 0,
                false, null, null, null, null, null, 0, now(), now()));
        var context = new AgentToolContext("run", "task", "agent", "owner", null, configuration, task);
        runs = mock(AgentRunStore.class); when(runs.get("run")).thenAnswer(i -> agents.findRun("run"));
        when(runs.context(any())).thenAnswer(i -> {
            var row = (AgentRunEntity) i.getArgument(0);
            return new AgentToolContext(row.id(), row.taskId(), row.agentId(), row.requestedBy(), row.ownerToken(), configuration, json.readValue(row.specificationJson(), AgentTaskSpecification.class));
        });
        access = mock(AgentAccessService.class); when(access.actor(anyString())).thenAnswer(i -> new AgentAccessService.Actor(i.getArgument(0), Set.copyOf(permissions)));
        doAnswer(i -> { if (!((AgentToolContext) i.getArgument(0)).specification().scopes().contains(i.getArgument(1))) throw new AccessDeniedException("scope"); return null; }).when(access).requireScope(any(), any());
        var manifest = new AgentChangeManifest(1, "run", "task", task, new AgentChangePlan("fix", "API", "regression", "restore", List.of(new AgentChangePlan.Dependency("web", "server"))),
                null, List.of(repo("server"), repo("web")), List.of("server", "web"));
        current.set(new AgentChangeView("change", "run", "task", 1, hash, now(), true, true, List.of(), manifest));
        var changes = mock(AgentChangeService.class); when(changes.detailForUser(eq("change"), anyString())).thenAnswer(i -> current.get());
        artifact("proof", "TEST_REPORT", "PLATFORM_EXECUTION", "{\"fixture\":true}");
        var approval = new AgentDeploymentApproval(deployments, agents, changes, runs, access, policies, mock(AgentVerificationMapper.class), json);
        gate = new AgentReleasePlanGate(plans, deployments, agents, json);
        outbox = proxy(new AgentDeploymentStore(deployments, agents, approval, gate, json, clock), interceptor);
        service = proxy(new AgentReleasePlanService(plans, deployments, agents, changes, runs, access, policies, approval, outbox, gate, json,
                Validation.buildDefaultValidatorFactory().getValidator(), clock), interceptor);
    }
    @AfterEach void close() throws Exception { connection.close(); }

    @Test void wholePlanFreezesExactCommitsAndDerivedOrderThenEnqueuesAtomically() {
        var plan = prepare("plan"); var snapshot = gate.snapshot(plans.find(plan));
        assertEquals(List.of("server", "web"), snapshot.steps().stream().map(AgentReleasePlanSnapshot.Step::bindingId).toList());
        assertEquals(List.of("server"), snapshot.steps().get(1).dependsOn());
        assertEquals(0, count("pm_agent_deployment")); assertEquals(sha, snapshot.steps().getFirst().parameters().get("COMMIT"));
        assertEquals(true, service.detail(plan, "reviewer").get("readyForReview"));
        approve(plan);
        assertEquals(2, count("pm_agent_deployment")); assertEquals(2, count("pm_agent_release_approval")); assertEquals(1, count("pm_agent_release_review"));
        assertEquals("APPROVED", plans.find(plan).status()); assertEquals("QUEUED", agents.findRun("run").status());
        for (var step : snapshot.steps()) for (var resource : AgentDeploymentStore.resources(step.binding())) assertEquals(plan, deployments.resourceOwner(resource));
    }
    @Test void missingRealTestAllowsVisibleDraftButNeverApproval() {
        ready(false); var plan = prepare("draft");
        assertEquals(false, service.detail(plan, "owner").get("readyForReview"));
        assertThrows(IllegalArgumentException.class, () -> approve(plan));
        assertEquals("WAITING_REVIEW", plans.find(plan).status()); assertEquals(0, count("pm_agent_release_review")); assertEquals(0, count("pm_agent_deployment"));
    }
    @Test void modelReportCannotReplaceTrustedTestEvidence() {
        jdbc.update("UPDATE pm_agent_artifact SET provenance='AI_REPORT' WHERE id='proof'");
        var plan = prepare("draft"); assertThrows(IllegalArgumentException.class, () -> approve(plan)); assertEquals(0, count("pm_agent_deployment"));
    }
    @Test void evidenceArrivingAfterDraftRequiresNewImmutablePlan() {
        ready(false); var old = prepare("old"); ready(true);
        assertEquals(false, service.detail(old, "owner").get("current")); assertThrows(IllegalStateException.class, () -> approve(old));
        var next = prepare("new"); approve(next); assertEquals("WAITING_REVIEW", plans.find(old).status());
    }
    @Test void addedEvidenceOrChangedBindingInvalidatesApproval() {
        var plan = prepare("old"); artifact("later", "VERIFICATION", "PLATFORM_VERIFICATION", "{\"fixture\":\"later\"}");
        assertThrows(IllegalStateException.class, () -> approve(plan));
        var next = prepare("new"); setBindings(List.of(binding("server", "changed-job", "server"), binding("web", "web", "web")));
        assertEquals(false, service.detail(next, "owner").get("current")); assertThrows(IllegalStateException.class, () -> approve(next));
    }
    @Test void repeatedPrepareAndReviewAreIdempotentButChangedPayloadIsRejected() {
        var plan = prepare("same"); assertEquals(plan, prepare("same"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("same", List.of("server"), List.of()), "owner"));
        var decision = decision(plan, "APPROVED"); service.review(plan, decision, "reviewer"); service.review(plan, decision, "reviewer");
        assertEquals(2, count("pm_agent_deployment")); assertEquals(1, count("pm_agent_release_review"));
        assertThrows(IllegalArgumentException.class, () -> service.review(plan, new AgentReleaseReviewRequest(decision.requestKey(), decision.expectedVersion(), decision.snapshotHash(), "REJECTED", "不同意见"), "reviewer"));
    }
    @Test void concurrentReviewCreatesOneSetOfRequests() throws Exception {
        var plan = prepare("same"); var decision = decision(plan, "APPROVED"); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return service.review(plan, decision, "reviewer"); });
            var b = pool.submit(() -> { start.await(); return service.review(plan, decision, "reviewer"); });
            start.countDown(); assertEquals(a.get(5, TimeUnit.SECONDS).get("id"), b.get(5, TimeUnit.SECONDS).get("id"));
        }
        assertEquals(1, count("pm_agent_release_review")); assertEquals(2, count("pm_agent_deployment"));
    }
    @Test void resourceConflictOnSecondStepRollsBackEntireHumanDecision() {
        var plan = prepare("plan"); var key = AgentDeploymentStore.resources(policies.binding("web")).getFirst();
        tx.executeWithoutResult(status -> deployments.acquire(key, "existing-request", now()));
        assertThrows(IllegalStateException.class, () -> approve(plan));
        assertEquals("WAITING_REVIEW", plans.find(plan).status()); assertEquals(0, count("pm_agent_release_review"));
        assertEquals(0, count("pm_agent_release_approval")); assertEquals(0, count("pm_agent_deployment")); assertEquals(1, count("pm_agent_release_resource"));
    }
    @Test void rejectionReturnsCommentToOriginalRunWithoutAnyOutbox() {
        var plan = prepare("plan"); service.review(plan, decision(plan, "REJECTED"), "reviewer");
        assertEquals("REJECTED", plans.find(plan).status()); assertEquals(0, count("pm_agent_deployment"));
        assertEquals("QUEUED", agents.findRun("run").status());
        assertTrue(agents.listEvents("run").stream().anyMatch(e -> e.kind().equals("HUMAN_INPUT") && e.payloadJson().contains("审核意见")));
    }
    @Test void rejectedReviewCannotWakeRunWithUnknownExternalHandle() {
        var plan = prepare("plan"); jdbc.update("UPDATE pm_agent_run SET external_handle='unknown' WHERE id='run'");
        service.review(plan, decision(plan, "REJECTED"), "reviewer"); assertEquals("WAITING_INPUT", agents.findRun("run").status());
    }
    @Test void permissionVersionAndMaterialHashAreCheckedBeforeApproval() {
        var plan = prepare("plan"); var decision = decision(plan, "APPROVED");
        permissions.remove("autonomy:release:review"); assertThrows(AccessDeniedException.class, () -> service.review(plan, decision, "reviewer"));
        permissions.add("autonomy:release:review");
        assertThrows(IllegalStateException.class, () -> service.review(plan, new AgentReleaseReviewRequest("other", 9, decision.snapshotHash(), "APPROVED", "意见"), "reviewer"));
        assertThrows(IllegalStateException.class, () -> service.review(plan, new AgentReleaseReviewRequest("other", 0, "f".repeat(64), "APPROVED", "意见"), "reviewer"));
        assertEquals(0, count("pm_agent_deployment"));
    }
    @Test void currentTargetScopeRevocationHidesPlanHistoryAndPreventsReview() {
        var plan = prepare("plan"); var decision = decision(plan, "APPROVED");
        var reduced = new AgentConfiguration(configuration.name(), configuration.instructions(), configuration.providerConfigId(), configuration.model(), List.of(), 50, 600, 1);
        jdbc.update("UPDATE pm_agent_definition SET configuration_json=? WHERE id='agent'", json.writeValueAsString(reduced));
        assertThrows(AccessDeniedException.class, () -> service.detail(plan, "reviewer"));
        assertThrows(AccessDeniedException.class, () -> service.review(plan, decision, "reviewer"));
    }
    @Test void disabledExecutionKeepsHistoryVisibleButMarksReviewUnavailable() {
        var plan = prepare("plan"); doThrow(new AccessDeniedException("disabled")).when(access).requireRunAuthorization(any());
        assertEquals(false, service.detail(plan, "reviewer").get("readyForReview"));
        assertThrows(AccessDeniedException.class, () -> approve(plan));
    }
    @Test void fullRepositoryCoverageAndAcyclicJobDependenciesAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("partial", List.of("server"), List.of()), "owner"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("cycle", List.of("server", "web"), List.of(new AgentReleasePlanRequest.Dependency("server", "web"))), "owner"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("foreign", List.of("server", "web"), List.of(new AgentReleasePlanRequest.Dependency("web", "unknown"))), "owner"));
        assertEquals(0, count("pm_agent_release_plan"));
    }
    @Test void sharedSystemRequiresOrderAndThenIsReservedForWholeGroup() {
        dependencies(List.of()); setBindings(List.of(binding("server", "server", "shared"), binding("web", "web", "shared")));
        assertThrows(IllegalArgumentException.class, () -> prepare("unordered"));
        var plan = (String) service.prepare(request("ordered", List.of("server", "web"), List.of(new AgentReleasePlanRequest.Dependency("web", "server"))), "owner").get("id");
        approve(plan); succeed(step(plan, "server")); assertTrue(count("pm_agent_release_resource") > 0);
        succeed(step(plan, "web")); assertEquals(0, count("pm_agent_release_resource")); assertEquals("SUCCEEDED", plans.find(plan).status());
    }
    @Test void directDispatcherClaimCannotSkipDependencyAndSuccessIsNotBusinessVerification() {
        var plan = prepare("plan"); approve(plan); var server = step(plan, "server"); var web = step(plan, "web");
        assertNull(outbox.claim(web)); assertEquals(List.of(server), deployments.due(now()));
        succeed(server); assertTrue(count("pm_agent_release_resource") > 0); assertEquals(List.of(web), deployments.due(now()));
        jdbc.update("UPDATE pm_agent_run SET status='WAITING_INPUT' WHERE id='run'");
        succeed(web); assertEquals("SUCCEEDED", plans.find(plan).status()); assertEquals(0, count("pm_agent_release_resource"));
        var proof = agents.findArtifact("run", "release:" + plan); assertEquals("DEPLOYMENT", proof.kind());
        assertFalse(json.readTree(proof.payloadJson()).path("businessVerified").asBoolean()); assertEquals("QUEUED", agents.findRun("run").status());
    }
    @Test void failedStepBlocksUnsentJobsAndReleasesOnlyKnownTerminalGroup() {
        var plan = prepare("plan"); approve(plan); var server = outbox.intent(outbox.claim(step(plan, "server")));
        outbox.finish(server, "REJECTED", null, null, null, null, "rejected");
        assertEquals("BLOCKED", deployments.find(step(plan, "web")).status()); assertEquals("FAILED", plans.find(plan).status());
        assertEquals(0, count("pm_agent_release_resource")); assertNull(outbox.claim(step(plan, "web")));
    }
    @Test void cancellationBeforeDispatchBlocksAllUnsentJobs() {
        var plan = prepare("plan"); approve(plan); jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'");
        assertNull(outbox.claim(step(plan, "server"))); assertEquals("CANCELLED", plans.find(plan).status());
        assertEquals(0, count("pm_agent_release_resource")); assertTrue(deployments.list("change").stream().allMatch(r -> r.status().equals("BLOCKED")));
    }
    @Test void cancellationAfterIntentRetainsUnknownRequestAndResourceLocks() {
        var plan = prepare("plan"); approve(plan); var server = outbox.intent(outbox.claim(step(plan, "server")));
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'");
        outbox.finish(server, "UNKNOWN", null, null, null, null, "lost response");
        assertEquals("NEEDS_ATTENTION", plans.find(plan).status()); assertEquals("BLOCKED", deployments.find(step(plan, "web")).status());
        assertTrue(count("pm_agent_release_resource") > 0); assertNull(outbox.claim(step(plan, "web")));
    }
    @Test void productionPreparationRequiresSeparateTestEnvironmentVerification() {
        var original = policies.current().bindings();
        setBindings(original.stream().map(b -> new AgentJenkinsPolicy.JobBinding(b.id(), b.name(), b.instanceId(), new AgentScope("BL000012", "PROD"), b.systemName(), "PROD_DEPLOY",
                b.jobSegments(), b.commitParameters(), b.correlationParameter(), b.manifestParameter(), b.environmentParameter(), b.fixedParameters(), b.receiptPath(), true)).toList());
        String plan = (String) service.prepare(new AgentReleasePlanRequest("prod", "change", "PROD_DEPLOY", List.of("server", "web"), List.of(), verification(new AgentScope("BL000012", "PROD"), 3600)), "owner").get("id");
        assertTrue(gate.snapshot(plans.find(plan)).blockers().contains("TEST_ENVIRONMENT_VERIFICATION_MISSING")); assertThrows(IllegalArgumentException.class, () -> approve(plan));
    }
    @Test void stageMismatchAndInvalidInputNeverCreatePlans() {
        assertThrows(IllegalArgumentException.class, () -> service.prepare(new AgentReleasePlanRequest("bad", "change", "PROD_DEPLOY", List.of("server", "web"), List.of()), "owner"));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("", List.of(), List.of()), "owner"));
        assertEquals(0, count("pm_agent_release_plan"));
    }
    @Test void corruptedFrozenMaterialsAreNotReadableOrExecutable() {
        var plan = prepare("plan"); jdbc.update("UPDATE pm_agent_release_plan SET snapshot_hash=? WHERE id=?", "f".repeat(64), plan);
        assertThrows(IllegalArgumentException.class, () -> service.detail(plan, "owner")); assertEquals(0, count("pm_agent_deployment"));
    }
    @Test void failedIndependentStepFencesAnotherWorkerStillInPreflight() {
        dependencies(List.of()); var plan = prepare("parallel"); approve(plan);
        var first = outbox.intent(outbox.claim(step(plan, "server"))); var probing = outbox.claim(step(plan, "web"));
        assertNotNull(probing);
        outbox.finish(first, "REJECTED", null, null, null, null, "rejected");
        assertThrows(AgentDeploymentStore.LeaseLost.class, () -> outbox.intent(probing));
        assertEquals("BLOCKED", deployments.find(probing.id()).status()); assertEquals("FAILED", plans.find(plan).status());
    }
    @Test void completeBundleJobsCanRunInExplicitOrderWithoutInventingCrossJobCycle() {
        var first = binding("bundleA", "bundleA", "shared"); var second = binding("bundleB", "bundleB", "shared");
        setBindings(List.of(first, second).stream().map(b -> new AgentJenkinsPolicy.JobBinding(b.id(), b.name(), b.instanceId(), b.scope(), b.systemName(), b.stage(),
                b.jobSegments(), Map.of("server", "SERVER_COMMIT", "web", "WEB_COMMIT"), b.correlationParameter(), b.manifestParameter(), b.environmentParameter(), b.fixedParameters(), b.receiptPath(), true)).toList());
        String plan = (String) service.prepare(request("bundle", List.of("bundleA", "bundleB"), List.of(new AgentReleasePlanRequest.Dependency("bundleB", "bundleA"))), "owner").get("id");
        approve(plan); assertNull(outbox.claim(step(plan, "bundleB"))); succeed(step(plan, "bundleA")); succeed(step(plan, "bundleB"));
        assertEquals("SUCCEEDED", plans.find(plan).status());
    }
    @Test void cancellationAfterClaimButBeforeIntentCannotSendTheJob() {
        var plan = prepare("plan"); approve(plan); var probing = outbox.claim(step(plan, "server"));
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'");
        assertThrows(IllegalStateException.class, () -> outbox.intent(probing));
        // intent 事务回滚后，由执行器保存明确未发送结果；不会写入 SUBMITTING。
        assertEquals("PREPARED", deployments.find(probing.id()).status());
        outbox.finish(probing, "BLOCKED", null, null, null, null, "cancelled before intent");
        assertEquals("CANCELLED", plans.find(plan).status()); assertEquals(0, count("pm_agent_release_resource"));
    }

    @Test void verificationStandardsAreImmutableAndBoundToRequestAndHumanSnapshot() {
        var id = prepare("standards");
        var snapshot = gate.snapshot(plans.find(id));
        assertEquals(2, snapshot.schemaVersion());
        assertEquals(verification(scope, 3600), snapshot.verificationPlan());
        var altered = new AgentReleasePlanRequest("standards", "change", "TEST_DEPLOY", List.of("server", "web"), List.of(), verification(scope, 7200));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(altered, "owner"));
        var next = (String) service.prepare(new AgentReleasePlanRequest("revised", "change", "TEST_DEPLOY", List.of("server", "web"), List.of(), altered.verificationPlan()), "owner").get("id");
        assertNotEquals(plans.find(id).snapshotHash(), plans.find(next).snapshotHash());
        var oldDecision = decision(id, "APPROVED");
        assertThrows(IllegalStateException.class, () -> service.review(next, oldDecision, "reviewer"));
        approve(id);
        assertEquals(plans.find(id).snapshotHash(), plans.reviews(id).getFirst().snapshotHash());
        assertEquals(false, service.detail(id, "reviewer").get("businessVerified"));
        var payload = (tools.jackson.databind.node.ObjectNode) json.readTree(plans.find(id).snapshotJson());
        ((tools.jackson.databind.node.ObjectNode) payload.at("/verificationPlan/checks/0")).put("observationSeconds", 1);
        jdbc.update("UPDATE pm_agent_release_plan SET snapshot_json=? WHERE id=?", json.writeValueAsString(payload), id);
        assertThrows(IllegalArgumentException.class, () -> gate.snapshot(plans.find(id)));
    }

    @Test void missingAndLegacyStandardsRemainVisibleButCannotBeApproved() {
        var missing = (String) service.prepare(new AgentReleasePlanRequest("missing", "change", "TEST_DEPLOY", List.of("server", "web"), List.of()), "owner").get("id");
        assertTrue(((List<?>) service.detail(missing, "owner").get("blockers")).contains("VERIFICATION_PLAN_MISSING"));
        assertEquals(false, service.detail(missing, "owner").get("readyForReview"));
        assertThrows(IllegalArgumentException.class, () -> approve(missing));
        var legacy = prepare("legacy"); legacy(legacy);
        assertEquals(1, gate.snapshot(plans.find(legacy)).schemaVersion());
        assertEquals(false, service.detail(legacy, "owner").get("readyForReview"));
        assertThrows(IllegalArgumentException.class, () -> approve(legacy));
        service.review(legacy, decision(legacy, "REJECTED"), "reviewer");
        assertEquals("REJECTED", plans.find(legacy).status());
        assertEquals(0, count("pm_agent_deployment"));
    }

    @Test void legacyApprovedPlanBlocksUnsentJobsAndPreservesInFlightRequest() {
        var id = prepare("legacy-approved"); approve(id);
        var sent = outbox.intent(outbox.claim(step(id, "server")));
        legacy(id);
        assertNull(outbox.claim(step(id, "web")));
        assertEquals("BLOCKED", deployments.find(step(id, "web")).status());
        assertEquals("SUBMITTING", deployments.find(sent.id()).status());
        assertEquals("STOPPING", plans.find(id).status());
        assertTrue(count("pm_agent_release_resource") > 0);
        outbox.finish(sent, "UNKNOWN", null, null, null, null, "原请求仍待核对");
        assertEquals("NEEDS_ATTENTION", plans.find(id).status());
        assertTrue(count("pm_agent_release_resource") > 0);
    }

    @Test void invalidVerificationScopeCoverageToolsAndTimingCannotCreatePlans() {
        var check = verification(scope, 3600).checks().getFirst();
        var invalid = new ArrayList<AgentReleaseVerificationPlan>();
        invalid.add(verification(new AgentScope("BL000012", "PROD"), 3600));
        invalid.add(new AgentReleaseVerificationPlan(List.of(check, check), "历史数据单独处理", "失败返回修复"));
        invalid.add(new AgentReleaseVerificationPlan(List.of(), "历史数据单独处理", "失败返回修复"));
        for (long[] timing : List.of(new long[]{0,60,300,2}, new long[]{3600,7200,300,2}, new long[]{3600,60,300,100}, new long[]{3600,60,0,2})) {
            invalid.add(new AgentReleaseVerificationPlan(List.of(new AgentReleaseVerificationPlan.Check("check", scope, check.toolCode(),
                    check.monitoringItem(), check.passCondition(), timing[0], timing[1], timing[2], timing[3], check.evidenceRequirements())), "历史数据单独处理", "失败返回修复"));
        }
        invalid.add(new AgentReleaseVerificationPlan(List.of(new AgentReleaseVerificationPlan.Check("check", scope, "code.apply",
                check.monitoringItem(), check.passCondition(), 3600, 60, 300, 2, check.evidenceRequirements())), "历史数据单独处理", "失败返回修复"));
        var tooLarge = new ArrayList<AgentReleaseVerificationPlan.Check>();
        for (int n = 0; n < 20; n++) tooLarge.add(new AgentReleaseVerificationPlan.Check("check-" + n, scope, check.toolCode(),
                check.monitoringItem(), "很长的标准".repeat(300), 3600, 60, 300, 2, check.evidenceRequirements()));
        invalid.add(new AgentReleaseVerificationPlan(tooLarge, "历史数据单独处理", "失败返回修复"));
        int i = 0;
        for (var value : invalid) {
            var request = new AgentReleasePlanRequest("invalid-" + i++, "change", "TEST_DEPLOY", List.of("server", "web"), List.of(), value);
            assertThrows(IllegalArgumentException.class, () -> service.prepare(request, "owner"));
            assertEquals(0, count("pm_agent_release_plan"));
        }
    }

    @Test void testCompletionTargetHidesProductionDespiteProductionAccess() {
        taskTargets(List.of(scope), true);
        var production = new AgentJenkinsPolicy.JobBinding("prod", "Production", "main", new AgentScope("BL000012", "PROD"), "production", "PROD_DEPLOY", List.of("prod"),
                Map.of("server", "SERVER", "web", "WEB"), "REQUEST", "MANIFEST", "ENV", Map.of(), "receipt.json", true);
        setBindings(List.of(binding("server", "server", "server"), binding("web", "web", "web"), production));
        assertEquals(Set.of("server", "web"), service.bindings("change", "owner").stream().map(value -> value.get("id")).collect(java.util.stream.Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(new AgentReleasePlanRequest("prod-attempt", "change", "PROD_DEPLOY", List.of("prod"), List.of(), verification(production.scope(), 3600)), "owner"));
        assertEquals(0, count("pm_agent_release_plan")); assertEquals(0, count("pm_agent_deployment"));
        approve(prepare("test-only")); assertEquals(2, count("pm_agent_deployment"));
    }
    @Test void productionOnlyCompletionRetainsAuthorizedTestPrerequisite() {
        taskTargets(List.of(new AgentScope("BL000012", "PROD")), true);
        assertEquals(2, service.bindings("change", "owner").size());
        var id = prepare("prerequisite"); assertEquals("TEST_DEPLOY", plans.find(id).stage());
        approve(id); assertEquals(2, count("pm_agent_deployment"));
    }
    @Test void testPrerequisiteDoesNotInventMissingTestAuthorization() {
        taskTargets(List.of(new AgentScope("BL000012", "PROD")), false);
        assertTrue(service.bindings("change", "owner").isEmpty());
        assertThrows(AccessDeniedException.class, () -> prepare("no-test-permission")); assertEquals(0, count("pm_agent_deployment"));
    }
    @Test void legacyMissingTargetsCannotApproveButHistoricalDraftRemainsVisible() {
        var id = prepare("before-target-check"); taskTargets(List.of(), true);
        assertEquals(false, service.detail(id, "owner").get("readyForReview"));
        assertThrows(IllegalArgumentException.class, () -> approve(id));
        assertThrows(IllegalArgumentException.class, () -> prepare("new"));
        assertEquals(0, count("pm_agent_release_review")); assertEquals(0, count("pm_agent_deployment"));
    }
    @Test void legacyMissingTargetsBlocksQueuedPlanWithoutLeavingResourcesStuck() {
        var id = prepare("before-target-check"); approve(id); taskTargets(List.of(), true);
        assertNull(outbox.claim(step(id, "server")));
        assertEquals("BLOCKED", deployments.find(step(id, "server")).status()); assertEquals("BLOCKED", deployments.find(step(id, "web")).status());
        assertEquals("FAILED", plans.find(id).status()); assertEquals(0, count("pm_agent_release_resource"));
    }
    @Test void targetViolationStopsUnsentStepsWhilePreservingOriginalInFlightRequest() {
        var id = prepare("before-target-check"); approve(id); var sent = outbox.intent(outbox.claim(step(id, "server")));
        taskTargets(List.of(), true); assertNull(outbox.claim(step(id, "web")));
        assertEquals("BLOCKED", deployments.find(step(id, "web")).status()); assertEquals("SUBMITTING", deployments.find(sent.id()).status());
        assertEquals("STOPPING", plans.find(id).status()); assertTrue(count("pm_agent_release_resource") > 0);
        outbox.finish(sent, "UNKNOWN", null, null, null, null, "继续核对原请求");
        assertEquals("NEEDS_ATTENTION", plans.find(id).status()); assertTrue(count("pm_agent_release_resource") > 0);
    }
    private void taskTargets(List<AgentScope> targets, boolean testAuthorized) {
        var old = json.readValue(agents.findRun("run").specificationJson(), AgentTaskSpecification.class);
        var scopes = testAuthorized ? old.scopes() : List.of(new AgentScope("BL000012", "PROD"));
        var next = new AgentTaskSpecification(old.objective(), old.context(), old.acceptanceCriteria(), old.expectedArtifacts(), scopes, targets);
        jdbc.update("UPDATE pm_agent_run SET specification_json=? WHERE id='run'", json.writeValueAsString(next));
    }

    private AgentReleaseVerificationPlan verification(AgentScope target, long window) {
        return new AgentReleaseVerificationPlan(List.of(new AgentReleaseVerificationPlan.Check("original-error", target, "workhub.logs",
                "原问题服务的 ERROR 日志与采集状态", "采集完整且没有原错误复发", window, 60, 300, 2,
                "保存实际查询、覆盖时间与数据时间，缺数据不能通过")), "历史异常数据另建待办并保留未处理状态", "保留证据并返回修复，不自动回滚");
    }
    private void legacy(String id) {
        var payload = (tools.jackson.databind.node.ObjectNode) json.readTree(plans.find(id).snapshotJson());
        payload.put("schemaVersion", 1); payload.remove("verificationPlan");
        String encoded = json.writeValueAsString(payload), hash = AgentCodePolicy.hash(encoded);
        jdbc.update("UPDATE pm_agent_release_plan SET snapshot_json=?,snapshot_hash=? WHERE id=?", encoded, hash, id);
        jdbc.update("UPDATE pm_agent_release_review SET snapshot_hash=? WHERE plan_id=?", hash, id);
    }

    private AgentReleasePlanRequest request(String key, List<String> bindings, List<AgentReleasePlanRequest.Dependency> dependencies) { return new AgentReleasePlanRequest(key, "change", "TEST_DEPLOY", bindings, dependencies, verification(scope, 3600)); }
    private String prepare(String key) { return (String) service.prepare(request(key, List.of("server", "web"), List.of()), "owner").get("id"); }
    private AgentReleaseReviewRequest decision(String id, String decision) { var plan = plans.find(id); return new AgentReleaseReviewRequest("review-1", plan.version(), plan.snapshotHash(), decision, "审核意见"); }
    private void approve(String id) { service.review(id, decision(id, "APPROVED"), "reviewer"); }
    private String step(String plan, String binding) { return gate.snapshot(plans.find(plan)).steps().stream().filter(s -> s.bindingId().equals(binding)).findFirst().orElseThrow().requestId(); }
    private void succeed(String request) { var row = outbox.intent(outbox.claim(request)); outbox.finish(row, "SUCCEEDED", 11L, 22L, "SUCCESS", "{\"fixture\":\"actual receipt supplied by dispatcher\"}", null); }
    private void ready(boolean ready) { var old = current.get(); current.set(new AgentChangeView(old.id(), old.runId(), old.taskId(), old.revision(), old.manifestHash(), old.createdAt(), true, ready, ready ? List.of() : List.of("PLATFORM_TEST_EVIDENCE_MISSING"), old.manifest())); }
    private void dependencies(List<AgentChangePlan.Dependency> edges) {
        var old = current.get(); var m = old.manifest(); var p = m.plan();
        current.set(new AgentChangeView(old.id(), old.runId(), old.taskId(), old.revision(), old.manifestHash(), old.createdAt(), old.current(), old.readyForReview(), old.blockers(),
                new AgentChangeManifest(m.schemaVersion(), m.runId(), m.taskId(), m.task(), new AgentChangePlan(p.summary(), p.impact(), p.acceptance(), p.rollback(), edges), m.caseReference(), m.repositories(), m.dependencyOrder())));
    }
    private AgentJenkinsPolicy.JobBinding binding(String id, String job, String system) {
        return new AgentJenkinsPolicy.JobBinding(id, id, "main", scope, system, "TEST_DEPLOY", List.of(job), Map.of(id, "COMMIT"), "REQUEST_ID", "MANIFEST", "ENV", Map.of(), "receipt.json", true);
    }
    private void setBindings(List<AgentJenkinsPolicy.JobBinding> bindings) { policyText.set(json.writeValueAsString(new AgentJenkinsPolicy.Policy(List.of(new AgentJenkinsPolicy.Instance("main", "Main", "https://jenkins.example.invalid/", "user", "jenkins", "token", true)), bindings))); }
    private AgentRepositoryChange repo(String id) { return new AgentRepositoryChange(id, id, scope, "workspace-" + id, 1, "p".repeat(64), "development", "c".repeat(40), "candidate", sha, List.of("src/Main.java"), "d".repeat(64), "diff", false); }
    private void artifact(String id, String kind, String provenance, String payload) { agents.insertArtifact(new AgentArtifactEntity(id, "run", id, kind, provenance, payload, AgentCodePolicy.hash(payload), now())); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    @SuppressWarnings("unchecked") private <T> T proxy(T target, TransactionInterceptor interceptor) { var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor); return (T) proxy.getProxy(); }
}
