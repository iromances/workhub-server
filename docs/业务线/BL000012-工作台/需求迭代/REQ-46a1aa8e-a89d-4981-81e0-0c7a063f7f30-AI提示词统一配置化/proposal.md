# Proposal：AI提示词统一配置化

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-46a1aa8e-a89d-4981-81e0-0c7a063f7f30 |
| 需求名称 | AI提示词统一配置化（系统提示词统一放入 AI 任务配置，不在代码写死） |
| 涉及仓库与功能域 | workhub-server：workhub-service（service/ai、service/intake、service/agent）、workhub-bootstrap（Flyway 迁移）；单测同步调整 |
| businessLineCode | BL000012 |
| businessLineName | 工作台 |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-46a1aa8e-a89d-4981-81e0-0c7a063f7f30-AI提示词统一配置化/ |
| 版本/状态 | v1.0，规格已确认（用户 2026-11-05 批准实施计划） |
| 目标系统 | WorkHub 后端（workhub-web 前端无需改动，变量说明来自 prompt_variables_desc 动态展示） |
| 原始需求来源及版本 | 用户口头需求（2026-11-05）："workhub系统里系统的提示词统一都放到AI任务配置里配置，不要在代码里写死" |
| 目标环境/分支 | codex/development-20261102 |
| 负责人/评审人 | sh |

## 1. Why：背景与问题

- 当前问题及证据：
  1. 5 个需求整理 AI 任务（intake.structured.extract、intake.sql-draft.generate、intake.clarification.analyze、intake.development.analyze、intake.development.adjust）虽有 AI 任务配置行，但 `prompt_template` 仅是 `${prompt}` 占位符，真正的指令文本硬编码在 `CodexCliStructuredExtractor`、`CodexCliSqlDraftGenerator`、`CodexCliClarificationAnalysisGenerator`、`CodexCliDevelopmentAnalysisGenerator` 的 `buildPrompt`/`buildAdjustmentPrompt` 中，改提示词必须发版。
  2. Agent 执行适配器 `AgentApiExecutionAdapter.prompt()` 与 `AgentNativeCliAdapter.prompt()` 的系统提示词（安全护栏 + 行动协议，各约 20 行）完全硬编码，且没有 AI 任务配置行。
- 受影响用户/角色：AI 任务配置管理员（无法在页面调整提示词）、平台运维（发版才能调提示词）。
- 不解决的后果：提示词调优周期被发版节奏绑定；AI 任务配置页面形同虚设（模板只是透传占位）。

## 2. What：目标与范围

### 本期目标

1. 系统 AI 提示词统一迁入 AI 任务配置（`ai_use_case_config.prompt_template`），代码只组装数据变量并渲染模板。
2. Agent API / CLI 两个执行适配器的步进系统提示词纳入 AI 任务配置管理（新增场景编码）。

### 本期交付范围

1. `AiUseCaseDefinitions` 变量白名单按场景细化；新增 `agent.api.step`、`agent.cli.step` 内置场景（领域 AGENT）。
2. 提示词模板渲染逻辑抽取为共享渲染器，网关与 Agent 适配器共用。
3. 4 个 intake generator 与 2 个 Agent 适配器删除硬编码指令文本，改为变量组装 + 模板渲染。
4. Flyway 迁移：更新 5 个 intake 行模板/变量说明/校验和/版本；幂等预置 2 个 agent 行；字典补 AGENT 领域。
5. 受影响单测更新与渲染器单测新增。

### 明确不做

1. 连接探测提示词（`DefaultAiGatewayClient` 探测/连通测试，`system.provider.smoke`）保留在代码——与 `requireExpectedProbeOutput` 返回体校验严格配对。
2. `ZCodeTaskConversation` 的 schema 整理固定指令、`DefaultAiGatewayClient`/`ZCodeAgentCliProtocol` 的 schema 拼接 glue 保留在代码。
3. Agent 工具目录、MANUAL_RECONCILIATION 语义、completionTargets 闸门等代码侧强制逻辑不变；本需求只迁移"说给模型听的引导文本"。
4. workhub-web 前端不改版。
5. 不引入代码内置兜底模板——配置缺失/停用时任务按现有语义失败（AI 场景配置不存在/已停用）。

### 约束与依赖

- 依赖的代码和文档：`ai_use_case_config` 表结构（V13/V20）；`AiUseCaseDefinitions` 安全能力边界（数据库不可提升能力，仅提示词文本可配置）。
- 业务/合规约束：模板渲染保留三类校验（未授权变量、缺变量、残留未渲染变量），变量白名单在代码中固定。
- 既有系统/接口/数据约束：`provider_config_id` NOT NULL，agent 预置行绑定 `codex-cli-default`（实际运行用运行快照接入，绑定仅满足建表约束）；旧 `${prompt}` 模板在新变量白名单下必然渲染失败，迁移统一重置为指令模板（迭代日志注明）。
- 外部依赖与前置条件：无。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 代码中不再存在 5 个 intake 场景与 2 个 Agent 适配器的硬编码指令文本；`grep "你是"` 在 src/main 无业务提示词命中 | 代码审查 + grep | sh |
| AC-02 | 迁移后各场景最终拼装的完整提示词与迁移前逐字一致（指令来自模板 + 数据来自变量） | 单测对比渲染结果 | sh |
| AC-03 | AI 任务配置页面修改模板/变量说明后，下一次任务执行即使用新模板，无需发版 | 页面修改 + 任务执行观察（上线后人工） | sh |
| AC-04 | 模板含未授权变量、缺变量或残留占位时，任务失败且错误信息指明场景与变量 | 渲染器单测 | sh |
| AC-05 | Agent 运行在 `agent.api.step`/`agent.cli.step` 配置缺失或停用时 FAILED，错误信息含场景编码 | 适配器单测 | sh |
| AC-06 | `mvn test` 全模块通过；Flyway 迁移可重复执行（幂等） | 构建与迁移验证 | sh |
