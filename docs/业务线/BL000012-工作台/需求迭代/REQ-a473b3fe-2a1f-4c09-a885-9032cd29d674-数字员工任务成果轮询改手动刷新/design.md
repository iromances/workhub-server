# Design：数字员工任务成果轮询改手动刷新

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 数字员工任务成果轮询改手动刷新（REQ-a473b3fe-2a1f-4c09-a885-9032cd29d674） |
| 涉及仓库与功能域 | workhub-web：AgentWorkbenchView 组件内轮询逻辑；workhub-server 仅文档 |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随 Proposal 一并确认） |
| 当前事实核对环境、分支、时间 | 2026-11-22；web 分支 `codex/development-20261122`（HEAD `78c23a9`） |

## 1. 现状与证据

- 当前流程、模块和代码位置（改动前）：`AgentWorkbenchView.vue` 中 `let timer`/`let polling`（约 L58-59）、`stopPolling()`/`startPolling()`（约 L258-266，`setInterval(…, 5000)` 内调 `loadRuns(true)`）；`onMounted`/`onActivated` 启动、`onDeactivated`/`onUnmounted` 停止；弹窗开着时每轮实际发起 `fetchAgentTask`+`fetchAgentRuns`+`fetchAgentRun`（`loadRuns` 内 `selectedRunId` 命中时顺带 `loadRunDetail`）。
- 事实来源：上述路径代码；用户页面观察（"已经读到了还在周期性读"）。
- 尚未核实的假设：无。

## 2. 系统架构

- 总体方案：删除组件内自动轮询，数据获取全部由用户动作或组件生命周期单次触发。
- 触发路径（改后全量枚举）：打开弹窗 `openTask→loadRuns`；弹窗「刷新」按钮 `loadRuns(false)`；重进页面 `onActivated` 一次性 `loadAgents`+`loadRuns(true)`；「开始新一轮」等操作后 `loadRuns()` 联动；翻页 `changeRunPage`。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AgentWorkbenchView.vue | 移除轮询；保留手动/生命周期触发加载 | 接口定义、其他组件 | 不变 | 无 |

## 3. 契约与数据模型

- 无契约变更；无数据库变更。既有接口调用频率下降，路径与参数不变。

## 4. 非功能约束

- 安全与权限：不变。
- 性能：弹窗静态打开期间请求量降为 0。
- 失败处理：`loadRuns` 错误提示路径不变。
- 日志、监控、告警与审计：不涉及。

## 5. 交付与回滚

- 影响系统和发布顺序：仅前端发布。
- 灰度或开关：无。
- 回滚条件、步骤与不可逆部分：还原该文件提交即回滚；无不可逆部分。
- 生效后需更新的文档：迭代日志已记条目；现状.md 无结构性变化不更新（模块与接口边界不变，仅前端刷新策略调整）。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → 删除轮询代码；目标 2 → 第 2 节触发路径枚举。
- 明确非范围是否被保持：AgentRunPanel 的 `updatedAt` watch、后端、任务列表刷新均未动。
- 待确认问题及影响：无（用户选项已定）。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
