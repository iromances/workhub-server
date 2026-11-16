# Design：运维Job管理与FunctionCall统一SQL访问

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-9f3c2a84-6d1e-4b7a-b5c2-8e4a71d0f39b |
| 需求名称与唯一需求目录 | 运维Job管理与FunctionCall统一SQL访问；docs/业务线/BL000012-工作台/需求迭代/REQ-9f3c2a84-6d1e-4b7a-b5c2-8e4a71d0f39b-运维Job管理与FunctionCall统一SQL访问/ |
| 涉及仓库与功能域 | workhub-server：bootstrap（Flyway）、job（动态调度+任务改造）、service（JobAdmin/FunctionCall/Collector 改造）、controller（2 个新 Controller+看板筛选）、dao（2 个新 Mapper+OpsMonitorMapper 扩展）、model（实体/DTO）；workhub-web：菜单/路由、JobManagementView、FunctionCallView、OpsMonitorView 筛选 |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server@codex/development-20261102，2026-11-14；生产运行库只读核对（ops_monitor_config 8 条 XXL_JOB 配置） |

## 1. 现状与证据

- 定时任务：workhub-job 下 9 类 10 个调度入口，全部静态 `@Scheduled`：
  | 调度入口 | 类型 | 现值 | 说明 |
  | --- | --- | --- | --- |
  | XxlJobFailureMonitorJob.scanFailedJobs | CRON | `0 */10 * * * *` | XXL-JOB 失败巡检 |
  | MqFailureMonitorJob.scanFailedMessages | CRON | `0 */10 * * * *` | MQ 失败巡检 |
  | ElkSystemAlertJob.collectErrors | CRON | `0 * * * * *` | ELK 系统预警采集 |
  | ElkSystemAlertJob.resumeIncompleteCollections | FIXED_DELAY | 10000ms | ELK 续读 |
  | AccountingReconcileJob.reconcilePreviousDay | CRON | `0 30 6 * * *`（zone Asia/Shanghai） | `@ConditionalOnProperty(workhub.ops.accounting.enabled=true)` 装配 |
  | WorkItemDueReminderJob.remindDueSoonItems | CRON | `0 */30 * * * *` | 内部 `workhub.reminder.enabled` 业务开关保留 |
  | AgentExecutionJob.dispatch / AgentReleaseJob.dispatch / AgentVerificationJob.wake / AgentBuildTestJob.dispatch | FIXED_DELAY | 5000ms（initialDelay 30000） | Agent 轮询 |
- 业务库访问链路：`XxlJobDatabaseQueryClient`（MQ 同用）→ `McpXxlJobDatabaseQueryClient.runVerifiedBatch` → MCP 目标解析（业务线+环境→唯一 readonly target）→ `DatabaseDiagnosticService.runReadonlyQueries` + `SqlPolicyGuard`（只读、单条、强制 LIMIT）。证据链 `XxlJobQueryEvidence` 依赖 `VerifiedBatch.targetHash/startedAt/finishedAt`。
- SQL 拼接点：XxlJobMonitorCollector 7 个模板体（summarySql/focusedSummarySql/healthStatsSql/failedJobsCountSql/failedJobsSql/silentJobsSql/logStatisticsSql/executorsSql）+ 片段构建器（detailFilterSql/logStatusFilterSql→abnormalPredicate/executorAppNameFilterSql）；MqMonitorCollector 3 个模板体（statsSql/messageStatisticsSql/messagePageSql）+ 片段（exchangeInSql/queueInSql/inListSql）。collectLogs 的组合语句为「固定外壳 + 两个模板渲染结果」的拼接（collector 第 236 行）。
- 建表与权限：Flyway 迁移在 workhub-bootstrap/src/main/resources/db/schema/mysql/；权限种子模式见 V16（sys_permission INSERT + ops:view 父关联）。前端菜单硬编码于 workhub-web/src/constants/menu.ts + router/index.ts。
- 尚未核实的假设：无多实例部署（执行日志含 hostname 字段留观察位，本期不做分布式锁）。

## 2. 系统架构

### 动态调度（目标 1）

- 新增 `WorkhubJob` 接口（workhub-job）：`code()/name()/scheduleType()/defaultScheduleValue()/run(String params)`；10 个调度入口各自实现为 Bean（ElkSystemAlertJob 拆为两个 WorkhubJob Bean）。
- 新增 `DynamicJobScheduler`：自建 `ThreadPoolTaskScheduler`（池大小 4，线程名 workhub-job-）；启动时对每个 WorkhubJob Bean 以 `INSERT ... SELECT WHERE NOT EXISTS` 幂等写入默认配置到 sys_job_config，随后按配置调度（CRON→CronTrigger，FIXED_DELAY→scheduleWithFixedDelay，保留 initialDelay=30000ms）；`JobAdminService` 修改配置后取消对应 `ScheduledFuture` 并立即重调度。
- 执行包装：每次运行写 sys_job_execution_log（RUNNING→SUCCESS/ERROR，含 duration/hostname/error 截断 4000）；并发保护：每任务 AtomicBoolean，调度触发遇运行中则跳过（记日志不记历史），手动触发遇运行中直接拒绝。内部每日一次清理 90 天前历史（调度器内置 housekeeping，不出现在管理页）。
- 语义保留：AccountingReconcileJob 未装配（属性关闭）时无 Bean、无配置行，不调度；WorkItemDueReminder 的 `reminder.enabled` 业务开关保留在 run() 内部。
- `@EnableScheduling` 保留但 10 处 `@Scheduled` 注解全部移除；`workhub.agent.dispatch-interval-ms/initial-delay-ms`、`workhub.ops.*.cron` 等调度属性不再生效（**行为变更点**：DB 为唯一调度事实源，默认值回写自代码常量）。

### Function Call（目标 3/4）

- 新增 `FunctionCallService`（workhub-service）：`render(code, params)` 按 sql_code 或场景编码取启用的模板并以 `:name` 命名参数替换（模板引用的参数缺失即报错；多余参数忽略并记录）；`run(...)`/`runBatch(...)`/`runVerified(...)` 走现有 `McpXxlJobDatabaseQueryClient` 三方法，证据链不变。模板每次直查 func_call_sql（巡检频率低），页面修改即时生效。
- 参数值安全：值由调用方构建并先经现有白名单校验（quoteDatabaseName/validateExecutorKeyword/escapeSqlLikeLiteral/sqlLiteral/date 校验等），FunctionCallService 只做替换；最终 SQL 仍过 SqlPolicyGuard。片段参数（:detailFilter/:statusFilter/:executorFilter/:exchangeFilter 等）允许空串。
- Collector 改造：模板体从登记表读取，代码中 `*Sql()` 方法改为「取模板→构建参数→render」；片段构建器保留在代码内（白名单逻辑不下放模板）。collectLogs 组合语句外壳保留代码内（内嵌两个模板渲染结果，登记场景 OPS-XXL-LOG-STATS 与 OPS-XXL-FAILED-PAGE）。
- 试运行（manage 权限）：传 sql_code/scenario + 参数 JSON（值为字符串）+ 业务线/环境 → render → runRows → 返回列名+最多 50 行；模板渲染后同样过 SqlPolicyGuard。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| DynamicJobScheduler | 调度生命周期、执行包装、历史清理 | 业务逻辑 | sys_job_config / WorkhubJob Bean | ThreadPoolTaskScheduler |
| JobAdminService/Controller | 管理页 API：列表/改调度/启停/触发/历史 | 调度执行 | REST /api/ops/jobs/** | DynamicJobScheduler |
| FunctionCallService | 模板读取渲染、统一执行入口、试运行 | SQL 值校验（调用方负责） | func_call_sql + 参数 Map | McpXxlJobDatabaseQueryClient |
| Collectors（XXL/MQ） | 参数构建（白名单）、结果组装、证据链 | SQL 模板维护 | FunctionCallService.render/run* | func_call_sql 种子 |
| workhub-web 两个新页 | 管理交互与展示 | — | REST API | 菜单/权限 |

## 3. 契约与数据模型

### 新表

- `sys_job_config`：id PK；job_code 唯一；job_name；schedule_type ENUM('CRON','FIXED_DELAY')；schedule_value VARCHAR(64)（cron 表达式或毫秒数）；default_schedule_value VARCHAR(64)；enabled TINYINT(1) 默认 1；allow_manual_trigger TINYINT(1) 默认 1；param_desc VARCHAR(512)；remark VARCHAR(255)；created_at/updated_at。索引：uk_job_code。
- `sys_job_execution_log`：id PK；job_code；trigger_type ENUM('SCHEDULED','MANUAL')；params TEXT；status ENUM('RUNNING','SUCCESS','ERROR')；started_at DATETIME(3)；finished_at DATETIME(3) NULL；duration_ms BIGINT NULL；error_message TEXT NULL；hostname VARCHAR(128)。索引 idx_job_exec(job_code, started_at)、idx_job_exec_status(status)。
- `func_call_sql`：id PK；sql_code VARCHAR(64) 唯一；scenario_code VARCHAR(64) NULL（**非唯一**：一个业务场景可映射多条分支模板，MQ 发送/接收分支即此形态，按 branch_order 组合）；branch_order INT 默认 1；name VARCHAR(128)；description VARCHAR(512)；sql_template MEDIUMTEXT；param_desc VARCHAR(1024)（参数名、含义、是否可空）；enabled TINYINT(1) 默认 1；source_module VARCHAR(64)（来源模块，如 OPS_XXL/OPS_MQ）；remark VARCHAR(255)；created_at/updated_at。索引：uk_func_call_sql_code、idx_func_call_scenario(scenario_code, branch_order)。

### 种子（场景编码 ↔ SQL，实施细化）

模板共 14 条：XXL 8 条 + MQ 6 条（MQ 按发送/接收分支拆分模板，UNION ALL 组合外壳保留在 Collector 代码，同 XXL collectLogs 组合模式）。种子文本取自当前代码在用 SQL 体（%n$s → :name 逐位替换）。**种子机制（对 v1.0 草案的细化）**：DDL 与权限走 Flyway（V20261114_110000）；模板数据由 FuncCallSchemaInitializer 启动时从 FuncCallSqlSeeds 常量幂等回写（sql_code 已存在不覆盖，页面维护优先），与 DynamicJobScheduler 的任务配置回写同一模式——避免 SQL 字符串二次转义并保证单测可直接复用种子常量做等价断言。

| scenario_code（branch_order） / sql_code | 用途 | 主要参数 |
| --- | --- | --- |
| OPS-XXL-SUMMARY / OPS_XXL_SUMMARY | 全库汇总（无执行器过滤） | databaseName/startDate/endDateExclusive |
| OPS-XXL-FOCUSED-SUMMARY / OPS_XXL_FOCUSED_SUMMARY | 执行器聚焦汇总 | databaseName/executorFilter/startDateTime/endExclusiveDateTime |
| OPS-XXL-HEALTH-STATS / OPS_XXL_HEALTH_STATS | 巡检五态健康统计 | databaseName/silentGraceCutoff/staleCutoff/startDate/endDateExclusive |
| OPS-XXL-FAILED-COUNT / OPS_XXL_FAILED_COUNT | 失败明细计数 | databaseName/startDateTime/endExclusiveDateTime/executorFilter/detailFilter/statusFilter |
| OPS-XXL-FAILED-PAGE / OPS_XXL_FAILED_PAGE | 失败明细分页 | 前者 + limit/offset |
| OPS-XXL-SILENT-JOBS / OPS_XXL_SILENT_JOBS | 静默任务明细 | databaseName/silentGraceCutoff |
| OPS-XXL-LOG-STATS / OPS_XXL_LOG_STATS | 日志页统计（单 SELECT 回执） | matching/databaseName/startDate/endDateExclusive/executorFilter/detailFilter |
| OPS-XXL-EXECUTORS / OPS_XXL_EXECUTORS | 执行器清单 | databaseName |
| OPS-MQ-STATS(1) / OPS_MQ_STATS_SENDER | MQ 巡检统计·发送分支 | databaseLabel/databaseName/windowFrom/windowTo/stuckLiteral/exchangeFilter |
| OPS-MQ-STATS(2) / OPS_MQ_STATS_RECEIVER | MQ 巡检统计·接收分支 | databaseLabel/databaseName/windowFrom/windowTo/exchangeFilter/queueFilter |
| OPS-MQ-MESSAGE-STATS(1) / OPS_MQ_MESSAGE_STATS_SENDER | MQ 消息统计·发送分支 | databaseName/windowFrom/windowTo/senderPredicate/exchangeFilter |
| OPS-MQ-MESSAGE-STATS(2) / OPS_MQ_MESSAGE_STATS_RECEIVER | MQ 消息统计·接收分支 | databaseName/windowFrom/windowTo/receiverPredicate/exchangeFilter/queueFilter |
| OPS-MQ-MESSAGE-PAGE(1) / OPS_MQ_MESSAGE_PAGE_SENDER | MQ 消息分页·发送分支 | databaseLabel/databaseName/windowFrom/windowTo/senderPredicate/exchangeFilter |
| OPS-MQ-MESSAGE-PAGE(2) / OPS_MQ_MESSAGE_PAGE_RECEIVER | MQ 消息分页·接收分支 | databaseLabel/databaseName/windowFrom/windowTo/receiverPredicate/exchangeFilter/queueFilter |

### API 契约

- Job 管理（权限 ops:job:view / ops:job:manage）：
  - `GET /api/ops/jobs` → [{jobCode, jobName, scheduleType, scheduleValue, defaultScheduleValue, enabled, allowManualTrigger, paramDesc, running, nextFireTime, lastExecution{status,startedAt,durationMs,errorMessage}}]
  - `PUT /api/ops/jobs/{code}/schedule` body {scheduleType, scheduleValue}（CRON 校验 CronExpression；FIXED_DELAY 1000–3600000ms）→ 即时重调度
  - `PUT /api/ops/jobs/{code}/enabled` body {enabled}
  - `POST /api/ops/jobs/{code}/trigger` body {params}（字符串，可空）→ 同步执行并返回执行结果记录（运行中拒绝）
  - `GET /api/ops/jobs/{code}/executions?page&pageSize` → 分页历史
- Function Call（权限 ops:function-call:view / ops:function-call:manage）：
  - `GET /api/ops/function-call/sql?keyword&enabled` → 列表
  - `POST /api/ops/function-call/sql`、`PUT /api/ops/function-call/sql/{id}` → 新增/编辑（sql_code/scenario_code 唯一校验；模板非空；模板内 `:param` 语法校验）
  - `PUT /api/ops/function-call/sql/{id}/enabled` → 启停
  - `POST /api/ops/function-call/execute` body {codeOrScenario, params(对象，值为字符串), businessLineCode, environmentCode} → {columns, rows(≤50), sqlRendered}
- XXL 看板筛选：`GET /api/ops/monitors/xxl-job/dashboard` 新增 `lastStatus`（UP/ERROR/UNCHECKED，UNCHECKED 匹配 last_status IS NULL 且 enabled=1；DISABLED 不单列，配合 enabled 筛选）与 `enabled`（true/false）参数；OpsMonitorMapper.findAll 对应扩展 `<if>`。
- 幂等/并发：trigger 与调度共用并发保护；func_call_sql 更新即时生效（无缓存）；job 配置更新后调度器同步重载；错误码沿用 ApiResponse 业务异常文案（400 语义）。

### 数据库变更落点

- `V20261114_100000__add_job_admin.sql`：两表 + ops:job:view/manage 权限种子（含 ops:view 父关联，模式照抄 V16）。
- `V20261114_110000__add_function_call.sql`：func_call_sql 表 + 11 条模板种子 + ops:function-call:view/manage 权限种子。
- 可重复性：CREATE TABLE IF NOT EXISTS + INSERT WHERE NOT EXISTS；回滚：DROP TABLE 三张 + DELETE 权限码（回滚脚本随迁移文件附文末注释）。

## 4. 非功能约束

- 安全：新页面权限码独立；试运行与业务监测同走 SqlPolicyGuard 只读网关；模板参数不解析 SQL 注入防护（防护=值白名单+网关）；func_call_sql 管理限 ops:function-call:manage。
- 性能：巡检频率 30 分钟级，模板直查 DB 开销可忽略；调度线程池 4；执行历史 90 天清理防膨胀（按 10 任务×巡检频率估算 90 天 < 5 万行）。
- 失败处理：任务异常捕获记 ERROR 历史，不影响下次调度；调度器启动失败快速失败阻断应用启动（保持现状语义）；模板禁用/缺失时 Collector 报错落 ops_monitor_config.last_status=ERROR（与现状一致）。
- 审计：func_call_sql 变更走 updated_at + remark 记录来源；执行历史含 hostname。

## 5. 交付与回滚

- 发布顺序：① 后端（迁移先行，Flyway 启动自执行）→ ② 前端（菜单/页面）。回滚同序倒推。
- 开关：sys_job_config.enabled 可停用任一任务（含误操作恢复 default 值参照 default_schedule_value 列）。
- 回滚条件：调度框架异常导致任务不执行；Collector 渲染不等价导致监测口径变化。回滚步骤：还原后端版本（Flyway 迁移只增不破坏旧表结构；新表留存不影响旧代码）+ 还原前端版本；不可逆部分：无（三张新表可留存或 DROP）。
- 生效后更新：docs/业务线/BL000012-工作台/迭代日志.md、AGENTS.md 研发节、docs/研发/FunctionCall数据库访问规范.md、docs/README.md（如需索引）。

## 6. 一致性与待确认

- 目标承接：目标1→§2 动态调度+§3 Job API；目标2→§3 看板筛选；目标3→§2 Function Call+§3 种子与 API；目标4→T-09 规范任务。
- 非范围保持：不引入外部调度中心、不改监测口径与证据链、不做分布式锁。
- 待确认：①「未检查」筛选口径（UNCHECKED=有配置从未巡检）是否需要含 DISABLED 展示态；② Agent 轮询任务允许停用带来的数字员工停摆风险已接受（页面二次确认）。以上不影响主体方案，实施中按默认口径执行。
- 关联：proposal.md v1.0；tasks.md v1.0。
