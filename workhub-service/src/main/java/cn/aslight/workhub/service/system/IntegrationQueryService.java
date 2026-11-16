package cn.aslight.workhub.service.system;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.service.agent.AgentJenkinsClient;
import cn.aslight.workhub.service.agent.AgentJenkinsPolicy;
import cn.aslight.workhub.service.intake.ZentaoClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 系统集成 MCP 只读查询服务：面向 AI 的禅道/Jenkins 查询编排。
 * 凭据只在服务端解密使用；结果做字段截断与深度收敛；每次调用写 MCP 审计（不记录返回数据与凭据）。
 * 不提供任何写操作：禅道建测试单仍走提测链路，Jenkins 触发构建仍走数字员工审批链路。
 */
@Service
public class IntegrationQueryService {

    public static final int DEFAULT_SEARCH_LIMIT = 20;
    public static final int MAX_SEARCH_LIMIT = 50;
    public static final int DEFAULT_JENKINS_LIST_LIMIT = 50;
    public static final int MAX_JENKINS_LIST_LIMIT = 200;
    public static final int DEFAULT_LOG_LINES = 200;
    public static final int MAX_LOG_LINES = 2000;
    public static final int DEFAULT_LOG_BYTES = 64 * 1024;
    public static final int MAX_LOG_BYTES = 256 * 1024;
    private static final int MAX_FIELD_CHARS = 4000;
    private static final int MAX_DEPTH = 4;

    private final ZentaoClient zentaoClient;
    private final AgentJenkinsPolicy jenkinsPolicy;
    private final AgentJenkinsClient jenkinsClient;
    private final SysConfigService sysConfigService;
    private final McpAuditLogger auditLogger;
    private final com.fasterxml.jackson.databind.ObjectMapper jackson2;
    private final tools.jackson.databind.ObjectMapper jackson3 = new tools.jackson.databind.ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public IntegrationQueryService(ZentaoClient zentaoClient,
                                   AgentJenkinsPolicy jenkinsPolicy,
                                   AgentJenkinsClient jenkinsClient,
                                   SysConfigService sysConfigService) {
        this(zentaoClient, jenkinsPolicy, jenkinsClient, sysConfigService,
                McpAuditLogger.defaultLogger(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    public IntegrationQueryService(ZentaoClient zentaoClient,
                                   AgentJenkinsPolicy jenkinsPolicy,
                                   AgentJenkinsClient jenkinsClient,
                                   SysConfigService sysConfigService,
                                   McpAuditLogger auditLogger) {
        this.zentaoClient = zentaoClient;
        this.jenkinsPolicy = jenkinsPolicy;
        this.jenkinsClient = jenkinsClient;
        this.sysConfigService = sysConfigService;
        this.auditLogger = auditLogger;
        this.jackson2 = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    // ---------------- 禅道（只读） ----------------

    public Map<String, Object> searchZentaoBugs(Long productId, Long executionId, String keyword, Integer limit) {
        int effectiveLimit = bounded(limit, "limit", DEFAULT_SEARCH_LIMIT, MAX_SEARCH_LIMIT);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", productId);
        params.put("executionId", executionId);
        params.put("keyword", keyword == null ? "" : keyword);
        params.put("limit", effectiveLimit);
        return cast(audited("search_zentao_bugs", params, () -> {
            List<tools.jackson.databind.JsonNode> matched = zentaoClient.searchBugs(productId, executionId, keyword, effectiveLimit);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("count", matched.size());
            output.put("limit", effectiveLimit);
            output.put("truncated", matched.size() >= effectiveLimit);
            output.put("bugs", matched.stream().map(this::toSanitizedMap).toList());
            return output;
        }));
    }

    public Map<String, Object> getZentaoBug(String bugId) {
        return getZentaoObject("get_zentao_bug", bugId, "bug", zentaoClient::getBug);
    }

    public Map<String, Object> getZentaoStory(String storyId) {
        return getZentaoObject("get_zentao_story", storyId, "story", zentaoClient::getStory);
    }

    public Map<String, Object> getZentaoTask(String taskId) {
        return getZentaoObject("get_zentao_task", taskId, "task", zentaoClient::getTask);
    }

    public Map<String, Object> getZentaoExecution(String executionId) {
        return getZentaoObject("get_zentao_execution", executionId, "execution", zentaoClient::getExecution);
    }

    private Map<String, Object> getZentaoObject(String tool, String id, String pathPrefix,
                                                java.util.function.LongFunction<tools.jackson.databind.JsonNode> fetcher) {
        long objectId = positiveLong(id, "id");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", objectId);
        return cast(audited(tool, params, () -> {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("id", objectId);
            String viewUrl = zentaoClient.viewUrl(pathPrefix, objectId);
            if (viewUrl != null) {
                output.put("viewUrl", viewUrl);
            }
            output.put(resultKey(tool), toSanitizedMap(fetcher.apply(objectId)));
            return output;
        }));
    }

    // ---------------- Jenkins（只读） ----------------

    public Map<String, Object> listJenkinsJobs(String instanceId, String folder, Integer limit) {
        int effectiveLimit = bounded(limit, "limit", DEFAULT_JENKINS_LIST_LIMIT, MAX_JENKINS_LIST_LIMIT);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("instanceId", instanceId == null ? "" : instanceId);
        params.put("folder", folder == null ? "" : folder);
        params.put("limit", effectiveLimit);
        return cast(audited("list_jenkins_jobs", params, () -> {
            List<String> segments = jobSegments(folder, false);
            var resolved = resolveJenkinsTarget(instanceId, segments.isEmpty() ? List.of("_root") : segments);
            var jobs = jenkinsClient.listJobs(resolved.target(), segments, effectiveLimit);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("instanceId", resolved.instance().id());
            output.put("folder", folder == null ? "" : folder);
            output.put("limit", effectiveLimit);
            output.put("jobs", toSanitizedList(jobs.path("jobs")));
            return output;
        }));
    }

    public Map<String, Object> getJenkinsJob(String instanceId, String jobPath) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("instanceId", instanceId == null ? "" : instanceId);
        params.put("jobPath", jobPath == null ? "" : jobPath);
        return cast(audited("get_jenkins_job", params, () -> {
            var resolved = resolveJenkinsTarget(instanceId, jobSegments(jobPath, true));
            var detail = jenkinsClient.jobDetail(resolved.target());
            List<String> parameterNames = jenkinsClient.probe(resolved.target()).parameterNames();
            Map<String, Object> job = toSanitizedMap(detail);
            job.put("parameterNames", parameterNames);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("instanceId", resolved.instance().id());
            output.put("jobPath", jobPath);
            output.put("job", job);
            return output;
        }));
    }

    public Map<String, Object> getJenkinsBuild(String instanceId, String jobPath, String buildNumber) {
        long number = positiveLong(buildNumber, "buildNumber");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("instanceId", instanceId == null ? "" : instanceId);
        params.put("jobPath", jobPath == null ? "" : jobPath);
        params.put("buildNumber", number);
        return cast(audited("get_jenkins_build", params, () -> {
            var resolved = resolveJenkinsTarget(instanceId, jobSegments(jobPath, true));
            var build = jenkinsClient.buildDetail(resolved.target(), number);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("instanceId", resolved.instance().id());
            output.put("jobPath", jobPath);
            output.put("buildNumber", number);
            output.put("build", toSanitizedMap(build));
            return output;
        }));
    }

    public Map<String, Object> getJenkinsBuildLog(String instanceId, String jobPath, String buildNumber, Integer lines, Integer maxBytes) {
        long number = positiveLong(buildNumber, "buildNumber");
        int effectiveLines = bounded(lines, "lines", DEFAULT_LOG_LINES, MAX_LOG_LINES);
        int effectiveBytes = bounded(maxBytes, "maxBytes", DEFAULT_LOG_BYTES, MAX_LOG_BYTES);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("instanceId", instanceId == null ? "" : instanceId);
        params.put("jobPath", jobPath == null ? "" : jobPath);
        params.put("buildNumber", number);
        params.put("lines", effectiveLines);
        params.put("maxBytes", effectiveBytes);
        return cast(audited("get_jenkins_build_log", params, () -> {
            var resolved = resolveJenkinsTarget(instanceId, jobSegments(jobPath, true));
            AgentJenkinsClient.LogTail tail = jenkinsClient.buildLogTail(resolved.target(), number, effectiveBytes);
            // 客户端按字节尾读后，这里再按行数收敛为末尾 N 行
            String[] allLines = tail.content().split("\n", -1);
            boolean linesTruncated = allLines.length > effectiveLines;
            String content = linesTruncated
                    ? String.join("\n", Arrays.copyOfRange(allLines, allLines.length - effectiveLines, allLines.length))
                    : tail.content();
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("instanceId", resolved.instance().id());
            output.put("jobPath", jobPath);
            output.put("buildNumber", number);
            output.put("lines", effectiveLines);
            output.put("headTruncated", tail.headTruncated());
            output.put("truncated", tail.headTruncated() || linesTruncated);
            output.put("content", content);
            return output;
        }));
    }

    // ---------------- 内部 ----------------

    private record JenkinsResolved(AgentJenkinsPolicy.Instance instance, AgentJenkinsClient.Target target) {
    }

    /** 解析目标实例（默认首个启用实例）并构造只读 Target；凭据只在服务端读取。 */
    private JenkinsResolved resolveJenkinsTarget(String instanceId, List<String> jobSegments) {
        AgentJenkinsPolicy.Instance instance = jenkinsPolicy.current().instances().stream()
                .filter(AgentJenkinsPolicy.Instance::enabled)
                .filter(item -> instanceId == null || instanceId.isBlank() || instanceId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(instanceId == null || instanceId.isBlank()
                        ? "Jenkins 尚无启用的实例（agent.jenkins/policy）"
                        : "Jenkins 实例不存在或未启用：" + instanceId));
        String token = sysConfigSecret(instance.credentialGroup(), instance.tokenKey());
        return new JenkinsResolved(instance, new AgentJenkinsClient.Target(
                URI.create(instance.baseUrl()), jobSegments, instance.userName(), token));
    }

    /** Jenkins API Token 只能来自已启用的加密配置（与发布链路同源）。 */
    private String sysConfigSecret(String credentialGroup, String tokenKey) {
        return sysConfigService.requireSecretValue(credentialGroup, tokenKey);
    }

    /** Job 路径按斜杠分段，段内容交由 Target 既有校验把关。 */
    private List<String> jobSegments(String jobPath, boolean required) {
        if (jobPath == null || jobPath.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Job 路径不能为空");
            }
            return List.of();
        }
        List<String> segments = Arrays.stream(jobPath.split("/"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        if (segments.isEmpty() && required) {
            throw new IllegalArgumentException("Job 路径不能为空");
        }
        return segments;
    }

    private Object audited(String tool, Map<String, Object> params, Supplier<Object> action) {
        long start = System.currentTimeMillis();
        try {
            Object result = action.get();
            audit(tool, params, "SUCCESS", null, System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            audit(tool, params, "ERROR", ex.getMessage(), System.currentTimeMillis() - start);
            throw ex;
        }
    }

    private void audit(String tool, Map<String, Object> params, String result, String error, long elapsedMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool", tool);
        fields.put("system", "INTEGRATION");
        fields.put("params", params);
        fields.put("result", result);
        if (error != null) {
            fields.put("error", error);
        }
        fields.put("elapsedMs", elapsedMs);
        auditLogger.record(fields);
    }

    /** 工具结果键名（bug/story/task/execution）。 */
    private static String resultKey(String tool) {
        return switch (tool) {
            case "get_zentao_bug" -> "bug";
            case "get_zentao_story" -> "story";
            case "get_zentao_task" -> "task";
            case "get_zentao_execution" -> "execution";
            default -> "object";
        };
    }

    /** tools.jackson(3) 节点转 Jackson(2) 树后做深度与字段长度收敛。 */
    private Map<String, Object> toSanitizedMap(tools.jackson.databind.JsonNode node) {
        return cast(sanitize(toJackson2(node), 0));
    }

    private List<Object> toSanitizedList(tools.jackson.databind.JsonNode array) {
        List<Object> items = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(item -> items.add(sanitize(toJackson2(item), 0)));
        }
        return items;
    }

    private com.fasterxml.jackson.databind.JsonNode toJackson2(tools.jackson.databind.JsonNode node) {
        try {
            // 必须由 Jackson3 序列化节点原文；Jackson2 无法识别 Jackson3 节点类型
            String json = jackson3.writeValueAsString(node);
            return jackson2.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("查询结果转换失败", ex);
        }
    }

    private Object sanitize(JsonNode node, int depth) {
        if (node.isTextual()) {
            String text = node.asText();
            return text.length() > MAX_FIELD_CHARS ? text.substring(0, MAX_FIELD_CHARS) + "…[字段截断]" : text;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isObject()) {
            if (depth >= MAX_DEPTH) {
                return Map.of("omitted", true);
            }
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), sanitize(entry.getValue(), depth + 1)));
            return map;
        }
        if (node.isArray()) {
            if (depth >= MAX_DEPTH) {
                return Map.of("items", node.size(), "omitted", true);
            }
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(sanitize(item, depth + 1)));
            return list;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    /** 可选整数参数：null 用默认值；非法、零或负数拒绝；超出上限收敛。 */
    private int bounded(Integer value, String field, int defaultValue, int max) {
        if (value == null) {
            return defaultValue;
        }
        if (value < 1) {
            throw new IllegalArgumentException("MCP 参数必须为正整数：" + field);
        }
        return Math.min(value, max);
    }

    /** 必填正整数参数（禅道/Jenkins 的 ID 与构建号）。 */
    private long positiveLong(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MCP 参数缺少字段：" + field);
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < 1) {
                throw new IllegalArgumentException("MCP 参数必须为正整数：" + field);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MCP 参数必须是数字：" + field);
        }
    }

    private <T> T cast(Object value) {
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return typed;
    }
}
