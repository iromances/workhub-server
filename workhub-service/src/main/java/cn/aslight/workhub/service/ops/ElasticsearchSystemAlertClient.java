package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.BusinessLogQueryEvidence.Activity;
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
import java.util.List;
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
    public SyncResult readErrors(String indexPattern, List<String> serviceNames, String environmentCode,
                                 Instant fromInclusive,
                                 Consumer<ElkSystemAlertLog> consumer) {
        ElkAlertRuntimeConfig config = configService.current(null, indexPattern);
        validateConfiguration(config);
        HttpClient httpClient = clientFor(config);
        String pitId = null;
        int fetched = 0;
        boolean complete = false;
        Instant latest = null;
        var seen = new java.util.HashSet<String>();
        JsonNode searchAfter = null;
        try {
            pitId = openPit(config, httpClient);
            while (true) {
                SearchPage page = search(config, httpClient, pitId, serviceNames, environmentCode,
                        fromInclusive, searchAfter);
                pitId = page.pitId();
                for (ElkSystemAlertLog event : page.events()) {
                    if (!seen.add(event.sourceEventId())) throw new IllegalStateException("ELK分页返回重复日志");
                    if (event.occurredAt().isBefore(fromInclusive)) throw new IllegalStateException("ELK返回日志超出查询时间范围");
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
                    complete = true;
                    break;
                }
                if (page.searchAfter() == null || page.searchAfter().equals(searchAfter))
                    throw new IllegalStateException("ELK响应缺少或重复分页游标");
                searchAfter = page.searchAfter();
            }
            return new SyncResult(fetched, latest, complete);
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

    /** 只读取一页；未完成时由事务保存位置，再继续同一PIT。过期不得拿旧sort套新快照。 */
    @Override
    public ErrorPage readErrorPage(String indexPattern, List<String> services, String environment, Instant from, Instant to, Position position) {
        var config = configService.current(null, indexPattern);
        validateConfiguration(config);
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("采集区间无效");
        final String targetHash = targetBinding(config);
        if (position != null && !targetHash.equals(position.targetHash())) throw new RestartRequired("ELK目标已变化，重新扫描原区间");
        var http = clientFor(config);
        String pitId = position == null ? null : position.pitId();
        boolean retain = false;
        Instant started = Instant.now();
        try {
            if (pitId == null) pitId = openPit(config, http);
            JsonNode after = position == null ? null : objectMapper.readTree(position.searchAfterJson());
            int size = Math.min(config.pageSize(), config.maxEventsPerRun());
            var page = searchLogsPage(config, http, pitId, services, environment, List.of(config.errorLevel()), from, to, null, null, null, size, after);
            pitId = page.pitId();
            if (page.events().stream().map(ElkSystemAlertLog::sourceEventId).distinct().count() != page.events().size())
                throw new IllegalStateException("ELK分页返回重复日志");
            if (page.events().stream().anyMatch(event -> event.occurredAt().isBefore(from.minusMillis(SystemAlertLogSource.BOUNDARY_TOLERANCE_MS))
                    || event.occurredAt().isAfter(to.plusMillis(SystemAlertLogSource.BOUNDARY_TOLERANCE_MS))))
                throw new IllegalStateException("ELK返回日志超出采集区间");
            boolean complete = page.hitCount() < size;
            if (!complete && (page.searchAfter() == null || page.searchAfter().equals(after)))
                throw new IllegalStateException("ELK响应缺少或重复分页游标");
            List<Activity> activities = List.of(); String activityIssue=null;
            if (complete && (services == null || services.size() <= 100)) {
                try {
                    var activity = services == null || services.isEmpty()
                            ? discoverActivity(config,http,pitId,environment,from,to)
                            : searchActivity(config,http,pitId,services,environment,from,to);
                    pitId = activity.pitId(); activities = activity.activities();
                } catch (InterruptedException ex) { throw ex; }
                catch (Exception ex) { activityIssue="SERVICE_ACTIVITY_QUERY_FAILED"; }
            } else if (complete) activityIssue="SERVICE_ACTIVITY_LIMIT_EXCEEDED";
            Position next = complete ? null : new Position(pitId,objectMapper.writeValueAsString(page.searchAfter()),targetHash);
            retain = !complete;
            return new ErrorPage(page.events(),next,complete,page.contentTruncated(),
                    new QueryReceipt(targetHash,started,Instant.now(),sha256(pitId),activities,activityIssue));
        } catch (RequestFailure ex) {
            if (ex.status == 404 && position != null) throw new RestartRequired("ELK分页快照失效，重新扫描原区间");
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("ELK查询被中断",ex);
        } catch (IllegalArgumentException | IllegalStateException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("ELK连接或响应异常",ex); }
        finally { if (!retain) closePit(config,http,pitId); }
    }

    public LogSearchResult searchLogs(String indexPattern,
                                      List<String> serviceNames,
                                      String environmentCode,
                                      List<String> levels,
                                      Instant fromInclusive,
                                      Instant toInclusive,
                                      String phrase,
                                      String traceId,
                                      String requestId,
                                      int limit) {
        ElkAlertRuntimeConfig config = configService.current(null, indexPattern);
        validateConfiguration(config);
        if (traceId != null && config.traceIdSourceField() == null) {
            throw new IllegalArgumentException("当前ELK配置未提供Trace ID字段");
        }
        if (requestId != null && config.requestIdSourceField() == null) {
            throw new IllegalArgumentException("当前ELK配置未提供请求ID字段");
        }
        HttpClient httpClient = clientFor(config);
        String pitId = null;
        JsonNode searchAfter = null;
        java.util.List<ElkSystemAlertLog> events = new java.util.ArrayList<>();
        boolean truncated = false;
        Instant startedAt = Instant.now();
        var seen = new java.util.HashSet<String>();
        int fetchLimit = limit + 1;
        try {
            pitId = openPit(config, httpClient);
            while (events.size() < fetchLimit) {
                int pageSize = Math.min(config.pageSize(), fetchLimit - events.size());
                SearchPage page = searchLogsPage(config, httpClient, pitId, serviceNames, environmentCode,
                        levels, fromInclusive, toInclusive, phrase, traceId, requestId, pageSize, searchAfter);
                pitId = page.pitId();
                for (var event : page.events()) {
                    if (!seen.add(event.sourceEventId())) throw new IllegalStateException("ELK分页返回重复日志");
                    if (event.occurredAt().isBefore(fromInclusive) || event.occurredAt().isAfter(toInclusive))
                        throw new IllegalStateException("ELK返回日志超出查询时间范围");
                    events.add(event);
                }
                truncated |= page.contentTruncated();
                if (page.hitCount() < pageSize) break;
                if (page.searchAfter() == null || page.searchAfter().equals(searchAfter))
                    throw new IllegalStateException("ELK响应缺少或重复分页游标");
                searchAfter = page.searchAfter();
            }
            if (events.size() > limit) {
                truncated = true;
                events = new java.util.ArrayList<>(events.subList(0, limit));
            }
            List<Activity> activities = List.of();
            if (serviceNames != null && !serviceNames.isEmpty() && serviceNames.size() <= 100) {
                var activity = searchActivity(config, httpClient, pitId, serviceNames, environmentCode, fromInclusive, toInclusive);
                pitId = activity.pitId();
                activities = activity.activities();
            }
            return new LogSearchResult(List.copyOf(events), truncated, new QueryReceipt(
                    sha256(objectMapper.writeValueAsString(config)), startedAt, Instant.now(), sha256(pitId), activities));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ELK查询被中断", ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ELK连接或响应异常", ex);
        } finally {
            closePit(config, httpClient, pitId);
        }
    }

    private String openPit(ElkAlertRuntimeConfig config, HttpClient httpClient) throws Exception {
        // _pit 不带 allow_partial_search_results：ES 7.16 及以下对该端点拒绝未知参数（HTTP 400），
        // 查询完整性由每次 _search 的 requireCompleteSearch（分片全部成功）保证。
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
                              String pitId, List<String> serviceNames, String environmentCode,
                              Instant fromInclusive, JsonNode searchAfter) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", config.pageSize());
        body.put("track_total_hits", false);
        ObjectNode pit = body.putObject("pit");
        pit.put("id", pitId);
        pit.put("keep_alive", config.pitKeepAlive());

        ArrayNode filters = body.putObject("query").putObject("bool").putArray("filter");
        if (serviceNames != null && serviceNames.size() == 1) {
            filters.add(term(config.serviceQueryField(), serviceNames.getFirst()));
        } else if (serviceNames != null && !serviceNames.isEmpty()) {
            filters.add(terms(config.serviceQueryField(), serviceNames));
        }
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

        HttpRequest request = request(config, endpoint(config, "/_search?allow_partial_search_results=false"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        JsonNode response = send(httpClient, request, "查询日志");
        return page(config, response, pitId, config.pageSize());
    }

    private SearchPage searchLogsPage(ElkAlertRuntimeConfig config,
                                      HttpClient httpClient,
                                      String pitId,
                                      List<String> serviceNames,
                                      String environmentCode,
                                      List<String> levels,
                                      Instant fromInclusive,
                                      Instant toInclusive,
                                      String phrase,
                                      String traceId,
                                      String requestId,
                                      int pageSize,
                                      JsonNode searchAfter) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", pageSize);
        body.put("track_total_hits", false);
        ObjectNode pit = body.putObject("pit");
        pit.put("id", pitId);
        pit.put("keep_alive", config.pitKeepAlive());

        ObjectNode bool = body.putObject("query").putObject("bool");
        ArrayNode filters = bool.putArray("filter");
        if (serviceNames != null && serviceNames.size() == 1) {
            filters.add(term(config.serviceQueryField(), serviceNames.getFirst()));
        } else if (serviceNames != null && !serviceNames.isEmpty()) {
            filters.add(terms(config.serviceQueryField(), serviceNames));
        }
        if (config.environmentQueryField() != null && trim(environmentCode) != null) {
            filters.add(term(config.environmentQueryField(), environmentCode));
        }
        if (levels.size() == 1) {
            filters.add(term(config.levelQueryField(), levels.getFirst()));
        } else {
            filters.add(terms(config.levelQueryField(), levels));
        }
        ObjectNode range = filters.addObject().putObject("range").putObject(config.timestampField());
        range.put("gte", fromInclusive.toString());
        range.put("lte", toInclusive.toString());
        if (traceId != null) {
            filters.add(term(config.traceIdSourceField(), traceId));
        }
        if (requestId != null) {
            filters.add(term(config.requestIdSourceField(), requestId));
        }
        if (phrase != null) {
            bool.putArray("must").addObject().putObject("match_phrase")
                    .put(config.messageSourceField(), phrase);
        }

        ArrayNode sort = body.putArray("sort");
        ObjectNode timestampSort = sort.addObject().putObject(config.timestampField());
        timestampSort.put("order", "desc");
        timestampSort.put("format", "strict_date_optional_time_nanos");
        timestampSort.put("numeric_type", "date_nanos");
        sort.addObject().put("_shard_doc", "asc");
        if (searchAfter != null) {
            body.set("search_after", searchAfter);
        }

        HttpRequest request = request(config, endpoint(config, "/_search?allow_partial_search_results=false"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        JsonNode response = send(httpClient, request, "查询日志");
        return page(config, response, pitId, pageSize);
    }

    private SearchPage page(ElkAlertRuntimeConfig config, JsonNode response, String pitId, int limit) {
        requireCompleteSearch(response);
        JsonNode hits = response.path("hits").path("hits");
        if (!hits.isArray() || hits.size() > limit) throw new IllegalStateException("ELK查询响应结构或数量异常");
        var events = new java.util.ArrayList<ElkSystemAlertLog>();
        JsonNode lastSort = null;
        boolean truncated = false;
        for (JsonNode hit : hits) {
            lastSort = hit.get("sort");
            if (lastSort != null && (!lastSort.isArray() || lastSort.size() != 2))
                throw new IllegalStateException("ELK分页游标格式异常");
            events.add(toEvent(config, hit));
            JsonNode source = hit.path("_source");
            truncated |= tooLong(firstText(source, config.messageSourceField(), "message"), MAX_MESSAGE_LENGTH)
                    || tooLong(textAt(source, config.stackTraceSourceField()), MAX_STACK_LENGTH)
                    || tooLong(textAt(source, config.loggerSourceField()), 255)
                    || tooLong(textAt(source, config.errorTypeSourceField()), 255)
                    || tooLong(firstText(source, config.traceIdSourceField(), "traceId"), 128)
                    || tooLong(firstText(source, config.requestIdSourceField(), "request.id", "requestId"), 128);
        }
        return new SearchPage(defaultText(text(response, "pit_id"), pitId), hits.size(), List.copyOf(events),
                lastSort == null ? null : lastSort.deepCopy(), truncated);
    }

    private void requireCompleteSearch(JsonNode response) {
        if (!response.path("timed_out").isBoolean() || response.path("timed_out").booleanValue()
                || (response.has("terminated_early") && (!response.path("terminated_early").isBoolean() || response.path("terminated_early").booleanValue())))
            throw new IllegalStateException("ELK查询未完整完成或缺少完成状态");
        JsonNode shards = response.path("_shards");
        long total = count(shards.path("total")), successful = count(shards.path("successful"));
        if (total == 0 || count(shards.path("failed")) != 0 || successful != total)
            throw new IllegalStateException("ELK查询分片未全部成功");
    }

    private long count(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw new IllegalStateException("ELK计数缺失或非法");
        return value.longValue();
    }

    private ActivityPage searchActivity(ElkAlertRuntimeConfig config, HttpClient httpClient, String pitId,
            List<String> services, String environment, Instant from, Instant to) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("size", 0); body.put("track_total_hits", false);
        body.putObject("pit").put("id", pitId).put("keep_alive", config.pitKeepAlive());
        var filters = body.putObject("query").putObject("bool").putArray("filter");
        if (config.environmentQueryField() != null && trim(environment) != null) filters.add(term(config.environmentQueryField(), environment));
        filters.addObject().putObject("range").putObject(config.timestampField()).put("gte", from.toString()).put("lte", to.toString());
        var aggregation = body.putObject("aggs").putObject("activity");
        var serviceFilters = aggregation.putObject("filters").putObject("filters");
        for (int i = 0; i < services.size(); i++) serviceFilters.set("s" + i, term(config.serviceQueryField(), services.get(i)));
        var latest = aggregation.putObject("aggs").putObject("latest").putObject("top_hits");
        latest.put("size", 1);
        latest.putArray("sort").addObject().putObject(config.timestampField()).put("order", "desc");
        latest.putObject("_source").putArray("includes").add(config.timestampField());
        var request = request(config, endpoint(config, "/_search?allow_partial_search_results=false"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        var response = send(httpClient, request, "核对日志活动");
        requireCompleteSearch(response);
        var buckets = response.path("aggregations").path("activity").path("buckets");
        if (!buckets.isObject() || buckets.size() != services.size()) throw new IllegalStateException("ELK服务活动回执不完整");
        var activities = new java.util.ArrayList<Activity>();
        for (int i = 0; i < services.size(); i++) {
            var bucket = buckets.path("s" + i);
            long count = count(bucket.path("doc_count"));
            var hits = bucket.path("latest").path("hits").path("hits");
            if (!hits.isArray() || hits.size() != (count == 0 ? 0 : 1)) throw new IllegalStateException("ELK最新活动回执不完整");
            Instant at = null; String eventId = null;
            if (count > 0) {
                var hit = hits.get(0);
                at = eventTime(config, hit);
                if (at.isBefore(from) || at.isAfter(to)) throw new IllegalStateException("ELK活动时间超出查询区间");
                eventId = sha256(text(hit, "_index") + ":" + text(hit, "_id"));
            }
            activities.add(new Activity(services.get(i), count, at, eventId));
        }
        return new ActivityPage(defaultText(text(response, "pit_id"), pitId), List.copyOf(activities));
    }

    /** ALL模式只提供已出现服务的活动，不推断预期服务全集。最多读取1000个桶。 */
    private ActivityPage discoverActivity(ElkAlertRuntimeConfig config,HttpClient http,String pitId,
            String environment,Instant from,Instant to) throws Exception {
        var body=objectMapper.createObjectNode(); body.put("size",0); body.put("track_total_hits",false);
        body.putObject("pit").put("id",pitId).put("keep_alive",config.pitKeepAlive());
        var filters=body.putObject("query").putObject("bool").putArray("filter");
        if(config.environmentQueryField()!=null && trim(environment)!=null) filters.add(term(config.environmentQueryField(),environment));
        filters.addObject().putObject("range").putObject(config.timestampField()).put("gte",from.toString()).put("lte",to.toString());
        var aggregation=body.putObject("aggs").putObject("activity");
        var composite=aggregation.putObject("composite"); composite.put("size",1000);
        composite.putArray("sources").addObject().putObject("service").putObject("terms").put("field",config.serviceQueryField());
        var latest=aggregation.putObject("aggs").putObject("latest").putObject("top_hits"); latest.put("size",1);
        latest.putArray("sort").addObject().putObject(config.timestampField()).put("order","desc");
        latest.putObject("_source").putArray("includes").add(config.timestampField());
        var request=request(config,endpoint(config,"/_search?allow_partial_search_results=false"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        var response=send(http,request,"核对已出现服务活动"); requireCompleteSearch(response);
        var buckets=response.path("aggregations").path("activity").path("buckets");
        if(!buckets.isArray() || buckets.size()>1000) throw new IllegalStateException("ELK服务活动回执不完整");
        var activities=new java.util.ArrayList<Activity>(); var seen=new java.util.HashSet<String>();
        for(var bucket:buckets) {
            String service=trim(text(bucket.path("key"),"service")); long count=count(bucket.path("doc_count"));
            var hits=bucket.path("latest").path("hits").path("hits");
            if(service==null || !seen.add(service) || count<=0 || !hits.isArray() || hits.size()!=1)
                throw new IllegalStateException("ELK服务活动桶无效");
            var hit=hits.get(0); var at=eventTime(config,hit);
            if(at.isBefore(from) || at.isAfter(to)) throw new IllegalStateException("ELK活动时间超出查询区间");
            activities.add(new Activity(service,count,at,sha256(text(hit,"_index")+":"+text(hit,"_id"))));
        }
        return new ActivityPage(defaultText(text(response,"pit_id"),pitId),List.copyOf(activities));
    }

    static String targetBinding(ElkAlertRuntimeConfig config) {
        try { return sha256(new ObjectMapper().writeValueAsString(config)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("无法固定ELK目标",ex); }
    }

    private boolean tooLong(String value, int maximum) { return value != null && value.length() > maximum; }

    private ObjectNode term(String field, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.putObject("term").put(field, value);
        return node;
    }

    private ObjectNode terms(String field, List<String> values) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode array = node.putObject("terms").putArray(field);
        values.forEach(array::add);
        return node;
    }

    private ElkSystemAlertLog toEvent(ElkAlertRuntimeConfig config, JsonNode hit) {
        String index = text(hit, "_index"), id = text(hit, "_id");
        JsonNode source = hit.path("_source");
        Instant occurredAt = eventTime(config, hit);
        String serviceName = trim(textAt(source, config.serviceSourceField()));
        String message = firstText(source, config.messageSourceField(), "message");
        return new ElkSystemAlertLog(
                sha256(index + ":" + id),
                serviceName,
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

    private Instant eventTime(ElkAlertRuntimeConfig config, JsonNode hit) {
        String timestamp = textAt(hit.path("_source"), config.timestampField());
        if (text(hit, "_index") == null || text(hit, "_id") == null || timestamp == null)
            throw new IllegalStateException("ELK日志缺少标识或数据时间");
        try { return Instant.parse(timestamp); }
        catch (java.time.DateTimeException ex) { throw new IllegalStateException("ELK日志数据时间非法"); }
    }

    private JsonNode send(HttpClient httpClient, HttpRequest request, String operation) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new IllegalStateException("ELK认证或授权失败");
        }
        if (status < 200 || status >= 300) {
            throw new RequestFailure(status, operation + "失败，HTTP " + status);
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成ELK事件指纹", ex);
        }
    }

    private static final class RequestFailure extends IllegalStateException {
        private final int status;
        RequestFailure(int status, String message) { super(message); this.status=status; }
    }

    private record SearchPage(String pitId, int hitCount, java.util.List<ElkSystemAlertLog> events,
                              JsonNode searchAfter, boolean contentTruncated) { }
    private record ActivityPage(String pitId, List<Activity> activities) { }
    public record QueryReceipt(String targetBindingHash, Instant startedAt, Instant finishedAt, String snapshotHash, List<Activity> activities, String activityIssue) {
        public QueryReceipt(String targetBindingHash,Instant startedAt,Instant finishedAt,String snapshotHash,List<Activity> activities) {
            this(targetBindingHash,startedAt,finishedAt,snapshotHash,activities,null);
        }
    }
    public record LogSearchResult(List<ElkSystemAlertLog> items, boolean truncated, QueryReceipt receipt) {
        public LogSearchResult(List<ElkSystemAlertLog> items, boolean truncated) { this(items, truncated, null); }
    }
}
