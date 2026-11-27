# Proposal：设计完成可选自动AI开发

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 设计完成可选自动AI开发（REQ-26d4ff19-db5f-4525-8768-8a3ddb65ff35） |
| 涉及仓库与功能域 | workhub-server：需求管理阶段推进与 AI 分支开发（IntakeService、IntakeAiDevelopmentService、IntakeStageActionRequest、IntakeController）；workhub-web：需求清单阶段弹窗与 AI 分支开发区块（IntakeListView.vue、types/work-item.ts） |
| 所属项目或业务线 | 工作台（BL000012） |
| 版本 | v1.0 |
| 状态 | 已确认（用户 2026-11-24 指令"需求清单，设计完成后支持选择是否自动开发"；交互问题未及时应答，按推荐方案执行并于计划审批时确认：设计完成弹窗内开关、默认启用、保留手动启动入口） |
| 目标系统 | WorkHub Web + WorkHub Server |
| 原始需求来源及版本 | 2026-11-24 用户："需求清单， 设计完成后支持选择是否自动开发" |
| 研发分支 | server/web 均为 `codex/development-20261122`（当前开发线） |
| 负责人 | AI（ZCode）实施 |
| 评审人 | 用户 |

## 1. Why：背景与问题

- 当前问题及证据：研发需求确认设计完成（`CONFIRM_DESIGN`，待设计→开发中）后必然自动触发 AI 分支开发——`IntakeService.advanceStage` 先强制预检开发数字员工（`validateBeforeDesignConfirmed`，未配置则拒绝确认），事务提交后无条件调用 `IntakeAiDevelopmentService.triggerAfterDesignConfirmed` 创建 Agent 开发任务。用户没有"本次人工开发"的选择。
- 受影响用户/角色：需求清单推进设计完成的研发人员。
- 不解决的后果：只想人工开发的需求也被迫先配置数字员工并自动启动 Agent 任务；无数字员工可用时需求卡在待设计无法推进。
- 先例：开始澄清动作的 `aiClarificationEnabled` 开关（`IntakeStageActionRequest` 字段 + 阶段弹窗 el-switch + 后端跳过自动化），本次沿用同一模式。

## 2. What：目标与范围

### 本期目标

1. 设计完成确认弹窗新增「自动 AI 开发」开关：默认启用；关闭时仅推进阶段到开发中，不预检数字员工、不触发 AI 开发，并在需求历史留痕。
2. 选择不自动开发的需求在开发中阶段可于详情「AI 分支开发」区块手动点「启动 AI 开发」补发，复用现有 retry 端点（无执行记录时视为首次启动）。

### 本期交付范围

1. workhub-server：`IntakeStageActionRequest` 新增 `aiDevelopmentEnabled`（仅设计完成动作使用，未传默认启用）；`IntakeService.advanceStage` 关闭时跳过数字员工预检、`schedulePostStageAutomation` 关闭时跳过触发、历史明细追加「AI 自动开发：启用/未启用」；`IntakeAiDevelopmentService.retry` 无记录且开发中时创建并启动；`IntakeController` retry 端点 javadoc 更新；`IntakeAiDevelopmentServiceTest`、`IntakeServiceTest` 用例新增。
2. workhub-web：`IntakeStageActionRequest` 类型加 `aiDevelopmentEnabled?: boolean`；`IntakeListView.vue` 设计完成弹窗开关（仅研发需求显示）、提交 payload 传值、AI 分支开发区块支持无记录展示「未启动」与「启动 AI 开发」按钮。

### 明确不做

1. 不新增数据库表或列（选择按次生效不持久化，执行记录复用 `pm_intake_ai_development`）。
2. 不改运维链路（数据提取/运维需求不走 `CONFIRM_DESIGN`）与提测链路（`IntakeSubmitTestReviewService` 走 `SUBMIT_TESTING`）。
3. 不新增 API 端点（手动启动复用 `POST /api/intake/{id}/ai-development/retry`）。
4. 不改 Agent 任务创建、watch 巡检与自测通过自动流转逻辑。

### 约束与依赖

- 依赖的代码和文档：`IntakeDemandStatusRules`（阶段状态机）、`pm_intake_ai_development` 执行记录表、系统配置 `intake.aiDevelopment`。
- 业务/合规约束：仓库安全规范；阶段推进权限 `intake:stage:operate` 不变。
- 既有系统/接口/数据约束：`POST /api/intake/{id}/stage-action` 请求体向后兼容（未传 `aiDevelopmentEnabled` = 启用，等价现状）。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 研发需求设计完成弹窗显示「自动 AI 开发」开关，默认启用；启用文案说明自动开发+自测，关闭文案说明人工开发可稍后启动 | 页面走查 + 代码 | 用户 |
| AC-02 | 关闭开关确认设计完成：阶段正常推进到开发中，不预检数字员工、不创建 AI 开发执行记录，需求历史含「AI 自动开发：未启用（人工开发）」 | 单元测试 | 用户 |
| AC-03 | 开关启用或未传字段（旧客户端/MCP）：行为与现状一致——预检数字员工并自动触发 AI 开发 | 单元测试（既有用例不回归） | 用户 |
| AC-04 | 未自动开发的需求处于开发中时，详情 AI 分支开发区块显示「未启动」与「启动 AI 开发」按钮；点击后创建执行记录并启动 Agent 任务 | 单元测试 + 页面走查 | 用户 |
| AC-05 | 后端 `mvn -pl workhub-service -am test`（含 controller 相关模块）全量通过；前端 `npm run build` 通过 | 构建输出 | AI |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
