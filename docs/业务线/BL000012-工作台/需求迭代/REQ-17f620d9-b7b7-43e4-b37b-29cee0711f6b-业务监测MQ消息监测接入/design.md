# Design：业务监测 MQ 消息监测接入

承接 [proposal](proposal.md)。镜像 XXL-JOB 监测链路（`OpsMonitorService` → `XxlJobMonitorCollector` → `XxlJobDatabaseQueryClient` → MCP `DatabaseDiagnosticService`），数据源换成各子应用库的 `message_sender_log` / `message_receiver_log`。

> **v1.1（2026-11-12 实施中补充）**：读取端硬约束实测确认——readonly profile `maxRows=100`（JDBC `setMaxRows` 硬截断，无截断标志）、`queryTimeoutSeconds=10`、单条 SQL 失败整批中止；db-prod 上 `th_saps.message_sender_log` 约 4600 万行（status 索引范围 556 万行）、`th_payment` 约 3500 万行无二级索引，实测单库 7 天窗口统计超过 60 秒必然超时。原"全库单条 UNION 语句"设计在 PROD 不可用，改为**逐库隔离执行**：统计与明细均按库单独下发，单个库超时/失败只降级该库（`MqDatabaseStatsResponse.errorMessage` 标注，面板状态 WARN 并点名），其余库正常出数；分页为各库取前 `fetchDepth`（= 分页终点，上限 99）前缀后 Java 归并切页，深度不足时明确报错不静默缺页；`MAX_PAGE_SIZE=50`；明细路径（含告警）任一库降级则整体失败（分页完整性与执行凭据优先于部分结果）。

## 1. 数据源契约（已核验）

- `message_sender_log`：`id`、`message_id`、`exchange`、`routing_key`、`message`、`need_retry_count`、`real_retry_count`、`send_time`、`status`(tinyint)、`error_msg`、`create_time`。status：1=未发送 2=发送中 3=失败 4=成功。
- `message_receiver_log`：`id`、`message_id`、`exchange`、`queue`、`routing_key`、`message`、`receiver_time`、`status`(tinyint)、`error_msg`、`deal_business_success`、`auto_retry`、`create_time`。status：0=签收失败 1=签收成功。
- 库发现：`SELECT table_schema FROM information_schema.tables WHERE table_name = 'message_sender_log'`；结果值校验 `[A-Za-z0-9_]+` 后以反引号引用嵌入 SQL。

## 2. SQL 形态

统一模板（占位符在 Java 侧格式化，全部字面量先过校验/转义）：

```sql
SELECT '{db}' AS databaseName,
  COUNT(1) AS sendTotalCount,
  SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END) AS sendFailedCount,
  SUM(CASE WHEN s.status IN (1, 2) AND s.create_time < '{stuckBefore}' THEN 1 ELSE 0 END) AS sendPendingCount,
  SUM(CASE WHEN s.status = 4 THEN 1 ELSE 0 END) AS sendSuccessCount,
  MAX(s.create_time) AS latestSendAt
FROM `{db}`.`message_sender_log` s
WHERE s.create_time >= '{start} 00:00:00' AND s.create_time < '{endExclusive} 00:00:00' {exchangeFilter}
UNION ALL
SELECT '{db}', COUNT(1),
  SUM(CASE WHEN r.status = 0 OR COALESCE(r.deal_business_success, 0) = 0 THEN 1 ELSE 0 END),
  0, 0,
  MAX(r.create_time)
FROM `{db}`.`message_receiver_log` r
WHERE r.create_time >= '{start} 00:00:00' AND r.create_time < '{endExclusive} 00:00:00' {exchangeFilter} {queueFilter}
```

- 全库统计：每库一组如上 UNION ALL 为单条语句（发现 N 库 → N 组，结果 N 行）；随分页明细语句一起经 `runVerifiedBatch` 成批执行。
- 明细分页：每库 sender/receiver 各一段 `UNION ALL`，外层 `ORDER BY occurredAt DESC, logId DESC LIMIT {pageSize} OFFSET {offset}`；总数语句同结构 `COUNT(*)`。`LIMIT/OFFSET` 显式写入（pageSize ≤ 200），避免守卫追加 `LIMIT maxRows` 截断分页。
- `stuckBefore = now - mqLagThreshold 分钟`（检测时刻计算）。exchange/queue 过滤：逗号分隔白名单 `[A-Za-z0-9_.-]+`，`IN (...)` 字面量；LIKE 关键词场景沿用 `escapeSqlLikeLiteral`。
- 别名避开危险关键字（`create`/`set` 等，`create_time` 因词边界安全）。

## 3. Java 结构

### workhub-model（`cn.aslight.workhub.model.ops`，record）

- `MqDatabaseStatsResponse(databaseName, sendTotalCount, sendSuccessCount, sendFailedCount, sendPendingCount, receiveTotalCount, receiveSuccessCount, receiveFailedCount, latestActivityAt)`
- `MqDashboardResponse(id, monitorKey, businessLineCode, environmentCode, name, status, message, checkedAt, databaseCount, sendTotalCount, sendSuccessCount, sendFailedCount, sendPendingCount, receiveTotalCount, receiveSuccessCount, receiveFailedCount, databaseStats, failedMessageCount, messagePage, messagePageSize, messages)`
- `MqMessageResponse(databaseName, logType, logId, messageId, exchange, routingKey, queue, status, failureType, failureTypeName, retryCount, autoRetry, dealBusinessSuccess, occurredAt, createTime, errorMsg)`；failureType：`SEND_FAILED`/`SEND_STUCK`/`RECEIVE_ACK_FAILED`/`RECEIVE_BUSINESS_FAILED`
- `MqMessagePageResponse(id, monitorKey, businessLineCode, environmentCode, name, status, message, checkedAt, total, page, pageSize, messages, executionEvidence)`
- `MqQueryEvidence`：与 `XxlJobQueryEvidence` 同形，`Definition` 中 `databaseName` 换为 `discoveredSchemas`（List），另含 `exchangeFilter`/`queueFilter`/`lagThresholdMinutes`/`logType`。

### workhub-service（`cn.aslight.workhub.service.ops`）

- 新增 `MqMonitorCollector`（构造注入 `XxlJobDatabaseQueryClient`，复用现有 MCP 客户端）：
  - `discoverDatabases(bl, env)` → `List<String>`（未配置目标/多目标报错与 xxljob 一致）
  - `collectSummary(monitor, start, end)` → `MqDashboardResponse`（status/message 口径见 proposal）
  - `collect(monitor, start, end, page, pageSize, logType, exchange, queue)` → 带明细分页的 dashboard
  - `collectMessages(...)` → `MqMessagePageResponse`：`runVerifiedBatch` 两语句（统计+分页），镜像 `collectLogs` 的回执完整性、统计/明细一致性校验与 `McpXxlJobDatabaseQueryClient.rows()` 严格要求，产出 `MqQueryEvidence`
  - `check(monitor)` → `OpsMonitorCheckResponse`（detail 含 databaseCount、discoveredDatabases、汇总计数）
- `OpsMonitorService`：
  - `toEntity`：MQ 时 monitorKey 默认 `{bl}:{env}:MQ`、name 默认 `{bl} {env} MQ 消息`；`xxlJobDatabaseName`/`adminBaseUrl`/`username`/`password` 置空不要求；`mqLagThreshold` 可空（null=默认 30，非空须 ≥1）；`mqTopic`/`mqConsumerGroup` 逗号分隔白名单校验；XXL_JOB 时维持现状清空 mq 三字段
  - `check(id)`、`mqDetail`/`mqMessages` 按 `monitorType` 分发；移除 4 处“下一步接入”
  - 新增 `mqDashboard(bl, env, enabledOnly)`（复用 `findAll("MQ", …)` 本地快照映射）、`mqDetail(id, start, end, page, …)`、`mqMessages(id, …)`
- 新增 `MqFailureAlertService`（镜像 `XxlJobFailureAlertService`）：扫描启用 MQ 监测 → `collect` → 逐条失败/积压消息告警；收件人 `userMapper.findBusinessLineMembers`，空则 `admin`；失败异常回写 `last_status=ERROR`。

### workhub-job / workhub-controller

- `MqFailureMonitorJob`：`@Scheduled(cron = "${workhub.ops.mq.failure-monitor.cron:0 */10 * * * *}")` 调 `scanAndAlert()`。
- `OpsMonitorController` 新增：`GET /api/ops/monitors/mq/dashboard`、`GET /api/ops/monitors/{id}/mq/detail`、`GET /api/ops/monitors/{id}/mq/messages`，权限沿用 `ops:monitor:view|check`。

## 4. 前端（workhub-web）

- `types/ops.ts`：`MqDatabaseStats`/`MqMessage`/`MqDashboard`/`MqMessagePage` + `OpsMonitor`/`OpsMonitorSaveRequest` 已有 mq 字段沿用。
- `api/ops.ts`：`fetchMqDashboard(params)`、`fetchMqMessages(id, params)`。
- `OpsMonitorView.vue` MQ 标签页：筛选（业务线/环境/仅启用）+ 列表（库数量、发送失败/积压、接收失败、状态、上次消息/时间、操作）+ 新增/编辑抽屉（mqTopic/mqConsumerGroup/mqLagThreshold，业务线/环境/启用/备注）+ 手动检测 + 详情弹窗（按库统计表格、日期窗口、logType/交换机/队列筛选、明细分页）。

## 5. 测试与回滚

- 单测（Fake Client，无真实网络）：`MqMonitorCollectorTest`（发现库 SQL、聚合口径、UNION ALL 形态、分页与一致性校验失败路径、exchange/queue 过滤）；`OpsMonitorServiceTest` 补 MQ CRUD 校验与分发；`MqFailureAlertServiceTest`（去重键、收件人、异常回写）。
- 验证：`mvn test`；workhub-web `npm run build`；真实抽查一条 MQ 监测手动检测。
- 回滚：本需求无 DDL、无配置迁移；回退分支即恢复原占位行为。
