package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.system.SysConfigService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 禅道只读查询的 HTTP 桩测试：验证关键词过滤、作用域端点 404 回退全局、详情 404 报错。
 */
class ZentaoClientQueryTest {

    private HttpServer server;
    private ExecutorService workers;
    private ZentaoClient client;
    private volatile String lastPath;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().toString();
            String path = exchange.getRequestURI().getPath();
            try {
                if (path.endsWith("/api.php/v1/tokens")) {
                    send(exchange, 200, "{\"token\":\"tok\"}");
                } else if (path.matches(".*/api\\.php/v1/products/59/bugs")) {
                    // 作用域端点缺失：验证回退全局列表
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                } else if (path.matches(".*/api\\.php/v1/bugs/101")) {
                    send(exchange, 200, "{\"id\":101,\"title\":\"登录失败\",\"steps\":\"详见附件\"}");
                } else if (path.matches(".*/api\\.php/v1/bugs/404")) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                } else if (path.endsWith("/api.php/v1/bugs")) {
                    if (exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().contains("page=2")) {
                        // 翻到第 2 页返回空列表，避免桩重复同一页数据
                        send(exchange, 200, "{\"page\":2,\"total\":3,\"bugs\":[]}");
                        return;
                    }
                    send(exchange, 200, """
                            {"page":1,"total":3,"bugs":[
                              {"id":101,"title":"登录失败","product":59,"keywords":"login"},
                              {"id":102,"title":"导出超时","product":59,"keywords":""},
                              {"id":103,"title":"登录页样式","product":60,"keywords":""}
                            ]}
                            """);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                }
            } catch (Exception ignored) {
                exchange.close();
            }
        });
        server.start();
        SysConfigService config = mock(SysConfigService.class);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/zentao";
        when(config.findPlainValue("zentao.base", "baseUrl")).thenReturn(baseUrl);
        when(config.findPlainValue("zentao.base", "account")).thenReturn("admin");
        when(config.findPlainValue("zentao.base", "password")).thenReturn("secret");
        client = new ZentaoClient(config);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    @Test
    void searchBugsFiltersByKeywordAndId() {
        List<JsonNode> matched = client.searchBugs(null, null, "登录", 20);

        assertEquals(2, matched.size());
        assertEquals("101", matched.getFirst().path("id").asText());
    }

    @Test
    void searchBugsFallsBackToGlobalWhenScopedEndpointMissing() {
        List<JsonNode> matched = client.searchBugs(59L, null, null, 20);

        assertEquals(2, matched.size());
        // 作用域端点 404 后回退全局列表（末次请求翻到第 2 页）
        assertTrue(lastPath.contains("/api.php/v1/bugs?limit=100&page=2"));
    }

    @Test
    void searchBugsScopedByExecutionUsesScopedEndpoint() {
        List<JsonNode> matched = client.searchBugs(null, 896L, "导出", 20);

        // 作用域端点 404 回退后按 execution=896 过滤，桩内数据无该迭代字段 → 命中 0
        assertEquals(0, matched.size());
        assertTrue(lastPath.contains("/api.php/v1/bugs?"));
    }

    @Test
    void getBugReturnsDetail() {
        JsonNode bug = client.getBug(101);

        assertEquals("登录失败", bug.path("title").asText());
    }

    @Test
    void getBugRejectsMissingBug() {
        var ex = assertThrows(IllegalArgumentException.class, () -> client.getBug(404));
        assertTrue(ex.getMessage().contains("缺陷"));
    }

    @Test
    void viewUrlBuildsPageLinkWithoutNetwork() {
        assertEquals("http://zentao.example/bug-view-101.html", clientWithBaseUrl("http://zentao.example").viewUrl("bug", 101));
        assertEquals("http://zentao.example/execution-view-896.html", clientWithBaseUrl("http://zentao.example/").viewUrl("execution", 896));
    }

    private ZentaoClient clientWithBaseUrl(String baseUrl) {
        SysConfigService config = mock(SysConfigService.class);
        when(config.findPlainValue("zentao.base", "baseUrl")).thenReturn(baseUrl);
        return new ZentaoClient(config);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
