package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.autonomy.AgentObservationEntity;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.auth.PermissionContextService;
import cn.aslight.workhub.service.autonomy.AgentObservationService;
import cn.aslight.workhub.service.system.SysConfigService;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 生产 SQL、事务、权限、工具和运行恢复；部署与监测数据是明确的本地夹具，不访问真实环境。 */
class AgentVerificationServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock();
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final String manifestHash = "a".repeat(64), sha = "b".repeat(40);
    private final Set<String> grants = new HashSet<>(Set.of("agent:manage", "agent:task:assign", "agent:code:deliver", "ops:monitor:view", "ops:accounting:view", "ops:system-alert:view"));
    private final AtomicInteger sequence = new AtomicInteger();
    private Connection connection;
    private JdbcTemplate jdbc;
    private TransactionInterceptor transactions;
    private AgentMapper agents;
    private AgentDeploymentMapper deployments;
    private AgentReleasePlanMapper plans;
    private AgentVerificationMapper verifications;
    private AutonomyMapper observations;
    private AgentRunStore runs;
    private AgentAccessService access;
    private AgentChangeService changes;
    private AgentVerificationService service;
    private AgentToolCatalog catalog;
    private AgentToolContext context;
    private AgentChangeView change;
    private AgentJenkinsPolicy.BindingSnapshot binding;
    private AgentReleasePlanSnapshot approved;
    private Instant deploymentTime;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        connection = source.getConnection(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_035107__add_agent_observations_and_cases.sql",
                "V20261104_054000__add_agent_deployment_outbox.sql", "V20261104_061500__add_agent_release_plans.sql", "V20261104_154000__add_agent_verification.sql"))
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + file));
        var config = new Configuration(); config.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(AgentMapper.class, AgentDeploymentMapper.class, AgentReleasePlanMapper.class, AgentVerificationMapper.class, AutonomyMapper.class)) config.addMapper(type);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
        var sessions = new SqlSessionTemplate(factory.getObject());
        agents = sessions.getMapper(AgentMapper.class); deployments = sessions.getMapper(AgentDeploymentMapper.class);
        plans = sessions.getMapper(AgentReleasePlanMapper.class); verifications = sessions.getMapper(AgentVerificationMapper.class); observations = sessions.getMapper(AutonomyMapper.class);
        transactions = new TransactionInterceptor(); transactions.setTransactionManager(new DataSourceTransactionManager(source)); transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var permissions = mock(PermissionContextService.class); var user = new SysUserEntity(); user.setId(1L); user.setUserName("owner");
        when(permissions.requireActiveUser("owner")).thenReturn(user); when(permissions.permissionCodes(1L)).thenAnswer(call -> List.copyOf(grants));
        var lines = mock(BusinessLineMapper.class); var line = new BusinessLineEntity(); line.setEnabled(true); line.setSupportedEnvironments("TEST,PROD");
        when(lines.findByCode("BL000012")).thenReturn(line);
        access = new AgentAccessService(permissions, lines, agents, json);
        var providers = mock(AiProviderConfigService.class); var provider = new AiProviderConfigEntity();
        provider.setId(1L); provider.setEnabled(true); provider.setChannelType("API"); provider.setDefaultModel("fixture");
        when(providers.getEnabledById(1L)).thenReturn(provider);
        var data = new AgentToolData(json);
        runs = proxy(new AgentRunStore(agents, json, access, data, providers, clock, AgentAcceptanceTestSupport.withoutCode(agents, access, json, clock)));
        changes = mock(AgentChangeService.class);
        service = proxy(new AgentVerificationService(verifications, plans, deployments, agents, observations, runs, access, changes,
                new AgentReleasePlanGate(plans, deployments, agents, json), new AgentVerificationEvidence(json), data, json, clock));
        var tools = new AgentVerificationTools(service, json);
        catalog = new AgentToolCatalog(List.of(tools.agentVerificationStartTool(), tools.agentVerificationRecordTool(), tools.agentVerificationFinishTool(), tools.agentVerificationStatusTool(), tools.agentVerificationWaitTool()));
        var codes = new ArrayList<>(catalog.all().stream().map(AgentTool::code).toList()); codes.addAll(AgentReleaseVerificationPlan.TOOL_CODES);
        var specification = new AgentTaskSpecification("修复", "夹具", "复查", List.of("REPORT"), List.of(scope, new AgentScope("BL000012", "PROD")), codes);
        var configuration = new AgentConfiguration("Agent", "复查", 1L, "fixture", specification.scopes(), codes, 200, 3600, 2);
        agents.insertAgent(new AgentDefinitionEntity("agent", "Agent", json.writeValueAsString(configuration), true, "owner", 0, now(), now()));
        agents.insertRun(new AgentRunEntity("run", "task", "agent", "fixture", "owner", json.writeValueAsString(configuration), json.writeValueAsString(specification),
                json.writeValueAsString(AgentProviderBinding.from(provider)), "QUEUED", 0, 0, false, null, null, null, null, null, 0, now(), now()));
        context = runs.context(runs.claim(2));
        var repo = new AgentRepositoryChange("server", "Server", scope, "workspace", 1, "p".repeat(64), "development", "c".repeat(40), "candidate", sha, List.of("src/Main.java"), "d".repeat(64), "diff", false);
        var manifest = new AgentChangeManifest(1, "run", "task", specification, new AgentChangePlan("fix", "API", "regression", "restore", List.of()), null, List.of(repo), List.of("server"));
        change = new AgentChangeView("change", "run", "task", 1, manifestHash, now(), true, true, List.of(), manifest);
        when(changes.detail(any(), eq("change"))).thenAnswer(call -> change);
        when(changes.detailForUser(eq("change"), anyString())).thenAnswer(call -> change);
        var instance = new AgentJenkinsPolicy.Instance("main", "Main", "https://jenkins.example.invalid/", "user", "jenkins", "token", true);
        var job = new AgentJenkinsPolicy.JobBinding("deploy", "Deploy", "main", scope, "workhub", "TEST_DEPLOY", List.of("test"), Map.of("server", "COMMIT"), "REQUEST", "MANIFEST", "ENV", Map.of(), "receipt.json", true);
        binding = new AgentJenkinsPolicy.BindingSnapshot(instance, job, "d".repeat(64));
        var parameters = new TreeMap<>(Map.of("COMMIT", sha, "REQUEST", "deploy", "MANIFEST", manifestHash, "ENV", "TEST"));
        var standards = standards("monitor.accounting.status");
        approved = new AgentReleasePlanSnapshot(2, change, List.of(), List.of(), List.of(new AgentReleasePlanSnapshot.Step("deploy", "approval", binding, parameters, List.of())), standards);
        String snapshot = json.writeValueAsString(approved);
        plans.insert(new AgentReleasePlanEntity("plan", "run", "change", "fixture", "d".repeat(64), manifestHash, "TEST_DEPLOY", snapshot, AgentCodePolicy.hash(snapshot), "SUCCEEDED", "owner", 0, now(), now()));
        plans.insertReview(new AgentReleaseReviewEntity("review", "plan", "fixture", "APPROVED", "owner", "已审核标准", AgentCodePolicy.hash(snapshot), 0, now()));
        deployments.insert(new AgentDeploymentEntity("deploy", "approval", "run", "change", manifestHash, json.writeValueAsString(binding), binding.hash(),
                json.writeValueAsString(parameters), AgentCodePolicy.hash(json.writeValueAsString(parameters)), "SUCCEEDED", 1L, 2L, "SUCCESS", null, null, null, null, now(), 0, now().minusMinutes(5), now()));
        deploymentTime = clock.instant().minusSeconds(60);
        String receipt = json.writeValueAsString(new AgentDeploymentReceipt.Verified(1, "WORKHUB_DEPLOYMENT", "deploy", manifestHash, "TEST_DEPLOY", scope, "test", 2L, "SUCCEEDED", Map.of("server", sha), deploymentTime.minusSeconds(10).toString(), deploymentTime.toString()));
        jdbc.update("UPDATE pm_agent_deployment SET build_number=2,receipt_json=? WHERE id='deploy'", receipt);
    }
    @AfterEach void close() throws Exception { connection.close(); }

    @Test void agentConclusionRequiresContinuousDistinctFreshEvidenceAndCreatesPersistedCertificate() {
        String id = start(); record(id, "one", accounting(clock.instant()), "PASS", false);
        assertEquals("OBSERVING", finish(id, "PASS", false).path("status").asText());
        clock.advance(10); record(id, "two", accounting(clock.instant()), "PASS", false);
        clock.advance(10); record(id, "three", accounting(clock.instant()), "PASS", false);
        var result = finish(id, "PASS", false);
        assertEquals("PASSED", result.path("status").asText());
        assertEquals(3, result.at("/checks/0/distinctSamples").asInt());
        assertEquals(20, result.at("/checks/0/observedSeconds").asLong());
        assertEquals(1, count("pm_agent_artifact"));
        var certificate = verifications.forArtifact(result.path("artifactId").asText()); assertNotNull(certificate);
        var artifact = agents.findArtifact("run", "verification:" + id);
        assertEquals(certificate.artifactHash(), artifact.contentHash());
        assertEquals("AI_ASSESSMENT", json.readTree(artifact.payloadJson()).path("conclusionSource").asText());
        assertEquals("TEST_ENVIRONMENT", json.readTree(artifact.payloadJson()).path("phase").asText());
        assertFalse(result.path("caseClosed").asBoolean());
        assertThrows(AgentTool.Rejected.class, () -> record(id, "later", accounting(clock.instant()), "PASS", false));
    }
    @Test void rereadingOldDataDoesNotAdvanceSampleCountOrWindow() {
        String id = start(); Instant old = clock.instant();
        record(id, "one", accounting(old), "PASS", false);
        clock.advance(10); record(id, "two", accounting(old), "PASS", false);
        clock.advance(10); record(id, "three", accounting(old), "PASS", false);
        var result = finish(id, "PASS", false);
        assertEquals(1, result.at("/checks/0/distinctSamples").asInt()); assertEquals("OBSERVING", result.path("status").asText());
        assertEquals(0, count("pm_agent_artifact"));
    }
    @Test void missedCadenceRestartsWindowAndKeepsEarlierEvidence() {
        String id = start(); record(id, "one", accounting(clock.instant()), "PASS", false);
        clock.advance(11); record(id, "two", accounting(clock.instant()), "PASS", false);
        clock.advance(10); record(id, "three", accounting(clock.instant()), "PASS", false);
        var result = finish(id, "PASS", false); assertEquals(2, result.at("/checks/0/distinctSamples").asInt());
        assertEquals("OBSERVING", result.path("status").asText()); assertEquals(3, count("pm_agent_verification_sample"));
        clock.advance(10); record(id, "four", accounting(clock.instant()), "PASS", false); assertEquals("PASSED", finish(id, "PASS", false).path("status").asText());
    }
    @Test void agentMayRejectEvenWhenPlatformEvidenceIsComplete() {
        String id = completeSamples(); var result = finish(id, "FAIL", false);
        assertEquals("FAILED", result.path("status").asText()); assertEquals("FAIL", result.at("/conclusion/assessment").asText());
        assertEquals(0, count("pm_agent_artifact"));
    }
    @Test void historicalDataPendingCannotBeOverruledByFinalPass() {
        String id = start();
        for (int index=0; index<3; index++) { if (index>0) clock.advance(10); record(id, "sample"+index, accounting(clock.instant()), "PASS", index==2); }
        var result = finish(id, "PASS", false);
        assertEquals("OBSERVING", result.path("status").asText()); assertTrue(result.at("/checks/0/constraints").toString().contains("HISTORICAL_DATA_PENDING"));
        assertEquals(0, count("pm_agent_artifact"));
    }
    @Test void finalPendingHistoryAndInconclusiveAssessmentKeepObservationOpen() {
        String id = completeSamples(); assertEquals("OBSERVING", finish(id, "PASS", true).path("status").asText());
        assertEquals("OBSERVING", finish(id, "INCONCLUSIVE", false).path("status").asText()); assertEquals(0, count("pm_agent_artifact"));
    }
    @Test void elapsedWallTimeCannotReplaceFreshFinalMonitoringData() {
        String id = completeSamples(); clock.advance(31);
        var result = finish(id, "PASS", false);
        assertEquals("OBSERVING", result.path("status").asText()); assertTrue(result.at("/checks/0/constraints").toString().contains("LATEST_DATA_STALE"));
    }
    @Test void preDeploymentAndFutureDataDoNotQualify() {
        String id = start();
        var result = record(id, "old", accounting(deploymentTime.minusSeconds(1)), "PASS", false);
        assertTrue(result.at("/checks/0/constraints").toString().contains("DATA_OUTSIDE_DEPLOYED_WINDOW"));
        result = record(id, "future", accounting(clock.instant().plusSeconds(1)), "PASS", false);
        assertEquals(0, result.at("/checks/0/distinctSamples").asInt());
        clock.advance(2); assertDoesNotThrow(() -> status(id));
    }
    @Test void differentSourceScopeAgentRunHashAndScalarReferencesAreRejected() {
        String id = start();
        String wrong = accounting(clock.instant()); jdbc.update("UPDATE pm_agent_observation SET environment_code='PROD' WHERE id=?", wrong);
        String wrongScope = wrong; assertThrows(AgentTool.Rejected.class, () -> record(id, "wrong-scope", wrongScope, "PASS", false));
        wrong = accounting(clock.instant()); jdbc.update("UPDATE pm_agent_observation SET agent_id='other' WHERE id=?", wrong);
        String agent = wrong; assertThrows(AgentTool.Rejected.class, () -> record(id, "wrong-agent", agent, "PASS", false));
        String foreign = accounting(clock.instant()); jdbc.update("UPDATE pm_agent_observation SET run_id='other' WHERE id=?", foreign);
        assertThrows(AgentTool.Rejected.class, () -> record(id, "wrong-run", foreign, "PASS", false));
        String corrupt = accounting(clock.instant()); jdbc.update("UPDATE pm_agent_observation SET content_hash='broken' WHERE id=?", corrupt);
        assertThrows(AgentTool.Rejected.class, () -> record(id, "corrupt", corrupt, "PASS", false));
        var args = recordArgs(id, "scalar", accounting(clock.instant()), "PASS", false); ((ObjectNode) args.at("/evidence/0")).put("pointer", "/data/0/status");
        assertThrows(AgentTool.Rejected.class, () -> invoke("verification.record", args)); assertEquals(0, count("pm_agent_verification_sample"));
    }
    @Test void latestFailedOrIncompleteSampleResetsEarlierHealthyWindow() {
        String id = completeSamples(); clock.advance(5); var result = record(id, "failure", accounting(clock.instant()), "FAIL", false);
        assertEquals(0, result.at("/checks/0/distinctSamples").asInt()); assertEquals("OBSERVING", finish(id, "PASS", false).path("status").asText());
    }
    @Test void replayedStartAndSampleAreIdempotentButChangedRequestsAreRejected() {
        String id = start(); assertEquals(id, start()); assertEquals(1, count("pm_agent_verification"));
        String observation = accounting(clock.instant()); record(id, "sample", observation, "PASS", false); record(id, "sample", observation, "PASS", false);
        assertEquals(1, count("pm_agent_verification_sample"));
        assertThrows(AgentTool.Rejected.class, () -> record(id, "sample", observation, "FAIL", false));
    }
    @Test void concurrentSameToolCallCommitsOnlyOneSampleAndReceipt() throws Exception {
        String id = start(); var args = recordArgs(id, "sample", accounting(clock.instant()), "PASS", false);
        String key = begin("verification.record", args);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> catalog.require("verification.record").execute(context, args));
            var b = pool.submit(() -> catalog.require("verification.record").execute(context, args));
            assertEquals(a.get(5, TimeUnit.SECONDS).toString(), b.get(5, TimeUnit.SECONDS).toString());
        }
        runs.endTool("run", context.ownerToken(), key, Map.of("fixture", true));
        assertEquals(1, count("pm_agent_verification_sample")); assertEquals(2, count("pm_agent_verification_call"));
    }
    @Test void failedReceiptPersistenceRollsBackSampleAndSessionVersion() {
        String id = start(); long version = verifications.find(id).version();
        jdbc.execute("ALTER TABLE pm_agent_verification_call ADD CONSTRAINT fail_fixture CHECK(tool_code <> 'verification.record')");
        assertThrows(RuntimeException.class, () -> record(id, "sample", accounting(clock.instant()), "PASS", false));
        assertEquals(0, count("pm_agent_verification_sample")); assertEquals(version, verifications.find(id).version());
    }
    @Test void startRejectsIncompleteDeploymentWrongCommitAndUnapprovedStandards() {
        jdbc.update("UPDATE pm_agent_release_plan SET status='EXECUTING' WHERE id='plan'");
        assertThrows(AgentTool.Rejected.class, this::start);
        jdbc.update("UPDATE pm_agent_release_plan SET status='SUCCEEDED' WHERE id='plan'");
        var receipt = (ObjectNode) json.readTree(deployments.find("deploy").receiptJson()); receipt.set("deployedCommits", json.valueToTree(Map.of("server", "c".repeat(40))));
        jdbc.update("UPDATE pm_agent_deployment SET receipt_json=? WHERE id='deploy'", receipt.toString());
        assertThrows(AgentTool.Rejected.class, this::start); assertEquals(0, count("pm_agent_verification"));
    }
    @Test void reviewAndPlanHashesMustRemainConsistent() {
        jdbc.update("UPDATE pm_agent_release_review SET snapshot_hash='different' WHERE id='review'");
        assertThrows(AgentTool.Rejected.class, this::start); assertEquals(0, count("pm_agent_verification"));
    }
    @Test void finalCertificateRequiresUnchangedDeploymentAndCandidate() {
        String id = completeSamples(); jdbc.update("UPDATE pm_agent_deployment SET status='UNKNOWN' WHERE id='deploy'");
        assertThrows(AgentTool.Rejected.class, () -> finish(id, "PASS", false));
        jdbc.update("UPDATE pm_agent_deployment SET status='SUCCEEDED' WHERE id='deploy'");
        change = new AgentChangeView(change.id(), change.runId(), change.taskId(), change.revision(), change.manifestHash(), change.createdAt(), false, false, List.of("CHANGED"), change.manifest());
        assertThrows(AgentTool.Rejected.class, () -> finish(id, "PASS", false)); assertEquals(0, count("pm_agent_artifact"));
    }
    @Test void currentSourcePermissionRevocationBlocksReadWriteAndWake() {
        String id = start(); waitFor(id, 5); pause(); grants.remove("ops:accounting:view"); clock.advance(5);
        assertFalse(service.wake(id)); assertNull(verifications.find(id).nextCheckAt()); assertEquals("WAITING_INPUT", runs.get("run").status());
        assertTrue(runs.events("run").stream().anyMatch(event -> event.kind().equals("VERIFICATION_WAIT_BLOCKED")));
    }
    @Test void registeredWaitWakesOriginalRunOnlyOnceWithoutResettingBudget() throws Exception {
        String id = start(); record(id, "sample", accounting(clock.instant()), "PASS", false); waitFor(id, 10); pause();
        var before = runs.get("run"); clock.advance(10); assertEquals(List.of(id), service.due());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.wake(id)); var b = pool.submit(() -> service.wake(id));
            assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
        }
        var after = runs.get("run"); assertEquals("QUEUED", after.status());
        assertEquals(before.stepCount(), after.stepCount()); assertEquals(before.elapsedMillis(), after.elapsedMillis());
        assertEquals(1, count("pm_agent_verification_sample")); assertNull(verifications.find(id).nextCheckAt());
    }
    @Test void waitsDoNotBypassUnknownHandlesActiveOwnersCancellationOrBudget() {
        String id = start(); waitFor(id, 1); pause(); clock.advance(1);
        jdbc.update("UPDATE pm_agent_run SET external_handle='unknown' WHERE id='run'"); assertFalse(service.wake(id)); assertNotNull(verifications.find(id).nextCheckAt());
        jdbc.update("UPDATE pm_agent_run SET external_handle=NULL,owner_token='unknown-owner' WHERE id='run'"); assertFalse(service.wake(id));
        jdbc.update("UPDATE pm_agent_run SET owner_token=NULL,step_count=200 WHERE id='run'"); assertFalse(service.wake(id));
        assertNull(verifications.find(id).nextCheckAt()); assertEquals("WAITING_INPUT", runs.get("run").status());
    }
    @Test void cancellationClearsScheduledWaitWithoutRequeueing() {
        String id = start(); waitFor(id, 1); pause(); clock.advance(1);
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'");
        assertFalse(service.wake(id)); assertNull(verifications.find(id).nextCheckAt()); assertEquals("WAITING_INPUT", runs.get("run").status());
    }
    @Test void committedApiMutationIsRecoveredWithoutRepeatingTheProducer() {
        var args = json.valueToTree(Map.of("planId", "plan")); String key = begin("verification.start", args);
        var result = catalog.require("verification.start").execute(context, args); expire(); runs.recoverExpired("run");
        assertEquals("QUEUED", runs.get("run").status()); assertNull(runs.get("run").externalHandle());
        var recovered = json.readTree(runs.event("run", key + ":result").payloadJson());
        assertEquals(json.writeValueAsString(result), json.writeValueAsString(recovered.path("value")));
        assertEquals(1, count("pm_agent_verification")); assertEquals(1, count("pm_agent_verification_call"));
    }
    @Test void nativeRecoveryPreservesUnknownProcessUntilActualStopReceipt() {
        String attempt = "cli:fixture"; runs.beginNative("run", context.ownerToken(), attempt, "fixture");
        var args = json.valueToTree(Map.of("planId", "plan")); String key = attempt + ":rpc:1";
        runs.beginTool("run", context.ownerToken(), key, catalog.require("verification.start"), args, true);
        catalog.require("verification.start").execute(context, args); expire(); runs.recoverExpired("run");
        assertEquals("WAITING_RESOURCE", runs.get("run").status()); assertNotNull(runs.get("run").ownerToken());
        assertNotNull(runs.event("run", key + ":result")); assertFalse(json.readTree(runs.get("run").externalHandle()).has("activeTool"));
        runs.endNative("run", context.ownerToken(), attempt, Map.of("processTreeStopped", true, "exitCode", 0, "result", Map.of("type", "NEEDS_INPUT", "summary", "等待")));
        runs.recoverExpired("run"); assertEquals("QUEUED", runs.get("run").status()); assertEquals(1, count("pm_agent_verification"));
    }
    @Test void missingOrMismatchedCommittedReceiptNeverProvesMutationCompleted() {
        var args = json.valueToTree(Map.of("planId", "plan")); String key = begin("verification.start", args);
        verifications.insertCall(new AgentVerificationCall("run", key, "verification.finish", "hash", "{}", now()));
        expire(); runs.recoverExpired("run"); assertNotNull(runs.get("run").externalHandle()); assertNull(runs.event("run", key + ":result"));
    }
    @Test void emptyAlertsAndFilteredLogsCannotCertifyCollectionHealth() {
        for (String tool : List.of("monitor.system.alerts", "workhub.logs", "monitor.business.detail")) {
            var evidence = new AgentVerificationEvidence(json); String id = observation(tool, json.createObjectNode(), clock.instant());
            var fact = evidence.inspect(observations.observation(id), context, standards(tool).checks().getFirst(), "/data", deploymentTime, clock.instant());
            assertTrue(fact.problems().contains(tool.equals("monitor.business.detail") ? "EXECUTION_SCOPE_UNVERIFIED" : "COLLECTION_COVERAGE_UNPROVEN"));
        }
    }

    @Test void accountingMissingExecutionProofIncompleteRunAndSkippedRulesRemainInconclusive() {
        String id = start();
        for (String scenario : List.of("missing-proof", "incomplete", "skipped", "old-execution")) {
            var content = accountingData(clock.instant()); var run = (ObjectNode) content.at("/0/latestRun");
            switch (scenario) {
                case "missing-proof" -> run.remove("executionEvidence");
                case "incomplete" -> run.put("status", "RUNNING");
                case "skipped" -> run.put("skippedCount", 1);
                case "old-execution" -> run.put("startedAt", LocalDateTime.ofInstant(deploymentTime.minusSeconds(1), ZoneOffset.UTC).toString());
            }
            var result = record(id, scenario, observation("monitor.accounting.status", content, clock.instant()), "PASS", false);
            assertEquals(0, result.at("/checks/0/distinctSamples").asInt(), scenario);
            assertFalse(result.at("/checks/0/constraints").isEmpty(), scenario);
        }
        assertEquals(0, count("pm_agent_artifact"));
    }
    @Test void accountingRuleDefinitionOrTargetChangeRestartsObservationWindow() {
        String id = start(); record(id, "first", accounting(clock.instant()), "PASS", false);
        clock.advance(10); var content = accountingData(clock.instant());
        ((ObjectNode) content.at("/0/latestRun/executionEvidence/snapshot")).put("targetBindingHash", "c".repeat(64));
        var result = record(id, "changed", observation("monitor.accounting.status", content, clock.instant()), "PASS", false);
        assertEquals(1, result.at("/checks/0/distinctSamples").asInt()); assertEquals(0, result.at("/checks/0/observedSeconds").asInt());
    }
    @Test void businessSummaryWithoutExecutionScopeCannotCertifyEvenWithFreshStatus() {
        var content = json.valueToTree(List.of(Map.of("id", 1, "type", "XXL_JOB", "enabled", true, "dataState", "HAS_RESULT", "status", "HEALTHY",
                "lastCheckedAt", now(), "configurationUpdatedAt", now())));
        String observation = observation("monitor.business.status", content, clock.instant());
        var fact = new AgentVerificationEvidence(json).inspect(observations.observation(observation), context,
                standards("monitor.business.status").checks().getFirst(), "/data/0", deploymentTime, clock.instant());
        assertTrue(fact.problems().contains("EXECUTION_SCOPE_UNVERIFIED"));
    }

    @Test void multipleSourcesRequireTheirCommonObservationWindow() {
        String id = start();
        for (int index=0; index<3; index++) {
            Instant earlier = clock.instant(); clock.advance(10);
            String a = accounting(earlier); var content = accountingData(clock.instant());
            ((ObjectNode) content.at("/0/latestRun")).put("configId", 2);
            ((ObjectNode) content.at("/0/latestRun/executionEvidence/snapshot/configuration")).put("configId", 2);
            String b = observation("monitor.accounting.status", content, clock.instant());
            var args = recordArgs(id, "multi"+index, a, "PASS", false);
            args.set("evidence", json.valueToTree(List.of(Map.of("observationId", a, "pointer", "/data/0"), Map.of("observationId", b, "pointer", "/data/0"))));
            invoke("verification.record", args);
        }
        var result = finish(id, "PASS", false);
        assertEquals(3, result.at("/checks/0/distinctSamples").asInt()); assertEquals(10, result.at("/checks/0/observedSeconds").asLong());
        assertEquals("OBSERVING", result.path("status").asText());
    }
    @Test void humanHistoryRequiresCurrentSourcePermissionAndOriginalScopes() {
        String id = start(); grants.addAll(Set.of("agent:task:view", "agent:code:use"));
        assertEquals(id, service.detailForUser("plan", "owner").path("id").asText());
        pause(); assertEquals(id, service.detailForUser("plan", "owner").path("id").asText());
        grants.remove("ops:accounting:view");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.detailForUser("plan", "owner"));
    }
    @Test void longReasonsKeepToolResultsUsableAndPreserveFullAuditText() {
        String id = start(); var args = recordArgs(id, "long", accounting(clock.instant()), "PASS", false);
        String reason = "实际证据".repeat(900); args.put("reason", reason);
        var result = invoke("verification.record", args);
        assertEquals(id, result.path("id").asText()); assertTrue(result.at("/checks/0/reasonTruncated").asBoolean());
        assertEquals(reason, verifications.samples(id, "health").getFirst().reason()); assertFalse(result.path("truncated").asBoolean());
    }

    @Test void dispatchScanExcludesUnresolvedOwnersAndCallsWithoutHidingReadyWaits() {
        String id = start(); waitFor(id, 1); clock.advance(1);
        assertTrue(service.due().isEmpty()); pause(); assertEquals(List.of(id), service.due());
        jdbc.update("UPDATE pm_agent_run SET external_handle='unknown' WHERE id='run'"); assertTrue(service.due().isEmpty());
        jdbc.update("UPDATE pm_agent_run SET external_handle=NULL,owner_token='old' WHERE id='run'"); assertTrue(service.due().isEmpty());
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'"); assertEquals(List.of(id), service.due());
    }
    @Test void historicalDataFlagMustBeAnExplicitBoolean() {
        String id = start(); var args = recordArgs(id, "flag", accounting(clock.instant()), "PASS", false);
        args.put("historicalDataPending", "false"); assertThrows(AgentTool.Rejected.class, () -> invoke("verification.record", args));
        args.remove("historicalDataPending"); assertThrows(AgentTool.Rejected.class, () -> invoke("verification.record", args));
        assertEquals(0, count("pm_agent_verification_sample"));
    }

    @Test void productionMaterialRequiresPersistedCertificateAndUnchangedWholeTestDeployment() {
        String id = completeSamples(); finish(id, "PASS", false);
        agents.insertArtifact(new AgentArtifactEntity("test-proof", "run", "test-proof", "TEST_REPORT", "PLATFORM_EXECUTION", "{}", AgentCodePolicy.hash("{}"), now()));
        var prod = new AgentJenkinsPolicy.JobBinding("prod", "Production", "main", new AgentScope("BL000012", "PROD"), "workhub",
                "PROD_DEPLOY", List.of("prod"), Map.of("server", "COMMIT"), "REQUEST", "MANIFEST", "ENV", Map.of(), "receipt.json", true);
        var configs = mock(SysConfigService.class);
        when(configs.findPlainValue("agent.jenkins", "policy")).thenReturn(json.writeValueAsString(new AgentJenkinsPolicy.Policy(List.of(binding.instance()), List.of(prod))));
        var gate = new AgentDeploymentApproval(deployments, agents, changes, runs, access, new AgentJenkinsPolicy(configs, json), verifications, json);
        assertTrue(gate.material("change", "prod", "production-draft", "owner").blockers().isEmpty());
        jdbc.update("UPDATE pm_agent_deployment SET status='UNKNOWN' WHERE id='deploy'");
        assertTrue(gate.material("change", "prod", "production-draft", "owner").blockers().contains("TEST_ENVIRONMENT_VERIFICATION_MISSING"));
        jdbc.update("UPDATE pm_agent_deployment SET status='SUCCEEDED' WHERE id='deploy'");
        jdbc.update("UPDATE pm_agent_verification SET artifact_hash='changed' WHERE id=?", id);
        assertTrue(gate.material("change", "prod", "production-draft", "owner").blockers().contains("TEST_ENVIRONMENT_VERIFICATION_MISSING"));
    }

    @Test void businessDetailProducerSuppliesFreshCompleteEvidenceForRealVerificationWindow() {
        useBusinessDetail(); String id = start();
        for (int index=0;index<3;index++) {
            if(index>0) clock.advance(10);
            recordBusiness(id,"business"+index,clock.instant());
        }
        var result=finish(id,"PASS",false);
        assertEquals("PASSED",result.path("status").asText()); assertEquals(3,result.at("/checks/0/distinctSamples").asInt());
        assertEquals(20,result.at("/checks/0/observedSeconds").asLong());
    }
    @Test void refreshingBusinessQueryWithoutNewActivityDoesNotInventNewSamples() {
        useBusinessDetail(); String id=start(); Instant old=clock.instant();
        for(int index=0;index<3;index++) { if(index>0) clock.advance(10); recordBusiness(id,"same"+index,old); }
        var result=finish(id,"PASS",false);
        assertEquals("OBSERVING",result.path("status").asText()); assertEquals(1,result.at("/checks/0/distinctSamples").asInt());
    }
    @Test void noBusinessActivityCannotBeReportedAsRecovery() {
        useBusinessDetail(); String id=start(); var result=recordBusiness(id,"empty",null);
        assertEquals(0,result.at("/checks/0/distinctSamples").asInt()); assertTrue(result.at("/checks/0/constraints").toString().contains("NO_BUSINESS_ACTIVITY"));
    }
    @Test void existingSamplesWithoutLogIntervalFieldsRemainReadable() {
        String id=completeSamples(); useLegacySampleEncoding(id);
        assertEquals("PASSED",finish(id,"PASS",false).path("status").asText());
    }
    @Test void previousUnprovenTruncatedLogSamplesKeepTheirOriginalConstraints() {
        useSource("workhub.logs"); String id=start();
        String observed=observation("workhub.logs",json.valueToTree(Map.of("truncated",true,"items",List.of())),clock.instant());
        var args=recordArgs(id,"old-logs",observed,"PASS",false); ((ObjectNode)args.at("/evidence/0")).put("pointer","/data");
        invoke("verification.record",args); useLegacySampleEncoding(id);
        var result=status(id); assertTrue(result.at("/checks/0/constraints").toString().contains("TRUNCATED_EVIDENCE"));
    }
    private void useLegacySampleEncoding(String id) {
        for(var sample:verifications.samples(id,"health")) {
            var facts=json.readTree(sample.evidenceJson());
            for(var fact:facts) { ((ObjectNode)fact).remove("coverageFrom"); ((ObjectNode)fact).remove("coverageThrough"); }
            String old=json.writeValueAsString(facts);
            jdbc.update("UPDATE pm_agent_verification_sample SET evidence_json=?,evidence_hash=? WHERE id=?",old,AgentCodePolicy.hash(old),sample.id());
        }
    }

    @Test void completeLogQueriesWithActivityForEveryServiceCanCertifyApprovedWindow() {
        useSource("workhub.logs"); String id=start();
        for(int i=0;i<3;i++) { if(i>0) clock.advance(10); recordLog(id,"log"+i,clock.instant(),clock.instant().minusSeconds(60),false,false,null); }
        var result=finish(id,"PASS",false);
        assertEquals("PASSED",result.path("status").asText()); assertEquals(20,result.at("/checks/0/observedSeconds").asLong());
    }
    @Test void logQueriesWithUnobservedTimeGapsCannotBeCombinedIntoContinuousWindow() {
        useSource("workhub.logs"); String id=start();
        for(int i=0;i<3;i++) { if(i>0) clock.advance(10); recordLog(id,"gap"+i,clock.instant(),clock.instant().minusSeconds(1),false,false,null); }
        var result=finish(id,"PASS",false);
        assertEquals("OBSERVING",result.path("status").asText()); assertEquals(1,result.at("/checks/0/distinctSamples").asInt());
    }
    @Test void refreshingLogSnapshotWithoutNewActivityDoesNotCreateSamples() {
        useSource("workhub.logs"); String id=start(); Instant old=clock.instant();
        for(int i=0;i<3;i++) { if(i>0) clock.advance(10); recordLog(id,"oldlog"+i,old,clock.instant().minusSeconds(60),false,false,null); }
        assertEquals(1,finish(id,"PASS",false).at("/checks/0/distinctSamples").asInt());
    }
    @Test void oneSilentOrStaleServiceAndPartialLogResultsCannotCertifyRecovery() {
        useSource("workhub.logs"); String id=start();
        var silent=recordLog(id,"silent",clock.instant(),clock.instant().minusSeconds(60),true,false,null);
        assertTrue(silent.at("/checks/0/constraints").toString().contains("NO_SERVICE_ACTIVITY"));
        var stale=recordLog(id,"stale",clock.instant().minusSeconds(40),clock.instant().minusSeconds(60),false,false,null);
        assertTrue(stale.at("/checks/0/constraints").toString().contains("DATA_STALE_AT_QUERY"));
        var partial=recordLog(id,"partial",clock.instant(),clock.instant().minusSeconds(60),false,true,null);
        assertTrue(partial.at("/checks/0/constraints").toString().contains("QUERY_RESULTS_INCOMPLETE"));
        assertEquals(0,partial.at("/checks/0/distinctSamples").asInt());
    }
    @Test void changingLogFiltersRestartsObservationWindow() {
        useSource("workhub.logs"); String id=start();
        for(int i=0;i<3;i++) { if(i>0) clock.advance(10); recordLog(id,"filter"+i,clock.instant(),clock.instant().minusSeconds(60),false,false,i==0?null:"a".repeat(64)); }
        assertEquals(2,finish(id,"PASS",false).at("/checks/0/distinctSamples").asInt());
    }
    private JsonNode recordLog(String id,String request,Instant activity,Instant from,boolean silent,boolean truncated,String phraseHash) {
        var definition=new cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.Definition(1L,"BL000012","TEST","SELECTED",List.of("workhub-*"),List.of("a","b"),List.of("ERROR","WARN"),phraseHash,null,null,30);
        var activities=List.of(new cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.Activity("a",1,clock.instant(),"a".repeat(64)),
                new cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.Activity("b",silent?0:1,silent?null:activity,silent?null:"b".repeat(64)));
        var proof=new cn.aslight.workhub.model.ops.BusinessLogQueryEvidence(1,UUID.randomUUID().toString(),definition,AgentCodePolicy.hash(json.writeValueAsString(definition)),"d".repeat(64),"e".repeat(64),clock.instant(),clock.instant(),from,clock.instant(),!truncated,activities);
        var result=new cn.aslight.workhub.model.ops.BusinessLogSearchResponse("BL000012","TEST",definition.indexPatterns(),definition.serviceNames(),definition.levels(),from,clock.instant(),30,0,truncated,true,List.of(),proof);
        String observation=observation("workhub.logs",new AgentToolData(json).sanitize(result),clock.instant());
        var args=recordArgs(id,request,observation,"PASS",false); ((ObjectNode)args.at("/evidence/0")).put("pointer","/data");
        return invoke("verification.record",args);
    }

    private void useBusinessDetail() { useSource("monitor.business.detail"); }
    private void useSource(String tool) {
        approved=new AgentReleasePlanSnapshot(2,change,List.of(),List.of(),approved.steps(),standards(tool));
        String snapshot=json.writeValueAsString(approved), hash=AgentCodePolicy.hash(snapshot);
        jdbc.update("UPDATE pm_agent_release_plan SET snapshot_json=?,snapshot_hash=? WHERE id='plan'",snapshot,hash);
        jdbc.update("UPDATE pm_agent_release_review SET snapshot_hash=? WHERE id='review'",hash);
    }
    private JsonNode recordBusiness(String verificationId,String request,Instant activityAt) {
        var monitor=new cn.aslight.workhub.model.ops.OpsMonitorEntity();
        monitor.setId(1L); monitor.setEnabled(true); monitor.setMonitorType("XXL_JOB"); monitor.setMonitorKey("workhub-test");
        monitor.setBusinessLineCode("BL000012"); monitor.setEnvironmentCode("TEST"); monitor.setXxlJobDatabaseName("fixture"); monitor.setName("任务监测");
        var query=new cn.aslight.workhub.service.ops.XxlJobDatabaseQueryClient() {
            public List<Map<String,Object>> runRows(String line,String environment,String sql) { throw new AssertionError("必须使用固定目标批次"); }
            public VerifiedBatch runVerifiedBatch(String line,String environment,List<String> sqls) {
                Map<String,Object> statistics=new LinkedHashMap<>(); statistics.put("failedJobTotal",0); statistics.put("activityCount",activityAt==null?0:1);
                statistics.put("latestActivityAt",activityAt==null?null:LocalDateTime.ofInstant(activityAt,ZoneId.of("Asia/Shanghai")));
                statistics.put("logId",null);
                return new VerifiedBatch("f".repeat(64),clock.instant().minusMillis(1),clock.instant(),List.of(List.of(statistics)));
            }
        };
        var date=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        var result=new cn.aslight.workhub.service.ops.XxlJobMonitorCollector(query).collectLogs(monitor,date.minusDays(1),date,1,20,null,null,"FAILED");
        String observation=observation("monitor.business.detail",new AgentToolData(json).sanitize(result),clock.instant());
        var args=recordArgs(verificationId,request,observation,"PASS",false);
        ((ObjectNode)args.at("/evidence/0")).put("pointer","/data");
        return invoke("verification.record",args);
    }

    private AgentReleaseVerificationPlan standards(String tool) {
        return new AgentReleaseVerificationPlan(List.of(new AgentReleaseVerificationPlan.Check("health", scope, tool, "实际监测项", "Agent 按原问题判断业务恢复",
                20, 10, 30, 3, "完整真实监测记录")), "Agent 核对历史差异，无待处理数据后通过", "保留证据并报告");
    }
    private String completeSamples() {
        String id = start();
        for (int index=0; index<3; index++) { if (index>0) clock.advance(10); record(id, "sample"+index, accounting(clock.instant()), "PASS", false); }
        return id;
    }
    private String start() { return invoke("verification.start", json.valueToTree(Map.of("planId", "plan"))).path("id").asText(); }
    private JsonNode status(String id) { return invoke("verification.status", json.valueToTree(Map.of("verificationId", id))); }
    private JsonNode finish(String id, String assessment, boolean pending) {
        return invoke("verification.finish", json.valueToTree(Map.of("verificationId", id, "assessment", assessment, "reason", "根据获批标准与真实监测证据判断", "historicalDataPending", pending)));
    }
    private JsonNode record(String id, String request, String observation, String assessment, boolean pending) {
        return invoke("verification.record", recordArgs(id, request, observation, assessment, pending));
    }
    private ObjectNode recordArgs(String id, String request, String observation, String assessment, boolean pending) {
        return (ObjectNode) json.valueToTree(Map.of("verificationId", id, "requestKey", request, "checkId", "health", "assessment", assessment,
                "reason", "已分析监测内容和历史数据", "historicalDataPending", pending, "evidence", List.of(Map.of("observationId", observation, "pointer", "/data/0"))));
    }
    private void waitFor(String id, long seconds) { invoke("verification.wait", json.valueToTree(Map.of("verificationId", id, "seconds", seconds))); }
    private void pause() { runs.finish("run", context.ownerToken(), "WAITING_INPUT", "等待下一次复查", null); }
    private String accounting(Instant at) {
        return observation("monitor.accounting.status", accountingData(at), clock.instant());
    }
    private JsonNode accountingData(Instant at) {
        var configuration = Map.of("configId", 1, "businessLineCode", "BL000012", "environmentCode", "TEST", "enabled", true);
        var snapshot = Map.of("runId", at.getEpochSecond(), "configuration", configuration, "configurationHash", "e".repeat(64), "targetBindingHash", "f".repeat(64),
                "recordTimeZone", "UTC", "rulesHash", AgentCodePolicy.hash(at.toString()),
                "rules", List.of(Map.of("ruleCode", "BALANCE", "definitionHash", "d".repeat(64))));
        var run = Map.of("id", at.getEpochSecond(), "configId", 1, "status", "WARNING", "failedCount", 0, "skippedCount", 0,
                "startedAt", LocalDateTime.ofInstant(at.minusSeconds(1), ZoneOffset.UTC), "finishedAt", LocalDateTime.ofInstant(at, ZoneOffset.UTC),
                "executionEvidence", Map.of("scopeVerified", true, "state", "RECORDED", "snapshot", snapshot));
        return json.valueToTree(List.of(Map.of("configId", 1, "enabled", true, "latestRun", run)));
    }
    private String observation(String tool, JsonNode content, Instant observedAt) {
        String id = UUID.randomUUID().toString();
        String payload = json.writeValueAsString(Map.of("scope", scope, "observedAt", observedAt.toString(), "recordTimeZone", "UTC", "coverage", "QUERY_SUCCEEDED", "data", content));
        observations.insertObservation(new AgentObservationEntity(id, "run", "agent", id, tool, AgentObservationService.sourceType(tool, scope.businessLineCode()),
                scope.businessLineCode(), scope.environmentCode(), payload, AgentCodePolicy.hash(payload), LocalDateTime.ofInstant(observedAt, ZoneOffset.UTC)));
        return id;
    }
    private String begin(String code, JsonNode args) {
        String key = "verification-test:" + sequence.incrementAndGet(); runs.beginTool("run", context.ownerToken(), key, catalog.require(code), args, true); return key;
    }
    private JsonNode invoke(String code, JsonNode args) {
        String key = begin(code, args);
        try { var result = (JsonNode) catalog.require(code).execute(context, args); runs.endTool("run", context.ownerToken(), key, result); return result; }
        catch (RuntimeException ex) { runs.endTool("run", context.ownerToken(), key, Map.of("fixtureFailure", true)); throw ex; }
    }
    private void expire() { jdbc.update("UPDATE pm_agent_run SET lease_until=? WHERE id='run'", now().minusSeconds(1)); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) { var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(transactions); return (T) proxy.getProxy(); }
    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-04T07:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant; }
        void advance(long seconds) { instant = instant.plusSeconds(seconds); }
    }
}
