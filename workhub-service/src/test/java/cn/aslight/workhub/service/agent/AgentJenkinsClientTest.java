package cn.aslight.workhub.service.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 loopback HTTP 验证认证、队列/构建回执和未知响应；不连接真实 Jenkins。 */
class AgentJenkinsClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private AgentJenkinsClient client;
    private AgentJenkinsClient.Target target;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> parameters = Map.of("REQUEST_ID", "request-1", "COMMIT_SHA", "a".repeat(40));
    private final String job = "/jenkins/job/team/job/app/";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body,
                    exchange.getRequestHeaders().getFirst("Authorization")));
            Reply reply = replies.getOrDefault(exchange.getRequestURI().getPath(), new Reply(404, "{}", null, 0));
            try (exchange) {
                if (reply.delay > 0) Thread.sleep(reply.delay);
                if (reply.location != null) exchange.getResponseHeaders().set("Location", reply.location);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] response = reply.body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(reply.status, response.length); exchange.getResponseBody().write(response);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { /* 超时测试由客户端主动断开本地连接。 */ }
        });
        server.start();
        target = new AgentJenkinsClient.Target(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jenkins/"), List.of("team", "app"), "fixture", "dummy-token");
        client = new AgentJenkinsClient(json, Duration.ofSeconds(2));
    }
    @AfterEach void stop() { client.close(); server.stop(0); executor.shutdownNow(); }

    @Test void probeReadsParameterDefinitionsWithoutTriggeringBuild() {
        reply(job + "api/json", Map.of("fullName", "team/app", "buildable", true,
                "actions", List.of(Map.of("parameterDefinitions", List.of(Map.of("name", "REQUEST_ID"), Map.of("name", "COMMIT_SHA"))))));
        var probe = client.probe(target);
        assertTrue(probe.buildable()); assertEquals("team/app", probe.jobName());
        assertEquals(List.of("COMMIT_SHA", "REQUEST_ID"), probe.parameterNames());
        assertEquals(1, requests.size()); assertEquals("GET", requests.getFirst().method);
        assertTrue(requests.getFirst().authorization.startsWith("Basic "));
        assertFalse(target.toString().contains("dummy-token"));
        assertFalse(json.writeValueAsString(target).contains("dummy-token"));
    }

    @Test void submitsExactParametersOnceAndReturnsBoundedQueueIdentity() {
        replies.put(job + "buildWithParameters", new Reply(201, "{}", target.baseUri() + "queue/item/12/", 0));
        assertEquals(12, client.submit(target, parameters));
        assertEquals(1, requests.size()); assertEquals("POST", requests.getFirst().method);
        assertEquals("COMMIT_SHA=" + "a".repeat(40) + "&REQUEST_ID=request-1", requests.getFirst().body);
    }

    @Test void rejectsCrossOriginQueueWithoutFollowingOrLeakingCredentials() {
        replies.put(job + "buildWithParameters", new Reply(201, "{}", "https://untrusted.example/queue/item/12/", 0));
        assertThrows(AgentJenkinsClient.SubmissionUnknown.class, () -> client.submit(target, parameters));
        assertEquals(1, requests.size());
    }

    @Test void distinguishesRejectedSubmissionFromUnknownAndNeverRetriesEither() {
        replies.put(job + "buildWithParameters", new Reply(403, "{}", null, 0));
        assertThrows(AgentJenkinsClient.SubmissionRejected.class, () -> client.submit(target, parameters));
        replies.put(job + "buildWithParameters", new Reply(500, "{}", null, 0));
        assertThrows(AgentJenkinsClient.SubmissionUnknown.class, () -> client.submit(target, parameters));
        assertEquals(2, requests.size());
    }

    @Test void queueAndBuildRequireMatchingJobAndActualParameters() {
        reply("/jenkins/queue/item/12/api/json", Map.of("id", 12, "task", Map.of("url", target.jobUri("").toString()),
                "executable", Map.of("number", 21, "url", target.jobUri("21/").toString()), "actions", actions(parameters)));
        var queue = client.queue(target, 12, parameters);
        assertEquals("STARTED", queue.state()); assertEquals(21, queue.buildNumber()); assertTrue(queue.parametersMatch());
        reply(job + "21/api/json", Map.of("number", 21, "url", target.jobUri("21/").toString(), "building", false,
                "result", "SUCCESS", "queueId", 12, "actions", actions(Map.of("REQUEST_ID", "request-1", "COMMIT_SHA", "b".repeat(40)))));
        var build = client.build(target, 21, parameters);
        assertEquals("SUCCESS", build.result()); assertFalse(build.parametersMatch());
        reply("/jenkins/queue/item/12/api/json", Map.of("id", 12, "task", Map.of("url", target.baseUri() + "job/wrong/"), "actions", actions(parameters)));
        assertThrows(AgentJenkinsClient.ClientFailure.class, () -> client.queue(target, 12, parameters));
    }

    @Test void expiredQueueRecoversBuildByCorrelationButZeroMatchesRemainUnknown() {
        assertEquals("EXPIRED", client.queue(target, 12, parameters).state());
        reply("/jenkins/queue/api/json", Map.of("items", List.of()));
        reply(job + "api/json", Map.of("builds", List.of(build(21, 12, parameters))));
        var found = client.find(target, "REQUEST_ID", "request-1", parameters);
        assertEquals("FOUND", found.state()); assertEquals(List.of(21L), found.buildNumbers());
        reply(job + "api/json", Map.of("builds", List.of()));
        assertEquals("NOT_FOUND_IN_WINDOW", client.find(target, "REQUEST_ID", "request-1", parameters).state());
        assertTrue(requests.stream().noneMatch(request -> "POST".equals(request.method)));
    }

    @Test void comparesNativeBooleanParametersWithoutLosingExactCommitChecks() {
        var expected = new TreeMap<>(parameters); expected.put("RESTART", "false");
        reply(job + "21/api/json", Map.of("number", 21, "url", target.jobUri("21/").toString(), "building", true,
                "actions", List.of(Map.of("parameters", List.of(Map.of("name", "REQUEST_ID", "value", "request-1"),
                        Map.of("name", "COMMIT_SHA", "value", "a".repeat(40)), Map.of("name", "RESTART", "value", false))))));
        assertTrue(client.build(target, 21, expected).parametersMatch());
        expected.put("RESTART", "true"); assertFalse(client.build(target, 21, expected).parametersMatch());
    }

    @Test void detectsDuplicateBuildsOrChangedFixedParametersDuringRecovery() {
        reply("/jenkins/queue/api/json", Map.of("items", List.of()));
        reply(job + "api/json", Map.of("builds", List.of(build(21, 12, parameters), build(22, 13, parameters))));
        assertEquals("DUPLICATE", client.find(target, "REQUEST_ID", "request-1", parameters).state());
        reply(job + "api/json", Map.of("builds", List.of(build(21, 12, Map.of("REQUEST_ID", "request-1", "COMMIT_SHA", "b".repeat(40))))));
        assertEquals("PARAMETER_MISMATCH", client.find(target, "REQUEST_ID", "request-1", parameters).state());
    }

    @Test void readsActualDeploymentReceiptSeparatelyAndCannotTraverseArtifacts() {
        reply(job + "21/artifact/reports/deployment.json", Map.of("requestId", "request-1", "actualSha", "a".repeat(40)));
        assertEquals("a".repeat(40), client.receipt(target, 21, "reports/deployment.json").path("actualSha").asText());
        assertThrows(IllegalArgumentException.class, () -> client.receipt(target, 21, "../config.xml"));
        assertThrows(IllegalArgumentException.class, () -> client.receipt(target, 21, "reports/%2e%2e/x"));
        assertEquals(1, requests.size());
    }

    @Test void timeoutLeavesSubmissionUnknownAndDoesNotIssueSecondPost() {
        client.close(); client = new AgentJenkinsClient(json, Duration.ofMillis(150));
        replies.put(job + "buildWithParameters", new Reply(201, "{}", target.baseUri() + "queue/item/12/", 700));
        assertThrows(AgentJenkinsClient.SubmissionUnknown.class, () -> client.submit(target, parameters));
        assertTrue(requests.size() <= 1); // 连接阶段超时也不假定远端未接收。
    }

    @Test void limitsLargeResponsesAndRejectsRedirects() {
        replies.put(job + "api/json", new Reply(200, "x".repeat(1024 * 1024 + 1), null, 0));
        assertThrows(AgentJenkinsClient.ClientFailure.class, () -> client.probe(target));
        replies.put(job + "api/json", new Reply(302, "{}", "/other", 0));
        assertThrows(AgentJenkinsClient.ClientFailure.class, () -> client.probe(target));
        assertTrue(requests.stream().noneMatch(request -> request.path.equals("/other")));
    }

    @Test void rejectsUnsafeInstanceJobAndParameterInputBeforeHttp() {
        for (String url : List.of("file:///tmp", "https://user:secret@example.com/", "https://example.com/..", "https://example.com/?token=x")) {
            assertThrows(IllegalArgumentException.class, () -> new AgentJenkinsClient.Target(URI.create(url), List.of("app"), "user", "token"));
        }
        assertThrows(IllegalArgumentException.class, () -> new AgentJenkinsClient.Target(target.baseUri(), List.of(".."), "user", "token"));
        assertThrows(IllegalArgumentException.class, () -> client.submit(target, Map.of("bad name", "value")));
        assertTrue(requests.isEmpty());
    }

    private List<Object> actions(Map<String, String> values) {
        return List.of(Map.of("parameters", values.entrySet().stream().map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue())).toList()));
    }
    private Map<String, Object> build(int number, int queue, Map<String, String> values) {
        return Map.of("number", number, "queueId", queue, "url", target.jobUri(number + "/").toString(), "actions", actions(values));
    }
    private void reply(String path, Object body) { replies.put(path, new Reply(200, json.writeValueAsString(body), null, 0)); }
    private record Reply(int status, String body, String location, long delay) { }
    private record Request(String method, String path, String body, String authorization) { }
}
