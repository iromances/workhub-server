package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import cn.aslight.workhub.dao.autonomy.AutonomyMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.autonomy.*;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.autonomy.AutonomyCaseService;
import cn.aslight.workhub.service.system.SysConfigService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
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
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实临时 Git 对象、生产迁移/Mapper、事务与运行租约；不访问远端、不执行仓库代码。 */
class AgentChangeServiceTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T04:30:00Z"), ZoneOffset.UTC);
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final AtomicReference<String> policyText = new AtomicReference<>();
    private Connection keepAlive;
    private ValidatorFactory validation;
    private JdbcTemplate jdbc;
    private TransactionInterceptor transactions;
    private AgentMapper agents;
    private AgentCodeMapper workspaces;
    private AgentChangeMapper mapper;
    private AgentDeliveryMapper deliveries;
    private AgentGitLabPolicy gitLabPolicies;
    private AgentGitLabClient gitLab;
    private AgentCodeGit publishing;
    private AgentDeliveryService deliveryService;
    private final AtomicReference<String> gitLabPolicyText = new AtomicReference<>();
    private final Map<String, AgentGitLabClient.Branch> remoteBranches = new ConcurrentHashMap<>();
    private final Map<String, AgentGitLabClient.MergeRequest> remoteRequests = new ConcurrentHashMap<>();
    private AutonomyMapper autonomy;
    private AgentCodePolicy policies;
    private AgentRunStore runs;
    private AgentAccessService access;
    private AutonomyCaseService cases;
    private AgentCodeWorkspaceService code;
    private AgentChangeStore store;
    private AgentChangeService service;
    private AgentToolContext context;
    private final Map<String, String> bases = new TreeMap<>();
    private final Set<String> userPermissions = new HashSet<>(Set.of("agent:task:view", "agent:code:use"));

    @BeforeEach void setup() throws Exception {
        temp = temp.toRealPath();
        for (String name : List.of("server", "web")) {
            Path source = Files.createDirectory(temp.resolve(name)); command(source, "init", "-b", "development");
            Files.createDirectory(source.resolve("src")); Files.writeString(source.resolve("src/Main.txt"), "old implementation\n");
            command(source, "add", "."); command(source, "commit", "-m", "fixture baseline"); bases.put(name, command(source, "rev-parse", "HEAD").trim());
            Files.writeString(source.resolve("src/Main.txt"), "user uncommitted file\n");
        }
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = source.getConnection(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        for (String file : List.of("V20261104_014200__add_agent_workbench.sql", "V20261104_031917__add_agent_code_workspaces.sql", "V20261104_035107__add_agent_observations_and_cases.sql", "V20261104_043000__add_agent_change_sets.sql", "V20261104_045500__add_agent_gitlab_delivery.sql")) {
            ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + file));
        }
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true);
        for (Class<?> type : List.of(AgentMapper.class, AgentCodeMapper.class, AgentChangeMapper.class, AgentDeliveryMapper.class, AutonomyMapper.class)) configuration.addMapper(type);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject()); agents = sessions.getMapper(AgentMapper.class); workspaces = sessions.getMapper(AgentCodeMapper.class);
        mapper = sessions.getMapper(AgentChangeMapper.class); autonomy = sessions.getMapper(AutonomyMapper.class);
        deliveries = sessions.getMapper(AgentDeliveryMapper.class);
        transactions = new TransactionInterceptor(); transactions.setTransactionManager(new DataSourceTransactionManager(source)); transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        access = mock(AgentAccessService.class);
        when(access.actor(anyString())).thenAnswer(invocation -> new AgentAccessService.Actor(invocation.getArgument(0), Set.copyOf(userPermissions)));
        doAnswer(invocation -> {
            var actor = (AgentAccessService.Actor) invocation.getArgument(0); var agent = (AgentDefinitionEntity) invocation.getArgument(1);
            if (agent == null || !agent.ownerName().equals(actor.name())) throw new AccessDeniedException("owner"); return null;
        }).when(access).requireAgent(any(), any());
        doAnswer(invocation -> {
            var ctx = (AgentToolContext) invocation.getArgument(0);
            if (!ctx.specification().scopes().contains(invocation.getArgument(1))) throw new AccessDeniedException("scope"); return null;
        }).when(access).requireScope(any(), any());
        var configs = mock(SysConfigService.class); when(configs.findPlainValue("agent.code", "policy")).thenAnswer(invocation -> policyText.get());
        gitLabPolicyText.set(json.writeValueAsString(new AgentGitLabPolicy.Policy(List.of(new AgentGitLabPolicy.Instance("instance", "GitLab", "https://gitlab.example.invalid", "gitlab.credentials", "token", true)),
                List.of(new AgentGitLabPolicy.Binding("server", "instance", 1, "group/server", "development", true), new AgentGitLabPolicy.Binding("web", "instance", 2, "group/web", "development", true)))));
        when(configs.findPlainValue("agent.gitlab", "policy")).thenAnswer(invocation -> gitLabPolicyText.get());
        when(configs.requireSecretValue("gitlab.credentials", "token")).thenReturn("fixture-secret");
        gitLabPolicies = new AgentGitLabPolicy(configs, json);
        policyText.set(json.writeValueAsString(new AgentCodePolicy.Policy(temp.resolve("workspaces").toString(), List.of(repo("server"), repo("web")))));
        policies = new AgentCodePolicy(configs, json);
        var providers = mock(AiProviderConfigService.class); var provider = new AiProviderConfigEntity(); provider.setId(1L); provider.setEnabled(true); provider.setChannelType("API");
        when(providers.getEnabledById(1L)).thenReturn(provider);
        var data = new AgentToolData(json); runs = proxy(new AgentRunStore(agents, json, access, data, providers, clock));
        var config = new AgentConfiguration("Code", "修复", 1L, "fixture", List.of(scope), List.of(), 100, 3600, 2);
        var specification = new AgentTaskSpecification("修复代码", "输入", "实际测试后交付", List.of("CHANGE_SET", "TEST_REPORT"), List.of(scope), List.of());
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        agents.insertAgent(new AgentDefinitionEntity("agent", "Code", json.writeValueAsString(config), true, "owner", 0, now, now));
        agents.insertRun(new AgentRunEntity("run", "task", "agent", "manual", "owner", json.writeValueAsString(config), json.writeValueAsString(specification),
                json.writeValueAsString(AgentProviderBinding.from(provider)), "RUNNING", 0, 0, false, "lease", now.plusSeconds(60), null, null, null, 0, now, now));
        jdbc.update("UPDATE pm_agent_run SET owner_token=?,lease_until=? WHERE id='run'", "lease", now.plusSeconds(60));
        context = runs.context(agents.findRun("run"));
        var codeStore = proxy(new AgentCodeStore(workspaces, agents, runs, json));
        code = new AgentCodeWorkspaceService(policies, workspaces, codeStore, runs, access, new AgentCodeGit(), json);
        cases = mock(AutonomyCaseService.class);
        store = proxy(new AgentChangeStore(mapper, workspaces, agents, autonomy, runs, policies, access, cases, json));
        validation = Validation.buildDefaultValidatorFactory();
        service = new AgentChangeService(mapper, workspaces, agents, autonomy, store, code, policies, runs, access, cases, data, json, validation.getValidator(), deliveries, gitLabPolicies);
    }
    @AfterEach void close() throws Exception { if (validation != null) validation.close(); if (keepAlive != null) keepAlive.close(); }

    @Test void freezesAllRepositoriesActualDiffAndDependencyOrderWithoutClaimingDelivery() throws Exception {
        String server = open("server"), web = open("web"); change(server, "fix", "new implementation\n");
        var result = service.prepare(context, "prepare", null, plan());
        assertEquals(List.of("server", "web"), result.manifest().dependencyOrder()); assertEquals(2, result.manifest().repositories().size());
        assertTrue(result.current()); assertFalse(result.readyForReview());
        assertEquals(List.of("PLATFORM_TEST_EVIDENCE_MISSING", "REMOTE_DELIVERY_EVIDENCE_MISSING"), result.blockers());
        var repository = result.manifest().repositories().getFirst();
        assertEquals(bases.get("server"), repository.baseSha()); assertEquals(workspaces.find(server).candidateSha(), repository.resultSha());
        assertTrue(repository.diffPreview().contains("-old implementation")); assertTrue(repository.diffPreview().contains("+new implementation"));
        assertEquals(List.of("src/Main.txt"), repository.changedPaths());
        assertTrue(result.manifest().repositories().get(1).changedPaths().isEmpty());
        assertEquals(AgentCodePolicy.hash(mapper.find(result.id()).manifestJson()), result.manifestHash());
        assertFalse(mapper.find(result.id()).manifestJson().contains(temp.toString()));
        assertTrue(agents.listArtifacts("run").isEmpty());
        assertEquals("user uncommitted file\n", Files.readString(temp.resolve("server/src/Main.txt")));
        assertEquals(bases.get("web"), workspaces.find(web).candidateSha());
    }

    @Test void emptyCodeChangesCannotBecomeFakeDeliveryArtifact() {
        open("server"); open("web");
        assertThrows(IllegalArgumentException.class, () -> service.prepare(context, "empty", null, plan())); assertEquals(0, mapper.count("run"));
    }

    @Test void retryKeepsOriginalManifestWhenCodeChangesAndNewKeyCreatesNewRevision() {
        String server = preparedWorkspaces(); var first = service.prepare(context, "same", null, plan());
        String frozen = mapper.find(first.id()).manifestJson();
        change(server, "next", "second implementation\n");
        var retry = service.prepare(context, "same", null, plan()); assertEquals(first.id(), retry.id()); assertFalse(retry.current());
        assertTrue(retry.blockers().contains("CODE_CHANGED:server")); assertEquals(frozen, mapper.find(first.id()).manifestJson());
        assertThrows(IllegalArgumentException.class, () -> service.prepare(context, "same", null, new AgentChangePlan("不同说明", "影响", "验收", "回退", List.of())));
        var second = service.prepare(context, "new", null, plan()); assertEquals(2, second.revision()); assertTrue(second.current());
        assertNotEquals(first.manifestHash(), second.manifestHash()); assertTrue(service.detail(context, first.id()).blockers().contains("NEWER_MANIFEST_EXISTS"));
    }

    @Test void invalidOrCyclicDependenciesDoNotSavePartialManifest() {
        preparedWorkspaces();
        for (var edges : List.of(List.of(new AgentChangePlan.Dependency("other", "server")), List.of(new AgentChangePlan.Dependency("web", "web")),
                List.of(new AgentChangePlan.Dependency("server", "web"), new AgentChangePlan.Dependency("web", "server")),
                List.of(new AgentChangePlan.Dependency("web", "server"), new AgentChangePlan.Dependency("web", "server")))) {
            assertThrows(IllegalArgumentException.class, () -> service.prepare(context, UUID.randomUUID().toString(), null, new AgentChangePlan("summary", "impact", "accept", "rollback", edges)));
        }
        assertEquals(0, mapper.count("run"));
    }

    @Test void wholeSetCasRejectsWorkspaceChangedAfterGitEvidenceWasRead() {
        String server = preparedWorkspaces(); var manifest = snapshot(null);
        change(server, "racing", "racing implementation\n");
        assertThrows(IllegalArgumentException.class, () -> store.save(context, "stale", "hash", manifest)); assertEquals(0, mapper.count("run"));
    }

    @Test void wholeSetCasRejectsOmittedRepository() {
        preparedWorkspaces(); var full = snapshot(null);
        var incomplete = new AgentChangeManifest(1, "run", "task", context.specification(), plan(), null,
                List.of(full.repositories().getFirst()), List.of("server"));
        assertThrows(IllegalArgumentException.class, () -> store.save(context, "partial", "hash", incomplete)); assertEquals(0, mapper.count("run"));
    }

    @Test void concurrentRequestsCreateOneImmutableRevision() throws Exception {
        preparedWorkspaces();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.prepare(context, "same", null, plan())); var b = pool.submit(() -> service.prepare(context, "same", null, plan()));
            assertEquals(a.get(30, TimeUnit.SECONDS).id(), b.get(30, TimeUnit.SECONDS).id());
        }
        assertEquals(1, mapper.count("run")); assertEquals(1, mapper.lastRevision("run"));
    }

    @Test void problemRepairCannotOmitCaseAndPreservesExactAnalysisVersion() {
        preparedWorkspaces(); caseFixture();
        assertThrows(IllegalArgumentException.class, () -> service.prepare(context, "no-case", null, plan()));
        var result = service.prepare(context, "case", "case", plan());
        assertEquals(2, result.manifest().caseReference().caseVersion()); assertEquals("proposal", result.manifest().caseReference().proposalId());
        verify(cases, atLeastOnce()).requireCaseAccess(eq(context), any());
        jdbc.update("UPDATE pm_autonomy_case SET version=3 WHERE id='case'");
        assertFalse(service.detail(context, result.id()).current()); assertTrue(service.detail(context, result.id()).blockers().contains("CASE_PLAN_CHANGED"));
    }

    @Test void staleProblemOrPolicyRollsBackManifestRegistration() {
        preparedWorkspaces(); caseFixture();
        var reference = new AgentChangeManifest.CaseReference("case", 2, "proposal", AgentCodePolicy.hash(autonomy.proposal("proposal").proposalJson()));
        var original = snapshot(reference);
        jdbc.update("UPDATE pm_autonomy_case SET version=3 WHERE id='case'");
        assertThrows(IllegalArgumentException.class, () -> store.save(context, "problem-changed", "hash", original));
        jdbc.update("UPDATE pm_autonomy_case SET version=2 WHERE id='case'");
        var policy = policies.current().policy();
        policyText.set(json.writeValueAsString(new AgentCodePolicy.Policy(policy.workspaceRoot(), List.of(repo("web"), repo("server")))));
        assertThrows(IllegalArgumentException.class, () -> store.save(context, "policy-changed", "hash", original)); assertEquals(0, mapper.count("run"));
    }

    @Test void changedRegisteredBugBlocksExistingCandidateAndRejectsNewRegistration() {
        preparedWorkspaces(); caseFixture();
        var candidate = service.prepare(context, "linked-bug", "case", plan());
        assertTrue(candidate.current());
        when(cases.bugContextChanged(any())).thenReturn(true);
        var changed = service.detail(context, candidate.id());
        assertFalse(changed.current()); assertTrue(changed.blockers().contains("BUG_CONTEXT_CHANGED"));
        doThrow(new IllegalArgumentException("关联缺陷的需求依据已变化")).when(cases).requireCurrentBugLinks(any());
        assertThrows(IllegalArgumentException.class, () -> service.prepare(context, "new-stale", "case", plan()));
        assertThrows(IllegalArgumentException.class, () -> store.save(context, "concurrent-change", "hash", candidate.manifest()));
        assertEquals(1, mapper.count("run"));
    }

    @Test void cancelledOrReplacedWorkerCannotRegisterFrozenCode() {
        preparedWorkspaces(); var manifest = snapshot(null);
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'");
        assertThrows(AgentRunStore.StopRequested.class, () -> store.save(context, "cancelled", "hash", manifest));
        jdbc.update("UPDATE pm_agent_run SET cancel_requested=0,owner_token='new-owner' WHERE id='run'");
        assertThrows(AgentRunStore.LeaseLost.class, () -> store.save(context, "stale-owner", "hash", manifest)); assertEquals(0, mapper.count("run"));
    }

    @Test void humanCanInspectStoppedAgentHistoryButCannotBypassOwnershipOrRevokedPaths() {
        preparedWorkspaces(); var value = service.prepare(context, "history", null, plan());
        jdbc.update("UPDATE pm_agent_run SET status='COMPLETED',owner_token=NULL,lease_until=NULL WHERE id='run'");
        jdbc.update("UPDATE pm_agent_definition SET enabled=0 WHERE id='agent'");
        assertEquals(value.id(), service.detailForUser(value.id(), "owner").id());
        assertThrows(AccessDeniedException.class, () -> service.detailForUser(value.id(), "other"));
        userPermissions.remove("agent:code:use"); assertThrows(AccessDeniedException.class, () -> service.detailForUser(value.id(), "owner")); userPermissions.add("agent:code:use");
        var prior = repo("server"); var revoked = new AgentCodePolicy.Repository(prior.id(), prior.name(), prior.scope(), prior.sourcePath(), prior.baseBranch(), List.of("docs"), List.of(), true);
        policyText.set(json.writeValueAsString(new AgentCodePolicy.Policy(temp.resolve("workspaces").toString(), List.of(revoked, repo("web")))));
        assertThrows(AccessDeniedException.class, () -> service.detailForUser(value.id(), "owner"));
    }

    @Test void movingSourceBaselineRequiresFreshPlanning() throws Exception {
        preparedWorkspaces(); command(temp.resolve("server"), "add", "src/Main.txt"); command(temp.resolve("server"), "commit", "-m", "source advanced");
        assertThrows(IllegalArgumentException.class, () -> service.prepare(context, "advanced", null, plan())); assertEquals(0, mapper.count("run"));
    }

    @Test void diffPreviewIsMaskedAndBoundedWhileHashCoversTheActualDiff() {
        String server = preparedWorkspaces(); var workspace = workspaces.find(server);
        code.write(context, server, workspace.version(), "src/Config.txt", "MISSING", "password=fixture-secret-value\n" + "x".repeat(30_000), "large", false);
        var value = service.prepare(context, "preview", null, plan()); var repository = value.manifest().repositories().getFirst();
        assertTrue(repository.diffTruncated()); assertTrue(repository.diffPreview().length() <= 16000); assertFalse(repository.diffPreview().contains("fixture-secret-value"));
        assertNotEquals(AgentCodePolicy.hash(repository.diffPreview()), repository.diffHash());
        assertFalse(mapper.find(value.id()).manifestJson().contains("fixture-secret-value"));
    }

    @Test void modelCannotSubmitItsOwnShaOrForgeReadinessThroughToolArguments() {
        preparedWorkspaces();
        var tools = new AgentChangeTools(service, access, json);
        assertThrows(AgentTool.Rejected.class, () -> tools.agentChangePrepareTool().execute(context, json.readTree("{\"requestKey\":\"bad\",\"resultSha\":\"forged\",\"tested\":true}")));
        var value = service.prepare(context, "draft", null, plan());
        for (String section : List.of("SUMMARY", "PLAN", "REPOSITORY")) {
            var arguments = json.createObjectNode().put("changeSetId", value.id()).put("section", section);
            if ("REPOSITORY".equals(section)) arguments.put("repositoryId", "server");
            Object result = tools.agentChangeDetailTool().execute(context, arguments);
            assertFalse(new AgentToolData(json).sanitize(result).path("truncated").asBoolean());
        }
        assertFalse(service.detail(context, value.id()).readyForReview());
    }

    @Test void publishesExactDraftWithCommittedIntentsAndKeepsWholeSetUnreadyUntilEveryRepositoryIsVerified() {
        preparedWorkspaces(); var value = service.prepare(context, "delivery", null, plan()); remoteFixture();
        var server = deliveryService.publish(context, value.id(), "server");
        assertEquals("REMOTE_CONFIRMED", server.get("status")); assertEquals(true, server.get("remoteConfirmed")); assertEquals(false, server.get("tested"));
        assertTrue(service.detail(context, value.id()).blockers().contains("REMOTE_DELIVERY_EVIDENCE_MISSING"));
        var web = deliveryService.publish(context, value.id(), "web"); assertEquals("UNCHANGED_CONFIRMED", web.get("status"));
        assertEquals(List.of("PLATFORM_TEST_EVIDENCE_MISSING"), service.detail(context, value.id()).blockers()); assertFalse(service.detail(context, value.id()).readyForReview());
        var again = deliveryService.publish(context, value.id(), "server"); assertEquals(server.get("id"), again.get("id"));
        verify(publishing, times(1)).publish(any(), any(), anyString(), anyString()); verify(gitLab, times(1)).create(any(), anyString(), anyString(), anyString(), anyString());
        var events = agents.listEvents("run").stream().filter(event -> "CODE_DELIVERY".equals(event.kind())).toList();
        assertTrue(events.stream().anyMatch(event -> event.payloadJson().contains("PUSHING")));
        assertTrue(events.stream().anyMatch(event -> event.payloadJson().contains("MR_CREATING")));
        assertFalse(json.writeValueAsString(events).contains("fixture-secret")); assertEquals(2, deliveryService.listForUser(value.id(), "owner").size());
    }

    @Test void lostPushResponseIsReconciledWithoutAnotherPush() {
        preparedWorkspaces(); var value = service.prepare(context, "lost-push", null, plan()); remoteFixture();
        doAnswer(invocation -> { rememberPush(invocation.getArgument(2), invocation.getArgument(3)); throw new IllegalStateException("connection lost"); }).when(publishing).publish(any(), any(), anyString(), anyString());
        assertEquals("PUSH_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status"));
        assertEquals("REMOTE_CONFIRMED", deliveryService.publish(context, value.id(), "server").get("status"));
        verify(publishing, times(1)).publish(any(), any(), anyString(), anyString());
    }

    @Test void missingUnknownPushIsNotResubmitted() {
        preparedWorkspaces(); var value = service.prepare(context, "missing-push", null, plan()); remoteFixture();
        doThrow(new IllegalStateException("connection lost")).when(publishing).publish(any(), any(), anyString(), anyString());
        assertEquals("PUSH_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status"));
        assertEquals("PUSH_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status"));
        verify(publishing, times(1)).publish(any(), any(), anyString(), anyString()); verify(gitLab, never()).create(any(), anyString(), anyString(), anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test void busyWorkspaceBeforeSendingDoesNotLeaveAnUnknownPush() {
        preparedWorkspaces(); var value = service.prepare(context, "busy-workspace", null, plan()); remoteFixture();
        doThrow(new IllegalStateException("workspace busy"))
                .doAnswer(invocation -> ((java.util.function.Supplier<Object>) invocation.getArgument(1)).get())
                .when(publishing).locked(any(), any());
        assertThrows(IllegalStateException.class, () -> deliveryService.publish(context, value.id(), "server"));
        assertEquals("NEW", deliveries.byRepository(value.id(), "server").status());
        assertEquals(0, deliveries.unresolved("run", "server"));
        verify(publishing, never()).publish(any(), any(), anyString(), anyString());
        assertEquals("REMOTE_CONFIRMED", deliveryService.publish(context, value.id(), "server").get("status"));
        verify(publishing, times(1)).publish(any(), any(), anyString(), anyString());
    }

    @Test void lostMergeRequestResponseIsFoundByOriginalBranchAndCorrelation() {
        preparedWorkspaces(); var value = service.prepare(context, "lost-mr", null, plan()); remoteFixture();
        doAnswer(invocation -> {
            rememberMergeRequest(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(4));
            throw new AgentGitLabClient.Unknown("lost response", null);
        }).when(gitLab).create(any(), anyString(), anyString(), anyString(), anyString());
        assertEquals("MR_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status"));
        assertEquals("REMOTE_CONFIRMED", deliveryService.publish(context, value.id(), "server").get("status"));
        verify(gitLab, times(1)).create(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void missingUnknownMergeRequestDoesNotBecomeNewPost() {
        preparedWorkspaces(); var value = service.prepare(context, "missing-mr", null, plan()); remoteFixture();
        doThrow(new AgentGitLabClient.Unknown("unknown", null)).when(gitLab).create(any(), anyString(), anyString(), anyString(), anyString());
        assertEquals("MR_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status"));
        assertEquals("MR_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status"));
        verify(gitLab, times(1)).create(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void protectedBranchOrDifferentExistingShaNeverGetsOverwritten() {
        preparedWorkspaces(); var value = service.prepare(context, "protected", null, plan()); remoteFixture();
        doThrow(new AgentGitLabClient.Conflict("protected")).when(gitLab).requireUnprotected(any(), anyString());
        assertEquals("CONFLICT", deliveryService.publish(context, value.id(), "server").get("status")); verifyNoInteractions(publishing);
        var next = service.prepare(context, "conflict", null, plan());
        String name = next.manifest().repositories().getFirst().sourceBranch() + "-r" + next.revision(); remoteBranches.put(name, new AgentGitLabClient.Branch(name, "f".repeat(40), false, false));
        assertEquals("CONFLICT", deliveryService.publish(context, next.id(), "server").get("status")); verifyNoInteractions(publishing);
    }

    @Test void remoteBaselineMismatchAndWrongMergeRequestCannotCountAsDelivery() {
        preparedWorkspaces(); var value = service.prepare(context, "wrong-mr", null, plan()); remoteFixture();
        doAnswer(invocation -> {
            var result = rememberMergeRequest(invocation.getArgument(0), invocation.getArgument(1), "unrelated request"); return result;
        }).when(gitLab).create(any(), anyString(), anyString(), anyString(), anyString());
        assertEquals("MR_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status"));
        doReturn(new AgentGitLabClient.Branch("development", "f".repeat(40), true, true)).when(gitLab).branch(any(), eq("development"));
        assertEquals("CONFLICT", deliveryService.publish(context, value.id(), "web").get("status"));
        assertTrue(service.detail(context, value.id()).blockers().contains("REMOTE_DELIVERY_EVIDENCE_MISSING"));
    }

    @Test void changedBindingOrChangedCodeStopsExistingDeliveryInsteadOfUpdatingIt() {
        String workspace = preparedWorkspaces(); var value = service.prepare(context, "old", null, plan()); remoteFixture();
        deliveryService.publish(context, value.id(), "server"); deliveryService.publish(context, value.id(), "web");
        gitLabPolicyText.set(gitLabPolicyText.get().replace("group/server", "different/server"));
        assertTrue(service.detail(context, value.id()).blockers().contains("REMOTE_DELIVERY_EVIDENCE_MISSING"));
        assertThrows(IllegalArgumentException.class, () -> deliveryService.publish(context, value.id(), "server"));
        gitLabPolicyText.set(gitLabPolicyText.get().replace("different/server", "group/server")); change(workspace, "next", "second fix\n");
        assertEquals("REMOTE_CONFIRMED", deliveryService.publish(context, value.id(), "server").get("status"));
        verify(publishing, times(1)).publish(any(), any(), anyString(), anyString());
    }

    @Test void newManifestCannotBypassUnknownRequestAndStaleVersionCanStillReconcile() {
        preparedWorkspaces(); var first = service.prepare(context, "unknown-old", null, plan()); remoteFixture();
        doAnswer(invocation -> { rememberPush(invocation.getArgument(2), invocation.getArgument(3)); throw new IllegalStateException("lost"); }).when(publishing).publish(any(), any(), anyString(), anyString());
        assertEquals("PUSH_UNKNOWN", deliveryService.publish(context, first.id(), "server").get("status"));
        var next = service.prepare(context, "unknown-new", null, plan());
        assertThrows(IllegalArgumentException.class, () -> deliveryService.publish(context, next.id(), "server"));
        assertEquals("BRANCH_READY", deliveryService.publish(context, first.id(), "server").get("status"));
        verify(gitLab, never()).create(any(), anyString(), anyString(), anyString(), anyString()); verify(publishing, times(1)).publish(any(), any(), anyString(), anyString());
        assertEquals(0, deliveries.unresolved("run", "server"));
    }

    @Test void permissionRevocationBetweenPushAndMergeRequestStopsFurtherWrites() {
        preparedWorkspaces(); var value = service.prepare(context, "revoked", null, plan()); remoteFixture();
        doAnswer(invocation -> {
            rememberPush(invocation.getArgument(2), invocation.getArgument(3));
            doThrow(new AccessDeniedException("revoked")).when(access).requireExecution(any(), eq(AgentDeliveryStore.TOOL), eq(AgentDeliveryStore.PERMISSIONS)); return null;
        }).when(publishing).publish(any(), any(), anyString(), anyString());
        assertThrows(AccessDeniedException.class, () -> deliveryService.publish(context, value.id(), "server"));
        assertEquals("BRANCH_READY", deliveries.byRepository(value.id(), "server").status());
        verify(gitLab, never()).create(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void cancellationDuringPushPreservesIntentAndDoesNotCreateMergeRequest() {
        preparedWorkspaces(); var value = service.prepare(context, "cancel", null, plan()); remoteFixture();
        doAnswer(invocation -> { rememberPush(invocation.getArgument(2), invocation.getArgument(3)); jdbc.update("UPDATE pm_agent_run SET cancel_requested=1 WHERE id='run'"); return null; }).when(publishing).publish(any(), any(), anyString(), anyString());
        assertThrows(AgentRunStore.StopRequested.class, () -> deliveryService.publish(context, value.id(), "server"));
        assertEquals("PUSHING", deliveries.byRepository(value.id(), "server").status()); verify(gitLab, never()).create(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void concurrentDeliveryCannotSendASecondPushAndStaleReceiptIsRecoverable() throws Exception {
        preparedWorkspaces(); var value = service.prepare(context, "parallel", null, plan()); remoteFixture();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); rememberPush(invocation.getArgument(2), invocation.getArgument(3)); return null; }).when(publishing).publish(any(), any(), anyString(), anyString());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> deliveryService.publish(context, value.id(), "server")); assertTrue(entered.await(10, TimeUnit.SECONDS));
            try { assertEquals("PUSH_UNKNOWN", deliveryService.publish(context, value.id(), "server").get("status")); } finally { release.countDown(); }
            assertThrows(ExecutionException.class, () -> first.get(10, TimeUnit.SECONDS));
        }
        assertEquals("REMOTE_CONFIRMED", deliveryService.publish(context, value.id(), "server").get("status"));
        verify(publishing, times(1)).publish(any(), any(), anyString(), anyString()); verify(gitLab, times(1)).create(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void modelCannotSupplyRemoteAddressOrClaimTestSuccess() {
        preparedWorkspaces(); var value = service.prepare(context, "arguments", null, plan()); remoteFixture();
        var tool = new AgentDeliveryTools(deliveryService, access, json).agentPublishChangeTool();
        var arguments = json.createObjectNode().put("changeSetId", value.id()).put("repositoryId", "server").put("url", "https://unbound.invalid").put("tested", true);
        assertThrows(AgentTool.Rejected.class, () -> tool.execute(context, arguments)); verifyNoInteractions(gitLab, publishing); assertTrue(deliveries.list(value.id()).isEmpty());
    }

    @SuppressWarnings("unchecked") private void remoteFixture() {
        gitLab = mock(AgentGitLabClient.class); publishing = mock(AgentCodeGit.class);
        when(gitLab.project(any())).thenAnswer(invocation -> { outsideTransaction(); return new AgentGitLabClient.Project("development"); });
        when(gitLab.branch(any(), anyString())).thenAnswer(invocation -> {
            outsideTransaction(); var target = (AgentGitLabClient.Target) invocation.getArgument(0); String branch = invocation.getArgument(1);
            return "development".equals(branch) ? new AgentGitLabClient.Branch(branch, bases.get(target.projectId() == 1 ? "server" : "web"), true, true) : remoteBranches.get(branch);
        });
        when(gitLab.find(any(), anyString(), anyString())).thenAnswer(invocation -> { outsideTransaction(); var mr = remoteRequests.get((String) invocation.getArgument(1)); return mr == null ? List.of() : List.of(mr); });
        when(gitLab.mergeRequest(any(), anyLong(), anyString(), anyString())).thenAnswer(invocation -> { outsideTransaction(); return remoteRequests.get((String) invocation.getArgument(2)); });
        when(gitLab.create(any(), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            outsideTransaction(); String branch = invocation.getArgument(1);
            assertEquals("MR_CREATING", jdbc.queryForObject("SELECT status FROM pm_agent_delivery WHERE source_branch=?", String.class, branch));
            return rememberMergeRequest(invocation.getArgument(0), branch, invocation.getArgument(4));
        });
        when(publishing.locked(any(), any())).thenAnswer(invocation -> ((java.util.function.Supplier<Object>) invocation.getArgument(1)).get());
        doAnswer(invocation -> {
            outsideTransaction(); String branch = invocation.getArgument(2);
            assertEquals("PUSHING", jdbc.queryForObject("SELECT status FROM pm_agent_delivery WHERE source_branch=?", String.class, branch));
            rememberPush(branch, invocation.getArgument(3)); return null;
        }).when(publishing).publish(any(), any(), anyString(), anyString());
        var deliveryStore = proxy(new AgentDeliveryStore(deliveries, agents, runs, service, gitLabPolicies, access, json));
        deliveryService = new AgentDeliveryService(deliveries, deliveryStore, service, code, publishing, gitLabPolicies, gitLab, access, json);
    }
    private void rememberPush(String branch, String sha) { remoteBranches.put(branch, new AgentGitLabClient.Branch(branch, sha, false, false)); }
    private AgentGitLabClient.MergeRequest rememberMergeRequest(AgentGitLabClient.Target target, String source, String description) {
        var result = new AgentGitLabClient.MergeRequest(4, target.webUri() + "/-/merge_requests/4", "opened", remoteBranches.get(source).sha(), true, description);
        remoteRequests.put(source, result); return result;
    }
    private void outsideTransaction() { assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(), "网络调用不能持有数据库事务"); }

    private AgentCodePolicy.Repository repo(String id) { return new AgentCodePolicy.Repository(id, id, scope, temp.resolve(id).toString(), "development", List.of("."), List.of("src"), true); }
    private String open(String id) { return json.valueToTree(code.open(context, id, bases.get(id))).path("workspaceId").asText(); }
    private String preparedWorkspaces() { String id = open("server"); open("web"); change(id, "first", "first fix\n"); return id; }
    private void change(String id, String key, String content) {
        var workspace = workspaces.find(id); var read = json.valueToTree(code.read(context, id, "src/Main.txt"));
        code.write(context, id, workspace.version(), "src/Main.txt", read.path("blob").asText(), content, key, false);
    }
    private AgentChangePlan plan() { return new AgentChangePlan("联合修复", "接口及页面", "原问题与关联链路回归", "按上个审核版本恢复", List.of(new AgentChangePlan.Dependency("web", "server"))); }
    private AgentChangeManifest snapshot(AgentChangeManifest.CaseReference reference) {
        return new AgentChangeManifest(1, "run", "task", context.specification(), plan(), reference,
                workspaces.list("run").stream().map(value -> code.snapshot(context, value.id())).toList(), List.of("server", "web"));
    }
    private void caseFixture() {
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        autonomy.insertCase(new AutonomyCaseEntity("case", "fingerprint", "BL000012", "TEST", "BUSINESS_MONITOR", "monitor:1", "EXECUTION_FAILURE", "任务失败", "ANALYZING", "run", "proposal", 1, 1, 2, now, now, now, null, now, now));
        autonomy.insertProposal(new AutonomyProposalEntity("proposal", "case", "run", "plan", "requestHash", 2, "{\"plan\":\"修复已确认来源的问题\"}", now));
    }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) { var proxy = new ProxyFactory(target); proxy.addAdvice(transactions); return (T) proxy.getProxy(); }
    private String command(Path directory, String... arguments) throws Exception {
        var command = new ArrayList<>(List.of("git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid")); command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true); builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null"); builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        var process = builder.start(); byte[] output = process.getInputStream().readAllBytes(); assertTrue(process.waitFor(5, TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), new String(output, StandardCharsets.UTF_8));
        return new String(output, StandardCharsets.UTF_8);
    }
}
