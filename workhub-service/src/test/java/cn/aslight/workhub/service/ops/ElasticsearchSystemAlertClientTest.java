package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.service.system.SysConfigService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchSystemAlertClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void shouldReadAllPitPagesMapEcsFieldsAndClosePit() throws Exception {
        AtomicInteger searchCount = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        List<String> searchBodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/workhub-logs-*/_pit", exchange -> respond(exchange, 200, "{\"id\":\"pit-1\"}"));
        server.createContext("/_search", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            searchBodies.add(body);
            if (searchCount.getAndIncrement() == 0) {
                respond(exchange, 200, """
                        {"pit_id":"pit-2","hits":{"hits":[{
                          "_index":"workhub-logs-2026.07.21","_id":"event-1",
                          "_source":{"@timestamp":"2026-07-21T02:03:04.123Z",
                            "service":{"name":"asset-payment","environment":"prod"},
                            "log":{"level":"ERROR","logger":"PaymentService"},
                            "message":"fallback","error":{"type":"java.lang.IllegalStateException","message":"支付回调失败","stack_trace":"stack"},
                            "trace":{"id":"trace-1"},"request":{"id":"req-1"}},
                          "sort":["2026-07-21T02:03:04.123Z",42]
                        }]}}
                        """);
            } else {
                respond(exchange, 200, "{\"pit_id\":\"pit-3\",\"hits\":{\"hits\":[]}}");
            }
        });
        server.createContext("/_pit", exchange -> {
            closed.set("DELETE".equals(exchange.getRequestMethod()));
            respond(exchange, 200, "{\"succeeded\":true}");
        });
        server.start();

        ElkAlertProperties properties = properties();
        properties.setPageSize(1);
        ElasticsearchSystemAlertClient client = new ElasticsearchSystemAlertClient(properties);
        List<ElkSystemAlertLog> events = new ArrayList<>();

        SystemAlertLogSource.SyncResult result = client.readErrors(null, List.of("asset-payment"), "prod",
                Instant.parse("2026-07-21T02:00:00Z"), events::add);

        assertEquals(1, result.fetchedCount());
        assertEquals("支付回调失败", events.getFirst().message());
        assertEquals("java.lang.IllegalStateException", events.getFirst().errorType());
        assertEquals("trace-1", events.getFirst().traceId());
        assertEquals(64, events.getFirst().sourceEventId().length());
        assertEquals(2, searchCount.get());
        assertFalse(searchBodies.getFirst().contains("search_after"));
        assertTrue(searchBodies.get(1).contains("search_after"));
        assertTrue(searchBodies.getFirst().contains("_shard_doc"));
        assertTrue(closed.get());
    }

    @Test
    void shouldReturnSafeAuthenticationFailureWithoutResponseBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/workhub-logs-*/_pit", exchange ->
                respond(exchange, 401, "secret-token-must-not-leak"));
        server.start();

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new ElasticsearchSystemAlertClient(properties()).readErrors(
                        null, List.of("asset-payment"), "prod", Instant.now(), ignored -> { }));

        assertEquals("ELK认证或授权失败", error.getMessage());
        assertFalse(error.getMessage().contains("secret-token"));
    }

    @Test
    void shouldRejectInvalidJsonAndStillClosePit() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/workhub-logs-*/_pit", exchange -> respond(exchange, 200, "{\"id\":\"pit-1\"}"));
        server.createContext("/_search", exchange -> respond(exchange, 200, "not-json"));
        server.createContext("/_pit", exchange -> {
            closed.set(true);
            respond(exchange, 200, "{\"succeeded\":true}");
        });
        server.start();

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new ElasticsearchSystemAlertClient(properties()).readErrors(
                        null, List.of("asset-payment"), "prod", Instant.now(), ignored -> { }));

        assertEquals("ELK响应不是有效JSON", error.getMessage());
        assertTrue(closed.get());
    }

    @Test
    void shouldOmitServiceFilterForAllModeAndUseTermsForSelectedServices() throws Exception {
        List<String> bodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/workhub-logs-*/_pit", exchange ->
                respond(exchange, 200, "{\"id\":\"pit-1\"}"));
        server.createContext("/_search", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"pit_id\":\"pit-2\",\"hits\":{\"hits\":[]}}");
        });
        server.createContext("/_pit", exchange -> respond(exchange, 200, "{\"succeeded\":true}"));
        server.start();
        ElasticsearchSystemAlertClient client = new ElasticsearchSystemAlertClient(properties());

        client.readErrors(null, List.of(), "prod", Instant.now(), ignored -> { });
        client.readErrors(null, List.of("asset-payment", "amp-saps"), "prod", Instant.now(), ignored -> { });

        assertFalse(bodies.getFirst().contains("service.name"));
        assertTrue(bodies.get(1).contains("\"terms\":{\"service.name\":[\"asset-payment\",\"amp-saps\"]}"));
    }

    @Test
    void shouldQueryAndMapObservedAmpSapsFieldsWithoutEnvironmentFilter() throws Exception {
        AtomicBoolean queried = new AtomicBoolean();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/amp-saps-*/_pit", exchange ->
                respond(exchange, 200, "{\"id\":\"pit-1\"}"));
        server.createContext("/_search", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"appName.keyword\":\"amp-saps\""));
            assertTrue(body.contains("\"level.keyword\":\"ERROR\""));
            assertFalse(body.contains("service.environment"));
            queried.set(true);
            respond(exchange, 200, """
                    {"pit_id":"pit-2","hits":{"hits":[{
                      "_index":"amp-saps-2026.07.23","_id":"event-1",
                      "_source":{"@timestamp":"2026-07-23T02:03:04.123Z",
                        "appName":"amp-saps","level":"ERROR","logger_name":"OrderService",
                        "message":"订单处理失败","stack_trace":"stack","TID":"tid-1"},
                      "sort":["2026-07-23T02:03:04.123Z",42]
                    }]}}
                    """);
        });
        server.createContext("/_pit", exchange -> respond(exchange, 200, "{\"succeeded\":true}"));
        server.start();

        SysConfigService sysConfigService = mock(SysConfigService.class);
        Map<String, String> values = Map.of(
                "enabled", "true",
                "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort(),
                "indexPattern", "amp-saps-*",
                "authType", "NONE"
        );
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP)).thenReturn(values);
        ElasticsearchSystemAlertClient client = new ElasticsearchSystemAlertClient(
                new ElkAlertRuntimeConfigService(new ElkAlertProperties(), sysConfigService),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                java.net.http.HttpClient.newHttpClient());
        List<ElkSystemAlertLog> events = new ArrayList<>();

        client.readErrors("amp-saps-*", List.of("amp-saps"), "prod",
                Instant.parse("2026-07-23T02:00:00Z"), events::add);

        assertTrue(queried.get());
        assertEquals(1, events.size());
        assertEquals("amp-saps", events.getFirst().serviceName());
        assertEquals("OrderService", events.getFirst().title());
        assertEquals("订单处理失败", events.getFirst().message());
        assertEquals("tid-1", events.getFirst().traceId());
        assertEquals("stack", events.getFirst().stackTrace());
    }

    @Test
    void shouldStopAfterConfiguredMaximumEventsPerRun() throws Exception {
        AtomicInteger searchCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/amp-saps-*/_pit", exchange ->
                respond(exchange, 200, "{\"id\":\"pit-1\"}"));
        server.createContext("/_search", exchange -> {
            searchCount.incrementAndGet();
            respond(exchange, 200, """
                    {"pit_id":"pit-2","hits":{"hits":[
                      {"_index":"amp-saps-2026.07.23","_id":"event-2",
                       "_source":{"@timestamp":"2026-07-23T02:04:04.123Z","appName":"amp-saps","level":"ERROR","message":"new"},
                       "sort":["2026-07-23T02:04:04.123Z",43]},
                      {"_index":"amp-saps-2026.07.23","_id":"event-1",
                       "_source":{"@timestamp":"2026-07-23T02:03:04.123Z","appName":"amp-saps","level":"ERROR","message":"old"},
                       "sort":["2026-07-23T02:03:04.123Z",42]}
                    ]}}
                    """);
        });
        server.createContext("/_pit", exchange -> respond(exchange, 200, "{\"succeeded\":true}"));
        server.start();

        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValues(ElkAlertRuntimeConfigService.CONFIG_GROUP)).thenReturn(Map.of(
                "enabled", "true",
                "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort(),
                "indexPattern", "amp-saps-*",
                "authType", "NONE",
                "maxEventsPerRun", "1"
        ));
        ElasticsearchSystemAlertClient client = new ElasticsearchSystemAlertClient(
                new ElkAlertRuntimeConfigService(new ElkAlertProperties(), sysConfigService),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                java.net.http.HttpClient.newHttpClient());
        List<ElkSystemAlertLog> events = new ArrayList<>();

        SystemAlertLogSource.SyncResult result = client.readErrors(
                "amp-saps-*", List.of("amp-saps"), "local", Instant.parse("2026-07-23T02:00:00Z"), events::add);

        assertEquals(1, searchCount.get());
        assertEquals(1, result.fetchedCount());
        assertEquals(List.of("new"), events.stream().map(ElkSystemAlertLog::message).toList());
        assertEquals(Instant.parse("2026-07-23T02:04:04.123Z"), result.latestOccurredAt());
    }

    @Test
    void shouldSearchControlledLevelsTimePhraseAndIdentifiers() throws Exception {
        List<String> bodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/scf-payment-*,scf-saps-*/_pit", exchange ->
                respond(exchange, 200, "{\"id\":\"pit-1\"}"));
        server.createContext("/_search", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"pit_id":"pit-2","hits":{"hits":[{
                      "_index":"scf-payment-2026.08.23","_id":"event-1",
                      "_source":{"@timestamp":"2026-08-22T23:59:00Z",
                        "service":{"name":"scf-payment","environment":"prod"},
                        "log":{"level":"INFO","logger":"BcmEventServer"},
                        "message":"查询当日交易明细返回条数为空！",
                        "trace":{"id":"trace-1"},"request":{"id":"req-1"}},
                      "sort":["2026-08-22T23:59:00Z",42]
                    }]}}
                    """);
        });
        server.createContext("/_pit", exchange -> respond(exchange, 200, "{\"succeeded\":true}"));
        server.start();

        ElasticsearchSystemAlertClient.LogSearchResult result =
                new ElasticsearchSystemAlertClient(properties()).searchLogs(
                        "scf-payment-*,scf-saps-*", List.of("scf-payment"), "prod",
                        List.of("INFO", "ERROR"),
                        Instant.parse("2026-08-22T23:50:00Z"), Instant.parse("2026-08-23T00:00:00Z"),
                        "查询当日交易明细", "trace-1", "req-1", 10);

        assertEquals(1, result.items().size());
        assertFalse(result.truncated());
        String body = bodies.getFirst();
        assertTrue(body.contains("\"terms\":{\"log.level\":[\"INFO\",\"ERROR\"]}"));
        assertTrue(body.contains("\"lte\":\"2026-08-23T00:00:00Z\""));
        assertTrue(body.contains("\"match_phrase\":{\"error.message\":\"查询当日交易明细\"}"));
        assertTrue(body.contains("\"trace.id\":\"trace-1\""));
        assertTrue(body.contains("\"http.request.id\":\"req-1\""));
    }

    private ElkAlertProperties properties() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRequestTimeoutSeconds(2);
        properties.setConnectTimeoutSeconds(2);
        return properties;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
