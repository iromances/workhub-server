# Tasks：需求任务评估SDD化与AI开发提测流程升级

## 1. 元信息

- 需求编码：REQ-a71851db-1d1c-4f20-b99e-330950af6ad9
- 需求名称与唯一需求目录：需求任务评估SDD化与AI开发提测流程升级，`docs/业务线/BL000012-工作台/需求迭代/REQ-a71851db-1d1c-4f20-b99e-330950af6ad9-需求任务评估SDD化与AI开发提测流程升级/`
- 涉及仓库与功能域：workhub-server（需求清单评估/阶段/提测、AI 场景、Agent 编排）；workhub-web（需求清单页面）
- Proposal 版本：v1
- Design 版本：v1
- 目标环境与分支：server `codex/development-20261102`；web `codex/development-20261103`
- 负责人/评审人：用户

## 2. 任务总览

| 任务 | 交付物 | 前置任务 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 迁移脚本（加列/两新表/AI 场景种子） | 无 | 设计 1；AC-01/07 | 已完成 |
| T-02 | 后端研发规范与审核员链路（实体/Mapper/上传/reviewer 端点） | T-01 | 设计 2；AC-01/02 | 已完成 |
| T-03 | SDD 模式评估（知识库定向读、场景、负责人置空）与确认发布三件套、负责人默认规则 | T-01 | 设计 3；AC-04/05 | 已完成 |
| T-04 | 评估页签合并（前端） | 无 | 设计 3；AC-03 | 已完成 |
| T-05 | 设计完成后 Agent 分支开发（表/服务/触发/端点/前端状态卡） | T-01 | 设计 4；AC-06 | 已完成 |
| T-06 | 提测 AI 审核 + 人工确认 + ZentaoClient + Jenkins 触发（前后端） | T-01 | 设计 5；AC-07/08 | 已完成 |
| T-07 | 定向测试与全量回归、前端构建 | T-02~T-06 | 设计 6；AC-01~09 | 已完成 |
| T-08 | 文档收尾（本三件套、迭代日志、现状、测试用例补充） | T-01~T-07 | Proposal 全部 | 已完成 |

执行顺序：T-01 → T-02/T-03/T-05/T-06（后端） → T-04（前端合并） → T-07 → T-08。

## 3. 任务明细与真实结果

### T-01：数据与配置基线（已完成）

- 交付：`V20261105_203000__intake_sdd_standard_reviewer_and_submit_test_review.sql`；`pm_intake_record` 加 `development_standard`/`review_user_name`；新表 `pm_intake_ai_development`、`pm_intake_submit_test_review`（含 conclusion/summary 列）；幂等种子 `intake.development.sdd-analyze`、`intake.submit-test.review`。
- 结果：迁移仅在代码评审与 H2 语义层面验证（未执行真实 MySQL/Flyway，遵守"未迁移"边界）。

### T-02：研发规范与审核员（已完成）

- 交付：`IntakeUploadRequest`/`IntakeCreateRequest`/`IntakeRecordEntity`/`IntakeMapper`（列、insert、`updateReviewUserName`）扩展；`IntakeService.normalizeDevelopmentStandard`（空/SDD→SDD、STANDARD→NULL、非法拒绝）、`updateReviewer`（活跃账号校验、可清空、记历史）；`POST /{id}/reviewer` 端点；前端录入"研发规范"下拉（默认 SDD）、澄清页签审核员选择。
- 结果：`IntakeSddFlowTest` 3 项断言覆盖（规范默认/非法、审核员校验与清空）。

### T-03：SDD 模式评估与确认（已完成）

- 交付：`IntakeSddDocuments` 模型；`DevelopmentAnalysisDraft.sddDocuments`；`CodexCliDevelopmentAnalysisGenerator.generateSdd`（SDD 场景、sddProcessSpec 变量、owner 代码层置空）；`ProjectKnowledgeBaseService.readSddProcessSpec`（配置化相对路径、防越界、截断）与 `publishSddDocuments`（指纹幂等）；`DevelopmentAnalysisService.executeAnalysis` SDD 分支、confirm 负责人规则（指派>审核员>SDD 拦截）与三件套发布。
- 结果：知识库读取（含越界拒绝）有测试；确认负责人规则经 `toWorkItemCreateRequest(draft,item,reviewer)` 落实，SDD 无负责人无审核员抛错。

### T-04：评估页签合并（已完成）

- 交付：`IntakeListView.vue` 删除"AI任务评估"页签，"任务评估"页签顶部并入 AI 状态/摘要/需求点只读区。
- 结果：前端构建通过；页面联调待部署。

### T-05：AI 分支开发（已完成）

- 交付：`pm_intake_ai_development` 实体/Mapper；`IntakeAiDevelopmentService`（CONFIRM_DESIGN afterCommit 触发、配置 `intake.aiDevelopment`、AgentWorkbench 任务创建、失败可重试）；`IntakeService` 构造器扩展与自动化接线；`GET /{id}/ai-development`、`POST /{id}/ai-development/retry`；前端执行状态卡。
- 结果：未配置 Agent 时明确 FAILED 并可在配置后重试；真实启动待用户提供 agentId 配置后联调。

### T-06：提测审核与提测执行（已完成）

- 交付：`IntakeSubmitTestReviewService`（startReview 异步审核、confirmAndSubmit 事务：强制确认→禅道建单→Jenkins 触发→回执入库→阶段推进）；`ZentaoClient`（真实 REST，未配置快速失败）；Jenkins 薄封装（`AgentJenkinsClient.Target` 复用，jobUri/instanceUri 改 public）；三个端点；前端提测弹窗审核区、环境下拉、轮询与强制确认。
- 结果：禅道未配置快速失败、审核未完成禁止确认、非开发中禁止审核均有测试；真实禅道/Jenkins 联调待配置。

### T-07：测试与构建（已完成）

- 后端：`IntakeSddFlowTest` 6/6 通过；service 模块 intake/development/codex 回归 162 项通过（1 项既有跳过）；controller `IntakeControllerTest`/`AttachmentControllerTest` 22 项通过；workhub 全模块 `compile` 通过。
- 前端：`npm run build`（vue-tsc 类型检查 + vite 构建）通过。

### T-08：文档（已完成）

- 本三件套、迭代日志 2026-11-05 条目、现状.md 更新、需求清单测试用例文档追加"评估合并与 SDD/提测审核"用例域。

## 4. 覆盖与评审

### 附：2026-11-06 外部系统核实与客户端修正

按用户指示经浏览器与 MCP 只读库核实外部系统并修正实现：

- 禅道：`http://10.10.116.28/zentao`，开源版 21.7.8；个人页无固定 API 密钥，REST 采用账号密码换 Token。**真实联调已打通建单全链路**：POST /v1/tokens 换 Token（201）→ GET /v1/projects/{id}/builds 按研发分支名匹配版本 → POST /v2/testtasks 建单（**21.7.8 缺陷绕法：`productID` 与 `product` 双字段同传**，v1 无创建接口、单传 product 报 Product does not exist）；已创建验证测试单 #605（等保产品，标题注明可删除）。
- Jenkins：`http://10.10.116.21:10004`，已用登录会话生成 API Token（用户 shihao，令牌名 workhub-intake-submit，经浏览器验证 200/471 Job）。Job 按环境前缀组织（dev/test/sit/pre/prod/uat，test-* 共 154 个，命名 `{env}-{system}`）。提测触发改为 `jenkinsJobPrefix.{ENV}` + 首个任务系统拼接，confirm 请求可显式指定 `jenkinsJobName`；前端提测弹窗增加 Job 输入并预填默认值。
- 最终配置契约：`zentao.base`（baseUrl/account/password[SECRET]/defaultMapping=产品ID:项目ID:执行ID）+ `zentao.mapping.{业务线编码}` 覆盖；build 自动创建需关联禅道"应用"暂不支持，改为按分支名匹配研发已建版本，匹配不到明确报错提示先建版本。
- 回归：`IntakeSddFlowTest` 6 项（含映射格式/分支缺失/凭据缺失校验）、前端构建通过；Jenkins 真实触发与 WorkHub 配置录入待用户配合完成。

### 附：2026-11-06 配置录入完成

经用户本机 WorkHub 前端（127.0.0.1:9529，系统配置页）代录并核对，9 项配置全部落库启用：`zentao.base`（baseUrl/account/password[SECRET]/defaultMapping=2:892:893）、`zentao.mapping.BL000001`=59:385:896、`intake.submitTest`（jenkinsBaseUrl/jenkinsUserName/jenkinsApiToken[SECRET]/jenkinsJobPrefix.TEST=test）。SECRET 项脱敏存储展示。首次真实提测时整链路（AI 审核→禅道建单→Jenkins 构建）即用该配置。

### 附：2026-11-10 提测 AI 审核与 Jenkins 构建改为可选项

用户确认将 AI 审核与 Jenkins 构建变为可选项（禅道建单保持必选），弹窗两开关默认关闭（默认直接提测）。规格补充见 design 第 7 节。

- 后端：`IntakeSubmitTestConfirmRequest` 增 `skipAiReview`（null=false）/`triggerJenkins`（null=true 兼容存量），`environmentCode` 去必填仅在触发构建时校验；`confirmAndSubmit` 按开关分支，跳过审核时无记录则插入 `SKIPPED` 行承载回执、不要求 forceConfirm；`IntakeSubmitTestReviewMapper` 增 `updateReviewOutcomeByIntakeId` 窄回写，`executeReview` 改用，消除跳过确认与异步审核并发覆盖回执的竞态；迁移 `V20261110_100000`（review_status 注释补 SKIPPED、intake_id 升级唯一键防并发重复行）。
- 前端：提测弹窗增两开关（默认关），AI 开关控制审核区显隐与轮询启停（开启才 fetch/触发/轮询，打开弹窗不再自动触发审核），Jenkins 开关控制测试环境/Job 显隐与必选校验；confirm payload 按开关组装，成功提示按实际动作生成；SKIPPED 展示"已跳过"。
- 验证：`IntakeSddFlowTest` 9/9（新增跳过+不触发构建、RUNNING 中跳过放行、触发构建缺环境拒绝 3 项）；service 全量 1028 项中除 `KnowledgeArchiveWorkflowTest`、`BusinessLogSearchServiceTest` 两项工作区既有失败（在途 docs/业务线 重构与日志范围配置改动，与本变更无引用关系）外全过；controller/mcp-server/job 模块编译通过；前端 `npm run build`（vue-tsc+vite）通过。
- 剩余风险：真实禅道/Jenkins 首次走可选链路的端到端联调仍待执行；`V20261110_100000` 若线上已存在同 intake_id 重复行迁移会失败可见，需人工裁决后重跑。

### 附：2026-11-10 提测自动创建禅道版本

真实提测报错「禅道项目（ID 892）下没有名为「feature/repurchase_rule_202611030001」的版本」后，用户确认提测时应由系统自动创建版本与测试单。规格补充见 design 第 8 节；确认人：用户（批准实施计划，2026-11-10）。

- 后端：`ZentaoClient.findBuildByBranch` 匹配不到返回 null，新增 `createBuild`（POST /api.php/v1/projects/{id}/builds，必填 execution/product/name/builder/date，已按开源版 21.7.8 tag 源码核实，无需"应用"字段，推翻 2026-11-06 旧结论；builder 用配置账号），`createTestTask` 改为查不到即建；401 重试与失败报错沿用既有模式。
- 验证：`IntakeSddFlowTest` 12/12（新增 loopback HTTP 桩 3 项：自动建版本载荷与回填断言、既有版本复用不发 POST、创建被拒明确报错）；`mvn compile` 全模块通过。
- 首次真实提测（2026-11-10）：禅道已自动创建版本 **#429**（feature/repurchase_rule_202611030001，产品 2/项目 892/执行 893），但 21.7.8 回执 `id` 为字符串（`"429"`，PHP PDO 特性），`readJsonLong` 仅认 JSON 数字误报"创建失败"并回滚。修复：`asLong` 容忍解析统一用于 `readJsonLong` 与 `findBuildByBranch`，桩用例改按真实字符串 id 形态，12/12 复验通过。
- 待办：部署修复版后重新确认提测——禅道已存在同名版本将按名复用直接建测试单（多次重试若在禅道留下同名重复版本可人工清理）；WorkHub 侧此前均回滚未留回执。

### 附：2026-11-10 提测归属改为按需求禅道地址定位

真实提测后发现版本/测试单建到了 defaultMapping 的错误归属（产品2 公司运维支持/项目892 等保专项），经禅道只读库核实该需求真实归属为 产品74/项目379/同名迭代923（story #786）；用户确认模型："按需求里配置的禅道地址找到对应的需求，研发分支就是禅道的版本"。规格见 design 第 9 节；确认人：用户（2026-11-10）。

- 后端：`ZentaoClient` 新增 `resolveStoryMapping`（story 详情 → 产品关联项目 → 同名迭代，接口均按 21.7.8 tag 源码核实，迭代列表分页 100/页×最多 10 页），`createTestTask` 增 `zentaoStoryUrl` 参数，`confirmAndSubmit` 传入 `intake.getZentaoUrl()`；地址存在但定位失败明确报错不静默回退，地址为空回退业务线映射；映射配置校验先于网络调用。
- 追加（真实数据触发）：需求关联的禅道地址实为迭代页（execution-task-923.html），新增迭代地址形态优先解析——`GET /api.php/v1/executions/{id}` 详情直接携带所属项目与关联产品，一步定位三元组；未识别出需求/迭代 ID、迭代不存在等均有明确报错。
- 验证：`IntakeSddFlowTest` 14/14（新增 story 锚点正向链路与三种定位失败用例）；全模块主代码编译通过；全模块 test 编译被并行在途 agent 域改动阻塞（`AgentConfiguration` 同日 16:50 被改），本用例以单独编译+`surefire:test` 定向运行验证。
- 数据修正（人工待办）：禅道删除版本 #429；WorkHub 需求关联禅道地址 `story-view-786.html` 后部署重试提测，预期版本+测试单出现在 产品74/项目379/迭代923 下。
- BL000009 无需再配 `zentao.mapping`（story 锚点优先于映射；defaultMapping 仅兜底未关联禅道地址的需求）。

### 附：2026-11-10 禅道测试单负责人改为提测选择测试负责人

用户确认：禅道测试单负责人应为测试负责人（本轮测试执行人），不是研发负责人。规格见 design 第 10 节；确认人：用户（2026-11-10）。

- 后端：`IntakeSubmitTestConfirmRequest` 加必填 `testOwnerUserName`；`confirmAndSubmit` 建单前校验并以其为测试单 owner，不再默认研发负责人。
- 前端：提测弹窗新增"测试负责人"必选下拉（账号管理活跃账号），payload 携带。
- 验证：`IntakeSddFlowTest` 16/16；workhub-web 构建通过。
- 部署注意：后端强制必填，旧前端不传会被拒，前后端需一并部署。


### 附：2026-11-12 撤销"审核员"设计（后续变更④）

用户要求删除审核员设计（"需求澄清的审核员是干嘛的"→"删掉该设计"）。原设计：澄清页签可选审核员（`pm_intake_record.review_user_name`），作为 AI 拆解任务的默认负责人（SDD 模式必填兜底）。撤销后语义变化：

- SDD 模式确认研发评估：任务负责人只认**任务项自身指派**，未指派直接拦截（报错文案改为"请指派任务负责人后再确认"）。
- 常规模式：任务负责人兜底顺序改为 任务项负责人 > 开发人员池首位。
- 整链路移除：`POST /api/intake/{id}/reviewer` 端点、`updateReviewer`、`IntakeReviewerUpdateRequest`、Mapper 列与 `updateReviewUserName`、详情响应字段、前端选择器与保存逻辑；迁移 `V20261112_150000__drop_intake_review_user_name.sql` 删列（数据仅为默认负责人提示用途，随列删除）。
- 验证：`IntakeSddFlowTest` 15/15（删除 updateReviewer 用例）、DevelopmentAnalysisServiceTest 16/16、澄清分析 9/9；全模块主代码编译与前端构建通过。

### 附：2026-11-13 禅道自动创建对象去掉品牌备注（后续变更⑤）

用户要求自动提测/建任务不带"WorkHub 提测确认自动创建"备注。规格补充见 design 第 11 节；确认人：用户（2026-11-13）。其余自动化产物（GitLab MR、Git 提交、知识库归档）的同源去品牌变更见 REQ-9ce9d184-d6c0-403c-9b16-52bee5e5a43b-自动化产物去除WorkHub品牌字样。

- 后端：`ZentaoClient` 建测试单与自动建版本载荷均不再发送 `desc`（可选字段，禅道侧描述留空）；历史带备注对象不回溯修改。
- 验证：`IntakeSddFlowTest` 15/15；真实链路待部署后下次提测观察。


- Proposal AC-01~09 映射：AC-01/02→T-02；AC-03→T-04；AC-04→T-03；AC-05→T-03；AC-06→T-05；AC-07/08→T-06；AC-09→T-03/T-07 回归（非 SDD 路径与既有用例未改动行为）。
- 未覆盖项（联调依赖用户配置）：真实禅道建单、真实 Jenkins 构建、真实 Agent 开发、SDD 评估真实 AI 推理、页面联调。
- 系统设计更新：现状.md 已补记；迭代日志已记录。
- 完整规格统一确认：用户 2026-11-05 需求原话 + 四项决策（设计完成后触发 AI 开发、接真实禅道 API、确认解除 Jenkins 暂缓、一次全做）作为实施确认；规格版本 v1，确认人：用户。
