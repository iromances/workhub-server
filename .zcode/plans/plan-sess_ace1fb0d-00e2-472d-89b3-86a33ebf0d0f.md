# 需求阶段回退 + "开发完成"状态 + AI 自测完成自动流转

## Context

202611050003 已到"开发中"，用户要求：退回上一阶段（待设计）重新走流程 —— 点"设计完成"后触发 AI 拉分支改代码自测，自测通过后需求自动变更为新状态"开发完成"，再衔接后续（提交测试→测试中→…）。经探查：**"设计完成→AI 开发"已存在无需开发**（`IntakeService.schedulePostStageAutomation:2920` 的 CONFIRM_DESIGN 分支 → `IntakeAiDevelopmentService.triggerAfterDesignConfirmed` 创建平台 Agent 任务，目标即"全部任务项完成开发并自测通过，代码交付到研发分支"）。缺的是：①通用阶段回退；②"开发完成"状态与 Agent 自测完成的自动联动。

已确认决策：两状态都可提测（开发中/开发完成）；通用回退+必填原因；自测失败停开发中可重试。

## 改动清单

### 1. 状态机 `IntakeDemandStatusRules`（workhub-service/.../intake/）
- 新常量 `开发完成`（DEVELOPMENT_COMPLETED）；`EDITABLE_BUSINESS_STATUSES` 加入。
- 新动作 `ACTION_AI_SELF_TEST_PASSED`（内部系统动作）：`开发中 → 开发完成`。
- `SUBMIT_TESTING`：允许 `开发中 或 开发完成 → 测试中`（人工路径保留）。
- 新动作 `ACTION_ROLLBACK_STAGE` + `resolvePreviousStatus(currentStatus, requirementType)`：研发链 待澄清→已收录、待评估→待澄清、待排期→待评估、待设计→待排期、开发中→待设计、开发完成→开发中、测试中→开发完成、待验收→测试中、待上线→待验收；运维链同理（处理中→待处理、待处理→待澄清…）；已收录无上一节点、终态/已暂停拒绝。

### 2. 通用回退（后端）
- Model：`IntakeStageRollbackRequest(reason 必填)`。
- `IntakeService.rollbackStage(id, request, operator)`：复用 advanceStage 模式（`requireExisting`/状态 resolve/`intakeMapper.updateManagementFields`/`recordHistory`，历史摘要"阶段回退：开发中 → 待设计"，detail 必填原因）。
- Controller `IntakeController`：`POST /api/intake/{id}/stage-rollback`，权限与 advanceStage 相同。
- 回退到待设计不自动触发任何 AI 动作；回退不清除已有 AI 开发记录/禅道映射（重新设计完成后 `schedule()` 会再插一条，`requestKey=intake-dev-{id}` 注意唯一约束 —— 若表有唯一键则改为 upsert 或先关闭旧记录，实施时按表结构处理）。

### 3. AI 自测完成 → 开发完成（自动联动，核心新增）
- `IntakeAiDevelopmentEntity.executionStatus` 语义扩展：新增 `COMPLETED`（Agent 任务完成自测通过）；PENDING=待启动、SUCCEEDED=Agent 已启动执行中、FAILED=失败、COMPLETED=自测通过。
- `IntakeAiDevelopmentService` 新增 `watchProgress()`：mapper 新增查询（`agent_task_id 非空 AND execution_status IN ('PENDING','SUCCEEDED')`，上限逐批）→ 逐条 `agentWorkbenchService.task(agentTaskId, operator)` 查 Agent 任务状态：
  - Agent SUCCEEDED → executionStatus=COMPLETED + 需求当前为"开发中"时 `updateManagementFields` 置"开发完成" + recordHistory("AI 自测完成", 分支与交付说明)；
  - Agent FAILED → executionStatus=FAILED + 消息（需求停"开发中"，前端已有失败重试按钮）；
  - 幂等：COMPLETED/FAILED 不再扫。
- `workhub-job` 新增 `IntakeAiDevelopmentWatchJob implements WorkhubJob`（code=`INTAKE_AI_DEVELOPMENT_WATCH`，FIXED_DELAY 默认 60000，照 `AgentReleaseJob` 样例）。
- 迁移 `V20261115_100000__seed_intake_ai_development_watch_job.sql`：sys_job_config 种子（照现有 job 种子迁移模式 INSERT...WHERE NOT EXISTS）。

### 4. 前端（workhub-web `IntakeListView.vue` + `api/intake.ts`）
- `resolveNextStageAction`：`'开发完成' → 'SUBMIT_TESTING'`；状态标签/筛选/时间线补充"开发完成"（含 `findHistoryTime` 时间线加"AI 自测完成"节点）。
- 回退：行/详情阶段操作区加"回退上一阶段"按钮（已收录/终态/暂停不显示），弹窗必填原因；`rollbackIntakeStage(id, reason)` API。
- "AI 分支开发"区块：executionStatus 文案补 COMPLETED（"AI 自测完成，已交付研发分支"）、SUCCEEDED 文案改为"Agent 执行中"。

### 5. 本次操作（实施完成后）
- 调 `POST /api/intake/277/stage-rollback`（原因：退回重走 AI 开发流程）把 202611050003 从 开发中 → 待设计。
- 用户在页面点"设计完成"重新触发 AI 分支开发。

## 关键复用
- `IntakeService.advanceStage`（校验/落库/历史模式）、`schedulePostStageAutomation`（CONFIRM_DESIGN 钩子不动）、`AgentReleaseJob`（WorkhubJob 样例）、`agentWorkbenchService.task(taskId, user)`（Agent 状态查询）、前端 `advanceIntakeStage` 弹窗模式。

## 验证
1. 单测：IntakeDemandStatusRules（回退映射、开发中/开发完成→提交测试、AI_SELF_TEST_PASSED 转移）、rollbackStage（非法状态/必填原因）、watchProgress（mock agentWorkbenchService：SUCCEEDED→置开发完成、FAILED→停开发中、幂等）。
2. `mvn -pl workhub-service -am test` 全量 + `vue-tsc`。
3. 真实闭环：回退 277 → 页面显示"待设计" → 点"设计完成" → "AI 分支开发"区块出现新记录 → Agent 完成后 watch job 将状态置"开发完成"（可手动触发 job 加速验证）→ 点"提交测试"进入测试中。

## 风险与说明
- `pm_intake_ai_development` 若对 request_key 有唯一约束，重复触发需先处理旧记录（实施时按表迁移确认，倾向 upsert 关闭旧记录）。
- watch job 用系统身份调 `agentWorkbenchService.task`，异常单条捕获不中断整批。
- Agent 任务的实际自测质量由平台 Agent 保证，WorkHub 只消费其终态。