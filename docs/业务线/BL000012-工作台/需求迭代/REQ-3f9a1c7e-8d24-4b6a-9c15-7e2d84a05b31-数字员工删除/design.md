# Design：数字员工删除

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-3f9a1c7e-8d24-4b6a-9c15-7e2d84a05b31 |
| 需求名称与唯一需求目录 | 数字员工删除；`docs/业务线/BL000012-工作台/需求迭代/REQ-3f9a1c7e-8d24-4b6a-9c15-7e2d84a05b31-数字员工删除/` |
| 涉及仓库与功能域 | workhub-server：agent 定义域；workhub-web：数字员工列表 |
| 状态 | 已确认（Proposal 同时确认） |

## 1. 契约

- `DELETE /api/agents/{id}?expectedVersion=`（`agent:manage`）。
- 校验顺序（镜像 `deleteTask`，AgentWorkbenchService）：权限/归属（`actor.require("agent:manage")` → `lockAgent FOR UPDATE` → `requireAgent`）→ 版本锁 → ① `enabled=0`（否则"请先停用"）→ ② 无启用中周期任务（`schedule_enabled=1` 计数为 0）→ ③ 无未结束运行（与任务删除同一状态集：QUEUED/RUNNING/CANCELLING/WAITING_RESOURCE 拒绝，WAITING_INPUT 不阻拦）→ ④ 无过程数据（见下）→ 同事务先 `INSERT pm_agent_audit(type=AGENT, action=DELETE, payload=名称与行数)` → 按序 DELETE `pm_agent_artifact`→`pm_agent_event`→`pm_agent_run`→`pm_agent_task`→`pm_agent_definition`（版本条件，`checkChanged`）。
- 并发安全：`dispatchScheduledTask`/`enqueue` 均先锁同一 Agent 行且要求 `enabled=1`；删除持锁并校验已停用 + 周期已暂停后级联，调度器无法再入队。
- 过程数据检查（一条多列 COUNT，经 `pm_agent_run.agent_id` 关联，与任务删除同类别同口径）：`pm_agent_observation`、`pm_agent_change_set`、`pm_agent_code_workspace`、`pm_agent_build_test`、`pm_agent_release_plan`、`pm_agent_deployment`、`pm_agent_verification`、`pm_agent_inspection_item(assigned_run_id)`、`pm_autonomy_case_run`；任一非零即拒绝并列出类别（复用 `TaskRemovalCounts` 的 `hasProcessData`/`processCategories`）。

## 2. 数据模型

无表结构变更。`AgentMapper` 新增：`countUnfinishedRunsByAgent`、`countEnabledSchedulesByAgent`、`agentRemovalCounts`（复用 `TaskRemovalCounts` record）、`deleteEventsByAgent/deleteArtifactsByAgent/deleteRunsByAgent/deleteTasksByAgent`（子查询/直接 agent_id 条件，H2/MySQL 兼容）、`deleteAgentById(id, version)`。

## 3. 前端

- `api/agent.ts` 增 `deleteAgent(agent)`（`DELETE /agents/{id}?expectedVersion=`）。
- `AgentWorkbenchView` Agent 表格操作列追加"删除"（`canManage`，link danger）；`ElMessageBox.confirm` 二次确认，文案说明物理删除范围与四项前置；成功后刷新列表，若删除的是当前选中 Agent 则清空选中（任务区随之隐藏）；失败 `ElMessage.error`。
- 操作列宽度 215 → 265 容纳第四个按钮。

## 4. 非功能与回滚

- 纯行为新增，无迁移；回滚应用即恢复无删除状态（已删数据不可恢复，由二次确认与审计兜底）。
- 审计行永远保留；巡检扫描位置不级联（与任务删除既有行为一致）。
- 测试：H2 + 真实 Mapper 模式沿用 `AgentWorkbenchServiceTest`。

## 5. 一致性

- 删除模型与任务删除（REQ-8985f2f0）完全同构：物理删除 + 证据保全 + 审计先行 + 停用/暂停前置。
- 权限与"停用 Agent"既有惯例一致。

- 关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
