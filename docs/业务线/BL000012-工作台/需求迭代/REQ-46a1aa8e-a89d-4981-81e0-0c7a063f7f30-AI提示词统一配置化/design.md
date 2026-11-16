# Design：AI提示词统一配置化

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-46a1aa8e-a89d-4981-81e0-0c7a063f7f30 |
| 需求名称与唯一需求目录 | AI提示词统一配置化；docs/业务线/BL000012-工作台/需求迭代/REQ-46a1aa8e-a89d-4981-81e0-0c7a063f7f30-AI提示词统一配置化/ |
| 涉及仓库与功能域 | workhub-server：workhub-service（ai/intake/agent）、workhub-bootstrap（db/schema/mysql）；沿用 Proposal |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1.0 |
| 状态 | v1.0，规格已确认（用户 2026-11-05 批准实施计划） |
| 当前事实核对环境、分支、时间 | 本地工作区 codex/development-20261102，2026-11-05 只读核对（文件行号见各节） |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 统一 AI 网关 `DefaultAiGatewayClient.executeInternal` 渲染 `useCase.getPromptTemplate()`（workhub-service/src/main/java/cn/aslight/workhub/service/ai/DefaultAiGatewayClient.java:281，`renderPrompt` 396-418 行，含 `${var}` 白名单校验）。
  - 5 个 intake 场景 DB 模板为 `${prompt}`（V19__initialize_ai_gateway_use_cases.sql:73），指令文本硬编码于：
    - CodexCliStructuredExtractor.buildPrompt（174-224 行）
    - CodexCliSqlDraftGenerator.buildPrompt（155-190 行）
    - CodexCliClarificationAnalysisGenerator.buildPrompt（104-156 行）
    - CodexCliDevelopmentAnalysisGenerator.buildPrompt（182-250 行）/ buildAdjustmentPrompt（296-319 行）
  - Agent 适配器硬编码提示词：AgentApiExecutionAdapter.prompt（144-174 行）、AgentNativeCliAdapter.prompt（160-185 行）；`executeAgentApi` 以固定标签 `agent.task.step` 直调 API（DefaultAiGatewayClient.java:118），不走模板。
- 当前接口、数据及配置：`ai_use_case_config.prompt_template/prompt_variables_desc/prompt_checksum/prompt_version`（V13/V20/V19）；`provider_config_id` NOT NULL。
- 事实来源：上述文件当前工作区内容（只读核对）。
- 目标系统当前实现证据及本次重新核对结果：全仓 `grep "你是"`（src/main）仅命中上述 6 处业务提示词；探测/整理辅助文本位置见 Proposal 明确不做。
- 尚未核实的假设：无（模板文本平移自当前代码，一致性由 AC-02 单测保证）。

## 2. 系统架构

- 总体方案：**指令进模板，数据走变量**。`prompt_template` 承载全部指令文本与 `${var}` 数据占位；Java 调用方组装数据片段 Map；共享渲染器校验并替换。
- 主要流程：调用方 → `AiPromptTemplateRenderer.render(template, variables, definition)`（白名单校验）→ 网关/适配器发出请求。Agent 适配器每步经 `AiUseCaseConfigService.requireEnabledByCode` 读模板后渲染（与网关同为每次执行现读，管理员改模板即时生效）。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AiUseCaseDefinitions | 场景注册、能力边界、变量白名单 | 模板文本 | 场景编码 → Definition | — |
| AiPromptTemplateRenderer（新） | 模板渲染与三类校验 | 模板存储、数据组装 | template+variables → prompt | AiUseCaseDefinitions |
| 4 个 intake generator | 数据变量组装、schema、调用网关 | 指令文本 | variables Map → AiGatewayRequest | 网关、渲染器 |
| 2 个 Agent 适配器 | 轨迹/任务/目录 JSON 序列化、模板加载渲染 | 指令文本 | 渲染后 prompt → API/CLI | AiUseCaseConfigService、渲染器 |
| Flyway 迁移 | 模板/变量说明/校验和/版本落库、agent 行与 AGENT 字典预置 | 运行时逻辑 | SQL | ai_use_case_config、dict |

## 3. 契约与数据模型

- API/事件：无对外契约变化；`AiGatewayRequest` 内部工厂由单 `prompt` 变量改为具名 variables。
- 变量白名单（`AiUseCaseDefinitions.allowedPromptVariables`，代码固定）：
  - intake.structured.extract：`sourceChannel, manualNotes, attachmentList, attachmentSummaries`
  - intake.sql-draft.generate：`intakeRecord, rawContent, structuredData, attachmentSummaries, attachmentList`
  - intake.clarification.analyze：`projectInfo, requirementMarkdown, intakeInfo, structuredFields, attachmentSummaries, knowledgeBase, rawContent`
  - intake.development.analyze：`projectInfo, developers, systemTags, requirementMarkdown, intakeInfo, structuredFields, attachmentSummaries, knowledgeBase, rawContent`
  - intake.development.adjust：`developers, currentDraft, userMessage`
  - agent.api.step（新，MODEL_ONLY，通道 API）：`agentInstructions, taskSpecification, toolCatalog, trajectory`
  - agent.cli.step（新，MODEL_ONLY，通道 CLI）：`agentInstructions, taskSpecification, trajectory`
- 渲染语义沿用现有三类校验：模板含白名单外变量、变量缺值、渲染后残留 `${...}` 均报错；调用方对空缺数据段给空串。
- 数据库变更（脚本落点 workhub-bootstrap/src/main/resources/db/schema/mysql/V20261105_193000__move_ai_prompts_into_use_case_config.sql，Flyway 启动执行）：
  1. dict 幂等插入 `('aiUseCaseDomain','AI任务领域','AGENT','Agent执行',95)`（ON DUPLICATE KEY UPDATE，列结构照 V20）。
  2. UPDATE 5 个 intake 行：`prompt_template` 置为完整指令模板（文本自现 Java 代码原样平移，占位符 `${var}`）；`prompt_variables_desc` 置变量说明 JSON（V19 格式 `[{"name":...,"description":...}]`）；`prompt_checksum=SHA2(模板,256)`、`prompt_version=prompt_version+1`。
  3. 幂等 INSERT `agent.api.step` / `agent.cli.step`（WHERE NOT EXISTS）：绑定 `codex-cli-default`（NOT NULL 约束；运行实际用快照接入），`model/reasoning_level/speed_mode/timeout_seconds` 置空，`enabled=1`，`json_schema_enabled=0`。
- 幂等与历史兼容：迁移可重复执行（WHERE NOT EXISTS / ON DUPLICATE KEY）；旧手工改动模板被重置（旧模板仅 `${prompt}` 透传且新白名单下必然渲染失败，重置为唯一自洽选项，迭代日志注明）。

## 4. 非功能约束

- 安全与权限：变量白名单与能力边界仍在代码（`AiUseCaseDefinitions`"不可由数据库提升的安全能力边界"）；DB 仅改引导文本。工具授权（`tools.require`、只读限制）、completionTargets 闸门、MANUAL_RECONCILIATION 语义保持代码强制。配置管理沿用既有 `system:ai-config:*` 权限与审计。
- 性能：Agent 多步运行每步现读配置一次（与网关行为一致），无新增缓存。
- 失败处理：模板未配置/停用/校验失败 → intake 走网关现有失败路径；Agent 运行 FAILED 且错误信息含场景编码（AC-05）。
- 日志与审计：沿用网关执行日志与 AI 任务配置操作审计（promptVersion 记录于审计快照）。

## 5. 交付与回滚

- 发布顺序：仅后端；Flyway 随 bootstrap 启动执行，无前后端顺序约束（前端动态读变量说明）。
- 灰度/开关：无新开关；AI 任务配置停用某场景即等价停用对应 AI 能力（现有行为）。
- 数据备份与校验：迁移前依赖 V20 既有 `ai_use_case_config_bak_*` 模式不重复创建；本次 UPDATE 仅动 prompt_* 字段，回滚脚本可按 git 中迁移文件逆向（模板文本入库前在迁移文件内自包含）。
- 回滚条件与步骤：迁移导致启动失败时回退代码版本（Flyway 未执行该版本不落库）；已执行后回滚需手工恢复模板（迁移文件内含原 `${prompt}` 语义说明）。
- 生效后更新：现状.md（AI 任务配置章节）、迭代日志.md、本目录 tasks.md 证据。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → §3 变量白名单 + 迁移 UPDATE；目标 2 → §3 新场景 + 适配器改造（§2 表）。
- 明确不做保持：探测/整理辅助文本、代码强制逻辑、前端不在本次范围（§1/Proposal）。
- 待确认问题：无。
- 关联：Tasks 见同目录 tasks.md；无 ADR（方案单一，无备选分歧需记录）。
