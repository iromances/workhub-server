package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 禅道 REST API 客户端：提测时创建测试单（已在禅道开源版 21.7.8 真实验证）。
 *
 * <p>归属定位（2026-11-10 起以需求为锚点）：需求关联的禅道地址优先——迭代地址（execution-*-{ID}）
 * 直接取迭代详情的所属项目与产品；需求详情地址（story-*-{ID}）则按 需求→产品→项目→同名迭代 定位；
 * 版本与测试单建到该产品/项目/迭代下；未配置禅道地址时回退业务线映射配置。研发分支名始终作为禅道版本名。
 *
 * <p>链路：账密换 Token（POST /api.php/v1/tokens，开源版无固定密钥）→ 定位归属 → 按项目查版本
 * （GET /api.php/v1/projects/{id}/builds，版本名即研发分支名）匹配提测分支，匹配不到时
 * 自动创建版本（POST /api.php/v1/projects/{id}/builds，必填 execution/product/name/builder/date）→
 * 创建测试单（POST /api.php/v2/testtasks；该版本需 productID 与 product 双字段同传）。
 *
 * <p>配置：组 {@code zentao.base}（baseUrl、account、password[SECRET]、defaultMapping）；
 * 业务线映射 {@code zentao.mapping.{业务线编码}}，值为 {@code 产品ID:项目ID:执行ID}（如 59:385:896），
 * 未配置业务线回退 defaultMapping。
 */
@Component
public class ZentaoClient {

    public static final String CONFIG_GROUP = "zentao.base";
    public static final String CONFIG_KEY_BASE_URL = "baseUrl";
    public static final String CONFIG_KEY_ACCOUNT = "account";
    public static final String CONFIG_KEY_PASSWORD = "password";
    public static final String CONFIG_KEY_DEFAULT_MAPPING = "defaultMapping";
    public static final String CONFIG_GROUP_MAPPING = "zentao.mapping";

    private final SysConfigService sysConfigService;
    private final HttpClient http;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public ZentaoClient(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
        this.objectMapper = new tools.jackson.databind.ObjectMapper();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** 禅道需求详情地址中的需求 ID 形态：story-view-786.html / story-786.html / storyID=786。 */
    private static final Pattern STORY_ID_PATTERN = Pattern.compile("story(?:-[a-z]+)?-(\\d+)|storyID=(\\d+)");

    /** 禅道迭代地址中的迭代 ID 形态：execution-task-923.html / execution-view-923.html / execution-923.html。 */
    private static final Pattern EXECUTION_ID_PATTERN = Pattern.compile("execution(?:-[a-z]+)?-(\\d+)");

    /**
     * 创建禅道测试单：优先按需求关联的禅道地址定位产品/项目/同名迭代（研发分支名即禅道版本名），
     * 未配置地址时按业务线映射；用研发分支名匹配项目下已有版本（build），匹配不到时自动创建版本后再建测试单。
     *
     * @param branchName 研发分支名（与禅道版本名一致）
     * @param ownerUserName 测试负责人（禅道测试单 owner，须为禅道有效账号；空则回退配置账号）
     * @param zentaoStoryUrl 需求关联的禅道地址（需求详情页，如 {baseUrl}/story-view-786.html）
     */
    public ZentaoTestTask createTestTask(String title, String ownerUserName, String businessLineCode, String branchName,
                                         String zentaoStoryUrl) {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("需求未设置研发分支，无法匹配禅道提测版本");
        }
        // 配置问题先快速失败，再发起网络调用
        ZentaoMapping mapping = resolveMapping(businessLineCode);
        String token = exchangeToken(baseUrl, account, password);
        ZentaoMapping storyMapping = resolveStoryMapping(baseUrl, token, zentaoStoryUrl);
        if (storyMapping != null) {
            mapping = storyMapping;
        }
        String buildId = findBuildByBranch(baseUrl, token, mapping, branchName.trim());
        if (buildId == null) {
            buildId = createBuild(baseUrl, token, mapping, account, password, branchName.trim());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        // 21.7.8 缺陷绕法：产品校验取 productID，表单保存取 product，两者必须同传。
        payload.put("productID", mapping.productId());
        payload.put("product", mapping.productId());
        payload.put("build", buildId);
        payload.put("execution", mapping.executionId());
        payload.put("name", title);
        payload.put("owner", ownerUserName == null || ownerUserName.isBlank() ? account : ownerUserName);
        payload.put("status", "wait");
        payload.put("begin", LocalDate.now().toString());
        payload.put("end", LocalDate.now().plusDays(7).toString());
        String body = writeJson(payload);

        HttpResponse<String> response = postJson(baseUrl + "/api.php/v2/testtasks", token, body);
        if (response.statusCode() == 401) {
            token = exchangeToken(baseUrl, account, password);
            response = postJson(baseUrl + "/api.php/v2/testtasks", token, body);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道拒绝创建测试单（HTTP " + response.statusCode()
                    + "）：" + truncate(response.body()));
        }
        long taskId = readJsonLong(response.body(), "id");
        if (taskId <= 0) {
            String message = readJsonText(response.body(), "message");
            throw new IllegalStateException("禅道创建测试单失败：" + firstNonBlank(message, truncate(response.body())));
        }
        return new ZentaoTestTask(String.valueOf(taskId), trimSlash(baseUrl) + "/testtask-view-" + taskId + ".html");
    }

    /**
     * 只读探活：用已配置账密换 Token，验证禅道地址与凭据可用；不做其他调用。
     */
    public ProbeResult probe() {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        long start = System.currentTimeMillis();
        exchangeToken(baseUrl, account, password);
        return new ProbeResult(account, System.currentTimeMillis() - start);
    }

    // ================= 研发任务同步（workItem → 禅道任务，创建/更新/取消） =================

    /**
     * 定位研发任务归属：需求关联的禅道地址优先（迭代/需求页），未配置时回退业务线映射。
     */
    public ZentaoMapping resolveTaskMapping(String businessLineCode, String zentaoStoryUrl) {
        ZentaoMapping mapping = resolveMapping(businessLineCode);
        if (zentaoStoryUrl != null && !zentaoStoryUrl.isBlank()) {
            String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
            String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
            String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
            ZentaoMapping storyMapping = resolveStoryMapping(baseUrl, exchangeToken(baseUrl, account, password), zentaoStoryUrl);
            if (storyMapping != null) {
                mapping = storyMapping;
            }
        }
        return mapping;
    }

    /**
     * 解析任务关联的禅道需求：产品下按标题全等查找（分页），找不到自动创建。
     * 该实例把『相关研发需求』配置为任务必填，任务统一挂到与 WorkHub 需求同名的禅道需求上。
     */
    public long resolveOrCreateStory(ZentaoMapping mapping, String storyTitle) {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        String token = exchangeToken(baseUrl, account, password);
        long storyId = findStoryByTitle(baseUrl, token, mapping.productId(), storyTitle.trim());
        if (storyId > 0) {
            return storyId;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product", mapping.productId());
        payload.put("title", storyTitle.trim());
        payload.put("type", "story");
        payload.put("pri", 3);
        HttpResponse<String> response = postJson(
                trimSlash(baseUrl) + "/api.php/v1/products/" + mapping.productId() + "/stories", token, writeJson(payload));
        if (response.statusCode() == 401) {
            response = postJson(trimSlash(baseUrl) + "/api.php/v1/products/" + mapping.productId() + "/stories",
                    exchangeToken(baseUrl, account, password), writeJson(payload));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道拒绝创建需求「" + storyTitle + "」（HTTP " + response.statusCode()
                    + "）：" + truncate(response.body()));
        }
        long created = readJsonLong(response.body(), "id");
        if (created <= 0) {
            String message = readJsonText(response.body(), "message");
            throw new IllegalStateException("禅道创建需求「" + storyTitle + "」失败："
                    + firstNonBlank(message, truncate(response.body())));
        }
        return created;
    }

    /** 产品下按标题全等分页查找需求；找不到返回 0。 */
    private long findStoryByTitle(String baseUrl, String token, long productId, String storyTitle) {
        int page = 1;
        int total = Integer.MAX_VALUE;
        int seen = 0;
        while (seen < total && page <= 10) {
            HttpResponse<String> response = sendGet(trimSlash(baseUrl) + "/api.php/v1/products/" + productId
                    + "/stories?limit=100&page=" + page, token);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("禅道需求列表查询失败（HTTP " + response.statusCode() + "）");
            }
            try {
                tools.jackson.databind.JsonNode body = objectMapper.readTree(response.body());
                total = Math.max(body.path("total").asInt(seen), seen);
                tools.jackson.databind.JsonNode stories = body.path("stories");
                if (!stories.isArray() || stories.isEmpty()) {
                    return 0;
                }
                for (tools.jackson.databind.JsonNode story : stories) {
                    if (storyTitle.equals(story.path("title").asText(null))) {
                        long id = asLong(story.path("id"));
                        if (id > 0) {
                            return id;
                        }
                    }
                }
                seen += stories.size();
            } catch (Exception ex) {
                throw new IllegalStateException("禅道需求列表解析失败", ex);
            }
            page++;
        }
        return 0;
    }

    /**
     * 在迭代下创建研发任务（POST /api.php/v1/executions/{id}/tasks，type=dev，status=wait）。
     * 禅道 21.x 校验必填『预计开始』：estStarted 缺省取当天，deadline 有值才传。
     *
     * @param estimateHours 预估工时（小时）；null 不传
     * @param assignedTo    禅道账号；空则不指派
     * @param pri           优先级 1-4
     * @param estStarted    预计开始（yyyy-MM-dd；null 取当天）
     * @param deadline      截止日期（yyyy-MM-dd；null 不传）
     * @param storyId       关联的禅道需求 ID（该实例任务必填『相关研发需求』）
     */
    public ZentaoDevTask createDevTask(ZentaoMapping mapping, String title, String descHtml,
                                       String assignedTo, Double estimateHours, int pri,
                                       String estStarted, String deadline, long storyId) {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("execution", mapping.executionId());
        payload.put("type", "dev");
        payload.put("name", title);
        payload.put("pri", Math.max(1, Math.min(4, pri)));
        String startDate = estStarted == null || estStarted.isBlank() ? LocalDate.now().toString() : estStarted.trim();
        payload.put("estStarted", startDate);
        // 该禅道实例建任务必填『预计开始/截止日期』：截止缺省取预计开始 + 7 天（与提测测试单兜底一致）
        payload.put("deadline", deadline == null || deadline.isBlank()
                ? LocalDate.parse(startDate).plusDays(7).toString() : deadline.trim());
        double safeEstimate = estimateHours != null && estimateHours > 0 ? estimateHours : 0;
        payload.put("estimate", safeEstimate);
        // 剩余工时创建时=预估，已消耗=0；module 显式 0（默认模块），任务必填『相关研发需求』
        payload.put("left", safeEstimate);
        payload.put("consumed", 0);
        payload.put("module", 0);
        payload.put("story", storyId);
        payload.put("status", "wait");
        if (assignedTo != null && !assignedTo.isBlank()) {
            payload.put("assignedTo", assignedTo.trim());
        }
        if (descHtml != null && !descHtml.isBlank()) {
            payload.put("desc", descHtml);
        }
        String body = writeJson(payload);
        HttpResponse<String> response = postJson(
                trimSlash(baseUrl) + "/api.php/v1/executions/" + mapping.executionId() + "/tasks", null, body);
        if (response.statusCode() == 401) {
            response = postJson(
                    trimSlash(baseUrl) + "/api.php/v1/executions/" + mapping.executionId() + "/tasks",
                    exchangeToken(baseUrl, account, password), body);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道拒绝创建研发任务「" + title + "」（HTTP " + response.statusCode()
                    + "）：" + truncate(response.body()));
        }
        long taskId = readJsonLong(response.body(), "id");
        if (taskId <= 0) {
            String message = readJsonText(response.body(), "message");
            throw new IllegalStateException("禅道创建研发任务「" + title + "」失败：" + firstNonBlank(message, truncate(response.body())));
        }
        return new ZentaoDevTask(String.valueOf(taskId), taskUrl(taskId));
    }

    /**
     * 更新已同步的禅道任务（PUT /api.php/v1/tasks/{id}）：名称/描述/工时/负责人/优先级。
     */
    public void updateDevTask(long taskId, String title, String descHtml, String assignedTo,
                              Double estimateHours, int pri) {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", title);
        payload.put("pri", Math.max(1, Math.min(4, pri)));
        if (estimateHours != null && estimateHours > 0) {
            payload.put("estimate", estimateHours);
        }
        if (assignedTo != null && !assignedTo.isBlank()) {
            payload.put("assignedTo", assignedTo.trim());
        }
        if (descHtml != null && !descHtml.isBlank()) {
            payload.put("desc", descHtml);
        }
        String body = writeJson(payload);
        HttpResponse<String> response = putJson(trimSlash(baseUrl) + "/api.php/v1/tasks/" + taskId, null, body);
        if (response.statusCode() == 401) {
            response = putJson(trimSlash(baseUrl) + "/api.php/v1/tasks/" + taskId, exchangeToken(baseUrl, account, password), body);
        }
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("禅道任务（ID " + taskId + "）不存在，可能已被删除");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道拒绝更新研发任务（ID " + taskId + "，HTTP " + response.statusCode()
                    + "）：" + truncate(response.body()));
        }
    }

    /**
     * 取消已同步但 WorkHub 已删除的禅道任务（PUT status=canceled）。
     */
    public void cancelDevTask(long taskId) {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        String body = writeJson(Map.of("status", "canceled"));
        HttpResponse<String> response = putJson(trimSlash(baseUrl) + "/api.php/v1/tasks/" + taskId, null, body);
        if (response.statusCode() == 401) {
            response = putJson(trimSlash(baseUrl) + "/api.php/v1/tasks/" + taskId, exchangeToken(baseUrl, account, password), body);
        }
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("禅道任务（ID " + taskId + "）不存在，可能已被删除");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道拒绝取消研发任务（ID " + taskId + "，HTTP " + response.statusCode()
                    + "）：" + truncate(response.body()));
        }
    }

    /** 禅道任务页地址（不发起网络调用）。 */
    public String taskUrl(long taskId) {
        return trimSlash(requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）"))
                + "/task-view-" + taskId + ".html";
    }

    public record ProbeResult(String account, long elapsedMs) {
    }

    // ================= MCP 只读查询（REQ-0051c0a8，仅 GET，不改变禅道状态） =================

    /** 检索最多扫描的分页数（每页 100 条），防止全量翻页。 */
    private static final int MAX_SCAN_PAGES = 5;

    /**
     * 只读检索缺陷：优先走迭代/产品作用域端点，端点 404 时回退全局列表按字段过滤；
     * keyword 对标题/关键词/ID 做包含匹配；最多扫描 {@value MAX_SCAN_PAGES} 页，命中 limit 即止。
     *
     * @return 命中的缺陷节点列表（原始字段，由调用方裁剪与脱敏）
     */
    public List<tools.jackson.databind.JsonNode> searchBugs(Long productId, Long executionId, String keyword, int limit) {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        String token = exchangeToken(baseUrl, account, password);
        String scopedPath = executionId != null ? "/api.php/v1/executions/" + executionId + "/bugs"
                : productId != null ? "/api.php/v1/products/" + productId + "/bugs"
                : null;
        long scopeId = executionId != null ? executionId : productId != null ? productId : -1;
        String scopeField = executionId != null ? "execution" : "product";
        List<tools.jackson.databind.JsonNode> matched = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            String path = attempt == 0 ? scopedPath : "/api.php/v1/bugs";
            if (path == null) {
                continue;
            }
            boolean globalFallback = attempt == 1;
            matched.clear();
            // null=未探测；true=端点缺失需回退；false=端点可用，正常返回
            Boolean endpointMissing = null;
            for (int page = 1; page <= MAX_SCAN_PAGES && matched.size() < limit; page++) {
                HttpResponse<String> response = globalFallback
                        ? fetchGlobalBugPage(baseUrl, token, page)
                        : sendGet(trimSlash(baseUrl) + path + "?limit=100&page=" + page, token);
                if (response.statusCode() == 404) {
                    endpointMissing = true;
                    break;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (globalFallback) {
                        throw new IllegalStateException("禅道全局缺陷列表不可用（HTTP " + response.statusCode()
                                + "），请改用 productId 或 executionId 检索");
                    }
                    throw new IllegalStateException("禅道缺陷列表查询失败（HTTP " + response.statusCode() + "）");
                }
                tools.jackson.databind.JsonNode bugs;
                try {
                    tools.jackson.databind.JsonNode body = objectMapper.readTree(response.body());
                    bugs = body.isArray() ? body : body.path("bugs");
                } catch (Exception ex) {
                    throw new IllegalStateException("禅道缺陷列表解析失败", ex);
                }
                if (!bugs.isArray() || bugs.isEmpty()) {
                    endpointMissing = false;
                    break;
                }
                endpointMissing = false;
                for (tools.jackson.databind.JsonNode bug : bugs) {
                    if (globalFallback && scopeId > 0 && asLong(bug.path(scopeField)) != scopeId) {
                        continue;
                    }
                    if (keyword != null && !matchesKeyword(bug, keyword)) {
                        continue;
                    }
                    matched.add(bug);
                    if (matched.size() >= limit) {
                        break;
                    }
                }
            }
            if (!Boolean.TRUE.equals(endpointMissing)) {
                // 命中或扫尽即返回；是否可能仍有更多数据由调用方按 limit 判断
                return matched;
            }
        }
        throw new IllegalStateException("禅道缺陷列表端点不可用（含全局回退端点），请改用 productId 或 executionId 检索");
    }

    /**
     * 全局缺陷列表的分页形态探测：21.x 各实例对全局列表的参数校验不一致（部分拒绝 limit=100），
     * 依次尝试 limit=100 → limit=20 → 无参（无参仅首页有意义，翻页时跳过）。
     */
    private HttpResponse<String> fetchGlobalBugPage(String baseUrl, String token, int page) {
        HttpResponse<String> response = sendGet(trimSlash(baseUrl) + "/api.php/v1/bugs?limit=100&page=" + page, token);
        if (response.statusCode() != 400) {
            return response;
        }
        response = sendGet(trimSlash(baseUrl) + "/api.php/v1/bugs?limit=20&page=" + page, token);
        if (response.statusCode() != 400 || page > 1) {
            return response;
        }
        return sendGet(trimSlash(baseUrl) + "/api.php/v1/bugs", token);
    }

    /** 只读查询单个缺陷/需求/任务/迭代详情（GET，原始字段）。 */
    public tools.jackson.databind.JsonNode getBug(long bugId) {
        return fetchObject(bugId, "/api.php/v1/bugs/", "缺陷");
    }

    public tools.jackson.databind.JsonNode getStory(long storyId) {
        return fetchObject(storyId, "/api.php/v1/stories/", "需求");
    }

    public tools.jackson.databind.JsonNode getTask(long taskId) {
        return fetchObject(taskId, "/api.php/v1/tasks/", "任务");
    }

    public tools.jackson.databind.JsonNode getExecution(long executionId) {
        return fetchObject(executionId, "/api.php/v1/executions/", "迭代");
    }

    /** 禅道页面地址（不发起网络调用）：{baseUrl}/{pathPrefix}-view-{id}.html；未配置地址返回 null。 */
    public String viewUrl(String pathPrefix, long id) {
        String baseUrl = sysConfigService.findPlainValue(CONFIG_GROUP, CONFIG_KEY_BASE_URL);
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return trimSlash(baseUrl) + "/" + pathPrefix + "-view-" + id + ".html";
    }

    private tools.jackson.databind.JsonNode fetchObject(long id, String pathPrefix, String label) {
        String baseUrl = requireConfig(CONFIG_KEY_BASE_URL, "禅道地址未配置（zentao.base.baseUrl）");
        String account = requireConfig(CONFIG_KEY_ACCOUNT, "禅道账号未配置（zentao.base.account）");
        String password = requireConfig(CONFIG_KEY_PASSWORD, "禅道密码未配置（zentao.base.password）");
        String token = exchangeToken(baseUrl, account, password);
        HttpResponse<String> response = sendGet(trimSlash(baseUrl) + pathPrefix + id, token);
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("禅道" + label + "（ID " + id + "）不存在");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道" + label + "（ID " + id + "）详情查询失败（HTTP " + response.statusCode() + "）");
        }
        try {
            tools.jackson.databind.JsonNode node = objectMapper.readTree(response.body());
            if (node == null || !node.isObject()) {
                throw new IllegalStateException("禅道" + label + "（ID " + id + "）返回内容不是 JSON 对象");
            }
            return node;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("禅道" + label + "（ID " + id + "）详情解析失败", ex);
        }
    }

    /** keyword 对标题/关键词/ID 做大小写不敏感的包含匹配。 */
    private boolean matchesKeyword(tools.jackson.databind.JsonNode bug, String keyword) {
        String lower = keyword.toLowerCase();
        for (String field : List.of("title", "keywords", "id")) {
            tools.jackson.databind.JsonNode value = bug.path(field);
            if (value.isValueNode() && value.asText().toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按需求关联的禅道地址定位提测归属：迭代地址直接取迭代详情的所属项目与产品；
     * 需求详情地址则按 需求→产品→项目→同名迭代 定位。
     * 未配置地址返回 null（走业务线映射）；配置了地址但解析或定位失败明确报错，不悄悄回退建错位置。
     */
    private ZentaoMapping resolveStoryMapping(String baseUrl, String token, String zentaoStoryUrl) {
        if (zentaoStoryUrl == null || zentaoStoryUrl.isBlank()) {
            return null;
        }
        String url = zentaoStoryUrl.trim();
        java.util.regex.Matcher executionMatcher = EXECUTION_ID_PATTERN.matcher(url);
        if (executionMatcher.find()) {
            return resolveExecutionMapping(baseUrl, token, Long.parseLong(executionMatcher.group(1)));
        }
        java.util.regex.Matcher storyMatcher = STORY_ID_PATTERN.matcher(url);
        if (!storyMatcher.find()) {
            throw new IllegalArgumentException("需求关联的禅道地址未识别出需求或迭代ID，请改为需求详情页（story-view-{ID}.html）"
                    + "或迭代页（execution-task-{ID}.html）形式的地址：" + url);
        }
        long storyId = Long.parseLong(firstNonBlank(storyMatcher.group(1), storyMatcher.group(2)));
        HttpResponse<String> response = sendGet(trimSlash(baseUrl) + "/api.php/v1/stories/" + storyId, token);
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("需求关联的禅道需求（ID " + storyId + "）不存在，请核对禅道地址");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道需求详情查询失败（HTTP " + response.statusCode() + "）");
        }
        long productId = -1;
        String storyTitle = null;
        try {
            tools.jackson.databind.JsonNode story = objectMapper.readTree(response.body());
            productId = asLong(story.path("product"));
            storyTitle = story.path("title").isTextual() ? story.path("title").asText() : null;
        } catch (Exception ex) {
            throw new IllegalStateException("禅道需求详情解析失败", ex);
        }
        if (productId <= 0 || storyTitle == null || storyTitle.isBlank()) {
            throw new IllegalStateException("禅道需求（ID " + storyId + "）详情缺少产品或需求名称，无法定位提测归属");
        }
        return findMappingByStory(baseUrl, token, storyId, productId, storyTitle.trim());
    }

    /**
     * 按迭代详情直接定位：返回值携带所属项目（project）与关联产品（products），一步取齐三元组。
     */
    private ZentaoMapping resolveExecutionMapping(String baseUrl, String token, long executionId) {
        HttpResponse<String> response = sendGet(trimSlash(baseUrl) + "/api.php/v1/executions/" + executionId, token);
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("需求关联的禅道迭代（ID " + executionId + "）不存在，请核对禅道地址");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道迭代详情查询失败（HTTP " + response.statusCode() + "）");
        }
        long projectId = -1;
        long productId = -1;
        try {
            tools.jackson.databind.JsonNode execution = objectMapper.readTree(response.body());
            projectId = asLong(execution.path("project"));
            tools.jackson.databind.JsonNode products = execution.path("products");
            if (products.isArray()) {
                for (tools.jackson.databind.JsonNode product : products) {
                    long id = asLong(product.path("id"));
                    if (id > 0) {
                        productId = id;
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("禅道迭代详情解析失败", ex);
        }
        if (projectId <= 0 || productId <= 0) {
            throw new IllegalStateException("禅道迭代（ID " + executionId + "）详情缺少所属项目或产品，无法定位提测归属");
        }
        return new ZentaoMapping(productId, projectId, executionId);
    }

    /**
     * 在产品关联的项目下按需求名匹配同名迭代，返回 产品:项目:迭代 三元组。
     */
    private ZentaoMapping findMappingByStory(String baseUrl, String token, long storyId, long productId, String storyTitle) {
        HttpResponse<String> response = sendGet(trimSlash(baseUrl) + "/api.php/v1/products/" + productId + "/projects", token);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道产品（ID " + productId + "）项目列表查询失败（HTTP " + response.statusCode() + "）");
        }
        List<Long> projectIds = new ArrayList<>();
        try {
            tools.jackson.databind.JsonNode projects = objectMapper.readTree(response.body()).path("projects");
            if (projects.isArray()) {
                for (tools.jackson.databind.JsonNode project : projects) {
                    // 产品关联列表可能混入执行（type!=project），只认真正的项目
                    String type = project.path("type").asText("");
                    long id = asLong(project.path("id"));
                    if (id > 0 && (type.isEmpty() || "project".equals(type))) {
                        projectIds.add(id);
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("禅道产品项目列表解析失败", ex);
        }
        if (projectIds.isEmpty()) {
            throw new IllegalArgumentException("禅道需求（ID " + storyId + "）所属产品（ID " + productId
                    + "）下未关联项目，无法定位提测归属");
        }
        for (long projectId : projectIds) {
            long executionId = findExecutionByName(baseUrl, token, projectId, storyTitle);
            if (executionId > 0) {
                return new ZentaoMapping(productId, projectId, executionId);
            }
        }
        throw new IllegalArgumentException("禅道需求「" + storyTitle + "」（ID " + storyId + "）所属产品（ID " + productId
                + "）各项目下没有同名迭代，无法定位提测归属；请在禅道为该需求建立同名迭代，或清除需求关联的禅道地址改用 zentao.mapping 配置");
    }

    /**
     * 分页遍历项目下迭代，按名称全等匹配；找不到返回 0。
     */
    private long findExecutionByName(String baseUrl, String token, long projectId, String executionName) {
        int page = 1;
        int total = Integer.MAX_VALUE;
        int seen = 0;
        while (seen < total && page <= 10) {
            HttpResponse<String> response = sendGet(trimSlash(baseUrl) + "/api.php/v1/projects/" + projectId
                    + "/executions?limit=100&page=" + page, token);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("禅道项目（ID " + projectId + "）迭代列表查询失败（HTTP " + response.statusCode() + "）");
            }
            try {
                tools.jackson.databind.JsonNode body = objectMapper.readTree(response.body());
                total = Math.max(body.path("total").asInt(seen), seen);
                tools.jackson.databind.JsonNode executions = body.path("executions");
                if (!executions.isArray() || executions.isEmpty()) {
                    return 0;
                }
                for (tools.jackson.databind.JsonNode execution : executions) {
                    if (executionName.equals(execution.path("name").asText(null))) {
                        long id = asLong(execution.path("id"));
                        if (id > 0) {
                            return id;
                        }
                    }
                }
                seen += executions.size();
            } catch (Exception ex) {
                throw new IllegalStateException("禅道项目迭代列表解析失败", ex);
            }
            page++;
        }
        return 0;
    }

    /**
     * 在项目版本列表中按分支名匹配提测版本；禅道版本名与研发分支名一致，匹配不到返回 null（由调用方自动创建）。
     */
    private String findBuildByBranch(String baseUrl, String token, ZentaoMapping mapping, String branchName) {
        HttpResponse<String> response = sendGet(
                trimSlash(baseUrl) + "/api.php/v1/projects/" + mapping.projectId() + "/builds", token);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道版本列表查询失败（HTTP " + response.statusCode() + "）");
        }
        try {
            tools.jackson.databind.JsonNode builds = objectMapper.readTree(response.body()).path("builds");
            if (builds.isArray()) {
                for (tools.jackson.databind.JsonNode build : builds) {
                    if (branchName.equals(build.path("name").asText(null))) {
                        long id = asLong(build.path("id"));
                        if (id > 0) {
                            return String.valueOf(id);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("禅道版本列表解析失败", ex);
        }
        return null;
    }

    /**
     * 按分支名自动创建禅道版本（POST /api.php/v1/projects/{id}/builds，必填 execution/product/name/builder/date）；
     * builder 用配置账号保证是禅道合法用户，成功返回新版本 ID。
     */
    private String createBuild(String baseUrl, String token, ZentaoMapping mapping, String account, String password, String branchName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("execution", mapping.executionId());
        payload.put("product", mapping.productId());
        payload.put("name", branchName);
        payload.put("builder", account);
        payload.put("date", LocalDate.now().toString());
        String body = writeJson(payload);

        HttpResponse<String> response = postJson(
                trimSlash(baseUrl) + "/api.php/v1/projects/" + mapping.projectId() + "/builds", token, body);
        if (response.statusCode() == 401) {
            token = exchangeToken(baseUrl, account, password);
            response = postJson(
                    trimSlash(baseUrl) + "/api.php/v1/projects/" + mapping.projectId() + "/builds", token, body);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道自动创建版本（" + branchName + "）被拒绝（HTTP " + response.statusCode()
                    + "）：" + truncate(response.body()));
        }
        long buildId = readJsonLong(response.body(), "id");
        if (buildId <= 0) {
            String message = readJsonText(response.body(), "message");
            throw new IllegalStateException("禅道自动创建版本（" + branchName + "）失败："
                    + firstNonBlank(message, truncate(response.body())));
        }
        return String.valueOf(buildId);
    }

    /**
     * 用账号密码换取访问 Token；禅道 tokens 接口返回 {token, expiredDate}。
     */
    private String exchangeToken(String baseUrl, String account, String password) {
        String body = writeJson(Map.of("account", account, "password", password));
        HttpResponse<String> response = postJson(baseUrl + "/api.php/v1/tokens", null, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("禅道账号认证失败（HTTP " + response.statusCode()
                    + "），请核对 zentao.base.account/password");
        }
        String token = readJsonText(response.body(), "token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("禅道 Token 响应缺少 token 字段：" + truncate(response.body()));
        }
        return token;
    }

    private ZentaoMapping resolveMapping(String businessLineCode) {
        String raw = null;
        if (businessLineCode != null && !businessLineCode.isBlank()) {
            raw = trimToNull(sysConfigService.findPlainValue(CONFIG_GROUP_MAPPING, businessLineCode.trim()));
        }
        raw = raw != null ? raw : requireConfig(CONFIG_KEY_DEFAULT_MAPPING,
                "禅道映射未配置：请在 zentao.mapping.{业务线编码} 或 zentao.base.defaultMapping 配置 产品ID:项目ID:执行ID");
        String[] parts = raw.split(":");
        if (parts.length != 3 || !parts[0].matches("\\d+") || !parts[1].matches("\\d+") || !parts[2].matches("\\d+")) {
            throw new IllegalArgumentException("禅道映射格式不正确（应为 产品ID:项目ID:执行ID）：" + raw);
        }
        return new ZentaoMapping(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
    }

    private HttpResponse<String> postJson(String url, String token, String body) {
        return sendJson(url, token, body, "POST");
    }

    private HttpResponse<String> putJson(String url, String token, String body) {
        return sendJson(url, token, body, "PUT");
    }

    private HttpResponse<String> sendJson(String url, String token, String body, String method) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            if (token != null) {
                builder.header("Token", token);
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new IllegalStateException("禅道请求失败：" + ex.getMessage(), ex);
        }
    }

    private HttpResponse<String> sendGet(String url, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Token", token)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new IllegalStateException("禅道请求失败：" + ex.getMessage(), ex);
        }
    }

    private long readJsonLong(String body, String field) {
        try {
            tools.jackson.databind.JsonNode node = objectMapper.readTree(body);
            long value = asLong(node.path(field));
            if (value >= 0) {
                return value;
            }
            value = asLong(node.path("data").path(field));
            if (value >= 0) {
                return value;
            }
        } catch (Exception ignored) {
            // 交由调用方统一报错
        }
        return -1;
    }

    /**
     * 禅道 v1 数字列常以字符串返回（PHP PDO 特性，如建版本回执 {"id":"429"}），数字与数字字符串均接受。
     */
    private long asLong(tools.jackson.databind.JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            return text.matches("\\d+") ? Long.parseLong(text) : -1;
        }
        return -1;
    }

    private String readJsonText(String body, String field) {
        try {
            tools.jackson.databind.JsonNode node = objectMapper.readTree(body);
            if (node.path(field).isTextual()) {
                return node.path(field).asText();
            }
        } catch (Exception ignored) {
            // 交由调用方统一报错
        }
        return null;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("禅道请求构造失败", ex);
        }
    }

    private String requireConfig(String key, String message) {
        String value = sysConfigService.findPlainValue(CONFIG_GROUP, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String trimSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    /** 产品/项目/执行三元组映射。 */
    public record ZentaoMapping(long productId, long projectId, long executionId) {
    }

    /** 禅道测试单回执。 */
    public record ZentaoTestTask(String taskNo, String url) {
    }

    /** 禅道研发任务回执。 */
    public record ZentaoDevTask(String taskId, String url) {
    }
}
