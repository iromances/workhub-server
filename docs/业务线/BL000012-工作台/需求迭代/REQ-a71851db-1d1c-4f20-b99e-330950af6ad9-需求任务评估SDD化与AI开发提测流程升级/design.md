# Design：需求任务评估SDD化与AI开发提测流程升级

### 2026-11-17 需求状态序列补齐开发完成

状态机（`IntakeDemandStatusRules`）中「开发完成」位于开发中与测试中之间，本轮仅补齐序列与展示，不动流转规则：

- workhub-server `IntakeMapper.LIST_ORDER_BY`：CASE 排序插入 `WHEN '开发完成' THEN 7`，测试中及之后顺延为 8~14，ELSE 15。
- workhub-server `IntakeService.DEMAND_STATUS_SORT_ORDER`：`IN_DEVELOPMENT` 与 `TESTING` 之间插入 `DEVELOPMENT_COMPLETED`（驱动项目进度报告待上线清单排序）。
- workhub-server `IntakeService.DASHBOARD_DEMAND_GROUPS`：developing 组（开发中的需求）状态列表加入 `DEVELOPMENT_COMPLETED`，阶段卡片、业务线统计、重点需求、进度报告汇总不再漏计。
- workhub-server `IntakeDemandStatusRules.normalizeEditableStatus` 报错文案补「开发完成」（清单本身已含，仅文案遗漏）。
- workhub-web `IntakeListView.vue` 需求状态筛选下拉在「开发中」「测试中」之间插入「开发完成」选项。
- workhub-web `IntakeBoardView.vue` `resolveStatusTagType`：「开发完成」与「开发中」同用 `primary`（与列表页两状态共用 developing 芯片一致）。
- workhub-web `dashboardReport.ts` `statusThemes`：「测试中」前新增「开发完成」，配色复用开发中蓝色系。

新增 `IntakeServiceDashboardGroupTest`（Mockito 仿 `IntakeServiceBoardTest`）：开发完成计入 developing 分组与业务线统计；进度报告待上线清单顺序为 开发中→开发完成→测试中。无迁移、无配置变更；回滚即还原上述清单插入。

### 2026-11-17 开发完成提测兼容

根因：IntakeDemandStatusRules 已允许开发完成执行 SUBMIT_TESTING，但 IntakeSubmitTestReviewService 的两个入口仍使用仅开发中的独立校验。两入口改为复用统一状态流转规则进行前置资格校验，不在校验时写状态；后续仍由原 advanceStage 流转。增加服务入口的双状态成功与非法状态无副作用测试，保留原有审核和外部集成测试。仅修改后端，无迁移、配置或前端变更。重启前检查在途任务，避免中断其他工作；实测不替用户实际建测试单或部署。回滚仅恢复该校验变更。

### 2026-11-17 同一任务重试状态恢复

巡检查询关联任务最新运行；FAILED 需求记录仅在最新运行已离开 FAILED/CANCELLED 时重新纳入。处理中恢复为 SUCCEEDED（已启动、待完成），COMPLETED 则直接沿既有完成流程推进。同步前核对当前 agentTaskId 仍与巡检目标一致，防止旧任务覆盖已重新分派的关联。原失败/取消不重复同步，保持历史与代码现场。使用真实 H2/MyBatis 验证运行→完成、跨轮快速完成、旧取消不重复处理。

## 1. 数据模型

- 迁移 `V20261105_203000__intake_sdd_standard_reviewer_and_submit_test_review.sql`（仅 H2 验证，未执行真实 MySQL）：
  - `pm_intake_record` 加 `development_standard`（SDD/NULL=常规）、`review_user_name`；旧数据 NULL 按常规处理，不回填。
  - 新表 `pm_intake_ai_development`（intake_id 唯一、request_key、execution_status、agent_id/agent_task_id、development_branch、message）。
  - 新表 `pm_intake_submit_test_review`（intake_id 唯一、review_status、findings_json、conclusion/summary、human_confirmed_by/at、禅道测试单与 Jenkins 构建回执）。
  - 幂等预置 AI 场景 `intake.development.sdd-analyze`、`intake.submit-test.review`（提示词与变量白名单随迁移写入，绑定内置 CLI 通道仅为满足约束）。
- 实体/Mapper：`IntakeRecordEntity` 加两字段；`IntakeMapper` 列清单/insert 同步并新增 `updateReviewUserName`；新 `IntakeAiDevelopmentMapper`、`IntakeSubmitTestReviewMapper`（注解 SQL）。
- `IntakeDetailResponse` canonical 加 `developmentStandard`、`reviewUserName`，兼容构造器补 null；`DevelopmentAnalysisDraft` canonical 尾部加 `IntakeSddDocuments sddDocuments`（proposal/design/tasks 三件套草稿），兼容构造器传 null，旧 draft_json 反序列化为 null。

## 2. 录入与审核员

- `IntakeUploadRequest`/`IntakeCreateRequest` 加 `developmentStandard`；`createIntakeRecord` 经 `normalizeDevelopmentStandard` 归一：空/SDD→`SDD`，STANDARD→NULL，其他抛错。
- 新端点 `POST /api/intake/{id}/reviewer`（权限 `intake:metadata:update` 或 `intake:record:update`）：审核员须为活跃账号，可清空；`IntakeService.updateReviewer` 更新列并记历史。
- 前端录入弹窗加"研发规范"下拉（默认 SDD）；澄清页签工具栏加审核员选择与保存。

## 3. 评估合并与 SDD 模式

- 前端删除"AI任务评估"页签；"任务评估"页签顶部并入 AI 状态 alert、AI 评估摘要、AI 识别需求点只读区，下方保留人工编辑表格与"评估完成"；列表"去评估"弹窗结构不变（本就是合并版）。
- `DevelopmentAnalysisService.executeAnalysis`：`developmentStandard=SDD` 时走 `CodexCliDevelopmentAnalysisGenerator.generateSdd(...)`，新增变量 `sddProcessSpec` 来自 `ProjectKnowledgeBaseService.readSddProcessSpec()`（配置 `knowledge.project.sddProcessPath`，默认 `schema/研发/SDD/流程.md`，相对 vaultPath，防越界，超 120k 截断；未配置/未找到明确抛错→评估 FAILED）。
- SDD 场景输出三件套存 `draft.sddDocuments`；workItems 由 sddTasks 摘要生成且 **ownerUserName 强制置空**（代码层清洗，不信任 AI 输出）。
- 确认（评估完成）负责人规则：任务指派 > 审核员（review_user_name）> SDD 模式抛错拦截；常规模式沿用 developerPool/admin 兜底（审核员优先于池子）。`toWorkItemCreateRequest` 增加 reviewer 参数。
- SDD 三件套发布：`ProjectKnowledgeBaseService.publishSddDocuments` 在确认事务内将 proposal/design/tasks 写入需求迭代目录（与研发评估.md 同目录），带 `workhub-sdd` 指纹幂等，空内容跳过。

## 4. 设计完成后 AI 分支开发

- `IntakeService.schedulePostStageAutomation` 新增 `CONFIRM_DESIGN` 分支：afterCommit 调 `IntakeAiDevelopmentService.triggerAfterDesignConfirmed`。
- 新表记录状态：PENDING→异步 start：校验研发分支、已确认任务拆解；读系统配置 `intake.aiDevelopment`（agentId 必填、environmentCode 默认 TEST、toolCodes 默认 change.prepare,change.publish）；构造 `AgentTaskSpecification`（objective=按拆解在分支开发，context 含 SDD tasks 与任务清单，artifacts=[CHANGE_SET]，scopes=[业务线+环境]）经 `AgentWorkbenchService.createTask(runImmediately=true)` 启动；未配置/权限不足/校验失败→FAILED+原因，可重试。
- 端点：`GET /{id}/ai-development`（详情权限）、`POST /{id}/ai-development/retry`（阶段操作权限，仅 FAILED）。前端任务评估页签尾部展示执行状态卡与重试按钮。

## 5. 提测 AI 审核与提测执行

- `IntakeSubmitTestReviewService`：
  - `startReview`（仅开发中研发需求）：upsert PENDING→异步 `executeReview`，经 AI 场景 `intake.submit-test.review` 输出 conclusion/findings/summary；FAIL→FAILED（停留开发中），否则 PASSED；执行异常→FAILED 带原因。
  - `confirmAndSubmit`（事务）：要求审核已完成；FAILED 需 `forceConfirm`（记录 human_confirmed_by/at）→ `ZentaoClient.createTestTask`（真实禅道 REST：POST {baseUrl}/api.php/v1/testtasks，Token 头；配置组 `zentao.base`：baseUrl/token/productId，未配置明确报错）→ `triggerJenkins(environment)`（配置组 `intake.submitTest`：jenkinsBaseUrl/jenkinsUserName/jenkinsApiToken/jenkinsJobPath.{ENV}；复用 `AgentJenkinsClient.Target/submit/queue`，返回队列或构建号）→ 回执入库 → `advanceStage(SUBMIT_TESTING)`（透传 actualEffort/actualCompletedTime/occurredAt）。任一外部失败抛错回滚，阶段停留开发中。
- `AgentJenkinsClient.Target.jobUri/instanceUri` 改 public（纯 URL 拼接，无行为变化）。
- 端点：`GET/POST /{id}/submit-test-review`、`POST /{id}/submit-test-review/confirm`。
- 前端：提交测试弹窗新增"AI 提测审核"区（状态 alert、缺陷表、重新审核按钮、4 秒轮询、失败时强制提测勾选）与测试环境下拉（业务线 supportedEnvironments 去 PROD，默认 TEST）；提交走 confirm 接口；弹窗关闭停止轮询；任务评估页签展示提测回执卡（测试单/构建链接/人工确认人）。

## 6. 验证与边界

- 后端：`IntakeSddFlowTest` 6 项（规范归一/非法拒绝、审核员校验与清空、SDD 规范读取含越界、禅道未配置快速失败、提测确认前置校验）全过；既有回归 service 162 项（1 项既有跳过）、controller IntakeController/Attachment 22 项全过；全模块编译通过。
- 前端 `npm run build`（vue-tsc + vite）通过。
- 未验证/待联调：真实禅道建单、真实 Jenkins 触发、真实 Agent 开发启动、SDD 评估真实 AI 生成（提示词经场景配置可调）；均需用户提供配置后执行，代码路径已用桩覆盖校验逻辑。
- 边界：非 SDD 模式行为不变；测试通过之后流程不变；生产 Jenkins 仍不在范围（环境过滤 PROD）；真实外部调用未配置时明确报错不伪装成功。

回滚：迁移为加列/建表/幂等种子，可按迁移回退；代码回退无数据副作用（新表数据仅新增）。

## 7. 后续变更（2026-11-10）：提测 AI 审核与 Jenkins 构建改为可选项

禅道/Jenkins 联调未就绪期间强制链路无法走通提测，本次将两步改为可选项，禅道测试单创建保持必选。

- 契约：`IntakeSubmitTestConfirmRequest` 新增 `skipAiReview`（null=false，维持先审后提）与 `triggerJenkins`（null=true 兼容存量调用）；`environmentCode` 去掉 NotBlank，仅 triggerJenkins 生效时必填（此时校验大写归一），jenkinsJobName 同理被忽略。
- `confirmAndSubmit`：skipAiReview=true 时不做审核前置校验（含 PENDING/RUNNING 中），审核记录不存在时插入 `review_status=SKIPPED`（reviewMessage="人工跳过 AI 提测审核"）承载禅道回执，已存在则不动审核字段、不要求 forceConfirm；Jenkins 触发仅 triggerJenkins=true 时执行。FAILED 未跳过时的报错文案补充"或勾选跳过 AI 审核直接提测"。
- 并发防护：`IntakeSubmitTestReviewMapper` 新增 `updateReviewOutcomeByIntakeId`（仅回写审核结果列），`executeReview` 的 RUNNING 置位与最终回写改用它，避免跳过确认与异步审核并发时旧实体全量覆盖禅道/Jenkins 回执；迁移 `V20261110_100000` 将 `pm_intake_submit_test_review.intake_id` 升级为唯一键（跳过路径确认接口也会插入记录，与触发审核并发插入重复行时冲突可见）并更新 review_status 注释含 SKIPPED。
- 前端：提交测试弹窗新增"AI 提测审核""触发 Jenkins 构建"两个开关，**默认关闭**（默认仅创建禅道测试单并推进测试中）；弹窗打开只拉取既有审核不再自动触发；AI 开关开启时按原逻辑 fetch/start/4 秒轮询，关闭即停轮询；Jenkins 开关控制测试环境与 Job 输入显隐，开启时环境必选；提交 payload 按开关传 skipAiReview/triggerJenkins，成功提示按实际动作生成；SKIPPED 状态展示"已跳过"（info）。

## 8. 后续变更（2026-11-10）：提测自动创建禅道版本

确认提测时若禅道项目下不存在与研发分支同名的版本，不再要求人工预建，改为自动创建后继续建测试单（真实报错场景：项目 ID 892 下缺 `feature/repurchase_rule_202611030001` 版本）。

- 接口契约（按开源版 21.7.8 源码 `api/v1/entries/builds.php`（tag zentaopms_21.7.8_20260108）核实）：`POST {baseUrl}/api.php/v1/projects/{projectId}/builds`，必填 `execution,product,name,builder,date`，可选 `scmPath/filePath/desc/branch`；成功 201+版本对象（含 id），失败 400/404+message。**无需"应用"字段**——2026-11-06 附录"build 自动创建需关联禅道应用暂不支持"的结论针对界面/Max 版，不适用于该 v1 REST 接口，本次推翻。
- `ZentaoClient`：`findBuildByBranch` 匹配不到返回 null（旧报错"请先在禅道按研发分支创建版本后重新提测"随之移除）；新增 `createBuild`（execution/product 取业务线映射，name=分支名，builder=`zentao.base.account` 配置账号——保证禅道合法用户，避免研发负责人非禅道账号导致 400；date=当天；desc="WorkHub 提测确认自动创建"），复用 401 重换 Token 重试；创建失败抛 `IllegalStateException("禅道自动创建版本（{分支}）…")`，沿用任一外部失败整体回滚、停留开发中的语义。`createTestTask` 编排：查版本 → 缺则建 → 建测试单。
- 已知边界：并发对同名分支提测存在小概率重复建版本（禅道版本名无唯一约束），接受；多分支产品未传 branch（现有产品非多分支，真实建单链路已验证）。
- 首次真实联调修复（2026-11-10）：禅道 21.7.8 建版本回执实际为 **201 + `{"id":"429",…}`（id 为字符串，PHP PDO 特性）**，原 `readJsonLong` 仅认 JSON 数字导致把成功当失败抛错（版本已在禅道落库、WorkHub 事务回滚停留开发中）。修复：新增 `asLong` 容忍解析（数字与数字字符串均接受），`readJsonLong` 与版本列表匹配 `findBuildByBranch` 统一改用（后者防列表 id 为字符串时误判未命中而重复建版本）；桩用例改按真实形态返回字符串 id。
- 验证：`IntakeSddFlowTest` 新增 loopback HTTP 桩 3 项（缺版本自动建并断言 execution/product/name/builder/date 载荷与 build 回填、已有版本复用不发 POST、创建被拒明确报错且不再建测试单），共 12/12；全模块编译通过。首次真实提测已验证到"版本自动创建成功（#429）"，id 解析修复后重试即走通（禅道已存在同名版本会按名复用，不重复创建）。

## 9. 后续变更（2026-11-10）：提测归属以需求关联的禅道地址为锚点

首次真实提测暴露归属错位：BL000009 未配 `zentao.mapping` 走了 defaultMapping（产品2 公司运维支持/项目892 等保专项/执行893），而该需求在禅道的真实位置是 需求 story #786「国胜项目-调整回购规则」→ 产品74 中恒卡尔恒昌 / 项目379 嘉泰-租赁保理系统 / **同名迭代923**（该业务线为"一需求一迭代"模式，固定三元组映射天然失配）。经只读库核实后用户确认模型：**按需求里配置的禅道地址找到对应需求，研发分支就是禅道的版本**。

- 归属解析（`ZentaoClient.resolveStoryMapping`，21.17.8 接口已按 tag 源码核实）：需求关联禅道地址（`pm_intake_record.zentao_url`）非空时按形态分派——
  - **迭代地址**（execution-{id} / execution-task-{id} / execution-view-{id} 等，正则 `execution(-[a-z]+)?-{id}` 优先匹配，避免 execution-story 被 story 规则误吞）：`GET /api.php/v1/executions/{id}` 迭代详情直接携带所属项目（project）与关联产品（products[]），一步取齐 产品:项目:迭代 三元组；
  - **需求详情地址**（story-view-{id} / story-{id} / storyID={id}）：`GET /api.php/v1/stories/{id}` 取 product 与 title → `GET /api.php/v1/products/{product}/projects`（过滤 type=project）→ 逐项目分页 `GET /api.php/v1/projects/{id}/executions?limit=100`（最多 10 页）按**需求名全等匹配同名迭代** → 三元组；
  版本与测试单均建到该归属下。真实数据里需求常关联的是迭代页地址（如 execution-task-923.html），故迭代形态为第一优先。
- 失败可见策略：地址非需求详情页 / 需求不存在 / 需求缺产品或标题 / 产品无关联项目 / 各项目下无同名迭代 → 明确抛错提示修正，**不静默回退**（静默回退正是 #429 建错位置的根源）；禅道地址为空 → 回退业务线映射（`zentao.mapping.{业务线编码}`/defaultMapping，BL000001 存量行为不变）。映射配置校验仍在任何网络调用前快速失败。
- `createTestTask` 签名增加 `zentaoStoryUrl`，`IntakeSubmitTestReviewService.confirmAndSubmit` 传入 `intake.getZentaoUrl()`；版本名=研发分支名、自动建版本、`asLong` 容忍解析等既有行为不变。
- 已知边界：需求详情页（story 页）显示版本需"版本关联需求"，21.7.8 REST 创建/修改接口均不含 stories 字段，无法自动化，需禅道界面手动关联（版本/测试单在同名迭代视图可见）；同名迭代跨项目重名时取产品项目列表顺序的首个匹配。
- 验证：`IntakeSddFlowTest` 15/15（新增 story 锚点与 execution 锚点正向链路断言 execution=923/product=74 且不回退 385、非需求/迭代地址、需求不存在、迭代不存在、无同名迭代等明确报错）；全模块主代码编译通过。全模块 `mvn test` 因并行在途改动（agent 域测试与 `AgentConfiguration` 同日编辑中）暂无法编译，本用例经单独编译+surefire 定向运行验证。
- 数据修正（人工）：禅道删除建错的版本 #429（等保专项下）；本需求在 WorkHub 关联禅道地址 `http://10.10.116.28/zentao/story-view-786.html` 后重新确认提测。

## 10. 后续变更（2026-11-10）：禅道测试单负责人改为提测时选择的测试负责人

经确认，禅道测试单（testtask）owner 语义是"本轮测试执行人"，应填测试负责人而非研发负责人；且 WorkHub 需求模型无测试负责人字段。

- 契约：`IntakeSubmitTestConfirmRequest` 新增必填 `testOwnerUserName`（≤64 字符，须为禅道有效账号）；`confirmAndSubmit` 在禅道建单前校验非空（缺失报"请选择测试负责人后再确认提测"并停留开发中），owner 直接取该值——**不再默认研发负责人/操作人**（`ZentaoClient` 内部空值回退配置账号仅作最后兜底）。
- 前端（workhub-web `IntakeListView.vue`）：提测弹窗顶部新增"测试负责人"必选下拉（候选复用 `fetchDeveloperOptions` 账号管理活跃账号，filterable+clearable），打开弹窗重置，提交前校验并随 payload 传 `testOwnerUserName`；`confirmSubmitTest` 类型同步（字段必填）。
- 验证：`IntakeSddFlowTest` 16/16（skip 用例断言 owner=去空格后的选择值、新增缺负责人拒绝且不建单不推进）；workhub-web `npm run build`（vue-tsc+vite）通过。
- 注意：旧版前端未传该字段会被后端拒绝，前后端需一并部署。

## 11. 后续变更（2026-11-13）：禅道自动创建对象不再携带品牌备注

用户要求自动提测/建任务不带"WorkHub 提测确认自动创建"类品牌备注（其余自动化产物的去品牌变更见 REQ-9ce9d184）。本节取代 §8 中 `createBuild` desc 契约：

- `ZentaoClient.createTestTask`（POST /api.php/v2/testtasks）与 `createBuild`（POST /api.php/v1/projects/{id}/builds）载荷不再发送 `desc` 字段——两者均为可选字段（§8 已核实 builds 必填仅 execution/product/name/builder/date），禅道侧描述留空。
- 测试单标题仍为 `测试单：{需求名}`、版本名仍为研发分支名，本就无品牌字样；WorkHub 自身提测记录（`pm_intake_submit_test_review`）只存回执编号/URL/构建信息，不受影响。
- 禅道中已存在的带该备注的历史对象不回溯修改。
- 验证：`IntakeSddFlowTest` 15/15（桩用例本就未断言 desc）；真实链路待部署后下次提测观察禅道新对象描述为空。

## 12. 后续变更（2026-11-18）：禅道测试单创建改为可选项

用户要求提交测试时可选择是否向禅道提测试单（此前禅道建单无条件执行，未设研发分支等场景会整单阻断提测）。经确认测试负责人保持始终必填，开关默认开启兼容存量调用。

- 契约：`IntakeSubmitTestConfirmRequest` 新增可选 `createZentaoTestTask`（Boolean，缺省/null 视为 true，语义与 `triggerJenkins` 缺省一致）；`confirmAndSubmit` 仅在开关为 true 时调用 `ZentaoClient.createTestTask` 并回写 `zentaoTestTaskNo/Url`，为 false 时显式清空两列（防回滚后重提残留旧回执）。`testOwnerUserName` 必填校验保持不变（不提单时同样必填）。无数据库结构变更（回执列本可空）。
- 前端（workhub-web `IntakeListView.vue`）：提测弹窗在「测试负责人」与「触发 Jenkins 构建」之间新增「禅道测试单」开关（提单/不提单，默认开启，打开弹窗重置为开启）；Jenkins 关闭态提示随禅道开关联动（禅道关闭时为「仅推进测试中。」）；payload 新增 `createZentaoTestTask`；完成提示按「是否提单 × 是否构建」组合输出。`confirmSubmitTest` 类型同步新增可选字段。
- 验证：`IntakeSddFlowTest` 27/27（新增用例：不提单不建单仍推进阶段、既有回执被清空、不提单仍必填测试负责人）；workhub-web `vue-tsc --noEmit -p tsconfig.app.json` 通过。


## 2026-11-17 自动开发触发与重试

阶段提交后以独立事务保存待执行状态，事务提交后提交后台执行。后台同一事务锁定执行记录并创建员工任务及首次运行，同时回写执行结果。重复提交由执行记录锁防止重复建任务。单次手动任务通过立即运行触发，关闭周期调度。失败摘要沿用当前详情。


## 2026-11-17 提测 Jenkins 测试部署闭环方案（待确认）

### 当前证据

- 系统集成实例 jenkins 已能通过 MCP 认证查询。
- 浏览器参数页实际提供 deploy_test_env=test-scs-server（唯一选项，10.10.116.43）、thctay_public_branch（默认 master）、project_branch（默认 master，包含 feature/repay_plan_query_202611050003）。MCP 的空参数列表不足以代表 Job 无参数，需修正读取参数定义的位置与结果截断。
- 构建 #124 为用户石浩触发，SUCCESS，业务 checkout=e7c66a14289955b26f6e263fefa361e73d1f52fa，部署至 10.10.116.43 的 /mnt/scs-server/scs-saps；流水线包含 execute sql，不能把构建成功等同于应用健康验证完成。
- 本轮 git ls-remote 核实需求远端仍为 e7c66a1；本地 HEAD 为 0775973224d6bcc7c77933950bdfea6f8da5ae37，包含资产管理费口径调整及用户删除测试文件，未推送。不得恢复用户删除的测试文件。

### 实施方案

1. 提测连接复用系统集成实例及其密钥引用，不复制或回显密钥。新增提测测试部署绑定，精确关联业务线/系统/TEST/实例/Job，以及已核实的固定参数。仅 TEST 可进入本路径；输入 Job 与绑定不符、环境不符、涉及系统未完整绑定时拒绝。保留数字员工既有生产发布审批链，不复用或放宽它。
2. WorkHub 将需求研发分支与测试发布分支分别建模。单需求提测默认发布其研发分支；多需求合测时从当时远程 master 的提交创建新的测试集成分支，合入用户选定的各需求研发分支，记录基线 SHA、来源分支及 SHA、合并结果 SHA。需求 developmentBranchName 不变；Bug 修复仍提交原研发分支，重新合测时重新记录实际合入版本；合并冲突必须停止，不擅自选边解决。master 始终代表生产，本测试流程不向 master 推送、不把测试集成分支当上线来源。上线仍按开发分支处理，具体生产发布与 master 同步机制不在本轮实施范围。参数由后端构造：deploy_test_env=test-scs-server；project_branch=本次测试发布分支；thctay_public_branch=master（沿用既有测试默认）。触发前查询 Job 参数及测试目标是否匹配，不静默回退到默认分支。预检记录远端预期提交，构建后核对业务仓库实际 checkout；现有 Job 仅提供分支选择，不能宣称构建输入已精确锁定 SHA。
3. 持久化独立部署尝试、参数快照、queueId、构建号/链接/状态/失败摘要、预期与实际版本。按需求锁定并拒绝并行重复请求。提交结果不明标记待核实，禁止自动重发；外部请求不依靠本地事务回滚来假装撤销。禅道创建与部署结果分别保留，避免 Jenkins 失败后丢失已创建测试单回执。
4. 通过 WorkhubJob 和 sys_job_config 调度轮询已有队列/构建，按排队、构建中、成功、失败、取消、待核实回写。不新增静态 @Scheduled。SUCCESS 还需核对版本；服务健康验证与 Jenkins 构建结果分别展示。
5. 前端支持单需求提测或选择多个需求合测，分别显示来源开发分支、本次测试发布分支、绑定的固定测试目标及部署状态；有本轮有效绑定时默认开启 Jenkins，已测试中支持只补发部署。当前需求不得通过回退阶段/重复建测试单来重试发布。
6. 验证覆盖单需求默认研发分支、多需求从 master 新建测试分支、来源版本记录、合并冲突停止、Bug 修复回原开发分支、master 不被测试流程修改；使用本地 HTTP 桩覆盖参数、拒绝生产/错 Job、重复提交、未知回执不重发、重启恢复轮询、失败回写、版本不符。完成后以需求 202611050003 在已确认测试 Job 进行一次真实联调，记录构建编号、参数、checkout SHA、测试服务结果。

### 边界、回滚与待确认

本轮不修改 Jenkins 流水线/共享库，不变更生产权限或凭据。用户已补充 Job 的 execute sql 是 Jenkins 集成脚本平台后自动执行所发布系统 dbscript 目录脚本的既有流程。本轮沿用此流程，不增加 WorkHub 直连业务库或独立执行 SQL 的能力，不修改脚本平台配置；代码构建及脚本平台必须使用同一本次测试发布分支，脚本步骤失败按流水线结果展示。脚本平台的执行基线、去重和幂等语义尚未核实，不宣称每次仅执行新增脚本。停止自动化可停用本测试绑定及轮询任务；已提交 Jenkins 构建不能靠停用配置撤销，须只读核实其结果。失败后不自动回滚应用或数据库。

生产防护、状态跟踪和版本核验按本节实现；数据库表/字段与端点细节在确认后按既有工程规范落地，迁移仅涉及 WorkHub 自身。

