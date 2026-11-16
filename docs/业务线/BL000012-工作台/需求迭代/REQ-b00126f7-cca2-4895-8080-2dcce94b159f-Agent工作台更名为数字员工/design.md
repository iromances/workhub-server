# Design：Agent 工作台更名为数字员工

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-b00126f7-cca2-4895-8080-2dcce94b159f |
| 需求名称与唯一需求目录 | 同 Proposal，`docs/业务线/BL000012-工作台/需求迭代/REQ-b00126f7-cca2-4895-8080-2dcce94b159f-Agent工作台更名为数字员工/` |
| 涉及仓库与功能域 | workhub-web 菜单/路由/页面标题；workhub-server 仅文档 |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 已确认并实施 |
| 当前事实核对环境、分支、时间 | web 本地源码 `codex/development-20261103`、server 本地源码 `codex/development-20261102`，2026-11-06 |

## 1. 现状与证据

- `workhub-web/src/constants/menu.ts:5`：菜单项 `{ key: 'agents', label: 'Agent 工作台', path: '/agents', permission: 'agent:view' }`。
- `workhub-web/src/router/index.ts:30`：路由 `agents` 的 `meta.title: 'Agent 工作台'`。
- `workhub-web/src/views/agent/AgentWorkbenchView.vue:210`：页面 `<h2>Agent 工作台</h2>`。
- 仓库内其余“Agent 工作台/Agent工作台”均位于文档：workhub-web 三份 docs 描述文档、workhub-server 现状/迭代日志与 REQ-bf94e06e 历史规格；workhub-server 与 workhub-web 源码中无其他该名称的用户可见文案（后端源码无“工作台”字样）。
- 尚未核实的假设：无。改动为三处展示字符串。

## 2. 方案

- 仅替换三处展示字符串“Agent 工作台”为“数字员工”：菜单 label、路由 `meta.title`、页面 `<h2>`；页面副标题与其余文案、代码标识（key/name/path/permission/目录/类型与 API 命名）不变。
- 文档同步：workhub-web `docs/frontend-architecture.md` 当前模块章节改为“数字员工”并注明原名；workhub-server `docs/业务线/BL000012-工作台/现状.md` 该模块标题与正文同步更名；`迭代日志.md` 新增本轮条目。历史条目、历史规格与迁移清单按原时点保留原称（沿用“共享支付系统更名为支付中台”的处理原则）。
- 术语边界：模块名改为“数字员工”后，页内被管理的实体仍称 Agent；如需页内一并改称，另行立项确认。

| 模块 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-web 菜单/路由/页面 | 展示名称 | 代码标识、API 契约、页内实体文案 | 静态字符串 | 无 |
| workhub-server docs | 现状与迭代记录 | 历史记录改写 | Markdown | 无 |

## 3. 契约与数据

无接口、权限、数据库变更；`/api/agents`、`agent:view` 等契约不变。

## 4. 非功能约束

不涉及权限、性能、外部依赖变化。

## 5. 交付与回滚

前端独立发布后生效；回滚还原三处字符串即可。系统设计正文（现状.md）标题随本轮同步，实际进展记录迭代日志。

## 6. 一致性与待确认

- Proposal 目标 1、明确不做项与第 2 节方案一一对应。
- 待确认：无（名称与范围以用户指令为准；页内实体称谓保留 Agent 已在边界中说明）。
- 关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
