package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.model.ops.OpsMonitorCheckResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.OpsMonitorResponse;
import cn.aslight.workhub.model.ops.OpsMonitorSaveRequest;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobExecutorResponse;
import cn.aslight.workhub.model.ops.XxlJobLogPageResponse;
import cn.aslight.workhub.service.mcp.McpCryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

@Service
public class OpsMonitorService {

    private static final String XXL_JOB = "XXL_JOB";

    private final OpsMonitorMapper opsMonitorMapper;
    private final OpsMonitorSchemaInitializer schemaInitializer;
    private final McpCryptoService mcpCryptoService;
    private final XxlJobMonitorCollector xxlJobMonitorCollector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpsMonitorService(OpsMonitorMapper opsMonitorMapper,
                             OpsMonitorSchemaInitializer schemaInitializer,
                             McpCryptoService mcpCryptoService,
                             XxlJobMonitorCollector xxlJobMonitorCollector) {
        this.opsMonitorMapper = opsMonitorMapper;
        this.schemaInitializer = schemaInitializer;
        this.mcpCryptoService = mcpCryptoService;
        this.xxlJobMonitorCollector = xxlJobMonitorCollector;
    }

    public List<OpsMonitorResponse> list(String monitorType,
                                         String businessLineCode,
                                         String environmentCode,
                                         String keyword,
                                         boolean enabledOnly) {
        schemaInitializer.ensureInitialized();
        return opsMonitorMapper.findAll(normalizeMonitorTypeOrNull(monitorType),
                        trimToNull(businessLineCode),
                        trimToNull(environmentCode),
                        trimToNull(keyword),
                        enabledOnly)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OpsMonitorResponse create(OpsMonitorSaveRequest request) {
        schemaInitializer.ensureInitialized();
        OpsMonitorEntity entity = toEntity(new OpsMonitorEntity(), request);
        OpsMonitorEntity existing = opsMonitorMapper.findByMonitorKey(entity.getMonitorKey());
        if (existing != null) {
            entity.setId(existing.getId());
            opsMonitorMapper.update(entity);
            return toResponse(requireExisting(existing.getId()));
        }
        opsMonitorMapper.insert(entity);
        return toResponse(opsMonitorMapper.findByMonitorKey(entity.getMonitorKey()));
    }

    @Transactional
    public OpsMonitorResponse update(Long id, OpsMonitorSaveRequest request) {
        schemaInitializer.ensureInitialized();
        OpsMonitorEntity existing = requireExisting(id);
        OpsMonitorEntity entity = toEntity(existing, request);
        OpsMonitorEntity duplicate = opsMonitorMapper.findByMonitorKey(entity.getMonitorKey());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalArgumentException("监测 key 已存在");
        }
        entity.setId(id);
        opsMonitorMapper.update(entity);
        return toResponse(requireExisting(id));
    }

    @Transactional
    public void delete(Long id) {
        schemaInitializer.ensureInitialized();
        if (opsMonitorMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("监测配置不存在");
        }
    }

    @Transactional
    public OpsMonitorCheckResponse check(Long id) {
        schemaInitializer.ensureInitialized();
        OpsMonitorEntity entity = requireExisting(id);
        if (!XXL_JOB.equals(entity.getMonitorType())) {
            throw new IllegalArgumentException("MQ 监测采集下一步接入");
        }
        try {
            OpsMonitorCheckResponse response = xxlJobMonitorCollector.check(entity);
            opsMonitorMapper.updateCheckResult(id, response.status(), truncate(response.message(), 1000));
            return response;
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            opsMonitorMapper.updateCheckResult(id, "ERROR", truncate(message, 1000));
            return new OpsMonitorCheckResponse(
                    id,
                    entity.getMonitorType(),
                    entity.getMonitorKey(),
                    "ERROR",
                    message,
                    LocalDateTime.now(),
                    Map.of("exception", ex.getClass().getName())
            );
        }
    }

    public List<XxlJobDashboardResponse> dashboard(String businessLineCode,
                                                   String environmentCode,
                                                   boolean enabledOnly) {
        schemaInitializer.ensureInitialized();
        return opsMonitorMapper.findAll(XXL_JOB,
                        trimToNull(businessLineCode),
                        trimToNull(environmentCode),
                        null,
                        enabledOnly)
                .stream()
                .map(this::toDashboardConfig)
                .toList();
    }

    public List<XxlJobExecutorResponse> xxlJobExecutors(String businessLineCode,
                                                        String environmentCode,
                                                        String xxlJobDatabaseName) {
        schemaInitializer.ensureInitialized();
        return xxlJobMonitorCollector.listExecutors(
                requireValue(businessLineCode, "业务线不能为空"),
                requireValue(environmentCode, "环境不能为空"),
                requireValue(xxlJobDatabaseName, "XXL-JOB 数据库名不能为空")
        );
    }

    public XxlJobDashboardResponse xxlJobDetail(Long id, LocalDate startDate, LocalDate endDate) {
        return xxlJobDetail(id, startDate, endDate, 1, 20);
    }

    public XxlJobDashboardResponse xxlJobDetail(Long id, LocalDate startDate, LocalDate endDate, int page, int pageSize) {
        return xxlJobDetail(id, startDate, endDate, page, pageSize, null, null);
    }

    public XxlJobDashboardResponse xxlJobDetail(Long id,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                int page,
                                                int pageSize,
                                                String author,
                                                String executorAppName) {
        return xxlJobDetail(id, startDate, endDate, page, pageSize, author, executorAppName, "ALL");
    }

    public XxlJobDashboardResponse xxlJobDetail(Long id,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                int page,
                                                int pageSize,
                                                String author,
                                                String executorAppName,
                                                String logStatus) {
        schemaInitializer.ensureInitialized();
        OpsMonitorEntity entity = requireExisting(id);
        if (!XXL_JOB.equals(entity.getMonitorType())) {
            throw new IllegalArgumentException("MQ 监测详情下一步接入");
        }
        LocalDate normalizedEndDate = endDate == null ? LocalDate.now() : endDate;
        LocalDate normalizedStartDate = startDate == null ? normalizedEndDate.minusMonths(1) : startDate;
        try {
            return xxlJobMonitorCollector.collect(entity, normalizedStartDate, normalizedEndDate, page, pageSize, author, executorAppName, logStatus);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return xxlJobDashboardError(entity, message, page, pageSize);
        }
    }

    public XxlJobLogPageResponse xxlJobLogs(Long id,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            int page,
                                            int pageSize,
                                            String author,
                                            String executorAppName,
                                            String logStatus) {
        schemaInitializer.ensureInitialized();
        OpsMonitorEntity entity = requireExisting(id);
        if (!XXL_JOB.equals(entity.getMonitorType())) {
            throw new IllegalArgumentException("MQ 监测详情下一步接入");
        }
        LocalDate normalizedEndDate = endDate == null ? LocalDate.now() : endDate;
        LocalDate normalizedStartDate = startDate == null ? normalizedEndDate.minusDays(7) : startDate;
        try {
            return xxlJobMonitorCollector.collectLogs(entity, normalizedStartDate, normalizedEndDate, page, pageSize, author, executorAppName, logStatus);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new XxlJobLogPageResponse(
                    entity.getId(),
                    entity.getMonitorKey(),
                    entity.getBusinessLineCode(),
                    entity.getEnvironmentCode(),
                    entity.getName(),
                    entity.getXxlJobDatabaseName(),
                    "ERROR",
                    message,
                    LocalDateTime.now(),
                    0,
                    page,
                    pageSize,
                    List.of()
            );
        }
    }

    private XxlJobDashboardResponse toDashboardConfig(OpsMonitorEntity entity) {
        String status = trimToNull(entity.getLastStatus());
        if (status == null) {
            status = Boolean.FALSE.equals(entity.getEnabled()) ? "DISABLED" : "CONFIGURED";
        }
        String message = trimToNull(entity.getLastMessage());
        if (message == null) {
            message = "仅展示本地 XXL-JOB 监测配置";
        }
        return new XxlJobDashboardResponse(
                entity.getId(),
                entity.getMonitorKey(),
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getName(),
                entity.getXxlJobDatabaseName(),
                status,
                message,
                entity.getLastCheckedAt(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                0,
                1,
                0,
                List.of()
        );
    }

    private XxlJobDashboardResponse xxlJobDashboardError(OpsMonitorEntity entity, String message) {
        return xxlJobDashboardError(entity, message, 1, 0);
    }

    private XxlJobDashboardResponse xxlJobDashboardError(OpsMonitorEntity entity, String message, int page, int pageSize) {
        return new XxlJobDashboardResponse(
                entity.getId(),
                entity.getMonitorKey(),
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getName(),
                entity.getXxlJobDatabaseName(),
                "ERROR",
                message,
                LocalDateTime.now(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "",
                0,
                page,
                pageSize,
                List.of()
        );
    }

    private OpsMonitorCheckResponse collectXxlJob(OpsMonitorEntity entity) throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String baseUrl = stripTrailingSlash(requireValue(entity.getAdminBaseUrl(), "XXL-JOB Admin 地址不能为空"));
        login(client, baseUrl, entity);
        Integer groupId = resolveJobGroup(client, baseUrl, entity);
        JsonNode job = resolveJob(client, baseUrl, entity, groupId);
        JsonNode log = resolveLatestLog(client, baseUrl, job.path("id").asText());
        return buildCheckResponse(entity, job, log);
    }

    private void login(HttpClient client, String baseUrl, OpsMonitorEntity entity) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userName", requireValue(entity.getUsername(), "XXL-JOB 用户名不能为空"));
        form.put("password", requireValue(mcpCryptoService.decrypt(entity.getPasswordEncrypted()), "XXL-JOB 密码不能为空"));
        form.put("ifRemember", "on");
        JsonNode response = postForm(client, baseUrl + "/login", form);
        int code = response.path("code").asInt(200);
        if (code != 200) {
            throw new IllegalArgumentException("XXL-JOB 登录失败：" + response.path("msg").asText("未知错误"));
        }
    }

    private Integer resolveJobGroup(HttpClient client, String baseUrl, OpsMonitorEntity entity) throws Exception {
        String appName = trimToNull(entity.getExecutorAppName());
        if (appName == null) {
            return null;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("appname", appName);
        form.put("title", "");
        form.put("start", "0");
        form.put("length", "20");
        JsonNode response = postForm(client, baseUrl + "/jobgroup/pageList", form);
        for (JsonNode row : response.path("data")) {
            if (appName.equals(row.path("appname").asText())) {
                return row.path("id").asInt();
            }
        }
        throw new IllegalArgumentException("XXL-JOB 执行器不存在：" + appName);
    }

    private JsonNode resolveJob(HttpClient client, String baseUrl, OpsMonitorEntity entity, Integer groupId) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("jobGroup", groupId == null ? "0" : String.valueOf(groupId));
        form.put("triggerStatus", "-1");
        form.put("jobDesc", trimToEmpty(entity.getJobDesc()));
        form.put("executorHandler", trimToEmpty(entity.getJobHandler()));
        form.put("author", "");
        form.put("start", "0");
        form.put("length", "20");
        JsonNode response = postForm(client, baseUrl + "/jobinfo/pageList", form);
        String handler = trimToNull(entity.getJobHandler());
        String jobDesc = trimToNull(entity.getJobDesc());
        for (JsonNode row : response.path("data")) {
            boolean handlerMatches = handler == null || handler.equals(row.path("executorHandler").asText());
            boolean descMatches = jobDesc == null || jobDesc.equals(row.path("jobDesc").asText());
            if (handlerMatches && descMatches) {
                return row;
            }
        }
        throw new IllegalArgumentException("XXL-JOB 任务不存在：" + firstNonBlank(handler, jobDesc));
    }

    private JsonNode resolveLatestLog(HttpClient client, String baseUrl, String jobId) throws Exception {
        if (jobId == null || jobId.isBlank()) {
            return objectMapper.createObjectNode();
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("jobId", jobId);
        form.put("triggerTimeStart", "");
        form.put("triggerTimeEnd", "");
        form.put("logStatus", "-1");
        form.put("start", "0");
        form.put("length", "1");
        JsonNode response = postForm(client, baseUrl + "/joblog/pageList", form);
        JsonNode data = response.path("data");
        return data.isArray() && !data.isEmpty() ? data.get(0) : objectMapper.createObjectNode();
    }

    private OpsMonitorCheckResponse buildCheckResponse(OpsMonitorEntity entity, JsonNode job, JsonNode log) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("jobId", job.path("id").asText(""));
        detail.put("jobDesc", job.path("jobDesc").asText(""));
        detail.put("executorHandler", job.path("executorHandler").asText(""));
        detail.put("triggerStatus", job.path("triggerStatus").asInt(-1));
        detail.put("lastLogId", log.path("id").asText(""));
        detail.put("lastTriggerTime", log.path("triggerTime").asText(""));
        detail.put("lastHandleTime", log.path("handleTime").asText(""));
        detail.put("lastHandleCode", log.path("handleCode").asInt(0));
        String status;
        String message;
        if (job.path("triggerStatus").asInt(0) != 1) {
            status = "WARN";
            message = "XXL-JOB 任务未启用";
        } else if (log.isMissingNode() || log.isEmpty()) {
            status = "WARN";
            message = "XXL-JOB 任务已启用，但未找到最近执行日志";
        } else if (log.path("handleCode").asInt(0) == 200) {
            status = "UP";
            message = "XXL-JOB 最近一次执行成功";
        } else {
            status = "ERROR";
            message = "XXL-JOB 最近一次执行失败：" + log.path("handleMsg").asText("");
        }
        return new OpsMonitorCheckResponse(
                entity.getId(),
                entity.getMonitorType(),
                entity.getMonitorKey(),
                status,
                truncate(message, 1000),
                LocalDateTime.now(),
                detail
        );
    }

    private JsonNode postForm(HttpClient client, String url, Map<String, String> form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("XXL-JOB 请求失败：" + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String formBody(Map<String, String> form) {
        StringJoiner joiner = new StringJoiner("&");
        form.forEach((key, value) -> joiner.add(encode(key) + "=" + encode(value == null ? "" : value)));
        return joiner.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private OpsMonitorEntity toEntity(OpsMonitorEntity entity, OpsMonitorSaveRequest request) {
        String monitorType = normalizeMonitorType(request.getMonitorType());
        if (!XXL_JOB.equals(monitorType)) {
            throw new IllegalArgumentException("MQ 监测采集下一步接入");
        }
        entity.setMonitorType(monitorType);
        String businessLineCode = requireValue(request.getBusinessLineCode(), "业务线不能为空");
        String environmentCode = requireValue(request.getEnvironmentCode(), "环境不能为空");
        entity.setBusinessLineCode(businessLineCode);
        entity.setEnvironmentCode(environmentCode);
        entity.setMonitorKey(defaultValue(request.getMonitorKey(), businessLineCode + ":" + environmentCode + ":XXL_JOB"));
        entity.setName(defaultValue(request.getName(), businessLineCode + " " + environmentCode + " XXL-JOB"));
        entity.setAdminBaseUrl(trimToNull(request.getAdminBaseUrl()));
        entity.setUsername(trimToNull(request.getUsername()));
        entity.setPasswordEncrypted(encryptOrKeep(request.getPassword(), entity.getPasswordEncrypted()));
        entity.setXxlJobDatabaseName(requireValue(request.getXxlJobDatabaseName(), "XXL-JOB 数据库名不能为空"));
        entity.setExecutorAppName(normalizeExecutorAppName(request.getExecutorAppName()));
        entity.setJobHandler(null);
        entity.setJobDesc(null);
        entity.setMqTopic(null);
        entity.setMqConsumerGroup(null);
        entity.setMqLagThreshold(null);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        return entity;
    }

    private OpsMonitorResponse toResponse(OpsMonitorEntity entity) {
        return new OpsMonitorResponse(
                entity.getId(),
                entity.getMonitorType(),
                entity.getMonitorKey(),
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getName(),
                entity.getAdminBaseUrl(),
                entity.getUsername(),
                trimToNull(entity.getPasswordEncrypted()) != null,
                entity.getXxlJobDatabaseName(),
                entity.getExecutorAppName(),
                entity.getJobHandler(),
                entity.getJobDesc(),
                entity.getMqTopic(),
                entity.getMqConsumerGroup(),
                entity.getMqLagThreshold(),
                entity.getEnabled(),
                entity.getLastStatus(),
                entity.getLastMessage(),
                entity.getLastCheckedAt(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OpsMonitorEntity requireExisting(Long id) {
        OpsMonitorEntity entity = opsMonitorMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("监测配置不存在");
        }
        return entity;
    }

    private String normalizeMonitorType(String monitorType) {
        String normalized = requireValue(monitorType, "监测类型不能为空").toUpperCase();
        if (!XXL_JOB.equals(normalized) && !"MQ".equals(normalized)) {
            throw new IllegalArgumentException("监测类型只支持 XXL_JOB 或 MQ");
        }
        return normalized;
    }

    private String normalizeMonitorTypeOrNull(String monitorType) {
        return monitorType == null || monitorType.isBlank() ? null : normalizeMonitorType(monitorType);
    }

    private String encryptOrKeep(String plainText, String existingCipherText) {
        String normalized = trimToNull(plainText);
        return normalized == null ? existingCipherText : mcpCryptoService.encrypt(normalized);
    }

    private String stripTrailingSlash(String value) {
        String normalized = requireValue(value, "地址不能为空");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        return normalizedFirst == null ? trimToNull(second) : normalizedFirst;
    }

    private String trimToEmpty(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "" : normalized;
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String defaultValue(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalizeExecutorAppName(String executorAppName) {
        String normalized = trimToNull(executorAppName);
        if (normalized == null) {
            return null;
        }
        Set<String> appNames = new LinkedHashSet<>();
        for (String item : normalized.split(",")) {
            String appName = trimToNull(item);
            if (appName == null) {
                continue;
            }
            if (!appName.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("XXL-JOB 关注执行器只允许字母、数字、下划线、中划线和点号");
            }
            appNames.add(appName);
        }
        return appNames.isEmpty() ? null : String.join(",", appNames);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
