package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.FuncCallSqlEntity;

import java.util.List;

/**
 * REQ-9f3c2a84：Function Call 场景 SQL 种子。模板文本取自改造前 XxlJobMonitorCollector /
 * MqMonitorCollector 的在用 SQL 文本块（%n$s/%n$d 逐位替换为 :name 命名参数，MQ 分支保持
 * stripTrailing 语义）；由 FuncCallSchemaInitializer 幂等回写，页面维护后以库内定义优先。
 * 本文件由生成脚本产出，请勿手改模板内容；新增场景请走页面登记或补充种子后重新校验等价性。
 */
public final class FuncCallSqlSeeds {

    private FuncCallSqlSeeds() {
    }

    public record Seed(String sqlCode, String scenarioCode, int branchOrder, String name, String description,
                       String sqlTemplate, String paramDesc, String sourceModule) {

        FuncCallSqlEntity toEntity() {
            FuncCallSqlEntity entity = new FuncCallSqlEntity();
            entity.setSqlCode(sqlCode());
            entity.setScenarioCode(scenarioCode());
            entity.setBranchOrder(branchOrder());
            entity.setName(name());
            entity.setDescription(description());
            entity.setSqlTemplate(sqlTemplate());
            entity.setParamDesc(paramDesc());
            entity.setSourceModule(sourceModule());
            entity.setEnabled(true);
            entity.setRemark("FuncCallSqlSeeds 初始种子");
            return entity;
        }
    }

    public static final List<Seed> SEEDS = List.of(
            new Seed("OPS_XXL_SUMMARY", "OPS-XXL-SUMMARY", 1, "XXL-JOB 全库汇总", "xxl_job_log_report 报表汇总（无执行器过滤）",
            """
            SELECT
              (SELECT COUNT(1) FROM :databaseName.`xxl_job_info`) AS jobCount,
              (SELECT COUNT(1) FROM :databaseName.`xxl_job_info` WHERE trigger_status = 1) AS enabledJobCount,
              (SELECT COUNT(1) FROM :databaseName.`xxl_job_info` WHERE trigger_status <> 1) AS disabledJobCount,
              (SELECT GROUP_CONCAT(address_list SEPARATOR ',') FROM :databaseName.`xxl_job_group` WHERE address_list IS NOT NULL AND address_list <> '') AS executorRegistryList,
              (SELECT COALESCE(SUM(running_count), 0) FROM :databaseName.`xxl_job_log_report` WHERE trigger_day >= ':startDate' AND trigger_day < ':endDateExclusive') AS triggerRunningCount,
              (SELECT COALESCE(SUM(suc_count), 0) FROM :databaseName.`xxl_job_log_report` WHERE trigger_day >= ':startDate' AND trigger_day < ':endDateExclusive') AS triggerSuccessCount,
              (SELECT COALESCE(SUM(fail_count), 0) FROM :databaseName.`xxl_job_log_report` WHERE trigger_day >= ':startDate' AND trigger_day < ':endDateExclusive') AS triggerFailedCount,
              (SELECT MAX(trigger_day) FROM :databaseName.`xxl_job_log_report` WHERE trigger_day >= ':startDate' AND trigger_day < ':endDateExclusive') AS reportUpdatedAt
            """,
                    "参数：databaseName, startDate, endDateExclusive；databaseName=反引号库名；startDate/endDateExclusive=trigger_day 窗口（含头不含尾，日期）", "OPS_XXL"),
            new Seed("OPS_XXL_FOCUSED_SUMMARY", "OPS-XXL-FOCUSED-SUMMARY", 1, "XXL-JOB 执行器聚焦汇总", "按执行器过滤的日志/任务汇总",
            """
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
              FROM :databaseName.`xxl_job_info` i
              JOIN :databaseName.`xxl_job_group` g ON g.id = i.job_group
              WHERE 1 = 1 :executorFilter
            ) job_stats
            CROSS JOIN (
              SELECT GROUP_CONCAT(g.address_list SEPARATOR ',') AS executorRegistryList
              FROM :databaseName.`xxl_job_group` g
              WHERE g.address_list IS NOT NULL AND g.address_list <> '' :executorFilter
            ) executor_stats
            CROSS JOIN (
              SELECT
                COALESCE(SUM(CASE WHEN l.handle_code = 0 AND l.trigger_code IN (0, 200) THEN 1 ELSE 0 END), 0) AS triggerRunningCount,
                COALESCE(SUM(CASE WHEN l.handle_code = 200 THEN 1 ELSE 0 END), 0) AS triggerSuccessCount,
                COALESCE(SUM(CASE WHEN l.handle_code <> 200
                                  AND NOT (l.trigger_code IN (0, 200) AND l.handle_code = 0)
                                  THEN 1 ELSE 0 END), 0) AS triggerFailedCount,
                MAX(l.trigger_time) AS reportUpdatedAt
              FROM :databaseName.`xxl_job_log` l
              JOIN :databaseName.`xxl_job_info` i ON i.id = l.job_id
              JOIN :databaseName.`xxl_job_group` g ON g.id = i.job_group
              WHERE l.trigger_time >= ':startDateTime'
                AND l.trigger_time < ':endExclusiveDateTime'
                :executorFilter
            ) log_stats
            """,
                    "参数：databaseName, executorFilter, startDateTime, endExclusiveDateTime；executorFilter=AND g.app_name IN (...) 片段；startDateTime/endExclusiveDateTime=yyyy-MM-dd 00:00:00", "OPS_XXL"),
            new Seed("OPS_XXL_HEALTH_STATS", "OPS-XXL-HEALTH-STATS", 1, "XXL-JOB 五态健康统计", "巡检用全库健康统计（成功/运行/卡死/执行失败/静默）",
            """
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
                COALESCE(SUM(CASE WHEN i.trigger_status = 1 AND (i.trigger_next_time IS NULL OR i.trigger_next_time < ':silentGraceCutoff') THEN 1 ELSE 0 END), 0) AS silentJobCount
              FROM :databaseName.`xxl_job_info` i
            ) job_stats
            CROSS JOIN (
              SELECT GROUP_CONCAT(g.address_list SEPARATOR ',') AS executorRegistryList
              FROM :databaseName.`xxl_job_group` g
              WHERE g.address_list IS NOT NULL AND g.address_list <> ''
            ) executor_stats
            CROSS JOIN (
              SELECT
                COALESCE(SUM(CASE WHEN l.trigger_code = 200 AND l.handle_code = 200 THEN 1 ELSE 0 END), 0) AS successCount,
                COALESCE(SUM(CASE WHEN l.trigger_code = 200 AND (l.handle_code = 0 OR l.handle_code IS NULL) AND l.trigger_time >= ':staleCutoff' THEN 1 ELSE 0 END), 0) AS runningCount,
                COALESCE(SUM(CASE WHEN l.trigger_code = 200 AND (l.handle_code = 0 OR l.handle_code IS NULL) AND l.trigger_time < ':staleCutoff' THEN 1 ELSE 0 END), 0) AS stuckCount,
                COALESCE(SUM(CASE WHEN l.trigger_code IS NULL OR l.trigger_code <> 200 OR l.handle_code NOT IN (0, 200) THEN 1 ELSE 0 END), 0) AS executionFailedCount,
                MAX(l.trigger_time) AS reportUpdatedAt
              FROM :databaseName.`xxl_job_log` l
              WHERE l.trigger_time >= ':startDate 00:00:00'
                AND l.trigger_time < ':endDateExclusive 00:00:00'
            ) log_stats
            """,
                    "参数：silentGraceCutoff, databaseName, staleCutoff, startDate, endDateExclusive；silentGraceCutoff=静默宽限线；staleCutoff=卡死阈值线；窗口为 trigger_time 日期段", "OPS_XXL"),
            new Seed("OPS_XXL_FAILED_COUNT", "OPS-XXL-FAILED-COUNT", 1, "XXL-JOB 失败明细计数", "失败/异常日志总数",
            """
            SELECT COUNT(1) AS failedJobTotal
            FROM :databaseName.`xxl_job_log` l
            JOIN :databaseName.`xxl_job_info` i ON i.id = l.job_id
            JOIN :databaseName.`xxl_job_group` g ON g.id = i.job_group
            WHERE l.trigger_time >= ':startDateTime'
              AND l.trigger_time < ':endExclusiveDateTime'
              :executorFilter
              :detailFilter
              :statusFilter
            """,
                    "参数：databaseName, startDateTime, endExclusiveDateTime, executorFilter, detailFilter, statusFilter；executorFilter/detailFilter/statusFilter=白名单片段，可为空串", "OPS_XXL"),
            new Seed("OPS_XXL_FAILED_PAGE", "OPS-XXL-FAILED-PAGE", 1, "XXL-JOB 失败明细分页", "失败/异常日志分页明细",
            """
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
            FROM :databaseName.`xxl_job_log` l
            JOIN :databaseName.`xxl_job_info` i ON i.id = l.job_id
            JOIN :databaseName.`xxl_job_group` g ON g.id = i.job_group
            WHERE l.trigger_time >= ':startDateTime'
              AND l.trigger_time < ':endExclusiveDateTime'
              :executorFilter
              :detailFilter
              :statusFilter
            ORDER BY l.trigger_time DESC, l.id DESC
            LIMIT :limit OFFSET :offset
            """,
                    "参数：databaseName, startDateTime, endExclusiveDateTime, executorFilter, detailFilter, statusFilter, limit, offset；limit/offset=分页数字；其余同失败明细计数", "OPS_XXL"),
            new Seed("OPS_XXL_SILENT_JOBS", "OPS-XXL-SILENT-JOBS", 1, "XXL-JOB 静默任务明细", "启用中但调度停摆的任务（前 20）",
            """
            SELECT
              g.app_name AS executorAppName,
              i.id AS jobId,
              i.job_desc AS jobDesc,
              i.author AS author,
              i.executor_handler AS executorHandler,
              i.trigger_next_time AS triggerNextTime
            FROM :databaseName.`xxl_job_info` i
            JOIN :databaseName.`xxl_job_group` g ON g.id = i.job_group
            WHERE i.trigger_status = 1
              AND (i.trigger_next_time IS NULL OR i.trigger_next_time < ':silentGraceCutoff')
            ORDER BY i.trigger_next_time ASC
            LIMIT 20
            """,
                    "参数：databaseName, silentGraceCutoff；silentGraceCutoff=静默宽限线时间字面量", "OPS_XXL"),
            new Seed("OPS_XXL_LOG_STATS", "OPS-XXL-LOG-STATS", 1, "XXL-JOB 日志页统计", "日志分页统计（单 SELECT 统计与明细共享读取视图）",
            """
            SELECT COUNT(:matching) AS failedJobTotal,
              COUNT(1) AS activityCount,
              MAX(GREATEST(l.trigger_time, COALESCE(l.handle_time, l.trigger_time))) AS latestActivityAt
            FROM :databaseName.`xxl_job_log` l
            JOIN :databaseName.`xxl_job_info` i ON i.id = l.job_id
            JOIN :databaseName.`xxl_job_group` g ON g.id = i.job_group
            WHERE l.trigger_time >= ':startDate 00:00:00'
              AND l.trigger_time < ':endDateExclusive 00:00:00'
              :executorFilter
              :detailFilter
            """,
                    "参数：matching, databaseName, startDate, endDateExclusive, executorFilter, detailFilter；matching=1 或 CASE WHEN 异常谓词片段", "OPS_XXL"),
            new Seed("OPS_XXL_EXECUTORS", "OPS-XXL-EXECUTORS", 1, "XXL-JOB 执行器清单", "xxl_job_group 执行器列表",
            """
            SELECT
              app_name AS appName,
              title AS title
            FROM :databaseName.`xxl_job_group`
            ORDER BY app_name ASC
            """,
                    "参数：databaseName；databaseName=反引号库名", "OPS_XXL"),
            new Seed("OPS_MQ_STATS_SENDER", "OPS-MQ-STATS", 1, "MQ 巡检统计·发送分支", "message_sender_log 窗口统计（发送侧）",
            """
            SELECT ':databaseLabel' AS databaseName,
              COUNT(1) AS sendTotalCount,
              COALESCE(SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END), 0) AS sendFailedCount,
              COALESCE(SUM(CASE WHEN s.status IN (1, 2) AND s.create_time < ':stuckLiteral' THEN 1 ELSE 0 END), 0) AS sendPendingCount,
              COALESCE(SUM(CASE WHEN s.status = 4 THEN 1 ELSE 0 END), 0) AS sendSuccessCount,
              0 AS receiveTotalCount,
              0 AS receiveSuccessCount,
              0 AS receiveFailedCount,
              MAX(s.create_time) AS latestActivityAt
            FROM :databaseName.`message_sender_log` s
            WHERE s.create_time >= ':windowFrom' AND s.create_time < ':windowTo':exchangeFilter""",
                    "参数：databaseLabel, databaseName, windowFrom, windowTo, stuckLiteral, exchangeFilter；databaseLabel=展示名原样字面量；databaseName=反引号库名；stuckLiteral=卡死阈值时间；exchangeFilter 可为空串", "OPS_MQ"),
            new Seed("OPS_MQ_STATS_RECEIVER", "OPS-MQ-STATS", 2, "MQ 巡检统计·接收分支", "message_receiver_log 窗口统计（接收侧）",
            """
            SELECT ':databaseLabel' AS databaseName,
              0 AS sendTotalCount,
              0 AS sendFailedCount,
              0 AS sendPendingCount,
              0 AS sendSuccessCount,
              COUNT(1) AS receiveTotalCount,
              COALESCE(SUM(CASE WHEN NOT (r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0) THEN 1 ELSE 0 END), 0) AS receiveSuccessCount,
              COALESCE(SUM(CASE WHEN r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0 THEN 1 ELSE 0 END), 0) AS receiveFailedCount,
              MAX(r.create_time) AS latestActivityAt
            FROM :databaseName.`message_receiver_log` r
            WHERE r.create_time >= ':windowFrom' AND r.create_time < ':windowTo':exchangeFilter:queueFilter""",
                    "参数：databaseLabel, windowFrom, windowTo, exchangeFilter, queueFilter, databaseName；exchangeFilter/queueFilter 可为空串", "OPS_MQ"),
            new Seed("OPS_MQ_MESSAGE_STATS_SENDER", "OPS-MQ-MESSAGE-STATS", 1, "MQ 消息统计·发送分支", "发送侧活动数/匹配数统计",
            """
            SELECT COUNT(1) AS activityCount,
              COALESCE(SUM(CASE WHEN :senderPredicate THEN 1 ELSE 0 END), 0) AS matchingCount,
              MAX(s.create_time) AS latestActivityAt
            FROM :databaseName.`message_sender_log` s
            WHERE s.create_time >= ':windowFrom' AND s.create_time < ':windowTo':exchangeFilter""",
                    "参数：databaseName, windowFrom, windowTo, senderPredicate, exchangeFilter；senderPredicate=异常判定片段（含卡死时间）", "OPS_MQ"),
            new Seed("OPS_MQ_MESSAGE_STATS_RECEIVER", "OPS-MQ-MESSAGE-STATS", 2, "MQ 消息统计·接收分支", "接收侧活动数/匹配数统计",
            """
            SELECT COUNT(1) AS activityCount,
              COALESCE(SUM(CASE WHEN :receiverPredicate THEN 1 ELSE 0 END), 0) AS matchingCount,
              MAX(r.create_time) AS latestActivityAt
            FROM :databaseName.`message_receiver_log` r
            WHERE r.create_time >= ':windowFrom' AND r.create_time < ':windowTo':exchangeFilter:queueFilter""",
                    "参数：databaseName, windowFrom, windowTo, receiverPredicate, exchangeFilter, queueFilter；receiverPredicate=异常判定片段", "OPS_MQ"),
            new Seed("OPS_MQ_MESSAGE_PAGE_SENDER", "OPS-MQ-MESSAGE-PAGE", 1, "MQ 消息分页·发送分支", "发送侧异常消息分页明细",
            """
            SELECT ':databaseLabel' AS databaseName, 'SENDER' AS logType, s.id AS logId, s.message_id AS messageId,
              s.exchange AS exchangeName, s.routing_key AS routingKey, '' AS queue, s.status AS status,
              s.real_retry_count AS retryCount, 0 AS autoRetry, NULL AS dealBusinessSuccess,
              COALESCE(s.send_time, s.create_time) AS occurredAt, s.create_time AS createTime, s.error_msg AS errorMsg
            FROM :databaseName.`message_sender_log` s
            WHERE s.create_time >= ':windowFrom' AND s.create_time < ':windowTo' AND :senderPredicate:exchangeFilter""",
                    "参数：databaseLabel, databaseName, windowFrom, windowTo, senderPredicate, exchangeFilter；同消息统计发送分支", "OPS_MQ"),
            new Seed("OPS_MQ_MESSAGE_PAGE_RECEIVER", "OPS-MQ-MESSAGE-PAGE", 2, "MQ 消息分页·接收分支", "接收侧异常消息分页明细",
            """
            SELECT ':databaseLabel' AS databaseName, 'RECEIVER' AS logType, r.id AS logId, r.message_id AS messageId,
              r.exchange AS exchangeName, r.routing_key AS routingKey, r.queue AS queue, r.status AS status,
              0 AS retryCount, COALESCE(r.auto_retry, 0) AS autoRetry, COALESCE(r.deal_business_success, 0) AS dealBusinessSuccess,
              COALESCE(r.receiver_time, r.create_time) AS occurredAt, r.create_time AS createTime, r.error_msg AS errorMsg
            FROM :databaseName.`message_receiver_log` r
            WHERE r.create_time >= ':windowFrom' AND r.create_time < ':windowTo' AND :receiverPredicate:exchangeFilter:queueFilter""",
                    "参数：databaseLabel, windowFrom, windowTo, receiverPredicate, exchangeFilter, queueFilter, databaseName；同消息统计接收分支", "OPS_MQ"),
            new Seed("OPS_MQ_DISCOVER_DATABASES", "OPS-MQ-DISCOVER-DATABASES", 1, "MQ 库发现", "按 message_sender_log 表定位业务线下的 MQ 子应用库",
            """
            SELECT table_schema AS databaseName FROM information_schema.tables
            WHERE table_name = 'message_sender_log'""",
                    "参数：无（REQ-b4d7e6f2 随执行收口由原内联 SQL 登记）", "OPS_MQ")
    );
}
