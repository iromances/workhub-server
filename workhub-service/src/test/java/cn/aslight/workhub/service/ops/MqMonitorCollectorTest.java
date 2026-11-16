package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.MqDashboardResponse;
import cn.aslight.workhub.model.ops.MqMessagePageResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqMonitorCollectorTest {

    @Test
    void discoverDatabases_shouldValidateAndSortSchemaNames() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(List.of(
                row("databaseName", "th_saps"),
                row("databaseName", "assets_saps"),
                row("databaseName", "th_saps"),
                row("databaseName", "bad-name")
        ));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        assertThrows(IllegalArgumentException.class,
                () -> collector.discoverDatabases("BL000001", "PROD"));

        queryClient.nextRows(List.of(
                row("databaseName", "th_saps"),
                row("databaseName", "assets_saps"),
                row("databaseName", "th_saps")
        ));
        List<String> databases = collector.discoverDatabases("BL000001", "PROD");

        assertEquals(List.of("assets_saps", "th_saps"), databases);
        String sql = queryClient.sqls.getFirst();
        assertTrue(sql.contains("information_schema.tables"));
        assertTrue(sql.contains("table_name = 'message_sender_log'"));
    }

    @Test
    void collectSummary_shouldAggregatePerDatabaseAndClassifyFailureAheadOfPending() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps", "th_saps"));
        queryClient.statsRows.put("assets_saps", List.of(
                senderStatsRow("assets_saps", 100, 4, 2, 94, "2026-06-02 09:00:00"),
                receiverStatsRow("assets_saps", 50, 1, "2026-06-02 09:05:00")));
        queryClient.statsRows.put("th_saps", List.of(
                senderStatsRow("th_saps", 10, 0, 0, 10, "2026-06-02 08:00:00"),
                receiverStatsRow("th_saps", 20, 0, "2026-06-02 08:30:00")));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        MqDashboardResponse dashboard = collector.collectSummary(
                monitor(), LocalDate.of(2026, 5, 27), LocalDate.of(2026, 6, 2));

        assertEquals("ERROR", dashboard.status());
        assertEquals("MQ 窗口发送失败 4 条、接收失败 1 条", dashboard.message());
        assertEquals(2, dashboard.databaseCount());
        assertEquals(110, dashboard.sendTotalCount());
        assertEquals(104, dashboard.sendSuccessCount());
        assertEquals(4, dashboard.sendFailedCount());
        assertEquals(2, dashboard.sendPendingCount());
        assertEquals(70, dashboard.receiveTotalCount());
        assertEquals(69, dashboard.receiveSuccessCount());
        assertEquals(1, dashboard.receiveFailedCount());
        assertEquals("2026-06-02 09:05:00", dashboard.databaseStats().getFirst().latestActivityAt());
        assertEquals("2026-06-02 08:30:00", dashboard.databaseStats().getLast().latestActivityAt());

        String statsSql = queryClient.sqls.getLast();
        assertTrue(statsSql.contains("`th_saps`.`message_sender_log`"));
        assertTrue(statsSql.contains("`th_saps`.`message_receiver_log`"));
        assertTrue(statsSql.contains("UNION ALL"));
        assertTrue(statsSql.contains("s.status IN (1, 2) AND s.create_time < '"));
        assertTrue(statsSql.contains("create_time >= '2026-05-27 00:00:00'"));
        assertTrue(statsSql.contains("create_time < '2026-06-03 00:00:00'"));
        assertTrue(queryClient.sqls.stream().anyMatch(sql -> sql.contains("`assets_saps`.`message_sender_log`")));
    }

    @Test
    void collectSummary_shouldDegradeSlowDatabaseAndKeepOthers() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps", "th_saps"));
        queryClient.statsRows.put("assets_saps", List.of(
                senderStatsRow("assets_saps", 100, 0, 0, 100, "2026-06-02 09:00:00"),
                receiverStatsRow("assets_saps", 50, 0, "2026-06-02 09:05:00")));
        queryClient.statsFailures.put("th_saps", new IllegalArgumentException("数据库查询失败：Query execution timed out"));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        MqDashboardResponse dashboard = collector.collectSummary(
                monitor(), LocalDate.of(2026, 5, 27), LocalDate.of(2026, 6, 2));

        assertEquals("WARN", dashboard.status());
        assertTrue(dashboard.message().contains("1 个子应用库采集失败：th_saps"));
        assertEquals(100, dashboard.sendTotalCount());
        assertEquals(0, dashboard.sendFailedCount());
        assertEquals(null, dashboard.databaseStats().getFirst().errorMessage());
        assertTrue(dashboard.databaseStats().getLast().errorMessage().contains("采集失败"));
        assertTrue(dashboard.databaseStats().getLast().sendTotalCount() == 0);
    }

    @Test
    void collectSummary_shouldReturnWarnWhenOnlyPendingMessages() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps"));
        queryClient.statsRows.put("assets_saps", List.of(
                senderStatsRow("assets_saps", 10, 0, 3, 7, "2026-06-02 09:00:00")));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        MqDashboardResponse dashboard = collector.collectSummary(
                monitor(), LocalDate.of(2026, 5, 27), LocalDate.of(2026, 6, 2));

        assertEquals("WARN", dashboard.status());
        assertEquals("MQ 窗口发送积压 3 条", dashboard.message());
    }

    @Test
    void collectSummary_shouldReportErrorWhenNoDatabaseDiscovered() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(List.of());
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        MqDashboardResponse dashboard = collector.collectSummary(
                monitor(), LocalDate.of(2026, 5, 27), LocalDate.of(2026, 6, 2));

        assertEquals("ERROR", dashboard.status());
        assertTrue(dashboard.message().contains("未发现包含 message_sender_log 的子应用库"));
        assertEquals(1, queryClient.sqls.size());
    }

    @Test
    void collectMessages_shouldMergePagesAcrossDatabasesAndKeepCompositeIdentity() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps", "th_saps"));
        queryClient.verifiedRows.put("assets_saps", List.of(
                messageRow("assets_saps", "SENDER", 9921L, "msg-a", 3, null, null, "2026-06-02 09:00:00")));
        queryClient.verifiedRows.put("th_saps", List.of(
                messageRow("th_saps", "SENDER", 9921L, "msg-b", 2, null, null, "2026-06-01 09:00:00")));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        MqMessagePageResponse response = collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                1, 20, "ALL", null, null);

        assertEquals(2, response.total());
        assertEquals("ERROR", response.status());
        assertEquals("SEND_FAILED", response.messages().getFirst().failureType());
        assertEquals("发送失败", response.messages().getFirst().failureTypeName());
        assertEquals("SEND_STUCK", response.messages().getLast().failureType());
        assertEquals("发送积压", response.messages().getLast().failureTypeName());
        assertEquals("assets_saps", response.messages().getFirst().databaseName());
        assertEquals("th_saps", response.messages().getLast().databaseName());
        assertTrue(response.executionEvidence() != null);
        assertEquals(List.of("assets_saps", "th_saps"), response.executionEvidence().definition().discoveredSchemas());
        assertEquals(30, response.executionEvidence().definition().lagThresholdMinutes());
        assertTrue(response.executionEvidence().completePage());
        assertEquals(2, response.executionEvidence().queryHashes().size());
        String joinedSql = queryClient.verifiedSqls.getFirst();
        assertTrue(joinedSql.contains("LEFT JOIN"));
        assertTrue(joinedSql.contains("ORDER BY msgs.occurredAt DESC"));
        assertTrue(joinedSql.contains("LIMIT 20"));
        assertTrue(joinedSql.contains("s.status = 3"));
        assertTrue(joinedSql.contains("COALESCE(r.deal_business_success, 0) = 0"));
    }

    @Test
    void collectMessages_shouldFailWhenPageIsIncompleteOrDuplicated() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps"));
        queryClient.verifiedRows.put("assets_saps", List.of(
                messageRow("assets_saps", "SENDER", 9921L, "msg-a", 3, null, null, "2026-06-02 09:00:00"),
                messageRow("assets_saps", "SENDER", 9921L, "msg-a", 3, null, null, "2026-06-02 09:00:00")
        ));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 1, 20, "ALL", null, null));
    }

    @Test
    void collectMessages_shouldFailWhenAnyDatabaseDegrades() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps", "th_saps"));
        queryClient.verifiedRows.put("assets_saps", List.of(
                messageRow("assets_saps", "SENDER", 9921L, "msg-a", 3, null, null, "2026-06-02 09:00:00")));
        queryClient.verifiedFailures.put("th_saps", new IllegalArgumentException("数据库查询失败：Query execution timed out"));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 1, 20, "ALL", null, null));
        assertTrue(error.getMessage().contains("th_saps"));
    }

    @Test
    void collectMessages_shouldRejectPaginationBeyondFetchDepth() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps"));
        List<Map<String, Object>> saturated = new ArrayList<>();
        for (long logId = 1; logId <= 99; logId++) {
            saturated.add(messageRow("assets_saps", "SENDER", logId, "msg-" + logId, 3, null, null,
                    "2026-06-02 08:00:00"));
        }
        queryClient.verifiedTotals.put("assets_saps", 150L);
        queryClient.verifiedRows.put("assets_saps", saturated);
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 2, 50, "ALL", null, null));
        assertTrue(error.getMessage().contains("分页深度超出单库取数上限"));
    }

    @Test
    void collect_shouldCombineStatsAndPagedMessagesFromSingleBatchPerDatabase() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps"));
        queryClient.batchRows.put("assets_saps", List.of(
                List.of(senderStatsRow("assets_saps", 10, 1, 0, 9, "2026-06-02 09:00:00")),
                List.of(messageRow("assets_saps", "SENDER", 9921L, "msg-a", 3, 500, "timeout", "2026-06-02 09:00:00"))));
        queryClient.statsRows.put("assets_saps", List.of(
                senderStatsRow("assets_saps", 10, 1, 0, 9, "2026-06-02 09:00:00")));
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        MqDashboardResponse dashboard = collector.collect(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                1, 20, "SENDER", null, null);

        assertEquals("ERROR", dashboard.status());
        assertEquals(1, dashboard.failedMessageCount());
        assertEquals(1, dashboard.messages().size());
        assertEquals(500, dashboard.messages().getFirst().retryCount());
        assertEquals("timeout", dashboard.messages().getFirst().errorMsg());
        assertEquals(1, dashboard.sendFailedCount());
        assertEquals(0, dashboard.receiveTotalCount());
        String statsSql = queryClient.sqls.getLast();
        assertTrue(statsSql.contains("`assets_saps`.`message_sender_log`"));
        assertTrue(statsSql.contains("message_receiver_log"));
        // REQ-b4d7e6f2 起 stats 单条也走批量接口进入 batchSqls，消息组合语句取含外壳标志的那条
        String messageSql = queryClient.batchSqls.stream()
                .filter(sql -> sql.contains("stats.*, msgs.*"))
                .findFirst()
                .orElseThrow();
        assertTrue(messageSql.contains("`assets_saps`.`message_sender_log`"));
        assertTrue(!messageSql.contains("message_receiver_log"));
    }

    @Test
    void collectMessages_shouldApplyExchangeAndQueueFiltersAndRejectInvalidKeywords() {
        FakeMqDatabaseQueryClient queryClient = new FakeMqDatabaseQueryClient();
        queryClient.nextRows(discoveryRows("assets_saps"));
        queryClient.verifiedRows.put("assets_saps", List.of());
        MqMonitorCollector collector = new MqMonitorCollector(queryClient);

        collector.collectMessages(monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                1, 20, "RECEIVER", "pay.order,refund.order", "lease.saps");

        String sql = queryClient.verifiedSqls.getFirst();
        assertTrue(sql.contains("r.exchange IN ('pay.order','refund.order')"));
        assertTrue(sql.contains("r.queue IN ('lease.saps')"));
        assertTrue(!sql.contains("'SENDER'"));

        assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 1, 20, "ALL", "bad exchange", null));
        assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 0, 20, "ALL", null, null));
        assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 1, 0, "ALL", null, null));
        assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1), 1, 20, "ALL", null, null));
        assertThrows(IllegalArgumentException.class, () -> collector.collectMessages(
                monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 1, 20, "SIDE", null, null));
    }

    private OpsMonitorEntity monitor() {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(7L);
        entity.setMonitorType("MQ");
        entity.setMonitorKey("pay:PROD:MQ");
        entity.setBusinessLineCode("BL000001");
        entity.setEnvironmentCode("PROD");
        entity.setName("保费分期 PROD MQ 消息");
        entity.setMqLagThreshold(30);
        entity.setEnabled(true);
        return entity;
    }

    private List<Map<String, Object>> discoveryRows(String... databases) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String database : databases) {
            rows.add(row("databaseName", database));
        }
        return rows;
    }

    private Map<String, Object> senderStatsRow(String database, long total, long failed, long pending, long success, String latest) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("databaseName", database);
        row.put("sendTotalCount", total);
        row.put("sendFailedCount", failed);
        row.put("sendPendingCount", pending);
        row.put("sendSuccessCount", success);
        row.put("receiveTotalCount", 0);
        row.put("receiveSuccessCount", 0);
        row.put("receiveFailedCount", 0);
        row.put("latestActivityAt", latest);
        return row;
    }

    private Map<String, Object> receiverStatsRow(String database, long total, long failed, String latest) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("databaseName", database);
        row.put("sendTotalCount", 0);
        row.put("sendFailedCount", 0);
        row.put("sendPendingCount", 0);
        row.put("sendSuccessCount", 0);
        row.put("receiveTotalCount", total);
        row.put("receiveSuccessCount", total - failed);
        row.put("receiveFailedCount", failed);
        row.put("latestActivityAt", latest);
        return row;
    }

    private Map<String, Object> messageRow(String database, String logType, long logId, String messageId,
                                           int status, Integer retryCount, String errorMsg, String occurredAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("databaseName", database);
        row.put("logType", logType);
        row.put("logId", logId);
        row.put("messageId", messageId);
        row.put("exchangeName", "pay.order");
        row.put("routingKey", "order.created");
        row.put("queue", "RECEIVER".equals(logType) ? "lease.saps" : "");
        row.put("status", status);
        row.put("retryCount", retryCount);
        row.put("autoRetry", null);
        row.put("dealBusinessSuccess", null);
        row.put("occurredAt", occurredAt);
        row.put("createTime", occurredAt);
        row.put("errorMsg", errorMsg);
        return row;
    }

    private Map<String, Object> row(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static class FakeMqDatabaseQueryClient implements XxlJobDatabaseQueryClient {
        private final List<List<Map<String, Object>>> queuedRows = new ArrayList<>();
        private final List<String> sqls = new ArrayList<>();
        private final List<String> batchSqls = new ArrayList<>();
        private final List<String> verifiedSqls = new ArrayList<>();
        private final Map<String, List<Map<String, Object>>> statsRows = new LinkedHashMap<>();
        private final Map<String, List<List<Map<String, Object>>>> batchRows = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> verifiedRows = new LinkedHashMap<>();
        private final Map<String, RuntimeException> statsFailures = new LinkedHashMap<>();
        private final Map<String, RuntimeException> verifiedFailures = new LinkedHashMap<>();
        private final Map<String, Long> verifiedTotals = new LinkedHashMap<>();

        void nextRows(List<Map<String, Object>> rows) {
            queuedRows.add(rows);
        }

        @Override
        public List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql) {
            sqls.add(sql);
            if (sql.contains("information_schema")) {
                return queuedRows.removeFirst();
            }
            String database = databaseOf(sql);
            RuntimeException failure = statsFailures.get(database);
            if (failure != null) {
                throw failure;
            }
            return statsRows.get(database);
        }

        @Override
        public List<List<Map<String, Object>>> runRowsBatch(String businessLineCode, String environmentCode, List<String> sqls) {
            // REQ-b4d7e6f2 起单条执行（库发现/统计）也统一走批量接口：非组合语句退化为原 runRows 路由
            if (sqls.size() == 1 && !sqls.getFirst().contains("stats.*, msgs.*")) {
                batchSqls.addAll(sqls);
                return List.of(runRows(businessLineCode, environmentCode, sqls.getFirst()));
            }
            batchSqls.addAll(sqls);
            String database = databaseOf(sqls.getFirst());
            List<List<Map<String, Object>>> batches = batchRows.get(database);
            return List.of(combinedRows(database, batches.get(1), null));
        }

        @Override
        public VerifiedBatch runVerifiedBatch(String businessLineCode, String environmentCode, List<String> sqls) {
            var started = java.time.Instant.parse("2026-06-02T02:00:00Z");
            verifiedSqls.addAll(sqls);
            String database = databaseOf(sqls.getFirst());
            RuntimeException failure = verifiedFailures.get(database);
            if (failure != null) {
                throw failure;
            }
            Long forcedTotal = verifiedTotals.get(database);
            return new VerifiedBatch("a".repeat(64), started, started.plusSeconds(1),
                    List.of(combinedRows(database, verifiedRows.get(database), forcedTotal)));
        }

        private String databaseOf(String sql) {
            return MqMonitorCollectorTest.databaseOf(sql);
        }

        private List<Map<String, Object>> combinedRows(String database, List<Map<String, Object>> messageRows, Long forcedTotal) {
            long total = forcedTotal != null ? forcedTotal : messageRows.size();
            var statistics = new LinkedHashMap<String, Object>();
            statistics.put("activityCount", Math.max(total, messageRows.size()));
            statistics.put("matchingCount", total);
            statistics.put("latestActivityAt", messageRows.isEmpty() ? null : messageRows.getFirst().get("occurredAt"));
            var combined = new ArrayList<Map<String, Object>>();
            if (messageRows.isEmpty()) {
                var row = new LinkedHashMap<String, Object>(statistics);
                row.put("logId", null);
                combined.add(row);
                return combined;
            }
            for (var detail : messageRows) {
                var row = new LinkedHashMap<String, Object>(statistics);
                row.putAll(detail);
                combined.add(row);
            }
            return combined;
        }
    }

    private static String databaseOf(String sql) {
        for (String candidate : List.of("assets_saps", "th_saps", "ol_order")) {
            if (sql.contains("`" + candidate + "`.`message_")) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("测试 SQL 无法识别目标库");
    }
}
