package cn.aslight.workhub.service.agent;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

/** Jenkins 远程客户端。仅接受平台绑定的 Job；调用方须先持久化获批发布意图，超时不得重发。 */
@Component
public class AgentJenkinsClient {
    private static final int MAX_RESPONSE = 1024 * 1024;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Duration timeout;

    @Autowired
    public AgentJenkinsClient(ObjectMapper json) { this(json, Duration.ofSeconds(30)); }
    AgentJenkinsClient(ObjectMapper json, Duration timeout) {
        this.json = json; this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** 只读检查 Job 和参数，不用触发构建代替连通性测试。 */
    public Probe probe(Target target) {
        var value = testJobDefinition(target);
        var names = new TreeSet<String>();
        for (String section : List.of("actions", "property"))
        for (var action : value.path(section)) for (var parameter : action.path("parameterDefinitions")) {
            if (parameter.path("name").isString()) names.add(parameter.path("name").asText());
        }
        return new Probe(value.path("fullName").asText(), value.path("buildable").asBoolean(), List.copyOf(names));
    }

    /** Pipeline 参数通常在 property 下；读取实际参数和选择范围，不以空列表放行。 */
    public JsonNode testJobDefinition(Target target) {
        return get(target, target.jobUri("api/json?tree=" + encode("name,fullName,buildable,"
                + "actions[parameterDefinitions[name,type,choices]],property[parameterDefinitions[name,type,choices]]")), false);
    }

    /** 按已持久化队列号回查构建，避免队列过期后使用最近一次构建误认回执。 */
    public Long buildForQueue(Target target, long queueId) {
        var value = get(target, target.jobUri("api/json?tree=" + encode("builds[number,queueId]{0,100}")), false);
        for (var build : value.path("builds"))
            if (build.path("queueId").asLong(-1) == queueId) return build.path("number").asLong();
        return null;
    }

    /** 读取 Git 插件按仓库记录的实际构建提交，不读取控制台凭据。 */
    public String repositoryRevision(Target target, long number, String projectPath) {
        var value = get(target, target.jobUri(number + "/api/json?tree="
                + encode("actions[remoteUrls,lastBuiltRevision[SHA1]]")), false);
        var revisions = new TreeSet<String>();
        for (var action : value.path("actions")) for (var remote : action.path("remoteUrls")) {
            String url = remote.asText();
            if (url.endsWith("/" + projectPath + ".git") || url.endsWith(":" + projectPath + ".git"))
                revisions.add(action.path("lastBuiltRevision").path("SHA1").asText());
        }
        return revisions.size() == 1 ? revisions.getFirst() : null;
    }

    /** 只读检查实例连通性，返回 Jenkins 版本；不依赖任何具体 Job。 */
    public InstanceProbe probeInstance(Target target) {
        var value = get(target, target.instanceUri("api/json"), false);
        String version = value.path("version").isString() ? value.path("version").asText() : null;
        return new InstanceProbe(version);
    }

    /** 只发送一次；调用方遇到 SubmissionUnknown 后必须查队列/构建并保留未知状态。 */
    public long submit(Target target, Map<String, String> parameters) {
        validateParameters(parameters);
        String body = parameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(Collectors.joining("&"));
        HttpResponse<byte[]> response;
        try {
            response = send(target, target.jobUri("buildWithParameters"), body);
        } catch (ClientFailure ex) { throw new SubmissionUnknown("Jenkins 触发响应未确认；必须先查询原请求，不能重复触发", ex); }
        if (Set.of(400, 401, 403, 404, 405).contains(response.statusCode())) throw new SubmissionRejected("Jenkins 拒绝构建请求（HTTP " + response.statusCode() + "）");
        if (response.statusCode() != 201 && response.statusCode() != 202) throw new SubmissionUnknown("Jenkins 未返回明确队列回执（HTTP " + response.statusCode() + "）", null);
        String location = response.headers().firstValue("Location").orElse(null);
        if (location == null) throw new SubmissionUnknown("Jenkins 接受请求但没有返回队列地址，需按请求标识对账", null);
        URI queue;
        try { queue = target.jobUri("buildWithParameters").resolve(location); }
        catch (IllegalArgumentException ex) { throw new SubmissionUnknown("Jenkins 返回的队列地址无效", ex); }
        String prefix = target.baseUri().toString() + "queue/item/";
        if (!queue.toString().startsWith(prefix) || !queue.toString().substring(prefix.length()).matches("[1-9][0-9]{0,17}/?")) {
            throw new SubmissionUnknown("Jenkins 返回的队列地址不属于已绑定实例，未跟随跳转", null);
        }
        return Long.parseLong(queue.toString().substring(prefix.length()).replace("/", ""));
    }

    public QueueState queue(Target target, long queueId, Map<String, String> expected) {
        positive(queueId); validateParameters(expected);
        var value = get(target, target.instanceUri("queue/item/" + queueId + "/api/json?tree="
                + encode("id,cancelled,why,task[url],executable[number,url],actions[parameters[name,value]]")), true);
        if (value == null) return new QueueState("EXPIRED", queueId, null, false);
        requireJobUrl(target, value.path("task").path("url").asText());
        if (value.path("id").asLong(-1) != queueId) throw new ClientFailure("Jenkins 队列编号与请求不符");
        boolean matches = matches(value, expected);
        if (value.path("cancelled").asBoolean()) return new QueueState("CANCELLED", queueId, null, matches);
        if (value.hasNonNull("executable")) {
            long build = value.path("executable").path("number").asLong(-1); positive(build);
            if (!target.jobUri(build + "/").toString().equals(value.path("executable").path("url").asText())) throw new ClientFailure("Jenkins 构建地址与绑定 Job 不符");
            return new QueueState("STARTED", queueId, build, matches);
        }
        return new QueueState("QUEUED", queueId, null, matches);
    }

    public BuildState build(Target target, long number, Map<String, String> expected) {
        positive(number); validateParameters(expected);
        var value = get(target, target.jobUri(number + "/api/json?tree=" + encode("number,url,building,result,queueId,timestamp,duration,actions[parameters[name,value]]")), true);
        if (value == null) return new BuildState("MISSING", number, null, false, null);
        if (value.path("number").asLong(-1) != number || !target.jobUri(number + "/").toString().equals(value.path("url").asText())) throw new ClientFailure("Jenkins 构建身份与已保存句柄不符");
        String state = value.path("building").asBoolean() ? "RUNNING" : value.path("result").isString() ? "FINISHED" : "UNKNOWN";
        String result = value.path("result").isString() ? value.path("result").asText() : null;
        return new BuildState(state, number, result, matches(value, expected), value.path("queueId").isIntegralNumber() ? value.path("queueId").asLong() : null);
    }

    /** 队列回执丢失或已过期时按持久化关联参数查找；零命中不能证明从未触发。 */
    public SearchResult find(Target target, String correlationParameter, String requestId, Map<String, String> expected) {
        validateParameters(expected);
        if (!Objects.equals(expected.get(correlationParameter), requestId)) throw new IllegalArgumentException("关联参数必须属于固定请求参数");
        var queue = get(target, target.instanceUri("queue/api/json?tree=" + encode("items[id,task[url],executable[number,url],actions[parameters[name,value]]]")), false);
        var queues = new TreeSet<Long>(); var builds = new TreeSet<Long>(); boolean mismatch = false;
        for (var item : queue.path("items")) {
            if (!target.jobUri("").toString().equals(item.path("task").path("url").asText())) continue;
            if (!matches(item, Map.of(correlationParameter, requestId))) continue;
            long id = item.path("id").asLong(-1); positive(id); queues.add(id);
            mismatch |= !matches(item, expected);
            if (item.path("executable").hasNonNull("number")) {
                long number = item.path("executable").path("number").asLong(-1); positive(number);
                if (!target.jobUri(number + "/").toString().equals(item.path("executable").path("url").asText())) throw new ClientFailure("队列关联了其他 Job 的构建");
                builds.add(number);
            }
        }
        var recent = get(target, target.jobUri("api/json?tree=" + encode("builds[number,queueId,url,actions[parameters[name,value]]]{0,100}")), false);
        for (var item : recent.path("builds")) {
            if (!matches(item, Map.of(correlationParameter, requestId))) continue;
            long number = item.path("number").asLong(-1); positive(number);
            if (!target.jobUri(number + "/").toString().equals(item.path("url").asText())) throw new ClientFailure("构建搜索返回其他 Job 的结果");
            builds.add(number);
            if (item.path("queueId").asLong(-1) > 0) queues.add(item.path("queueId").asLong());
            mismatch |= !matches(item, expected);
        }
        String state = mismatch ? "PARAMETER_MISMATCH" : queues.size() > 1 || builds.size() > 1 ? "DUPLICATE"
                : queues.isEmpty() && builds.isEmpty() ? "NOT_FOUND_IN_WINDOW" : "FOUND";
        return new SearchResult(state, List.copyOf(queues), List.copyOf(builds), 100);
    }

    /** 构建成功不等于准确版本已部署；发布流程必须另外验证归档的结构化实际部署回执。 */
    public JsonNode receipt(Target target, long number, String artifactPath) {
        positive(number);
        if (artifactPath == null || artifactPath.length() > 300) throw new IllegalArgumentException("Jenkins 回执路径非法");
        String encoded = Arrays.stream(artifactPath.split("/", -1)).map(AgentJenkinsClient::segment).collect(Collectors.joining("/"));
        return get(target, target.jobUri(number + "/artifact/" + encoded), true);
    }

    // ================= MCP 只读查询（REQ-0051c0a8，全部 GET，不触发构建、不改变 Jenkins 状态） =================

    private static final int MAX_LOG_BYTES = 256 * 1024;

    /** 只读列出实例根目录或指定 folder 路径下的 Job；folderSegments 为空时列实例根目录。 */
    public JsonNode listJobs(Target target, List<String> folderSegments, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Jenkins Job 列表上限必须为正整数");
        boolean root = folderSegments == null || folderSegments.isEmpty();
        Target scope = root ? target : new Target(target.baseUri(), folderSegments, target.userName(), target.apiToken());
        String tree = encode("jobs[name,fullName,color,url,buildable,lastBuild[number,result]]{0," + Math.min(limit, 200) + "}");
        URI uri = root ? scope.instanceUri("api/json?tree=" + tree) : scope.jobUri("api/json?tree=" + tree);
        return get(target, uri, false);
    }

    /** 只读查询 Job 详情（可构建性、健康度、最近构建）。 */
    public JsonNode jobDetail(Target target) {
        return get(target, target.jobUri("api/json?tree=" + encode(
                "name,fullName,description,buildable,color,lastBuild[number,result,building,timestamp,duration],healthReport[score,description]")), false);
    }

    /** 只读查询构建详情（结果、参数、时间戳与耗时）。 */
    public JsonNode buildDetail(Target target, long number) {
        positive(number);
        return get(target, target.jobUri(number + "/api/json?tree=" + encode(
                "number,url,building,result,timestamp,duration,queueId,actions[parameters[name,value]]")), false);
    }

    /** 只读尾读构建控制台日志：以 progressiveText 两跳定位末尾 maxBytes 字节。 */
    public LogTail buildLogTail(Target target, long number, int maxBytes) {
        positive(number);
        if (maxBytes < 1 || maxBytes > MAX_LOG_BYTES) throw new IllegalArgumentException("Jenkins 日志字节数须在 1-" + MAX_LOG_BYTES + " 之间");
        var first = sendLog(target, target.jobUri(number + "/logText/progressiveText?start=0"));
        long textSize = first.headers().firstValueAsLong("X-Text-Size").orElse(-1);
        if (textSize < 0 || textSize <= maxBytes) return new LogTail(body(first), false);
        var tail = sendLog(target, target.jobUri(number + "/logText/progressiveText?start=" + (textSize - maxBytes)));
        return new LogTail(body(tail), true);
    }

    public record LogTail(String content, boolean headTruncated) { }

    private static String body(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> sendLog(Target target, URI uri) {
        var response = send(target, uri, null);
        if (response.statusCode() == 404) throw new ClientFailure("Jenkins 构建或日志不存在（HTTP 404）");
        if (response.statusCode() != 200) throw new ClientFailure("Jenkins 日志查询失败（HTTP " + response.statusCode() + "），未跟随重定向");
        return response;
    }

    private JsonNode get(Target target, URI uri, boolean missingAllowed) {
        var response = send(target, uri, null);
        if (response.statusCode() == 404 && missingAllowed) return null;
        if (response.statusCode() != 200) throw new ClientFailure("Jenkins 查询失败（HTTP " + response.statusCode() + "），未跟随重定向");
        try {
            var value = json.readTree(response.body());
            if (value == null || !value.isObject()) throw new ClientFailure("Jenkins 返回内容不是 JSON 对象");
            return value;
        } catch (tools.jackson.core.JacksonException ex) { throw new ClientFailure("Jenkins 返回的 JSON 无法解析"); }
    }

    private HttpResponse<byte[]> send(Target target, URI uri, String body) {
        if (!uri.toString().startsWith(target.baseUri().toString())) throw new IllegalArgumentException("Jenkins 请求超出已配置实例");
        String credentials = Base64.getEncoder().encodeToString((target.userName() + ":" + target.apiToken()).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", "Basic " + credentials)
                .header("Accept", "application/json");
        if (body == null) request.GET();
        else request.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body));
        var pending = http.sendAsync(request.build(), info -> new BoundedBody());
        try { return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (ExecutionException | TimeoutException ex) { pending.cancel(true); throw new ClientFailure("Jenkins 响应未能确认", ex); }
        catch (InterruptedException ex) { pending.cancel(true); Thread.currentThread().interrupt(); throw new ClientFailure("Jenkins 调用已中断，需查询既有请求状态", ex); }
    }

    private static boolean matches(JsonNode value, Map<String, String> expected) {
        var parameters = new HashMap<String, String>();
        var duplicates = new HashSet<String>();
        for (var action : value.path("actions")) for (var parameter : action.path("parameters")) {
            String name = parameter.path("name").asText();
            if (!expected.containsKey(name)) continue;
            JsonNode actual = parameter.get("value");
            if (actual == null || !(actual.isString() || actual.isBoolean() || actual.isIntegralNumber())) return false;
            if (parameters.putIfAbsent(name, actual.asText()) != null) duplicates.add(name);
        }
        return duplicates.isEmpty() && expected.entrySet().stream().allMatch(entry -> entry.getValue().equals(parameters.get(entry.getKey())));
    }
    private static void requireJobUrl(Target target, String url) { if (!target.jobUri("").toString().equals(url)) throw new ClientFailure("Jenkins 队列任务与已绑定 Job 不符"); }
    private static void positive(long value) { if (value < 1) throw new IllegalArgumentException("Jenkins 队列或构建编号必须为正整数"); }
    static void validateParameters(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty() || parameters.size() > 100) throw new IllegalArgumentException("Jenkins 参数缺失或超过上限");
        parameters.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_-]{0,63}") || value == null || value.length() > 4000 || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Jenkins 固定参数非法");
        });
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static String segment(String text) {
        if (text == null || text.isBlank() || text.equals(".") || text.equals("..") || text.length() > 200
                || text.chars().anyMatch(c -> c < 32 || c == 127 || "/\\%?#:".indexOf(c) >= 0)) throw new IllegalArgumentException("Jenkins Job 或回执路径段非法");
        return encode(text).replace("+", "%20");
    }

    /** 仅服务端持有；不会序列化到模型或页面。只支持 API Token，不关闭 CSRF。 */
    public record Target(URI baseUri, List<String> jobSegments, @com.fasterxml.jackson.annotation.JsonIgnore String userName,
                          @com.fasterxml.jackson.annotation.JsonIgnore String apiToken) {
        public Target {
            Objects.requireNonNull(baseUri);
            String rawPath = baseUri.getRawPath();
            if (!Set.of("https", "http").contains(baseUri.getScheme()) || baseUri.getHost() == null || baseUri.getRawUserInfo() != null
                    || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null || rawPath == null
                    || !rawPath.matches("(/[A-Za-z0-9_.-]+)*/?") || Arrays.stream(rawPath.split("/")).anyMatch(part -> part.equals("..") || part.equals("."))) throw new IllegalArgumentException("Jenkins 实例地址非法");
            if (!baseUri.toString().endsWith("/")) baseUri = URI.create(baseUri + "/");
            if (jobSegments == null || jobSegments.isEmpty() || jobSegments.size() > 20) throw new IllegalArgumentException("Jenkins Job 路径缺失");
            jobSegments = List.copyOf(jobSegments); jobSegments.forEach(AgentJenkinsClient::segment);
            if (userName == null || userName.isBlank() || userName.contains(":") || apiToken == null || apiToken.isBlank()) throw new IllegalArgumentException("Jenkins API Token 认证配置缺失");
        }
        public URI instanceUri(String suffix) { return baseUri.resolve(suffix); }
        public URI jobUri(String suffix) { return instanceUri(jobSegments.stream().map(part -> "job/" + segment(part) + "/").collect(Collectors.joining()) + suffix); }
        @Override public String toString() { return "JenkinsTarget[credentials=redacted]"; }
    }
    public record Probe(String jobName, boolean buildable, List<String> parameterNames) { }
    public record InstanceProbe(String version) { }
    public record QueueState(String state, long queueId, Long buildNumber, boolean parametersMatch) { }
    public record BuildState(String state, long buildNumber, String result, boolean parametersMatch, Long queueId) { }
    public record SearchResult(String state, List<Long> queueIds, List<Long> buildNumbers, int scannedBuildLimit) { }
    public static class ClientFailure extends IllegalStateException {
        ClientFailure(String message) { super(message); }
        ClientFailure(String message, Throwable cause) { super(message, cause); }
    }
    public static class SubmissionUnknown extends ClientFailure { SubmissionUnknown(String message, Throwable cause) { super(message, cause); } }
    public static class SubmissionRejected extends ClientFailure { SubmissionRejected(String message) { super(message); } }

    @PreDestroy public void close() { http.shutdownNow(); }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                if (bytes.size() + chunk.remaining() > MAX_RESPONSE) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Jenkins 响应超过1MiB上限")); return;
                }
                byte[] data = new byte[chunk.remaining()]; chunk.get(data); bytes.writeBytes(data);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
