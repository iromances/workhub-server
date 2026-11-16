# Proposal：数字员工工具管理

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-f573f4cb-c741-4825-81e4-e74512074b46 |
| 需求名称 | 数字员工工具管理 |
| 涉及仓库与功能域 | workhub-server：工具展示文案表、管理接口、options 契约扩展；workhub-web：数字员工页工具管理入口、配置/任务对话框选择器 |
| 所属项目或业务线 | BL000012-工作台（WorkHub 平台自身功能） |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-f573f4cb-c741-4825-81e4-e74512074b46-数字员工工具管理/` |
| 版本/状态 | v1 已确认（2026-11-07 用户批准实施计划，见 tasks.md 确认记录） |
| 目标系统 | WorkHub Server + WorkHub Web |
| 原始需求来源及版本 | 2026-11-07 用户：“能不能做一个工具管理，工具名称，工具描述这样，然后可以从员工里选择工具名称”（前置背景：工具下拉文案为 100~202 字长段落，乱且难选） |
| 目标环境/分支 | server：`codex/development-20261102`；web：`codex/development-20261103`（两侧均有用户在途未提交改动，实施仅追加式修改） |
| 负责人/评审人 | 用户（计划批准即规格确认） |

## 1. Why：背景与问题

- 当前 50 个数字员工工具硬编码在 `workhub-service/src/main/java/cn/aslight/workhub/service/agent/*Tools.java`，由 `AgentToolCatalog` 聚合；`description()` 同时供 AI 运行时工具目录（AgentMcpBridge、AgentApiExecutionAdapter）与管理端选项列表（`GET /api/agents/options`）使用。
- 管理端因此显示的是面向 AI 的完整约束文案（21~202 字、风格不一），数字员工配置对话框与任务分派对话框的工具下拉拥挤难读，用户无法整理。
- 不解决的后果：配置数字员工时按长段落选工具，效率低且易错；文案要改必须改代码发版。

受影响用户/角色：拥有 `agent:view`/`agent:manage` 的管理员与任务分派人。

## 2. What：目标与范围

### 本期目标

1. 新增工具管理界面：维护每个工具的「工具名称」（短）与「工具描述」（短，人读），入口在数字员工页内，`agent:manage` 可编辑。
2. 数字员工配置与任务分派的工具选择器按「工具名称」显示，短描述作辅助提示。
3. 首次上线即提供 50 个工具整理后的默认名称与描述（迁移种子），不再显示长段落。

### 本期交付范围

1. server：新表 `pm_agent_tool`（仅展示文案）+ 迁移种子；`GET/PUT /api/agent-tools` 管理接口；`/api/agents/options` 的 `tools[]` 增加 `name` 且 `description` 改为短文案；审计写入 `pm_agent_audit`。
2. web：数字员工页「工具管理」对话框（列表+编辑）；`AgentConfigDialog`、`AgentTaskDialog` 选择器改用名称。

### 明确不做

1. 不改 AI 运行时工具目录：`AgentTool.description()` 及 AgentMcpBridge/AgentApiExecutionAdapter 输出保持原文，Agent 行为与红线约束不变。
2. 不做工具的增删与启停：工具本体（code/schema/权限/执行）仍由代码定义；管理界面仅维护展示文案。
3. 不新增菜单/权限项：入口在数字员工页内，复用 `agent:view`/`agent:manage`。

### 约束与依赖

- 依赖代码：`AgentToolCatalog`（目录来源）、`AgentMapper.insertAudit`（审计）、`AgentOptionsService`（options 契约）。
- 数据约束：`pm_agent_tool` 为展示层冗余，代码目录为权威；表中存在而目录不存在的行标记「已下线」不可编辑，目录新增工具未维护时名称回退为编码。
- 不安装依赖；不主动部署；未经要求不 commit/push。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 数字员工页对 `agent:manage` 用户显示「工具管理」入口，打开后列出代码目录全部工具及名称/描述/编码/只读 | 手工联调 + 前端构建 | 用户 |
| AC-02 | 编辑某工具名称/描述保存后，管理列表与 `/api/agents/options` 立即返回新文案，并写入 `pm_agent_audit`（AGENT_TOOL） | 接口验证 + 服务测试 | 用户 |
| AC-03 | 数字员工配置对话框与任务分派对话框的工具下拉按「名称」显示，不再显示 AI 长描述 | 手工联调 | 用户 |
| AC-04 | 无 `agent:manage` 的账号看不到编辑入口，`PUT /api/agent-tools/{code}` 返回 403 | 接口验证 + controller 测试 | 用户 |
| AC-05 | 目录中不存在该工具编码时 PUT 拒绝；目录已下线工具（表有目录无）标记展示且不可编辑 | 服务测试 | 用户 |
| AC-06 | AI 运行时工具目录输出不变（`AgentTool` 实现与桥接代码零改动），既有后端回归与前端构建通过 | 测试报告 + 构建日志归档 | 用户 |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
