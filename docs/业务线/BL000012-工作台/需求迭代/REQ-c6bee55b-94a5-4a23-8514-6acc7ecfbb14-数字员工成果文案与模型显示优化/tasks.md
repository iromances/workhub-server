# Tasks：数字员工成果文案与模型显示优化

## 1. 元信息

- 需求名称与编码：数字员工成果文案与模型显示优化（REQ-c6bee55b-94a5-4a23-8514-6acc7ecfbb14）
- 涉及仓库与功能域：workhub-web 数字员工模块；workhub-server 仅文档
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0
- Design/契约版本：v1.0（无契约变更）
- 研发分支：web `codex/digital-employee-ui-20261122`；server `codex/digital-employee-ui-20261122-docs`
- 任务计划状态：已确认并实施
- 负责人：AI（ZCode）
- 评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | AgentRunPanel.vue 文案删除与模型名展示 | 无 | 与 T-02 独立（不同文件） | design §2 / AC-01、AC-04 | 已实施 |
| T-02 | AgentWorkbenchView.vue 提示删除与模型列 | 无 | 与 T-01 独立 | design §2 / AC-02、AC-03 | 已实施 |
| T-03 | 全系统模型显示排查记录 | 无 | 只读 | design §1 / AC-05 | 已完成（只读排查） |
| T-04 | 构建验证 | T-01、T-02 | — | AC-06 | 已通过 |
| T-05 | 页面走查（部署后） | 前端部署 | — | AC-01~AC-04 | 待部署 |

执行顺序：T-01/T-02/T-03 并行 → T-04 → 合并由用户指令触发 → T-05。

## 3. 任务明细

### T-01：AgentRunPanel.vue 文案删除与模型名展示

- 描述与目标：删免责文案、报告后缀；运行信息与下载报告仅模型名。
- 输入：Proposal §2、design §2。
- 输出：`src/views/agent/AgentRunPanel.vue` 修改。
- 允许修改范围：该文件模板、脚本、样式。
- 明确不修改范围：API 调用、类型、其他组件。

验收条件：

- [x] 条件 1：运行概况区不再含 `run-status-note` span；报告元信息行为 `执行状态：${status}`。
- [x] 条件 2：运行信息行为 `模型：{model || '未记录模型'}`，不再含 channelType/接入配置版本；报告元信息仅在有模型时含 `模型：{model}` 行（数组条件展开实现）。

计划验证方式（尚未执行）：grep 残留 + `npm run build`。

状态及真实结果：已实施；grep 无「不代表业务健康/本次 AI/AI：/接入配置版本/run-status-note」残留，构建随 T-04 通过。页面走查待部署（T-05）。

### T-02：AgentWorkbenchView.vue 提示删除与模型列

- 描述与目标：删历史提示；「AI / 模型」列改「模型」列仅显示模型名。
- 输入：Proposal §2、design §2。
- 输出：`src/views/agent/AgentWorkbenchView.vue` 修改。
- 允许修改范围：该文件模板、脚本、样式。
- 明确不修改范围：配置弹窗、任务弹窗、数据加载逻辑。

验收条件：

- [x] 条件 1：模板不再含 `history-hint`，样式表同步清理且 `.history-list` 补 `margin-top: 12px` 防布局塌陷。
- [x] 条件 2：列为 `label="模型"`，单元格仅 `row.configuration.model || '使用接入默认模型'`；`providerName()` 无引用后删除。

计划验证方式（尚未执行）：grep 残留 + `npm run build`。

状态及真实结果：已实施；grep 无 `providerName`（该文件）与 `history-hint` 残留，构建随 T-04 通过。页面走查待部署（T-05）。

### T-03：全系统模型显示排查

- 描述与目标：确认除 3 处外无其他「供应商信息 + 模型」组合展示，并固化不改边界。
- 输入：workhub-web `src/**`（.vue/.ts）、workhub-server 用户可见文案。
- 输出：design §1 排查结论。
- 明确不修改范围：`AgentConfigDialog.vue`、`AiConfigView.vue`、`AgentInspectionService.java`、API 类型。

验收条件：

- [x] 条件 1：全局 grep 记录组合展示仅 3 处，全部处理。
- [x] 条件 2：不改边界的理由留档（录入表单/管理页/模型工具字段）。

状态及真实结果：已完成；残留 `providerName` 仅在 `AiConfigView.vue`（接入管理页，明确不改）。

### T-04：构建验证

- 输入：T-01、T-02 产物。
- 输出：`npm run build` 结果。

验收条件：

- [x] vue-tsc 类型检查与 vite 构建通过。

状态及真实结果：已通过；2026-11-22 `npm run build` 输出 `✓ built in 6.94s`，`AgentWorkbenchView` chunk 正常产出。

### T-05：页面走查（部署后）

- 输入：前端部署含本分支改动的构建产物。
- 输出：AC-01~AC-04 页面核对结论。

验收条件：

- [ ] 条件 1：任务成果页与员工列表按 AC-01~AC-04 展示。
- [ ] 条件 2：历史侧栏与运行概况布局无塌陷。

计划验证方式（尚未执行）：用户登录页面走查。

状态及真实结果：待开始；依赖部署。

## 4. 覆盖与评审

- Proposal 成功标准是否均映射至任务：AC-01~AC-04 → T-01/T-02/T-05；AC-05 → T-03；AC-06 → T-04。
- Design 接口/数据/非功能约束是否均有承接：无契约/数据变更，无需承接。
- 未覆盖项及原因：无。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：迭代日志已记条目（工作区，随本分支流程提交由用户决定）；现状.md 无结构性变化不更新（理由见 design §5）。
- 关联 Proposal、Design 与契约的版本链接：Proposal v1.0 / Design v1.0（本目录）。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：范围为本目录三份规格全部内容（含「明确不做」与第 4 条为空的处理）；v1.0；用户于 2026-11-22 以方案批准形式确认，并要求独立分支（`codex/digital-employee-ui-20261122` / `…-docs`）实施、汇报后经其指令合并；web 侧提交 `78c23a9`（含两文件既有结构化报告 WIP，已在 design §5 说明）。
