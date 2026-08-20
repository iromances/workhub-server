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
                "triggerFailedCount", 2
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
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 18)));
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

        assertEquals(18, response.total());
        assertEquals(1, response.logs().size());
        assertEquals("SUCCESS", response.logs().getFirst().failureType());
        assertEquals(2, queryClient.sqls.size());
        assertTrue(queryClient.sqls.getFirst().contains("COUNT(1) AS failedJobTotal"));
        assertTrue(queryClient.sqls.getFirst().contains("FROM `amp_xxl_job`.`xxl_job_log` l"));
        assertTrue(queryClient.sqls.get(1).contains("ORDER BY l.trigger_time DESC, l.id DESC"));
        assertTrue(!queryClient.sqls.getFirst().contains("xxl_job_log_report"));
        assertTrue(!queryClient.sqls.get(1).contains("xxl_job_log_report"));
        assertTrue(queryClient.sqls.getFirst().contains("i.author LIKE '%张%'"));
        assertTrue(queryClient.sqls.get(1).contains("g.app_name LIKE '%premium%'"));
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
                "triggerFailedCount", 1
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
                "triggerFailedCount", 0
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
                "triggerFailedCount", 2
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
                "triggerFailedCount", 2
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
                "triggerFailedCount", 2
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
                "triggerFailedCount", 1
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
                "triggerFailedCount", 0
        )));
        queryClient.nextRows(List.of(Map.of("failedJobTotal", 0)));
        queryClient.nextRows(List.of());
        XxlJobMonitorCollector collector = new XxlJobMonitorCollector(queryClient);

        OpsMonitorCheckResponse response = collector.check(monitor());

        assertEquals("UP", response.status());
        assertEquals("XXL-JOB 区间内无失败日志", response.message());
    }

    private OpsMonitorEntity monitor() {
        OpsMonitorEntity entity = new OpsMonitorEntity();
        entity.setId(7L);
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
    }
}
