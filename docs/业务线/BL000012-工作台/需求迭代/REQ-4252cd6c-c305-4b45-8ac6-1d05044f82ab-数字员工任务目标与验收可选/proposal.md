# Proposal：数字员工任务目标与验收标准改为可选

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-4252cd6c-c305-4b45-8ac6-1d05044f82ab |
| 需求名称 | 数字员工任务目标与验收标准改为可选 |
| 涉及仓库与功能域 | workhub-server（workhub-model、workhub-service 数字员工任务规格）；workhub-web（agent 任务表单） |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-4252cd6c-c305-4b45-8ac6-1d05044f82ab-数字员工任务目标与验收可选/ |
| 版本/状态 | 草案 v1，待完整规格统一确认 |
| 目标系统 | WorkHub 后端、WorkHub 前端 |
| 原始需求来源及版本 | 用户提出（2026-11-16 会话：取消验收标准和任务目标的必输校验） |
| 目标环境/分支 | workhub-server：codex/development-20261102；workhub-web：待确认 |
| 负责人/评审人 | 待确认 |

## 1. Why：背景与问题

- 当前问题及证据：`AgentTaskSpecification` 的 objective、acceptanceCriteria 为 `@NotBlank` 必输，前端 `AgentTaskDialog.vue` 同步拦截。REQ-0d4c0b49 把数据路由上收平台后，巡检口径正迁移到数字员工职责；当前 5 条业务线巡检任务贴同一段 600 余字验收文本（pm_agent_task 只读核查 2026-11-16），必输校验在催生复制粘贴。
- 受影响用户/角色：分派数字员工任务的用户。
- 不解决的后果：例行周期任务每次分派被迫重复填写模板化文本，验收标准继续膨胀难读。

## 2. What：目标与范围

### 本期目标

1. 任务规格的 objective、acceptanceCriteria 改为可选：null/缺省归一化为空串，长度上限（12000/8000）不变。
2. 前端任务表单两字段去掉必输，提交校验仅保留“期望产物至少一种”。

### 本期交付范围

1. workhub-server：`AgentTaskSpecification` 校验调整与空值归一化，配套单测。
2. workhub-web：`AgentTaskDialog.vue` 表单必输放宽。

### 明确不做

1. 不改 expectedArtifacts 必选、scopes/completionTargets 校验规则。
2. 不改 Agent 职责（instructions）必输。
3. 不迁移存量任务数据（存量规格两字段均有值）。

### 约束与依赖

- 依赖的代码和文档：REQ-0d4c0b49（数字员工平台数据指南）确立的“职责承载口径”方向。
- 既有系统/接口/数据约束：request_hash 按规格序列化 JSON 计算，归一化后空串参与哈希，幂等语义不变；存量任务读写不受影响。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | objective/acceptanceCriteria 为 null 或空白创建、更新任务成功，存储规格两字段为空串 | 后端单测 | 用户 |
| AC-02 | 前端两字段留空可提交；不选期望产物仍被拦截 | 前端构建 + 手工核对 | 用户 |
| AC-03 | 既有测试全绿；空目标下删除任务审计、巡检通知标题不抛 NPE | 后端单测 | 用户 |
| AC-04 | 存量任务（两字段有值）读取、重试、补充指令行为不变 | 后端单测 | 用户 |
