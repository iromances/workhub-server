# Design：设计完成可选自动AI开发

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 设计完成可选自动AI开发（REQ-26d4ff19-db5f-4525-8768-8a3ddb65ff35） |
| 涉及仓库与功能域 | workhub-server：需求阶段推进（IntakeService）、AI 分支开发编排（IntakeAiDevelopmentService）、阶段动作请求模型与控制器；workhub-web：需求清单阶段弹窗与详情 AI 分支开发区块（IntakeListView.vue）、请求类型（types/work-item.ts） |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随完整规格经 2026-11-24 计划审批确认） |
| 当前事实核对环境、分支、时间 | 本机工作区，server/web 均在 `codex/development-20261122` 且干净；2026-11-24 核对 |

## 1. 现状与证据

- 阶段推进入口：`IntakeController` → `IntakeService.advanceStage(id, IntakeStageActionRequest, ...)`（workhub-service/.../intake/IntakeService.java:1099）。`CONFIRM_DESIGN` 分支：1116-1119 行对研发需求强制 `intakeAiDevelopmentService.validateBeforeDesignConfirmed`（要求 `intake.ai_agent_id` 或系统配置 `intake.aiDevelopment.agentId` 指定且可授权的数字员工，否则抛"请先选择数字员工再确认设计完成"）。
- 自动触发：`schedulePostStageAutomation`（同文件 2965-2985 行）在 `CONFIRM_DESIGN` 且研发需求时 `runAfterCommit` 调 `triggerAfterDesignConfirmed`，后者另开事务 upsert `pm_intake_ai_development`（PENDING）并提交后台线程创建 Agent 任务。同方法 2973-2976 行已有 `aiClarificationEnabled` 为 FALSE 时跳过澄清自动化的先例。
- 请求模型：`IntakeStageActionRequest`（workhub-model）已有 `Boolean aiClarificationEnabled`（仅开始澄清动作使用，未传默认启用）。
- 手动重试：`POST /api/intake/{id}/ai-development/retry`（权限 `intake:stage:operate`）→ `IntakeAiDevelopmentService.retry`：无执行记录时抛"当前需求尚未触发 AI 开发"；FAILED 或 PENDING-无任务时 resetPending 后重新 submit。
- 前端：阶段弹窗在 `IntakeListView.vue`（4062-4075 行为「AI 辅助澄清」开关模板先例；1837-1839 行 payload 按 action 决定是否传 `aiClarificationEnabled`）；详情 AI 分支开发区块 5059 行 `v-if="aiDevelopment"` 仅在有执行记录时渲染，唯一操作是「重试」（开发中且 FAILED/PENDING-无任务，5063 行）。`advanceStage` 的 HTTP 入口仅 `IntakeController` 一处，前端仅 `IntakeListView.vue` 调用；`IntakeSubmitTestReviewService` 走 `SUBMIT_TESTING` 不受影响。
- 事实来源：上述文件路径与行号均为 2026-11-24 工作区只读核对；`docs/业务线/BL000012-工作台/现状.md` 11a 节记录既有链路。

## 2. 系统架构

- 总体方案：在既有"阶段动作请求携带开关 → 后端按开关跳过自动化"的模式上扩展，不引入新组件；手动启动复用 retry 端点的无记录分支。
- 流程变化（研发需求设计完成）：

```
设计完成弹窗 ──aiDevelopmentEnabled──▶ advanceStage(CONFIRM_DESIGN)
   ├─ 默认/true：预检数字员工 → 推进开发中 → 事务提交后 triggerAfterDesignConfirmed（现状不变）
   └─ false：跳过预检 → 推进开发中 → 不触发；历史追加「AI 自动开发：未启用（人工开发）」
                                            │
              开发中详情·AI 分支开发区块 ◀────┘（无执行记录时显示「未启动」+「启动 AI 开发」）
                        └─ POST /ai-development/retry（无记录→upsert+submit，等价 triggerAfterDesignConfirmed）
```

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| IntakeService | 开关判定、跳过预检与触发、历史留痕 | Agent 任务创建 | `aiDevelopmentEnabled` 请求字段 | IntakeAiDevelopmentService |
| IntakeAiDevelopmentService | retry 无记录时创建并启动 | 弹窗交互 | intakeId、操作人 | AgentWorkbenchService、pm_intake_ai_development |
| IntakeListView.vue | 弹窗开关（仅研发需求）、payload、手动启动按钮 | 后端判定 | 开关状态、selectedDetail | /api/intake 系列 |

## 3. 契约与数据模型

- `POST /api/intake/{id}/stage-action` 请求体新增可选字段 `aiDevelopmentEnabled: Boolean`：仅 `action=CONFIRM_DESIGN` 且研发需求时生效；`null`/`true` = 启用（现状语义），`false` = 跳过数字员工预检与自动触发。其他动作忽略该字段。向后兼容。
- `POST /api/intake/{id}/ai-development/retry` 语义扩展：需求处于开发中且无执行记录时，创建 PENDING 记录并启动（等价设计完成自动触发路径）；既有"仅失败或尚未创建任务的待执行记录允许重试"校验不变；非开发中仍报"仅开发中的需求可以启动 AI 开发"。响应结构不变（`IntakeAiDevelopmentResponse`）。
- 历史记录：设计完成且研发需求时，`pm_intake_history` 明细追加一行 `AI 自动开发：启用` 或 `AI 自动开发：未启用（人工开发）`；非研发需求不追加。
- 幂等/并发：retry 无记录分支沿用 `triggerAfterDesignConfirmed` 的事务内 `lockByIntakeId` upsert（`requestKey=intake-dev-{id}`、`intake_id` 唯一键），并发双击不产生双记录；后台 `startLocked` 仍以 PENDING+锁为闸门。
- 无数据库变更（无新表/新列/迁移脚本）。

## 4. 非功能约束

- 安全与权限：两端点权限不变（stage-action 沿用既有校验；retry 为 `intake:stage:operate`）；开关不绕过任何权限。
- 失败处理：关闭开关后无外部调用，无新增失败面；手动启动失败路径与自动触发一致（PENDING/FAILED + executionMessage，可再试）。
- 日志与审计：选择写入需求历史；Agent 任务启动日志沿用 `IntakeAiDevelopmentService` 现有 log。

## 5. 交付与回滚

- 发布顺序：先 server 后 web（旧前端不传字段 = 默认启用，行为不变；新前端 + 旧后端时字段被忽略、开关无效但无错误）。
- 回滚：纯代码回滚即可，无数据不可逆部分；已产生的执行记录与历史行语义自洽（历史文字描述当时选择）。
- 生效后更新：`docs/业务线/BL000012-工作台/现状.md` 11a 节、`迭代日志.md`。

## 6. 一致性与待确认

- Proposal 目标 1 → 契约字段 + IntakeService 三处改动 + 弹窗开关；目标 2 → retry 无记录分支 + 区块「启动 AI 开发」。
- 非范围保持：不改数据库、不动运维/提测链路、不新增端点、不改 Agent 任务与巡检逻辑。
- 待确认问题：无（交互三问未获应答，按推荐方案执行并在计划审批时一并确认）。
- 关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)；先例 REQ-a71851db（AI 开发链路）、`aiClarificationEnabled` 模式。
