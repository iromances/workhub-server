# 数字员工删除工具层，职责描述 + WorkHub MCP 直接驱动

## Context（背景）

数字员工现在的执行架构：Agent 配置勾选"可用工具"（toolCodes 白名单）→ 任务再勾子集 → 运行时通过自定义 `AgentTool` 目录（50 个工具）+ 每次运行独享的 `AgentMcpBridge`（本机 HTTP MCP）桥接 WorkHub 能力，11-07 又加了工具文案管理（`pm_agent_tool`）。用户判定这套"工具能力架构"整体不要了：

- **删除全部自定义工具层**（AgentTool 目录、11 个 *Tools 包装、AgentMcpBridge、toolCodes、工具管理）。
- AI 执行时**直接使用 WorkHub 已支持的标准 MCP 服务器**（`POST /api/mcp/runtime`，即 workhub-mcp-server 模块：业务线上下文、只读 SQL、日志、服务状态、文件读取等，自带策略守卫与审计），**用哪些能力由 AI 依据职责描述自行选择**。
- 前后端一起改。本计划批准即视为 SDD 规格确认。

> **范围判断（需知悉）**：巡检领取/代码候选/构建测试/发布审核/验证等自治流程的工具入口一并消失，其后台服务、表和人工审核页面**本次保留不动**（仅 AI 不再能驱动，历史数据仍可查看）；是否彻底下线这些自治子系统，另行立项。任务产物类型（CHANGE_SET/DEPLOYMENT 等）保留字段，但不再有工具回执背书。

## 一、SDD 建档（workhub-server）

新建 `docs/业务线/BL000012-工作台/需求迭代/REQ-{UUIDv4}-数字员工职责驱动执行/`：`proposal.md`（含撤销 REQ-f573f4cb 及工具层移除决策）、`design.md`、`tasks.md`。

## 二、后端 workhub-server

### 1. 删除工具层（service/controller/dao/model）
- 删文件：`AgentTool`、`AgentToolCatalog`、`AgentToolContext`、`AgentToolArguments`、`AgentToolReplies`、`AgentMcpBridge`、`AgentToolAdminService`、11 个 `*Tools.java`（Bug/Monitoring/Inspection/Code/BuildTest/Jenkins/Change/Delivery/Release/AutonomyCase/Verification）、`AgentToolController`、`AgentToolMapper`+XML、`AgentToolSaveRequest`/`AgentToolAdminResponse`/`AgentToolRegistrationEntity`。
- 保留：`AgentToolData`（通用脱敏，适配器还在用）、各自治子系统 Service/Store/Controller（人工页面继续可用）。

### 2. 模型与校验
- `AgentConfiguration`/`AgentTaskSpecification`：删 `toolCodes`。
- `AgentWorkbenchService`：删 toolCodes 相关校验与快照字段。
- `AgentAccessService`：删 `requireTool`、`requireExecution` 的 toolCodes 包含检查与逐工具权限复核（工具层没了，调用方不复存在）。
- `AgentRunReconciliationService`：删逐工具授权循环；保留进程级对账，删工具调用级对账。
- `IntakeAiDevelopmentService`：删 `CONFIG_KEY_TOOL_CODES`/`splitToolCodes`。

### 3. CLI 运行时改接 WorkHub 标准 MCP
- `AgentNativeCliAdapter`：不再创建 `AgentMcpBridge`；MCP 地址改为 `http://127.0.0.1:{server.port}/api/mcp/runtime`，Token 复用 `workhub.mcp.access-token`（未配置则 CLI 无 MCP 运行）。
- `AgentCliProtocol.prepare` 签名去掉 toolNames 白名单参数；`CodexAgentCliProtocol` 的 `mcp_servers.workhub_task.*` 配置改为按需注入 workhub 服务器（无 token 时省略）；`ZCodeAgentCliProtocol` 去掉 `mcp__workhub_task__*` 允许清单（不限制）。
- `AgentOptionsService`/`AgentOptionsResponse`：删 tools 输出。

### 4. API 渠道去工具化
- `AgentApiExecutionAdapter`：决策协议只留 PLAN/RESULT/NEEDS_INPUT，删 TOOL_CALL 分支与 `beginApiTool` 调用；`AgentApiDecision` 删 tool/arguments 字段。
- `AgentRunStore`：工具回执相关方法随调用方消失一并清理。

### 5. 提示词模板迁移（DB ai_use_case_config）
- `agent.api.step` 升版：删 `toolCatalog` 变量与 TOOL_CALL 协议行，职责描述+任务+轨迹驱动。
- `agent.cli.step` 升版：删全部具体工具操作指引（inspection.next/change.prepare/build.test/release.* 等），改为"依据职责描述自主规划，用 workhub MCP 服务器提供的 WorkHub 能力（自行选择）"。
- 同步 `AiUseCaseDefinitions` 变量白名单。

### 6. 数据迁移 `V20261110xxxxxx__remove_agent_tool_layer.sql`
- `DROP TABLE IF EXISTS pm_agent_tool`；
- `pm_agent_definition.configuration_json`、`pm_agent_task.specification_json` 清理存量 `$.toolCodes`（`JSON_VALID` 守卫；`pm_agent_run` 快照作为历史证据不动，Jackson 忽略未知字段）；
- 删 `sys_config_item` 的 `(intake.aiDevelopment, toolCodes)` 行；
- 升版两条 agent 提示词模板（更新 template/checksum/prompt_version）。

### 7. 测试
- 删：`AgentToolAdminServiceTest`、`AgentToolControllerTest`、`AgentMonitoringToolsTest`、`AgentBugToolsTest`、`AgentReleaseToolsTest` 等 *Tools 测试。
- 改：`AgentWorkbenchServiceTest`、`AgentChangeServiceTest`、`AgentReleasePlanServiceTest`、`CodexAgentCliProtocolTest`、`AgentInspectionServiceTest`、`AgentVerificationServiceTest`、`AutonomyCaseServiceTest` 等构造与断言。
- 全量 `mvn test` 通过（H2 + 真实 Flyway 迁移）。

## 三、前端 workhub-web

- `AgentConfigDialog.vue` 删"可用工具"；`AgentTaskDialog.vue` 删"任务工具"；`AgentWorkbenchView.vue` 删"工具管理"按钮/`AgentToolsDialog`/列表"N 项工具"；删 `AgentToolsDialog.vue`。
- `api/agent.ts` 删 agent-tools 接口；`types/agent.ts` 删 toolCodes/`AgentToolRegistration`/options.tools。
- `npm run build` 通过。

## 四、交付

- 更新 `docs/业务线/BL000012-工作台/现状.md`（数字员工节改为职责驱动+标准 MCP）、`迭代日志.md`；验证证据归档需求目录 `验证/`。
- `docs/mcp-server.md` 补一句：数字员工 CLI 运行时也经此入口访问（同 Token）。
- 不自动 git 提交。

## 安全说明（需知悉）

运行中的 CLI 将持有平台全局 MCP Token（原先每次运行是独享最小 Token + 白名单）。MCP 服务器自身的守卫仍生效：只读 SQL 策略、目录/服务白名单、内容脱敏、JSONL 审计、Token 常量时间校验。这是"AI 自行选择 WorkHub 支持能力"的直接后果。

## 验证方式

1. 后端全量 `mvn -q test`。
2. 前端 `npm run build`（vue-tsc）。
3. 双仓库 grep `toolCodes|agent-tools|AgentToolsDialog|AgentToolCatalog|AgentMcpBridge` 清零（docs 归档与保留的自治子系统除外）。
4. 代码级核对 CLI 启动参数：MCP 指向 `/api/mcp/runtime`、Token 注入环境变量、无白名单参数。