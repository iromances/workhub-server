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
    private final Set<String> grants = new HashSet<>(Set.of("agent:manage", "agent:task:assign", "agent:code:deliver", "ops:monitor:view", "ops:accounting:view", "ops:system-alert:view", "autonomy:case:operate"));
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
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_035107__add_agent_observations_and_cases.sql", "V20261104_174329__add_autonomy_case_resolution.sql",
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
        var specification = new AgentTaskSpecification("修复", "夹具", "复查", List.of("VERIFICATION"), List.of(scope, new AgentScope("BL000012", "PROD")), List.of(scope, new AgentScope("BL000012", "PROD")));
        var configuration = new AgentConfiguration("Agent","OPS", "复查", 1L, "fixture", specification.scopes(), 200, 3600, 2);
        agents.insertAgent(new AgentDefinitionEntity("agent", "Agent", json.writeValueAsString(configuration), true, "owner", 0, now(), now()));
        agents.insertRun(new AgentRunEntity("run", "task", "agent", "fixture", "owner", json.writeValueAsString(configuration), json.writeValueAsString(specification),
                json.writeValueAsString(AgentProviderBinding.from(provider)), "QUEUED", 0, 0, false, null, null, null, null, null, 0, now(), now()));
        context = runs.context(runs.claim());
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

    @Test void passedCertificateClosesOnlyBoundProblemAndKeepsSameCandidateCurrent() {
        bindCase("TEST","config:1:rule:BALANCE");
        String id=completeSamples(); finish(id,"PASS",false);
        var result=resolve(id,2,1);
        assertTrue(result.path("caseClosed").asBoolean());
        var problem=observations.find("case");
        assertEquals("RESOLVED",problem.status()); assertEquals(3,problem.version()); assertEquals(1,problem.evidenceVersion()); assertNull(problem.activeRunId());
        var resolution=observations.resolutionForVerification(id); assertNotNull(resolution);
        assertEquals(2,resolution.caseVersion()); assertEquals(3,resolution.resolvedVersion()); assertEquals("[\"health\"]",resolution.checkIdsJson());
        assertEquals(20,Duration.between(resolution.windowStart(),resolution.windowEnd()).getSeconds());
        var policy=new cn.aslight.workhub.service.autonomy.AutonomyCaseService(observations,agents,runs,access,
                new cn.aslight.workhub.service.autonomy.AutonomyEvidence(json),new AgentToolData(json),json,null,null);
        var candidate=new AgentChangeSetEntity("change","run","task",1,"key","hash",manifestHash,json.writeValueAsString(change.manifest()),now());
        assertTrue(policy.matchesReference(problem,candidate,change.manifest().caseReference()));
        assertTrue(resolve(id,2,1).path("caseClosed").asBoolean()); assertEquals(1,count("pm_autonomy_case_resolution"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_event WHERE kind='VERIFICATION_CASE_RESOLVED'",Integer.class));
        jdbc.update("UPDATE pm_autonomy_case SET status='DISCOVERED',version=version+1, resolved_at=NULL WHERE id='case'");
        assertFalse(policy.matchesReference(observations.find("case"),candidate,change.manifest().caseReference()));
        var reopened=status(id); assertFalse(reopened.path("caseClosed").asBoolean()); assertEquals(resolution.id(),reopened.at("/caseResolution/id").asText());
        assertEquals("DISCOVERED",reopened.path("caseStatus").asText());
    }
    @Test void closureRejectsUnfinishedHistoricalPendingAndStaleEvidence() {
        bindCase("TEST","config:1"); String id=completeSamples();
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        finish(id,"PASS",true); assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        finish(id,"PASS",false); clock.advance(31);
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        assertEquals("ANALYZING",observations.find("case").status()); assertEquals(0,count("pm_autonomy_case_resolution"));
    }
    @Test void testEnvironmentVerificationCannotCloseProductionProblem() {
        bindCase("PROD","config:1"); String id=completeSamples(); finish(id,"PASS",false);
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1)); assertEquals(0,count("pm_autonomy_case_resolution"));
    }
    @Test void unrelatedConfigurationOrRuleCannotCloseOriginalProblem() {
        bindCase("TEST","config:2"); String id=completeSamples(); finish(id,"PASS",false);
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        jdbc.update("UPDATE pm_autonomy_case SET subject_key='config:1:rule:OTHER' WHERE id='case'");
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1)); assertEquals(0,count("pm_autonomy_case_resolution"));
    }
    @Test void closureRequiresLatestVersionsAndNoKnownFailureInsideObservationWindow() {
        bindCase("TEST","config:1"); String id=completeSamples(); finish(id,"PASS",false);
        assertThrows(IllegalArgumentException.class,()->resolve(id,1,1));
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,0));
        jdbc.update("UPDATE pm_autonomy_case SET evidence_version=2,last_seen_at=? WHERE id='case'",now().minusSeconds(10));
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,2)); assertEquals(0,count("pm_autonomy_case_resolution"));
    }
    @Test void failureExactlyAtRecoveredWindowStartCannotClose() {
        bindCase("TEST","config:1"); String id=completeSamples(); finish(id,"PASS",false);
        jdbc.update("UPDATE pm_autonomy_case SET last_seen_at=? WHERE id='case'",now().minusSeconds(20));
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1)); assertEquals(0,count("pm_autonomy_case_resolution"));
    }
    @Test void closureRechecksCasePermissionProposalAndPersistedCertificate() {
        bindCase("TEST","config:1"); String id=completeSamples(); finish(id,"PASS",false);
        grants.remove("autonomy:case:operate"); assertThrows(org.springframework.security.access.AccessDeniedException.class,()->resolve(id,2,1)); grants.add("autonomy:case:operate");
        grants.remove("ops:accounting:view"); assertThrows(org.springframework.security.access.AccessDeniedException.class,()->resolve(id,2,1)); grants.add("ops:accounting:view");
        jdbc.update("UPDATE pm_autonomy_proposal SET proposal_json='{}' WHERE id='proposal'");
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        jdbc.update("UPDATE pm_autonomy_proposal SET proposal_json=? WHERE id='proposal'","{\"plan\":\"fix\"}");
        jdbc.update("UPDATE pm_agent_artifact SET payload_json='{}' WHERE kind='VERIFICATION'");
        assertThrows(IllegalArgumentException.class,()->resolve(id,2,1)); assertEquals(0,count("pm_autonomy_case_resolution"));
    }
    @Test void failedJournalInsertRollsBackClosureAndReceiptTogether() {
        bindCase("TEST","config:1"); String id=completeSamples(); finish(id,"PASS",false);
        var row=verifications.find(id); String key="verification:"+id+":CASE_RESOLVED:"+row.version();
        agents.insertEvent(new AgentEventEntity("collision","run",key,agents.nextEventOrdinal("run"),"FIXTURE","{}","fixture",now()));
        assertThrows(RuntimeException.class,()->resolve(id,2,1));
        assertEquals("ANALYZING",observations.find("case").status()); assertEquals(2,observations.find("case").version());
        assertEquals(0,count("pm_autonomy_case_resolution"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_verification_call WHERE tool_code='verification.resolve_case'",Integer.class));
    }
    @Test void anUnlinkedVerificationDoesNotInventAProblemClosure() {
        String id=completeSamples(); finish(id,"PASS",false);
        assertThrows(IllegalArgumentException.class,()->resolve(id,0,0)); assertEquals(0,count("pm_autonomy_case_resolution"));
    }
    @Test void committedClosureReceiptRecoversWithoutClosingTwice() {
        bindCase("TEST","config:1"); String id=completeSamples(); finish(id,"PASS",false);
        var args=json.valueToTree(Map.of("verificationId",id,"caseVersion",2,"evidenceVersion",1,"reason","原问题及历史数据已核对"));
        String key=begin("verification.resolve_case",args);
        var result=dispatch("verification.resolve_case",args); expire(); runs.recoverExpired("run");
        assertEquals("QUEUED",runs.get("run").status()); assertNull(runs.get("run").externalHandle());
        assertEquals(json.writeValueAsString(result),json.writeValueAsString(json.readTree(runs.event("run",key+":result").payloadJson()).path("value")));
        assertEquals(1,count("pm_autonomy_case_resolution")); assertEquals(3,observations.find("case").version());
    }
    @Test void logClosureRequiresTheOriginalServiceRatherThanAnotherHealthyService() {
        bindCase("TEST","service:"+AgentCodePolicy.hash("outside")+":error:signature");
        jdbc.update("UPDATE pm_autonomy_case SET source_type='SELF_LOG',category='APPLICATION_ERROR' WHERE id='case'");
        useSource("workhub.logs"); String id=start();
        for(int i=0;i<3;i++){if(i>0)clock.advance(10);recordLog(id,"log-close"+i,clock.instant(),clock.instant().minusSeconds(60),false,false,null);}
        finish(id,"PASS",false); assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        jdbc.update("UPDATE pm_autonomy_case SET subject_key=? WHERE id='case'","service:"+AgentCodePolicy.hash("a")+":error:signature");
        assertTrue(resolve(id,2,1).path("caseClosed").asBoolean());
    }
    @Test void collectedAlertAndBusinessProofsCanCloseTheirOwnOriginalObjects() {
        bindCase("TEST","service:"+AgentCodePolicy.hash("a")+":error:signature");
        jdbc.update("UPDATE pm_autonomy_case SET source_type='SYSTEM_ALERT',category='APPLICATION_ERROR' WHERE id='case'");
        useSource("monitor.system.alerts"); String id=start();
        for(int i=0;i<3;i++){if(i>0)clock.advance(10);recordAlert(id,"alert-close"+i,clock.instant(),"window"+i,"a".repeat(64),false);}
        finish(id,"PASS",false); assertTrue(resolve(id,2,1).path("caseClosed").asBoolean());
    }
    @Test void businessProofCannotResolveAnotherMonitor() {
        bindCase("TEST","monitor:2:job:20");
        jdbc.update("UPDATE pm_autonomy_case SET source_type='BUSINESS_MONITOR',category='EXECUTION_FAILURE' WHERE id='case'");
        useBusinessDetail(); String id=start();
        for(int i=0;i<3;i++){if(i>0)clock.advance(10);recordBusiness(id,"business-close"+i,clock.instant());}
        finish(id,"PASS",false); assertThrows(IllegalArgumentException.class,()->resolve(id,2,1));
        jdbc.update("UPDATE pm_autonomy_case SET subject_key='monitor:1:job:20' WHERE id='case'");
        assertTrue(resolve(id,2,1).path("caseClosed").asBoolean());
    }

    private void bindCase(String environment,String subject) {
        String proposal="{\"plan\":\"fix\"}";
        observations.insertCase(new cn.aslight.workhub.model.autonomy.AutonomyCaseEntity("case","fingerprint","BL000012",environment,
                "ACCOUNTING",subject,"DATA_DISCREPANCY","账务差异","ANALYZING","run","proposal",1,1,2,now().minusMinutes(5),now().minusMinutes(5),now(),null,now(),now()));
        observations.insertProposal(new cn.aslight.workhub.model.autonomy.AutonomyProposalEntity("proposal","case","run","request","hash",2,proposal,now()));
        var old=change.manifest(); var reference=new AgentChangeManifest.CaseReference("case",2,"proposal",AgentCodePolicy.hash(proposal));
        var manifest=new AgentChangeManifest(old.schemaVersion(),old.runId(),old.taskId(),old.task(),old.plan(),reference,old.repositories(),old.dependencyOrder());
        change=new AgentChangeView(change.id(),change.runId(),change.taskId(),change.revision(),change.manifestHash(),change.createdAt(),true,true,List.of(),manifest);
        approved=new AgentReleasePlanSnapshot(approved.schemaVersion(),change,approved.evidence(),approved.blockers(),approved.steps(),approved.verificationPlan());
        String snapshot=json.writeValueAsString(approved),hash=AgentCodePolicy.hash(snapshot);
        jdbc.update("UPDATE pm_agent_release_plan SET snapshot_json=?,snapshot_hash=? WHERE id='plan'",snapshot,hash);
        jdbc.update("UPDATE pm_agent_release_review SET snapshot_hash=? WHERE plan_id='plan'",hash);
    }
    private JsonNode resolve(String id,long version,long evidence) {
        return invoke("verification.resolve_case",json.valueToTree(Map.of("verificationId",id,"caseVersion",version,"evidenceVersion",evidence,"reason","原问题和全部历史证据已处理，按获批标准确认恢复")));
    }

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
        assertThrows(IllegalArgumentException.class, () -> record(id, "later", accounting(clock.instant()), "PASS", false));
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
        String wrongScope = wrong; assertThrows(IllegalArgumentException.class, () -> record(id, "wrong-scope", wrongScope, "PASS", false));
        wrong = accounting(clock.instant()); jdbc.update("UPDATE pm_agent_observation SET agent_id='other' WHERE id=?", wrong);
        String agent = wrong; assertThrows(IllegalArgumentException.class, () -> record(id, "wrong-agent", agent, "PASS", false));
        String foreign = accounting(clock.instant()); jdbc.update("UPDATE pm_agent_observation SET run_id='other' WHERE id=?", foreign);
        assertThrows(IllegalArgumentException.class, () -> record(id, "wrong-run", foreign, "PASS", false));
        String corrupt = accounting(clock.instant()); jdbc.update("UPDATE pm_agent_observation SET content_hash='broken' WHERE id=?", corrupt);
        assertThrows(IllegalArgumentException.class, () -> record(id, "corrupt", corrupt, "PASS", false));
        var args = recordArgs(id, "scalar", accounting(clock.instant()), "PASS", false); ((ObjectNode) args.at("/evidence/0")).put("pointer", "/data/0/status");
        assertThrows(IllegalArgumentException.class, () -> invoke("verification.record", args)); assertEquals(0, count("pm_agent_verification_sample"));
    }
    @Test void latestFailedOrIncompleteSampleResetsEarlierHealthyWindow() {
        String id = completeSamples(); clock.advance(5); var result = record(id, "failure", accounting(clock.instant()), "FAIL", false);
        assertEquals(0, result.at("/checks/0/distinctSamples").asInt()); assertEquals("OBSERVING", finish(id, "PASS", false).path("status").asText());
    }
    @Test void replayedStartAndSampleAreIdempotentButChangedRequestsAreRejected() {
        String id = start(); assertEquals(id, start()); assertEquals(1, count("pm_agent_verification"));
        String observation = accounting(clock.instant()); record(id, "sample", observation, "PASS", false); record(id, "sample", observation, "PASS", false);
        assertEquals(1, count("pm_agent_verification_sample"));
        assertThrows(IllegalArgumentException.class, () -> record(id, "sample", observation, "FAIL", false));
    }
    @Test void concurrentSameToolCallCommitsOnlyOneSampleAndReceipt() throws Exception {
        String id = start(); var args = recordArgs(id, "sample", accounting(clock.instant()), "PASS", false);
        String key = begin("verification.record", args);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> dispatch("verification.record", args));
            var b = pool.submit(() -> dispatch("verification.record", args));
            assertEquals(a.get(5, TimeUnit.SECONDS).toString(), b.get(5, TimeUnit.SECONDS).toString());
        }
        endTool(key, Map.of("fixture", true));
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
        assertThrows(IllegalArgumentException.class, this::start);
        jdbc.update("UPDATE pm_agent_release_plan SET status='SUCCEEDED' WHERE id='plan'");
        var receipt = (ObjectNode) json.readTree(deployments.find("deploy").receiptJson()); receipt.set("deployedCommits", json.valueToTree(Map.of("server", "c".repeat(40))));
        jdbc.update("UPDATE pm_agent_deployment SET receipt_json=? WHERE id='deploy'", receipt.toString());
        assertThrows(IllegalArgumentException.class, this::start); assertEquals(0, count("pm_agent_verification"));
    }
    @Test void reviewAndPlanHashesMustRemainConsistent() {
        jdbc.update("UPDATE pm_agent_release_review SET snapshot_hash='different' WHERE id='review'");
        assertThrows(IllegalArgumentException.class, this::start); assertEquals(0, count("pm_agent_verification"));
    }
    @Test void finalCertificateRequiresUnchangedDeploymentAndCandidate() {
        String id = completeSamples(); jdbc.update("UPDATE pm_agent_deployment SET status='UNKNOWN' WHERE id='deploy'");
        assertThrows(IllegalArgumentException.class, () -> finish(id, "PASS", false));
        jdbc.update("UPDATE pm_agent_deployment SET status='SUCCEEDED' WHERE id='deploy'");
        change = new AgentChangeView(change.id(), change.runId(), change.taskId(), change.revision(), change.manifestHash(), change.createdAt(), false, false, List.of("CHANGED"), change.manifest());
        assertThrows(IllegalArgumentException.class, () -> finish(id, "PASS", false)); assertEquals(0, count("pm_agent_artifact"));
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
        var result = dispatch("verification.start", args); expire(); runs.recoverExpired("run");
        assertEquals("QUEUED", runs.get("run").status()); assertNull(runs.get("run").externalHandle());
        var recovered = json.readTree(runs.event("run", key + ":result").payloadJson());
        assertEquals(json.writeValueAsString(result), json.writeValueAsString(recovered.path("value")));
        assertEquals(1, count("pm_agent_verification")); assertEquals(1, count("pm_agent_verification_call"));
    }
    @Test void nativeRecoveryPreservesUnknownProcessUntilActualStopReceipt() {
        String attempt = "cli:fixture"; runs.beginNative("run", context.ownerToken(), attempt, "fixture");
        var args = json.valueToTree(Map.of("planId", "plan")); String key = attempt + ":rpc:1";
        var tool = json.createObjectNode().put("kind", "TOOL").put("key", key).put("tool", "verification.start").put("readOnly", false);
        var handle = (ObjectNode) json.readTree(runs.get("run").externalHandle()); handle.set("activeTool", tool);
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), "run", key + ":request", agents.nextEventOrdinal("run"),
                "TOOL_REQUEST", json.writeValueAsString(Map.of("tool", "verification.start", "arguments", args)), "agent-runtime", now()));
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id='run'", json.writeValueAsString(handle));
        dispatch("verification.start", args); expire(); runs.recoverExpired("run");
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
    @Test void humanSampleHistoryUsesStableUpperBoundAndKeysetPagination() {
        String id=start();grants.addAll(Set.of("agent:task:view","agent:code:use"));
        for(int i=0;i<3;i++){if(i>0)clock.advance(10);record(id,"page"+i,accounting(clock.instant()),"PASS",false);}
        var first=service.samplesForUser("plan","owner","health",0,null,2);
        assertEquals(2,first.path("items").size());assertTrue(first.path("hasMore").asBoolean());
        long upper=first.path("throughOrder").asLong(),after=first.path("nextAfterOrder").asLong();
        clock.advance(10);record(id,"new",accounting(clock.instant()),"PASS",false);
        var second=service.samplesForUser("plan","owner","health",after,upper,2);
        assertEquals(1,second.path("items").size());assertFalse(second.path("hasMore").asBoolean());
        assertEquals(upper,second.path("throughOrder").asLong());assertEquals(4,service.samplesForUser("plan","owner","health",0,null,20).path("items").size());
        assertEquals(4,verifications.find(id).version());
        assertThrows(IllegalArgumentException.class,()->service.samplesForUser("plan","owner","foreign",0,null,2));
        for(long invalid:List.of(-1L,Long.MAX_VALUE))assertThrows(IllegalArgumentException.class,()->service.samplesForUser("plan","owner","health",invalid,null,2));
        assertThrows(IllegalArgumentException.class,()->service.samplesForUser("plan","owner","health",0,upper+10,2));
        assertThrows(IllegalArgumentException.class,()->service.samplesForUser("plan","owner","health",0,null,21));
    }
    @Test void humanHistoryPreservesFullReasonsAndReadsOnlyReferencedObservationPointer() {
        String id=start();String observed=accounting(clock.instant());var args=recordArgs(id,"full",observed,"PASS",false);
        String reason="本次业务判定的详细理由".repeat(250);args.put("reason",reason);invoke("verification.record",args);
        grants.addAll(Set.of("agent:task:view","agent:code:use"));pause();
        var page=service.samplesForUser("plan","owner","health",0,null,10);var sample=page.path("items").get(0);
        assertEquals(reason,sample.path("reason").asText());String sampleId=sample.path("id").asText();
        var original=service.evidenceForUser("plan","owner",sampleId,observed,"/data/0");
        assertEquals(json.readTree(observations.observation(observed).payloadJson()),original.path("payload"));
        assertEquals(id,original.path("verificationId").asText());
        assertThrows(IllegalArgumentException.class,()->service.evidenceForUser("plan","owner",sampleId,observed,"/data"));
        String other=accounting(clock.instant());assertThrows(IllegalArgumentException.class,()->service.evidenceForUser("plan","owner",sampleId,other,"/data/0"));
        assertThrows(IllegalArgumentException.class,()->service.evidenceForUser("plan","owner","foreign",observed,"/data/0"));
        verify(changes,atLeastOnce()).detailForUser("change","owner");
    }
    @Test void humanEvidenceAndHistoryRequireCurrentSourceAccessAndAgentScope() {
        String id=start(),observed=accounting(clock.instant());record(id,"one",observed,"PASS",false);
        String sampleId=verifications.samples(id,"health").getFirst().id();grants.addAll(Set.of("agent:task:view","agent:code:use"));
        grants.remove("ops:accounting:view");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.samplesForUser("plan","owner","health",0,null,10));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.evidenceForUser("plan","owner",sampleId,observed,"/data/0"));
        grants.add("ops:accounting:view");var config=(ObjectNode)json.readTree(agents.findAgent("agent").configurationJson());config.set("scopes",json.valueToTree(List.of(new AgentScope("BL000099", "TEST"))));
        jdbc.update("UPDATE pm_agent_definition SET configuration_json=? WHERE id='agent'",json.writeValueAsString(config));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.detailForUser("plan","owner"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.samplesForUser("plan","owner","health",0,null,10));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.evidenceForUser("plan","owner",sampleId,observed,"/data/0"));
    }
    @Test void missingOrAlteredOriginalEvidenceIsNotAnEmptyHistory() {
        String id=start(),observed=accounting(clock.instant());record(id,"one",observed,"PASS",false);
        String sampleId=verifications.samples(id,"health").getFirst().id();grants.addAll(Set.of("agent:task:view","agent:code:use"));
        jdbc.update("UPDATE pm_agent_observation SET payload_json='{}' WHERE id=?",observed);
        assertThrows(IllegalArgumentException.class,()->service.samplesForUser("plan","owner","health",0,null,10));
        assertThrows(IllegalArgumentException.class,()->service.evidenceForUser("plan","owner",sampleId,observed,"/data/0"));
        jdbc.update("DELETE FROM pm_agent_observation WHERE id=?",observed);
        assertThrows(IllegalStateException.class,()->service.samplesForUser("plan","owner","health",0,null,10));
    }
    @Test void historicalPassedConclusionAndCurrentStalenessRemainSeparate() {
        String id=completeSamples();String reason="完整总体判定理由".repeat(250);
        invoke("verification.finish",json.valueToTree(Map.of("verificationId",id,"assessment","PASS","reason",reason,"historicalDataPending",false)));
        grants.addAll(Set.of("agent:task:view","agent:code:use"));clock.advance(100);
        var history=service.detailForUser("plan","owner");
        assertEquals("PASSED",history.path("status").asText());assertFalse(history.path("currentConstraintsSatisfied").asBoolean());
        assertTrue(history.at("/conclusion/platformConstraintsSatisfied").asBoolean());assertEquals(reason,history.at("/conclusion/reason").asText());
        assertFalse(history.at("/conclusion/checks").isEmpty());assertTrue(history.path("checks").toString().contains("LATEST_DATA_STALE"));
        assertEquals("TEST_DEPLOY",history.path("stage").asText());assertEquals("change",history.path("changeSetId").asText());
    }
    @Test void missingVerificationIsNullDetailButCannotBeQueriedAsEmptySampleHistory() {
        grants.addAll(Set.of("agent:task:view","agent:code:use"));assertTrue(service.detailForUser("plan","owner").isNull());
        assertThrows(IllegalArgumentException.class,()->service.samplesForUser("plan","owner","health",0,null,10));
        assertThrows(IllegalArgumentException.class,()->service.evidenceForUser("plan","owner","sample","observation","/data/0"));
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
        args.put("historicalDataPending", "false"); assertThrows(IllegalArgumentException.class, () -> invoke("verification.record", args));
        args.remove("historicalDataPending"); assertThrows(IllegalArgumentException.class, () -> invoke("verification.record", args));
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
    @Test void completeAlertCollectionWindowsCanSupportAgentPassingJudgment() {
        useSource("monitor.system.alerts");String id=start();
        for(int i=0;i<3;i++) {if(i>0)clock.advance(10);recordAlert(id,"alert"+i,clock.instant(),"window"+i,"a".repeat(64),false);}
        var result=finish(id,"PASS",false);assertEquals("PASSED",result.path("status").asText());assertEquals(20,result.at("/checks/0/observedSeconds").asLong());
    }
    @Test void repeatedAlertWindowAndOldActivityCannotCreateExtraSamples() {
        useSource("monitor.system.alerts");String id=start();Instant first=clock.instant();
        for(int i=0;i<3;i++){if(i>0)clock.advance(10);recordAlert(id,"repeat"+i,first,"same-window","a".repeat(64),false);}
        assertEquals(1,finish(id,"PASS",false).at("/checks/0/distinctSamples").asInt());
    }
    @Test void changedAlertRulesRestartObservationAndMissingRowsCannotCertify() {
        useSource("monitor.system.alerts");String id=start();
        recordAlert(id,"first",clock.instant(),"one","a".repeat(64),false);clock.advance(10);
        var changed=recordAlert(id,"changed",clock.instant(),"two","b".repeat(64),false);assertEquals(1,changed.at("/checks/0/distinctSamples").asInt());
        clock.advance(10);var missing=recordAlert(id,"missing",clock.instant(),"three","b".repeat(64),true);
        assertTrue(missing.at("/checks/0/constraints").toString().contains("QUERY_CONTENT_DIFFERS_FROM_COLLECTION"));assertNotEquals("PASSED",finish(id,"PASS",false).path("status").asText());
    }
    private JsonNode recordAlert(String id,String request,Instant activity,String window,String rules,boolean invalid) {
        var definition=new cn.aslight.workhub.model.ops.SystemAlertQueryEvidence.Definition(1L,"BL000012","TEST","ALL",List.of("workhub-*"),List.of("a"),rules,"UTC");
        var from=clock.instant().minusSeconds(60);var to=clock.instant();
        var proof=new cn.aslight.workhub.model.ops.SystemAlertQueryEvidence(1,UUID.randomUUID().toString(),definition,AgentCodePolicy.hash(json.writeValueAsString(definition)),
                window,"c".repeat(64),"d".repeat(64),1,to,to,to,to,from,to,from,to,!invalid,invalid?List.of("QUERY_CONTENT_DIFFERS_FROM_COLLECTION"):List.of(),
                List.of(new cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.Activity("a",1,activity,"e".repeat(64))),0,List.of(),List.of());
        var result=new cn.aslight.workhub.model.ops.SystemAlertDashboardResponse(0,1,30,List.of(),List.of(),proof);
        String key=UUID.randomUUID().toString(),tool="monitor.system.alerts";
        String payload=json.writeValueAsString(new AgentToolData(json).sanitize(Map.of("scope",scope,"observedAt",to.toString(),"recordTimeZone","UTC","query",Map.of("from",from.toString(),"to",to.toString()),"data",result)));
        observations.insertObservation(new AgentObservationEntity(key,"run","agent",key,tool,AgentObservationService.sourceType(tool,scope.businessLineCode()),scope.businessLineCode(),scope.environmentCode(),payload,AgentCodePolicy.hash(payload),now()));
        var args=recordArgs(id,request,key,"PASS",false);((ObjectNode)args.at("/evidence/0")).put("pointer","/data");return invoke("verification.record",args);
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
    /** 历史运行的工具调用意图直接落库：对账与恢复按原样识别已提交事务，不重放调用。 */
    private String begin(String code, JsonNode args) {
        String key = "verification-test:" + sequence.incrementAndGet();
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), "run", key + ":request", agents.nextEventOrdinal("run"),
                "TOOL_REQUEST", json.writeValueAsString(Map.of("tool", code, "arguments", args)), "agent-runtime", now()));
        jdbc.update("UPDATE pm_agent_run SET external_handle=? WHERE id='run'",
                json.writeValueAsString(Map.of("kind", "TOOL", "key", key, "tool", code, "readOnly", false)));
        return key;
    }
    private void endTool(String key, Object result) {
        agents.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), "run", key + ":result", agents.nextEventOrdinal("run"),
                "TOOL_RESULT", json.writeValueAsString(result), "agent-runtime", now()));
        jdbc.update("UPDATE pm_agent_run SET external_handle=NULL WHERE id='run'");
    }
    private JsonNode invoke(String code, JsonNode args) {
        String key = begin(code, args);
        try {
            var result = dispatch(code, args);
            endTool(key, result); return result;
        } catch (RuntimeException ex) { endTool(key, Map.of("fixtureFailure", true)); throw ex; }
    }
    private JsonNode dispatch(String code, JsonNode args) {
        return switch (code) {
            case "verification.start" -> service.start(context, args.path("planId").asText());
            case "verification.record" -> service.record(context, args.path("verificationId").asText(), args.path("requestKey").asText(),
                    args.path("checkId").asText(), args.path("assessment").asText(), args.path("reason").asText(""),
                    requiredBoolean(args, "historicalDataPending"), references(args));
            case "verification.finish" -> service.finish(context, args.path("verificationId").asText(), args.path("assessment").asText(),
                    args.path("reason").asText(""), requiredBoolean(args, "historicalDataPending"));
            case "verification.resolve_case" -> service.resolveCase(context, args.path("verificationId").asText(),
                    args.path("caseVersion").asLong(), args.path("evidenceVersion").asLong(), args.path("reason").asText());
            case "verification.status" -> service.status(context, args.path("verificationId").asText());
            case "verification.wait" -> service.waitForNext(context, args.path("verificationId").asText(), args.path("seconds").asLong());
            default -> throw new IllegalArgumentException("未知验证调用: " + code);
        };
    }
    private boolean requiredBoolean(JsonNode args, String name) {
        var value = args.get(name);
        if (value == null || !value.isBoolean()) throw new IllegalArgumentException(name + " 必须为布尔值");
        return value.asBoolean();
    }
    private List<AgentVerificationEvidenceReference> references(JsonNode args) {
        var references = new ArrayList<AgentVerificationEvidenceReference>();
        for (var row : args.path("evidence")) references.add(new AgentVerificationEvidenceReference(row.path("observationId").asText(), row.path("pointer").asText()));
        return references;
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
