# Tasks：数字员工职责驱动执行

## 1. 元信息

- 需求编码：REQ-6deb61c4-226e-422c-8856-6d81c31345ea
- 需求名称与唯一需求目录：数字员工职责驱动执行；`docs/业务线/BL000012-工作台/需求迭代/REQ-6deb61c4-226e-422c-8856-6d81c31345ea-数字员工职责驱动执行/`
- 涉及仓库与功能域：workhub-server（model/dao/service/controller/适配器/协议/迁移/测试）、workhub-web（api/types/数字员工页）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：v1
- Design/契约版本：v1
- 目标环境与分支：server `codex/development-20261102`；web `codex/development-20261103`；均含用户在途改动，仅做本需求修改
- 任务计划状态：已确认
- 负责人/评审人：用户

## 2. 规格确认记录

- 2026-11-10 用户批准实施计划（本文件与 proposal/design 同步定稿），关键裁决：
  1. 彻底删除自定义工具层（含内部工具执行能力），AI 经 WorkHub 标准 MCP 依职责描述自行选择能力。
  2. 撤销 REQ-f573f4cb 工具管理全部产物。
  3. 前后端一起改。
  4. 平台全局 MCP Token（`workhub.mcp.access-token`）交由 CLI 运行使用；MCP 侧守卫与审计兜底。
  5. 自治子系统（巡检/代码候选/构建测试/发布/验证/问题协作）本次保留服务、表与人工页面，仅删 AI 工具入口；是否下线另行立项。

## 3. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | SDD 建档（proposal/design/tasks） | 无 | 全文 | 已完成 |
| T-02 | 删除工具层文件（AgentTool/目录/桥接/11 个 *Tools/工具管理/模型/DAO/控制器） | T-01 | §2 / AC-02 AC-06 | 已完成（AgentToolContext/AgentToolReplies/AgentToolData 因被保留子系统与历史回执恢复使用而保留，见验证说明偏差记录） |
| T-03 | 模型与校验去 toolCodes（Configuration/Specification/Workbench/Access/Reconciliation/Intake/Options） | T-02 | §3 / AC-02 | 已完成 |
| T-04 | CLI 运行时改接标准 MCP（NativeAdapter/两协议/Token 注入） | T-02 | §2 §3 / AC-03 | 已完成（新增 `workhub.agent.cli-mcp-endpoint` 覆盖项；ZCode 允许清单取 `McpRuntimeService.toolNames()` 全量） |
| T-05 | API 渠道去工具化（ApiAdapter/ApiDecision/RunStore 清理） | T-02 | §3 / AC-04 | 已完成 |
| T-06 | 提示词模板升版 + AiUseCaseDefinitions 同步 + 数据迁移 SQL（`V20261110_130000`，避让同日 120000） | T-04 T-05 | §3 / AC-04 AC-05 | 已完成 |
| T-07 | 后端测试修复与全量 mvn test | T-02~T-06 | §4 / AC-02~AC-06 | 已完成（995 项，本需求范围 0 失败；2 项既有无关失败见验证说明） |
| T-08 | 前端 workhub-web 删除工具 UI/API/类型，npm run build | T-03 契约 | §2 / AC-01 | 已完成 |
| T-09 | 交付文档（现状.md、迭代日志.md、mcp-server.md）与验证证据归档 | T-07 T-08 | §5 / AC-06 | 已完成 |

执行顺序：T-02 → T-03/T-04/T-05 并行 → T-06 → T-07/T-08 并行 → T-09。

## 4. 任务明细

### T-02：删除工具层文件

- 删除：`AgentTool`、`AgentToolCatalog`、`AgentToolContext`、`AgentToolArguments`、`AgentToolReplies`、`AgentMcpBridge`、`AgentToolAdminService`、11 个 `*Tools.java`、`AgentToolController`、`AgentToolMapper`+XML、`AgentToolSaveRequest`/`AgentToolAdminResponse`/`AgentToolRegistrationEntity` 及对应测试（AgentToolAdminServiceTest、AgentToolControllerTest、AgentMonitoringToolsTest、AgentBugToolsTest、AgentReleaseToolsTest 等 *Tools 测试）。
- 保留：`AgentToolData`（通用脱敏）、自治子系统 Service/Store/Controller。
- 验收：删除后主代码对上述符号零引用。

### T-03：模型与校验去 toolCodes

- `AgentConfiguration`/`AgentTaskSpecification` 删组件与兼容构造器参数；`AgentWorkbenchService` 删校验与快照字段；`AgentAccessService` 删 `requireTool` 与工具复核；`AgentRunReconciliationService` 删逐工具循环与工具调用级对账；`IntakeAiDevelopmentService` 删 `CONFIG_KEY_TOOL_CODES`/`splitToolCodes`；`AgentOptionsService`/`AgentOptionsResponse` 删 tools。
- 验收：`grep toolCodes` 主代码清零（运行快照读取处除外——历史 JSON 忽略未知字段）。

### T-04：CLI 运行时改接标准 MCP

- `AgentNativeCliAdapter`：删 bridge 创建；MCP url=`http://127.0.0.1:{server.port}/api/mcp/runtime`，token=`workhub.mcp.access-token`（@Value 注入，未配置传 null）；协议 `prepare` 去 toolNames。
- Codex 协议：Token 存在时注入 `mcp_servers.workhub.*`（url/bearer_token_env_var/required/tool_timeout_sec），否则省略；ZCode 协议：mcpUrl/token 透传，无允许清单。
- 验收：协议测试断言新参数与按需注入；AC-03。

### T-05：API 渠道去工具化

- `AgentApiExecutionAdapter`：删 TOOL_CALL 分支、`beginApiTool`、toolCatalog 变量；`AgentApiDecision` 删 tool/arguments；`AgentRunStore` 工具回执方法随调用方清理。
- 验收：决策解析对 TOOL_CALL 报格式错误；AC-04。

### T-06：模板升版与数据迁移

- `AiUseCaseDefinitions`：AGENT_API_STEP_VARIABLES 删 toolCatalog；模板变量描述同步。
- 迁移 `V20261110xxxxxx__remove_agent_tool_layer.sql`：drop `pm_agent_tool`、JSON 清理 toolCodes、删 intake 配置键、两条模板 UPDATE v2+checksum。
- 验收：H2 全程执行；AC-04 AC-05。

### T-07：后端测试

- 修复 `AgentWorkbenchServiceTest`、`AgentChangeServiceTest`、`AgentReleasePlanServiceTest`、`CodexAgentCliProtocolTest`、`AgentInspectionServiceTest`、`AgentVerificationServiceTest`、`AutonomyCaseServiceTest` 等受影响构造/断言；全量 `mvn test`。

### T-08：前端

- 删"可用工具/任务工具/工具管理"UI、`AgentToolsDialog.vue`、agent-tools API、类型字段；`npm run build`。

### T-09：交付文档与证据

- 更新 `现状.md` 数字员工节、`迭代日志.md`、`docs/mcp-server.md`；测试/构建证据归档 `验证/20261110-职责驱动执行/`。

## 5. 验证记录

2026-11-10 实施完成并自动验证，证据见 [验证/20261110-职责驱动执行/验证说明.md](验证/20261110-职责驱动执行/验证说明.md)：

- 后端 `mvn clean test` 995 项，本需求范围 0 失败（2 项为工作区既有无关失败：知识归档路径、ELK 范围，属用户在途改动）。
- 前端 `npm run build`（vue-tsc）通过，工具相关引用清零。
- 迁移 `V20261110_130000` 与提示词模板一致性经 `TestPromptTemplates`/`PromptTemplateMigrationConsistencyTest` 验证；真实 MySQL 执行与页面联调待部署。

## 6. 后续变更：CLI 运行时联调修复与首次真实巡检（2026-11-10/11 部署联调）

新版后端部署到本机运行实例后，经"保费分期监测巡检助手"周期巡检任务六次触发联调，修复两个运行时缺陷：

1. **失败原因可见性**：`AgentNativeCliAdapter` 的"CLI 协议执行失败/未返回有效协议结果"原先吞掉底层 cause，现把 `readerFailure` 消息并入运行错误信息（定位问题必需）。
2. **协议流总量护栏过低**：`ZCodeCliClient.readProtocol` 总量上限 4MB 字符——巡检类任务密集调用 MCP 只读查询（每结果 ≤64KB 且在 ZCode 事件流回显），约 3 分钟即触顶中断；上限提高到 64MB（流式计数不占内存）。相关测试 86 项通过。

第六次运行**成功完成**（263.6 秒，1 步，REPORT 产物落库）：ZCode 经平台 MCP 完成系统预警/业务监测/账务监测三域巡检，正确应用增量窗口（首轮建基线）、正确报告数据缺口（db-prod 堡垒机认证失败、账务未配置规则）、输出分级处理建议与需人工确认项——职责驱动执行架构端到端验证通过。过程与运维操作见 `docs/notes.md` 2026-11-10/11 各节。
