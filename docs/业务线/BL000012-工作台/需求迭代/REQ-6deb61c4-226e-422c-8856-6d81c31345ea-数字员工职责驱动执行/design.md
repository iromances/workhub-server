# Design：数字员工职责驱动执行

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-6deb61c4-226e-422c-8856-6d81c31345ea |
| 需求名称与唯一需求目录 | 数字员工职责驱动执行；`docs/业务线/BL000012-工作台/需求迭代/REQ-6deb61c4-226e-422c-8856-6d81c31345ea-数字员工职责驱动执行/` |
| 涉及仓库与功能域 | workhub-server：agent 域（模型/服务/适配器/协议/迁移）；workhub-web：数字员工页 |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1 |
| 状态 | 已确认（与 Proposal 同时确认） |
| 当前事实核对环境、分支、时间 | server `codex/development-20261102`、web `codex/development-20261103`，2026-11-10 静态核对源码 |

## 1. 现状与证据

- 工具定义与暴露：`workhub-service/.../agent/AgentTool.java` 接口 + 11 个 `@Configuration` `*Tools.java`（50 个 `@Bean`），`AgentToolCatalog` 聚合；API 渠道 `AgentApiExecutionAdapter.prompt()` 以 `specification().toolCodes()` 构造 toolCatalog 注入提示词；CLI 渠道 `AgentNativeCliAdapter` 创建每运行独享 `AgentMcpBridge`（127.0.0.1 随机端口 + 随机 Token），`AgentCliProtocol.prepare(..., mcpUrl, token, toolNames, prompt)` 注入 CLI。
- 白名单链：`AgentConfiguration.toolCodes`（≤64）⊇ 任务 `AgentTaskSpecification.toolCodes` ⊇ 运行时实际调用，`AgentAccessService.requireExecution` 三方实时复核。
- 工具管理层（REQ-f573f4cb，本次撤销）：`pm_agent_tool` 表、`GET/PUT /api/agent-tools`（AgentToolController/AgentToolAdminService）、options `tools[]`、web `AgentToolsDialog.vue` 与两个选择器。
- 标准 MCP 入口：`POST /api/mcp/runtime`（`McpRuntimeController` + `workhub-mcp-server` 模块），Bearer Token `workhub.mcp.access-token`（`WORKHUB_MCP_ACCESS_TOKEN` 优先），能力见 [docs/mcp-server.md](../../../mcp-server.md)：目标发现、业务线上下文、只读 SQL、服务状态与日志、服务器目录/文件读取；自带策略守卫、脱敏与 JSONL 审计。
- 提示词模板存于 `ai_use_case_config`（`agent.api.step`/`agent.cli.step`），变量白名单在 `AiUseCaseDefinitions`。

## 2. 系统架构

删除自定义工具层，数字员工 = 职责描述（instructions）+ 范围（scopes）+ AI 接入/模型 + 预算；执行渠道二选一：

- **CLI 渠道**：`AgentNativeCliAdapter` 不再创建 `AgentMcpBridge`，改为向协议注入平台标准 MCP 服务器 `http://127.0.0.1:{server.port}/api/mcp/runtime` + `workhub.mcp.access-token`（经环境变量传给 CLI 进程）；无 Token 则不注入 MCP 配置。AI 依据职责描述自行选择 MCP 能力，MCP 侧守卫与审计兜底。
- **API 渠道**：`AgentApiExecutionAdapter` 决策协议收缩为 PLAN/RESULT/NEEDS_INPUT，无工具调用；职责描述 + 任务规格 + 轨迹驱动。

| 模块/系统 | 负责 | 不负责 | 变更 |
| --- | --- | --- | --- |
| AgentNativeCliAdapter + AgentCliProtocol | CLI 进程编排与参数注入 | 工具授权 | MCP 地址/Token 改平台入口；`prepare` 去掉 toolNames 参数 |
| AgentApiExecutionAdapter | API 步进与决策落轨迹 | 工具执行 | 删 TOOL_CALL 分支与 toolCatalog 变量 |
| AgentWorkbenchService / AgentAccessService | 配置与任务校验、运行授权 | 工具白名单 | 删 toolCodes 校验与 `requireTool`/工具复核 |
| 自治子系统（巡检/代码/候选/构建/发布/验证/问题） | 人工页面与既有数据 | AI 驱动入口 | 仅删 `*Tools` 包装，服务/存储/控制器保留 |
| workhub-mcp-server | 标准 MCP 能力、守卫、审计 | — | 零改动 |

## 3. 契约与数据模型

- `AgentConfiguration`：删 `toolCodes` 组件；`AgentTaskSpecification`：删 `toolCodes` 与兼容构造器该参数。
- `GET /api/agents/options`：只返回 `providers[]` 与 `scopes[]`；`GET/PUT /api/agent-tools` 删除。
- `AgentApiDecision`：删 `tool`/`arguments` 字段，type ∈ {PLAN, RESULT, NEEDS_INPUT}（协议含 TOOL_CALL 视为格式错误）。
- `AgentCliProtocol.prepare(provider, model, directory, schema, output, mcpUrl, token, prompt)`：去 toolNames；`mcpUrl`/`token` 为 null 表示无 MCP。Codex 配置键 `mcp_servers.workhub.*`（url、bearer_token_env_var、required、tool_timeout_sec），仅在 Token 配置时注入；ZCode 不再传允许工具清单。
- 提示词模板升版（prompt_version=2）：`agent.api.step` 删 `toolCatalog` 变量与 TOOL_CALL 协议行及工具回执要求；`agent.cli.step` 删全部具体工具操作指引，改为"依据职责描述自主规划，使用 workhub MCP 服务器提供的 WorkHub 能力（自行选择）"；`AiUseCaseDefinitions` 变量白名单同步。
- 迁移 `V20261110_130000__remove_agent_tool_layer.sql`（版本号避让同日 REQ-9d5d6044 的 120000）：
  1. `DROP TABLE IF EXISTS pm_agent_tool`；
  2. `pm_agent_definition.configuration_json`、`pm_agent_task.specification_json` 以 `JSON_REMOVE($.toolCodes)` 清理存量（`JSON_VALID` + `JSON_CONTAINS_PATH` 守卫；`pm_agent_run` 快照为历史证据不动）；
  3. `DELETE FROM sys_config_item WHERE config_group='intake.aiDevelopment' AND config_key='toolCodes'`；
  4. 两条 agent 模板 UPDATE 为 v2 文案并重算 checksum。
- 兼容：Jackson（tools.jackson）忽略未知 JSON 字段，含 `toolCodes` 的历史运行快照可正常反序列化；旧任务/配置在迁移后读取即新模型。

## 4. 非功能约束

- 安全：CLI 进程环境沿用 `restrictEnvironment` 白名单，仅新增 Token 变量；平台 MCP Token 的暴露面从"逐运行随机 Token + 任务白名单"变为"全局 Token + MCP 侧策略守卫/审计"（用户已确认）。Token 未配置时 CLI 无 MCP，运行不失败。
- 审计：MCP 调用经 `McpAuditLogger`（JSONL）审计；运行轨迹保留 PLAN/MODEL/RESULT 事件，工具回执事件随工具层消失。
- 性能：options 接口减少一次 `pm_agent_tool` 全表读；CLI 提示词不再拼接 50 工具目录，上下文显著变小。
- 测试数据：H2 + 真实迁移（沿用 AgentWorkbenchServiceTest 模式），不连业务库。

## 5. 交付与回滚

- 顺序：先 server（代码+迁移+测试），后 web（界面+类型）；可独立发布。
- CLI 接入端点：默认 `http://127.0.0.1:{server.port}/api/mcp/runtime`，可经 `workhub.agent.cli-mcp-endpoint` 覆盖；`workhub.mcp.access-token`（或 `WORKHUB_MCP_ACCESS_TOKEN`）未配置时不注入 MCP。
- 回滚：应用回滚保留数据；彻底回滚需恢复工具层代码与 `pm_agent_tool` 表（Flyway 反向脚本不自动执行）；存量 `toolCodes` 已从 JSON 清理，回滚后旧配置等价于未勾选工具，需重新配置。
- 生效后更新：`现状.md`（数字员工节）、`迭代日志.md`、`docs/mcp-server.md`（补数字员工 CLI 复用入口说明）；验证证据归档至本目录 `验证/20261110-职责驱动执行/`。

## 6. 一致性与待确认

- Proposal 目标 1→模型/校验/options 与 web 删除；目标 2→工具层文件删除；目标 3→CLI 协议与 Token 注入；目标 4→API 决策协议与模板升版。
- 范围裁决（用户确认记录见 tasks.md）：自治子系统保留不删；全局 MCP Token 交给 CLI 运行已确认。
- 待确认：无。

关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
