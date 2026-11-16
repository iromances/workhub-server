# AI 提示词统一迁入 AI 任务配置

## Context

系统的 AI 提示词目前硬编码在 Java 代码里，无法在不发版的情况下调整：

- **5 个需求整理 AI 任务**（`intake.structured.extract` / `intake.sql-draft.generate` / `intake.clarification.analyze` / `intake.development.analyze` / `intake.development.adjust`）虽已有 AI 任务配置行，但 `prompt_template` 只是 `${prompt}` 占位符，真正的指令文本全部硬编码在各 `CodexCli*Generator` 的 `buildPrompt` 中，整段塞进 `${prompt}` 变量。
- **2 个 Agent 执行适配器**（`AgentApiExecutionAdapter.prompt()`、`AgentNativeCliAdapter.prompt()`）的系统提示词（约 20 行安全护栏 + 行动协议）完全硬编码，且没有 AI 任务配置行。

目标：提示词统一在 AI 任务配置（`ai_use_case_config`）中维护，代码只负责组装数据变量并渲染模板。协议辅助文本（连接探测提示词、`ZCodeTaskConversation` schema 整理指令、schema 拼接 glue）与返回体校验严格配对，按用户确认保留在代码。

## 方案：指令进模板，数据走变量

沿用 `DefaultAiGatewayClient.renderPrompt` 已有的 `${var}` 渲染与变量白名单机制，把每个场景的指令文本移入 DB 模板，Java 只组装数据片段放入具名变量。

### 1. `AiUseCaseDefinitions` 变更（workhub-service/.../service/ai/AiUseCaseDefinitions.java）

- 5 个 intake 场景的 `allowedPromptVariables` 从 `Set.of("prompt")` 改为各自的数据变量集（渲染时缺变量报错，调用方需始终提供全部变量，空缺给空串）：
  - `intake.structured.extract`：`sourceChannel`、`manualNotes`、`attachmentList`、`attachmentSummaries`
  - `intake.sql-draft.generate`：`intakeRecord`、`rawContent`、`structuredData`、`attachmentSummaries`、`attachmentList`
  - `intake.clarification.analyze`：`projectInfo`、`requirementMarkdown`、`intakeInfo`、`structuredFields`、`attachmentSummaries`、`knowledgeBase`、`rawContent`
  - `intake.development.analyze`：`projectInfo`、`developers`、`systemTags`、`requirementMarkdown`、`intakeInfo`、`structuredFields`、`attachmentSummaries`、`knowledgeBase`、`rawContent`
  - `intake.development.adjust`：`developers`、`currentDraft`、`userMessage`
- 新增两个内置场景（capability 均为 `MODEL_ONLY`，领域 `AGENT`）：
  - `agent.api.step`（Agent API 步进），通道 `{API}`，变量 `agentInstructions`、`taskSpecification`、`toolCatalog`、`trajectory`
  - `agent.cli.step`（Agent CLI 步进），通道 `{CLI}`，变量 `agentInstructions`、`taskSpecification`、`trajectory`

### 2. 提示词渲染抽取复用

- 从 `DefaultAiGatewayClient` 抽出 `renderPrompt` 逻辑为共享组件（如 `AiPromptTemplateRenderer`，service.ai 包），保留"未授权变量/缺变量/残留未渲染变量"三类校验；网关与 Agent 适配器共用。
- `AiGatewayRequest.structured(...)` 工厂（单 `prompt` 变量）改为/替换为具名 variables 工厂；5 个 generator 调用点同步切换。

### 3. Java 调用方改造（删硬编码指令文本，仅组数据）

- `CodexCliStructuredExtractor`、`CodexCliSqlDraftGenerator`、`CodexCliClarificationAnalysisGenerator`、`CodexCliDevelopmentAnalysisGenerator`：`buildPrompt` 重构为"构建变量 Map"，指令文本（你是…/要求/工作流/输出要求等）从代码删除；分段小节标题（如"附件清单："）随数据片段在 Java 组装，空缺时段落整体为空串。
- `AgentApiExecutionAdapter`、`AgentNativeCliAdapter`：注入 `AiUseCaseConfigService` + 渲染器；每步按 `agent.api.step` / `agent.cli.step` 加载启用的 `promptTemplate` 并渲染（`instructions`/`specification`/工具目录/轨迹仍由代码 JSON 序列化）。配置缺失或停用 → 该次运行 FAILED，错误信息明确指出场景编码（与 `requireEnabledByCode` 现有语义一致；迁移会预置启用行，升级后行为不变）。
- 安全边界不受影响：工具目录 `tools.require`、只读限制、MANUAL_RECONCILIATION 语义、completionTargets 闸门等强制逻辑仍在代码，DB 只改"说给模型听的引导文本"。

### 4. DB 迁移（workhub-bootstrap/src/main/resources/db/schema/mysql/，新增 `V20261105_HHMMSS__move_ai_prompts_into_use_case_config.sql`）

- dict 插入 `('aiUseCaseDomain','AI任务领域','AGENT','Agent执行',95)`（`ON DUPLICATE KEY UPDATE` 幂等，列结构照 V20）。
- UPDATE 5 个 intake 行：`prompt_template` 置为完整指令模板（含 `${var}` 占位，文本从现 Java 代码原样平移）；`prompt_variables_desc` 置为变量说明 JSON（V19 同格式）；`prompt_checksum=SHA2(template,256)`、`prompt_version=prompt_version+1`（与服务层保存逻辑一致）。
- 幂等 INSERT 2 个 agent 行（`WHERE NOT EXISTS`）：`provider_config_id` 绑定 V19 预置的 `codex-cli-default`（列 NOT NULL；Agent 运行实际用运行快照里的接入，绑定仅满足建表约束，remark 说明），`model/reasoning_level/speed_mode/timeout_seconds` 留空（由运行快照决定），`enabled=1`。
- 说明：旧模板若被手工改过会被本次重置——旧模板只是 `${prompt}` 透传，且变量白名单变化后旧模板必然渲染失败，统一重置是唯一自洽选项（在迭代日志注明）。

### 5. 测试与文档

- 更新受影响单测：generator 类（断言 prompt 文本的用例改为断言变量组装）、`DefaultAiGatewayClientTest`、`AiUseCaseConfigServiceTest`（新场景/变量校验）、Agent 相关测试（`AgentWorkbenchServiceTest`、`AgentInspectionServiceTest`、`AutonomyCaseServiceTest`、`FakeAgentCliProcess`）；新增渲染器单测。
- SDD 建档：`docs/业务线/BL000012-工作台/需求迭代/{REQ}-{AI提示词统一配置化}/` 下 proposal.md / design.md / tasks.md（按知识库 SDD 模板）。
- 更新 `docs/业务线/BL000012-工作台/现状.md`（AI 任务配置章节）与 `迭代日志.md`。
- 前端 workhub-web 无需改动（变量说明来自 `prompt_variables_desc` 动态展示）；连接探测提示词、`ZCodeTaskConversation:97` 整理指令、schema 拼接文本按确认保留在代码。

## 验证

1. `mvn test`（先 `workhub-service`，再全模块）——覆盖渲染校验、变量白名单、generator 变量组装、agent 适配器模板加载与失败路径。
2. 本地起 bootstrap 验证 Flyway 迁移可执行、迁移后 `ai_use_case_config` 模板/校验和/版本正确（可用现有单测基建或本地 MySQL）。
3. 抽查一条 intake 链路（如结构化提取单测级端到端）：确认最终拼出的提示词与迁移前逐字一致（指令来自模板 + 数据来自变量）。