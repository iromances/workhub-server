# Design：XXL-JOB 监测回调丢失检测缺陷修复

对应 [proposal.md](proposal.md)（v2）。改动集中在 `XxlJobMonitorCollector`（核心口径）、监测配置链路（阈值列）与响应模型。

## 1. 核心口径定义（白名单五态 + 静默）

按优先级短路，唯一可信成功值是 200；一切非 200 值按语义归类，不依赖魔法值枚举：

| 优先级 | 条件 | 状态 | 处置 |
| --- | --- | --- | --- |
| 1 | `trigger_code IS NULL OR trigger_code <> 200` | EXECUTION_FAILED 执行失败（含调度失败，2026-11-14 用户确认并入） | 计异常（立即） |
| 2 | `trigger_code = 200 AND handle_code = 200` | SUCCESS 执行成功 | — |
| 3 | `trigger_code = 200 AND (handle_code = 0 OR handle_code IS NULL) AND trigger_time >= 截止` | RUNNING 执行中 | 容忍 |
| 3' | 同上但 `trigger_time < 截止` | STUCK 卡死（回调丢失） | 计异常（超时） |
| 4 | `trigger_code = 200 AND handle_code NOT IN (0,200)`（NULL 经 NOT IN 落空归 3/3'） | EXECUTION_FAILED 执行失败 | 计异常（立即） |

- 截止 = `LocalDateTime.now().minusMinutes(T)`，应用侧时钟生成 `yyyy-MM-dd HH:mm:ss` 字面量拼入 SQL，与既有日期字面量拼接方式一致（避免 DB 时钟依赖与 MySQL/H2 INTERVAL 方言差异）。
- `T = monitor.staleThresholdMinutes`，NULL 时取 `DEFAULT_STALE_THRESHOLD_MINUTES = 120`（2 小时，2026-11-14 用户确认由 30 分钟上调）；<1 拒绝。
- 静默（任务维度，独立于日志）：`trigger_status = 1 AND (trigger_next_time IS NULL OR trigger_next_time < now - SILENT_GRACE_MINUTES[10])`，计数 `silentJobCount`。
- 状态在查询时点由原始字段计算，不落库；迟到回调到达后 STUCK 自动迁移为 SUCCESS/EXECUTION_FAILED。
- 真实数据回放依据（2026-11-13/14 会话核实）：事故 2,844 条卡死行 `trigger=200/handle=0`（含 handle_time 已写+msg 空串的半写形态）→ STUCK；admin"任务结果丢失"标记行 `handle=500` → EXECUTION_FAILED；旧中心 `trigger=500` → SCHEDULE_FAILED。

**监测项状态**：`executionFailedCount + stuckCount + silentJobCount > 0 → ERROR，否则 UP`（executionFailedCount 含调度失败）。message：异常时列非零项（如"执行失败 15、卡死（回调丢失）2039、静默任务 2"）；健康时"区间内无异常日志，无静默任务"。

## 2. SQL 落点（XxlJobMonitorCollector）

| 方法 | 现状 | 变更 |
| --- | --- | --- |
| `silentJobsSql`（新增，collect() 第 4 条语句） | — | 全库取静默任务明细 top 20（按 next_time 落后最早优先）：executorAppName/jobId/jobDesc/author/executorHandler/triggerNextTime（不选 schedule_conf：老版本 xxl_job_info 无该列，2026-11-14 运行期实证并移除）；lagMinutes 在 Java 侧由 next_time 与当前时间差计算（避免 SQL 方言函数），响应新增 `silentJobs` 列表。
| `healthStatsSql`（新增，替代 collect() 第 1 条语句） | — | 全库（不拼 executor 过滤）单行 SELECT：`xxl_job_info` 子查询输出 jobCount/enabledJobCount/disabledJobCount/silentJobCount，`xxl_job_group` 子查询输出 executorRegistryList，`xxl_job_log`（窗口内、仅按 trigger_time 过滤，不 join info/group）SUM CASE 输出 successCount/runningCount/stuckCount/scheduleFailedCount/executionFailedCount 与 `MAX(trigger_time) AS reportUpdatedAt`。结构沿用 `focusedSummarySql` 的 CROSS JOIN 子查询模式，返回恰好 1 行 |
| `summarySql`/`focusedSummarySql` | collect() 的显示统计来源 | **从 collect() 移除**；`collectSummary`（report 报表口径，无生产调用方）继续使用 `summarySql`/`focusedSummarySql`，语义不变，新增响应字段填 0 |
| `logStatusFilterSql` | FAILED=旧确定性失败谓词 | ABNORMAL=五态异常谓词（schedule_failed OR execution_failed OR stuck，见第 1 节条件组合）；ALL 不变 |
| `logStatisticsSql` | matching=旧谓词 | matching 换五态异常谓词（collectLogs 证据路径），failedJobTotal 语义 = ABNORMAL 计数 |
| `failedJobsCountSql`/`failedJobsSql` | 复用 statusFilter | 谓词自动生效；仍按 executor/author/keyword 过滤（明细聚焦），新增输出行分类所需字段无变化（分类在 Java 侧） |
| `normalizeLogStatus` | ALL/FAILED | ALL/ABNORMAL/FAILED，FAILED 归一化为 ABNORMAL（前端兼容） |
| `collect` 状态判定 | `failedOnly && failedJobCount>0` | 改为第 1 节健康口径（不再依赖 logStatus 视角；logStatus 只影响明细列表） |
| `check` | detail map | 新增 scheduleFailedCount/executionFailedCount/stuckCount/silentJobCount 键 |

显示字段映射（全库日志口径）：`triggerSuccessCount=successCount`、`triggerRunningCount=runningCount`、`triggerFailedCount=scheduleFailed+executionFailed+stuck`、`triggerCount=五类合计`。无 executor 过滤的监测由 report 口径切换为日志口径（同时消除 report 路径看不见卡死的盲区）。

## 3. 行分类（toFailedJob）

`failureType/failureTypeName` 改为带时间参数的 Java 实现：`failureType(triggerCode, handleCode, triggerTime, staleCutoff)`，triggerTime 用既有 `timestamp()` 解析（`Timestamp.toString()` 的 ".0" 尾缀 ISO 兼容），triggerTime 为 NULL 或不可解析归 SCHEDULE_FAILED（未被有效触发的行按白名单原则归调度异常）。删除无调用方的单参重载。取值：SUCCESS 执行成功 / RUNNING 执行中 / STUCK 卡死（回调丢失） / EXECUTION_FAILED 执行失败（trigger≠200 的调度失败同归此类，明细行仍可用 triggerCode 原始值区分根因）。

## 4. 配置链路（阈值）

- `ops_monitor_config` 新增 `stale_threshold_minutes INT NULL`：`OpsMonitorSchemaInitializer.ensureColumn` 幂等加列（`AFTER mq_lag_threshold`），CREATE TABLE 同步；`OpsMonitorMapper` 三处 SELECT/INSERT/UPDATE 增列；`OpsMonitorEntity`/`OpsMonitorSaveRequest` 增字段。
- `OpsMonitorService.toEntity`：XXL 分支 `normalizeStaleThresholdMinutes`（null→null 由 collector 取默认；<1 抛 IllegalArgumentException"卡死阈值必须大于等于 1 分钟"）；MQ 分支置 null。

## 5. 模型

`XxlJobDashboardResponse` 在 triggerFailedCount 后新增 `int executionFailedCount, int stuckCount, int silentJobCount`（executionFailedCount 含调度失败，行级分类 SCHEDULE_FAILED 并入 EXECUTION_FAILED，2026-11-14 用户确认），末尾新增 `List<XxlJobSilentJobResponse> silentJobs`（静默明细 top 20，2026-11-14 用户确认"加上吧"）；全参构造器更新（collect/toDashboardConfig/xxlJobDashboardError），兼容构造器（collectSummary 用）填 0。

## 6. 测试设计（XxlJobMonitorCollectorTest）

1. 既有 collect 系列用例首行 fixture 换为 healthStats 字段；断言按新口径与 SQL 形状更新（sqls[0] 无 report 表、无 executor 过滤）。
2. 新增：STUCK 计入状态与行分类（trigger=200/handle=0 旧时间）；阈值内 RUNNING 不异常；silentJobCount 计入状态；executor 名单不影响健康统计 SQL；threshold=120 时 SQL 截止字面量变化、threshold<1 拒绝；FAILED 与 ABNORMAL 同谓词。
3. H2 MySQL 模式：xxl_job_info 补 trigger_next_time 列，新增 collect() 全链路真实 SQL 用例（healthStats+count+page），含 stuck 行与静默行验证 ERROR。
4. collectLogs 证据路径用例沿用，谓词字符串断言更新。

## 7. 风险与回滚

- 无 executor 过滤的监测 display 口径由 report 切换为 7 天日志聚合：数值来源变化（更准），查询成本与既有 focused 路径同级（均为窗口内日志聚合，每 10 分钟一次）。
- 阈值内长任务被提前标 STUCK：可配置调大；STUCK 非终态，回调到达自动迁移。
- 回滚：口径类改动 + 响应字段新增 + 一列可空配置，回滚代码即恢复旧谓词；新列无回滚需求（可空、无默认行为改变）。
- 事故历史卡死记录使 ERROR 持续至 7 天窗口滚出：如实反映。
