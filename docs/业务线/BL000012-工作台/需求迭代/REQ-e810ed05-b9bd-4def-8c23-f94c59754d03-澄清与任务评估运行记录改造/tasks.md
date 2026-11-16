# Tasks：澄清与任务评估运行记录改造

## 1. 元信息

- 需求编码：REQ-e810ed05-b9bd-4def-8c23-f94c59754d03
- 需求名称与唯一需求目录：澄清与任务评估运行记录改造；`docs/业务线/BL000012-工作台/需求迭代/REQ-e810ed05-b9bd-4def-8c23-f94c59754d03-澄清与任务评估运行记录改造/`
- 涉及仓库与功能域：workhub-server（intake dao/model/service/controller/migration/单测）；workhub-web（需求录入列表页）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：v1（2026-11-12）
- Design/契约版本：v1（2026-11-12）
- 目标环境与分支：workhub-server `codex/development-20261102`；workhub-web 当前开发分支；运行库 10.10.116.32:3306/workhub
- 任务计划状态：规格已确认（2026-11-12），实施中
- 负责人/评审人：用户（sh）

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 两个 Response DTO 加 retryable | 无 | §3 契约 / AC-04 | 待开始 |
| T-02 | IntakeClarificationAnalysisMapper：failInFlightByIntakeId + listByIntakeId | 无 | §3 / AC-01、AC-05 | 待开始 |
| T-03 | DevelopmentAnalysisMapper：failInFlightByIntakeId | 无 | §3 / AC-07 | 待开始 |
| T-04 | IntakeClarificationAnalysisService 改造 + history 方法 | T-01、T-02 | §2 流程、§4 兜底 / AC-01~04 | 待开始 |
| T-05 | DevelopmentAnalysisService 同构改造（含 Noop mapper 补实现） | T-01、T-03 | §2 / AC-07 | 待开始 |
| T-06 | IntakeController 历史端点 | T-04 | §3 / AC-05 | 待开始 |
| T-07 | Flyway 存量数据修复迁移 | 无 | §3 迁移 / AC-06 | 待开始 |
| T-08 | 后端单测更新与新增并运行 | T-04、T-05 | AC-01~03、AC-07 | 待开始 |
| T-09 | 前端：类型、API、retryable 判定、历史列表 UI | T-06（联调契约） | AC-04、AC-05 | 待开始 |
| T-10 | 端到端验证与文档更新（现状/迭代日志/notes） | T-07~T-09 | AC-01~07 | 待开始 |

执行顺序：T-01 → T-02/T-03 → T-04/T-05 → T-06 → T-07 → T-08 → T-09 → T-10。T-04 与 T-05 改不同文件可并行；T-07 独立可随时插入。

## 2a. 执行记录（2026-11-12）

| 任务 | 状态 | 真实结果与证据 |
| --- | --- | --- |
| T-01 | 已完成 | 两 Response record 加 `boolean retryable`；`mvn compile` 全模块通过 |
| T-02 | 已完成 | `IntakeClarificationAnalysisMapper.failInFlightByIntakeId/listByIntakeId`（list 含 business_line 联接与 items JSON 处理，ORDER BY id DESC） |
| T-03 | 已完成 | `DevelopmentAnalysisMapper.failInFlightByIntakeId` |
| T-04 | 已完成 | analyze：fresh 防重保留 → 旧 items 归档 → failInFlight → insert 新行 → 提交拒绝置 FAILED+history+抛 IllegalStateException；executeAnalysis 拆 runAnalysis + Throwable 兜底 + 起止/耗时日志；toResponse/isRetryable；history() |
| T-05 | 已完成 | 同构改造（analyze 去除"永久返回 in-progress"，补 isStaleExecution）；NoopIntakeClarificationAnalysisMapper 补两方法；人工录入草稿构造点补 retryable=true |
| T-06 | 已完成 | `GET /api/intake/{id}/clarification-analysis/history`（intake:record:view） |
| T-07 | 已完成 | `V20261112_120000__fail_stale_intake_analysis_runs.sql`，两表超 30 分钟 PENDING/RUNNING → FAILED（updated_by='migration'），可重复执行 |
| T-08 | 已完成 | IntakeClarificationAnalysisServiceTest 重写 3 个既有用例 + 新增 5 个（stale 废弃重起、FAILED 重试、提交拒绝、Throwable 兜底、retryable、history）；DevelopmentAnalysisServiceTest 新增 2 个；两套件 25 项全部通过（`mvn -pl workhub-service -am test`）。模块全量测试被工作区在途改动阻断：workhub-service 3 个 ops 测试编译错误（用户在途 MQ 改动，验证期间临时移出并已字节级原样恢复）、KnowledgeArchiveWorkflowTest 存量 wiki 路径断言失败（用户在途 ProjectKnowledgeBaseService 改动，与本次无关，未修改）、workhub-controller AgentToolControllerTest 编译错误（用户在途 agent 域改动），bootstrap 迁移测试因此未跑，迁移语义经只读口径核对 |
| T-09 | 已完成 | types/api 加 retryable 与历史查询；两个 isXxxInProgress 改按 `retryable === false`（无字段回退 status）；澄清页签"历史澄清记录（N 轮）"折叠列表（只读）；`npm run build` 通过 |
| T-10 | 部分完成 | 迭代日志、现状 11b 已更新；服务重启应用迁移、intake 271/275/277 重试澄清端到端走查待用户部署后执行 |

后续变更①（2026-11-12 用户提出）：删除 PENDING 排队提示文案——澄清（"需求澄清分析已提交…"）与任务评估（"任务评估已提交…"）两处 `MESSAGE_PENDING` 均移除，插入 PENDING 行不再写 analysis_message（页面仅显示状态"排队中"），测试夹具同步；RUNNING 提示与失败消息保留。两套件 25 项复测通过。存量 3 条 PENDING 行的旧文案随迁移置 FAILED 后被迁移消息覆盖，无需额外清洗。

后续变更②（2026-11-12 用户提出）：澄清运行记录增加开始/结束时间并以表格+详情弹框展示——`pm_intake_clarification_analysis` 加 `started_at`（首次进入 RUNNING）/`finished_at`（首次到达 DRAFT/CONFIRMED/FAILED）列（迁移 V20261112_130000，存量行按 created_at/updated_at 近似回填），Mapper 两处 UPDATE 以 CASE 首次赋值（回复不改变已录得时间）；响应 DTO 加 startedAt/finishedAt。澄清页签重构：澄清轮数据用 el-table 展示（轮次/状态/开始时间/结束时间/待确认项数/风险项数/失败原因），点"详情"弹框查看该轮待确认项与风险项——最新轮可交互回复（保存回复/确认风险），历史轮只读（含回复人与回复内容），弹框标题含轮次与状态，FAILED 轮展示失败原因横幅；弹框内容按 id 实时取自轮次数据，回复保存后自动刷新。任务评估表暂不加列与弹框（用户仅要求澄清）。后端两套件 25 项、`npm run build` 通过。

后续变更③（2026-11-12 用户提出，拒绝"AI 自答"方案后定向收紧）：澄清生成提示词收紧——用户否决"AI 给建议答案人工采纳"设计，要求只提"综合知识库、代码、需求后确实无法确定（不能是推测）"的问题、只列有明确依据的风险。迁移 `V20261112_161000__tighten_clarification_prompt.sql` 更新 `intake.clarification.analyze` 模板（checksum 幂等模式，变量白名单不变）：待确认项准入=已查证仍无法确定唯一答案+答案实质影响实现行为+evidence 须写明查证来源与结论（不得"证据不足"留白）；风险项仅限有具体位置依据的现实冲突/依赖缺失/数据口径问题，假设性风险不列；数量纪律=宁缺毋滥、通常不超过 5 项、可返回空数组。`PromptTemplateMigrationConsistencyTest` 2/2 通过（占位与白名单一致、渲染可用）。生成质量需重启迁移后对真实需求实测。

后续变更④（2026-11-12 用户提出）：研发任务评估补齐与澄清同款的每轮记录——迁移 `V20261112_170000` 给 `pm_intake_development_analysis` 加 `started_at`/`finished_at`（CASE 首次赋值，update/updateExecutionState/updateStatus 三条语句全覆盖，存量按 created_at/updated_at 回填）；实体/DTO（startedAt/finishedAt）/`listByIntakeId`/`history()`/`GET /intake/{id}/development-analysis/history`（intake:record:view）齐备；研发任务页签新增轮次 el-table（轮次/状态/开始/结束/任务数/失败原因）+ 每轮详情只读弹框（评估摘要/需求点/拆解任务/风险）+ **行内"重试"按钮**（权限按 canRunDevelopmentAnalysis，调既有 analyze 端点，后端 insert-per-run 与防重语义不变），ZCode 空转产生空 workItems 的轮次在表格里一眼可见（任务数 0）。响应占位草稿补 startedAt/finishedAt 字段。三套件 40/40、前端构建通过。

## 3. 任务明细

### T-01：DTO 加 retryable
- 描述与目标：`IntakeClarificationAnalysisResponse`、`DevelopmentAnalysisResponse` 增加 `boolean retryable`。
- 输出：两 record 字段变更；所有构造点编译通过。
- 验收条件：
  - [ ] `mvn compile` 通过，无未更新构造点。

### T-02/T-03：Mapper 新增方法
- 描述与目标：见 Design §3；SQL 与既有注解风格一致（@Update/@Select 文本块）。
- 验收条件：
  - [ ] `failInFlightByIntakeId` 仅命中 PENDING/RUNNING 行并置 FAILED + message + updated_by + updated_at；
  - [ ] `listByIntakeId` 按 id DESC 返回全部轮次，字段与 findById 一致。

### T-04：IntakeClarificationAnalysisService 改造
- 描述与目标：analyze() 改 insert-per-run（防重保留、废弃旧行、拒绝兜底）；executeAnalysis() 加日志 + Throwable 兜底；toResponse 计算 retryable；新增 history()。
- 验收条件：
  - [ ] stale 重试：旧行 FAILED（注明被取代）+ 新行 insert + executor.execute 调用；
  - [ ] fresh PENDING/RUNNING：不 insert、不 execute；
  - [ ] 提交拒绝：新行 FAILED + history"需求澄清分析失败" + 抛出；
  - [ ] Throwable：行 FAILED + log.error + history。

### T-05：DevelopmentAnalysisService 同构改造
- 描述与目标：analyze() 去除"in-progress 永远返回"，改为同澄清的 stale 判定 + insert-per-run；executeAnalysis 同兜底；toResponse retryable；Noop 补实现。
- 验收条件：
  - [ ] stale/in-flight 行可被新运行废弃重起；
  - [ ] chat/confirm/updateDraft/retryKnowledgeArchive 行为不回归（现有相关单测通过）。

### T-06：Controller 历史端点
- 描述与目标：`GET /api/intake/{id}/clarification-analysis/history`。
- 验收条件：
  - [ ] 返回按 id DESC 全部轮次；权限注解与相邻端点一致。

### T-07：Flyway 迁移
- 描述与目标：`V20261112_120000__fail_stale_intake_analysis_runs.sql`，两表存量超时 PENDING/RUNNING → FAILED。
- 验收条件：
  - [ ] 迁移执行后 intake 271/275/277 澄清记录为 FAILED；正常行不受影响（只读 SQL 核查）。

### T-08：后端单测
- 描述与目标：更新 `IntakeClarificationAnalysisServiceTest` 三个既有用例语义，新增失败兜底与 retryable 用例；新增 DevelopmentAnalysisService 同类用例。
- 验收条件：
  - [ ] `mvn -pl workhub-service test` 相关用例全部通过。

### T-09：前端改造
- 描述与目标：类型加 retryable 与历史类型；`fetchClarificationAnalysisHistory`；两个 `isXxxInProgress` 改判 `retryable === false`（无字段回退旧逻辑）；澄清 tab 增加历史轮次折叠列表（只读）。
- 验收条件：
  - [ ] `npm run build` 通过；
  - [ ] 页面：FAILED/stale 时按钮可点，fresh in-progress 时禁用。

### T-10：端到端与文档
- 描述与目标：重启服务（建议普通 Run）→ 迁移生效 → 对 271/275/277 重试澄清 → 走完 PENDING→RUNNING→DRAFT；回复澄清项；查看历史列表；澄清完成后任务评估同链路；更新现状/迭代日志/notes。
- 验收条件：
  - [ ] AC-01~07 逐条核对通过并记录证据。

## 4. 覆盖与评审

- Proposal AC-01~07 均映射至 T-01~T-10；Design 契约/迁移/非功能均有承接。
- 未覆盖项：无。
- 完整规格统一确认：2026-11-12 用户确认（范围：澄清+研发拆解一起改、增加历史列表、前端按后端返回判定重试），据此实施。
