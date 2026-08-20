package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Component
public class ElasticsearchSystemAlertClient implements SystemAlertLogSource {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSystemAlertClient.class);
    private static final Pattern INDEX_PATTERN = Pattern.compile("[A-Za-z0-9*._,-]+");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[A-Za-z0-9_@.-]+");
    private static final Pattern DURATION_PATTERN = Pattern.compile("[1-9][0-9]*(ms|s|m|h)");
    private static final int MAX_MESSAGE_LENGTH = 8_000;
    private static final int MAX_STACK_LENGTH = 100_000;

    private final ElkAlertRuntimeConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient fixedHttpClient;
    private final Map<Integer, HttpClient> httpClients = new ConcurrentHashMap<>();

    @Autowired
    public ElasticsearchSystemAlertClient(ElkAlertRuntimeConfigService configService) {
        this(configService, new ObjectMapper(), null);
    }

    ElasticsearchSystemAlertClient(ElkAlertProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this(new ElkAlertRuntimeConfigService(properties), objectMapper, httpClient);
    }

    ElasticsearchSystemAlertClient(ElkAlertProperties properties) {
        this(new ElkAlertRuntimeConfigService(properties), new ObjectMapper(), null);
    }

    ElasticsearchSystemAlertClient(ElkAlertRuntimeConfigService configService,
                                   ObjectMapper objectMapper,
                                   HttpClient httpClient) {
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.fixedHttpClient = httpClient;
    }

    @Override
    public SyncResult readErrors(String indexPattern, String serviceName, String environmentCode,
                                 Instant fromInclusive,
                                 Consumer<ElkSystemAlertLog> consumer) {
        ElkAlertRuntimeConfig config = configService.current(serviceName, indexPattern);
        validateConfiguration(config);
        HttpClient httpClient = clientFor(config);
        String pitId = null;
        int fetched = 0;
        Instant latest = null;
        JsonNode searchAfter = null;
        try {
            pitId = openPit(config, httpClient);
            while (true) {
                SearchPage page = search(config, httpClient, pitId, serviceName, environmentCode,
                        fromInclusive, searchAfter);
                pitId = page.pitId();
                for (ElkSystemAlertLog event : page.events()) {
                    consumer.accept(event);
                    fetched++;
                    if (latest == null || event.occurredAt().isAfter(latest)) {
                        latest = event.occurredAt();
                    }
                    if (fetched >= config.maxEventsPerRun()) {
                        break;
                    }
                }
                if (fetched >= config.maxEventsPerRun()) {
                    break;
                }
                if (page.hitCount() < config.pageSize()) {
                    break;
                }
                searchAfter = page.searchAfter();
                if (searchAfter == null) {
                    throw new IllegalStateException("ELK响应缺少分页游标");
                }
            }
            return new SyncResult(fetched, latest);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ELK查询被中断", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ELK连接或响应异常", ex);
        } finally {
            closePit(config, httpClient, pitId);
        }
    }

    private String openPit(ElkAlertRuntimeConfig config, HttpClient httpClient) throws Exception {
        HttpRequest request = request(config, endpoint(config,
                        "/" + config.indexPattern() + "/_pit?keep_alive=" + config.pitKeepAlive()))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        JsonNode body = send(httpClient, request, "创建PIT");
        String pitId = text(body, "id");
        if (pitId == null) {
            throw new IllegalStateException("ELK创建PIT响应缺少ID");
        }
        return pitId;
    }

    private SearchPage search(ElkAlertRuntimeConfig config, HttpClient httpClient,
                              String pitId, String serviceName, String environmentCode,
                              Instant fromInclusive, JsonNode searchAfter) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", config.pageSize());
        body.put("track_total_hits", false);
        ObjectNode pit = body.putObject("pit");
        pit.put("id", pitId);
        pit.put("keep_alive", config.pitKeepAlive());

        ArrayNode filters = body.putObject("query").putObject("bool").putArray("filter");
        filters.add(term(config.serviceQueryField(), serviceName));
        if (config.environmentQueryField() != null && trim(environmentCode) != null) {
            filters.add(term(config.environmentQueryField(), environmentCode));
        }
        filters.add(term(config.levelQueryField(), config.errorLevel()));
        filters.addObject().putObject("range").putObject(config.timestampField())
                .put("gte", fromInclusive.toString());

        ArrayNode sort = body.putArray("sort");
        ObjectNode timestampSort = sort.addObject().putObject(config.timestampField());
        timestampSort.put("order", "desc");
        timestampSort.put("format", "strict_date_optional_time_nanos");
        timestampSort.put("numeric_type", "date_nanos");
        sort.addObject().put("_shard_doc", "asc");
        if (searchAfter != null) {
            body.set("search_after", searchAfter);
        }

        HttpRequest request = request(config, endpoint(config, "/_search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        JsonNode response = send(httpClient, request, "查询日志");
        String nextPitId = text(response, "pit_id");
        if (nextPitId == null) {
            nextPitId = pitId;
        }
        JsonNode hits = response.path("hits").path("hits");
        if (!hits.isArray()) {
            throw new IllegalStateException("ELK查询响应结构异常");
        }
        java.util.List<ElkSystemAlertLog> events = new java.util.ArrayList<>();
        JsonNode lastSort = null;
        for (JsonNode hit : hits) {
            lastSort = hit.get("sort");
            ElkSystemAlertLog event = toEvent(config, hit, serviceName);
            if (event != null) {
                events.add(event);
            }
        }
        return new SearchPage(nextPitId, hits.size(), events,
                lastSort == null ? null : lastSort.deepCopy());
    }

    private ObjectNode term(String field, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.putObject("term").put(field, value);
        return node;
    }

    private ElkSystemAlertLog toEvent(ElkAlertRuntimeConfig config, JsonNode hit,
                                      String configuredServiceName) {
        String index = text(hit, "_index");
        String id = text(hit, "_id");
        JsonNode source = hit.path("_source");
        String timestamp = textAt(source, config.timestampField());
        if (index == null || id == null || timestamp == null) {
            return null;
        }
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(timestamp);
        } catch (RuntimeException ex) {
            return null;
        }
        String message = firstText(source, config.messageSourceField(), "message");
        return new ElkSystemAlertLog(
                sha256(index + ":" + id),
                defaultText(textAt(source, config.serviceSourceField()), configuredServiceName),
                defaultText(textAt(source, config.levelSourceField()), config.errorLevel()).toUpperCase(),
                truncate(textAt(source, config.loggerSourceField()), 255),
                truncate(message, MAX_MESSAGE_LENGTH),
                truncate(textAt(source, config.errorTypeSourceField()), 255),
                truncate(textAt(source, config.stackTraceSourceField()), MAX_STACK_LENGTH),
                truncate(firstText(source, config.traceIdSourceField(), "traceId"), 128),
                truncate(firstText(source, config.requestIdSourceField(), "request.id", "requestId"), 128),
                occurredAt
        );
    }

    private JsonNode send(HttpClient httpClient, HttpRequest request, String operation) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new IllegalStateException("ELK认证或授权失败");
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(operation + "失败，HTTP " + status);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("ELK响应不是有效JSON", ex);
        }
    }

    private HttpRequest.Builder request(ElkAlertRuntimeConfig config, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                .header("Accept", "application/json");
        if ("API_KEY".equals(config.authType())) {
            builder.header("Authorization", "ApiKey " + config.apiKey());
        } else if ("BASIC".equals(config.authType())) {
            String credentials = config.username() + ":" + config.password();
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder;
    }

    private void closePit(ElkAlertRuntimeConfig config, HttpClient httpClient, String pitId) {
        if (pitId == null) {
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode().put("id", pitId);
            HttpRequest request = request(config, endpoint(config, "/_pit"))
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("关闭ELK PIT失败，HTTP {}，将等待其自动过期", response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("关闭ELK PIT失败，将等待其自动过期：{}", ex.getClass().getSimpleName());
        }
    }

    private void validateConfiguration(ElkAlertRuntimeConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("ELK采集未启用");
        }
        String baseUrl = trim(config.baseUrl());
        if (baseUrl == null) {
            throw new IllegalStateException("ELK地址未配置");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("ELK地址格式非法");
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("ELK地址格式非法");
        }
        if (!INDEX_PATTERN.matcher(defaultText(config.indexPattern(), "")).matches()) {
            throw new IllegalStateException("ELK索引模式非法");
        }
        if (!DURATION_PATTERN.matcher(defaultText(config.pitKeepAlive(), "")).matches()) {
            throw new IllegalStateException("ELK PIT有效期非法");
        }
        validateField(config.timestampField(), "时间字段", false);
        validateField(config.serviceQueryField(), "服务查询字段", false);
        validateField(config.serviceSourceField(), "服务来源字段", false);
        validateField(config.environmentQueryField(), "环境查询字段", true);
        validateField(config.levelQueryField(), "级别查询字段", false);
        validateField(config.levelSourceField(), "级别来源字段", false);
        validateField(config.messageSourceField(), "消息来源字段", false);
        validateField(config.loggerSourceField(), "Logger来源字段", true);
        validateField(config.errorTypeSourceField(), "异常类型来源字段", true);
        validateField(config.stackTraceSourceField(), "堆栈来源字段", true);
        validateField(config.traceIdSourceField(), "Trace ID来源字段", true);
        validateField(config.requestIdSourceField(), "请求ID来源字段", true);
        String authType = defaultText(trim(config.authType()), "NONE").toUpperCase(Locale.ROOT);
        if (!"NONE".equals(authType) && !"BASIC".equals(authType) && !"API_KEY".equals(authType)) {
            throw new IllegalStateException("ELK认证类型非法");
        }
        if ("BASIC".equals(authType)
                && (trim(config.username()) == null || trim(config.password()) == null)) {
            throw new IllegalStateException("ELK Basic Auth配置不完整");
        }
        if ("API_KEY".equals(authType) && trim(config.apiKey()) == null) {
            throw new IllegalStateException("ELK API Key配置不完整");
        }
    }

    private void validateField(String field, String name, boolean optional) {
        if (field == null && optional) {
            return;
        }
        if (field == null || !FIELD_PATTERN.matcher(field).matches()) {
            throw new IllegalStateException("ELK" + name + "非法");
        }
    }

    private URI endpoint(ElkAlertRuntimeConfig config, String path) {
        String base = config.baseUrl().trim().replaceAll("/+$", "");
        return URI.create(base + path);
    }

    private HttpClient clientFor(ElkAlertRuntimeConfig config) {
        if (fixedHttpClient != null) {
            return fixedHttpClient;
        }
        return httpClients.computeIfAbsent(config.connectTimeoutSeconds(), seconds ->
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(seconds)).build());
    }

    private String textAt(JsonNode node, String dottedPath) {
        if (dottedPath == null) {
            return null;
        }
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            current = current.path(part);
        }
        return current.isTextual() ? trim(current.asText()) : null;
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            if (path == null) continue;
            String value = textAt(node, path);
            if (value != null) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? trim(value.asText()) : null;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultText(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成ELK事件指纹", ex);
        }
    }

    private record SearchPage(String pitId, int hitCount, java.util.List<ElkSystemAlertLog> events,
                              JsonNode searchAfter) {
    }
}
