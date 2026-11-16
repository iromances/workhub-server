# Proposal：澄清与任务评估运行记录改造

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-e810ed05-b9bd-4def-8c23-f94c59754d03 |
| 需求名称 | 澄清与任务评估运行记录改造 |
| 涉及仓库与功能域 | workhub-server：需求澄清分析（intake clarification）与研发拆解分析（development analysis）链路（dao/service/model/controller/migration/单测）；workhub-web：需求录入列表页澄清/任务评估重试判定与历史澄清记录列表 |
| 所属项目或业务线 | WorkHub 平台自身功能，业务线 BL000012-工作台 |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-e810ed05-b9bd-4def-8c23-f94c59754d03-澄清与任务评估运行记录改造/` |
| 版本/状态 | v1 已确认（2026-11-12），随实施更新 |
| 目标系统 | WorkHub Server（本地运行库 10.10.116.32:3306/workhub）+ WorkHub Web |
| 原始需求来源及版本 | 2026-11-12 用户会话：需求 271/275/277 澄清分析提交后超 1 小时仍 PENDING、按钮灰色不可重试；确认改为"每次澄清/评估插入运行记录、按本次结果更新状态、前端按后端返回判断可否重试"，并确认研发拆解分析一并改造、增加历史澄清记录列表 |
| 目标环境/分支 | workhub-server `codex/development-20261102`；workhub-web 当前开发分支 |
| 负责人/评审人 | 用户（sh） |

## 1. Why：背景与问题

1. 静默丢失：`IntakeClarificationAnalysisService.executeAnalysis` / `DevelopmentAnalysisService.executeAnalysis` 在首次 DB 写之前（requireAnalysisEntity/updateExecutionState）抛出任意异常、try 块内抛 `Error` 级异常、executor 提交被拒（`RejectedExecutionException`）三种情形都不会留痕——行停在 PENDING、无 history、无 notes，线程静默死亡。服务无任何起止日志。
2. 单行覆盖：`pm_intake_clarification_analysis`、`pm_intake_development_analysis` 按 intake upsert 同一行（`upsertExecutionState`），历次运行不留存；PENDING/RUNNING 卡死后：
   - 澄清：`generate()` 有 30 分钟 stale 重发通道（`EXECUTION_STALE_TIMEOUT`），但被前端封死；
   - 研发拆解：`analyze()` 对 in-progress 永远直接返回旧行（无 stale 判断），卡死即永久无法重试。
3. 前后端不一致：前端 `isClarificationInProgress` / `isDevelopmentAnalysisInProgress` 只要 PENDING/RUNNING 就禁用按钮，不看时长；后端 stale 通道对用户不可达。
4. 存量数据：运行库两表合计 25+ 条 PENDING 僵尸行（2026-05 至 2026-11），包括当前 3 条在澄清需求（intake 271/275/277）。

不解决的后果：每次 AI 分析后台异常/服务重启/线程死亡都产生一条无法恢复的需求流程，需人工改库解锁；历次澄清项内容在库内被覆盖，只能靠 notes.md 快照回溯。

## 2. What：目标与范围

### 本期目标

1. 每次澄清分析、任务评估运行（首次、重试、重评）都插入一条新运行记录，异步任务按本次运行结果更新该条状态；旧 in-flight 行在新运行启动时显式置 FAILED（"超时未完成，已被新分析取代"）。
2. 任何失败路径（Error、提交拒绝、启动前失败）都必须落到运行记录 FAILED + 需求 history + 服务日志。
3. 后端在响应中计算并下发 `retryable`，前端重试按钮只依据该字段（缺失时回退旧 status 判断）。
4. 澄清增加历史轮次查询接口与前端列表（每轮状态/时间/澄清项数，可只读展开）。
5. Flyway 迁移一次性把两表存量超时 PENDING/RUNNING 置 FAILED，解锁当前 3 条与历史僵尸行。

### 本期交付范围

1. workhub-server：两个 Mapper 新增 `failInFlightByIntakeId`（澄清另加 `listByIntakeId`）；两个 Service 改造（insert-per-run、Throwable 兜底、提交拒绝兜底、SLF4J 日志、retryable）；两个 Response DTO 加 `retryable`；`IntakeController` 新增澄清历史端点；`DevelopmentAnalysisService` 内 Noop mapper 补实现；Flyway 数据修复迁移；单测更新与新增。
2. workhub-web：`types/work-item.ts` 两接口加 `retryable` + 历史类型；`api/intake.ts` 历史查询函数；`IntakeListView.vue` 重试判定改用 retryable（无字段回退旧逻辑）、需求澄清 tab 增加"历史澄清记录"折叠列表。
3. 本仓库 `docs/业务线/BL000012-工作台/` 现状与迭代日志更新、`docs/notes.md` 过程记录。

### 明确不做

1. 不改 `IntakeAiDevelopmentService`（设计确认后自动触发的 AI 任务）的执行模式。
2. 不做前端 PENDING/RUNNING 自动轮询刷新（维持手动刷新）。
3. 不改 `workhubTaskExecutor` 线程池参数（core=2/max=4/queue=32）。
4. 不改澄清项回复（reply）、确认澄清完成、任务评估确认等业务规则与状态机（`IntakeDemandStatusRules`）。

### 约束与依赖

- 依赖：现有 `EXECUTION_STALE_TIMEOUT = 30 分钟` 口径沿用为 retryable 的 stale 阈值。
- 数据约束：`findLatestByIntakeId`（ORDER BY id DESC LIMIT 1）为最新一轮语义；reply/confirm/chat/updateDraft 等继续只操作最新行；insert 使用现有 `useGeneratedKeys`。
- 迁移约束：仅 UPDATE 数据、不改表结构；`updated_by='migration'` 可追溯；迁移幂等（仅命中 PENDING/RUNNING 且超 30 分钟行）。
- 测试不依赖真实网络与真实 AI（沿用现有 Mockito 风格与 `Executor` 注入）。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 对 stale（>30 分钟）PENDING 或 FAILED 最新记录发起澄清分析：旧行置 FAILED（message 注明被新分析取代）、新插入一行 PENDING 并异步执行 | 单测 + 运行库行数核查 | 用户 2026-11-12 会话确认 |
| AC-02 | 最新记录 PENDING/RUNNING 且未超 30 分钟时再次发起：返回现有行，不新增行、不重复执行 | 单测（沿用现有用例语义） | 现有行为保持 |
| AC-03 | `executeAnalysis` 中任何 `Throwable`、executor 拒绝：对应运行记录变 FAILED、写入需求 history、输出 ERROR 日志；不再出现新的静默 PENDING | 单测 + 重启/异常演练 | 用户确认"根据这次运行结果更新状态" |
| AC-04 | 响应含 `retryable`：无记录/FAILED/DRAFT/CONFIRMED 为 true；PENDING/RUNNING 超 30 分钟为 true、未超为 false；前端按钮 disabled 仅由 `retryable === false` 决定 | 单测 + 前端构建 + 页面走查 | 用户确认"前端根据后台返回判断" |
| AC-05 | 历史澄清记录接口按 id 倒序返回全部轮次；前端列表可展开只读查看每轮澄清项与回复 | 接口调用 + 页面走查 | 用户确认"增加历史列表" |
| AC-06 | 迁移执行后，两表 analysis_status IN (PENDING,RUNNING) 且 updated_at 超 30 分钟的行全部变 FAILED（intake 271/275/277 按钮恢复可用）；正常行不受影响 | 只读 SQL 核查 | 用户确认存量修复 |
| AC-07 | 任务评估（研发拆解）与澄清行为一致：stale/FAILED 可重试并新插入运行记录，旧的"永久 in-progress 返回"行为消除 | 单测 | 用户确认"澄清 + 研发拆解一起" |
