# Design：CLI 通道限制清理与 ZCode 仓库读取

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-d97d16b2-1380-4d78-8fb5-a6022efdd7b1 |
| 需求名称与唯一需求目录 | CLI 通道限制清理与 ZCode 仓库读取；docs/业务线/BL000012-工作台/需求迭代/REQ-d97d16b2-1380-4d78-8fb5-a6022efdd7b1-CLI通道限制清理与ZCode仓库读取/ |
| 涉及仓库与功能域 | workhub-server / workhub-mcp-server（MCP 工具）、workhub-service（AI 网关、MCP 运行时、场景定义） |
| 所属项目或业务线（适用时） | BL000012-工作台 |
| 对应 Proposal 版本 | 草案 v1 |
| 状态 | 草案，随完整规格实施前统一确认 |
| 当前事实核对环境、分支、时间 | 本机 codex/development-20261102；ZCode 0.17.2 官方分发源码只读核实；2026-11-11 |

## 1. 现状与证据

- 拦截点：`DefaultAiGatewayClient.executeCli` ZCODE 分支原守卫 `definition.capability() == LOCAL_REPOSITORY_READ` 抛"ZCode 的代码操作请使用 Agent 工作台授权工具"。
- 场景限制：`AiUseCaseDefinitions` 三场景 `allowedCliTypes=Set.of("CODEX")`；前端 `useCaseChannelAllowed`（AiConfigView.vue:287）据此过滤供应商下拉。
- Codex 参照：`CodexCliClarificationAnalysisGenerator` / `CodexCliDevelopmentAnalysisGenerator` 传 `workingDirectory=repositoryBundle.localRoot()`（GitlabRepositoryService.DEFAULT_CODE_WORKSPACE_ROOT=/Users/aslight/IDEAWorkspace 下按组检出）与各仓库路径。
- MCP 通路参照：`AgentNativeCliAdapter.mcpUrl()/mcpAccessToken()`（`workhub.agent.cli-mcp-endpoint` 覆盖、默认 `http://127.0.0.1:{server.port}/api/mcp/runtime`；token 取 `WORKHUB_MCP_ACCESS_TOKEN` 环境变量或 `workhub.mcp.access-token`）；`ZCodeAgentCliProtocol` 以 `McpRuntimeService.toolNames()` 全量授权先例。
- 速度限制依据：ZCode 0.17.2 `zcodeSessionCreateParamsSchema`（shared/src/zcode-protocol/index.ts:1980-2005，strict）无 speed 字段——CLI 协议不支持速度调整，保留 STANDARD-only 有据。
- 未注册场景事实：`intake.development.sdd-analyze`、`intake.submit-test.review` 仅存在于 ai_use_case_config 表，`AiUseCaseDefinitions.require()` 对其抛"未注册的 AI 场景"——在途半成品。

## 2. 系统架构

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| RepositoryFileService（新增） | 白名单解析与三项只读操作、安全校验 | 工具协议组装 | 路径→目录/文本/匹配结果 | workhub.mcp.repository-read-roots |
| McpRuntimeService | 注册三个工具（白名单空不注册）；toolNames 下发 | 白名单内容管理 | MCP tools/list、tools/call | RepositoryFileService |
| AiUseCaseDefinitions | 场景能力边界（CODEX+ZCODE） | 运行时校验 | Definition | — |
| DefaultAiGatewayClient ZCODE 分支 | 按场景分支注入 MCP 与仓库提示；无 token 明确失败 | 内置工具面 | 10 参 execute | ObjectProvider<McpRuntimeService>（延迟防循环依赖） |
| ZCodeCliClient/ZCodeTaskConversation | mcpServers 注入、工具授权校验（既有） | 白名单语义 | session/create+mcpServers | ZCode 0.17 协议 |

数据流：澄清/研发分析 → gateway executeStructured → ZCODE 分支（repository=true）→ prepare(toolNames 全量, mcpUrl, token) → session/create 带 mcpServers(workhub_task) → AI tools/list 自发现 → 只读文件工具读仓库 → 结果 JSON → 格式校正（既有）。

## 3. 契约与数据模型

- MCP 工具契约（tools/call 参数与返回）：
  - `list_repository_files(path!, maxDepth?, maxEntries?)` → `{root, truncated, entries[{path,directory}]}`；默认深度 6 / 条目 200，上限 12 / 2000
  - `read_repository_text_file(path!, maxBytes?)` → `{path, bytes, content}`；默认 256KB，上限 1MB；拒链接/特殊文件/二进制
  - `search_repository_text(path!, keyword!, maxMatches?)` → `{root, keyword, truncated, matches[{path,line}]}`；固定串非正则；默认 30，上限 100；跳过二进制与 >2MB 文件
- 白名单：`workhub.mcp.repository-read-roots`（逗号/分号分隔绝对路径，normalize 后前缀匹配；根目录本身不可读；空=三工具不注册）。AI 无自行扩展通道。
- 网关内部分支：`repository = capability == LOCAL_REPOSITORY_READ`；repository=true 时要求 token（缺失抛 `ZCode 通道本地仓库读取需配置 workhub.mcp.access-token`），mcpUrl 默认本机端点，toolNames=MCP 全量；false 时三参传 null/null/List.of()（现状等价）。
- 提示词附注：repository 场景在 prompt 尾部追加"代码读取约束"（工具名 + 仓库根 + 授权目录），不影响 schema 段。
- 无数据库结构变更；`ai_use_case_config` 的通道绑定仍由页面管理（澄清/研发分析改绑先行通属配置操作）。

## 4. 非功能约束

- 安全：新增工具全部只读；路径白名单前缀校验 + NOFOLLOW；二进制/超限拒绝；不执行任何文件；工具面经 ZCode 会话 toolAllowlist 精确授权（tool.updated 事件校验，越权即失败——既有机制）。
- 性能：search 上限深度 12、单文件 2MB；list 有界；read 有界。
- 失败处理：IO 异常包装为 IllegalArgumentException（"仓库文件访问失败：…"），经 MCP 错误响应与识别 warnings 可见。
- 循环依赖：Gateway→McpRuntimeService→IntakeService→…→Gateway 环存在，以 `ObjectProvider` 延迟解析（getIfAvailable 为 null 时明确报错）。

## 5. 交付与回滚

- 发布顺序：单仓库单模块；配置先行（access-token、repository-read-roots）。
- 回滚：恢复守卫与场景定义、注销三工具即可；无数据库变更。
- 生效后更新：Tasks 证据、迭代日志；限制盘点表（含保留项依据）入 Design §6。

## 6. 限制盘点表（设计定稿）

| 限制 | 性质 | 处置 |
| --- | --- | --- |
| 图片附件拦截（ZCode） | 适配缺失 | 已补齐（REQ-1ad2035e） |
| LOCAL_REPOSITORY_READ 拦截（ZCode） | 适配缺失 | 本次补齐并删除 |
| 3 场景 CODEX-only | 适配缺失 | 本次放开 CODEX+ZCODE |
| ZCode 速度仅 STANDARD | CLI 协议本质 | 保留（§1 源码依据） |
| agent.api.step 无 CLI 通道 | 执行链本质 | 保留 |
| sdd-analyze / submit-test.review 未注册 | 在途半成品 | 不在范围 |
