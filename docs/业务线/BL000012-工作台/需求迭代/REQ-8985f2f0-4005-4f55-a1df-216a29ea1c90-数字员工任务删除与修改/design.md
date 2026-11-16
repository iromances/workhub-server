# Design：数字员工任务删除与修改

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-8985f2f0-4005-4f55-a1df-216a29ea1c90 |
| 需求名称与唯一需求目录 | 数字员工任务删除与修改；`docs/业务线/BL000012-工作台/需求迭代/REQ-8985f2f0-4005-4f55-a1df-216a29ea1c90-数字员工任务删除与修改/` |
| 涉及仓库与功能域 | workhub-server：agent 任务域；workhub-web：任务列表与对话框 |
| 状态 | 已确认（Proposal 同时确认） |

## 1. 契约

### 修改

- `PUT /api/agent-tasks/{id}`（`agent:task:operate`）body `AgentTaskUpdateRequest{expectedVersion≥0, specification}`；成功返回更新后 `AgentTaskResponse`。
- 服务层：`actor.require("agent:task:operate")` + `requireAgent` + `lockTask` + 版本锁 → `validateTask(specification, agentConfiguration, actor)`（与创建同规则）→ `UPDATE pm_agent_task SET specification_json=?, version=version+1` → 审计 `TASK/UPDATE_SPEC`。
- 已有运行的 `specification_json` 快照不动；`enqueue` 每次仍会按新规格重校验授权。

### 删除

- `DELETE /api/agent-tasks/{id}?expectedVersion=`（`agent:manage`）。
- 校验顺序：归属/权限 → 版本 → `schedule_enabled=0` → 无未结束运行（状态 ∉ COMPLETED/FAILED/CANCELLED/WAITING_INPUT 拒绝）→ 无过程数据（见下）→ 同事务先 `INSERT pm_agent_audit(action=DELETE, payload=行数与目标摘要)` → 按序 DELETE `pm_agent_artifact`→`pm_agent_event`→`pm_agent_run`→`pm_agent_task`。
- 过程数据检查（一条多列 COUNT，按 run 关联）：`pm_agent_observation`、`pm_agent_change_set`、`pm_agent_code_workspace`、`pm_agent_build_test`、`pm_agent_release_plan`、`pm_agent_delivery`、`pm_agent_deployment`、`pm_agent_verification`、`pm_agent_inspection_item(assigned_run_id)`、`pm_autonomy_case_run`、`pm_autonomy_case(active_run_id)`；任一非零即拒绝并列出类别。

## 2. 数据模型

无表结构变更；新增 `AgentTaskUpdateRequest`（workhub-model）。`AgentMapper` 新增：`updateTaskSpecification`、`taskDependencyCounts`、`countUnfinishedRuns`、`deleteArtifactsByTask/deleteEventsByTask/deleteRunsByTask/deleteTaskById`（子查询关联 task，H2/MySQL 兼容）。

## 3. 前端

- `AgentTaskDialog` 支持编辑模式（`task` 属性）：预填规格、隐藏执行方式/立即执行，提交 `PUT`。
- 任务列表操作列：`编辑`（`agent:task:assign` 同分派人可见）与 `删除`（`agent:manage`，ElMessageBox 二次确认，提示运行与产物将一并删除）。
- `api/agent.ts` 增 `updateAgentTask`/`deleteAgentTask`。

## 4. 非功能与回滚

- 纯行为新增，无迁移；回滚应用即恢复无删除/修改状态（已删数据不可恢复，由二次确认与审计兜底）。
- 测试：H2 + 真实 Mapper 模式沿用 `AgentWorkbenchServiceTest`。

## 5. 一致性

- 权限与"调整周期/停用 Agent"既有惯例一致；删除审计与 2026-11-10 SQL 运维删除的留痕方式一致。
- 关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
