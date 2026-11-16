# Proposal：CLI 通道限制清理与 ZCode 仓库读取

> SDD 流程的 Proposal 模板。随完整规格在实施前统一确认。AI 不得自行填写"已批准"。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-d97d16b2-1380-4d78-8fb5-a6022efdd7b1 |
| 需求名称 | CLI 通道限制清理与 ZCode 仓库读取 |
| 涉及仓库与功能域 | workhub-server（workhub-mcp-server：MCP 工具；workhub-service：AI 网关、MCP 运行时、场景定义）；分支 codex/development-20261102 |
| 所属项目或业务线（适用时） | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-d97d16b2-1380-4d78-8fb5-a6022efdd7b1-CLI通道限制清理与ZCode仓库读取/ |
| 版本/状态 | 草案 v1，随完整规格实施前统一确认 |
| 目标系统 | WorkHub 后端（workhub_task MCP 运行时、AI 网关 ZCode CLI 通道；外部依赖 ZCode CLI 0.17 app-server 协议） |
| 原始需求来源及版本 | 用户设计原则："WorkHub 不在自身层面人为限制对 Codex/ZCode CLI 的调用，能力边界由 CLI 本身决定"；前序 REQ-1ad2035e 已打通 ZCode 图片通路 |
| 目标环境/分支 | 本地开发环境；codex/development-20261102 |
| 负责人/评审人 | 用户（发起与确认）；AI 协作（实施） |

## 1. Why：背景与问题

- 当前问题及证据：
  - 三个 LOCAL_REPOSITORY_READ 场景（需求澄清分析、研发方案分析/调整）场景定义 `allowedCliTypes` 仅 CODEX，网关对 ZCode 通道直接拦截（DefaultAiGatewayClient "ZCode 的代码操作请使用 Agent 工作台授权工具"）——同为 CLI 通道，能力由 WorkHub 人为限定。
  - 该拦截的本质原因是 ZCode task host 沙箱隐藏内置文件工具，ZCode 会话读不了真实仓库目录；但 WorkHub 已有受控 MCP 工具体系（全部只读+脱敏），具备以企业工具通路补齐读取能力的条件。
- 受影响用户/角色：使用需求澄清分析、研发方案分析/调整的用户（希望走先行通/ZCode 通道）；AI 配置管理员。
- 不解决的后果：上述场景仅能绑 Codex CLI；通道选择受 WorkHub 配置层限制而非 CLI 实际能力。

## 2. What：目标与范围

### 本期目标

1. 消除 WorkHub 对 CLI 通道的适配缺失型限制：LOCAL_REPOSITORY_READ 拦截删除、三个场景 allowedCliTypes 放开 CODEX+ZCODE。
2. 以 workhub_task MCP 新增本地只读文件工具（白名单限定）补齐 ZCode 会话的仓库读取能力，安全边界由 WorkHub 全量控制。

### 本期交付范围

1. `RepositoryFileService`（新增）：白名单内 list/read/search 三种只读操作，含链接拒绝、二进制拒绝、大小/条数/匹配数上限。
2. `McpRuntimeService`：注册 `list_repository_files` / `read_repository_text_file` / `search_repository_text`；白名单配置 `workhub.mcp.repository-read-roots`（空=工具不注册）。
3. `AiUseCaseDefinitions`：clarification.analyze / development.analyze / development.adjust → `Set.of("CODEX", "ZCODE")`。
4. `DefaultAiGatewayClient` ZCODE 分支：删除 LOCAL_REPOSITORY_READ 守卫；仓库场景注入 MCP（access-token + cli-mcp-endpoint/默认本机 `/api/mcp/runtime`），无 token 抛明确错误；提示词附仓库根与工具引导；非仓库场景保持无 MCP 现状。
5. `ZCodeCliClient.execute` 扩展 mcpUrl/token/toolNames 透传（prepare 已支持）。
6. 测试同步与定向验证。

### 明确不做

1. 不放开 ZCode 内置文件/Shell 工具、不改 HOME 隔离沙箱。
2. 不改 ZCode 速度限制（STANDARD-only）——CLI 协议本质：session/create strict schema 无 speed 字段（0.17.2 源码核实；Codex 有 model_reasoning_effort，ZCode 无对应）。
3. 不放开 `agent.api.step`——执行链本质：`executeAgentApi` 为纯 API 调用，CLI 分发链不存在；数字员工走 ZCode 的通道是 agent.cli.step（已支持）。
4. 不处理 `intake.development.sdd-analyze` / `intake.submit-test.review`——数据库有配置行但代码未注册（在途半成品，require() 抛"未注册的 AI 场景"），归属其在途需求。
5. 不新增图片、超时等其他能力变化。

### 约束与依赖

- 依赖：REQ-1ad2035e 的 ZCode 协议链路（execute 10 参、attachments）；workhub_task MCP 既有安全模式（allowedLogPaths 白名单语义、全工具只读+脱敏）。
- 前置：`workhub.mcp.access-token` 配置（Agent 工作台已在用）；MCP 与服务同进程部署（默认本机端点）。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 配置 access-token 与白名单后，澄清分析场景绑先行通执行"开始澄清"，ZCode 会话 tools/list 可见仓库只读工具且分析成功 | 端到端日志核对 | 用户 |
| AC-02 | 白名单内路径 list/read/search 正常；白名单外、根目录本身、符号链接、二进制、超限内容全部明确拒绝 | RepositoryFileServiceTest | 本仓库测试规范 |
| AC-03 | 无 access-token 时仓库场景识别为明确失败（"需配置 workhub.mcp.access-token"），不静默降级 | 网关单测 | 代码风格（不伪装成功） |
| AC-04 | 非 LOCAL_REPOSITORY_READ 的 ZCode 场景（结构化提取等）不带 mcpServers，行为与改动前一致 | 网关单测回归 | Design 兼容性约束 |
| AC-05 | 速度限制、连接测试、agent.api.step 行为不变 | 既有用例回归 | Design 范围边界 |
