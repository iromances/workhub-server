package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.OpsMonitorCheckResponse;
import cn.aslight.workhub.model.ops.OpsMonitorEntity;
import cn.aslight.workhub.model.ops.XxlJobDashboardResponse;
import cn.aslight.workhub.model.ops.XxlJobExecutorResponse;
import cn.aslight.workhub.model.ops.XxlJobLogPageResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XxlJobMonitorCollectorTest {

    private static final String LOG_STATUS_ALL = "ALL";

    @Test
    void collectSummary_shouldReadXxlJobAdminReportSnapshotWithoutFailedJobDetail() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999,10.0.0.2:9999,10.0.0.1:9999",
                "jobCount", 8,
                "enabledJobCount", 6,
                "disabledJobCount", 2,
                "triggerRunningCount", 3,
                "triggerSuccessCount", 11,
                "triggerFailedCount", 5,
                "reportUpdatedAt", "2026-06-02 13:40:00"
        )));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobDashboardResponse dashboard = collector.collectSummary(monitor());

        assertEquals("ERROR", dashboard.status());
        assertEquals(2, dashboard.executorCount());
        assertEquals(8, dashboard.jobCount());
        assertEquals(19, dashboard.triggerCount());
        assertEquals(3, dashboard.triggerRunningCount());
        assertEquals(11, dashboard.triggerSuccessCount());
        assertEquals(5, dashboard.triggerFailedCount());
        assertEquals("2026-06-02 13:40:00", dashboard.reportUpdatedAt());
        assertEquals(0, dashboard.failedJobs().size());
        assertEquals(1, queryClient.sqls.size());
        assertTrue(queryClient.sqls.getFirst().contains("`amp_xxl_job`.`xxl_job_log_report`"));
        assertTrue(queryClient.sqls.getFirst().contains("address_list"));
        assertTrue(queryClient.sqls.getFirst().contains("MAX(trigger_day)"));
        assertTrue(queryClient.sqls.getFirst().contains("trigger_day >= '"));
        assertTrue(queryClient.sqls.getFirst().contains("trigger_day < '"));
        assertTrue(!queryClient.sqls.getFirst().contains("registry_list"));
        assertTrue(!queryClient.sqls.getFirst().contains("update_time"));
        assertTrue(!queryClient.sqls.getFirst().contains("`amp_xxl_job`.`xxl_job_log`"));
    }

    @Test
    void collectSummary_shouldReadStatisticsWhenColumnLabelsAreLowerCase() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorregistrylist", "10.0.0.1:9999,10.0.0.2:9999",
                "jobcount", 8,
                "enabledjobcount", 6,
                "disabledjobcount", 2,
                "triggerrunningcount", 3,
                "triggersuccesscount", 11,
                "triggerfailedcount", 5,
                "reportupdatedat", "2026-06-02 00:00:00"
        )));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobDashboardResponse dashboard = collector.collectSummary(monitor());

        assertEquals(2, dashboard.executorCount());
        assertEquals(8, dashboard.jobCount());
        assertEquals(19, dashboard.triggerCount());
        assertEquals(5, dashboard.triggerFailedCount());
        assertEquals("2026-06-02 00:00:00", dashboard.reportUpdatedAt());
    }

    @Test
    void collectSummary_shouldScopeStatisticsToFocusedExecutorsWhenConfigured() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 2,
                "enabledJobCount", 1,
                "disabledJobCount", 1,
                "triggerRunningCount", 1,
                "triggerSuccessCount", 8,
                "triggerFailedCount", 2,
                "reportUpdatedAt", "2026-06-04 09:00:00"
        )));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);
        OpsMonitorEntity monitor = monitor();
        monitor.setExecutorAppName("premium-job,premium-settle");

        XxlJobDashboardResponse dashboard = collector.collectSummary(monitor);

        assertEquals(2, dashboard.jobCount());
        assertEquals(1, dashboard.enabledJobCount());
        assertEquals(1, dashboard.disabledJobCount());
        assertEquals(11, dashboard.triggerCount());
        assertEquals(2, dashboard.triggerFailedCount());
        assertTrue(queryClient.sqls.getFirst().contains("g.app_name IN ('premium-job','premium-settle')"));
        assertTrue(queryClient.sqls.getFirst().contains("`amp_xxl_job`.`xxl_job_info` i"));
        assertTrue(queryClient.sqls.getFirst().contains("`amp_xxl_job`.`xxl_job_log` l"));
        assertEquals(1, countOccurrences(queryClient.sqls.getFirst(), "FROM `amp_xxl_job`.`xxl_job_log` l"));
        assertTrue(queryClient.sqls.getFirst().contains("SUM(CASE WHEN l.handle_code = 200 THEN 1 ELSE 0 END)"));
        assertTrue(queryClient.sqls.getFirst().contains("MAX(l.trigger_time) AS reportUpdatedAt"));
        assertTrue(queryClient.sqls.getFirst().contains("l.trigger_time >= '"));
        assertTrue(queryClient.sqls.getFirst().contains("l.trigger_time < '"));
    }

    @Test
    void listExecutors_shouldReadExecutorOptionsFromConfiguredXxlJobDatabase() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(
                Map.of("appName", "premium-job", "title", "保费分期任务"),
                Map.of("appName", "premium-settle", "title", "保费结算任务")
        ));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        List<XxlJobExecutorResponse> executors = collector.listExecutors("保费分期", "prod", "amp_xxl_job");

        assertEquals(2, executors.size());
        assertEquals("premium-job", executors.getFirst().appName());
        assertEquals("保费分期任务", executors.getFirst().title());
        assertTrue(queryClient.sqls.getFirst().contains("FROM `amp_xxl_job`.`xxl_job_group`"));
        assertTrue(queryClient.sqls.getFirst().contains("app_name AS appName"));
        assertEquals("保费分期", queryClient.businessLineCodes.getFirst());
        assertEquals("prod", queryClient.environmentCodes.getFirst());
    }

    @Test
    void collect_shouldReadStatisticsAndFailedJobsFromConfiguredDatabaseName() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999,10.0.0.2:9999",
                "jobCount", 8,
                "enabledJobCount", 6,
                "disabledJobCount", 2,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 4,
                "triggerFailedCount", 1,
                "reportUpdatedAt", "2026-06-02 13:40:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 26)));
        queryClient.nextRows(List.of(failedJobRow()));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobDashboardResponse dashboard = collector.collect(monitor(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 2), 2, 20);

        assertEquals("ERROR", dashboard.status());
        assertEquals(2, dashboard.executorCount());
        assertEquals(8, dashboard.jobCount());
        assertEquals(6, dashboard.enabledJobCount());
        assertEquals(2, dashboard.disabledJobCount());
        assertEquals(26, dashboard.failedJobCount());
        assertEquals(2, dashboard.failedJobPage());
        assertEquals(20, dashboard.failedJobPageSize());
        assertEquals(5, dashboard.triggerCount());
        assertEquals(1, dashboard.triggerFailedCount());
        assertEquals(1, dashboard.failedJobs().size());
        assertEquals("EXECUTION_FAILED", dashboard.failedJobs().getFirst().failureType());
        assertEquals("执行失败", dashboard.failedJobs().getFirst().failureTypeName());
        assertEquals(200, dashboard.failedJobs().getFirst().triggerCode());
        assertEquals("调度成功", dashboard.failedJobs().getFirst().triggerMsg());
        assertEquals("张三", dashboard.failedJobs().getFirst().author());
        assertEquals("settleJobHandler", dashboard.failedJobs().getFirst().executorHandler());
        assertTrue(queryClient.sqls.get(0).contains("`amp_xxl_job`.`xxl_job_info`"));
        assertTrue(queryClient.sqls.get(0).contains("`amp_xxl_job`.`xxl_job_log_report`"));
        assertTrue(queryClient.sqls.get(0).contains("trigger_day >= '2026-05-01'"));
        assertTrue(queryClient.sqls.get(0).contains("trigger_day < '2026-06-03'"));
        assertTrue(queryClient.sqls.get(1).contains("COUNT(1) AS failedJobTotal"));
        assertTrue(queryClient.sqls.get(2).contains("`amp_xxl_job`.`xxl_job_log`"));
        assertTrue(queryClient.sqls.get(2).contains("i.author AS author"));
        assertTrue(queryClient.sqls.get(2).contains("l.trigger_code AS triggerCode"));
        assertTrue(queryClient.sqls.get(2).contains("l.trigger_msg AS triggerMsg"));
        assertTrue(queryClient.sqls.get(2).contains("trigger_time >= '"));
        assertTrue(queryClient.sqls.get(2).contains("LIMIT 20 OFFSET 20"));
        assertTrue(!queryClient.sqls.get(2).contains("FROM `amp_xxl_job`.`xxl_job_log`\n                  GROUP BY job_id"));
    }

    @Test
    void collect_shouldReadAllRawLogsWhenLogStatusIsAll() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 3,
                "enabledJobCount", 3,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 20,
                "triggerFailedCount", 2,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 18)));
        queryClient.nextRows(List.of(successJobRow()));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobDashboardResponse dashboard = collector.collect(
                monitor(),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 2),
                1,
                20,
                null,
                null,
                LOG_STATUS_ALL
        );

        assertEquals(18, dashboard.failedJobCount());
        assertEquals("XXL-JOB 区间日志数：18", dashboard.message());
        assertEquals("SUCCESS", dashboard.failedJobs().getFirst().failureType());
        assertEquals("执行成功", dashboard.failedJobs().getFirst().failureTypeName());
        assertTrue(!queryClient.sqls.get(1).contains("l.handle_code <> 200"));
        assertTrue(!queryClient.sqls.get(1).contains("NOT (l.trigger_code IN (0, 200) AND l.handle_code = 0)"));
        assertTrue(!queryClient.sqls.get(2).contains("l.handle_code <> 200"));
        assertTrue(!queryClient.sqls.get(2).contains("NOT (l.trigger_code IN (0, 200) AND l.handle_code = 0)"));
    }

    @Test
    void collectLogs_shouldReadRawLogsWithoutCollectingSummary() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 1, "activityCount", 1, "latestActivityAt", "2026-06-02 09:01:00")));
        queryClient.nextRows(List.of(successJobRow()));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobLogPageResponse response = collector.collectLogs(
                monitor(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 9),
                1,
                20,
                "张",
                "premium",
                LOG_STATUS_ALL
        );

        assertEquals(1, response.total());
        assertEquals(1, response.logs().size());
        assertEquals("SUCCESS", response.logs().getFirst().failureType());
        assertEquals(1, queryClient.sqls.size());
        assertTrue(queryClient.sqls.getFirst().contains("COUNT(1) AS failedJobTotal"));
        assertTrue(queryClient.sqls.getFirst().contains("FROM `amp_xxl_job`.`xxl_job_log` l"));
        assertTrue(queryClient.sqls.getFirst().contains("ORDER BY l.trigger_time DESC, l.id DESC"));
        assertTrue(!queryClient.sqls.getFirst().contains("xxl_job_log_report"));
        assertTrue(!queryClient.sqls.getFirst().contains("xxl_job_log_report"));
        assertTrue(queryClient.sqls.getFirst().contains("i.author LIKE '%张%'"));
        assertTrue(queryClient.sqls.getFirst().contains("g.app_name LIKE '%premium%'"));
    }

    @Test
    void collect_shouldClassifyScheduleFailureWhenTriggerCodeIsNotSuccess() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 1,
                "enabledJobCount", 1,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 0,
                "triggerFailedCount", 1,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 1)));
        Map<String, Object> failedJob = failedJobRow();
        failedJob.put("triggerCode", 500);
        failedJob.put("triggerMsg", "执行器地址为空");
        failedJob.put("handleCode", 0);
        queryClient.nextRows(List.of(failedJob));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobDashboardResponse dashboard = collector.collect(monitor());

        assertEquals("SCHEDULE_FAILED", dashboard.failedJobs().getFirst().failureType());
        assertEquals("调度失败", dashboard.failedJobs().getFirst().failureTypeName());
    }

    @Test
    void collect_shouldUseLatestSevenCalendarDaysByDefault() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 1,
                "enabledJobCount", 1,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 1,
                "triggerFailedCount", 0,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 0)));
        queryClient.nextRows(List.of());
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);
        LocalDate today = LocalDate.now();

        collector.collect(monitor());

        String startDate = today.minusDays(6).toString();
        String endExclusiveDate = today.plusDays(1).toString();
        assertTrue(queryClient.sqls.get(0).contains("trigger_day >= '" + startDate + "'"));
        assertTrue(queryClient.sqls.get(0).contains("trigger_day < '" + endExclusiveDate + "'"));
        assertTrue(queryClient.sqls.get(1).contains("l.trigger_time >= '" + startDate + " 00:00:00'"));
        assertTrue(queryClient.sqls.get(1).contains("l.trigger_time < '" + endExclusiveDate + " 00:00:00'"));
        assertTrue(queryClient.sqls.get(2).contains("l.trigger_time >= '" + startDate + " 00:00:00'"));
        assertTrue(queryClient.sqls.get(2).contains("l.trigger_time < '" + endExclusiveDate + " 00:00:00'"));
    }

    @Test
    void collect_shouldReadRawFailedLogsInDateRangeWithoutGroupingByJob() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 3,
                "enabledJobCount", 3,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 20,
                "triggerFailedCount", 2,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 1)));
        queryClient.nextRows(List.of(failedJobRow()));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobDashboardResponse dashboard = collector.collect(monitor(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 2));

        assertEquals(1, dashboard.failedJobs().size());
        assertTrue(queryClient.sqls.get(2).contains("l.trigger_time >= '2026-05-01 00:00:00'"));
        assertTrue(queryClient.sqls.get(2).contains("l.trigger_time < '2026-06-03 00:00:00'"));
        assertTrue(queryClient.sqls.get(2).contains("FROM `amp_xxl_job`.`xxl_job_log` l"));
        assertTrue(queryClient.sqls.get(2).contains("ORDER BY l.trigger_time DESC, l.id DESC"));
        assertTrue(!queryClient.sqls.get(2).contains("GROUP BY job_id"));
        assertTrue(!queryClient.sqls.get(2).contains("latest_log_id"));
    }

    @Test
    void collect_shouldFilterFailedLogsByConfiguredExecutorAppNames() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 3,
                "enabledJobCount", 3,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 20,
                "triggerFailedCount", 2,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 1)));
        queryClient.nextRows(List.of(failedJobRow()));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);
        OpsMonitorEntity monitor = monitor();
        monitor.setExecutorAppName("premium-job,premium-settle");

        collector.collect(monitor, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 2));

        assertTrue(queryClient.sqls.get(1).contains("g.app_name IN ('premium-job','premium-settle')"));
        assertTrue(queryClient.sqls.get(2).contains("g.app_name IN ('premium-job','premium-settle')"));
    }

    @Test
    void collect_shouldFilterFailedLogsByAuthorAndExecutorKeyword() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 3,
                "enabledJobCount", 3,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 20,
                "triggerFailedCount", 2,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 1)));
        queryClient.nextRows(List.of(failedJobRow()));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        collector.collect(monitor(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 2), 1, 20, "张", "premium");

        assertTrue(queryClient.sqls.get(1).contains("i.author LIKE '%张%'"));
        assertTrue(queryClient.sqls.get(1).contains("g.app_name LIKE '%premium%'"));
        assertTrue(queryClient.sqls.get(2).contains("i.author LIKE '%张%'"));
        assertTrue(queryClient.sqls.get(2).contains("g.app_name LIKE '%premium%'"));
    }

    @Test
    void collect_shouldTruncateLongHandleMessage() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 1,
                "enabledJobCount", 1,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 1,
                "triggerFailedCount", 1,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 1)));
        Map<String, Object> failedJob = failedJobRow();
        failedJob.put("handleMsg", "x".repeat(800));
        queryClient.nextRows(List.of(failedJob));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        XxlJobDashboardResponse dashboard = collector.collect(monitor());

        assertEquals(500, dashboard.failedJobs().getFirst().handleMsg().length());
    }

    @Test
    void check_shouldReportUpWhenNoLatestFailedJobsExist() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 3,
                "enabledJobCount", 3,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 3,
                "triggerFailedCount", 0,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 0)));
        queryClient.nextRows(List.of());
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        OpsMonitorCheckResponse response = collector.check(monitor());

        assertEquals("UP", response.status());
        assertEquals("XXL-JOB 区间内无失败日志", response.message());
    }

    @Test
    void collectLogs_shouldExecuteCountAndPageSqlInSingleBatch() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 1, "activityCount", 3, "latestActivityAt", "2026-06-02 09:01:00")));
        queryClient.nextRows(List.of(failedJobRow()));
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        collector.collectLogs(monitor(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 9), 1, 20, null, null, "FAILED");

        assertEquals(1, queryClient.batchCount);
        assertEquals(1, queryClient.sqls.size());
    }

    @Test
    void collect_shouldExecuteSummaryCountAndPageSqlInSingleBatch() {
        FakeXxlJobDatabaseQueryClient queryClient = new FakeXxlJobDatabaseQueryClient();
        queryClient.nextRows(List.of(Map.of(
                "executorRegistryList", "10.0.0.1:9999",
                "jobCount", 1,
                "enabledJobCount", 1,
                "disabledJobCount", 0,
                "triggerRunningCount", 0,
                "triggerSuccessCount", 1,
                "triggerFailedCount", 0,
                "reportUpdatedAt", "2026-06-02 09:00:00"
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 0)));
        queryClient.nextRows(List.of());
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        collector.collect(monitor());

        assertEquals(1, queryClient.batchCount);
        assertEquals(3, queryClient.sqls.size());
    }

    @Test
    void missingEmptyMultirowAndInvalidAggregateCountsCannotReportHealthy() {
        for (var rows : List.<List<Map<String, Object>>>of(List.of(), List.of(Map.of()),
                List.of(Map.of("jobCount", 0), Map.of("jobCount", 0)))) {
            var client = new FakeXxlJobDatabaseQueryClient(); client.nextRows(rows);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new XxlJobMonitorCollector(client).collectSummary(monitor()));
        }
        for (Object invalid : List.of(-1, "not-a-number", "1.2", "2147483648")) {
            var client = new FakeXxlJobDatabaseQueryClient();
            client.nextRows(List.of(Map.of("triggerRunningCount", invalid)));
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> new XxlJobMonitorCollector(client).collectSummary(monitor()));
        }
    }

    @Test
    void zeroFailuresWithActualActivityCarriesFullFixedExecutionEvidence() {
        var client = new FakeXxlJobDatabaseQueryClient();
        client.nextRows(List.of(Map.of("failedJobTotal", 0, "activityCount", 4, "latestActivityAt", "2026-06-02 09:02:00")));
        client.nextRows(List.of());
        var result = new XxlJobMonitorCollector(client).collectLogs(monitor(), LocalDate.of(2026,6,1), LocalDate.of(2026,6,9), 1,20,null,null,"FAILED");
        assertEquals("UP", result.status()); assertEquals(0, result.logs().size());
        var evidence = result.executionEvidence(); org.junit.jupiter.api.Assertions.assertNotNull(evidence);
        assertEquals("支付", evidence.definition().businessLineCode()); assertEquals("prod", evidence.definition().environmentCode());
        assertEquals(4, evidence.activityCount()); assertTrue(evidence.completePage()); assertEquals(1, evidence.queryHashes().size());
        assertEquals("Asia/Shanghai", evidence.recordTimeZone());
        assertTrue(client.sqls.getFirst().contains("COUNT(CASE WHEN")); assertTrue(client.sqls.getFirst().contains("latestActivityAt"));
        assertEquals(1, client.batchCount);
    }

    @Test
    void noActivityRemainsExplicitInsteadOfInventingDataTime() {
        var client = new FakeXxlJobDatabaseQueryClient(); var statistics = new LinkedHashMap<String,Object>();
        statistics.put("failedJobTotal",0); statistics.put("activityCount",0); statistics.put("latestActivityAt",null);
        client.nextRows(List.of(statistics)); client.nextRows(List.of());
        var result = new XxlJobMonitorCollector(client).collectLogs(monitor(), LocalDate.of(2026,6,1),LocalDate.of(2026,6,9),1,20,null,null,"FAILED");
        assertEquals(0,result.executionEvidence().activityCount()); org.junit.jupiter.api.Assertions.assertNull(result.executionEvidence().latestActivityAt());
    }

    @Test
    void partialPageAndDuplicateIdsCannotAppearComplete() {
        var client = new FakeXxlJobDatabaseQueryClient();
        client.nextRows(List.of(Map.of("failedJobTotal", 2, "activityCount", 3, "latestActivityAt","2026-06-02 09:02:00")));
        client.nextRows(List.of(failedJobRow()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new XxlJobMonitorCollector(client).collectLogs(monitor(),LocalDate.of(2026,6,1),LocalDate.of(2026,6,9),1,20,null,null,"FAILED"));
        var duplicate = new FakeXxlJobDatabaseQueryClient();
        duplicate.nextRows(List.of(Map.of("failedJobTotal", 2, "activityCount", 3, "latestActivityAt","2026-06-02 09:02:00")));
        duplicate.nextRows(List.of(failedJobRow(),failedJobRow()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new XxlJobMonitorCollector(duplicate).collectLogs(monitor(),LocalDate.of(2026,6,1),LocalDate.of(2026,6,9),1,20,null,null,"FAILED"));
    }

    @Test
    void completeLaterPageStillDoesNotClaimWholeQueryCoverage() {
        var client = new FakeXxlJobDatabaseQueryClient();
        client.nextRows(List.of(Map.of("failedJobTotal", 2, "activityCount", 3, "latestActivityAt","2026-06-02 09:02:00")));
        client.nextRows(List.of(failedJobRow()));
        var result = new XxlJobMonitorCollector(client).collectLogs(monitor(),LocalDate.of(2026,6,1),LocalDate.of(2026,6,9),2,1,null,null,"FAILED");
        assertEquals(1,result.logs().size()); org.junit.jupiter.api.Assertions.assertFalse(result.executionEvidence().completePage());
    }

    @Test
    void actualSqlReturnsConsistentCountsActivityAndLogsIncludingEmptyMatch() throws Exception {
        try (var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL")) {
            try (var statement=connection.createStatement()) {
                for (String ddl:List.of("CREATE SCHEMA amp_xxl_job",
                        "CREATE TABLE amp_xxl_job.xxl_job_group(id BIGINT,app_name VARCHAR(50))",
                        "CREATE TABLE amp_xxl_job.xxl_job_info(id BIGINT,job_group BIGINT,job_desc VARCHAR(50),author VARCHAR(50),executor_handler VARCHAR(50),trigger_status INT)",
                        "CREATE TABLE amp_xxl_job.xxl_job_log(id BIGINT,job_id BIGINT,trigger_time TIMESTAMP,trigger_code INT,trigger_msg VARCHAR(50),handle_time TIMESTAMP,handle_code INT,handle_msg VARCHAR(50))",
                        "INSERT INTO amp_xxl_job.xxl_job_group VALUES(1,'worker')",
                        "INSERT INTO amp_xxl_job.xxl_job_info VALUES(1,1,'job','owner','handler',1)",
                        "INSERT INTO amp_xxl_job.xxl_job_log VALUES(1,1,'2026-06-02 09:00:00',200,'ok','2026-06-02 09:01:00',200,'ok'),(2,1,'2026-06-02 09:02:00',200,'ok','2026-06-02 09:03:00',500,'failed')")) statement.execute(ddl);
            }
            XxlJobDatabaseQueryClient client=new XxlJobDatabaseQueryClient() {
                public List<Map<String,Object>> runRows(String line,String environment,String sql) { throw new AssertionError(); }
                public VerifiedBatch runVerifiedBatch(String line,String environment,List<String> sqls) {
                    assertEquals(1,sqls.size()); var rows=new ArrayList<Map<String,Object>>();
                    try (var statement=connection.createStatement();var rs=statement.executeQuery(sqls.getFirst())) {
                        var metadata=rs.getMetaData();
                        while(rs.next()) { Map<String,Object> row=new LinkedHashMap<>(); for(int i=1;i<=metadata.getColumnCount();i++) row.put(metadata.getColumnLabel(i),rs.getObject(i)); rows.add(row); }
                    } catch(java.sql.SQLException ex) { throw new IllegalStateException(ex); }
                    var at=java.time.Instant.parse("2026-06-02T02:00:00Z");
                    return new VerifiedBatch("a".repeat(64),at,at,List.of(rows));
                }
            };
            var collector=new XxlJobMonitorCollector(client); var from=LocalDate.of(2026,6,1); var to=LocalDate.of(2026,6,2);
            var failed=collector.collectLogs(monitor(),from,to,1,20,null,null,"FAILED");
            assertEquals(1,failed.total()); assertEquals(2,failed.executionEvidence().activityCount());
            assertEquals(2,failed.logs().getFirst().logId()); assertEquals("ERROR",failed.status());
            assertEquals(java.time.LocalDateTime.parse("2026-06-02T09:03:00"),failed.executionEvidence().latestActivityAt());
            try(var statement=connection.createStatement()) { statement.executeUpdate("UPDATE amp_xxl_job.xxl_job_log SET handle_code=200 WHERE id=2"); }
            var healthy=collector.collectLogs(monitor(),from,to,1,20,null,null,"FAILED");
            assertEquals(0,healthy.total()); assertEquals(0,healthy.logs().size()); assertEquals(2,healthy.executionEvidence().activityCount());
            assertTrue(healthy.executionEvidence().completePage()); assertEquals("UP",healthy.status());
            var unknown=collector.collectLogs(monitor(),from,to,1,20,null,"unknown","FAILED");
            assertEquals(0,unknown.executionEvidence().activityCount()); org.junit.jupiter.api.Assertions.assertNull(unknown.executionEvidence().latestActivityAt());
        }
    }

    private OpsMonitorEntity monitor() {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(7L);
        entity.setEnabled(true);
        entity.setMonitorType("XXL_JOB");
        entity.setMonitorKey("pay-prod-xxljob");
        entity.setBusinessLineCode("支付");
        entity.setEnvironmentCode("prod");
        entity.setName("支付生产 XXL-JOB");
        entity.setXxlJobDatabaseName("amp_xxl_job");
        return entity;
    }

    private Map<String, Object> failedJobRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("executorAppName", "pay-job-executor");
        row.put("jobId", 31);
        row.put("jobDesc", "结算任务");
        row.put("author", "张三");
        row.put("executorHandler", "settleJobHandler");
        row.put("triggerStatus", 1);
        row.put("logId", 9921);
        row.put("triggerTime", "2026-06-02 09:00:00");
        row.put("triggerCode", 200);
        row.put("triggerMsg", "调度成功");
        row.put("handleTime", "2026-06-02 09:01:00");
        row.put("handleCode", 500);
        row.put("handleMsg", "timeout");
        return row;
    }

    private Map<String, Object> successJobRow() {
        Map<String, Object> row = failedJobRow();
        row.put("handleCode", 200);
        row.put("handleMsg", "success");
        return row;
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static class FakeXxlJobDatabaseQueryClient implements XxlJobDatabaseQueryClient {
        private final List<List<Map<String, Object>>> queuedRows = new ArrayList<>();
        private final List<String> sqls = new ArrayList<>();
        private final List<String> businessLineCodes = new ArrayList<>();
        private final List<String> environmentCodes = new ArrayList<>();
        private int batchCount;

        void nextRows(List<Map<String, Object>> rows) {
            queuedRows.add(rows);
        }

        @Override
        public List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql) {
            businessLineCodes.add(businessLineCode);
            environmentCodes.add(environmentCode);
            sqls.add(sql);
            return queuedRows.removeFirst();
        }

        @Override
        public VerifiedBatch runVerifiedBatch(String line, String environment, List<String> statements) {
            var started = java.time.Instant.parse("2026-06-02T02:00:00Z");
            batchCount++; sqls.addAll(statements); businessLineCodes.add(line); environmentCodes.add(environment);
            var statistics = queuedRows.removeFirst().getFirst(); var page = queuedRows.removeFirst();
            var combined = new ArrayList<Map<String,Object>>();
            if (page.isEmpty()) { var row = new LinkedHashMap<String,Object>(statistics); row.put("logId",null); combined.add(row); }
            else for (var detail : page) { var row=new LinkedHashMap<String,Object>(statistics); row.putAll(detail); combined.add(row); }
            return new VerifiedBatch("a".repeat(64), started, started.plusSeconds(1), List.of(combined));
        }

        @Override
        public List<List<Map<String, Object>>> runRowsBatch(String businessLineCode, String environmentCode, List<String> sqls) {
            batchCount++;
            return XxlJobDatabaseQueryClient.super.runRowsBatch(businessLineCode, environmentCode, sqls);
        }
    }
}
