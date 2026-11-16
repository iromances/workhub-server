package cn.aslight.workhub.service.ops;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REQ-9f3c2a84：种子模板渲染等价性守卫。expected 侧保留改造前 Collector 的
 * .formatted 拼接表达式（原文本块原样内嵌），render 侧走 FunctionCallService
 * 渲染 FuncCallSqlSeeds 种子；两者必须逐字节一致，防止模板化造成口径漂移。
 */
class FunctionCallTemplateEquivalenceTest {

    private static final DateTimeFormatter LITERAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FunctionCallService service = new FunctionCallService(new SeedsTemplateStore(), null);

    private static String literal(LocalDateTime value) {
        return value.format(LITERAL);
    }

    @Test
    void xxlSummaryTemplateMatchesLegacyFormatting() {
        LocalDate start = LocalDate.of(2026, 11, 8);
        LocalDate end = LocalDate.of(2026, 11, 14);
        String expected = """
                SELECT
                  (SELECT COUNT(1) FROM %1$s.`xxl_job_info`) AS jobCount,
                  (SELECT COUNT(1) FROM %1$s.`xxl_job_info` WHERE trigger_status = 1) AS enabledJobCount,
                  (SELECT COUNT(1) FROM %1$s.`xxl_job_info` WHERE trigger_status <> 1) AS disabledJobCount,
                  (SELECT GROUP_CONCAT(address_list SEPARATOR ',') FROM %1$s.`xxl_job_group` WHERE address_list IS NOT NULL AND address_list <> '') AS executorRegistryList,
                  (SELECT COALESCE(SUM(running_count), 0) FROM %1$s.`xxl_job_log_report` WHERE trigger_day >= '%2$s' AND trigger_day < '%3$s') AS triggerRunningCount,
                  (SELECT COALESCE(SUM(suc_count), 0) FROM %1$s.`xxl_job_log_report` WHERE trigger_day >= '%2$s' AND trigger_day < '%3$s') AS triggerSuccessCount,
                  (SELECT COALESCE(SUM(fail_count), 0) FROM %1$s.`xxl_job_log_report` WHERE trigger_day >= '%2$s' AND trigger_day < '%3$s') AS triggerFailedCount,
                  (SELECT MAX(trigger_day) FROM %1$s.`xxl_job_log_report` WHERE trigger_day >= '%2$s' AND trigger_day < '%3$s') AS reportUpdatedAt
                """.formatted("`db_xxl`", start, end.plusDays(1));
        assertEquals(expected, service.render("OPS_XXL_SUMMARY", FunctionCallService.params(
                "databaseName", "`db_xxl`",
                "startDate", start.toString(),
                "endDateExclusive", end.plusDays(1).toString())));
    }

    @Test
    void xxlFocusedSummaryTemplateMatchesLegacyFormatting() {
        LocalDate start = LocalDate.of(2026, 11, 8);
        LocalDate end = LocalDate.of(2026, 11, 14);
        String executorFilter = "AND g.app_name IN ('lease-saps','lease-payment')";
        String expected = """
                SELECT
                  job_stats.jobCount AS jobCount,
                  job_stats.enabledJobCount AS enabledJobCount,
                  job_stats.disabledJobCount AS disabledJobCount,
                  executor_stats.executorRegistryList AS executorRegistryList,
                  log_stats.triggerRunningCount AS triggerRunningCount,
                  log_stats.triggerSuccessCount AS triggerSuccessCount,
                  log_stats.triggerFailedCount AS triggerFailedCount,
                  log_stats.reportUpdatedAt AS reportUpdatedAt
                FROM (
                  SELECT
                    COUNT(1) AS jobCount,
                    COALESCE(SUM(CASE WHEN i.trigger_status = 1 THEN 1 ELSE 0 END), 0) AS enabledJobCount,
                    COALESCE(SUM(CASE WHEN i.trigger_status <> 1 THEN 1 ELSE 0 END), 0) AS disabledJobCount
                  FROM %1$s.`xxl_job_info` i
                  JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                  WHERE 1 = 1 %2$s
                ) job_stats
                CROSS JOIN (
                  SELECT GROUP_CONCAT(g.address_list SEPARATOR ',') AS executorRegistryList
                  FROM %1$s.`xxl_job_group` g
                  WHERE g.address_list IS NOT NULL AND g.address_list <> '' %2$s
                ) executor_stats
                CROSS JOIN (
                  SELECT
                    COALESCE(SUM(CASE WHEN l.handle_code = 0 AND l.trigger_code IN (0, 200) THEN 1 ELSE 0 END), 0) AS triggerRunningCount,
                    COALESCE(SUM(CASE WHEN l.handle_code = 200 THEN 1 ELSE 0 END), 0) AS triggerSuccessCount,
                    COALESCE(SUM(CASE WHEN l.handle_code <> 200
                                      AND NOT (l.trigger_code IN (0, 200) AND l.handle_code = 0)
                                      THEN 1 ELSE 0 END), 0) AS triggerFailedCount,
                    MAX(l.trigger_time) AS reportUpdatedAt
                  FROM %1$s.`xxl_job_log` l
                  JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                  JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                  WHERE l.trigger_time >= '%3$s'
                    AND l.trigger_time < '%4$s'
                    %2$s
                ) log_stats
                """.formatted("`db_xxl`", executorFilter, start + " 00:00:00", end.plusDays(1) + " 00:00:00");
        assertEquals(expected, service.render("OPS_XXL_FOCUSED_SUMMARY", FunctionCallService.params(
                "databaseName", "`db_xxl`",
                "executorFilter", executorFilter,
                "startDateTime", start + " 00:00:00",
                "endExclusiveDateTime", end.plusDays(1) + " 00:00:00")));
    }

    @Test
    void xxlHealthStatsTemplateMatchesLegacyFormatting() {
        LocalDate start = LocalDate.of(2026, 11, 8);
        LocalDate end = LocalDate.of(2026, 11, 14);
        LocalDateTime staleCutoff = LocalDateTime.of(2026, 11, 14, 15, 0, 0);
        LocalDateTime silentGraceCutoff = LocalDateTime.of(2026, 11, 14, 15, 50, 0);
        String expected = """
                SELECT
                  job_stats.jobCount AS jobCount,
                  job_stats.enabledJobCount AS enabledJobCount,
                  job_stats.disabledJobCount AS disabledJobCount,
                  job_stats.silentJobCount AS silentJobCount,
                  executor_stats.executorRegistryList AS executorRegistryList,
                  log_stats.successCount AS successCount,
                  log_stats.runningCount AS runningCount,
                  log_stats.stuckCount AS stuckCount,
                  log_stats.executionFailedCount AS executionFailedCount,
                  log_stats.reportUpdatedAt AS reportUpdatedAt
                FROM (
                  SELECT
                    COUNT(1) AS jobCount,
                    COALESCE(SUM(CASE WHEN i.trigger_status = 1 THEN 1 ELSE 0 END), 0) AS enabledJobCount,
                    COALESCE(SUM(CASE WHEN i.trigger_status <> 1 THEN 1 ELSE 0 END), 0) AS disabledJobCount,
                    COALESCE(SUM(CASE WHEN i.trigger_status = 1 AND (i.trigger_next_time IS NULL OR i.trigger_next_time < '%1$s') THEN 1 ELSE 0 END), 0) AS silentJobCount
                  FROM %2$s.`xxl_job_info` i
                ) job_stats
                CROSS JOIN (
                  SELECT GROUP_CONCAT(g.address_list SEPARATOR ',') AS executorRegistryList
                  FROM %2$s.`xxl_job_group` g
                  WHERE g.address_list IS NOT NULL AND g.address_list <> ''
                ) executor_stats
                CROSS JOIN (
                  SELECT
                    COALESCE(SUM(CASE WHEN l.trigger_code = 200 AND l.handle_code = 200 THEN 1 ELSE 0 END), 0) AS successCount,
                    COALESCE(SUM(CASE WHEN l.trigger_code = 200 AND (l.handle_code = 0 OR l.handle_code IS NULL) AND l.trigger_time >= '%3$s' THEN 1 ELSE 0 END), 0) AS runningCount,
                    COALESCE(SUM(CASE WHEN l.trigger_code = 200 AND (l.handle_code = 0 OR l.handle_code IS NULL) AND l.trigger_time < '%3$s' THEN 1 ELSE 0 END), 0) AS stuckCount,
                    COALESCE(SUM(CASE WHEN l.trigger_code IS NULL OR l.trigger_code <> 200 OR l.handle_code NOT IN (0, 200) THEN 1 ELSE 0 END), 0) AS executionFailedCount,
                    MAX(l.trigger_time) AS reportUpdatedAt
                  FROM %2$s.`xxl_job_log` l
                  WHERE l.trigger_time >= '%4$s 00:00:00'
                    AND l.trigger_time < '%5$s 00:00:00'
                ) log_stats
                """.formatted(literal(silentGraceCutoff), "`db_xxl`", literal(staleCutoff), start, end.plusDays(1));
        assertEquals(expected, service.render("OPS_XXL_HEALTH_STATS", FunctionCallService.params(
                "silentGraceCutoff", literal(silentGraceCutoff),
                "databaseName", "`db_xxl`",
                "staleCutoff", literal(staleCutoff),
                "startDate", start.toString(),
                "endDateExclusive", end.plusDays(1).toString())));
    }

    @Test
    void xxlFailedCountAndPageTemplatesMatchLegacyFormatting() {
        LocalDate start = LocalDate.of(2026, 11, 8);
        LocalDate end = LocalDate.of(2026, 11, 14);
        String executorFilter = "AND g.app_name IN ('ol-saps')";
        String detailFilter = "AND i.author LIKE '%zhang%' ";
        String statusFilter = "AND (l.trigger_code IS NULL OR l.trigger_code <> 200 OR l.handle_code NOT IN (0, 200) OR ((l.handle_code = 0 OR l.handle_code IS NULL) AND l.trigger_time < '2026-11-14 15:00:00'))";
        String countExpected = """
                SELECT COUNT(1) AS failedJobTotal
                FROM %1$s.`xxl_job_log` l
                JOIN %1$s.`xxl_job_info` i ON i.id = l.job_id
                JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                WHERE l.trigger_time >= '%2$s'
                  AND l.trigger_time < '%3$s'
                  %4$s
                  %5$s
                  %6$s
                """.formatted("`db_xxl`", start + " 00:00:00", end.plusDays(1) + " 00:00:00", executorFilter, detailFilter, statusFilter);
        assertEquals(countExpected, service.render("OPS_XXL_FAILED_COUNT", FunctionCallService.params(
                "databaseName", "`db_xxl`",
                "startDateTime", start + " 00:00:00",
                "endExclusiveDateTime", end.plusDays(1) + " 00:00:00",
                "executorFilter", executorFilter,
                "detailFilter", detailFilter,
                "statusFilter", statusFilter)));

        String pageExpected = """
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
                """.formatted("`db_xxl`", start + " 00:00:00", end.plusDays(1) + " 00:00:00", executorFilter, detailFilter, statusFilter, 20, 0);
        assertEquals(pageExpected, service.render("OPS_XXL_FAILED_PAGE", FunctionCallService.params(
                "databaseName", "`db_xxl`",
                "startDateTime", start + " 00:00:00",
                "endExclusiveDateTime", end.plusDays(1) + " 00:00:00",
                "executorFilter", executorFilter,
                "detailFilter", detailFilter,
                "statusFilter", statusFilter,
                "limit", "20",
                "offset", "0")));
    }

    @Test
    void xxlSilentJobsLogStatsAndExecutorsTemplatesMatchLegacyFormatting() {
        LocalDateTime silentGraceCutoff = LocalDateTime.of(2026, 11, 14, 15, 50, 0);
        String silentExpected = """
                SELECT
                  g.app_name AS executorAppName,
                  i.id AS jobId,
                  i.job_desc AS jobDesc,
                  i.author AS author,
                  i.executor_handler AS executorHandler,
                  i.trigger_next_time AS triggerNextTime
                FROM %1$s.`xxl_job_info` i
                JOIN %1$s.`xxl_job_group` g ON g.id = i.job_group
                WHERE i.trigger_status = 1
                  AND (i.trigger_next_time IS NULL OR i.trigger_next_time < '%2$s')
                ORDER BY i.trigger_next_time ASC
                LIMIT 20
                """.formatted("`db_xxl`", literal(silentGraceCutoff));
        assertEquals(silentExpected, service.render("OPS_XXL_SILENT_JOBS", FunctionCallService.params(
                "databaseName", "`db_xxl`",
                "silentGraceCutoff", literal(silentGraceCutoff))));

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 9);
        String matching = "CASE WHEN l.trigger_code IS NULL OR l.trigger_code <> 200 OR l.handle_code NOT IN (0, 200) OR ((l.handle_code = 0 OR l.handle_code IS NULL) AND l.trigger_time < '2026-06-09 13:00:00') THEN 1 END";
        String statsExpected = """
                SELECT COUNT(%1$s) AS failedJobTotal,
                  COUNT(1) AS activityCount,
                  MAX(GREATEST(l.trigger_time, COALESCE(l.handle_time, l.trigger_time))) AS latestActivityAt
                FROM %2$s.`xxl_job_log` l
                JOIN %2$s.`xxl_job_info` i ON i.id = l.job_id
                JOIN %2$s.`xxl_job_group` g ON g.id = i.job_group
                WHERE l.trigger_time >= '%3$s 00:00:00'
                  AND l.trigger_time < '%4$s 00:00:00'
                  %5$s
                  %6$s
                """.formatted(matching, "`db_xxl`", from, to.plusDays(1), "AND g.app_name IN ('ol-saps')", "");
        assertEquals(statsExpected, service.render("OPS_XXL_LOG_STATS", FunctionCallService.params(
                "matching", matching,
                "databaseName", "`db_xxl`",
                "startDate", from.toString(),
                "endDateExclusive", to.plusDays(1).toString(),
                "executorFilter", "AND g.app_name IN ('ol-saps')",
                "detailFilter", "")));

        String executorsExpected = """
                SELECT
                  app_name AS appName,
                  title AS title
                FROM %1$s.`xxl_job_group`
                ORDER BY app_name ASC
                """.formatted("`db_xxl`");
        assertEquals(executorsExpected, service.render("OPS_XXL_EXECUTORS", FunctionCallService.params(
                "databaseName", "`db_xxl`")));
    }

    @Test
    void mqStatsTemplatesMatchLegacyFormatting() {
        LocalDate from = LocalDate.of(2026, 11, 8);
        LocalDate to = LocalDate.of(2026, 11, 14);
        String windowFrom = from + " 00:00:00";
        String windowTo = to.plusDays(1) + " 00:00:00";
        String stuckLiteral = "2026-11-14 15:00:00";
        String senderExpected = """
                SELECT '%1$s' AS databaseName,
                  COUNT(1) AS sendTotalCount,
                  COALESCE(SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END), 0) AS sendFailedCount,
                  COALESCE(SUM(CASE WHEN s.status IN (1, 2) AND s.create_time < '%5$s' THEN 1 ELSE 0 END), 0) AS sendPendingCount,
                  COALESCE(SUM(CASE WHEN s.status = 4 THEN 1 ELSE 0 END), 0) AS sendSuccessCount,
                  0 AS receiveTotalCount,
                  0 AS receiveSuccessCount,
                  0 AS receiveFailedCount,
                  MAX(s.create_time) AS latestActivityAt
                FROM %2$s.`message_sender_log` s
                WHERE s.create_time >= '%3$s' AND s.create_time < '%4$s'%6$s
                """.formatted("mq_log", "`db_mq`", windowFrom, windowTo, stuckLiteral, " AND s.exchange IN ('ex.a','ex.b')").stripTrailing();
        assertEquals(senderExpected, service.render("OPS_MQ_STATS_SENDER", FunctionCallService.params(
                "databaseLabel", "mq_log",
                "databaseName", "`db_mq`",
                "windowFrom", windowFrom,
                "windowTo", windowTo,
                "stuckLiteral", stuckLiteral,
                "exchangeFilter", " AND s.exchange IN ('ex.a','ex.b')")));

        String receiverExpected = """
                SELECT '%1$s' AS databaseName,
                  0 AS sendTotalCount,
                  0 AS sendFailedCount,
                  0 AS sendPendingCount,
                  0 AS sendSuccessCount,
                  COUNT(1) AS receiveTotalCount,
                  COALESCE(SUM(CASE WHEN NOT (r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0) THEN 1 ELSE 0 END), 0) AS receiveSuccessCount,
                  COALESCE(SUM(CASE WHEN r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0 THEN 1 ELSE 0 END), 0) AS receiveFailedCount,
                  MAX(r.create_time) AS latestActivityAt
                FROM %6$s.`message_receiver_log` r
                WHERE r.create_time >= '%2$s' AND r.create_time < '%3$s'%4$s%5$s
                """.formatted("mq_log", windowFrom, windowTo, "", " AND r.queue IN ('q1')", "`db_mq`").stripTrailing();
        assertEquals(receiverExpected, service.render("OPS_MQ_STATS_RECEIVER", FunctionCallService.params(
                "databaseLabel", "mq_log",
                "windowFrom", windowFrom,
                "windowTo", windowTo,
                "exchangeFilter", "",
                "queueFilter", " AND r.queue IN ('q1')",
                "databaseName", "`db_mq`")));
    }

    @Test
    void mqMessageStatsTemplatesMatchLegacyFormatting() {
        LocalDate[] window = {LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)};
        String windowFrom = window[0] + " 00:00:00";
        String windowTo = window[1].plusDays(1) + " 00:00:00";
        String stuckLiteral = "2026-11-14 15:00:00";
        String senderPredicate = "(s.status = 3 OR (s.status IN (1, 2) AND s.create_time < '" + stuckLiteral + "'))";
        String receiverPredicate = "(r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0)";
        String senderExpected = """
                SELECT COUNT(1) AS activityCount,
                  COALESCE(SUM(CASE WHEN %4$s THEN 1 ELSE 0 END), 0) AS matchingCount,
                  MAX(s.create_time) AS latestActivityAt
                FROM %1$s.`message_sender_log` s
                WHERE s.create_time >= '%2$s' AND s.create_time < '%3$s'%5$s
                """.formatted("`db_mq`", windowFrom, windowTo, senderPredicate, "").stripTrailing();
        assertEquals(senderExpected, service.render("OPS_MQ_MESSAGE_STATS_SENDER", FunctionCallService.params(
                "databaseName", "`db_mq`",
                "windowFrom", windowFrom,
                "windowTo", windowTo,
                "senderPredicate", senderPredicate,
                "exchangeFilter", "")));

        String receiverExpected = """
                SELECT COUNT(1) AS activityCount,
                  COALESCE(SUM(CASE WHEN %4$s THEN 1 ELSE 0 END), 0) AS matchingCount,
                  MAX(r.create_time) AS latestActivityAt
                FROM %1$s.`message_receiver_log` r
                WHERE r.create_time >= '%2$s' AND r.create_time < '%3$s'%5$s%6$s
                """.formatted("`db_mq`", windowFrom, windowTo, receiverPredicate, "", " AND r.queue IN ('q1')").stripTrailing();
        assertEquals(receiverExpected, service.render("OPS_MQ_MESSAGE_STATS_RECEIVER", FunctionCallService.params(
                "databaseName", "`db_mq`",
                "windowFrom", windowFrom,
                "windowTo", windowTo,
                "receiverPredicate", receiverPredicate,
                "exchangeFilter", "",
                "queueFilter", " AND r.queue IN ('q1')")));
    }

    @Test
    void mqMessagePageTemplatesMatchLegacyFormatting() {
        LocalDate[] window = {LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)};
        String windowFrom = window[0] + " 00:00:00";
        String windowTo = window[1].plusDays(1) + " 00:00:00";
        String stuckLiteral = "2026-11-14 15:00:00";
        String senderPredicate = "(s.status = 3 OR (s.status IN (1, 2) AND s.create_time < '" + stuckLiteral + "'))";
        String receiverPredicate = "(r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0)";
        String senderExpected = """
                SELECT '%1$s' AS databaseName, 'SENDER' AS logType, s.id AS logId, s.message_id AS messageId,
                  s.exchange AS exchangeName, s.routing_key AS routingKey, '' AS queue, s.status AS status,
                  s.real_retry_count AS retryCount, 0 AS autoRetry, NULL AS dealBusinessSuccess,
                  COALESCE(s.send_time, s.create_time) AS occurredAt, s.create_time AS createTime, s.error_msg AS errorMsg
                FROM %2$s.`message_sender_log` s
                WHERE s.create_time >= '%3$s' AND s.create_time < '%4$s' AND %5$s%6$s
                """.formatted("mq_log", "`db_mq`", windowFrom, windowTo, senderPredicate, " AND s.exchange IN ('ex.a')").stripTrailing();
        assertEquals(senderExpected, service.render("OPS_MQ_MESSAGE_PAGE_SENDER", FunctionCallService.params(
                "databaseLabel", "mq_log",
                "databaseName", "`db_mq`",
                "windowFrom", windowFrom,
                "windowTo", windowTo,
                "senderPredicate", senderPredicate,
                "exchangeFilter", " AND s.exchange IN ('ex.a')")));

        String receiverExpected = """
                SELECT '%1$s' AS databaseName, 'RECEIVER' AS logType, r.id AS logId, r.message_id AS messageId,
                  r.exchange AS exchangeName, r.routing_key AS routingKey, r.queue AS queue, r.status AS status,
                  0 AS retryCount, COALESCE(r.auto_retry, 0) AS autoRetry, COALESCE(r.deal_business_success, 0) AS dealBusinessSuccess,
                  COALESCE(r.receiver_time, r.create_time) AS occurredAt, r.create_time AS createTime, r.error_msg AS errorMsg
                FROM %7$s.`message_receiver_log` r
                WHERE r.create_time >= '%2$s' AND r.create_time < '%3$s' AND %4$s%5$s%6$s
                """.formatted("mq_log", windowFrom, windowTo, receiverPredicate, "", " AND r.queue IN ('q1')", "`db_mq`").stripTrailing();
        assertEquals(receiverExpected, service.render("OPS_MQ_MESSAGE_PAGE_RECEIVER", FunctionCallService.params(
                "databaseLabel", "mq_log",
                "windowFrom", windowFrom,
                "windowTo", windowTo,
                "receiverPredicate", receiverPredicate,
                "exchangeFilter", "",
                "queueFilter", " AND r.queue IN ('q1')",
                "databaseName", "`db_mq`")));
    }

    @Test
    void renderScenarioReturnsBranchesInOrder() {
        List<String> branches = service.renderScenario("OPS-MQ-STATS", FunctionCallService.params(
                "databaseLabel", "mq_log",
                "databaseName", "`db_mq`",
                "windowFrom", "2026-11-08 00:00:00",
                "windowTo", "2026-11-15 00:00:00",
                "stuckLiteral", "2026-11-14 15:00:00",
                "exchangeFilter", "",
                "queueFilter", ""));
        assertEquals(2, branches.size());
        assertEquals(true, branches.get(0).startsWith("SELECT 'mq_log' AS databaseName"));
        assertEquals(true, branches.get(1).contains("FROM `db_mq`.`message_receiver_log`"));
    }

    @Test
    void missingParamIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.render("OPS_XXL_EXECUTORS", FunctionCallService.params()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.render("OPS_XXL_NOT_EXIST", FunctionCallService.params("databaseName", "`db`")));
    }
}
