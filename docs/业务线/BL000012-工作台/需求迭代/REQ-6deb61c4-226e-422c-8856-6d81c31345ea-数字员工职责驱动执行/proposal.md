# Proposal：数字员工职责驱动执行

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-6deb61c4-226e-422c-8856-6d81c31345ea |
| 需求名称 | 数字员工职责驱动执行 |
| 涉及仓库与功能域 | workhub-server：agent 域工具层移除、CLI 接入标准 MCP、API 渠道去工具化、迁移；workhub-web：数字员工页删除工具配置/管理 |
| 所属项目或业务线 | BL000012-工作台（WorkHub 平台自身功能） |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-6deb61c4-226e-422c-8856-6d81c31345ea-数字员工职责驱动执行/` |
| 版本/状态 | v1 已确认（2026-11-10 用户批准实施计划，见 tasks.md 确认记录） |
| 目标系统 | WorkHub Server + WorkHub Web |
| 原始需求来源及版本 | 2026-11-10 用户："数字员工删掉现在的可用工具和工具的能力架构，太奇怪了。让ai直接通过职责描述来做事"；追问后明确"保留内部工具执行能力这个也不要了，因为AI可以根据职责的描述自行选择workhub支持的去做" |
| 目标环境/分支 | server：`codex/development-20261102`；web：`codex/development-20261103`（两侧均有用户在途未提交改动，实施仅做本需求修改，不代管无关改动） |
| 负责人/评审人 | 用户（计划批准即规格确认） |
| 撤销关系 | 撤销 [REQ-f573f4cb 数字员工工具管理](../REQ-f573f4cb-c741-4825-81e4-e74512074b46-数字员工工具管理/proposal.md) 的全部产物（`pm_agent_tool`、`/api/agent-tools`、前端工具管理对话框） |

## 1. Why：背景与问题

- 数字员工配置要求人工勾选"可用工具"（`toolCodes` 白名单，50 个工具目录的子集），任务分派时再勾子集；运行时通过自定义 `AgentTool` 目录 + 每次运行独享的 `AgentMcpBridge`（本机 HTTP MCP）桥接 WorkHub 能力；REQ-f573f4cb 又叠加了工具文案管理层。
- 用户判定这套"工具能力架构"整体不合理：数字员工应只靠职责描述（instructions）做事，AI 用哪些能力由职责引导、由 WorkHub 已有 MCP 服务器提供并自行选择，而不是人工逐个勾选工具。
- 不解决的后果：配置负担重、心智模型怪（用户要理解 50 个内部工具），工具文案/白名单/桥接三层维护成本高。

受影响用户/角色：拥有 `agent:view`/`agent:manage` 的管理员与任务分派人；运行数字员工任务的所有业务线。

## 2. What：目标与范围

### 本期目标

1. 数字员工配置与任务分派不再出现任何工具概念：删 `toolCodes` 字段、`options.tools`、工具管理界面。
2. 删除自定义工具执行层：`AgentTool` 接口与目录、11 个 `*Tools` 包装、`AgentMcpBridge`、工具管理服务与接口。
3. CLI 运行渠道改接 WorkHub 标准 MCP 服务器（`POST /api/mcp/runtime`，复用 `workhub.mcp.access-token`）：AI 依据职责描述自行选择 WorkHub 支持的能力；Token 未配置时 CLI 无 MCP 运行。
4. API 运行渠道去工具化：决策协议只保留 PLAN/RESULT/NEEDS_INPUT，职责描述+任务+轨迹驱动。

### 本期交付范围

1. server：删除工具层文件与全部 `toolCodes` 逻辑；`AgentNativeCliAdapter`/两个 CLI 协议改注入标准 MCP；`AgentApiExecutionAdapter` 决策协议收缩；提示词模板 `agent.api.step`/`agent.cli.step` 升版；迁移 drop `pm_agent_tool`、清理存量 JSON、删 intake 工具配置键。
2. web：配置对话框删"可用工具"、任务对话框删"任务工具"、数字员工页删"工具管理"入口与"N 项工具"列文案，删除 `AgentToolsDialog.vue` 与 agent-tools API。

### 明确不做

1. **不删除自治子系统**：巡检/代码工作区/候选变更/构建测试/发布计划/验证/问题协作的服务、表与人工审核页面全部保留（本次仅移除 AI 工具入口，AI 不再能驱动这些流程；是否彻底下线另行立项）。
2. 不改 WorkHub MCP 服务器本体（工具清单、守卫、审计不变），不改任务产物类型字段（`expectedArtifacts`/`completionTargets` 保留，但不再有工具回执背书）。
3. 不新增菜单/权限项；`agent:view`/`agent:manage` 语义沿用。

### 约束与依赖

- 安全边界变化（用户已确认）：运行中的 CLI 将持有平台全局 MCP Token（原先每次运行是独享 Token + 任务白名单）；MCP 服务器自身守卫（只读 SQL 策略、目录/服务白名单、脱敏、审计、Token 常量时间校验）仍生效。
- 依赖现有 `workhub.mcp.access-token` 配置与 `/api/mcp/runtime` 端点；`pm_agent_run` 历史快照不改动（Jackson 忽略未知字段，旧数据可读）。
- 不安装依赖；不主动部署；未经要求不 commit/push。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 数字员工配置与任务分派界面不再出现"可用工具/任务工具/工具管理"；保存与分派正常 | 前端构建 + 手工联调 | 用户 |
| AC-02 | `AgentConfiguration`/`AgentTaskSpecification` 无 `toolCodes`；`/api/agents/options` 不再返回 `tools[]`；`/api/agent-tools` 404 | 服务/controller 测试 | 用户 |
| AC-03 | CLI 运行的启动参数注入 `mcp_servers`（指向 `/api/mcp/runtime`、Token 走环境变量、无工具白名单参数）；`workhub.mcp.access-token` 未配置时不注入 MCP 配置 | 协议测试 + 代码核对 | 用户 |
| AC-04 | API 渠道决策协议只认 PLAN/RESULT/NEEDS_INPUT；提示词模板已升版且无 toolCatalog/工具操作指引 | 迁移 SQL 核对 + 服务测试 | 用户 |
| AC-05 | 迁移后 `pm_agent_tool` 不存在、存量配置 JSON 无 `toolCodes`、intake 工具配置键删除；`pm_agent_run` 历史快照未改动 | 迁移测试 | 用户 |
| AC-06 | 自定义工具层代码清零（AgentTool/目录/桥接/*Tools/工具管理），自治子系统服务与人工页面保留可用；后端全量测试与前端构建通过 | 测试报告 + 构建日志归档 | 用户 |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
