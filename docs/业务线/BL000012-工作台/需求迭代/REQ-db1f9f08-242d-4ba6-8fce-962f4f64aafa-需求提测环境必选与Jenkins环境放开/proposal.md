# Proposal：需求提测环境必选与 Jenkins 环境放开

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 需求提测环境必选与 Jenkins 环境放开（REQ-db1f9f08-242d-4ba6-8fce-962f4f64aafa） |
| 涉及仓库与功能域 | workhub-server：需求管理提测确认（IntakeSubmitTestReviewService、IntakeTestDeploymentService）；workhub-web：需求管理提测弹窗（IntakeListView.vue、IntakeTestDeploymentPanel.vue） |
| 所属项目或业务线 | 工作台（BL000012） |
| 版本 | v1.0 |
| 状态 | 已确认（用户 2026-11-23 指令"需求管理提测的时候增加选择环境必选"，并经方案问题确认：环境始终必选；Jenkins 构建环境本次一并放开可选） |
| 目标系统 | WorkHub Web + WorkHub Server |
| 原始需求来源及版本 | 2026-11-23 用户："需求管理提测的时候增加选择环境必选"、"jenkins构建环境也可选" |
| 研发分支 | server/web 均为 `codex/development-20261122`（当前开发线） |
| 负责人 | AI（ZCode）实施 |
| 评审人 | 用户 |

## 1. Why：背景与问题

- 当前问题及证据：提测确认弹窗（`IntakeListView.vue` SUBMIT_TESTING 区块）没有可见的环境选择控件，`submitTestEnvironmentCode` 仅在脚本中静默置为 TEST；后端 `IntakeSubmitTestReviewService.confirmAndSubmit` 仅在开启 Jenkins 构建时要求环境且硬编码只允许 TEST，`IntakeTestDeploymentService.validateBinding` 同样强制 TEST。智能柜（TEST/DEV/SIT）、叉车（TEST/SIT）等多环境业务线无法选择提测环境。
- 受影响用户/角色：需求管理提测的研发与测试人员。
- 不解决的后果：环境多配置的业务线提测时无法指定目标环境；环境值对用户不可见、不可控。
- 先例：业务线支持环境配置（REQ-95321398，`pm_business_line.supported_environments`）；需求任务评估 SDD 化与 AI 开发提测流程升级（REQ-a71851db，建立提测确认三开关）。

## 2. What：目标与范围

### 本期目标

1. 提测确认弹窗新增"测试环境"必选下拉：选项为业务线支持环境去掉 PROD（未配置时回退 TEST），默认 TEST；无论是否触发 Jenkins 构建都必须选择。
2. 后端提测确认环境始终必填：非空、非 PROD、且属于业务线支持环境；所选环境写入提测回执（`jenkins_environment_code` 列，语义调整为提测确认环境）。
3. Jenkins 测试发布放开到非生产环境：绑定校验按绑定环境推导 Job/发布目标前缀（`{env小写}-`），提测确认时所选环境须与发布绑定环境一致；PROD 永久禁止。

### 本期交付范围

1. workhub-server：`IntakeSubmitTestConfirmRequest` 注释更新；`IntakeSubmitTestReviewService.confirmAndSubmit` 环境必选与业务线/绑定一致性校验、回执环境写入；`IntakeTestDeploymentService.validateBinding` 环境泛化；`IntakeSddFlowTest`、`IntakeTestDeploymentServiceTest` 用例更新与新增。
2. workhub-web：`IntakeListView.vue` 提测弹窗环境必选下拉、提交校验与请求体、环境切换联动发布面板、Jenkins 开关禁用态、详情回执"提测环境"指标卡；`IntakeTestDeploymentPanel.vue` 按环境过滤绑定、选项 label 显示真实环境、change 事件附带绑定环境。

### 明确不做

1. 不改数据库表结构（沿用 `pm_intake_submit_test_review.jenkins_environment_code` 列）。
2. 不放开 PROD（防生产误发布护栏永久保留）。
3. 不改 GitLab 合测分支 `test/workhub-{uuid}` 前缀命名与 Jenkins `deploy_test_env` 单选参数校验。
4. 不在本期新增任何 SIT/DEV 绑定数据（由运维按 `{env小写}-{system}` 约定在 sys_config `intake.testDeploy`/`bindings` 登记）。

### 约束与依赖

- 依赖的代码和文档：业务线支持环境配置（`pm_business_line.supported_environments`）、测试发布绑定配置（sys_config `intake.testDeploy`/`bindings`）。
- 业务/合规约束：仓库安全规范；禁止生产发布护栏。
- 既有系统/接口/数据约束：提测确认接口路径与字段不变（`POST /intake/{id}/submit-test-review/confirm`）。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 提测弹窗显示"测试环境"必选下拉，选项=业务线支持环境去 PROD（未配置回退 TEST），默认 TEST；不选择无法提交（前端 warning） | 页面走查 + 代码 | 用户 |
| AC-02 | 不开 Jenkins 构建提测也必须选环境；后端缺环境报"请选择测试环境后再确认提测" | 单元测试 + 页面走查 | 用户 |
| AC-03 | 选择非 TEST 环境（如 SIT）且存在匹配绑定时可完成提测并触发对应环境构建；回执记录所选环境 | 单元测试 | 用户 |
| AC-04 | PROD 环境被前后端双重拒绝；绑定环境与所选环境不一致时后端报"测试环境与所选测试发布目标不符" | 单元测试 | 用户 |
| AC-05 | 后端 `mvn -pl workhub-service -am test` 全量通过；前端 `npm run build` 通过 | 构建输出 | AI |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
