# Design：数字员工职责 AI 起草

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-3e8d95c2-851d-4756-8c3d-0de3603dcc67 |
| 需求名称与唯一需求目录 | 数字员工职责 AI 起草；docs/业务线/BL000012-工作台/需求迭代/REQ-3e8d95c2-851d-4756-8c3d-0de3603dcc67-数字员工职责AI起草/ |
| 涉及仓库与功能域 | workhub-server：AiUseCaseDefinitions、AgentInstructionsDraftService、AgentController、db 迁移；workhub-web：AgentConfigDialog、api/agent.ts、types/agent.ts |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | 草案 v1 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server codex/development-20261102；workhub-web codex/development-20261103；2026-11-16 |

## 1. 现状与证据

- 网关已支持按请求覆盖 Provider/模型：`AiGatewayRequest` 含 `providerConfigIdOverride`/`modelOverride` 等字段，`DefaultAiGatewayClient.executeInternal`（:331-337）先取覆盖值，场景模板仍在网关侧用 `promptTemplateRenderer.render` 渲染——本需求无需改网关。
- 场景注册：`AiUseCaseDefinitions`（MODEL_ONLY、通道白名单、变量白名单）；模板种子进 `ai_use_case_config`，测试经 `TestPromptTemplates`（迁移文件清单需登记新文件）。
- 端点模式：`AgentController`（@PreAuthorize agent:view/manage）；一次性生成参照 `IntakeSubmitTestReviewService`（executeStructured/execute + result.succeeded 判定）。
- 前端：`AgentConfigDialog.vue` 职责为必输 textarea（maxlength 12000）；options.providers 含 id/name/channelType/enabled/defaultModel。
- 尚未核实的假设：无（均为代码直接证据）。

## 2. 系统架构

- 总体方案：无状态对话式起草——前端保存会话消息，每轮把完整对话发给后端；后端渲染场景模板（变量：agentName、scopeSummary、conversation）经网关 execute（纯文本输出），覆盖接入/模型取自请求，返回完整版职责草稿。
- 交互流：职责旁"AI 起草"按钮 → 面板（接入/模型选择 + 对话区）→ 首条描述出初稿 → 调整意见逐轮修订 → "采用此稿"回填（已有内容先确认覆盖）。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AiUseCaseDefinitions | 注册 agent.instructions.draft 与变量白名单 | 模板内容 | 常量 | — |
| AgentInstructionsDraftService | 参数校验、conversation/scopeSummary 组装、网关调用、输出裁剪 | 会话状态、持久化 | 请求→响应 | AiGatewayClient |
| AgentController | 端点与权限（agent:manage） | 业务规则 | REST | service |
| 迁移 | 模板种子（场景配置页可维护） | — | SQL | Flyway |
| AgentConfigDialog | 起草面板、会话内存、采用回填 | 后端校验 | Vue 组件 | api/agent.ts |

## 3. 契约与数据模型

- `POST /api/agents/instructions-draft`（agent:manage）：
  - 请求 `AgentInstructionsDraftRequest`：`providerConfigId`（@NotNull @Positive）、`model`（≤128，可空回退场景/接入默认）、`name`（≤128，可空）、`scopeSummary`（≤2000，前端由已选业务范围标签拼接）、`messages`（@NotEmpty ≤20，元素 role ∈ {user, assistant}、content @NotBlank ≤8000）。
  - 响应 `AgentInstructionsDraftResponse`：`instructions`（服务端 trim，超 12000 截断）、`model`（实际使用模型）。
  - 错误：校验失败 400；场景未配置/停用或网关失败抛 IllegalStateException/IllegalArgumentException 文案（沿用全局异常处理）。
- 场景 `agent.instructions.draft`：MODEL_ONLY；通道 API+CLI（网关按接入实际通道路由）；变量 `agentName、scopeSummary、conversation`。模板要点：职责公式（身份+常态职责边界+输出习惯+升级规则）、不写代码坐标与单次任务目标、按最新意见修订并输出完整新版、只输出职责文本。
- 幂等/并发：无状态请求，无落库，无幂等要求。
- 数据：仅新增 `ai_use_case_config` 种子行；无业务表变更。

## 4. 非功能约束

- 安全与权限：端点 agent:manage；API Key 仍由网关统一解析，请求不携带凭据；对话内容不入库，网关日志沿用现网脱敏策略。
- 性能：单轮同步调用，超时沿用场景/接入配置；消息与输出长度上限防滥用。
- 日志：网关既有执行日志（useCase/provider/model/时长）足够审计定位。

## 5. 交付与回滚

- 发布顺序：后端（含迁移）先行，前端随后；仅前端先发会 404。
- 回滚：还原两端提交；迁移为幂等 INSERT（WHERE NOT EXISTS），回滚不强制清行（场景停用即可）。
- 生效后更新：BL000012 迭代日志；现状.md 数字员工章节补一句职责起草入口。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1/3 → 前端面板（§2）；目标 2 → 请求覆盖字段（§3）。
- 非范围保持：不落库、不改保存校验、不动既有场景。
- 待确认问题：无（默认值：面板预选表单已选接入/模型；覆盖回填前确认）。
