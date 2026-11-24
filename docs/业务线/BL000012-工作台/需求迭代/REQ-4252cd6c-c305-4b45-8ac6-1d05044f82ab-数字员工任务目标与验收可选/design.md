# Design：数字员工任务目标与验收标准改为可选

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-4252cd6c-c305-4b45-8ac6-1d05044f82ab |
| 需求名称与唯一需求目录 | 数字员工任务目标与验收标准改为可选；docs/业务线/BL000012-工作台/需求迭代/REQ-4252cd6c-c305-4b45-8ac6-1d05044f82ab-数字员工任务目标与验收可选/ |
| 涉及仓库与功能域 | workhub-server：workhub-model 规格校验与归一化；workhub-web：AgentTaskDialog 表单 |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | 草案 v1 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server 分支 codex/development-20261102；生产库只读核查 pm_agent_task；2026-11-16 |

## 1. 现状与证据

- 当前必输校验：`workhub-model/src/main/java/cn/aslight/workhub/model/agent/AgentTaskSpecification.java:10,12` objective/acceptanceCriteria 标注 `@NotBlank`，经 `AgentTaskCreateRequest`/`AgentTaskUpdateRequest` 的 `@Valid` 生效。
- 前端拦截：`workhub-web/src/views/agent/AgentTaskDialog.vue:50` 提交前对两字段 trim 判空，:90/:92 表单项标记 required。
- 空值风险点（若仅删注解）：`AgentWorkbenchService.java:214` 删除任务审计 `Map.of("objective", spec.objective(), ...)` 不接受 null；`AgentRunNotifier.java:64` `abbreviate(spec.objective(), 30)` 同样依赖非空。
- 现状数据：5 条业务线巡检任务验收标准为同一段 600 余字文本（pm_agent_task 生产只读核查）；存量规格两字段均有值。
- 尚未核实的假设：无（行为均为代码与只读数据直接证据）。

## 2. 系统架构

- 总体方案：规格字段可选后，空目标/空验收的语义是“按数字员工职责执行”；运行时提示词中 taskSpecification 序列化始终包含两字段（空串），模型自然回落到职责描述。
- 关键设计：在 `AgentTaskSpecification` 紧凑构造器内将 null 归一化为 `""`（与既有 analysisScope 默认值处理同风格），下游（提示词 JSON、删除审计 Map.of、通知标题 abbreviate）收到空串，无需逐点判空。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-server model | 规格校验放宽与空值归一化 | 表单交互 | AgentTaskSpecification | bean validation |
| workhub-server service | 既有流程不动 | — | 规格 JSON 存取 | model |
| workhub-web AgentTaskDialog | 两字段必输放宽 | 后端规则 | 任务创建/更新提交 | API 契约 |

## 3. 契约与数据模型

- API：`POST/PUT` 数字员工任务的 specification.objective、specification.acceptanceCriteria 允许缺省/null/空白；不再因此返回校验错误；响应与存储中两字段恒为字符串（空串），字段语义“为空表示按职责执行”。
- 幂等：request_hash 按序列化 JSON 计算，归一化后同一内容哈希一致，uk_agent_task_request 幂等不变。
- 历史兼容：存量规格均有值，反序列化不受影响；无数据库变更、无迁移脚本。
- 错误码：仅移除两字段的缺失类校验错误，其余校验（长度、expectedArtifacts、scopes、completionTargets）不变。

## 4. 非功能约束

- 安全与权限：无权限变化；空目标不扩大授权（scopes/completionTargets 仍强制）。
- 性能：无影响（仅构造器归一化）。
- 日志与审计：删除审计、巡检通知在空目标下输出空串标题片段，标题退化为“[数字员工巡检] {name}·”，可接受。

## 5. 交付与回滚

- 发布顺序：后端先行（放宽校验），前端随后（去掉拦截）；仅前端先发会被后端 400 拦截，顺序不可颠倒。
- 回滚：还原两端提交即可，无数据不可逆部分。
- 生效后更新：BL000012 迭代日志、现状.md（任务分派字段说明）。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → model 归一化（§2）；目标 2 → 前端表单（§2 模块表）。
- 非范围保持：未触及 expectedArtifacts/scopes/completionTargets/职责必输。
- 待确认问题：workhub-web 目标分支（实施前核对当前工作区）。
