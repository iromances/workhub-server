package cn.aslight.workhub.service.agent;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 只处理平台固定项目的分支核验及草稿 MR；不合并、删除、修改保护规则或触发流水线。 */
@Component
public class AgentGitLabClient {
    private static final int MAX_BYTES = 1024 * 1024;
    private final ObjectMapper json;
    private final Duration timeout;
    private final HttpClient http;
    @Autowired public AgentGitLabClient(ObjectMapper json) { this(json, Duration.ofSeconds(30)); }
    AgentGitLabClient(ObjectMapper json, Duration timeout) {
        this.json = json; this.timeout = timeout;
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Project project(Target target) {
        var value = request(target, "", null, false).value();
        if (value.path("id").asLong(-1) != target.projectId() || !target.projectPath().equals(value.path("path_with_namespace").asText())
                || !target.repositoryUri().toString().equals(value.path("http_url_to_repo").asText())
                || !target.webUri().toString().equals(value.path("web_url").asText())) throw new Conflict("GitLab 项目身份或仓库地址与绑定不符");
        if (!value.path("archived").isBoolean() || value.path("archived").asBoolean()) throw new Conflict("GitLab 项目已归档或状态不可确认");
        String branch = value.path("default_branch").asText(); AgentCodePolicy.branch(branch);
        return new Project(branch);
    }

    public Branch branch(Target target, String branch) {
        AgentCodePolicy.branch(branch);
        var value = request(target, "/repository/branches/" + encode(branch), null, true).value();
        if (value == null) return null;
        if (!branch.equals(value.path("name").asText()) || !value.path("protected").isBoolean() || !value.path("default").isBoolean()) {
            throw new Conflict("GitLab 分支身份或保护状态不明确");
        }
        return new Branch(branch, AgentCodeGit.sha(value.path("commit").path("id").asText()), value.path("protected").asBoolean(), value.path("default").asBoolean());
    }

    public void requireUnprotected(Target target, String branch) {
        AgentCodePolicy.branch(branch);
        var response = request(target, "/protected_branches?per_page=100&page=1", null, false);
        if (!response.value().isArray() || response.value().size() >= 100 || response.hasNextPage()) throw new Conflict("GitLab 分支保护规则未完整读取");
        for (var rule : response.value()) {
            String name = rule.path("name").asText();
            if (name.isBlank() || name.length() > 300) throw new Conflict("GitLab 分支保护规则无效");
            String pattern = Arrays.stream(name.split("\\*", -1)).map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining(".*"));
            if (branch.matches(pattern)) throw new Conflict("候选分支命中 GitLab 保护规则，未创建分支");
        }
    }

    /** 搜索所有状态，避免把人工关闭的原 MR 当作从未创建；分页不完整时不允许新建。 */
    public List<MergeRequest> find(Target target, String source, String destination) {
        AgentCodePolicy.branch(source); AgentCodePolicy.branch(destination);
        var response = request(target, "/merge_requests?scope=all&state=all&source_branch=" + encode(source)
                + "&target_branch=" + encode(destination) + "&per_page=100&page=1", null, false);
        if (!response.value().isArray() || response.value().size() > 100 || response.hasNextPage()) throw new Conflict("GitLab MR 搜索不完整，不能据此新建");
        var results = new ArrayList<MergeRequest>();
        for (var value : response.value()) results.add(mergeRequest(target, value, source, destination));
        return List.copyOf(results);
    }

    public MergeRequest mergeRequest(Target target, long iid, String source, String destination) {
        if (iid < 1) throw new IllegalArgumentException("MR 编号非法");
        var value = request(target, "/merge_requests/" + iid, null, true).value();
        if (value == null) return null;
        var result = mergeRequest(target, value, source, destination);
        if (result.iid() != iid) throw new Conflict("GitLab MR 编号与回执不符");
        return result;
    }

    /** 调用前须落盘创建意图；异常不代表未创建，调用方必须回读同一分支的 MR。 */
    public MergeRequest create(Target target, String source, String destination, String title, String description) {
        AgentCodePolicy.branch(source); AgentCodePolicy.branch(destination);
        if (source.equals(destination) || title == null || !title.startsWith("Draft: ") || title.length() > 255
                || description == null || description.length() > 20_000) throw new IllegalArgumentException("草稿 MR 材料非法");
        String body = json.writeValueAsString(Map.of("source_branch", source, "target_branch", destination, "title", title,
                "description", description, "remove_source_branch", false, "squash", false));
        try { return mergeRequest(target, request(target, "/merge_requests", body, false).value(), source, destination); }
        catch (Rejected ex) { throw ex; }
        catch (RuntimeException ex) { throw new Unknown("GitLab MR 创建结果未确认，需要查询原分支关联记录", ex); }
    }

    private MergeRequest mergeRequest(Target target, JsonNode value, String source, String destination) {
        long iid = value.path("iid").asLong(-1);
        if (iid < 1 || value.path("project_id").asLong(-1) != target.projectId() || value.path("source_project_id").asLong(-1) != target.projectId()
                || value.path("target_project_id").asLong(-1) != target.projectId() || !source.equals(value.path("source_branch").asText())
                || !destination.equals(value.path("target_branch").asText())) throw new Conflict("GitLab MR 的项目、分支或编号与本次交付不符");
        String url = target.webUri() + "/-/merge_requests/" + iid;
        if (!url.equals(value.path("web_url").asText())) throw new Conflict("GitLab MR 地址不属于已绑定项目");
        String sha = value.path("sha").isString() ? AgentCodeGit.sha(value.path("sha").asText()) : null;
        // 新 MR 的提交字段可能异步生成，不能用请求中的 SHA 代填实际值。
        return new MergeRequest(iid, url, value.path("state").asText(), sha,
                value.path("draft").asBoolean() || value.path("work_in_progress").asBoolean(), value.path("description").asText());
    }

    private Response request(Target target, String suffix, String body, boolean missingAllowed) {
        var builder = HttpRequest.newBuilder(target.apiUri(suffix)).timeout(timeout).header("PRIVATE-TOKEN", target.accessToken()).header("Accept", "application/json");
        if (body == null) builder.GET(); else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        var pending = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> response;
        try { response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (InterruptedException ex) { pending.cancel(true); Thread.currentThread().interrupt(); throw new Failure("GitLab 调用已中断", ex); }
        catch (ExecutionException | TimeoutException ex) { pending.cancel(true); throw new Failure("GitLab 响应未确认", ex); }
        try (InputStream stream = response.body(); var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            var reading = reader.submit(() -> stream.readNBytes(MAX_BYTES + 1));
            byte[] bytes;
            try { bytes = reading.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (InterruptedException ex) { reading.cancel(true); stream.close(); Thread.currentThread().interrupt(); throw new Failure("GitLab 内容读取中断", ex); }
            catch (ExecutionException | TimeoutException ex) { reading.cancel(true); stream.close(); throw new Failure("GitLab 内容未完整读取", ex); }
            if (bytes.length > MAX_BYTES) throw new Failure("GitLab 响应超过1MiB限制");
            int status = response.statusCode();
            if (body != null && Set.of(400, 401, 403, 404, 405, 422).contains(status)) throw new Rejected("GitLab 拒绝 MR 创建（HTTP " + status + "）");
            if (status == 404 && missingAllowed) return new Response(null, false);
            if (status != (body == null ? 200 : 201)) throw new Failure("GitLab 请求未成功（HTTP " + status + "），未跟随跳转");
            JsonNode value = json.readTree(bytes);
            if (value == null || !value.isContainer()) throw new Failure("GitLab 返回内容不是 JSON 对象或列表");
            return new Response(value, !response.headers().firstValue("X-Next-Page").orElse("").isBlank());
        } catch (IOException | tools.jackson.core.JacksonException ex) { throw new Failure("GitLab 响应内容不可读取", ex); }
    }

    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    public record Target(URI baseUri, long projectId, String projectPath, @com.fasterxml.jackson.annotation.JsonIgnore String accessToken) {
        public Target {
            if (baseUri == null || !Set.of("http", "https").contains(baseUri.getScheme()) || baseUri.getHost() == null || baseUri.getRawUserInfo() != null
                    || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null || !baseUri.getRawPath().matches("(/[A-Za-z0-9_-]+)*/?")) throw new IllegalArgumentException("GitLab 实例地址非法");
            if (!baseUri.toString().endsWith("/")) baseUri = URI.create(baseUri + "/");
            if (projectId < 1 || projectPath == null || projectPath.length() > 300 || !projectPath.matches("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)+")
                    || Arrays.stream(projectPath.split("/")).anyMatch(part -> part.startsWith(".") || part.endsWith(".git"))) throw new IllegalArgumentException("GitLab 项目绑定非法");
            if (accessToken == null || accessToken.isBlank() || accessToken.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("GitLab 凭据缺失或非法");
        }
        URI apiUri(String suffix) { return baseUri.resolve("api/v4/projects/" + projectId + suffix); }
        URI repositoryUri() { return baseUri.resolve(projectPath + ".git"); }
        URI webUri() { return baseUri.resolve(projectPath); }
        @Override public String toString() { return "GitLabTarget[credentials=redacted]"; }
    }
    public record Project(String defaultBranch) { }
    public record Branch(String name, String sha, boolean protectedBranch, boolean defaultBranch) { }
    public record MergeRequest(long iid, String webUrl, String state, String sha, boolean draft, String description) { }
    private record Response(JsonNode value, boolean hasNextPage) { }
    public static class Failure extends IllegalStateException {
        Failure(String message) { super(message); } Failure(String message, Throwable cause) { super(message, cause); }
    }
    public static class Conflict extends Failure { Conflict(String message) { super(message); } }
    public static class Rejected extends Failure { Rejected(String message) { super(message); } }
    public static class Unknown extends Failure { Unknown(String message, Throwable cause) { super(message, cause); } }
    @PreDestroy public void close() { http.shutdownNow(); }
}
