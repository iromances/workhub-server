package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.OpsMonitorCheckResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobExecutorResponse;
import cn.aslight.workhub.model.ops.XxlJobFailedJobResponse;
import cn.aslight.workhub.model.ops.XxlJobLogPageResponse;
import cn.aslight.workhub.model.ops.XxlJobSilentJobResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class XxlJobMonitorCollector {

    private static final int DEFAULT_LOG_DAYS = 7;
    private static final String LOG_STATUS_ALL = "ALL";
    private static final String LOG_STATUS_ABNORMAL = "ABNORMAL";
    private static final String LOG_STATUS_FAILED = "FAILED";
    /** REQ-a58c7e21：日志筛选与行级 failureType 五态口径对齐（SUCCESS/RUNNING/STUCK/EXECUTION_FAILED）。 */
    private static final String LOG_STATUS_SUCCESS = "SUCCESS";
    private static final String LOG_STATUS_RUNNING = "RUNNING";
    private static final String LOG_STATUS_STUCK = "STUCK";
    private static final String LOG_STATUS_EXECUTION_FAILED = "EXECUTION_FAILED";
    private static final java.util.Set<String> ABNORMAL_FILTER_STATUSES =
            java.util.Set.of(LOG_STATUS_ABNORMAL, LOG_STATUS_STUCK, LOG_STATUS_EXECUTION_FAILED);
    private static final int DEFAULT_STALE_THRESHOLD_MINUTES = 120;
    private static final int SILENT_GRACE_MINUTES = 10;

    private final FunctionCallService functionCallService;

    /** 生产构造：模板与执行统一走 FunctionCallService（页面维护优先 + 执行审计）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public XxlJobMonitorCollector(FunctionCallService functionCallService) {
        this.functionCallService = functionCallService;
    }

    /** 测试/无库环境构造：模板取 FuncCallSqlSeeds 种子，执行走传入的查询客户端（无审计）。 */
    public XxlJobMonitorCollector(XxlJobDatabaseQueryClient queryClient) {
        this(new FunctionCallService(new SeedsTemplateStore(), queryClient));
    }

    public XxlJobDashboardResponse collectSummary(OpsMonitorEntity monitor) {
        LocalDate endDate = LocalDate.now();
        return collectSummary(monitor, defaultStartDate(endDate), endDate);
    }

    public XxlJobDashboardResponse collectSummary(OpsMonitorEntity monitor, LocalDate startDate, LocalDate endDate) {
        LocalDate normalizedStartDate = requireDate(startDate, "开始日期不能为空");
        LocalDate normalizedEndDate = requireDate(endDate, "结束日期不能为空");
        if (normalizedStartDate.isAfter(normalizedEndDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        String databaseName = quoteDatabaseName(monitor.getXxlJobDatabaseName());
        Map<String, Object> statistics = firstRow(functionCallService.executeStatements(
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                List.of(summarySql(databaseName, monitor.getExecutorAppName(), normalizedStartDate, normalizedEndDate))
        ).getFirst());
        int triggerRunningCount = countValue(rowValue(statistics, "triggerRunningCount"));
        int triggerSuccessCount = countValue(rowValue(statistics, "triggerSuccessCount"));
        int triggerFailedCount = countValue(rowValue(statistics, "triggerFailedCount"));
        int triggerCount = Math.addExact(Math.addExact(triggerRunningCount, triggerSuccessCount), triggerFailedCount);
        String status = triggerFailedCount > 0 ? "ERROR" : "UP";
        String message = triggerFailedCount > 0
                ? "XXL-JOB 报表失败调度次数：" + triggerFailedCount
                : "XXL-JOB 报表无失败调度";
        return new XxlJobDashboardResponse(
                monitor.getId(),
                monitor.getMonitorKey(),
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                monitor.getName(),
                monitor.getXxlJobDatabaseName(),
                status,
                message,
                LocalDateTime.now(),
                executorCount(rowValue(statistics, "executorRegistryList")),
                countValue(rowValue(statistics, "jobCount")),
                countValue(rowValue(statistics, "enabledJobCount")),
                countValue(rowValue(statistics, "disabledJobCount")),
                triggerCount,
                triggerRunningCount,
                triggerSuccessCount,
                triggerFailedCount,
                stringValue(rowValue(statistics, "reportUpdatedAt")),
                0,
                List.of()
        );
    }

    public XxlJobDashboardResponse collect(OpsMonitorEntity monitor) {
        LocalDate endDate = LocalDate.now();
        return collect(monitor, defaultStartDate(endDate), endDate);
    }

    public XxlJobDashboardResponse collect(OpsMonitorEntity monitor, LocalDate startDate, LocalDate endDate) {
        return collect(monitor, startDate, endDate, 1, 500);
    }

    public XxlJobDashboardResponse collect(OpsMonitorEntity monitor, LocalDate startDate, LocalDate endDate, int page, int pageSize) {
        return collect(monitor, startDate, endDate, page, pageSize, null, null);
    }

    public XxlJobDashboardResponse collect(OpsMonitorEntity monitor,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           int page,
                                           int pageSize,
                                           String author,
                                           String executorAppName) {
        return collect(monitor, startDate, endDate, page, pageSize, author, executorAppName, LOG_STATUS_FAILED);
    }

    public XxlJobDashboardResponse collect(OpsMonitorEntity monitor,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           int page,
                                           int pageSize,
                                           String author,
                                           String executorAppName,
                                           String logStatus) {
        LocalDate normalizedStartDate = requireDate(startDate, "开始日期不能为空");
        LocalDate normalizedEndDate = requireDate(endDate, "结束日期不能为空");
        if (normalizedStartDate.isAfter(normalizedEndDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        String databaseName = quoteDatabaseName(monitor.getXxlJobDatabaseName());
        int staleThresholdMinutes = normalizeStaleThresholdMinutes(monitor.getStaleThresholdMinutes());
        LocalDateTime staleCutoff = LocalDateTime.now().minusMinutes(staleThresholdMinutes);
        LocalDateTime silentGraceCutoff = LocalDateTime.now().minusMinutes(SILENT_GRACE_MINUTES);
        String detailFilter = detailFilterSql(author, executorAppName);
        String statusFilter = logStatusFilterSql(logStatus, staleCutoff);
        // 健康口径按监测库全量聚合（不拼执行器过滤），executor 名单只聚焦失败明细列表。
        List<List<Map<String, Object>>> batches = functionCallService.executeStatements(
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                List.of(
                        healthStatsSql(databaseName, normalizedStartDate, normalizedEndDate, staleCutoff, silentGraceCutoff),
                        failedJobsCountSql(databaseName, normalizedStartDate, normalizedEndDate, monitor.getExecutorAppName(), detailFilter, statusFilter),
                        failedJobsSql(databaseName, normalizedStartDate, normalizedEndDate, monitor.getExecutorAppName(), detailFilter, statusFilter, normalizedPageSize, offset),
                        silentJobsSql(databaseName, silentGraceCutoff)
                )
        );
        Map<String, Object> statistics = firstRow(batches.get(0));
        int successCount = countValue(rowValue(statistics, "successCount"));
        int runningCount = countValue(rowValue(statistics, "runningCount"));
        int stuckCount = countValue(rowValue(statistics, "stuckCount"));
        int executionFailedCount = countValue(rowValue(statistics, "executionFailedCount"));
        int silentJobCount = countValue(rowValue(statistics, "silentJobCount"));
        String reportUpdatedAt = timeStringValue(rowValue(statistics, "reportUpdatedAt"));
        int executorCount = executorCount(rowValue(statistics, "executorRegistryList"));
        int jobCount = countValue(rowValue(statistics, "jobCount"));
        int enabledJobCount = countValue(rowValue(statistics, "enabledJobCount"));
        int disabledJobCount = countValue(rowValue(statistics, "disabledJobCount"));
        int failedJobCount = countValue(rowValue(firstRow(batches.get(1)), "failedJobTotal"));
        List<XxlJobFailedJobResponse> failedJobs = batches.get(2)
                .stream()
                .map(row -> toFailedJob(row, staleCutoff))
                .toList();
        List<XxlJobSilentJobResponse> silentJobs = batches.get(3)
                .stream()
                .map(this::toSilentJob)
                .toList();
        int triggerRunningCount = runningCount;
        int triggerSuccessCount = successCount;
        int triggerFailedCount = Math.addExact(executionFailedCount, stuckCount);
        int triggerCount = Math.addExact(Math.addExact(triggerFailedCount, triggerRunningCount), triggerSuccessCount);
        boolean abnormal = triggerFailedCount > 0 || silentJobCount > 0;
        String status = abnormal ? "ERROR" : "UP";
        String message = abnormal
                ? abnormalMessage(executionFailedCount, stuckCount, silentJobCount)
                : "XXL-JOB 区间内无异常日志，无静默任务";
        return new XxlJobDashboardResponse(
                monitor.getId(),
                monitor.getMonitorKey(),
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                monitor.getName(),
                monitor.getXxlJobDatabaseName(),
                status,
                message,
                LocalDateTime.now(),
                executorCount,
                jobCount,
                enabledJobCount,
                disabledJobCount,
                triggerCount,
                triggerRunningCount,
                triggerSuccessCount,
                triggerFailedCount,
                executionFailedCount,
                stuckCount,
                silentJobCount,
                reportUpdatedAt,
                failedJobCount,
                normalizedPage,
                normalizedPageSize,
                failedJobs,
                silentJobs
        );
    }

    /** 白名单四态健康口径：异常只列非零项（执行失败含调度失败），状态判定见 REQ-3d8f2a71 design 第 1 节。 */
    private String abnormalMessage(int executionFailedCount, int stuckCount, int silentJobCount) {
        StringBuilder builder = new StringBuilder("XXL-JOB");
        if (executionFailedCount > 0) {
            builder.append("、执行失败 ").append(executionFailedCount);
        }
        if (stuckCount > 0) {
            builder.append("、卡死（回调丢失） ").append(stuckCount);
        }
        if (silentJobCount > 0) {
            builder.append("、静默任务 ").append(silentJobCount);
        }
        return builder.toString();
    }

    public XxlJobLogPageResponse collectLogs(OpsMonitorEntity monitor,
                                             LocalDate startDate, LocalDate endDate, int page, int pageSize,
                                             String author, String executorAppName, String logStatus) {
        LocalDate from = requireDate(startDate, "开始日期不能为空"), to = requireDate(endDate, "结束日期不能为空");
        if (from.isAfter(to)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        int normalizedPage = normalizePage(page), size = normalizePageSize(pageSize);
        int offset = Math.multiplyExact(normalizedPage - 1, size);
        List<String> normalizedStatuses = normalizeLogStatuses(logStatus);
        String normalizedStatus = String.join(",", normalizedStatuses);
        var definition = new cn.aslight.workhub.model.ops.XxlJobQueryEvidence.Definition(monitor.getId(), monitor.getMonitorType(),
                monitor.getMonitorKey(), monitor.getBusinessLineCode(), monitor.getEnvironmentCode(), monitor.getXxlJobDatabaseName(),
                trimToNull(monitor.getExecutorAppName()), Boolean.TRUE.equals(monitor.getEnabled()), from, to, trimToNull(author),
                trimToNull(executorAppName), normalizedStatus, normalizedPage, size);
        String database = quoteDatabaseName(definition.databaseName());
        LocalDateTime staleCutoff = LocalDateTime.now().minusMinutes(normalizeStaleThresholdMinutes(monitor.getStaleThresholdMinutes()));
        String detailFilter = detailFilterSql(definition.author(), definition.executorFilter());
        String statusFilter = logStatusFilterSql(normalizedStatuses, staleCutoff);
        String statisticsSql = logStatisticsSql(database, from, to, definition.configuredExecutors(), detailFilter, normalizedStatuses, staleCutoff).sql();
        String pageSql = failedJobsSql(database, from, to, definition.configuredExecutors(), detailFilter, statusFilter, size, offset).sql();
        // 一个 SELECT 内的统计与明细共享数据库读取视图，避免并发新增日志把旧计数配上新分页；组合语句审计编码为参与码连接。
        var statements = List.of(FunctionCallService.Statement.of("OPS_XXL_LOG_STATS+OPS_XXL_FAILED_PAGE",
                "SELECT stats.*, logs.* FROM (" + statisticsSql + ") stats LEFT JOIN (" + pageSql
                        + ") logs ON 1=1 ORDER BY logs.triggerTime DESC, logs.logId DESC"));
        var batch = functionCallService.executeVerified(definition.businessLineCode(), definition.environmentCode(), statements);
        if (batch == null || batch.rows() == null || batch.rows().size() != statements.size() || batch.targetHash() == null
                || !batch.targetHash().matches("[a-f0-9]{64}") || batch.startedAt() == null || batch.finishedAt() == null
                || batch.finishedAt().isBefore(batch.startedAt())) throw new IllegalArgumentException("缺少实际查询目标或完整批次回执");
        var rows = batch.rows().getFirst();
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("数据库查询缺少聚合回执");
        var statistics = rows.getFirst();
        int total = countValue(rowValue(statistics, "failedJobTotal"));
        long activityCount = countLong(rowValue(statistics, "activityCount"));
        LocalDateTime latest = timestamp(rowValue(statistics, "latestActivityAt"));
        if (total > activityCount || (activityCount == 0) != (latest == null)) throw new IllegalArgumentException("日志统计数量与实际活动时间不一致");
        for (var row : rows) {
            if (countValue(rowValue(row, "failedJobTotal")) != total || countLong(rowValue(row, "activityCount")) != activityCount
                    || !java.util.Objects.equals(timestamp(rowValue(row, "latestActivityAt")), latest))
                throw new IllegalArgumentException("同一查询的统计与分页回执不一致");
            if (rowValue(row, "logId") == null && rows.size() != 1) throw new IllegalArgumentException("空分页标记与实际记录混合");
        }
        var logs = rows.stream().filter(row -> rowValue(row, "logId") != null).map(row -> toFailedJob(row, staleCutoff)).toList();
        int expected = Math.min(size, Math.max(0, total - offset));
        if (logs.size() != expected || logs.stream().map(XxlJobFailedJobResponse::logId).distinct().count() != logs.size())
            throw new IllegalArgumentException("日志分页不完整或查询期间数据已变化，请重新查询");
        var json = new tools.jackson.databind.ObjectMapper();
        var evidence = new cn.aslight.workhub.model.ops.XxlJobQueryEvidence(1, java.util.UUID.randomUUID().toString(), definition,
                AccountingExecutionEvidenceService.hash(json.writeValueAsString(definition)), batch.targetHash(),
                statements.stream().map(FunctionCallService.Statement::sql).map(AccountingExecutionEvidenceService::hash).toList(), batch.startedAt(), batch.finishedAt(),
                "Asia/Shanghai", total, activityCount, latest, normalizedPage == 1 && total == logs.size());
        // REQ-a58c7e21：异常态筛选（ABNORMAL/STUCK/EXECUTION_FAILED）有结果按 ERROR；文案与行状态名对齐；REQ-4a3634e6 多选按「、」连接
        boolean abnormalFilter = normalizedStatuses.stream().anyMatch(ABNORMAL_FILTER_STATUSES::contains);
        String statusName = logStatusesName(normalizedStatuses);
        String message = isLogStatusAll(normalizedStatuses)
                ? "XXL-JOB 区间日志数：" + total
                : (total > 0 ? "XXL-JOB 区间" + statusName + "日志数：" + total : "XXL-JOB 区间内无" + statusName + "日志");
        return new XxlJobLogPageResponse(definition.monitorId(), definition.monitorKey(), definition.businessLineCode(), definition.environmentCode(),
                monitor.getName(), definition.databaseName(), abnormalFilter && total > 0 ? "ERROR" : "UP",
                message,
                LocalDateTime.ofInstant(batch.finishedAt(), java.time.ZoneId.systemDefault()), total, normalizedPage, size, logs, evidence);
    }

    /** 匹配总数与全状态活动时间来自同一条 SQL，不能拼接不同时点的零失败和最新活动。 */
    private FunctionCallService.Statement logStatisticsSql(String database, LocalDate from, LocalDate to,
                                                           String executors, String filter, List<String> statuses,
                                                           LocalDateTime staleCutoff) {
        String matching = isLogStatusAll(statuses) ? "1"
                : "CASE WHEN " + statusPredicate(statuses, staleCutoff) + " THEN 1 END";
        return FunctionCallService.Statement.of("OPS_XXL_LOG_STATS", functionCallService.render(
                "OPS_XXL_LOG_STATS", FunctionCallService.params(
                        "matching", matching,
                        "databaseName", database,
                        "startDate", from.toString(),
                        "endDateExclusive", to.plusDays(1).toString(),
                        "executorFilter", executorAppNameFilterSql(executors),
                        "detailFilter", filter)));
    }

    /** 全库健康统计：五态计数（窗口内日志）+ 静默任务数 + 任务/执行器统计，恰好返回一行。 */
    private FunctionCallService.Statement healthStatsSql(String databaseName,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         LocalDateTime staleCutoff,
                                                         LocalDateTime silentGraceCutoff) {
        return FunctionCallService.Statement.of("OPS_XXL_HEALTH_STATS", functionCallService.render(
                "OPS_XXL_HEALTH_STATS", FunctionCallService.params(
                        "silentGraceCutoff", sqlLiteral(silentGraceCutoff),
                        "databaseName", databaseName,
                        "staleCutoff", sqlLiteral(staleCutoff),
                        "startDate", startDate.toString(),
                        "endDateExclusive", endDate.plusDays(1).toString())));
    }

    private String sqlLiteral(LocalDateTime value) {
        return value.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 静默任务明细（全库视角）：启用中但调度停摆的任务，按停摆程度（next_time 最早落后）取前 20。不选 schedule_conf——老版本 xxl_job_info 无该列（运行期实证）。 */
    private FunctionCallService.Statement silentJobsSql(String databaseName, LocalDateTime silentGraceCutoff) {
        return FunctionCallService.Statement.of("OPS_XXL_SILENT_JOBS", functionCallService.render(
                "OPS_XXL_SILENT_JOBS", FunctionCallService.params(
                        "databaseName", databaseName,
                        "silentGraceCutoff", sqlLiteral(silentGraceCutoff))));
    }

    private XxlJobSilentJobResponse toSilentJob(Map<String, Object> row) {
        LocalDateTime nextTime = timestamp(rowValue(row, "triggerNextTime"));
        return new XxlJobSilentJobResponse(
                stringValue(rowValue(row, "executorAppName")),
                longValue(rowValue(row, "jobId")),
                stringValue(rowValue(row, "jobDesc")),
                stringValue(rowValue(row, "author")),
                stringValue(rowValue(row, "executorHandler")),
                timeStringValue(rowValue(row, "triggerNextTime")),
                nextTime == null ? null : java.time.Duration.between(nextTime, LocalDateTime.now()).toMinutes()
        );
    }

    private LocalDateTime timestamp(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime timestamp) return timestamp;
        try { return LocalDateTime.parse(value.toString().replace(' ', 'T')); }
        catch (java.time.DateTimeException ex) { throw new IllegalArgumentException("数据库活动时间格式无效", ex); }
    }

    public OpsMonitorCheckResponse check(OpsMonitorEntity monitor) {
        XxlJobDashboardResponse dashboard = collect(monitor);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("xxlJobDatabaseName", dashboard.xxlJobDatabaseName());
        detail.put("executorCount", dashboard.executorCount());
        detail.put("jobCount", dashboard.jobCount());
        detail.put("enabledJobCount", dashboard.enabledJobCount());
        detail.put("disabledJobCount", dashboard.disabledJobCount());
        detail.put("failedJobCount", dashboard.failedJobCount());
        detail.put("executionFailedCount", dashboard.executionFailedCount());
        detail.put("stuckCount", dashboard.stuckCount());
        detail.put("silentJobCount", dashboard.silentJobCount());
        detail.put("silentJobs", dashboard.silentJobs());
        detail.put("failedJobs", dashboard.failedJobs());
        return new OpsMonitorCheckResponse(
                monitor.getId(),
                monitor.getMonitorType(),
                monitor.getMonitorKey(),
                dashboard.status(),
                dashboard.message(),
                dashboard.checkedAt(),
                detail
        );
    }

    public List<XxlJobExecutorResponse> listExecutors(String businessLineCode, String environmentCode, String xxlJobDatabaseName) {
        String databaseName = quoteDatabaseName(xxlJobDatabaseName);
        return functionCallService.executeStatements(businessLineCode, environmentCode,
                        List.of(executorsSql(databaseName)))
                .getFirst()
                .stream()
                .map(row -> new XxlJobExecutorResponse(
                        stringValue(rowValue(row, "appName")),
                        stringValue(rowValue(row, "title"))
                ))
                .toList();
    }

    private String quoteDatabaseName(String databaseName) {
        String normalized = trimToNull(databaseName);
        if (normalized == null) {
            throw new IllegalArgumentException("XXL-JOB 数据库名不能为空");
        }
        if (!normalized.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("XXL-JOB 数据库名只允许字母、数字和下划线");
        }
        return "`" + normalized + "`";
    }

    /** REQ-9f3c2a84：SQL 主体统一走 func_call_sql 登记模板（场景编码 OPS-XXL-*），片段参数由白名单构建；
     * REQ-b4d7e6f2：构建结果带归属编码（Statement），执行与审计由 FunctionCallService 收口。 */
    private FunctionCallService.Statement summarySql(String databaseName,
                                                     String executorAppName,
                                                     LocalDate startDate,
                                                     LocalDate endDate) {
        String executorFilter = executorAppNameFilterSql(executorAppName);
        if (!executorFilter.isBlank()) {
            return focusedSummarySql(databaseName, executorFilter, startDate, endDate);
        }
        return FunctionCallService.Statement.of("OPS_XXL_SUMMARY", functionCallService.render("OPS_XXL_SUMMARY",
                FunctionCallService.params(
                        "databaseName", databaseName,
                        "startDate", startDate.toString(),
                        "endDateExclusive", endDate.plusDays(1).toString())));
    }

    private FunctionCallService.Statement focusedSummarySql(String databaseName,
                                                            String executorFilter,
                                                            LocalDate startDate,
                                                            LocalDate endDate) {
        return FunctionCallService.Statement.of("OPS_XXL_FOCUSED_SUMMARY", functionCallService.render(
                "OPS_XXL_FOCUSED_SUMMARY", FunctionCallService.params(
                        "databaseName", databaseName,
                        "executorFilter", executorFilter,
                        "startDateTime", startDate + " 00:00:00",
                        "endExclusiveDateTime", endDate.plusDays(1) + " 00:00:00")));
    }

    private LocalDate defaultStartDate(LocalDate endDate) {
        return endDate.minusDays(DEFAULT_LOG_DAYS - 1L);
    }

    private FunctionCallService.Statement executorsSql(String databaseName) {
        return FunctionCallService.Statement.of("OPS_XXL_EXECUTORS", functionCallService.render(
                "OPS_XXL_EXECUTORS", FunctionCallService.params("databaseName", databaseName)));
    }

    private FunctionCallService.Statement failedJobsCountSql(String databaseName,
                                                             LocalDate startDate,
                                                             LocalDate endDate,
                                                             String executorAppName,
                                                             String detailFilter,
                                                             String statusFilter) {
        return FunctionCallService.Statement.of("OPS_XXL_FAILED_COUNT", functionCallService.render(
                "OPS_XXL_FAILED_COUNT", FunctionCallService.params(
                        "databaseName", databaseName,
                        "startDateTime", startDate + " 00:00:00",
                        "endExclusiveDateTime", endDate.plusDays(1) + " 00:00:00",
                        "executorFilter", executorAppNameFilterSql(executorAppName),
                        "detailFilter", detailFilter,
                        "statusFilter", statusFilter)));
    }

    private FunctionCallService.Statement failedJobsSql(String databaseName,
                                                        LocalDate startDate,
                                                        LocalDate endDate,
                                                        String executorAppName,
                                                        String detailFilter,
                                                        String statusFilter,
                                                        int limit,
                                                        int offset) {
        return FunctionCallService.Statement.of("OPS_XXL_FAILED_PAGE", functionCallService.render(
                "OPS_XXL_FAILED_PAGE", FunctionCallService.params(
                        "databaseName", databaseName,
                        "startDateTime", startDate + " 00:00:00",
                        "endExclusiveDateTime", endDate.plusDays(1) + " 00:00:00",
                        "executorFilter", executorAppNameFilterSql(executorAppName),
                        "detailFilter", detailFilter,
                        "statusFilter", statusFilter,
                        "limit", String.valueOf(limit),
                        "offset", String.valueOf(offset))));
    }

    private String logStatusFilterSql(String logStatus, LocalDateTime staleCutoff) {
        return logStatusFilterSql(normalizeLogStatuses(logStatus), staleCutoff);
    }

    private String logStatusFilterSql(List<String> statuses, LocalDateTime staleCutoff) {
        if (isLogStatusAll(statuses)) {
            return "";
        }
        return "AND " + statusPredicate(statuses, staleCutoff);
    }

    /** 白名单五态异常谓词：调度失败、执行失败或超时卡死（见 REQ-3d8f2a71 design 第 1 节）。 */
    private String abnormalPredicate(LocalDateTime staleCutoff) {
        return """
                l.trigger_code IS NULL
                  OR l.trigger_code <> 200
                  OR l.handle_code NOT IN (0, 200)
                  OR ((l.handle_code = 0 OR l.handle_code IS NULL) AND l.trigger_time < '%1$s')"""
                .formatted(sqlLiteral(staleCutoff));
    }

    /** REQ-a58c7e21：按筛选状态产出与行级 failureType 一致的谓词片段（STUCK∪EXECUTION_FAILED=ABNORMAL）；REQ-4a3634e6 多选 OR 连接。 */
    private String statusPredicate(List<String> normalizedStatuses, LocalDateTime staleCutoff) {
        return normalizedStatuses.stream()
                .map(status -> singleStatusPredicate(status, staleCutoff))
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    private String singleStatusPredicate(String normalizedStatus, LocalDateTime staleCutoff) {
        String cutoff = sqlLiteral(staleCutoff);
        return switch (normalizedStatus) {
            case LOG_STATUS_ABNORMAL -> abnormalPredicate(staleCutoff);
            case LOG_STATUS_SUCCESS -> "l.trigger_code = 200 AND l.handle_code = 200";
            // trigger_time 为 NULL 视作执行中（与 failureType 的 null 归 RUNNING 一致）
            case LOG_STATUS_RUNNING -> "l.trigger_code = 200 AND (l.handle_code = 0 OR l.handle_code IS NULL)"
                    + " AND (l.trigger_time IS NULL OR l.trigger_time >= '" + cutoff + "')";
            case LOG_STATUS_STUCK -> "l.trigger_code = 200 AND (l.handle_code = 0 OR l.handle_code IS NULL)"
                    + " AND l.trigger_time < '" + cutoff + "'";
            case LOG_STATUS_EXECUTION_FAILED -> "l.trigger_code IS NULL OR l.trigger_code <> 200 OR l.handle_code NOT IN (0, 200)";
            default -> throw new IllegalArgumentException("不支持的日志状态谓词：" + normalizedStatus);
        };
    }

    /** 筛选状态中文名（文案与行状态列 failureTypeName 对齐）。 */
    private String logStatusName(String normalizedStatus) {
        return switch (normalizedStatus) {
            case LOG_STATUS_ABNORMAL -> "异常";
            case LOG_STATUS_SUCCESS -> "执行成功";
            case LOG_STATUS_RUNNING -> "执行中";
            case LOG_STATUS_STUCK -> "卡死（回调丢失）";
            case LOG_STATUS_EXECUTION_FAILED -> "执行失败";
            default -> "";
        };
    }

    /** REQ-4a3634e6：多选中文名按白名单序以「、」连接。 */
    private String logStatusesName(List<String> normalizedStatuses) {
        return normalizedStatuses.stream().map(this::logStatusName).collect(Collectors.joining("、"));
    }

    private boolean isLogStatusAll(List<String> statuses) {
        return statuses.size() == 1 && LOG_STATUS_ALL.equals(statuses.getFirst());
    }

    /** REQ-4a3634e6：逗号分隔多选归一——逐项白名单校验（FAILED 兼容归一 ABNORMAL），按白名单次序去重；含 ALL 归一为 [ALL]；空值沿用服务层默认 ABNORMAL。 */
    private List<String> normalizeLogStatuses(String logStatus) {
        String normalized = trimToNull(logStatus);
        if (normalized == null) {
            return List.of(LOG_STATUS_ABNORMAL);
        }
        Set<String> statuses = new LinkedHashSet<>();
        for (String item : normalized.split(",")) {
            String upper = trimToNull(item);
            if (upper == null) {
                continue;
            }
            upper = upper.toUpperCase(Locale.ROOT);
            if (LOG_STATUS_FAILED.equals(upper)) {
                upper = LOG_STATUS_ABNORMAL;
            }
            if (!LOG_STATUS_ALL.equals(upper) && !LOG_STATUS_ABNORMAL.equals(upper) && !LOG_STATUS_SUCCESS.equals(upper)
                    && !LOG_STATUS_RUNNING.equals(upper) && !LOG_STATUS_STUCK.equals(upper)
                    && !LOG_STATUS_EXECUTION_FAILED.equals(upper)) {
                throw new IllegalArgumentException(
                        "XXL-JOB 日志状态只支持 ALL、ABNORMAL、SUCCESS、RUNNING、STUCK 或 EXECUTION_FAILED，多选用逗号分隔");
            }
            statuses.add(upper);
        }
        if (statuses.contains(LOG_STATUS_ALL)) {
            return List.of(LOG_STATUS_ALL);
        }
        if (statuses.isEmpty()) {
            return List.of(LOG_STATUS_ABNORMAL);
        }
        List<String> whitelistOrder = List.of(LOG_STATUS_ABNORMAL, LOG_STATUS_SUCCESS, LOG_STATUS_RUNNING,
                LOG_STATUS_STUCK, LOG_STATUS_EXECUTION_FAILED);
        return whitelistOrder.stream().filter(statuses::contains).toList();
    }

    private int normalizeStaleThresholdMinutes(Integer staleThresholdMinutes) {
        if (staleThresholdMinutes == null) {
            return DEFAULT_STALE_THRESHOLD_MINUTES;
        }
        if (staleThresholdMinutes < 1) {
            throw new IllegalArgumentException("卡死阈值必须大于等于 1 分钟");
        }
        return staleThresholdMinutes;
    }

    private String detailFilterSql(String author, String executorAppName) {
        StringBuilder builder = new StringBuilder();
        String normalizedAuthor = trimToNull(author);
        if (normalizedAuthor != null) {
            builder.append("AND i.author LIKE '%")
                    .append(escapeSqlLikeLiteral(normalizedAuthor))
                    .append("%' ");
        }
        String normalizedExecutorAppName = trimToNull(executorAppName);
        if (normalizedExecutorAppName != null) {
            validateExecutorKeyword(normalizedExecutorAppName);
            builder.append("AND g.app_name LIKE '%")
                    .append(escapeSqlLikeLiteral(normalizedExecutorAppName))
                    .append("%' ");
        }
        return builder.toString();
    }

    private String escapeSqlLikeLiteral(String value) {
        return value.replace("'", "''");
    }

    private void validateExecutorKeyword(String executorAppName) {
        if (!executorAppName.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("XXL-JOB 执行器查询只允许字母、数字、下划线、中划线和点号");
        }
    }

    private int normalizePage(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于等于 1");
        }
        return page;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("每页条数必须大于等于 1");
        }
        return Math.min(pageSize, 200);
    }

    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        if (rows == null || rows.size() != 1 || rows.getFirst() == null) throw new IllegalArgumentException("数据库聚合回执必须完整且恰好一行");
        return rows.getFirst();
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new IllegalArgumentException(message);
        }
        return date;
    }

    private XxlJobFailedJobResponse toFailedJob(Map<String, Object> row, LocalDateTime staleCutoff) {
        Integer triggerCode = nullableIntValue(rowValue(row, "triggerCode"));
        Integer handleCode = nullableIntValue(rowValue(row, "handleCode"));
        LocalDateTime triggerTime = timestamp(rowValue(row, "triggerTime"));
        return new XxlJobFailedJobResponse(
                stringValue(rowValue(row, "executorAppName")),
                longValue(rowValue(row, "jobId")),
                stringValue(rowValue(row, "jobDesc")),
                stringValue(rowValue(row, "author")),
                stringValue(rowValue(row, "executorHandler")),
                intValue(rowValue(row, "triggerStatus")),
                failureType(triggerCode, handleCode, triggerTime, staleCutoff),
                failureTypeName(triggerCode, handleCode, triggerTime, staleCutoff),
                longValue(rowValue(row, "logId")),
                timeStringValue(rowValue(row, "triggerTime")),
                triggerCode,
                truncate(stringValue(rowValue(row, "triggerMsg")), 500),
                timeStringValue(rowValue(row, "handleTime")),
                handleCode,
                truncate(stringValue(rowValue(row, "handleMsg")), 500)
        );
    }

    /** 行值可能来自 JDBC（Timestamp）或 MCP 反序列化（LocalDateTime/带 T 字符串），统一输出 yyyy-MM-dd HH:mm:ss；空值输出空串，无法解析的按原样返回。 */
    private String timeStringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "";
        }
        try {
            return sqlLiteral(timestamp(value));
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            return stringValue(value);
        }
    }

    /** 白名单四态分类：trigger≠200 计为执行失败（含调度失败，2026-11-14 用户确认并入）；handle 只有 200 成功、0/NULL 未决（按时间分 RUNNING/STUCK）、其他执行失败。 */
    private String failureType(Integer triggerCode, Integer handleCode, LocalDateTime triggerTime, LocalDateTime staleCutoff) {
        if (!Integer.valueOf(200).equals(triggerCode)) {
            return "EXECUTION_FAILED";
        }
        if (Integer.valueOf(200).equals(handleCode)) {
            return "SUCCESS";
        }
        if (handleCode == null || Integer.valueOf(0).equals(handleCode)) {
            return triggerTime != null && triggerTime.isBefore(staleCutoff) ? "STUCK" : "RUNNING";
        }
        return "EXECUTION_FAILED";
    }

    private String failureTypeName(Integer triggerCode, Integer handleCode, LocalDateTime triggerTime, LocalDateTime staleCutoff) {
        return switch (failureType(triggerCode, handleCode, triggerTime, staleCutoff)) {
            case "SUCCESS" -> "执行成功";
            case "RUNNING" -> "执行中";
            case "STUCK" -> "卡死（回调丢失）";
            default -> "执行失败";
        };
    }

    private String executorAppNameFilterSql(String executorAppName) {
        List<String> appNames = splitExecutorAppNames(executorAppName);
        if (appNames.isEmpty()) {
            return "";
        }
        String values = appNames.stream()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(","));
        return "AND g.app_name IN (" + values + ")";
    }

    private List<String> splitExecutorAppNames(String executorAppName) {
        String normalized = trimToNull(executorAppName);
        if (normalized == null) {
            return List.of();
        }
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .peek(this::validateExecutorAppName)
                .distinct()
                .toList();
    }

    private void validateExecutorAppName(String executorAppName) {
        if (!executorAppName.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("XXL-JOB 执行器只允许字母、数字、下划线、中划线和点号");
        }
    }

    private Object rowValue(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("数据库回执缺少字段：" + key);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int executorCount(Object registryListValue) {
        String registryList = stringValue(registryListValue);
        if (registryList.isBlank()) {
            return 0;
        }
        Set<String> addresses = new LinkedHashSet<>();
        Arrays.stream(registryList.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .forEach(addresses::add);
        return addresses.size();
    }

    private int intValue(Object value) {
        try { return new java.math.BigDecimal(java.util.Objects.requireNonNull(value, "整数值缺失").toString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException | NullPointerException ex) { throw new IllegalArgumentException("数据库回执整数值缺失、无效或越界", ex); }
    }

    private Integer nullableIntValue(Object value) {
        return value == null ? null : intValue(value);
    }

    private int countValue(Object value) { return Math.toIntExact(countLong(value)); }
    private long countLong(Object value) {
        try {
            long result = new java.math.BigDecimal(java.util.Objects.requireNonNull(value, "计数缺失").toString()).longValueExact();
            if (result < 0) throw new IllegalArgumentException("数据库回执计数不能为负数");
            return result;
        } catch (ArithmeticException | NumberFormatException | NullPointerException ex) { throw new IllegalArgumentException("数据库回执计数缺失、无效或越界", ex); }
    }
    private Long longValue(Object value) {
        long result = countLong(value);
        if (result == 0) throw new IllegalArgumentException("数据库记录标识必须为正整数");
        return result;
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
