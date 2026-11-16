# Proposal：监测通知改由数字员工巡检发出

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-172f8541-6f47-4a8c-9efa-ae1e52d5ba72 |
| 需求名称 | 监测通知改由数字员工巡检发出 |
| 涉及仓库与功能域 | workhub-server：ops 三处站内信删除、agent 运行完成通知新增、测试 |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-172f8541-6f47-4a8c-9efa-ae1e52d5ba72-监测通知改由数字员工巡检发出/` |
| 版本/状态 | v1 已确认（2026-11-12 用户："原本业务监测，财务监测，系统预警发给站内信的通知删除，改由数字员工巡检通知"；确认问题未答，按推荐默认：三处全删 + 仅 P0/P1 异常时发） |
| 负责人/评审人 | 用户 |

## 1. Why

站内信目前由三个监测采集点即时散发（XXL 失败、MQ 失败、ELK 采集故障），碎片化且无人汇总；数字员工巡检已能按增量窗口汇总三域并分级建议——通知应改为"巡检发现异常才打扰人"。

## 2. What

### 删除（仅删通知，不删采集/监测本体）

1. `XxlJobFailureAlertService`：删除 `createSystemNotification` 循环与收件人解析（扫描与日志保留）。
2. `MqFailureAlertService`：同上。
3. `SystemAlertCollectionStore`：删除 ELK_ERROR 采集故障站内信（故障状态跟踪保留）。财务监测现状本无站内信，无需删。

### 新增：数字员工巡检完成通知

- 新组件 `AgentRunNotifier`：运行结束（COMPLETED）且报告含 `P0` 或 `P1` 标记时，向任务范围内各业务线的成员（复用 `UserMapper.findBusinessLineMembers`）发站内信：类型 `AGENT_PATROL`，标题 `[数字员工巡检] {Agent名}·{目标前30字}`，内容为报告前 1000 字，dedupe=`AGENT_RUN:{runId}`（每轮至多一次）。无标记或非 COMPLETED 不发；通知失败仅记日志不影响运行。
- 挂钩点：`AgentExecutionCoordinator.execute` 在适配器执行后调用（try/catch 包裹）。
- P0/P1 判定依据报告文本标记（职责/验收标准要求建议按 P0/P1 分级），依赖已在巡检助手文案中固定。

### 明确不做

不做任务级开关/收件人配置（后续需要再立项）；不发企业微信；不通知 WAITING_INPUT/FAILED。

## 3. Done

| 编号 | 验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | 三处删除后全库无 XXL_JOB_FAILED/MQ/ELK_ERROR 站内信新增；采集/扫描行为不变 | 测试+代码核对 |
| AC-02 | 含 P0/P1 的 COMPLETED 运行给范围内业务线成员发 1 条 AGENT_PATROL（每轮 dedupe 一次）；无标记/失败/等待输入不发 | 服务测试 |
| AC-03 | 通知异常不影响运行结果 | 服务测试 |
| AC-04 | 全量 mvn test 本需求范围 0 失败 | mvn test |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
