package cn.aslight.workhub.service.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentGitLabClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService workers;
    private AgentGitLabClient client;
    private AgentGitLabClient.Target target;
    private volatile Handler handler;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String path;
    private volatile String body;
    private volatile String method;
    private final String sha = "a".repeat(40);

    @BeforeEach void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); workers = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(workers);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); path = exchange.getRequestURI().toString(); method = exchange.getRequestMethod();
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!"fixture-token".equals(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"))) { exchange.sendResponseHeaders(401, -1); exchange.close(); return; }
            try { handler.handle(exchange); } catch (Exception ex) { exchange.close(); }
        });
        server.start(); target = new AgentGitLabClient.Target(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/gitlab"), 17, "group/repository", "fixture-token");
        client = new AgentGitLabClient(json, Duration.ofSeconds(2));
    }
    @AfterEach void close() { if (client != null) client.close(); if (server != null) server.stop(0); if (workers != null) workers.shutdownNow(); }

    @Test void checksProjectIdentityBeforeUsingRepositoryAddress() {
        handler = exchange -> send(exchange, 200, project());
        assertEquals("development", client.project(target).defaultBranch()); assertEquals("GET", method);
        assertEquals("/gitlab/api/v4/projects/17", path);
        var foreign = new LinkedHashMap<>(project()); foreign.put("http_url_to_repo", "https://outside.invalid/group/repository.git");
        handler = exchange -> send(exchange, 200, foreign);
        assertThrows(AgentGitLabClient.Conflict.class, () -> client.project(target));
    }

    @Test void encodesBranchAndDoesNotInventMissingBranch() {
        handler = exchange -> send(exchange, 200, Map.of("name", "codex/fix", "protected", false, "default", false, "commit", Map.of("id", sha)));
        assertEquals(sha, client.branch(target, "codex/fix").sha()); assertTrue(path.endsWith("/repository/branches/codex%2Ffix"));
        handler = exchange -> send(exchange, 404, Map.of("message", "missing"));
        assertNull(client.branch(target, "codex/fix"));
    }

    @Test void refusesUncertainProtectionAndWrongBranchIdentity() {
        handler = exchange -> send(exchange, 200, Map.of("name", "codex/fix", "commit", Map.of("id", sha)));
        assertThrows(AgentGitLabClient.Conflict.class, () -> client.branch(target, "codex/fix"));
        handler = exchange -> send(exchange, 200, Map.of("name", "main", "protected", true, "default", true, "commit", Map.of("id", sha)));
        assertThrows(AgentGitLabClient.Conflict.class, () -> client.branch(target, "codex/fix"));
    }

    @Test void checksInheritedWildcardProtectionWithoutChangingIt() {
        handler = exchange -> send(exchange, 200, List.of(Map.of("name", "codex/*", "inherited", true)));
        assertThrows(AgentGitLabClient.Conflict.class, () -> client.requireUnprotected(target, "codex/fix")); assertEquals("GET", method);
        handler = exchange -> send(exchange, 200, List.of(Map.of("name", "release/*"), Map.of("name", "main")));
        assertDoesNotThrow(() -> client.requireUnprotected(target, "codex/fix"));
        handler = exchange -> { exchange.getResponseHeaders().add("X-Next-Page", "2"); send(exchange, 200, List.of()); };
        assertThrows(AgentGitLabClient.Conflict.class, () -> client.requireUnprotected(target, "codex/fix"));
    }

    @Test void searchesAllMergeRequestStatesAndRejectsIncompleteSearch() {
        var closed = mr(); closed.put("state", "closed");
        handler = exchange -> send(exchange, 200, List.of(closed));
        assertEquals("closed", client.find(target, "codex/fix", "development").getFirst().state());
        assertTrue(path.contains("state=all")); assertTrue(path.contains("source_branch=codex%2Ffix"));
        handler = exchange -> { exchange.getResponseHeaders().add("X-Next-Page", "2"); send(exchange, 200, List.of()); };
        assertThrows(AgentGitLabClient.Conflict.class, () -> client.find(target, "codex/fix", "development"));
    }

    @Test void createsOnlyDraftWithoutMergeOrBranchDeletionFlags() {
        handler = exchange -> send(exchange, 201, mr());
        var value = client.create(target, "codex/fix", "development", "Draft: WorkHub candidate", "WorkHub-Delivery: fixture");
        assertTrue(value.draft()); assertEquals(sha, value.sha()); assertEquals(4, value.iid()); assertEquals("POST", method);
        var sent = json.readTree(body); assertFalse(sent.path("remove_source_branch").asBoolean()); assertFalse(sent.path("squash").asBoolean());
        assertFalse(sent.has("auto_merge")); assertFalse(sent.has("merge_when_pipeline_succeeds")); assertEquals(6, sent.size());
        assertThrows(IllegalArgumentException.class, () -> client.create(target, "codex/fix", "development", "Ready", "x")); assertEquals(1, calls.get());
    }

    @Test void missingAsynchronousShaRemainsMissing() {
        var pending = mr(); pending.put("sha", null); handler = exchange -> send(exchange, 200, pending);
        assertNull(client.mergeRequest(target, 4, "codex/fix", "development").sha());
    }

    @Test void rejectsForeignMergeRequestAndTreatsPostReceiptMismatchAsUnknown() {
        var foreign = mr(); foreign.put("source_project_id", 99); handler = exchange -> send(exchange, 200, List.of(foreign));
        assertThrows(AgentGitLabClient.Conflict.class, () -> client.find(target, "codex/fix", "development"));
        var wrongUrl = mr(); wrongUrl.put("web_url", "https://outside.invalid/merge_requests/4");
        handler = exchange -> send(exchange, 201, wrongUrl);
        assertThrows(AgentGitLabClient.Unknown.class, () -> client.create(target, "codex/fix", "development", "Draft: candidate", "x"));
    }

    @Test void knownRejectionAndUnknownSubmissionAreDifferentAndNeverRetried() {
        handler = exchange -> send(exchange, 403, Map.of("message", "forbidden"));
        assertThrows(AgentGitLabClient.Rejected.class, () -> client.create(target, "codex/fix", "development", "Draft: candidate", "x")); assertEquals(1, calls.get());
        handler = exchange -> send(exchange, 500, Map.of("message", "unknown"));
        assertThrows(AgentGitLabClient.Unknown.class, () -> client.create(target, "codex/fix", "development", "Draft: candidate", "x")); assertEquals(2, calls.get());
    }

    @Test void doesNotFollowRedirectOrLeakTokenThroughSerialization() {
        handler = exchange -> { exchange.getResponseHeaders().add("Location", target.baseUri() + "unexpected"); exchange.sendResponseHeaders(302, -1); exchange.close(); };
        assertThrows(AgentGitLabClient.Failure.class, () -> client.project(target)); assertEquals(1, calls.get());
        assertFalse(target.toString().contains("fixture-token")); assertFalse(json.writeValueAsString(target).contains("fixture-token"));
        assertThrows(IllegalArgumentException.class, () -> new AgentGitLabClient.Target(URI.create("https://user:secret@example.invalid"), 1, "group/repo", "secret"));
        assertThrows(IllegalArgumentException.class, () -> new AgentGitLabClient.Target(URI.create("https://example.invalid"), 1, "../repo", "secret"));
    }

    @Test void boundsResponseAndSubmissionTimeout() {
        handler = exchange -> send(exchange, 200, Map.of("oversize", "x".repeat(1024 * 1024)));
        assertThrows(AgentGitLabClient.Failure.class, () -> client.project(target));
        client.close(); client = new AgentGitLabClient(json, Duration.ofMillis(100));
        handler = exchange -> { Thread.sleep(500); send(exchange, 201, mr()); };
        assertThrows(AgentGitLabClient.Unknown.class, () -> client.create(target, "codex/fix", "development", "Draft: candidate", "x")); assertEquals(2, calls.get());
    }

    private Map<String, Object> project() { return Map.of("id", 17, "path_with_namespace", "group/repository", "http_url_to_repo", target.repositoryUri().toString(), "web_url", target.webUri().toString(), "archived", false, "default_branch", "development"); }
    private Map<String, Object> mr() {
        var result = new LinkedHashMap<String, Object>(); result.put("project_id", 17); result.put("source_project_id", 17); result.put("target_project_id", 17);
        result.put("iid", 4); result.put("source_branch", "codex/fix"); result.put("target_branch", "development"); result.put("state", "opened");
        result.put("sha", sha); result.put("draft", true); result.put("description", "WorkHub-Delivery: fixture"); result.put("web_url", target.webUri() + "/-/merge_requests/4"); return result;
    }
    private void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = json.writeValueAsBytes(value); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length); try (var output = exchange.getResponseBody()) { output.write(bytes); } finally { exchange.close(); }
    }
    private interface Handler { void handle(HttpExchange exchange) throws Exception; }
}
