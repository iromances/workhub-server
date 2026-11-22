# Design：数字员工任务成果弹框全屏

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 数字员工任务成果弹框全屏（REQ-75416059-d7aa-4f51-8155-48e7140c14e1） |
| 涉及仓库与功能域 | workhub-web：AgentWorkbenchView 弹框属性与样式；workhub-server 仅文档 |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随 Proposal 一并确认） |
| 当前事实核对环境、分支、时间 | 2026-11-22；web 分支 `codex/development-20261122`（HEAD `5609749`） |

## 1. 现状与证据

- 当前流程、模块和代码位置（改动前）：`AgentWorkbenchView.vue` 约 L292 `<el-dialog v-model="drawer" title="任务成果" width="min(1480px, 96vw)" top="3vh" class="task-dialog">`；约 L367 `:global(.task-dialog .el-dialog__body) { max-height: calc(90vh - 110px); overflow-y: auto; }`。
- 事实来源：上述路径代码。
- 尚未核实的假设：无。

## 2. 系统架构

- 总体方案：使用 Element Plus `el-dialog` 原生 `fullscreen` 属性铺满视口；内容区高度上限同步放大。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AgentWorkbenchView.vue | 任务成果弹框全屏与内容高度 | 任务列表弹框、周期抽屉 | 不变 | element-plus el-dialog fullscreen |

## 3. 契约与数据模型

- 无契约变更；无数据库变更。

## 4. 非功能约束

- 安全与权限：不变。
- 性能：无影响。
- 日志、监控、告警与审计：不涉及。

## 5. 交付与回滚

- 影响系统和发布顺序：仅前端发布。
- 回滚条件、步骤与不可逆部分：还原该文件提交即回滚；无不可逆部分。
- 生效后需更新的文档：迭代日志已记条目；现状.md 无结构性变化不更新。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → §2 方案。
- 明确非范围是否被保持：任务列表弹框与抽屉未动。
- 待确认问题及影响：无。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
