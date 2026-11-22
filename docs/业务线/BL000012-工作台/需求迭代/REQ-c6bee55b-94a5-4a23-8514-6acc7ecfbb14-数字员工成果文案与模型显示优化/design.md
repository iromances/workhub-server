# Design：数字员工成果文案与模型显示优化

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 数字员工成果文案与模型显示优化（REQ-c6bee55b-94a5-4a23-8514-6acc7ecfbb14） |
| 涉及仓库与功能域 | workhub-web：数字员工模块两个视图组件；workhub-server：仅需求归档文档 |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随 Proposal 一并批准） |
| 当前事实核对环境、分支、时间 | 2026-11-22；web 分支 `codex/development-20261103`（HEAD `210002f`）+ 未提交工作区；server 分支 `codex/development-20261102` |

## 1. 现状与证据

- 当前流程、模块和代码位置（工作区版本，即用户实际所见）：
  - `AgentRunPanel.vue`：运行概况区 `<span class="run-status-note">执行状态不代表业务健康或产物验收通过</span>`；下载报告元信息 `执行状态：xxx（不代表业务健康或验收通过）` 与 `AI：{channelType} · {model}`；「运行信息」详情 `本次 AI：{channelType} · {model} · 接入配置版本 N`。
  - `AgentWorkbenchView.vue`：历史运行侧栏 `<p class="history-hint">初次优先展示当前页最近完成的报告</p>`；员工列表「AI / 模型」列 = `providerName(providerConfigId)` + `configuration.model` 两行。
- 全系统排查（第 3 条，只读 grep）：workhub-web 全部 `.vue`/`.ts` 与 workhub-server 用户可见文案中，「供应商信息 + 模型」组合展示仅上述 3 处；`AiConfigView.vue` 的 `providerName` 用于接入配置管理页本体（列主体即供应商，属管理用途，不改）；`AgentInspectionService.java:82` 的 `interpretation` 为工具返回 JSON 字段供模型读取，非页面文案。
- 事实来源：文件路径与行号如上；HEAD 与工作区差异经 `git show`/`git diff` 核对——两句被删文案与「运行信息」折叠详情为工作区未提交改造引入，HEAD 仅存在「本次 AI」行与「AI / 模型」列。
- 尚未核实的假设：无。

## 2. 系统架构

- 总体方案：纯前端文案删除与展示收敛，无流程、数据、状态变化。
- 主要流程、数据流和状态流：不变。
- 前端页面、交互与后端规则边界：

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AgentRunPanel.vue | 运行概况去免责文案；运行信息/下载报告仅模型名 | API 字段、验收面板 | `AgentRunDetail`（不变） | 结构化报告改造 WIP（未跟踪文件） |
| AgentWorkbenchView.vue | 员工列表「模型」列；删历史提示 | 配置表单、任务弹窗 | `AgentConfiguration`（不变） | 无 |

## 3. 契约与数据模型

- API/事件提供方与消费者：无变更。前端继续以 `providerConfigId` + `model` 与服务端交互（`AgentConfiguration`、`providerBinding` 类型定义不变）。
- 新增、修改或废弃的契约：无；数据库无变更、无迁移。

## 4. 非功能约束

- 安全与权限：权限模型不变；无新数据处理。
- 性能：无影响（仅删减渲染节点）。
- 失败处理：无外部依赖变化。
- 日志、监控、告警与审计：不涉及。

## 5. 交付与回滚

- 影响系统和发布顺序：仅 workhub-web 前端发布；后端无发布。
- 灰度或开关：无。
- 数据备份与前后校验：不涉及。
- 回滚条件、步骤与不可逆部分：还原 web 分支提交 `78c23a9` 即完全回滚；无不可逆部分。
- 生效后需更新的文档：迭代日志已记条目；现状.md 无结构性变化（页面功能与模块边界不变），不更新。
- 注意：web 提交包含两目标文件既有的结构化报告 WIP（改动脉络见 Proposal 约束节），回滚该提交会连带回滚这部分 WIP。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1/2 → AgentRunPanel.vue 与 AgentWorkbenchView.vue 删文案；目标 3 → 两文件展示收敛 + 全系统排查结论（第 1 节）。
- 明确非范围是否被保持：API 字段、配置表单、AiConfigView、后端代码均未改动。
- 待确认问题及影响：用户清单第 4 条为空，如后续补充按 SDD 流程追加。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
