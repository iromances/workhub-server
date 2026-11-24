package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentCodeMapper;
import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.system.SysConfigService;
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
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实临时 Git 仓库 + H2/生产 Mapper/事务；不接触用户仓库、不运行仓库脚本。 */
class AgentCodeWorkspaceServiceTest {
    @TempDir Path temp;
    private Path source;
    private String base;
    private Connection keepAlive;
    private AgentCodeMapper mapper;
    private AgentCodePolicy policy;
    private AgentCodeStore store;
    private AgentRunStore runs;
    private AgentAccessService access;
    private AgentCodeWorkspaceService service;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> policyText = new AtomicReference<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private AgentToolContext context;

    @BeforeEach void setup() throws Exception {
        temp = temp.toRealPath();
        source = Files.createDirectory(temp.resolve("source"));
        command(source, "init", "-b", "development");
        Files.createDirectories(source.resolve("src"));
        Files.createDirectories(source.resolve("docs"));
        Files.writeString(source.resolve("src/Main.txt"), "old implementation\n");
        Files.writeString(source.resolve("docs/README.md"), "read only documentation\n");
        Files.writeString(source.resolve(".env"), "TOKEN=dummy-test-value\n");
        Files.createSymbolicLink(source.resolve("src/link"), Path.of("Main.txt"));
        command(source, "add", ".");
        command(source, "commit", "-m", "fixture baseline");
        base = command(source, "rev-parse", "HEAD").trim();
        Files.writeString(source.resolve("src/Main.txt"), "user uncommitted changes\n");
        Files.writeString(source.resolve("untracked.txt"), "user local file\n");

        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keepAlive = dataSource.getConnection();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        ScriptUtils.executeSqlScript(keepAlive, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261104_031917__add_agent_code_workspaces.sql"));
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true); configuration.addMapper(AgentCodeMapper.class);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(AgentCodeMapper.class);
        var configs = mock(SysConfigService.class);
        when(configs.findPlainValue("agent.code", "policy")).thenAnswer(invocation -> policyText.get());
        policyText.set(json.writeValueAsString(new AgentCodePolicy.Policy(temp.resolve("workspaces").toString(), List.of(
                new AgentCodePolicy.Repository("main", "Fixture", scope, source.toString(), "development", List.of("."), List.of("src"), true)))));
        policy = new AgentCodePolicy(configs, json);
        runs = mock(AgentRunStore.class);
        doAnswer(invocation -> {
            if (!active.get() || !"lease".equals(invocation.getArgument(1))) throw new AgentRunStore.LeaseLost("old lease");
            return null;
        }).when(runs).requireActive(anyString(), any());
        access = mock(AgentAccessService.class);
        doAnswer(invocation -> {
            AgentToolContext value = invocation.getArgument(0);
            if (!value.specification().scopes().contains(invocation.getArgument(1))) throw new org.springframework.security.access.AccessDeniedException("scope");
            return null;
        }).when(access).requireScope(any(), any());
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new AgentCodeStore(mapper, mock(AgentMapper.class), runs, json)); proxy.addAdvice(interceptor);
        store = (AgentCodeStore) proxy.getProxy();
        service = service(new AgentCodeGit());
        context = context("run-one");
    }

    @AfterEach void close() throws Exception { if (keepAlive != null) keepAlive.close(); }

    @Test void copiesOnlyPinnedCommittedObjectsAndKeepsUserChanges() throws Exception {
        String before = command(source, "status", "--porcelain");
        var opened = open();
        assertEquals(opened.get("workspaceId"), object(service.open(context, "main", base)).get("workspaceId"));
        assertEquals("old implementation\n", object(service.read(context, id(opened), "src/Main.txt")).get("content"));
        assertEquals(before, command(source, "status", "--porcelain"));
        assertEquals("user uncommitted changes\n", Files.readString(source.resolve("src/Main.txt")));
        assertFalse(json.valueToTree(service.list(context, id(opened), null, 0)).toString().contains("untracked.txt"));
        assertEquals(base, mapper.find(id(opened)).candidateSha());
        assertEquals("", command(temp.resolve("workspaces").resolve(id(opened)).resolve("objects.git"), "remote"));
    }

    @Test void writeCreatesExactCandidateWithBlobCasAndIdempotency() throws Exception {
        var opened = open(); String id = id(opened);
        String blob = object(service.read(context, id, "src/Main.txt")).get("blob").toString();
        var result = object(service.write(context, id, 0, "src/Main.txt", blob, "fixed implementation\n", "write-one", false));
        var repeated = object(service.write(context, id, 0, "src/Main.txt", blob, "fixed implementation\n", "write-one", false));
        assertEquals(result.get("candidateSha"), repeated.get("candidateSha"));
        assertEquals(1, mapper.find(id).version());
        assertEquals(false, result.get("tested"));
        assertEquals(false, result.get("delivered"));
        assertEquals("fixed implementation\n", object(service.read(context, id, "src/Main.txt")).get("content"));
        assertThrows(IllegalArgumentException.class, () -> service.write(context, id, 0, "src/Main.txt", blob, "different", "write-one", false));
        assertThrows(IllegalArgumentException.class, () -> service.write(context, id, 0, "src/Main.txt", blob, "stale", "write-two", false));
        assertThrows(IllegalArgumentException.class, () -> service.write(context, id, 1, "src/Main.txt", blob, "wrong blob", "write-two", false));
        String diff = object(service.diff(context, id)).get("diff").toString();
        assertTrue(diff.contains("-old implementation")); assertTrue(diff.contains("+fixed implementation"));
        Path bare = temp.resolve("workspaces").resolve(id).resolve("objects.git");
        assertEquals(base, command(bare, "rev-parse", result.get("candidateSha") + "^").trim());
        assertEquals(base, command(source, "rev-parse", "HEAD").trim());
    }

    @Test void rejectsTraversalLinksSecretsAndReadOnlyPaths() {
        String id = id(open());
        for (String path : List.of("../secret", "/etc/passwd", "src/../../x", ".git/config", "src/.git/config", "src\\x", "src//x", ".env", "src/link", "src/link/child", "docs/README.md")) {
            assertThrows(IllegalArgumentException.class, () -> service.write(context, id, 0, path, "MISSING", "bad", "bad-write", false), path);
        }
        assertThrows(IllegalArgumentException.class, () -> service.read(context, id, ".env"));
        assertThrows(IllegalArgumentException.class, () -> service.read(context, id, "src/link"));
        assertEquals(0, mapper.find(id).version());
    }

    @Test void separateRunsCannotReadOrModifyEachOthersWorkspace() {
        var first = open();
        var second = context("run-two");
        assertThrows(IllegalArgumentException.class, () -> service.read(second, id(first), "src/Main.txt"));
        var openedSecond = object(service.open(second, "main", base));
        assertNotEquals(id(first), id(openedSecond));
        var blob = object(service.read(context, id(first), "src/Main.txt")).get("blob").toString();
        service.write(context, id(first), 0, "src/Main.txt", blob, "run-one-only", "write", false);
        assertEquals("old implementation\n", object(service.read(second, id(openedSecond), "src/Main.txt")).get("content"));
    }

    @Test void refusesMovingBaselineAndPolicyChanges() throws Exception {
        command(source, "add", "src/Main.txt"); command(source, "commit", "-m", "branch advanced");
        assertThrows(IllegalArgumentException.class, () -> open());
        var stored = mapper.findByRepository(context.runId(), "main");
        assertEquals(base, stored.baseSha()); assertEquals("PREPARING", stored.status());
        String moved = command(source, "rev-parse", "HEAD").trim();
        assertThrows(IllegalArgumentException.class, () -> service.open(context, "main", moved));
        context = context("new-run"); base = moved;
        var opened = open();
        policyText.set(policyText.get().replace("\"writablePaths\":[\"src\"]", "\"writablePaths\":[]"));
        assertThrows(IllegalArgumentException.class, () -> service.read(context, id(opened), "src/Main.txt"));
    }

    @Test void lostLeaseAfterGeneratingObjectsCannotRegisterCandidate() {
        String id = id(open());
        String blob = object(service.read(context, id, "src/Main.txt")).get("blob").toString();
        var losingGit = new AgentCodeGit() {
            @Override public String revise(Path directory, String previous, String parent, String path, String content, String mode, String message, long time) {
                String result = super.revise(directory, previous, parent, path, content, mode, message, time);
                active.set(false); return result;
            }
        };
        assertThrows(AgentRunStore.LeaseLost.class, () -> service(losingGit).write(context, id, 0, "src/Main.txt", blob, "should not apply", "expired", false));
        assertEquals(0, mapper.find(id).version()); assertEquals(base, mapper.find(id).candidateSha());
        assertNull(mapper.operation(id, "expired"));
    }

    @Test void deleteIsConfinedToCandidateAndRecordedOnce() throws Exception {
        String id = id(open());
        String blob = object(service.read(context, id, "src/Main.txt")).get("blob").toString();
        service.write(context, id, 0, "src/Main.txt", blob, null, "delete-one", true);
        assertThrows(IllegalArgumentException.class, () -> service.read(context, id, "src/Main.txt"));
        assertTrue(Files.exists(source.resolve("src/Main.txt")));
        assertEquals(1, mapper.find(id).version());
        assertEquals("MISSING", object(service.write(context, id, 0, "src/Main.txt", blob, null, "delete-one", true)).get("blob"));
    }

    @Test void searchesOnlyAuthorizedTextAndReportsCoverage() {
        String id = id(open());
        JsonNode result = json.valueToTree(service.search(context, id, "old", 0));
        assertEquals(1, result.get("matches").size());
        assertEquals("src/Main.txt", result.get("matches").get(0).get("path").asText());
        assertFalse(result.get("hasMore").asBoolean());
        JsonNode list = json.valueToTree(service.list(context, id, null, 0));
        assertEquals(2, list.get("total").asInt());
    }

    @Test void concurrentMutationsDoNotOverwriteOneAnother() throws Exception {
        String id = id(open());
        String blob = object(service.read(context, id, "src/Main.txt")).get("blob").toString();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int index = i;
                attempts.add(pool.submit(() -> {
                    barrier.await();
                    try { service.write(context, id, 0, "src/Main.txt", blob, "change-" + index, "parallel-" + index, false); return true; }
                    catch (IllegalArgumentException | IllegalStateException ex) { return false; }
                }));
            }
            int successes = 0; for (var attempt : attempts) if (attempt.get(20, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1, mapper.find(id).version());
    }

    @Test void refusesUnconfiguredOrOverlappingWorkspaceRoot() {
        policyText.set(null);
        assertThrows(AgentCodePolicy.Unavailable.class, () -> service.repositories(context));
        var unsafe = new AgentCodePolicy.Policy(source.resolve("agent-cache").toString(), List.of(
                new AgentCodePolicy.Repository("main", "Fixture", scope, source.toString(), "development", List.of("."), List.of("src"), true)));
        policyText.set(json.writeValueAsString(unsafe));
        assertThrows(IllegalArgumentException.class, this::open);
        assertFalse(Files.exists(source.resolve("agent-cache")));
    }

    @Test void pagedReadAndUniqueReplacementPreserveUnexposedParts() throws Exception {
        String original = "a".repeat(17000) + "\nunique target\n" + "z".repeat(1000);
        Files.writeString(source.resolve("src/Large.txt"), original);
        command(source, "add", "src/Large.txt"); command(source, "commit", "-m", "large fixture");
        base = command(source, "rev-parse", "HEAD").trim();
        String id = id(open());
        var first = object(service.read(context, id, "src/Large.txt"));
        assertEquals(true, first.get("truncated")); assertEquals(16000, first.get("nextOffset"));
        var second = object(service.read(context, id, "src/Large.txt", 16000, 16000));
        assertEquals(original, first.get("content").toString() + second.get("content"));
        service.replace(context, id, 0, "src/Large.txt", first.get("blob").toString(), "unique target", "fixed target", "replace-one");
        var updated = object(service.read(context, id, "src/Large.txt", 16000, 16000));
        assertTrue(updated.get("content").toString().contains("fixed target"));
        assertTrue(updated.get("content").toString().endsWith("z".repeat(1000)));
        assertThrows(IllegalArgumentException.class, () -> service.replace(context, id, 1, "src/Large.txt", updated.get("blob").toString(), "aaa", "x", "ambiguous"));
    }

    @Test void maskedFileCannotBeReplacedWholesaleAndPartialEditPreservesSecret() throws Exception {
        String original = "apiKey=fixture-secret-value\ncode=old\n";
        Files.writeString(source.resolve("src/config.txt"), original);
        command(source, "add", "src/config.txt"); command(source, "commit", "-m", "masked fixture");
        base = command(source, "rev-parse", "HEAD").trim();
        String id = id(open());
        var content = object(service.read(context, id, "src/config.txt"));
        assertEquals(true, content.get("contentMasked"));
        assertFalse(content.get("content").toString().contains("fixture-secret-value"));
        assertThrows(IllegalArgumentException.class, () -> service.write(context, id, 0, "src/config.txt", content.get("blob").toString(), "code=new", "overwrite", false));
        service.replace(context, id, 0, "src/config.txt", content.get("blob").toString(), "code=old", "code=new", "partial");
        Path bare = temp.resolve("workspaces").resolve(id).resolve("objects.git");
        assertEquals(original.replace("code=old", "code=new"), command(bare, "show", mapper.find(id).candidateSha() + ":src/config.txt"));
    }

    private AgentCodeWorkspaceService service(AgentCodeGit git) { return new AgentCodeWorkspaceService(policy, mapper, store, runs, access, git, json); }
    private AgentToolContext context(String runId) {
        var config = new AgentConfiguration("Code","OPS", "task", 1L, "model", List.of(scope), 50, 300, 1);
        return new AgentToolContext(runId, "task", "agent", "owner", "lease", config,
                new AgentTaskSpecification("fix", "", "verified", List.of("CHANGE_SET", "TEST_REPORT"), List.of(scope)));
    }
    private Map<String, Object> open() { return object(service.open(context, "main", base)); }
    private String id(Map<String, Object> result) { return result.get("workspaceId").toString(); }
    @SuppressWarnings("unchecked") private Map<String, Object> object(Object result) { return (Map<String, Object>) result; }

    private String command(Path directory, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid"));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null"); builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        var process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(output, java.nio.charset.StandardCharsets.UTF_8));
        return new String(output, java.nio.charset.StandardCharsets.UTF_8);
    }
}
