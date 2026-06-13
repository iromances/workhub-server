package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.OpsMonitorCheckResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobExecutorResponse;
import cn.aslight.workhub.model.ops.XxlJobFailedJobResponse;
import cn.aslight.workhub.model.ops.XxlJobLogPageResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class XxlJobMonitorCollector {

    private static final String LOG_STATUS_ALL = "ALL";
    private static final String LOG_STATUS_FAILED = "FAILED";

    private final XxlJobDatabaseQueryClient queryClient;

    public XxlJobMonitorCollector(XxlJobDatabaseQueryClient queryClient) {
        this.queryClient = queryClient;
    }

    public XxlJobDashboardResponse collectSummary(OpsMonitorEntity monitor) {
        String databaseName = quoteDatabaseName(monitor.getXxlJobDatabaseName());
        Map<String, Object> statistics = firstRow(queryClient.runRows(
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                summarySql(databaseName, monitor.getExecutorAppName())
        ));
        int triggerRunningCount = intValue(rowValue(statistics, "triggerRunningCount"));
        int triggerSuccessCount = intValue(rowValue(statistics, "triggerSuccessCount"));
        int triggerFailedCount = intValue(rowValue(statistics, "triggerFailedCount"));
        int triggerCount = triggerRunningCount + triggerSuccessCount + triggerFailedCount;
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
                intValue(rowValue(statistics, "jobCount")),
                intValue(rowValue(statistics, "enabledJobCount")),
                intValue(rowValue(statistics, "disabledJobCount")),
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
        return collect(monitor, LocalDate.now().minusMonths(1), LocalDate.now());
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
        XxlJobDashboardResponse summary = collectSummary(monitor);
        String databaseName = quoteDatabaseName(monitor.getXxlJobDatabaseName());
        String detailFilter = detailFilterSql(author, executorAppName);
        String statusFilter = logStatusFilterSql(logStatus);
        int failedJobCount = intValue(rowValue(firstRow(queryClient.runRows(
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                failedJobsCountSql(databaseName, normalizedStartDate, normalizedEndDate, monitor.getExecutorAppName(), detailFilter, statusFilter)
        )), "failedJobTotal"));
        List<XxlJobFailedJobResponse> failedJobs = queryClient.runRows(
                        monitor.getBusinessLineCode(),
                        monitor.getEnvironmentCode(),
                        failedJobsSql(databaseName, normalizedStartDate, normalizedEndDate, monitor.getExecutorAppName(), detailFilter, statusFilter, normalizedPageSize, offset)
                )
                .stream()
                .map(this::toFailedJob)
                .toList();
        boolean failedOnly = LOG_STATUS_FAILED.equals(normalizeLogStatus(logStatus));
        String status = failedOnly && failedJobCount > 0 ? "ERROR" : "UP";
        String message = failedOnly
                ? (failedJobCount > 0 ? "XXL-JOB 区间失败日志数：" + failedJobCount : "XXL-JOB 区间内无失败日志")
                : "XXL-JOB 区间日志数：" + failedJobCount;
        return new XxlJobDashboardResponse(
                summary.id(),
                summary.monitorKey(),
                summary.businessLineCode(),
                summary.environmentCode(),
                summary.name(),
                summary.xxlJobDatabaseName(),
                status,
                message,
                LocalDateTime.now(),
                summary.executorCount(),
                summary.jobCount(),
                summary.enabledJobCount(),
                summary.disabledJobCount(),
                summary.triggerCount(),
                summary.triggerRunningCount(),
                summary.triggerSuccessCount(),
                summary.triggerFailedCount(),
                summary.reportUpdatedAt(),
                failedJobCount,
                normalizedPage,
                normalizedPageSize,
                failedJobs
        );
    }

    public XxlJobLogPageResponse collectLogs(OpsMonitorEntity monitor,
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
        String detailFilter = detailFilterSql(author, executorAppName);
        String statusFilter = logStatusFilterSql(logStatus);
        int total = intValue(rowValue(firstRow(queryClient.runRows(
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                failedJobsCountSql(databaseName, normalizedStartDate, normalizedEndDate, monitor.getExecutorAppName(), detailFilter, statusFilter)
        )), "failedJobTotal"));
        List<XxlJobFailedJobResponse> logs = queryClient.runRows(
                        monitor.getBusinessLineCode(),
                        monitor.getEnvironmentCode(),
                        failedJobsSql(databaseName, normalizedStartDate, normalizedEndDate, monitor.getExecutorAppName(), detailFilter, statusFilter, normalizedPageSize, offset)
                )
                .stream()
                .map(this::toFailedJob)
                .toList();
        boolean failedOnly = LOG_STATUS_FAILED.equals(normalizeLogStatus(logStatus));
        String status = failedOnly && total > 0 ? "ERROR" : "UP";
        String message = failedOnly
                ? (total > 0 ? "XXL-JOB 区间失败日志数：" + total : "XXL-JOB 区间内无失败日志")
                : "XXL-JOB 区间日志数：" + total;
        return new XxlJobLogPageResponse(
                monitor.getId(),
                monitor.getMonitorKey(),
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                monitor.getName(),
                monitor.getXxlJobDatabaseName(),
                status,
                message,
                LocalDateTime.now(),
                total,
                normalizedPage,
                normalizedPageSize,
                logs
        );
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
        return queryClient.runRows(businessLineCode, environmentCode, executorsSql(databaseName))
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

    private String summarySql(String databaseName, String executorAppName) {
        String executorFilter = executorAppNameFilterSql(executorAppName);
        if (!executorFilter.isBlank()) {
            return focusedSummarySql(databaseName, executorFilter);
        }
        return """
                SELECT
                  (SELECT COUNT(1) FROM %1$s.`xxl_job_info`) AS jobCount,
                  (SELECT COUNT(1) FROM %1$s.`xxl_job_info` WHERE trigger_status = 1) AS enabledJobCount,
                  (SELECT COUNT(1) FROM %1$s.`xxl_job_info` WHERE trigger_status <> 1) AS disabledJobCount,
                  (SELECT GROUP_CONCAT(address_list SEPARATOR ',') FROM %1$s.`xxl_job_group` WHERE address_list IS NOT NULL AND address_list <> '') AS executorRegistryList,
                  (SELECT COALESCE(SUM(running_count), 0) FROM %1$s.`xxl_job_log_report`) AS triggerRunningCount,
                  (SELECT COALESCE(SUM(suc_count), 0) FROM %1$s.`xxl_job_log_report`) AS triggerSuccessCount,
                  (SELECT COALESCE(SUM(fail_count), 0) FROM %1$s.`xxl_job_log_report`) AS triggerFailedCount,
                  (SELECT MAX(trigger_day) FROM %1$s.`xxl_job_log_report`) AS reportUpdatedAt
                """.formatted(databaseName);
    }

    private String focusedSummarySql(String databaseName, String executorFilter) {
        return """
                SELECT
                  (SELECT COUNT(1)
                   FROM %1$s.`xxl_job_info` i
                   JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                   WHERE 1 = 1 %2$s) AS jobCount,
                  (SELECT COUNT(1)
                   FROM %1$s.`xxl_job_info` i
                   JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                   WHERE i.trigger_status = 1 %2$s) AS enabledJobCount,
                  (SELECT COUNT(1)
                   FROM %1$s.`xxl_job_info` i
                   JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                   WHERE i.trigger_status <> 1 %2$s) AS disabledJobCount,
                  (SELECT GROUP_CONCAT(g.address_list SEPARATOR ',')
                   FROM %1$s.`xxl_job_group` g
                   WHERE g.address_list IS NOT NULL AND g.address_list <> '' %2$s) AS executorRegistryList,
                  (SELECT COUNT(1)
                   FROM %1$s.`xxl_job_log` l
                   JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                   JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                   WHERE l.handle_code = 0 AND l.trigger_code IN (0, 200) %2$s) AS triggerRunningCount,
                  (SELECT COUNT(1)
                   FROM %1$s.`xxl_job_log` l
                   JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                   JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                   WHERE l.handle_code = 200 %2$s) AS triggerSuccessCount,
                  (SELECT COUNT(1)
                   FROM %1$s.`xxl_job_log` l
                   JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                   JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                   WHERE l.handle_code <> 200
                     AND NOT (l.trigger_code IN (0, 200) AND l.handle_code = 0) %2$s) AS triggerFailedCount,
                  (SELECT MAX(l.trigger_time)
                   FROM %1$s.`xxl_job_log` l
                   JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                   JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                   WHERE 1 = 1 %2$s) AS reportUpdatedAt
                """.formatted(databaseName, executorFilter);
    }

    private String executorsSql(String databaseName) {
        return """
                SELECT
                  app_name AS appName,
                  title AS title
                FROM %1$s.`xxl_job_group`
                ORDER BY app_name ASC
                """.formatted(databaseName);
    }

    private String failedJobsCountSql(String databaseName,
                                      LocalDate startDate,
                                      LocalDate endDate,
                                      String executorAppName,
                                      String detailFilter,
                                      String statusFilter) {
        String startDateTime = startDate + " 00:00:00";
        String endExclusiveDateTime = endDate.plusDays(1) + " 00:00:00";
        String executorFilter = executorAppNameFilterSql(executorAppName);
        return """
                SELECT COUNT(1) AS failedJobTotal
                FROM %1$s.`xxl_job_log` l
                JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                WHERE l.trigger_time >= '%2$s'
                  AND l.trigger_time < '%3$s'
                  %4$s
                  %5$s
                  %6$s
                """.formatted(databaseName, startDateTime, endExclusiveDateTime, executorFilter, detailFilter, statusFilter);
    }

    private String failedJobsSql(String databaseName,
                                 LocalDate startDate,
                                 LocalDate endDate,
                                 String executorAppName,
                                 String detailFilter,
                                 String statusFilter,
                                 int limit,
                                 int offset) {
        String startDateTime = startDate + " 00:00:00";
        String endExclusiveDateTime = endDate.plusDays(1) + " 00:00:00";
        String executorFilter = executorAppNameFilterSql(executorAppName);
        return """
                SELECT
                  g.app_name AS executorAppName,
                  i.id AS jobId,
                  i.job_desc AS jobDesc,
                  i.author AS author,
                  i.executor_handler AS executorHandler,
                  i.trigger_status AS triggerStatus,
                  l.id AS logId,
                  l.trigger_time AS triggerTime,
                  l.trigger_code AS triggerCode,
                  l.trigger_msg AS triggerMsg,
                  l.handle_time AS handleTime,
                  l.handle_code AS handleCode,
                  l.handle_msg AS handleMsg
                FROM %1$s.`xxl_job_log` l
                JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                WHERE l.trigger_time >= '%2$s'
                  AND l.trigger_time < '%3$s'
                  %4$s
                  %5$s
                  %6$s
                ORDER BY l.trigger_time DESC, l.id DESC
                LIMIT %7$d OFFSET %8$d
                """.formatted(databaseName, startDateTime, endExclusiveDateTime, executorFilter, detailFilter, statusFilter, limit, offset);
    }

    private String logStatusFilterSql(String logStatus) {
        String normalized = normalizeLogStatus(logStatus);
        if (LOG_STATUS_ALL.equals(normalized)) {
            return "";
        }
        return """
                AND l.handle_code <> 200
                  AND NOT (l.trigger_code IN (0, 200) AND l.handle_code = 0)""";
    }

    private String normalizeLogStatus(String logStatus) {
        String normalized = trimToNull(logStatus);
        if (normalized == null) {
            return LOG_STATUS_FAILED;
        }
        String upper = normalized.toUpperCase();
        if (LOG_STATUS_ALL.equals(upper) || LOG_STATUS_FAILED.equals(upper)) {
            return upper;
        }
        throw new IllegalArgumentException("XXL-JOB 日志状态只支持 ALL 或 FAILED");
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
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new IllegalArgumentException(message);
        }
        return date;
    }

    private XxlJobFailedJobResponse toFailedJob(Map<String, Object> row) {
        Integer triggerCode = intValue(rowValue(row, "triggerCode"));
        Integer handleCode = intValue(rowValue(row, "handleCode"));
        return new XxlJobFailedJobResponse(
                stringValue(rowValue(row, "executorAppName")),
                longValue(rowValue(row, "jobId")),
                stringValue(rowValue(row, "jobDesc")),
                stringValue(rowValue(row, "author")),
                stringValue(rowValue(row, "executorHandler")),
                intValue(rowValue(row, "triggerStatus")),
                failureType(triggerCode, handleCode),
                failureTypeName(triggerCode, handleCode),
                longValue(rowValue(row, "logId")),
                stringValue(rowValue(row, "triggerTime")),
                triggerCode,
                truncate(stringValue(rowValue(row, "triggerMsg")), 500),
                stringValue(rowValue(row, "handleTime")),
                handleCode,
                truncate(stringValue(rowValue(row, "handleMsg")), 500)
        );
    }

    private String failureType(Integer triggerCode) {
        return failureType(triggerCode, null);
    }

    private String failureTypeName(Integer triggerCode) {
        return failureTypeName(triggerCode, null);
    }

    private String failureType(Integer triggerCode, Integer handleCode) {
        if (Integer.valueOf(200).equals(handleCode)) {
            return "SUCCESS";
        }
        if (Integer.valueOf(0).equals(handleCode) && (Integer.valueOf(0).equals(triggerCode) || Integer.valueOf(200).equals(triggerCode))) {
            return "RUNNING";
        }
        return Integer.valueOf(200).equals(triggerCode) ? "EXECUTION_FAILED" : "SCHEDULE_FAILED";
    }

    private String failureTypeName(Integer triggerCode, Integer handleCode) {
        if (Integer.valueOf(200).equals(handleCode)) {
            return "执行成功";
        }
        if (Integer.valueOf(0).equals(handleCode) && (Integer.valueOf(0).equals(triggerCode) || Integer.valueOf(200).equals(triggerCode))) {
            return "执行中";
        }
        return Integer.valueOf(200).equals(triggerCode) ? "执行失败" : "调度失败";
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
        return null;
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
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = trimToNull(value == null ? null : String.valueOf(value));
        return text == null ? 0 : Integer.parseInt(text);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = trimToNull(value == null ? null : String.valueOf(value));
        return text == null ? null : Long.parseLong(text);
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
