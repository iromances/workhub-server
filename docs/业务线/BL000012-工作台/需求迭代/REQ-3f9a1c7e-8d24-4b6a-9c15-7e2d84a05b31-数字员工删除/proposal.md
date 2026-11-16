# Proposal：数字员工删除

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-3f9a1c7e-8d24-4b6a-9c15-7e2d84a05b31 |
| 需求名称 | 数字员工删除 |
| 涉及仓库与功能域 | workhub-server：agent 定义域（dao/service/controller/测试）；workhub-web：数字员工列表操作 |
| 所属项目或业务线 | BL000012-工作台（WorkHub 平台自身功能） |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-3f9a1c7e-8d24-4b6a-9c15-7e2d84a05b31-数字员工删除/` |
| 版本/状态 | v1 已确认（2026-11-13 用户批准实施计划，见 tasks.md 确认记录） |
| 目标系统 | WorkHub Server + WorkHub Web |
| 原始需求来源及版本 | 2026-11-13 用户："数字员工增加删除按钮" |
| 目标环境/分支 | server `codex/development-20261102`；web `codex/development-20261103` |
| 负责人/评审人 | 用户（计划批准即规格确认） |

## 1. Why：背景与问题

- 数字员工（Agent）目前只能创建、配置、启用/停用，**不能删除**；不再使用的 Agent 只能永久留在列表里。
- 任务级删除已于 2026-11-10 按 REQ-8985f2f0 产品化（物理删除 + 前置校验 + 审计先行）；Agent 本体沿用同一删除模型，保持一个心智模型。

## 2. What：目标与范围

### 本期目标

**Agent 删除**：`DELETE /api/agents/{id}`（`expectedVersion` 查询参数）物理删除 Agent 及其全部任务、运行、事件、产物；删除前在同一事务先写 `pm_agent_audit`（type=AGENT，action=DELETE，记录名称与删除行数）。前端数字员工列表"操作"列增加"删除"按钮（`agent:manage` 可见，二次确认）。

### 删除前置校验（不满足即拒绝，提示处理方式）

1. Agent 须已停用（与"周期任务须先暂停"同一逻辑：停用是删除的准备步骤，停用本身会向活动运行发停止请求）。
2. 不存在启用中的周期任务（`schedule_enabled=0`）。
3. 不存在未结束运行（状态须全为 COMPLETED/FAILED/CANCELLED/WAITING_INPUT；QUEUED/RUNNING/CANCELLING/WAITING_RESOURCE 拒绝）。
4. 运行不挂接过程数据：观测、候选变更、代码工作区、构建测试、发布计划、部署、业务复查、巡检分配、问题协作任一存在即拒绝（保留证据，停用替代），错误信息列出类别。

### 权限

- 删除：`agent:manage` + Agent 归属（与停用 Agent 一致）。

### 明确不做

1. 不删审计行；不删除运行挂接的过程数据（由前置校验阻止）。
2. 不做批量删除；不改动任务删除与停用既有行为。
3. 无 DDL：沿用现有表结构，不引入软删除标记。
4. 巡检扫描位置（`pm_agent_inspection_stream`，按 task_id 键）不在级联范围，与任务删除既有行为一致（历史遗留扫描位置不产生新读取）。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 已停用、无周期启用、无未结束运行、无过程数据的 Agent 可删除；definition/task/run/event/artifact 级联清零，先写 DELETE 审计 | 服务测试 | 用户 |
| AC-02 | 未停用、周期任务未暂停、存在未结束运行、存在过程数据的 Agent 删除被拒且数据不变，错误信息说明原因与替代操作 | 服务测试 | 用户 |
| AC-03 | 版本冲突提示刷新；非归属且无 `agent:manage` 被拒 | 服务测试 | 用户 |
| AC-04 | 删除后列表/详情不再返回该 Agent | 服务测试 | 用户 |
| AC-05 | 前端数字员工列表提供"删除"入口（`agent:manage` 可见，二次确认），构建通过 | 前端构建 | 用户 |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
