# Proposal：业务监测 MQ 消息监测接入

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-17f620d9-b7b7-43e4-b37b-29cee0711f6b |
| 需求名称 | 业务监测 MQ 消息监测接入 |
| 涉及仓库与功能域 | workhub-server：`ops` 监测链路（model/service/job/controller）+ 单测；workhub-web：运维-业务监测页面 MQ 标签页 |
| 所属项目或业务线 | 不适用（WorkHub 平台自身功能，业务线 BL000012-工作台） |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-17f620d9-b7b7-43e4-b37b-29cee0711f6b-业务监测MQ消息监测接入/` |
| 版本/状态 | v1 已实施（2026-11-12；页面联调与告警落地待部署后走查） |
| 目标系统 | WorkHub Server + WorkHub Web |
| 原始需求来源及版本 | 2026-11-12 用户会话：“MQ把业务监测里的MQ消息模块完善下，类似xxljob的实现方式，不过数据库更细粒度，因为MQ的消息是在各个子应用的数据库里的message_sender_log和message_receiver_log” |
| 目标环境/分支 | workhub-server `codex/development-20261102`；workhub-web 当前开发分支 |
| 负责人/评审人 | 用户 |

## 1. Why：背景与问题

业务监测页面已具备完整 XXL-JOB 监测链路（`ops_monitor_config` 配置 → `XxlJobMonitorCollector` 拼 SQL → `McpXxlJobDatabaseQueryClient` 按业务线+环境解析 MCP 数据库目标只读查询 → dashboard/日志详情/`XxlJobFailureMonitorJob` 定时告警）。MQ 模块仅为占位骨架：

1. 后端 `OpsMonitorService` 在 `check`、`xxlJobDetail`、`xxlJobLogs`、`toEntity` 四处对 MQ 类型抛“MQ 监测…下一步接入”。
2. `ops_monitor_config` 的 `mq_topic`/`mq_consumer_group`/`mq_lag_threshold` 三列为空字段，从未使用。
3. 前端 `OpsMonitorView.vue` MQ 标签页为空 `el-empty` 占位。

MQ 消息真实来源已核验（db-prod 只读查询）：各子应用数据库 schema 普遍存在 `message_sender_log`（发送，status 1=未发送 2=发送中 3=失败 4=成功）与 `message_receiver_log`（接收，status 0=签收失败 1=签收成功，另有 `deal_business_success` 业务处理成功标志），两表语义来自业务公共组件 lease-mq/thctay-mq 枚举。一个业务线对应多个子应用库（db-prod 上约 28 个 schema 分布），比 XXL-JOB 的单一调度库粒度更细。不接入则 MQ 消息发送/接收失败与积压无统一监测入口，只能逐库人工排查。

## 2. What：目标与范围

### 本期目标

1. MQ 监测配置可创建、编辑、检测、删除，与 XXL-JOB 同一 `ops_monitor_config` 表、同一套页面权限（`ops:monitor:*`）。
2. 运行时自动发现监测范围内全部子应用库并逐库统计发送/接收总量、成功、失败、积压。
3. 提供失败/积压消息明细分页查询（带执行证据），与 XXL-JOB 日志详情同等凭据纪律。
4. 定时告警与手动检测同口径，按业务线成员发系统通知并去重。

### 已确认口径（2026-11-12 用户确认）

1. 库粒度：自动发现全部库。监测配置不填库名，运行时按业务线+环境解析 MCP 数据库目标后查 `information_schema.tables` 发现所有含 `message_sender_log` 的 schema，schema 名校验 `[A-Za-z0-9_]+`。
2. 异常口径：发送失败 `status=3`；发送积压 `status IN (1,2)` 且 `create_time` 早于 `now - N 分钟`；接收失败 `status=0` 或 `deal_business_success=0`。状态判定 `SUM(CASE WHEN …)`，明细含四种 failureType：`SEND_FAILED`/`SEND_STUCK`/`RECEIVE_ACK_FAILED`/`RECEIVE_BUSINESS_FAILED`。
3. 字段复用，无 DDL：`mq_topic`→关注交换机（exchange，逗号分隔，可空=全部）、`mq_consumer_group`→关注队列（queue，逗号分隔，仅接收侧）、`mq_lag_threshold`→积压阈值分钟（默认 30）。统计窗口默认 7 天，与 XXL-JOB 一致。
4. 状态汇总：接收失败或发送失败 >0 → `ERROR`；仅积压 >0 → `WARN`；否则 `UP`。
5. 告警本期实现：`MqFailureAlertService` + `MqFailureMonitorJob`（`@Scheduled(cron = "${workhub.ops.mq.failure-monitor.cron:0 */10 * * * *}")`），dedupeKey `MQ:{monitorId}:{db}:{logType}:{logId}`，通知类型 `MQ_MESSAGE_FAILED`。

### 本期交付范围

1. workhub-server：model 新增 Mq 系列 record；`MqMonitorCollector`；`OpsMonitorService` MQ 分发与校验；`MqFailureAlertService`；workhub-job `MqFailureMonitorJob`；controller 三个只读接口；单测。
2. workhub-web：`types/ops.ts`、`api/ops.ts` 补 MQ 契约；`OpsMonitorView.vue` MQ 标签页（列表、抽屉、检测、按库统计与明细分页弹窗）。
3. 本仓库 `docs/业务线/BL000012-工作台/` 现状与迭代日志更新。

### 明确不做

1. 不做 RabbitMQ 管理端（Management API）采集、队列堆积（真实 lag）监测。
2. 不做 MQ 监测的 Agent MCP 工具接入（xxljob 的 `xxlJobLogsInScope` Agent 链路暂不镜像）。
3. 不新增 `ops_monitor_config` 列与迁移，不改动 MCP 数据库目标与只读守卫。

### 约束与依赖

- 依赖 MCP 数据库目标配置（`McpResourceService.catalog()`）与 readonly profile 可连通目标库。
- 只读查询受 `SqlPolicyGuard` 单语句、无注释、危险关键字拦截与 maxRows/字节上限约束；统计/明细 SQL 需显式 `LIMIT`，别名避开 `create/set` 等危险字。
- 测试不依赖真实网络（Fake QueryClient 模式）。

## 3. 验收标准

1. `mvn test` 全量通过，新增 `MqMonitorCollectorTest`、`MqFailureAlertServiceTest`、`OpsMonitorServiceTest` MQ 分支覆盖。
2. workhub-web 构建通过，MQ 标签页可完成配置 CRUD、手动检测、按库统计与明细分页。
3. 真实链路抽查：对一条已配置业务线的 MQ 监测执行手动检测返回统计；collector 生成的 SQL 口径与 MCP readonly 手工结果一致。
