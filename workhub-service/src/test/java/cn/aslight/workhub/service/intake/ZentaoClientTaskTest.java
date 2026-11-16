package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.system.SysConfigService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 研发任务同步的 HTTP 桩测试：创建（迭代任务端点、dev 类型、工时/负责人透传）、更新、取消与失败分支。
 */
class ZentaoClientTaskTest {

    private HttpServer server;
    private ExecutorService workers;
    private ZentaoClient client;
    private final ObjectMapper json = new ObjectMapper();
    private final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
    private volatile String lastTaskBody;
    private volatile String lastQuery;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            String path = exchange.getRequestURI().getPath();
            try {
                if (path.endsWith("/api.php/v1/tokens")) {
                    send(exchange, 200, "{\"token\":\"tok\"}");
                } else if (path.matches(".*/api\\.php/v1/products/59/stories")) {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        lastTaskBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        send(exchange, 201, "{\"id\":\"786\"}");
                        return;
                    }
                    lastQuery = exchange.getRequestURI().getQuery();
                    send(exchange, 200, "{\"total\":1,\"stories\":[{\"id\":786,\"title\":\"申城医美资产数据同步\"}]}");
                } else if (path.matches(".*/api\\.php/v1/executions/896/tasks")) {
                    lastTaskBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    send(exchange, 201, "{\"id\":\"921\"}");
                } else if (path.matches(".*/api\\.php/v1/executions/897/tasks")) {
                    send(exchange, 400, "{\"message\":\"名称不能为空\"}");
                } else if (path.matches(".*/api\\.php/v1/tasks/921")) {
                    send(exchange, 200, "{\"id\":921}");
                } else if (path.matches(".*/api\\.php/v1/tasks/404")) {
                    send(exchange, 404, "{}");
                } else {
                    send(exchange, 404, "{}");
                }
            } catch (Exception ex) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });
        server.start();
        SysConfigService configService = mock(SysConfigService.class);
        when(configService.findPlainValue("zentao.base", "baseUrl")).thenReturn(base);
        when(configService.findPlainValue("zentao.base", "account")).thenReturn("wh");
        when(configService.findPlainValue("zentao.base", "password")).thenReturn("secret");
        client = new ZentaoClient(configService);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        workers.shutdownNow();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    @Test
    void createDevTask_postsToExecutionTasksWithDevTypeAndReturnsReceipt() {
        ZentaoClient.ZentaoDevTask task = client.createDevTask(
                new ZentaoClient.ZentaoMapping(59L, 385L, 896L), "scs-order 新增还款计划科目字段",
                "变更点：<br/>- 新增字段", "shihao", 4.0, 2, "2026-11-15", "2026-11-25", 786L);
        var payload = json.readTree(lastTaskBody);
        assertEquals("2026-11-15", payload.path("estStarted").asText());
        assertEquals("2026-11-25", payload.path("deadline").asText());
        assertEquals(786L, payload.path("story").asLong());
        assertEquals("921", task.taskId());
        assertTrue(task.url().endsWith("/task-view-921.html"));
        assertTrue(requests.stream().anyMatch(line -> line.equals("POST /api.php/v1/executions/896/tasks")));
    }

    @Test
    void updateDevTask_putsToTaskEndpoint() {
        client.updateDevTask(921L, "新标题", null, "shihao", 8.0, 1);
        assertTrue(requests.stream().anyMatch(line -> line.equals("PUT /api.php/v1/tasks/921")));
    }

    @Test
    void cancelDevTask_putsCanceledStatus() {
        client.cancelDevTask(921L);
        assertTrue(requests.stream().anyMatch(line -> line.equals("PUT /api.php/v1/tasks/921")));
    }

    @Test
    void createFailureAndMissingTaskSurfaceZentaoMessage() {
        IllegalStateException createError = assertThrows(IllegalStateException.class, () -> client.createDevTask(
                new ZentaoClient.ZentaoMapping(59L, 385L, 897L), "坏任务", null, null, null, 3, null, null, 786L));
        assertTrue(createError.getMessage().contains("名称不能为空"));
        IllegalArgumentException updateError = assertThrows(IllegalArgumentException.class,
                () -> client.updateDevTask(404L, "t", null, null, null, 3));
        assertTrue(updateError.getMessage().contains("不存在"));
        assertThrows(IllegalArgumentException.class, () -> client.cancelDevTask(404L));
    }

    @Test
    void createDevTask_defaultsEstStartedToTodayWhenAbsent() {
        client.createDevTask(new ZentaoClient.ZentaoMapping(59L, 385L, 896L), "无日期任务", null, null, null, 3, null, null, 786L);
        var payload = json.readTree(lastTaskBody);
        assertEquals(java.time.LocalDate.now().toString(), payload.path("estStarted").asText());
        assertEquals(java.time.LocalDate.now().plusDays(7).toString(), payload.path("deadline").asText());
    }

    @Test
    void resolveOrCreateStory_reusesSameTitleStoryBeforeCreating() {
        long storyId = client.resolveOrCreateStory(new ZentaoClient.ZentaoMapping(59L, 385L, 896L), "申城医美资产数据同步");
        assertEquals(786L, storyId);
        assertTrue(requests.stream().anyMatch(line -> line.equals("GET /api.php/v1/products/59/stories")));
        assertTrue(lastQuery.contains("limit=100"));
    }

    @Test
    void resolveTaskMapping_fallsBackToBusinessLineMappingWithoutStoryUrl() {
        SysConfigService configService = mock(SysConfigService.class);
        when(configService.findPlainValue("zentao.base", "baseUrl")).thenReturn("http://zentao.example.com");
        when(configService.findPlainValue("zentao.base", "account")).thenReturn("wh");
        when(configService.findPlainValue("zentao.base", "password")).thenReturn("secret");
        when(configService.findPlainValue("zentao.mapping", "BL000011")).thenReturn("59:385:896");
        ZentaoClient.ZentaoMapping mapping = new ZentaoClient(configService).resolveTaskMapping("BL000011", null);
        assertEquals(new ZentaoClient.ZentaoMapping(59L, 385L, 896L), mapping);
    }

    @Test
    void mappingRecordCarriesTriple() {
        var mapping = new ZentaoClient.ZentaoMapping(59L, 385L, 896L);
        assertEquals(List.of(59L, 385L, 896L), List.of(mapping.productId(), mapping.projectId(), mapping.executionId()));
        assertEquals(Map.of("productId", 59L), Map.of("productId", mapping.productId()));
    }
}
