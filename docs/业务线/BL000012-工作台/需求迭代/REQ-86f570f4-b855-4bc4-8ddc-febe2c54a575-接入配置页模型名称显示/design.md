# Design：接入配置页模型名称显示

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 接入配置页模型名称显示（REQ-86f570f4-b855-4bc4-8ddc-febe2c54a575） |
| 涉及仓库与功能域 | 仅 `workhub-web`：`src/views/system/AiConfigView.vue` 展示层；后端仓库无改动 |
| 所属项目或业务线（适用时） | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-web 分支 `codex/development-20261122`（有未提交改动），2026-11-24 核对 |

## 1. 现状与证据

- 当前流程、模块和代码位置（workhub-web `src/views/system/AiConfigView.vue`）：
  - 接入配置表"CLI通道"列默认行直接渲染 `row.cli?.defaultModel` 原始标识（1150 行）；"API通道"列渲染 `row.api?.defaultModel` 字典编码（1139 行）。
  - 使用场景表模型列 `modelText(row.effectiveModel)`（1069 行）走字典 `optionText`，CLI 的 `providerId/modelId` 不在字典中，原样回退。
  - 编辑弹窗已有目录加载链路：`fetchCliCatalog` → `fetchZCodeModels`/`fetchCodexModels`，下拉展示 `displayName`（1292-1297 行）。
  - `providerGroups` 行携带完整 `AiProviderConfig`（含 `cliType`、`cliCommand`、三个 ZCode 路径字段、工作目录），足以构造目录请求（254-284 行）。
  - 页面挂载即 `loadProviders()`（997 行），使用场景行含 `providerConfigId`/`channelType`，可与 `providerList` 关联。
- 当前接口、数据及配置：
  - `POST /api/manage/system/ai-provider/zcode-models` 与 Codex 同类接口返回 `{models: [{model, displayName, supportedReasoningEfforts…}], fetchedAt, cached}`；服务端 60 秒 TTL 缓存 + 并发去重（workhub-server `CodexModelCatalogService`）。
  - 权限：页面列表仅需 `system:ai-config:view`；目录接口要求 `create/update/manage`（`ZCodeModelCatalogController`）。
  - 前端 `createCodexCatalogLoader` 无跨调用缓存，每次 `load()` 重新请求（后端 TTL 兜底）。
- 事实来源：上述文件行号基于 2026-11-24 工作区代码核对；截图证据见 Proposal 原始需求来源。
- 尚未核实的假设：无（展示层改动，无数据契约假设）。

## 2. 系统架构

- 总体方案：纯前端展示层增强。页面在 `providerList` 加载后，对列表中每个 CLI 通道配置按"目录请求签名"去重发起一次目录请求（复用 `fetchCliCatalog`），把返回的 `model → displayName` 合入响应式名称表；三处展示点查表，查不到回退原始标识。失败（含 403）静默丢弃，保持原始标识。
- 数据流：`providerList` → 去重 CLI 配置 → 会话级 `Map<签名, Promise<目录>>` → 响应式 `cliModelNames` → 模板渲染（接入配置列、使用场景列）。
- 前端页面、交互与后端规则边界：名称解析属轻量展示格式化，仅前端负责；后端接口、权限、缓存语义零改动。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AiConfigView.vue 名称映射 | CLI 默认模型的名称解析、展示与回退 | 名称持久化、目录刷新交互、权限申请 | 输入 `providerList`/使用场景行；输出 displayName 或原始标识 | 既有目录接口与字典 |
| 目录接口（既有） | 模型目录查询与 60s 缓存 | 为本需求做任何调整 | CLI 配置 → 模型列表 | 无新增 |

## 3. 契约与数据模型

- API/事件提供方与消费者：复用既有目录接口，无新增、修改或废弃契约。
- 请求、响应、字段语义：不变；前端仅消费 `models[].model` 与 `displayName`。
- 幂等、并发：同一签名会话内仅发起一次请求；跨会话/用户由后端 TTL 与 in-flight 去重兜底。
- 数据归属：不涉及存储变更，`defaultModel` 仍保存原始标识（ZCode 校验格式不变）。

## 4. 非功能约束

- 安全与权限：目录请求仅在有 `create/update/manage` 权限的用户会话成功；`view` 用户 403 回退原始标识，不弹错。
- 性能、容量：每会话每 CLI 配置最多一次请求；冷启动拉起一次 CLI 进程，与打开一次编辑弹窗同量级；请求异步，列表先渲染原始标识，名称到达后响应式替换。
- 失败处理：目录失败/超时不重试、不提示，原始标识即最终展示（编辑弹窗内仍有显式刷新入口）。
- 日志、监控：不新增。

## 5. 交付与回滚

- 影响系统：仅 workhub-web 单文件；无前后端发布顺序问题。
- 灰度或开关：不需要。
- 数据备份与回滚：还原该文件即回滚，无数据影响。
- 生效并核实后需更新的文档：本需求 Tasks 证据、工作台《迭代日志.md》；《现状.md》如提及该页展示再同步。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1、2 由名称映射与三处模板替换承接；目标 3 由静默回退承接。
- 非范围保持：不改后端、不持久化、不改弹窗行为。
- 待确认问题：workhub-web `codex/development-20261122` 分支存在较多未提交改动，是否直接在该分支实施。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
