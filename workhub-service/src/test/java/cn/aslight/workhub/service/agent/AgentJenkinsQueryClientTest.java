package cn.aslight.workhub.service.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jenkins 只读查询方法的 HTTP 桩测试：Job 列表/详情、构建详情与日志尾读；不触发任何构建。
 */
class AgentJenkinsQueryClientTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService workers;
    private AgentJenkinsClient client;
    private AgentJenkinsClient.Target target;
    private volatile Handler handler;
    private volatile String lastPath;
    private volatile int callCount;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        String credentials = Base64.getEncoder().encodeToString("deploy:tok".getBytes(StandardCharsets.UTF_8));
        server.createContext("/", exchange -> {
            callCount++;
            lastPath = exchange.getRequestURI().toString();
            if (!("Basic " + credentials).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            try {
                handler.handle(exchange);
            } catch (Exception ignored) {
                exchange.close();
            }
        });
        server.start();
        target = new AgentJenkinsClient.Target(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jenkins"),
                List.of("folder", "job"), "deploy", "tok");
        client = new AgentJenkinsClient(json, Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    @Test
    void listJobsAtRootQueriesInstanceUri() {
        handler = exchange -> send(exchange, 200, "{\"jobs\":[{\"name\":\"job\",\"color\":\"blue\"}]}");

        JsonNode jobs = client.listJobs(target, List.of(), 50);

        assertEquals("job", jobs.path("jobs").get(0).path("name").asText());
        assertTrue(lastPath.startsWith("/jenkins/api/json?tree="));
        assertFalse(lastPath.contains("job/"));
    }

    @Test
    void listJobsUnderFolderQueriesJobUri() {
        handler = exchange -> send(exchange, 200, "{\"jobs\":[]}");

        client.listJobs(target, List.of("group", "sub"), 50);

        assertTrue(lastPath.contains("/jenkins/job/group/job/sub/api/json"));
    }

    @Test
    void jobDetailQueriesBoundJob() {
        handler = exchange -> send(exchange, 200, "{\"fullName\":\"folder/job\",\"buildable\":true}");

        JsonNode detail = client.jobDetail(target);

        assertEquals("folder/job", detail.path("fullName").asText());
        // jobUri 最后一段即 Job 名：folder=folder、job=job → /job/folder/job/job/api/json
        assertTrue(lastPath.contains("/jenkins/job/folder/job/job/api/json"), "actual path: " + lastPath);
    }

    @Test
    void buildDetailQueriesBuildApi() {
        handler = exchange -> send(exchange, 200, "{\"number\":12,\"result\":\"SUCCESS\"}");

        JsonNode detail = client.buildDetail(target, 12);

        assertEquals("SUCCESS", detail.path("result").asText());
        assertTrue(lastPath.contains("/jenkins/job/folder/job/job/12/api/json"), "actual path: " + lastPath);
    }

    @Test
    void buildDetailRejectsNonPositiveNumber() {
        assertThrows(IllegalArgumentException.class, () -> client.buildDetail(target, 0));
    }

    @Test
    void buildLogTailReturnsHeadWhenWithinCap() {
        handler = exchange -> {
            exchange.getResponseHeaders().add("X-Text-Size", "5");
            send(exchange, 200, "hello");
        };

        AgentJenkinsClient.LogTail tail = client.buildLogTail(target, 12, 65536);

        assertEquals("hello", tail.content());
        assertFalse(tail.headTruncated());
        assertEquals(1, callCount);
    }

    @Test
    void buildLogTailFetchesTailWhenExceedingCap() {
        handler = exchange -> {
            if (!exchange.getRequestURI().getQuery().contains("start=0")) {
                send(exchange, 200, "tail-part");
                return;
            }
            exchange.getResponseHeaders().add("X-Text-Size", "1000");
            send(exchange, 200, "head-part");
        };

        AgentJenkinsClient.LogTail tail = client.buildLogTail(target, 12, 256);

        assertEquals("tail-part", tail.content());
        assertTrue(tail.headTruncated());
        assertEquals(2, callCount);
        assertTrue(lastPath.contains("start=744"));
    }

    @Test
    void buildLogTailRejectsOversizedBytesAndMissingBuild() {
        assertThrows(IllegalArgumentException.class, () -> client.buildLogTail(target, 12, 300000));
        handler = exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        };
        var ex = assertThrows(AgentJenkinsClient.ClientFailure.class, () -> client.buildLogTail(target, 12, 1024));
        assertTrue(ex.getMessage().contains("404"));
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
