package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.MqDatabaseStatsResponse;
import cn.aslight.workhub.model.ops.MqDashboardResponse;
import cn.aslight.workhub.model.ops.MqMessagePageResponse;
import cn.aslight.workhub.model.ops.MqMessageResponse;
import cn.aslight.workhub.model.ops.MqQueryEvidence;
import cn.aslight.workhub.model.ops.OpsMonitorCheckResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MqMonitorCollector {

    static final int DEFAULT_LAG_THRESHOLD_MINUTES = 30;
    private static final int DEFAULT_LOG_DAYS = 7;
    /** 读取端 JDBC maxRows 上限为 100，分页与逐库取数深度必须留出统计行余量。 */
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_FETCH_DEPTH = 99;
    private static final String LOG_TYPE_ALL = "ALL";
    private static final String LOG_TYPE_SENDER = "SENDER";
    private static final String LOG_TYPE_RECEIVER = "RECEIVER";
    private static final DateTimeFormatter SQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Comparator<MqMessageResponse> MESSAGE_ORDER = Comparator
            .comparing((MqMessageResponse item) -> item.occurredAt()).reversed()
            .thenComparing(MqMessageResponse::logId, Comparator.reverseOrder())
            .thenComparing(MqMessageResponse::databaseName)
            .thenComparing(MqMessageResponse::logType);

    private final FunctionCallService functionCallService;

    /** 生产构造：模板与执行统一走 FunctionCallService（页面维护优先 + 执行审计）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public MqMonitorCollector(FunctionCallService functionCallService) {
        this.functionCallService = functionCallService;
    }

    /** 测试/无库环境构造：模板取 FuncCallSqlSeeds 种子，执行走传入的查询客户端（无审计）。 */
    public MqMonitorCollector(XxlJobDatabaseQueryClient queryClient) {
        this(new FunctionCallService(new SeedsTemplateStore(), queryClient));
    }

    /** 业务口径：发送积压判定基准 = 检测时刻减去积压阈值分钟数。 */
    public int lagThresholdMinutes(OpsMonitorEntity monitor) {
        Integer configured = monitor.getMqLagThreshold();
        if (configured == null) {
            return DEFAULT_LAG_THRESHOLD_MINUTES;
        }
        if (configured < 1) {
            throw new IllegalArgumentException("MQ 积压阈值必须大于等于 1 分钟");
        }
        return configured;
    }

    public List<String> discoverDatabases(String businessLineCode, String environmentCode) {
        return functionCallService.executeStatements(requireValue(businessLineCode, "业务线不能为空"),
                        requireValue(environmentCode, "环境不能为空"),
                        List.of(FunctionCallService.Statement.of("OPS_MQ_DISCOVER_DATABASES",
                                functionCallService.render("OPS_MQ_DISCOVER_DATABASES", FunctionCallService.params()))))
                .getFirst()
                .stream()
                .map(row -> requireDatabaseName(stringValue(rowValue(row, "databaseName"))))
                .distinct()
                .sorted()
                .toList();
    }

    public MqDashboardResponse collectSummary(OpsMonitorEntity monitor, LocalDate startDate, LocalDate endDate) {
        LocalDate[] window = requireWindow(startDate, endDate);
        List<String> databases = discoverDatabases(monitor.getBusinessLineCode(), monitor.getEnvironmentCode());
        if (databases.isEmpty()) {
            return emptyScopeDashboard(monitor);
        }
        List<String> exchanges = normalizedKeywords(monitor.getMqTopic(), "交换机");
        List<String> queues = normalizedKeywords(monitor.getMqConsumerGroup(), "队列");
        LocalDateTime stuckBefore = stuckBefore(monitor);
        return collectDatabasesStats(monitor, databases, window, exchanges, queues, stuckBefore, LOG_TYPE_ALL,
                LocalDateTime.now(), 0, 1, 0, List.of());
    }

    public MqDashboardResponse collect(OpsMonitorEntity monitor) {
        LocalDate endDate = LocalDate.now();
        return collect(monitor, endDate.minusDays(DEFAULT_LOG_DAYS - 1L), endDate, 1, MAX_PAGE_SIZE, LOG_TYPE_ALL, null, null);
    }

    public MqDashboardResponse collect(OpsMonitorEntity monitor,
                                       LocalDate startDate,
                                       LocalDate endDate,
                                       int page,
                                       int pageSize,
                                       String logType,
                                       String exchangeKeyword,
                                       String queueKeyword) {
        LocalDate[] window = requireWindow(startDate, endDate);
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedLogType = normalizeLogType(logType);
        List<String> exchanges = normalizedKeywords(exchangeKeyword, "交换机");
        List<String> queues = normalizedKeywords(queueKeyword, "队列");
        List<String> databases = discoverDatabases(monitor.getBusinessLineCode(), monitor.getEnvironmentCode());
        if (databases.isEmpty()) {
            throw new IllegalArgumentException("未发现包含 message_sender_log 的子应用库，请核对业务线与环境的数据库目标");
        }
        int depth = fetchDepth(normalizedPage, normalizedPageSize);
        CollectResult result = collectPerDatabase(monitor, databases, window, exchanges, queues,
                normalizedLogType, depth);
        List<MqMessageResponse> pageMessages = mergePage(result.messages(), result.total(), normalizedPage, normalizedPageSize);
        return collectDatabasesStats(monitor, databases, window, exchanges, queues, stuckBefore(monitor), LOG_TYPE_ALL,
                LocalDateTime.now(), result.total(), normalizedPage, normalizedPageSize, pageMessages);
    }

    public MqMessagePageResponse collectMessages(OpsMonitorEntity monitor,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 int page,
                                                 int pageSize,
                                                 String logType,
                                                 String exchangeKeyword,
                                                 String queueKeyword) {
        LocalDate[] window = requireWindow(startDate, endDate);
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedLogType = normalizeLogType(logType);
        List<String> exchanges = normalizedKeywords(exchangeKeyword, "交换机");
        List<String> queues = normalizedKeywords(queueKeyword, "队列");
        int lagThresholdMinutes = lagThresholdMinutes(monitor);
        List<String> databases = discoverDatabases(monitor.getBusinessLineCode(), monitor.getEnvironmentCode());
        if (databases.isEmpty()) {
            throw new IllegalArgumentException("未发现包含 message_sender_log 的子应用库，请核对业务线与环境的数据库目标");
        }
        int depth = fetchDepth(normalizedPage, normalizedPageSize);
        Instant startedAt = Instant.now();
        CollectResult result = collectPerDatabaseVerified(monitor, databases, window, exchanges, queues,
                normalizedLogType, depth);
        Instant finishedAt = Instant.now();
        List<MqMessageResponse> pageMessages = mergePage(result.messages(), result.total(), normalizedPage, normalizedPageSize);
        var json = new tools.jackson.databind.ObjectMapper();
        var definition = new MqQueryEvidence.Definition(monitor.getId(), monitor.getMonitorType(), monitor.getMonitorKey(),
                monitor.getBusinessLineCode(), monitor.getEnvironmentCode(), databases,
                normalizedKeywords(monitor.getMqTopic(), "交换机").stream().collect(Collectors.joining(",")),
                normalizedKeywords(monitor.getMqConsumerGroup(), "队列").stream().collect(Collectors.joining(",")),
                lagThresholdMinutes, Boolean.TRUE.equals(monitor.getEnabled()), window[0], window[1], normalizedLogType,
                String.join(",", exchanges), String.join(",", queues), normalizedPage, normalizedPageSize);
        var evidence = new MqQueryEvidence(1, java.util.UUID.randomUUID().toString(), definition,
                AccountingExecutionEvidenceService.hash(json.writeValueAsString(definition)), result.targetHash(),
                result.queryHashes(), result.startedAt() == null ? startedAt : result.startedAt(),
                result.finishedAt() == null ? finishedAt : result.finishedAt(),
                "Asia/Shanghai", result.total(), result.activityCount(), result.latestActivityAt(),
                (long) (normalizedPage - 1) * normalizedPageSize + pageMessages.size() >= result.total());
        boolean hasFailure = result.total() > 0;
        return new MqMessagePageResponse(
                monitor.getId(),
                monitor.getMonitorKey(),
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                monitor.getName(),
                hasFailure ? "ERROR" : "UP",
                hasFailure ? "MQ 窗口失败或积压消息数：" + result.total() : "MQ 窗口内无失败或积压消息",
                LocalDateTime.ofInstant(result.finishedAt() == null ? finishedAt : result.finishedAt(), java.time.ZoneId.systemDefault()),
                (int) result.total(), normalizedPage, normalizedPageSize, pageMessages, evidence);
    }

    public OpsMonitorCheckResponse check(OpsMonitorEntity monitor) {
        LocalDate endDate = LocalDate.now();
        MqDashboardResponse dashboard = collectSummary(monitor, endDate.minusDays(DEFAULT_LOG_DAYS - 1L), endDate);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("databaseCount", dashboard.databaseCount());
        detail.put("discoveredDatabases", dashboard.databaseStats().stream().map(MqDatabaseStatsResponse::databaseName).toList());
        detail.put("degradedDatabases", dashboard.databaseStats().stream()
                .filter(item -> item.errorMessage() != null).map(MqDatabaseStatsResponse::databaseName).toList());
        detail.put("sendTotalCount", dashboard.sendTotalCount());
        detail.put("sendFailedCount", dashboard.sendFailedCount());
        detail.put("sendPendingCount", dashboard.sendPendingCount());
        detail.put("receiveTotalCount", dashboard.receiveTotalCount());
        detail.put("receiveFailedCount", dashboard.receiveFailedCount());
        detail.put("databaseStats", dashboard.databaseStats());
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

    // ---- 逐库采集：单个库失败只降级该库，不阻断其余库 ----

    private record CollectResult(long total,
                                 long activityCount,
                                 LocalDateTime latestActivityAt,
                                 String targetHash,
                                 List<String> queryHashes,
                                 Instant startedAt,
                                 Instant finishedAt,
                                 List<MqMessageResponse> messages,
                                 List<String> degradedDatabases) {
    }

    private CollectResult collectPerDatabase(OpsMonitorEntity monitor,
                                             List<String> databases,
                                             LocalDate[] window,
                                             List<String> exchanges,
                                             List<String> queues,
                                             String logType,
                                             int depth) {
        String stuckLiteral = SQL_DATETIME.format(stuckBefore(monitor));
        List<MqMessageResponse> messages = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        long total = 0;
        long activityCount = 0;
        LocalDateTime latest = null;
        for (String database : databases) {
            try {
                String statisticsSql = messageStatisticsSql(database, window, stuckLiteral, exchanges, queues, logType);
                String pageSql = messagePageSql(database, window, stuckLiteral, exchanges, queues, logType, depth);
                var statements = List.of(FunctionCallService.Statement.of(mqMessageCompositeCode(logType),
                        "SELECT stats.*, msgs.* FROM (" + statisticsSql + ") stats LEFT JOIN (" + pageSql
                                + ") msgs ON 1=1 ORDER BY msgs.occurredAt DESC, msgs.logId DESC"));
                var batch = functionCallService.executeStatements(monitor.getBusinessLineCode(), monitor.getEnvironmentCode(), statements);
                if (batch == null || batch.size() != statements.size()) throw new IllegalArgumentException("数据库查询回执缺少批次结果");
                DatabasePage databasePage = readDatabaseMessagePage(database, batch.getLast(), depth);
                messages.addAll(databasePage.messages());
                total += databasePage.total();
                activityCount += databasePage.activityCount();
                latest = mergeLatestTime(latest, databasePage.latestActivityAt());
            } catch (Exception ex) {
                degraded.add(database + "：" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        if (!degraded.isEmpty()) {
            throw new IllegalArgumentException("部分子应用库采集失败，无法返回完整分页：" + String.join("；", degraded));
        }
        return new CollectResult(total, activityCount, latest, null, List.of(), null, null, messages, degraded);
    }

    private CollectResult collectPerDatabaseVerified(OpsMonitorEntity monitor,
                                                     List<String> databases,
                                                     LocalDate[] window,
                                                     List<String> exchanges,
                                                     List<String> queues,
                                                     String logType,
                                                     int depth) {
        String stuckLiteral = SQL_DATETIME.format(stuckBefore(monitor));
        List<MqMessageResponse> messages = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        List<String> queryHashes = new ArrayList<>();
        long total = 0;
        long activityCount = 0;
        LocalDateTime latest = null;
        String targetHash = null;
        Instant startedAt = null;
        Instant finishedAt = null;
        for (String database : databases) {
            try {
                String statisticsSql = messageStatisticsSql(database, window, stuckLiteral, exchanges, queues, logType);
                String pageSql = messagePageSql(database, window, stuckLiteral, exchanges, queues, logType, depth);
                var statements = List.of(FunctionCallService.Statement.of(mqMessageCompositeCode(logType),
                        "SELECT stats.*, msgs.* FROM (" + statisticsSql + ") stats LEFT JOIN (" + pageSql
                                + ") msgs ON 1=1 ORDER BY msgs.occurredAt DESC, msgs.logId DESC"));
                var batch = functionCallService.executeVerified(monitor.getBusinessLineCode(), monitor.getEnvironmentCode(), statements);
                if (batch == null || batch.rows() == null || batch.rows().size() != statements.size() || batch.targetHash() == null
                        || !batch.targetHash().matches("[a-f0-9]{64}") || batch.startedAt() == null || batch.finishedAt() == null
                        || batch.finishedAt().isBefore(batch.startedAt())) throw new IllegalArgumentException("缺少实际查询目标或完整批次回执");
                targetHash = batch.targetHash();
                startedAt = startedAt == null ? batch.startedAt() : (batch.startedAt().isBefore(startedAt) ? batch.startedAt() : startedAt);
                finishedAt = finishedAt == null ? batch.finishedAt() : (batch.finishedAt().isAfter(finishedAt) ? batch.finishedAt() : finishedAt);
                statements.forEach(statement -> queryHashes.add(AccountingExecutionEvidenceService.hash(statement.sql())));
                DatabasePage databasePage = readDatabaseMessagePage(database, batch.rows().getFirst(), depth);
                messages.addAll(databasePage.messages());
                total += databasePage.total();
                activityCount += databasePage.activityCount();
                latest = mergeLatestTime(latest, databasePage.latestActivityAt());
            } catch (Exception ex) {
                degraded.add(database + "：" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        if (!degraded.isEmpty()) {
            throw new IllegalArgumentException("部分子应用库采集失败，无法返回完整分页与执行凭据：" + String.join("；", degraded));
        }
        if (targetHash == null) {
            throw new IllegalArgumentException("未发现包含 message_sender_log 的子应用库，请核对业务线与环境的数据库目标");
        }
        return new CollectResult(total, activityCount, latest, targetHash, queryHashes, startedAt, finishedAt, messages, degraded);
    }

    private record DatabasePage(long total, long activityCount, LocalDateTime latestActivityAt, List<MqMessageResponse> messages) { }

    /** 单库统计与明细同语句返回，避免并发新增消息把旧计数配上新分页；明细取前 depth 行供全局归并。 */
    private DatabasePage readDatabaseMessagePage(String database, List<Map<String, Object>> rows, int depth) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("数据库查询缺少聚合回执");
        var statistics = rows.getFirst();
        long total = countLong(rowValue(statistics, "matchingCount"));
        long activityCount = countLong(rowValue(statistics, "activityCount"));
        LocalDateTime latest = timestamp(rowValue(statistics, "latestActivityAt"));
        if (total > activityCount || (activityCount == 0) != (latest == null)) throw new IllegalArgumentException("消息统计数量与实际活动时间不一致");
        for (var row : rows) {
            if (countLong(rowValue(row, "matchingCount")) != total || countLong(rowValue(row, "activityCount")) != activityCount
                    || !Objects.equals(timestamp(rowValue(row, "latestActivityAt")), latest))
                throw new IllegalArgumentException("同一查询的统计与分页回执不一致");
            if (rowValue(row, "logId") == null && rows.size() != 1) throw new IllegalArgumentException("空分页标记与实际记录混合");
        }
        var messages = rows.stream().filter(row -> rowValue(row, "logId") != null).map(this::toMessage).toList();
        long expected = Math.min(depth, total);
        if (messages.size() != expected || messages.stream()
                .map(item -> item.databaseName() + ":" + item.logType() + ":" + item.logId())
                .distinct().count() != messages.size())
            throw new IllegalArgumentException("消息分页不完整或查询期间数据已变化，请重新查询");
        return new DatabasePage(total, activityCount, latest, messages);
    }

    /** 各库前缀按同一排序键归并后切页；深度受限（深分页）且有库未取尽时明确失败，不静默缺页。 */
    private List<MqMessageResponse> mergePage(List<MqMessageResponse> fetched,
                                              long total,
                                              int page,
                                              int pageSize) {
        List<MqMessageResponse> merged = fetched.stream().sorted(MESSAGE_ORDER).toList();
        int offset = Math.multiplyExact(page - 1, pageSize);
        int end = offset + pageSize;
        if (end > merged.size() && total > merged.size()) {
            throw new IllegalArgumentException("分页深度超出单库取数上限（" + MAX_FETCH_DEPTH + " 行），请缩小页码或每页条数");
        }
        if (offset >= merged.size()) {
            return List.of();
        }
        return merged.subList(offset, Math.min(end, merged.size()));
    }

    // ---- 统计汇总：逐库采集失败降级，不阻断整体 ----

    private MqDashboardResponse collectDatabasesStats(OpsMonitorEntity monitor,
                                                      List<String> databases,
                                                      LocalDate[] window,
                                                      List<String> exchanges,
                                                      List<String> queues,
                                                      LocalDateTime stuckBefore,
                                                      String logType,
                                                      LocalDateTime checkedAt,
                                                      long failedMessageCount,
                                                      int page,
                                                      int pageSize,
                                                      List<MqMessageResponse> messages) {
        String stuckLiteral = SQL_DATETIME.format(stuckBefore);
        Map<String, MqDatabaseStatsResponse> statsByDatabase = new LinkedHashMap<>();
        for (String database : databases) {
            statsByDatabase.put(database, new MqDatabaseStatsResponse(database, 0, 0, 0, 0, 0, 0, 0, null, null));
        }
        for (String database : databases) {
            try {
                List<Map<String, Object>> statsRows = functionCallService.executeStatements(monitor.getBusinessLineCode(),
                                monitor.getEnvironmentCode(),
                                List.of(statsSql(database, window[0], window[1], stuckLiteral, exchanges, queues, logType)))
                        .getFirst();
                mergeDatabaseStats(statsByDatabase, database, statsRows);
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                MqDatabaseStatsResponse existing = statsByDatabase.get(database);
                statsByDatabase.put(database, new MqDatabaseStatsResponse(database, 0, 0, 0, 0, 0, 0, 0, null,
                        "采集失败：" + truncate(message, 200)));
            }
        }
        List<MqDatabaseStatsResponse> databaseStats = List.copyOf(statsByDatabase.values());
        long sendFailed = databaseStats.stream().mapToLong(MqDatabaseStatsResponse::sendFailedCount).sum();
        long sendPending = databaseStats.stream().mapToLong(MqDatabaseStatsResponse::sendPendingCount).sum();
        long receiveFailed = databaseStats.stream().mapToLong(MqDatabaseStatsResponse::receiveFailedCount).sum();
        List<String> degraded = databaseStats.stream()
                .filter(item -> item.errorMessage() != null)
                .map(MqDatabaseStatsResponse::databaseName)
                .toList();
        String status;
        String message;
        if (sendFailed + receiveFailed > 0) {
            status = "ERROR";
            message = "MQ 窗口发送失败 " + sendFailed + " 条、接收失败 " + receiveFailed + " 条";
        } else if (!degraded.isEmpty()) {
            status = "WARN";
            message = degraded.size() + " 个子应用库采集失败：" + String.join("、", degraded);
        } else if (sendPending > 0) {
            status = "WARN";
            message = "MQ 窗口发送积压 " + sendPending + " 条";
        } else {
            status = "UP";
            message = "MQ 窗口无失败或积压消息";
        }
        if (!degraded.isEmpty() && !"ERROR".equals(status)) {
            message = message + "（其余库已正常采集）";
        }
        if (!degraded.isEmpty() && "ERROR".equals(status)) {
            message = message + "；另有 " + degraded.size() + " 个库采集失败：" + String.join("、", degraded);
        }
        return new MqDashboardResponse(
                monitor.getId(),
                monitor.getMonitorKey(),
                monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(),
                monitor.getName(),
                status,
                message,
                checkedAt,
                databases.size(),
                databaseStats.stream().mapToLong(MqDatabaseStatsResponse::sendTotalCount).sum(),
                databaseStats.stream().mapToLong(MqDatabaseStatsResponse::sendSuccessCount).sum(),
                sendFailed,
                sendPending,
                databaseStats.stream().mapToLong(MqDatabaseStatsResponse::receiveTotalCount).sum(),
                databaseStats.stream().mapToLong(MqDatabaseStatsResponse::receiveSuccessCount).sum(),
                receiveFailed,
                databaseStats,
                failedMessageCount,
                page,
                pageSize,
                messages,
                monitor.getMqTopic(),
                monitor.getMqConsumerGroup(),
                monitor.getMqLagThreshold(),
                monitor.getRemark()
        );
    }

    private void mergeDatabaseStats(Map<String, MqDatabaseStatsResponse> statsByDatabase,
                                    String database,
                                    List<Map<String, Object>> statsRows) {
        int expectedBranches = statsRows.size();
        if (expectedBranches == 0) {
            throw new IllegalArgumentException("统计回执缺少分支行");
        }
        for (var row : statsRows) {
            String rowDatabase = requireDatabaseName(stringValue(rowValue(row, "databaseName")));
            if (!rowDatabase.equals(database)) {
                throw new IllegalArgumentException("统计回执数据库与请求不一致：" + rowDatabase);
            }
            MqDatabaseStatsResponse existing = statsByDatabase.get(database);
            if (existing == null) throw new IllegalArgumentException("统计回执包含未发现的数据库：" + database);
            LocalDateTime latest = timestamp(rowValue(row, "latestActivityAt"));
            statsByDatabase.put(database, new MqDatabaseStatsResponse(
                    database,
                    existing.sendTotalCount() + countLong(rowValue(row, "sendTotalCount")),
                    existing.sendSuccessCount() + countLong(rowValue(row, "sendSuccessCount")),
                    existing.sendFailedCount() + countLong(rowValue(row, "sendFailedCount")),
                    existing.sendPendingCount() + countLong(rowValue(row, "sendPendingCount")),
                    existing.receiveTotalCount() + countLong(rowValue(row, "receiveTotalCount")),
                    existing.receiveSuccessCount() + countLong(rowValue(row, "receiveSuccessCount")),
                    existing.receiveFailedCount() + countLong(rowValue(row, "receiveFailedCount")),
                    mergeLatest(existing.latestActivityAt(), latest),
                    null));
        }
    }

    private MqDashboardResponse emptyScopeDashboard(OpsMonitorEntity monitor) {
        return new MqDashboardResponse(monitor.getId(), monitor.getMonitorKey(), monitor.getBusinessLineCode(),
                monitor.getEnvironmentCode(), monitor.getName(), "ERROR",
                "未发现包含 message_sender_log 的子应用库，请核对业务线与环境的数据库目标",
                LocalDateTime.now(), 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 1, 0, List.of(),
                monitor.getMqTopic(), monitor.getMqConsumerGroup(), monitor.getMqLagThreshold(), monitor.getRemark());
    }

    private MqMessageResponse toMessage(Map<String, Object> row) {
        String logType = normalizeLogType(stringValue(rowValue(row, "logType")));
        Integer status = intValueOrNull(rowValue(row, "status"));
        String failureType;
        String failureTypeName;
        if (LOG_TYPE_SENDER.equals(logType)) {
            if (Integer.valueOf(3).equals(status)) {
                failureType = "SEND_FAILED";
                failureTypeName = "发送失败";
            } else {
                failureType = "SEND_STUCK";
                failureTypeName = "发送积压";
            }
        } else if (Integer.valueOf(0).equals(status)) {
            failureType = "RECEIVE_ACK_FAILED";
            failureTypeName = "签收失败";
        } else {
            failureType = "RECEIVE_BUSINESS_FAILED";
            failureTypeName = "业务处理失败";
        }
        return new MqMessageResponse(
                stringValue(rowValue(row, "databaseName")),
                logType,
                countLong(rowValue(row, "logId")),
                stringValue(rowValue(row, "messageId")),
                stringValue(rowValue(row, "exchangeName")),
                stringValue(rowValue(row, "routingKey")),
                stringValue(rowValue(row, "queue")),
                status,
                failureType,
                failureTypeName,
                intValueOrNull(rowValue(row, "retryCount")),
                boolValue(rowValue(row, "autoRetry")),
                boolValue(rowValue(row, "dealBusinessSuccess")),
                formattedTime(rowValue(row, "occurredAt")),
                formattedTime(rowValue(row, "createTime")),
                truncate(stringValue(rowValue(row, "errorMsg")), 500)
        );
    }

    // ---- SQL 组装：REQ-9f3c2a84 起模板主体统一走 func_call_sql 登记模板（场景编码 OPS-MQ-*），
    // 分支按 logType 组合，UNION ALL / 聚合外壳保留在代码内；全部占位值仍先过白名单校验或转义。
    // REQ-b4d7e6f2：组合语句以参与码 + 连接作为 Statement 编码，交 FunctionCallService 统一执行与审计 ----

    private FunctionCallService.Statement statsSql(String database, LocalDate from, LocalDate to, String stuckLiteral,
                                                   List<String> exchanges, List<String> queues, String logType) {
        String windowFrom = from + " 00:00:00";
        String windowTo = to.plusDays(1) + " 00:00:00";
        String senderExchangeIn = exchangeInSql(exchanges, "s");
        String receiverExchangeIn = exchangeInSql(exchanges, "r");
        String receiverQueueIn = queueInSql(queues, "r");
        String quoted = quoteDatabaseName(database);
        List<String> branches = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        if (includeSender(logType)) {
            codes.add("OPS_MQ_STATS_SENDER");
            branches.add(functionCallService.render("OPS_MQ_STATS_SENDER", FunctionCallService.params(
                    "databaseLabel", database,
                    "databaseName", quoted,
                    "windowFrom", windowFrom,
                    "windowTo", windowTo,
                    "stuckLiteral", stuckLiteral,
                    "exchangeFilter", senderExchangeIn)));
        }
        if (includeReceiver(logType)) {
            codes.add("OPS_MQ_STATS_RECEIVER");
            branches.add(functionCallService.render("OPS_MQ_STATS_RECEIVER", FunctionCallService.params(
                    "databaseLabel", database,
                    "windowFrom", windowFrom,
                    "windowTo", windowTo,
                    "exchangeFilter", receiverExchangeIn,
                    "queueFilter", receiverQueueIn,
                    "databaseName", quoted)));
        }
        return FunctionCallService.Statement.of(String.join("+", codes), String.join("\nUNION ALL\n", branches));
    }

    /** 组合语句（统计+分页外壳）的审计编码：按 logType 实际参与的分支模板码 + 连接。 */
    private String mqMessageCompositeCode(String logType) {
        List<String> codes = new ArrayList<>();
        if (includeSender(logType)) {
            codes.add("OPS_MQ_MESSAGE_STATS_SENDER");
            codes.add("OPS_MQ_MESSAGE_PAGE_SENDER");
        }
        if (includeReceiver(logType)) {
            codes.add("OPS_MQ_MESSAGE_STATS_RECEIVER");
            codes.add("OPS_MQ_MESSAGE_PAGE_RECEIVER");
        }
        return String.join("+", codes);
    }

    private String messageStatisticsSql(String database, LocalDate[] window, String stuckLiteral,
                                        List<String> exchanges, List<String> queues, String logType) {
        String windowFrom = window[0] + " 00:00:00";
        String windowTo = window[1].plusDays(1) + " 00:00:00";
        String senderExchangeIn = exchangeInSql(exchanges, "s");
        String receiverExchangeIn = exchangeInSql(exchanges, "r");
        String receiverQueueIn = queueInSql(queues, "r");
        String senderPredicate = "(s.status = 3 OR (s.status IN (1, 2) AND s.create_time < '" + stuckLiteral + "'))";
        String receiverPredicate = "(r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0)";
        String quoted = quoteDatabaseName(database);
        List<String> branches = new ArrayList<>();
        if (includeSender(logType)) {
            branches.add(functionCallService.render("OPS_MQ_MESSAGE_STATS_SENDER", FunctionCallService.params(
                    "databaseName", quoted,
                    "windowFrom", windowFrom,
                    "windowTo", windowTo,
                    "senderPredicate", senderPredicate,
                    "exchangeFilter", senderExchangeIn)));
        }
        if (includeReceiver(logType)) {
            branches.add(functionCallService.render("OPS_MQ_MESSAGE_STATS_RECEIVER", FunctionCallService.params(
                    "databaseName", quoted,
                    "windowFrom", windowFrom,
                    "windowTo", windowTo,
                    "receiverPredicate", receiverPredicate,
                    "exchangeFilter", receiverExchangeIn,
                    "queueFilter", receiverQueueIn)));
        }
        return "SELECT COALESCE(SUM(t.activityCount), 0) AS activityCount, "
                + "COALESCE(SUM(t.matchingCount), 0) AS matchingCount, MAX(t.latestActivityAt) AS latestActivityAt FROM ("
                + String.join("\nUNION ALL\n", branches) + ") t";
    }

    private String messagePageSql(String database, LocalDate[] window, String stuckLiteral,
                                  List<String> exchanges, List<String> queues, String logType, int depth) {
        String windowFrom = window[0] + " 00:00:00";
        String windowTo = window[1].plusDays(1) + " 00:00:00";
        String senderExchangeIn = exchangeInSql(exchanges, "s");
        String receiverExchangeIn = exchangeInSql(exchanges, "r");
        String receiverQueueIn = queueInSql(queues, "r");
        String senderPredicate = "(s.status = 3 OR (s.status IN (1, 2) AND s.create_time < '" + stuckLiteral + "'))";
        String receiverPredicate = "(r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0)";
        String quoted = quoteDatabaseName(database);
        List<String> branches = new ArrayList<>();
        if (includeSender(logType)) {
            branches.add(functionCallService.render("OPS_MQ_MESSAGE_PAGE_SENDER", FunctionCallService.params(
                    "databaseLabel", database,
                    "databaseName", quoted,
                    "windowFrom", windowFrom,
                    "windowTo", windowTo,
                    "senderPredicate", senderPredicate,
                    "exchangeFilter", senderExchangeIn)));
        }
        if (includeReceiver(logType)) {
            branches.add(functionCallService.render("OPS_MQ_MESSAGE_PAGE_RECEIVER", FunctionCallService.params(
                    "databaseLabel", database,
                    "windowFrom", windowFrom,
                    "windowTo", windowTo,
                    "receiverPredicate", receiverPredicate,
                    "exchangeFilter", receiverExchangeIn,
                    "queueFilter", receiverQueueIn,
                    "databaseName", quoted)));
        }
        return "SELECT msgs.* FROM (" + String.join("\nUNION ALL\n", branches)
                + ") msgs ORDER BY msgs.occurredAt DESC, msgs.logId DESC, msgs.databaseName, msgs.logType LIMIT " + depth;
    }

    private String exchangeInSql(List<String> exchanges, String alias) {
        return inListSql(exchanges, alias, "exchange");
    }

    private String queueInSql(List<String> queues, String alias) {
        return inListSql(queues, alias, "queue");
    }

    private String inListSql(List<String> values, String alias, String column) {
        if (values.isEmpty()) {
            return "";
        }
        String list = values.stream().map(value -> "'" + value + "'").collect(Collectors.joining(","));
        return " AND " + alias + "." + column + " IN (" + list + ")";
    }

    // ---- 参数与口径校验 ----

    private LocalDate[] requireWindow(LocalDate startDate, LocalDate endDate) {
        LocalDate from = requireDate(startDate, "开始日期不能为空");
        LocalDate to = requireDate(endDate, "结束日期不能为空");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return new LocalDate[]{from, to};
    }

    private LocalDateTime stuckBefore(OpsMonitorEntity monitor) {
        return LocalDateTime.now().minusMinutes(lagThresholdMinutes(monitor));
    }

    private boolean includeSender(String logType) {
        return !LOG_TYPE_RECEIVER.equals(logType);
    }

    private boolean includeReceiver(String logType) {
        return !LOG_TYPE_SENDER.equals(logType);
    }

    private String normalizeLogType(String logType) {
        String normalized = trimToNull(logType);
        if (normalized == null) {
            return LOG_TYPE_ALL;
        }
        String upper = normalized.toUpperCase();
        if (LOG_TYPE_ALL.equals(upper) || LOG_TYPE_SENDER.equals(upper) || LOG_TYPE_RECEIVER.equals(upper)) {
            return upper;
        }
        throw new IllegalArgumentException("MQ 日志类型只支持 ALL、SENDER 或 RECEIVER");
    }

    private List<String> normalizedKeywords(String keyword, String label) {
        String normalized = trimToNull(keyword);
        if (normalized == null) {
            return List.of();
        }
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .peek(item -> {
                    if (!item.matches("[A-Za-z0-9_.-]+")) {
                        throw new IllegalArgumentException("MQ " + label + "只允许字母、数字、下划线、中划线和点号");
                    }
                })
                .distinct()
                .toList();
    }

    private String requireDatabaseName(String databaseName) {
        String normalized = trimToNull(databaseName);
        if (normalized == null) {
            throw new IllegalArgumentException("MQ 子应用库名不能为空");
        }
        if (!normalized.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("MQ 子应用库名只允许字母、数字和下划线");
        }
        return normalized;
    }

    private String quoteDatabaseName(String databaseName) {
        return "`" + requireDatabaseName(databaseName) + "`";
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
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 单库取数深度 = 全局分页终点；超深分页受读取端行数上限约束，触发饱和校验时明确失败。 */
    private int fetchDepth(int page, int pageSize) {
        return Math.min(Math.multiplyExact(page - 1, pageSize) + pageSize, MAX_FETCH_DEPTH);
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new IllegalArgumentException(message);
        }
        return date;
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String mergeLatest(String current, LocalDateTime candidate) {
        LocalDateTime parsed = current == null ? null : LocalDateTime.parse(current.replace(' ', 'T'));
        if (parsed == null || (candidate != null && candidate.isAfter(parsed))) {
            return candidate == null ? current : SQL_DATETIME.format(candidate);
        }
        return current;
    }

    private LocalDateTime mergeLatestTime(LocalDateTime current, LocalDateTime candidate) {
        if (current == null || (candidate != null && candidate.isAfter(current))) {
            return candidate;
        }
        return current;
    }

    // ---- 回执解析 ----

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

    private Integer intValueOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("数据库回执整数值无效或越界", ex);
        }
    }

    private Boolean boolValue(Object value) {
        if (value == null) {
            return null;
        }
        return new java.math.BigDecimal(value.toString()).compareTo(java.math.BigDecimal.ZERO) != 0;
    }

    private long countLong(Object value) {
        try {
            long result = new java.math.BigDecimal(java.util.Objects.requireNonNull(value, "计数缺失").toString()).longValueExact();
            if (result < 0) throw new IllegalArgumentException("数据库回执计数不能为负数");
            return result;
        } catch (ArithmeticException | NumberFormatException | NullPointerException ex) {
            throw new IllegalArgumentException("数据库回执计数缺失、无效或越界", ex);
        }
    }

    private LocalDateTime timestamp(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime timestamp) return timestamp;
        try {
            return LocalDateTime.parse(value.toString().replace(' ', 'T'));
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException("数据库活动时间格式无效", ex);
        }
    }

    private String formattedTime(Object value) {
        LocalDateTime time = timestamp(value);
        return time == null ? "" : SQL_DATETIME.format(time);
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
