package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.security.SensitiveDataMasker;
import cn.aslight.workhub.model.ops.BusinessLogSearchRequest;
import cn.aslight.workhub.model.ops.BusinessLogSearchResponse;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertScopeResponse;
import cn.aslight.workhub.model.ops.SystemAlertWatchMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BusinessLogSearchService {

    private static final Set<String> ALLOWED_LEVELS = Set.of("DEBUG", "INFO", "WARN", "ERROR");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_RESPONSE_CHARACTERS = 200_000;
    private static final Pattern SECRET_VALUE_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|token|access[-_]?token|api[-_]?key|secret|private[-_]?key|authorization|密码|密钥|令牌)(\\s*[:=]\\s*)((?:bearer|basic)\\s+[^,;\\s&}]+|[^,;\\s&}]+)");
    private static final Pattern ACCOUNT_VALUE_PATTERN = Pattern.compile(
            "(?i)(bank[-_]?account|account[-_]?(?:no|number)|card[-_]?(?:no|number)|银行账号|银行卡号|账号)(\\s*[:=]\\s*)([0-9]{8,30})");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(?<!\\d)(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}(?!\\d)");

    private final SystemAlertScopeService scopeService;
    private final ElasticsearchSystemAlertClient elasticsearchClient;
    private final Clock clock;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final McpAuditLogger auditLogger;

    @Autowired
    public BusinessLogSearchService(SystemAlertScopeService scopeService,
                                    ElasticsearchSystemAlertClient elasticsearchClient) {
        this(scopeService, elasticsearchClient, Clock.systemUTC(), new SensitiveDataMasker(),
                McpAuditLogger.defaultLogger(new ObjectMapper()));
    }

    BusinessLogSearchService(SystemAlertScopeService scopeService,
                             ElasticsearchSystemAlertClient elasticsearchClient,
                             Clock clock,
                             SensitiveDataMasker sensitiveDataMasker,
                             McpAuditLogger auditLogger) {
        this.scopeService = scopeService;
        this.elasticsearchClient = elasticsearchClient;
        this.clock = clock;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.auditLogger = auditLogger;
    }

    public BusinessLogSearchResponse search(BusinessLogSearchRequest request) {
        String businessLineCode = requireText(request.businessLineCode(), "业务线不能为空");
        String environmentCode = requireText(request.environmentCode(), "环境不能为空");
        SystemAlertScopeResponse scope = resolveScope(businessLineCode, environmentCode);
        List<String> services = resolveServices(scope, request.serviceNames());
        List<String> levels = normalizeLevels(request.levels());
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("日志返回条数允许范围为1-" + MAX_LIMIT);
        }

        Instant now = clock.instant();
        Instant to = parseInstant(request.to(), now, "结束时间");
        Instant from = parseInstant(request.from(), to.minus(Duration.ofMinutes(15)), "开始时间");
        validateTimeRange(from, to, now, levels);
        String phrase = optionalText(request.phrase(), 500, "检索短语最多500个字符");
        String traceId = optionalText(request.traceId(), 256, "Trace ID最多256个字符");
        String requestId = optionalText(request.requestId(), 256, "请求ID最多256个字符");

        long startedAt = System.nanoTime();
        ElasticsearchSystemAlertClient.LogSearchResult result;
        try {
            result = elasticsearchClient.searchLogs(
                    String.join(",", scope.indexPatterns()), services, environmentCode, levels,
                    from, to, phrase, traceId, requestId, limit);
        } catch (RuntimeException ex) {
            auditLogger.record(Map.ofEntries(
                    Map.entry("tool", "search_business_logs"),
                    Map.entry("businessLineCode", businessLineCode),
                    Map.entry("environmentCode", environmentCode),
                    Map.entry("services", services),
                    Map.entry("levels", levels),
                    Map.entry("from", from.toString()),
                    Map.entry("to", to.toString()),
                    Map.entry("limit", limit),
                    Map.entry("result", "FAILED"),
                    Map.entry("failureType", ex.getClass().getSimpleName()),
                    Map.entry("durationMs", elapsedMillis(startedAt)),
                    Map.entry("hasPhrase", phrase != null),
                    Map.entry("hasTraceId", traceId != null),
                    Map.entry("hasRequestId", requestId != null)
            ));
            throw ex;
        }
        MaskResult masked = maskAndBound(result.items());
        boolean truncated = result.truncated() || masked.truncated();
        auditLogger.record(Map.ofEntries(
                Map.entry("tool", "search_business_logs"),
                Map.entry("businessLineCode", businessLineCode),
                Map.entry("environmentCode", environmentCode),
                Map.entry("services", services),
                Map.entry("levels", levels),
                Map.entry("from", from.toString()),
                Map.entry("to", to.toString()),
                Map.entry("limit", limit),
                Map.entry("returned", masked.items().size()),
                Map.entry("truncated", truncated),
                Map.entry("result", "SUCCESS"),
                Map.entry("durationMs", elapsedMillis(startedAt)),
                Map.entry("hasPhrase", phrase != null),
                Map.entry("hasTraceId", traceId != null),
                Map.entry("hasRequestId", requestId != null)
        ));
        return new BusinessLogSearchResponse(
                businessLineCode, environmentCode, scope.indexPatterns(), services, levels,
                from, to, limit, masked.items().size(), truncated, true, masked.items());
    }

    private SystemAlertScopeResponse resolveScope(String businessLineCode, String environmentCode) {
        List<SystemAlertScopeResponse> scopes = scopeService.list(businessLineCode, environmentCode, true);
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("当前业务线环境未配置启用的ELK日志范围："
                    + businessLineCode + "." + environmentCode);
        }
        if (scopes.size() > 1) {
            throw new IllegalArgumentException("当前业务线环境存在多个ELK日志范围，无法安全查询");
        }
        SystemAlertScopeResponse scope = scopes.getFirst();
        if (scope.indexPatterns() == null || scope.indexPatterns().isEmpty()) {
            throw new IllegalArgumentException("当前业务线环境未配置ELK索引范围");
        }
        return scope;
    }

    private List<String> resolveServices(SystemAlertScopeResponse scope, List<String> requested) {
        List<String> normalizedRequested = normalizeValues(requested);
        if (!SystemAlertWatchMode.SELECTED.name().equals(scope.watchMode())) {
            return normalizedRequested;
        }
        List<String> allowed = scope.services().stream()
                .filter(item -> Boolean.TRUE.equals(item.enabled()))
                .map(item -> item.serviceName().trim())
                .toList();
        if (normalizedRequested.isEmpty()) {
            return allowed;
        }
        List<String> denied = normalizedRequested.stream()
                .filter(value -> allowed.stream().noneMatch(item -> item.equalsIgnoreCase(value)))
                .toList();
        if (!denied.isEmpty()) {
            throw new IllegalArgumentException("请求的服务不在业务线ELK关注范围：" + String.join(",", denied));
        }
        return normalizedRequested;
    }

    private List<String> normalizeLevels(List<String> requested) {
        List<String> values = requested == null || requested.isEmpty()
                ? List.of("ERROR", "WARN") : requested;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String level = requireText(value, "日志级别不能为空").toUpperCase(Locale.ROOT);
            if (!ALLOWED_LEVELS.contains(level)) {
                throw new IllegalArgumentException("不支持的日志级别：" + value);
            }
            normalized.add(level);
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> normalized.add(requireText(value, "服务名不能为空")));
        return List.copyOf(normalized);
    }

    private void validateTimeRange(Instant from, Instant to, Instant now, List<String> levels) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
        if (to.isAfter(now.plus(Duration.ofMinutes(1)))) {
            throw new IllegalArgumentException("结束时间不能晚于当前时间");
        }
        Duration maxRange = levels.stream().anyMatch(level -> "INFO".equals(level) || "DEBUG".equals(level))
                ? Duration.ofHours(24) : Duration.ofDays(7);
        if (Duration.between(from, to).compareTo(maxRange) > 0) {
            throw new IllegalArgumentException("当前日志级别查询时间跨度最多" +
                    (maxRange.toHours() == 24 ? "24小时" : "7天"));
        }
    }

    private Instant parseInstant(String value, Instant fallback, String fieldName) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parse(value.trim());
            return Instant.from(parsed);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + "必须是带时区的ISO-8601时间", ex);
        }
    }

    private MaskResult maskAndBound(List<ElkSystemAlertLog> source) {
        List<ElkSystemAlertLog> items = new ArrayList<>();
        int characters = 0;
        for (ElkSystemAlertLog item : source) {
            ElkSystemAlertLog masked = new ElkSystemAlertLog(
                    item.sourceEventId(),
                    truncate(mask(item.serviceName()), 255),
                    truncate(mask(item.level()), 32),
                    truncate(mask(item.title()), 500),
                    truncate(mask(item.message()), 4_000),
                    truncate(mask(item.errorType()), 255),
                    truncate(mask(item.stackTrace()), 12_000),
                    truncate(mask(item.traceId()), 128),
                    truncate(mask(item.requestId()), 128),
                    item.occurredAt());
            int itemCharacters = textLength(masked.serviceName()) + textLength(masked.level())
                    + textLength(masked.title()) + textLength(masked.message())
                    + textLength(masked.errorType()) + textLength(masked.stackTrace())
                    + textLength(masked.traceId()) + textLength(masked.requestId());
            if (characters + itemCharacters > MAX_RESPONSE_CHARACTERS) {
                return new MaskResult(List.copyOf(items), true);
            }
            characters += itemCharacters;
            items.add(masked);
        }
        return new MaskResult(List.copyOf(items), false);
    }

    private String mask(String value) {
        if (value == null) {
            return null;
        }
        String masked = sensitiveDataMasker.maskText(value);
        masked = replaceValue(masked, SECRET_VALUE_PATTERN, "******");
        masked = replaceAccount(masked);
        Matcher matcher = IPV4_PATTERN.matcher(masked);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String ip = matcher.group();
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    ip.substring(0, ip.lastIndexOf('.') + 1) + "***"));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String replaceValue(String value, Pattern pattern, String replacementValue) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + replacementValue));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String replaceAccount(String value) {
        Matcher matcher = ACCOUNT_VALUE_PATTERN.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String account = matcher.group(3);
            String masked = "*".repeat(Math.min(12, account.length() - 4))
                    + account.substring(account.length() - 4);
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + masked));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String optionalText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private record MaskResult(List<ElkSystemAlertLog> items, boolean truncated) {
    }
}
