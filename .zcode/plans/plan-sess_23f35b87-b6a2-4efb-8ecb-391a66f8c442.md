# 需求：移除 WorkHub 对 CLI 通道的人为调用限制

## Context

**原则（用户确认）**：WorkHub 不在自身层面人为限制对 Codex/ZCode CLI 的调用，能力边界由 CLI 本身决定。据此逐层审视现有全部限制，属于"适配缺失"的一律补齐删除；属于"CLI 本质能力/执行链本质"的如实保留。

## 限制盘点与处置

| 限制 | 性质 | 处置 |
| --- | --- | --- |
| 图片附件拦截（ZCode） | 适配缺失 | ✅ 已补齐（REQ-1ad2035e，session/send attachments） |
| LOCAL_REPOSITORY_READ 拦截（ZCode） | 适配缺失 | **本次补齐**：MCP 只读文件工具通路，随后删除拦截 |
| 澄清/研发方案 3 场景 allowedCliTypes=CODEX-only | 适配缺失 | **本次放开** 为 CODEX+ZCODE |
| ZCode 速度仅 STANDARD | CLI 协议本质（session/create strict schema 无 speed 字段，已核实 0.17.2 源码；Codex 有 model_reasoning_effort，ZCode 无对应） | 保留，规格注明依据 |
| agent.api.step 无 CLI 通道 | 执行链本质（executeAgentApi 纯 API 调用，CLI 分发链不存在） | 保留；数字员工走 ZCode 的通道是 agent.cli.step（已支持） |
| sdd-analyze / submit-test.review 未注册 | 在途半成品（数据库有配置行、代码注册表缺失，require() 抛错） | 不在范围，向在途需求反馈 |

放开后 7 个已注册场景中 **6 个支持 ZCode**（structured.extract、sql-draft、agent.cli.step 已支持 + clarification.analyze、development.analyze、development.adjust 本次放开），agent.api.step 因协议本质除外。

## 实施步骤

### 1. SDD 建档
新 REQ 编码，`docs/业务线/BL000012-工作台/需求迭代/{REQ}-CLI通道限制清理与ZCode仓库读取/` 三份规格。

### 2. workhub_task MCP 新增 3 个本地只读文件工具
`workhub-mcp-server/.../McpToolRegistry.java`：`list_repository_files` / `read_repository_text_file` / `search_repository_text`（安全语义照抄 read_server_file/list_server_directory：不跟随链接、不执行、大小/条数/匹配数上限）。
白名单 `workhub.mcp.repository-read-roots`（空=工具不注册；建议默认 GitLab 检出根）。

### 3. 场景定义放开
`AiUseCaseDefinitions`：clarification.analyze / development.analyze / development.adjust → `Set.of("CODEX", "ZCODE")`。

### 4. 网关删除 LOCAL_REPOSITORY_READ 拦截
`DefaultAiGatewayClient` ZCODE 分支：删除守卫；注入 MCP（复用 `AgentNativeCliAdapter.mcpUrl()/mcpAccessToken()` 解析模式：`workhub.mcp.access-token` + `workhub.agent.cli-mcp-endpoint` 默认本机 `/api/mcp/runtime`）；toolNames 取 `McpRuntimeService.toolNames()` 全量（与 Agent 工作台一致，全部只读+脱敏）；无 token 抛明确错误；仓库根转为提示词上下文附注引导 AI 用只读工具。

### 5. ZCodeCliClient 透传
`execute` 增加 mcpUrl/token/toolNames（prepare 已支持）。

### 6. 测试同步
McpToolRegistry 新工具单测（白名单/上限/链接拒绝）；网关 repository 场景改为 ZCODE+MCP 透传断言（speed 拒绝保留并注明依据）；无 token 报错用例；会话 mcpServers 断言。

### 7. 验证
1. 定向单测（javac+MiniRunner 方式，规避工作区在途断点）
2. 端到端：配置 access-token 与白名单 → 澄清分析改绑先行通 → 开始澄清 → 核日志（mcpServers、tools/list、文件工具调用、分析成功）
3. 回归：已支持场景（结构化提取/SQL 草稿 ZCode、澄清分析 Codex）不变

### 8. 交付
Tasks 证据、迭代日志；限制盘点表（含保留项依据）写入 Design。

## 风险与回滚
- MCP 全量放开 = 澄清/研发 ZCode 会话可见只读 SQL/日志工具，与 Agent 工作台同等边界（全部只读+脱敏）
- 白名单未配置时文件工具缺席，分析退化为纯文本上下文（tools/list 可见缺失，非静默）
- 回滚：恢复守卫与场景定义即可，无数据库结构变更