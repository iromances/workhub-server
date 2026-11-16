# Proposal：运维Job管理与FunctionCall统一SQL访问

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-9f3c2a84-6d1e-4b7a-b5c2-8e4a71d0f39b |
| 需求名称 | 运维Job管理与FunctionCall统一SQL访问 |
| 涉及仓库与功能域 | workhub-server（workhub-bootstrap/workhub-job/workhub-service/workhub-controller/workhub-dao/workhub-model）；workhub-web（运维菜单、Job管理页、Function Call 页、业务监测筛选） |
| 所属项目或业务线 | BL000012-工作台（WorkHub 平台） |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-9f3c2a84-6d1e-4b7a-b5c2-8e4a71d0f39b-运维Job管理与FunctionCall统一SQL访问/（相对 workhub-server 根目录） |
| 版本/状态 | v1.0 草案，待完整规格统一确认 |
| 目标系统 | WorkHub 后端（Java 25 / Spring Boot 4 / MyBatis / MySQL / Flyway）、WorkHub 前端（Vue 3 + Element Plus） |
| 原始需求来源及版本 | 用户提出（2026-11-14 会话），四点原始诉求见 Why 节 |
| 目标环境/分支 | workhub-server 当前开发分支 codex/development-20261102；workhub-web 对应开发分支；集成验证在本地/TEST |
| 负责人/评审人 | 待确认 |

## 1. Why：背景与问题

- 当前问题及证据：
  - 9 个定时任务类（10 个调度入口）全部为静态 `@Scheduled`（workhub-job/src/main/java/cn/aslight/workhub/job/），修改调度时间需改配置并重启；无执行历史表，失败原因只能翻服务日志。
  - 业务监测 XXL-JOB 看板（`/api/ops/monitors/xxl-job/dashboard`）只有业务线/环境/仅启用筛选，无法按「上次状态（UP/ERROR）」「启用状态」过滤，8 条配置中一半 ERROR 时定位不便。
  - XXL-JOB 与 MQ 巡检访问业务库的 SQL 在 `XxlJobMonitorCollector`（10 个 `*Sql()` 方法）与 `MqMonitorCollector`（3 个）里以字符串拼接方式散落，无登记、无编号、无跨场景复用；新增业务库查询场景只能继续改代码。
- 受影响用户/角色：WorkHub 运维/研发用户（ops:monitor:view 等权限持有者）；数字员工巡检流程（消费 XXL-JOB 失败监测状态）。
- 不解决的后果：调度调整依赖发版；业务库 SQL 无统一治理入口，口径漂移无据可查；规范无法落地为强制通道。

## 2. What：目标与范围

### 本期目标

1. 定时任务治理：XXL-JOB 与 MQ 失败巡检 cron 默认从每 10 分钟调整为每 30 分钟；新增「Job 管理」页面，可查看全部系统定时任务、修改调度时间（即时生效）、启停、手动触发（支持输入参数）、查看过往执行情况（执行时间、状态、失败原因）。
2. XXL-JOB 看板增加「上次状态」「启用状态」筛选条件。
3. 新增「Function Call」菜单：提供通用业务库查询函数；业务监测 XXL-JOB 与 MQ 请求业务数据库统一改为调用该函数；SQL 模板统一登记在 Function Call 子菜单（SQL 管理）下维护，用「场景编码 ↔ sql_code」关联，调用时传场景或编码，函数内部取模板渲染执行并返回。
4. 将上述通道设立为项目规范：需要连接业务数据库时，必须先在 Function Call 登记场景 SQL，再经通用函数调用；代码内直拼业务库 SQL 下线。

### 本期交付范围

1. workhub-server：两张新表（sys_job_config、sys_job_execution_log）与一张新表（func_call_sql）的 Flyway 迁移；动态调度框架（替代静态 @Scheduled）；Job 管理 API；XXL 看板筛选；FunctionCallService 与两个 Collector 改造；Function Call 管理/试运行 API；新权限码种子。
2. workhub-web：运维组新增「Job 管理」「Function Call」菜单与页面；业务监测 XXL-JOB 看板筛选栏扩展。
3. 规范文档：AGENTS.md 新增条目 + docs/研发/FunctionCall数据库访问规范.md。

### 明确不做

1. 不引入外部调度中心（XXL-JOB Admin/Quartz），动态调度基于 Spring TaskScheduler 自研轻量框架。
2. 不改变业务监测的统计口径、五态健康判定与证据链（XxlJobQueryEvidence）结构。
3. 不做跨节点调度协调（当前 WorkHub 单实例部署；执行日志记录 hostname 以备多实例观察，但本期不加分布式锁）。
4. MCP 数据库目标（targetKey/profileKey）的配置管理不在本期（沿用现有 MCP 资源配置）。

### 约束与依赖

- 依赖的代码和文档：`McpXxlJobDatabaseQueryClient`→`DatabaseDiagnosticService.runReadonlyQueries`→`SqlPolicyGuard` 只读链路必须保留；`runVerifiedBatch` 的证据能力（targetHash/起止时间）必须保留。
- 业务/合规约束：业务库访问仅只读（SqlPolicyGuard：SELECT/WITH/SHOW/DESC/EXPLAIN、单条、强制 LIMIT）；SQL 模板页面可维护但同样只经该网关执行。
- 既有系统约束：Agent 高频轮询任务（fixedDelay 5 秒级）纳入管理但误停会导致数字员工停摆，页面需二次确认；`workhub.ops.accounting.enabled` 装配开关、`workhub.reminder.enabled` 业务开关语义保留。
- 外部依赖与前置条件：无新外部依赖。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | Job 管理页列出全部 10 个调度入口（9 类任务），展示调度类型/当前值/启停/最近执行；修改 cron 或 fixedDelay 保存后不重启立即按新调度执行 | 本地/TEST 起服后页面操作 + sys_job_config 数据核对 | 用户 |
| AC-02 | 手动触发可输入参数文本并执行；执行历史可查每次执行的时间、触发方式、参数、状态、耗时、失败原因；历史保留 90 天自动清理 | 页面操作 + sys_job_execution_log 数据核对 | 用户 |
| AC-03 | XXL-JOB 失败巡检与 MQ 失败巡检默认 cron 为 `0 */30 * * * *`（30 分钟） | sys_job_config 种子默认值核对 | 用户 |
| AC-04 | XXL-JOB 看板可按上次状态（全部/UP/ERROR/未检查）与启用状态（全部/启用/停用）筛选，结果与库内 ops_monitor_config 数据一致 | 页面操作 + API 返回核对 | 用户 |
| AC-05 | Function Call 页可新增/编辑/启停 SQL 定义（sql_code、场景编码、模板、参数说明），可试运行（传参数与业务线/环境）返回限行结果 | 页面操作 | 用户 |
| AC-06 | XXL-JOB 与 MQ 巡检全部业务库取数改经 FunctionCallService 按场景编码执行，模板来自 func_call_sql；改造后与改造前等价（单测断言渲染 SQL 一致、接口返回一致、证据链字段不变） | mvn test 单测对照 + 详情页证据字段核对 | 用户 |
| AC-07 | AGENTS.md 与 docs/研发/FunctionCall数据库访问规范.md 生效，明确「业务库访问必须先登记场景 SQL 并经 FunctionCallService」 | 文档评审 | 用户 |
