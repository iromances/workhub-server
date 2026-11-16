# Proposal：AI任务配置通道能力动态下发与选择交互修复

> SDD 流程 Proposal。作为 Design 的需求边界；随 Design、Tasks 连续起草，完整规格在实施前统一确认。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-072661e7-6dc5-46b2-bba6-e19d2b16b6b2（首建生成，后续沿用） |
| 需求名称 | AI任务配置通道能力动态下发与选择交互修复（独立任务，名称待用户确认） |
| 涉及仓库与功能域 | workhub-server（workhub-model/workhub-service/workhub-dao/workhub-controller，AI 配置域）；workhub-web（src/views/system/AiConfigView.vue、src/types/ai-config.ts、src/api/ai-config.ts） |
| 所属项目或业务线 | BL000012-工作台（WorkHub 平台自身研发任务） |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-072661e7-6dc5-46b2-bba6-e19d2b16b6b2-AI任务配置通道能力动态下发与选择交互修复/（相对 workhub-server 仓库根目录） |
| 版本/状态 | 草案 v1，待完整规格统一确认 |
| 目标系统 | WorkHub 后端（AI 配置管理接口）、WorkHub 前端（系统管理 → AI 接入配置页） |
| 原始需求来源及版本 | 用户在 AI 任务配置编辑弹窗中实测反馈（2026-11-06 会话）：切换接入供应商后通道下拉为空，提示语未说明原因 |
| 目标环境/分支 | 后端 codex/development-20261102（含未提交的进行中 AI 改动，实施基于当前工作区不回退）；前端 codex/development-20261103；验证用开发库 10.10.116.53:3308/workhub |
| 负责人/评审人 | 待确认 |

## 1. Why：背景与问题

- 当前问题及证据：
  1. 后端 `AiUseCaseDefinitions` 已为每个内置场景定义权威能力（`Capability`、`allowedChannelTypes`），保存时校验（`AiUseCaseConfigService.java:178-181`：`requireChannel` + ZCODE 不允许绑定 `LOCAL_REPOSITORY_READ` 场景）；但场景 list/detail 接口（`AiUseCaseConfigResponse`）不下发能力字段。
  2. 前端 `AiConfigView.vue:818` 硬编码三个场景编码复制该规则（`requiresLocalRepository`），在 3 处使用（:284 通道过滤、:750 匹配、:822 能力文案）；`useCaseCapabilityText` 另硬编码两个 IMAGE 场景编码（:826）。
  3. 交互缺陷：接入供应商/模型厂商下拉未应用该过滤，用户可选中"组合下无任何可用通道"的死胡同组合（实测：本地仓库类场景选先行通+智谱，通道下拉为空），提示语"请先在接入配置中维护对应组合和通道"未说明真实原因是场景能力限制。
- 受影响用户/角色：维护 AI 任务配置的平台管理员（system:ai-config:* 权限）。
- 不解决的后果：新增同类场景必须同步改前端代码；管理员继续在死胡同组合上困惑；前后端规则双份维护存在漂移风险。

## 2. What：目标与范围

### 本期目标

1. 场景能力规则单点维护：后端 `AiUseCaseDefinitions` 为唯一权威，接口向前端下发场景能力（capability、允许的通道类型、允许的 CLI 类型），前端删除硬编码编码清单。
2. 前端下拉按能力过滤：通道、模型厂商、接入供应商三级下拉均按"该场景能力下存在可用通道"过滤，不再出现死胡同组合。
3. 提示语可解释：未匹配到通道时，提示区分"该组合下无通道"与"该场景能力仅允许特定通道类型"。

### 本期交付范围

1. 后端：`AiUseCaseConfigResponse` 增加 `capability`、`allowedChannelTypes`、`allowedCliTypes` 字段并在服务层填充（源为 `AiUseCaseDefinitions`，无数据库变更）；保存校验改为由 Definition 统一承载（含现 ZCODE+LOCAL_REPOSITORY_READ 拒绝语义）。
2. 前端：`AiConfigView.vue` 场景编辑弹窗三级下拉联动与匹配逻辑改用接口下发的能力字段；`useCaseCapabilityText` 改由 capability 字段映射；提示语分场景能力说明原因；`types/ai-config.ts` 补充类型。
3. 后端单测与前端验证（构建/类型检查 + 手工验证步骤记录于 Tasks）。

### 明确不做

1. 不改能力规则本身：`LOCAL_REPOSITORY_READ` 场景仍仅允许 Codex CLI（ZCode 独立工作目录读不了仓库，语义不变）。
2. 不处理存量未注册编码行（开发库 `intake.development.sdd-analyze`、`intake.submit-test.review` 未在 `AiUseCaseDefinitions` 注册，保存本就会被 `require` 拒绝）：响应中其能力字段为空，前端按"无附加限制"展示，与现状一致。
3. 不在本需求内处理生产库未执行 `V20261104` 迁移的问题（另行跟进，见 Design 待确认）。
4. 不改接入配置（provider）管理页的通道维护功能。

### 约束与依赖

- 依赖的代码和文档：`AiUseCaseDefinitions.java`、`AiUseCaseConfigService.java`、`AiUseCaseConfigMapper.java`、`AiConfigController.java`/`AiConfigCompatibilityController.java`、前端 `AiConfigView.vue`/`ai-config.ts`；[MCP 能力总览](../../../mcp-server.md) 不受影响。
- 业务/合规约束：AI 配置接口权限不变（system:ai-config:view/manage）。
- 既有系统/接口/数据约束：场景 list/detail 的既有字段与语义不变，仅新增字段（向后兼容）；无表结构变更。
- 外部依赖与前置条件：两个仓库当前分支的进行中未提交改动保持不回退；验证环境为本地后端 + 开发库。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | `GET /api/system/ai-use-cases`（及 `/manage/system/ai-use-case/list`、`/{id}`）每条已注册场景返回 `capability`、`allowedChannelTypes`、`allowedCliTypes`，值与 `AiUseCaseDefinitions` 一致（如需求澄清分析 = LOCAL_REPOSITORY_READ / ["CLI"] / ["CODEX"]） | 接口调用比对 + 后端单测 | 待确认 |
| AC-02 | 编辑"需求澄清分析"等本地仓库场景时，接入供应商/模型厂商下拉不再出现"组合下无任何可用通道"的选项；先行通+智谱（仅 ZCode CLI）组合不再可选或选后通道下拉直接呈现真实原因提示 | 前端手工验证（开发环境） | 待确认 |
| AC-03 | 前端代码中不再存在硬编码场景编码清单（`requiresLocalRepository` 及 :826 的 IMAGE 编码清单删除），过滤与文案均由接口字段驱动 | 代码检索 grep 无残留 | 待确认 |
| AC-04 | 未匹配到通道时提示语能区分：组合下确无通道 vs 场景能力限制（含允许的通道类型说明） | 前端手工验证 | 待确认 |
| AC-05 | 后端保存校验行为不变：ZCode 绑定 LOCAL_REPOSITORY_READ 启用场景仍被拒绝（错误信息语义保留）；既有单测全绿 | 后端单测（含新增用例） | 待确认 |
| AC-06 | 存量未注册编码行（如 sdd-analyze）页面展示与现状一致（能力显示兜底文案、不因此报错） | 前端手工验证 + 接口返回空能力字段 | 待确认 |
