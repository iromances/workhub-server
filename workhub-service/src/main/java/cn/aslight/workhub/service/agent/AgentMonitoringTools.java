package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.dao.ops.OpsMonitorMapper;
import cn.aslight.workhub.model.agent.AgentConfiguration;
import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.service.ops.*;
import cn.aslight.workhub.service.autonomy.AgentObservationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.function.BiFunction;

/** 复用 WorkHub 现有监测事实；Agent 自行决定调用次序和后续调查。 */
@Configuration(proxyBeanMethods = false)
public class AgentMonitoringTools {
    private final AgentAccessService access;
    private final AgentMapper agents;
    private final OpsMonitorService business;
    private final OpsMonitorMapper businessMapper;
    private final AccountingMonitorService accounting;
    private final AccountingMonitorMapper accountingConfigs;
    private final AccountingRunMapper accountingRuns;
    private final SystemAlertService alerts;
    private final BusinessLogSearchService logs;
    private final ObjectMapper json;
    private final AgentToolData data;
    private final AgentObservationService observations;
    private final AgentMonitorReadMapper history;
    private final AccountingExecutionEvidenceService executionEvidence;

    public AgentMonitoringTools(AgentAccessService access, AgentMapper agents, OpsMonitorService business,
                                OpsMonitorMapper businessMapper, AccountingMonitorService accounting,
                                AccountingMonitorMapper accountingConfigs, AccountingRunMapper accountingRuns,
                                SystemAlertService alerts, BusinessLogSearchService logs,
                                ObjectMapper json, AgentToolData data, AgentObservationService observations, AgentMonitorReadMapper history, AccountingExecutionEvidenceService executionEvidence) {
        this.access = access;
        this.agents = agents;
        this.business = business;
        this.businessMapper = businessMapper;
        this.accounting = accounting;
        this.accountingConfigs = accountingConfigs;
        this.accountingRuns = accountingRuns;
        this.alerts = alerts;
        this.logs = logs;
        this.json = json;
        this.data = data;
        this.observations = observations;
        this.history = history;
        this.executionEvidence = executionEvidence;
    }

    @Bean
    AgentTool agentScopesTool() {
        return tool("workhub.scopes", "列出本次任务当前仍有权巡检的业务线与环境。", Set.of("agent:view"),
                Map.of(), List.of(), (context, args) -> {
                    AgentConfiguration current = json.readValue(agents.findAgent(context.agentId()).configurationJson(), AgentConfiguration.class);
                    return context.specification().scopes().stream()
                            .filter(context.configuration().scopes()::contains).filter(current.scopes()::contains).toList();
                });
    }

    @Bean
    AgentTool agentBusinessMonitorTool() {
        return tool("monitor.business.status", "主动分页查询业务监测项及最近执行时间，可指定 monitorId。MQ 未接入、未执行与无配置分别标记。",
                Set.of("ops:monitor:view", "ops:monitor:manage"), statusProperties("monitorId"), scopeRequired(), (context, args) -> {
                    AgentScope scope = scope(context, args);
                    Long monitorId = args.optionalId("monitorId");
                    int page = args.bounded("page", 1, 1, 10000), size = args.bounded("pageSize", 10, 1, 20);
                    List<BusinessFact> facts = business.list(null, scope.businessLineCode(), scope.environmentCode(), null, false).stream()
                            .filter(item -> monitorId == null || monitorId.equals(item.id()))
                            .sorted(Comparator.comparing(OpsMonitorResponse::id))
                            .map(item -> new BusinessFact(item.id(), item.name(), item.monitorType(), item.enabled(),
                                    !"XXL_JOB".equals(item.monitorType()) ? "UNAVAILABLE" : state(item.enabled(), item.lastCheckedAt()),
                                    item.lastStatus(), item.lastMessage(), item.lastCheckedAt(), age(item.lastCheckedAt()), item.updatedAt()))
                            .toList();
                    return statusPage("业务监测", scope, facts, page, size, monitorId != null);
                });
    }

    @Bean
    AgentTool agentBusinessDetailTool() {
        return tool("monitor.business.detail", "读取已授权监测项逐次 XXL-JOB 失败日志和实际查询凭据、全状态最新活动时间；默认最近七天，可固定 fromDate/toDate（单次最多31天）和 page/pageSize 续读。旧数据或不完整分页不能作为复查通过证据。",
                Set.of("ops:monitor:view", "ops:monitor:manage"),
                Map.of("monitorId", integer(1, Long.MAX_VALUE), "page", integer(1, 10000), "pageSize", integer(1, 20),
                        "fromDate", string(), "toDate", string()), List.of("monitorId"), (context, args) -> {
                    long id = args.id("monitorId");
                    var window = dateWindow(args);
                    int page = args.bounded("page", 1, 1, 10000), size = args.bounded("pageSize", 10, 1, 20);
                    OpsMonitorEntity monitor = businessMapper.findById(id);
                    if (monitor == null) throw new IllegalArgumentException("监测项不存在");
                    AgentScope scope = new AgentScope(monitor.getBusinessLineCode(), monitor.getEnvironmentCode());
                    access.requireScope(context, scope);
                    var result = business.xxlJobLogsInScope(id, scope.businessLineCode(), scope.environmentCode(), window.from(), window.to(), page, size, null, null, "FAILED");
                    var actualScope = new AgentScope(result.businessLineCode(), result.environmentCode());
                    access.requireScope(context, actualScope);
                    var value = observation("XXL-JOB 失败记录", actualScope, result);
                    value.put("query", Map.of("fromDate", window.from().toString(), "toDate", window.to().toString(), "page", page, "pageSize", size));
                    value.put("coverage", result.executionEvidence() != null ? "QUERY_SUCCEEDED" : "ERROR".equals(result.status()) ? "QUERY_FAILED" : "UNVERIFIED_QUERY");
                    return value;
                });
    }

    @Bean
    AgentTool agentAccountingStatusTool() {
        return tool("monitor.accounting.status", "主动读取账务监测最近运行，包含失败、未运行和数据时间；不能把查询失败或未运行解释为零差异。",
                Set.of("ops:accounting:view", "ops:accounting:manage"), statusProperties("configId"), scopeRequired(), (context, args) -> {
                    AgentScope scope = scope(context, args);
                    Long configId = args.optionalId("configId");
                    int page = args.bounded("page", 1, 1, 10000), size = args.bounded("pageSize", 10, 1, 20);
                    List<AccountingFact> facts = accounting.dashboard(scope.businessLineCode()).monitors().stream()
                            .filter(item -> scope.environmentCode().equals(item.config().environmentCode()))
                            .filter(item -> configId == null || configId.equals(item.config().id()))
                            .sorted(Comparator.comparing(item -> item.config().id()))
                            .map(item -> new AccountingFact(item.config().id(), item.config().displayName(), item.config().systemName(),
                                    item.config().enabled(), accountingState(item.config().enabled(), item.latestRun()),
                                    item.latestRun() == null ? null : executionEvidence.describe(item.latestRun()), accountingAge(item.latestRun()), item.config().updatedAt()))
                            .toList();
                    return statusPage("账务监测", scope, facts, page, size, configId != null);
                });
    }

    @Bean
    AgentTool agentAccountingRunsTool() {
        var properties = new LinkedHashMap<>(scopeProperties());
        properties.put("configId", integer(1, Long.MAX_VALUE));
        properties.put("beforeRunId", integer(0, Long.MAX_VALUE));
        properties.put("pageSize", integer(1, 10));
        return tool("monitor.accounting.runs", "查询授权环境的历史账务运行，避免最新成功掩盖中间失败。按 ID 倒序，用 beforeRunId 续页；汇总不是规则差异证据。",
                Set.of("ops:accounting:view", "ops:accounting:manage"), properties, scopeRequired(), (context, args) -> {
                    var scope = scope(context, args);
                    Long configId = args.optionalId("configId");
                    long before = args.position("beforeRunId");
                    int size = args.bounded("pageSize", 5, 1, 10);
                    var rows = history.accountingRuns(scope.businessLineCode(), scope.environmentCode(), configId, before, size + 1);
                    boolean more = rows.size() > size;
                    var page = List.copyOf(rows.subList(0, Math.min(rows.size(), size)));
                    var described = page.stream().map(executionEvidence::describe).toList();
                    var value = observation("账务历史运行", scope, Map.of("runs", described, "hasMore", more,
                            "nextBeforeRunId", more ? page.getLast().id() : 0L, "pageSize", size,
                            "coverageMeaning", "仅表示当前历史页；没有记录不代表监测配置、采集或账务健康"));
                    boolean verified = !described.isEmpty() && described.stream().allMatch(AccountingExecutionEvidenceService::scopeVerified);
                    value.put("scopeBasis", verified ? "RUN_EXECUTION_SNAPSHOT" : described.stream().anyMatch(AccountingExecutionEvidenceService::scopeVerified) ? "MIXED_EXECUTION_EVIDENCE" : "CURRENT_MONITOR_CONFIGURATION");
                    value.put("historicalEnvironmentVerified", verified);
                    return value;
                });
    }

    @Bean
    AgentTool agentAccountingDetailTool() {
        return tool("monitor.accounting.detail", "分页读取账务运行的逐条规则，用 afterResultId 续读。WARN/ERROR 为差异候选，FAILED 为执行失败，SKIPPED 为未评估；未终结运行或单页不能证明全部通过。",
                Set.of("ops:accounting:view", "ops:accounting:manage"), Map.of("runId", integer(1, Long.MAX_VALUE),
                        "afterResultId", integer(0, Long.MAX_VALUE), "pageSize", integer(1, 10)), List.of("runId"), (context, args) -> {
                    long id = args.id("runId");
                    long after = args.position("afterResultId");
                    int size = args.bounded("pageSize", 5, 1, 10);
                    var run = accountingRuns.findById(id);
                    if (run == null) throw new IllegalArgumentException("账务运行不存在");
                    var saved = executionEvidence.read(run);
                    AgentScope scope;
                    if (saved != null) {
                        var config = saved.snapshot().configuration();
                        scope = new AgentScope(config.businessLineCode(), config.environmentCode());
                    } else {
                        var config = accountingConfigs.findById(run.configId());
                        if (config == null) throw new IllegalStateException("账务运行配置已不存在，需核实原始范围");
                        scope = new AgentScope(config.businessLineCode(), config.environmentCode());
                    }
                    access.requireScope(context, scope);
                    var rows = history.accountingResults(id, after, size + 1);
                    boolean more = rows.size() > size;
                    var page = List.copyOf(rows.subList(0, Math.min(rows.size(), size)));
                    var value = observation("账务规则结果", scope, Map.of("run", executionEvidence.describe(run), "results", page, "hasMore", more,
                            "nextAfterResultId", page.isEmpty() ? after : page.getLast().id(), "pageSize", size,
                            "runFinished", run.finishedAt() != null && !Set.of("PENDING", "RUNNING").contains(run.status())));
                    value.put("scopeBasis", saved == null ? "CURRENT_MONITOR_CONFIGURATION" : "RUN_EXECUTION_SNAPSHOT");
                    value.put("historicalEnvironmentVerified", saved != null);
                    if (saved != null) value.put("recordTimeZone", saved.snapshot().recordTimeZone());
                    return value;
                });
    }

    @Bean
    AgentTool agentSystemAlertsTool() {
        var properties = new LinkedHashMap<>(scopeProperties());
        properties.put("serviceName", string());
        properties.put("minutes", integer(1, 10080));
        properties.put("page", integer(1, 10000));
        properties.put("pageSize", integer(1, 30));
        properties.put("from", string()); properties.put("to", string());
        return tool("monitor.system.alerts", "查询系统异常与慢 SQL；可固定 from/to 时间范围（ISO时间，最多31天），分页复用返回范围。无记录不证明采集健康。",
                Set.of("ops:system-alert:view", "ops:system-alert:manage"), properties, scopeRequired(), (context, args) -> {
                    AgentScope scope = scope(context, args);
                    var window = timeWindow(args);
                    int page = args.bounded("page", 1, 1, 10000), size = args.bounded("pageSize", 10, 1, 30);
                    var value = observation("系统预警", scope, alerts.dashboard(scope.businessLineCode(), scope.environmentCode(),
                            args.optional("serviceName"), null, null, null,
                            window.from(), window.to(), page, size));
                    value.put("query", Map.of("from", window.from().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(),
                            "to", window.to().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(), "page", page, "pageSize", size));
                    return value;
                });
    }

    @Bean
    AgentTool agentBusinessLogsTool() {
        var properties = new LinkedHashMap<>(scopeProperties());
        for (String name : List.of("serviceName", "phrase", "traceId", "from", "to")) properties.put(name, string());
        return tool("workhub.logs", "在既有受控 ELK 范围实时检索日志及同一快照的指定服务活动。效果复查需明确服务、连续查询区间和新鲜活动；空错误列表本身不证明恢复。WorkHub自检同样选择其业务线和环境。",
                Set.of("ops:system-alert:view", "ops:system-alert:manage"), properties, scopeRequired(), (context, args) -> {
                    AgentScope scope = scope(context, args);
                    String service = args.optional("serviceName");
                    return observation("受控实时日志", scope, logs.search(new BusinessLogSearchRequest(scope.businessLineCode(),
                            scope.environmentCode(), service == null ? List.of() : List.of(service), List.of(), args.optional("from"),
                            args.optional("to"), args.optional("phrase"), args.optional("traceId"), null, 30)));
                });
    }

    private AgentScope scope(AgentToolContext context, AgentToolArguments args) {
        AgentScope scope = new AgentScope(args.required("businessLineCode"), args.required("environmentCode"));
        access.requireScope(context, scope);
        return scope;
    }

    private AgentTool tool(String code, String description, Set<String> permissions, Map<String, Object> properties,
                            List<String> required, BiFunction<AgentToolContext, AgentToolArguments, Object> action) {
        JsonNode schema = json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return description; }
            public JsonNode inputSchema() { return schema; }
            public Set<String> permissions() { return permissions; }
            public boolean readOnly() { return true; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                access.requireExecution(context, this);
                Object value = action.apply(context, new AgentToolArguments(arguments, properties.keySet()));
                return "workhub.scopes".equals(code) ? data.sanitize(value) : observations.capture(context, code, value);
            }
        };
    }

    private Map<String, Object> scopeProperties() {
        return Map.of("businessLineCode", string(), "environmentCode", string());
    }
    private List<String> scopeRequired() { return List.of("businessLineCode", "environmentCode"); }
    private Map<String, Object> string() { return Map.of("type", "string", "maxLength", 500); }
    private Map<String, Object> integer(long min, long max) { return Map.of("type", "integer", "minimum", min, "maximum", max); }
    private Map<String, Object> observation(String source, AgentScope scope, Object result) {
        return new LinkedHashMap<>(Map.of("source", source, "scope", scope, "observedAt", Instant.now().toString(),
                "recordTimeZone", ZoneId.systemDefault().getId(), "data", result,
                "coverage", result instanceof List<?> list && list.isEmpty() ? "NO_CONFIGURATION" : "QUERY_SUCCEEDED"));
    }

    private Map<String, Object> statusProperties(String id) {
        var properties = new LinkedHashMap<>(scopeProperties());
        properties.put(id, integer(1, Long.MAX_VALUE));
        properties.put("page", integer(1, 10000)); properties.put("pageSize", integer(1, 20));
        return properties;
    }

    private <T> Object statusPage(String source, AgentScope scope, List<T> rows, int page, int size, boolean filtered) {
        int from = Math.min((page - 1) * size, rows.size()), to = Math.min(from + size, rows.size());
        var value = observation(source, scope, rows.subList(from, to));
        value.put("coverage", rows.isEmpty() ? filtered ? "NO_MATCHING_CONFIGURATION" : "NO_CONFIGURATION" : "QUERY_SUCCEEDED");
        value.put("pagination", Map.of("page", page, "pageSize", size, "total", rows.size(), "hasMore", to < rows.size()));
        return value;
    }

    private DateWindow dateWindow(AgentToolArguments args) {
        String from = args.optional("fromDate"), to = args.optional("toDate");
        if ((from == null) != (to == null)) throw new IllegalArgumentException("fromDate/toDate 必须同时提供");
        LocalDate end = to == null ? LocalDate.now() : LocalDate.parse(to);
        LocalDate start = from == null ? end.minusDays(6) : LocalDate.parse(from);
        if (start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 30) throw new IllegalArgumentException("单次日志范围须为1至31天，请分段补扫");
        return new DateWindow(start, end);
    }

    private TimeWindow timeWindow(AgentToolArguments args) {
        String from = args.optional("from"), to = args.optional("to");
        if ((from == null) != (to == null) || from != null && args.has("minutes")) throw new IllegalArgumentException("from/to 必须同时提供，且不能与 minutes 混用");
        LocalDateTime end = to == null ? LocalDateTime.now() : localTime(to);
        LocalDateTime start = from == null ? end.minusMinutes(args.bounded("minutes", 1440, 1, 10080)) : localTime(from);
        if (start.isAfter(end) || Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0) throw new IllegalArgumentException("预警查询范围必须有序且不超过31天，请分段补扫");
        return new TimeWindow(start, end);
    }

    private LocalDateTime localTime(String text) {
        try { return LocalDateTime.ofInstant(OffsetDateTime.parse(text).toInstant(), ZoneId.systemDefault()); }
        catch (java.time.format.DateTimeParseException ex) { return LocalDateTime.parse(text); }
    }

    private record DateWindow(LocalDate from, LocalDate to) { }
    private record TimeWindow(LocalDateTime from, LocalDateTime to) { }
    private String state(Boolean enabled, LocalDateTime lastAt) {
        if (!Boolean.TRUE.equals(enabled)) return "DISABLED";
        if (lastAt == null) return "NEVER_RUN";
        return age(lastAt) < 0 ? "FUTURE_TIMESTAMP" : "HAS_RESULT";
    }
    private String accountingState(Boolean enabled, AccountingRunEntity run) {
        if (!Boolean.TRUE.equals(enabled)) return "DISABLED";
        if (run == null) return "NEVER_RUN";
        if (Set.of("PENDING", "RUNNING").contains(run.status())) return "IN_PROGRESS";
        if ("FAILED".equals(run.status())) return "EXECUTION_FAILED";
        if (run.ruleTotal() != null && run.ruleTotal() > 0 && run.ruleTotal().equals(run.skippedCount())) return "NOT_EVALUATED";
        if (run.finishedAt() == null) return "NEVER_RUN";
        return accountingAge(run) < 0 ? "FUTURE_TIMESTAMP" : "HAS_RESULT";
    }
    private Long accountingAge(AccountingRunEntity run) {
        if (run == null || run.finishedAt() == null) return null;
        var saved = executionEvidence.read(run);
        ZoneId zone = saved == null ? AccountingExecutionEvidenceService.RECORD_ZONE : ZoneId.of(saved.snapshot().recordTimeZone());
        return Duration.between(run.finishedAt().atZone(zone).toInstant(), Instant.now()).getSeconds();
    }
    private Long age(LocalDateTime value) {
        return value == null ? null : Duration.between(value.atZone(ZoneId.systemDefault()).toInstant(), Instant.now()).getSeconds();
    }

    record BusinessFact(Long id, String name, String type, Boolean enabled, String dataState,
                         String status, String message, LocalDateTime lastCheckedAt, Long dataAgeSeconds, LocalDateTime configurationUpdatedAt) { }
    record AccountingFact(Long configId, String name, String systemName, Boolean enabled, String dataState,
                           JsonNode latestRun, Long dataAgeSeconds, LocalDateTime configurationUpdatedAt) { }
}
