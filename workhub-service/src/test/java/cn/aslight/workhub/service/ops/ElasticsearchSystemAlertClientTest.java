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
                        {"timed_out":false,"_shards":{"total":1,"successful":1,"failed":0},"pit_id":"pit-2","hits":{"hits":[{
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
                respond(exchange, 200, "{\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0},\"pit_id\":\"pit-3\",\"hits\":{\"hits\":[]}}");
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
            respond(exchange, 200, "{\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0},\"pit_id\":\"pit-2\",\"hits\":{\"hits\":[]}}");
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
                    {"timed_out":false,"_shards":{"total":1,"successful":1,"failed":0},"pit_id":"pit-2","hits":{"hits":[{
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
                    {"timed_out":false,"_shards":{"total":1,"successful":1,"failed":0},"pit_id":"pit-2","hits":{"hits":[
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
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(request);
            if (request.contains("\"aggs\"")) {
                assertTrue(request.contains("\"id\":\"pit-2\""));
                assertFalse(request.contains("trace.id")); assertFalse(request.contains("log.level"));
                respond(exchange, 200, activityResponse(List.of("2026-08-22T23:59:00Z"))); return;
            }
            respond(exchange, 200, """
                    {"timed_out":false,"_shards":{"total":1,"successful":1,"failed":0},"pit_id":"pit-2","hits":{"hits":[{
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

    @Test
    void shouldRejectPartialAndMalformedPagesRatherThanReturnEmptySuccess() throws Exception {
        var response = new java.util.concurrent.atomic.AtomicReference<String>();
        AtomicInteger closed = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/workhub-logs-*/_pit", e -> respond(e, 200, "{\"id\":\"pit-1\"}"));
        server.createContext("/_search", e -> respond(e, 200, response.get()));
        server.createContext("/_pit", e -> { closed.incrementAndGet(); respond(e, 200, "{}"); }); server.start();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var valid = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(completeEmpty());
        var timed = valid.deepCopy().put("timed_out", true);
        var failed = valid.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)failed.path("_shards")).put("failed", 1);
        var missing = valid.deepCopy(); missing.remove("_shards");
        var unknown = valid.deepCopy(); unknown.remove("timed_out");
        var zero = valid.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)zero.path("_shards")).put("total", 0).put("successful", 0);
        var early = valid.deepCopy().put("terminated_early", true);
        for (var value : List.of(timed, failed, missing, unknown, zero, early)) {
            response.set(value.toString());
            var client = new ElasticsearchSystemAlertClient(properties());
            assertThrows(IllegalStateException.class, () -> client.readErrors(null,List.of(),"TEST",Instant.EPOCH, ignored -> {}));
            assertThrows(IllegalStateException.class, () -> query(client,List.of()));
        }
        response.set(oneHit("not-a-time", true));
        assertThrows(IllegalStateException.class, () -> query(new ElasticsearchSystemAlertClient(properties()), List.of()));
        var properties = properties(); properties.setPageSize(1);
        response.set(oneHit("2026-08-22T23:59:00Z", false));
        assertThrows(IllegalStateException.class, () -> query(new ElasticsearchSystemAlertClient(properties), List.of()));
        assertEquals(14, closed.get());
    }

    @Test
    void shouldBindEveryExpectedServiceActivityToTheSamePitAndUnfilteredTimeRange() throws Exception {
        var requests = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        AtomicBoolean closed = new AtomicBoolean();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/workhub-logs-*/_pit", e -> {
            assertTrue(e.getRequestURI().getQuery().contains("allow_partial_search_results=false"));
            respond(e,200,"{\"id\":\"pit-1\"}");
        });
        server.createContext("/_search", e -> {
            var request=json.readTree(e.getRequestBody()); requests.add(request);
            assertTrue(e.getRequestURI().getQuery().contains("allow_partial_search_results=false"));
            if(request.has("aggs")) respond(e,200,activityResponse(java.util.Arrays.asList("2026-08-22T23:59:00Z",null)));
            else respond(e,200,completeEmpty());
        });
        server.createContext("/_pit", e -> { closed.set(json.readTree(e.getRequestBody()).path("id").asText().equals("pit-activity")); respond(e,200,"{}"); }); server.start();
        var result=query(new ElasticsearchSystemAlertClient(properties()),List.of("a","b"));
        assertFalse(result.truncated()); assertTrue(result.items().isEmpty());
        assertEquals(2,result.receipt().activities().size());
        assertEquals(0,result.receipt().activities().get(1).count()); assertEquals(null,result.receipt().activities().get(1).latestAt());
        assertEquals(Instant.parse("2026-08-22T23:59:00Z"),result.receipt().activities().getFirst().latestAt());
        assertEquals(64,result.receipt().targetBindingHash().length()); assertTrue(closed.get());
        assertEquals("pit-2",requests.get(1).at("/pit/id").asText());
        assertFalse(requests.get(1).path("query").toString().contains("log.level"));
        assertTrue(requests.get(1).path("query").toString().contains("service.environment"));
        assertTrue(requests.get(1).path("query").toString().contains("2026-08-23T00:00:00Z"));
        assertEquals("a",requests.get(1).at("/aggs/activity/filters/filters/s0/term/service.name").asText());
    }

    @Test
    void shouldRejectRepeatedPagesAndMissingActivityBuckets() throws Exception {
        var response = new java.util.concurrent.atomic.AtomicReference<String>(oneHit("2026-08-22T23:59:00Z",true));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/workhub-logs-*/_pit", e -> respond(e,200,"{\"id\":\"pit-1\"}"));
        server.createContext("/_search",e -> respond(e,200,response.get()));
        server.createContext("/_pit",e -> respond(e,200,"{}")); server.start();
        var properties=properties(); properties.setPageSize(1);
        assertThrows(IllegalStateException.class,()->query(new ElasticsearchSystemAlertClient(properties),List.of()));
        assertThrows(IllegalStateException.class,()->new ElasticsearchSystemAlertClient(properties).readErrors(null,List.of(),"TEST",Instant.EPOCH,ignored->{}));
        response.set(completeEmpty());
        assertThrows(IllegalStateException.class,()->query(new ElasticsearchSystemAlertClient(properties()),List.of("a")));
    }

    @Test
    void shouldMarkTruncatedLogContent() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var response=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(oneHit("2026-08-22T23:59:00Z",true));
        ((com.fasterxml.jackson.databind.node.ObjectNode)response.at("/hits/hits/0/_source")).put("error", "unused").put("message","x".repeat(8001));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/workhub-logs-*/_pit",e->respond(e,200,"{\"id\":\"pit-1\"}"));
        server.createContext("/_search",e->respond(e,200,response.toString()));
        server.createContext("/_pit",e->respond(e,200,"{}")); server.start();
        assertTrue(query(new ElasticsearchSystemAlertClient(properties()),List.of()).truncated());
    }

    private ElasticsearchSystemAlertClient.LogSearchResult query(ElasticsearchSystemAlertClient client,List<String> services) {
        return client.searchLogs(null,services,"TEST",List.of("ERROR"),Instant.parse("2026-08-22T23:50:00Z"),Instant.parse("2026-08-23T00:00:00Z"),null,null,null,10);
    }
    private String completeEmpty() { return """
        {"timed_out":false,"_shards":{"total":1,"successful":1,"failed":0},"pit_id":"pit-2","hits":{"hits":[]}}
        """; }
    private String oneHit(String time,boolean sort) throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var root=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(completeEmpty());
        var hit=((com.fasterxml.jackson.databind.node.ArrayNode)root.at("/hits/hits")).addObject().put("_index","logs").put("_id","one");
        hit.putObject("_source").put("@timestamp",time).put("message","fixture");
        if(sort) hit.putArray("sort").add(time).add(1);
        return root.toString();
    }
    private String activityResponse(List<String> times) throws IOException {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var root=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(completeEmpty()); root.put("pit_id","pit-activity");
        var buckets=root.putObject("aggregations").putObject("activity").putObject("buckets");
        for(int i=0;i<times.size();i++) {
            String time=times.get(i); var bucket=buckets.putObject("s"+i).put("doc_count",time==null?0:2);
            var hits=bucket.putObject("latest").putObject("hits").putArray("hits");
            if(time!=null) hits.addObject().put("_index","logs").put("_id","active"+i).putObject("_source").put("@timestamp",time);
        }
        return root.toString();
    }

    @Test
    void persistedErrorPagePositionContinuesSamePitAcrossClientInstances() throws Exception {
        var open=new AtomicInteger();var close=new AtomicInteger();var requests=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var calls=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/workhub-logs-*/_pit",e->{open.incrementAndGet();respond(e,200,"{\"id\":\"pit-1\"}");});
        String hit=oneHit("2026-08-22T23:59:00Z",true);
        String discovered=discoveredActivity("2026-08-22T23:59:00Z");
        server.createContext("/_search",e->{var body=json.readTree(e.getRequestBody());requests.add(body);respond(e,200,body.has("aggs")?discovered:calls.getAndIncrement()==0?hit:completeEmpty());});
        server.createContext("/_pit",e->{close.incrementAndGet();respond(e,200,"{}");});server.start();
        var properties=properties();properties.setPageSize(1);
        Instant from=Instant.parse("2026-08-22T23:50:00Z"),to=Instant.parse("2026-08-23T00:00:00Z");
        var first=new ElasticsearchSystemAlertClient(properties).readErrorPage(null,List.of(),"TEST",from,to,null);
        assertFalse(first.complete());assertEquals(0,close.get());assertEquals(1,first.events().size());
        var saved=json.readValue(json.writeValueAsString(first.next()),SystemAlertLogSource.Position.class);
        var second=new ElasticsearchSystemAlertClient(properties).readErrorPage(null,List.of(),"TEST",from,to,saved);
        assertTrue(second.complete());assertEquals(null,second.next());assertEquals(1,open.get());assertEquals(1,close.get());
        assertEquals("pit-2",requests.get(1).at("/pit/id").asText());assertEquals(json.readTree(saved.searchAfterJson()),requests.get(1).path("search_after"));
        assertTrue(requests.get(1).toString().contains("2026-08-23T00:00:00Z"));
        assertEquals(first.execution().targetBindingHash(),second.execution().targetBindingHash());
    }

    @Test
    void allModeActivityUsesSamePitAndInvalidBucketsRemainUnprovenWithoutLosingErrors() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var requests=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var response=new java.util.concurrent.atomic.AtomicReference<>(discoveredActivity("2026-08-22T23:59:00Z"));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/workhub-logs-*/_pit",e->respond(e,200,"{\"id\":\"pit-1\"}"));
        server.createContext("/_search",e->{var body=json.readTree(e.getRequestBody());requests.add(body);respond(e,200,body.has("aggs")?response.get():completeEmpty());});
        server.createContext("/_pit",e->respond(e,200,"{}"));server.start();
        var client=new ElasticsearchSystemAlertClient(properties());var from=Instant.parse("2026-08-22T23:50:00Z");var to=Instant.parse("2026-08-23T00:00:00Z");
        var page=client.readErrorPage(null,List.of(),"TEST",from,to,null);assertTrue(page.complete());assertEquals("a",page.execution().activities().getFirst().serviceName());
        assertEquals("pit-2",requests.get(1).at("/pit/id").asText());assertEquals(1000,requests.get(1).at("/aggs/activity/composite/size").asInt());
        assertEquals("service.name",requests.get(1).at("/aggs/activity/composite/sources/0/service/terms/field").asText());
        assertFalse(requests.get(1).path("query").toString().contains("log.level"));assertTrue(requests.get(1).path("query").toString().contains("service.environment"));
        var malformed=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(response.get());var buckets=(com.fasterxml.jackson.databind.node.ArrayNode)malformed.at("/aggregations/activity/buckets");buckets.add(buckets.get(0).deepCopy());response.set(malformed.toString());
        assertEquals("SERVICE_ACTIVITY_QUERY_FAILED",client.readErrorPage(null,List.of(),"TEST",from,to,null).execution().activityIssue());
        response.set(discoveredActivity("2026-08-23T00:01:00Z"));assertEquals("SERVICE_ACTIVITY_QUERY_FAILED",client.readErrorPage(null,List.of(),"TEST",from,to,null).execution().activityIssue());
        response.set(completeEmpty());assertEquals("SERVICE_ACTIVITY_QUERY_FAILED",client.readErrorPage(null,List.of(),"TEST",from,to,null).execution().activityIssue());
    }
    private String discoveredActivity(String at) throws IOException {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var root=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(completeEmpty());
        var bucket=root.putObject("aggregations").putObject("activity").putArray("buckets").addObject().put("doc_count",2);bucket.putObject("key").put("service","a");
        bucket.putObject("latest").putObject("hits").putArray("hits").addObject().put("_index","logs").put("_id","latest").putObject("_source").put("@timestamp",at);
        return root.toString();
    }

    @Test
    void expiredPitAndChangedTargetRequireExplicitRestartWithoutReusingSortOnNewSnapshot() throws Exception {
        var open=new AtomicInteger();var query=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/workhub-logs-*/_pit",e->{open.incrementAndGet();respond(e,200,"{\"id\":\"pit-1\"}");});
        String hit=oneHit("2026-08-22T23:59:00Z",true);
        server.createContext("/_search",e->{if(query.getAndIncrement()==0)respond(e,200,hit);else respond(e,404,"private-server-message");});
        server.createContext("/_pit",e->respond(e,200,"{}"));server.start();
        var properties=properties();properties.setPageSize(1);var client=new ElasticsearchSystemAlertClient(properties);
        Instant from=Instant.parse("2026-08-22T23:50:00Z"),to=Instant.parse("2026-08-23T00:00:00Z");
        var first=client.readErrorPage(null,List.of(),"TEST",from,to,null);
        var error=assertThrows(SystemAlertLogSource.RestartRequired.class,()->client.readErrorPage(null,List.of(),"TEST",from,to,first.next()));
        assertFalse(error.getMessage().contains("private"));assertEquals(1,open.get());assertEquals(2,query.get());
        var changed=new SystemAlertLogSource.Position(first.next().pitId(),first.next().searchAfterJson(),"0".repeat(64));
        assertThrows(SystemAlertLogSource.RestartRequired.class,()->client.readErrorPage(null,List.of(),"TEST",from,to,changed));
        assertEquals(2,query.get());
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
