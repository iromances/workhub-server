# Design：AI任务配置通道能力动态下发与选择交互修复

> SDD 流程 Design。以 Proposal v1 和已核实的代码事实为输入，随完整规格在实施前统一确认。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-072661e7-6dc5-46b2-bba6-e19d2b16b6b2 |
| 需求名称与唯一需求目录 | AI任务配置通道能力动态下发与选择交互修复；docs/业务线/BL000012-工作台/需求迭代/REQ-072661e7-6dc5-46b2-bba6-e19d2b16b6b2-AI任务配置通道能力动态下发与选择交互修复/ |
| 涉及仓库与功能域 | workhub-server：workhub-model（响应契约）、workhub-service（能力规则与填充）、workhub-dao（行对象映射方式）、workhub-controller（无接口签名变化，透传）；workhub-web：AiConfigView.vue 场景编辑弹窗与列表能力列 |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server codex/development-20261102、workhub-web codex/development-20261103（两仓均有进行中未提交改动，按当前工作区核对）；开发库 10.10.116.53:3308/workhub 只读查询；核对时间 2026-11-06 |

## 1. 现状与证据

- 规则权威定义：`workhub-service/.../ai/AiUseCaseDefinitions.java:40-57` 每个内置场景注册 `Definition(code, name, capability, allowedChannelTypes, allowedPromptVariables)`；`Capability` 枚举 MODEL_ONLY / IMAGE_INPUT / LOCAL_REPOSITORY_READ（:74-78）。
- 后端保存校验：`AiUseCaseConfigService.java:178` `definition.requireChannel(provider.getChannelType())`（API/CLI 级）；`:179-181` 启用状态下 `AiCliType.is(provider,"ZCODE") && capability==LOCAL_REPOSITORY_READ` 拒绝（"ZCode 代码任务请使用 Agent 工作台；旧本地研发分析场景尚未适配"）。即 CLI 级限制目前以 if 语句形式散落，未进 Definition。
- 响应契约：`workhub-model/.../AiUseCaseConfigResponse.java` 无能力字段；由 `AiUseCaseConfigMapper.findAll/findDetailById` 的 `@Select`（MANAGE_COLUMNS，LEFT JOIN ai_provider_config）直接映射构造 record。
- 前端硬编码：`AiConfigView.vue:818-820` `requiresLocalRepository`（3 个编码）；`:826` IMAGE 编码清单；使用点 `:284`（通道过滤，条件 `CLI && cliType!=='ZCODE'`）、`:750`（匹配，条件 `CLI && cliType==='CODEX'`，两处语义相同）、`:822-830`（能力文案）、`:909/:1229`（列表列与弹窗只读展示）。厂商/模型厂商下拉（`:263-277`）无能力过滤。
- 前端数据源：下拉选项来自 `loadProviders()` → `/manage/system/ai-provider/list`（动态查库，非硬编码）；场景 list/detail 走 `/manage/system/ai-use-case/list`、`/{id}`（`AiConfigCompatibilityController.java:71-78`），与 `/api/system/*`（`AiConfigController.java:120-150`）共用同一 service。
- 开发库事实（只读查询 2026-11-06）：`ai_provider_config` 两条有效通道——id=1 直连+OPENAI+CLI/CODEX（启用）、id=3 XIANXINGTONG+ZHIPU+CLI/ZCODE（启用）；`ai_use_case_config` 9 行均绑定 provider 1，其中含未注册编码 `intake.development.sdd-analyze`、`intake.submit-test.review`。
- 尚未核实的假设：无（映射方式已核实到 mapper 源码级；MyBatis 对 record 构造器的自动映射参数名机制以现有代码可运行为准，本设计不依赖未验证行为，见 §3）。

## 2. 系统架构

- 总体方案：能力规则收敛到 `AiUseCaseDefinitions`（增加 CLI 类型级约束），接口把能力随场景数据下发，前端删除本地规则副本、三级下拉全部按"能力 × 通道库存量"过滤，并把未匹配原因分场景能力说明。规则本身（谁允许什么通道）语义不变。
- 主要流程：管理员打开场景编辑弹窗 → 前端以该场景的 `capability/allowedChannelTypes/allowedCliTypes` 为过滤依据渲染三级下拉（选项仍来自通道列表接口）→ 级联选择解析出 `providerConfigId` → 保存时后端按同一 Definition 校验兜底。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-model `AiUseCaseConfigResponse` | 承载新增 3 个能力字段 | 规则计算 | 输入：服务层填充值；输出：JSON 响应 | 无 |
| workhub-service `AiUseCaseDefinitions` | 唯一权威：capability、allowedChannelTypes、**新增 allowedCliTypes** 及统一校验方法 | 持久化 | 输入：场景编码、通道（channelType+cliType）；输出：Definition、通过/拒绝 | 无 |
| workhub-service `AiUseCaseConfigService` | list/detail 响应能力字段填充；保存校验改走 Definition 统一方法 | 前端交互 | 输入：mapper 行对象；输出：完整响应 | AiUseCaseDefinitions |
| workhub-dao `AiUseCaseConfigMapper` | 行查询（SQL 不变） | 能力字段 | 输出改为行记录对象（见 §3） | 无 |
| workhub-web `AiConfigView.vue` | 三级下拉能力过滤、原因可解释提示、能力文案 | 规则判定（以后端下发为准） | 输入：场景能力字段 + 通道列表；输出：providerConfigId | 两接口 |

## 3. 契约与数据模型

- API/事件提供方与消费者：提供方 workhub-server；消费者 workhub-web 系统管理页。无事件。
- 变更契约（唯一权威文件：`AiUseCaseConfigResponse.java`；前端镜像 `src/types/ai-config.ts`）：
  - `GET /api/system/ai-use-cases`、`/api/system/ai-use-cases/{id}`、`/manage/system/ai-use-case/list`、`/manage/system/ai-use-case/{id}` 响应每行新增：
    - `capability: string | null` —— `MODEL_ONLY` / `IMAGE_INPUT` / `LOCAL_REPOSITORY_READ`；场景编码未注册于 Definitions 时为 `null`。
    - `allowedChannelTypes: string[] | null` —— 如 `["CLI"]`、`["API","CLI"]`；未注册为 `null`。
    - `allowedCliTypes: string[] | null` —— CLI 被允许时的 `["CODEX"]` 或 `["CODEX","ZCODE"]`；仅 API 时为空数组；未注册为 `null`。
  - 既有字段、路径、权限、分页结构不变；纯新增字段，旧消费者不受影响。
- 字段填充实现约束（确定性，不依赖 MyBatis 缺列隐式行为）：mapper 的 `findAll`/`findDetailById` 返回类型改为等字段行记录（如新增 `AiUseCaseConfigRow`，字段=现响应去掉新增 3 项），SQL 与 MANAGE_COLUMNS 不变；service 层 `Row → Response` 映射时从 `AiUseCaseDefinitions.find(code)`（新增可空查找，未注册返回 null）填充 3 字段。`list()`、`detail()` 两条路径统一走该映射。
- 保存校验收敛：`Definition` 新增 `allowedCliTypes` 字段与统一校验（如 `requireProvider(channelType, cliType)`）：channelType 归一化后须 ∈ allowedChannelTypes；为 CLI 时 cliType 须 ∈ allowedCliTypes。`AiUseCaseConfigService.toEntity` 的 `:178-181` 两段判断替换为该调用，拒绝语义与错误信息含义保留（错误信息可合并表述为"AI 场景 X 不允许使用 Y 通道"式，保留 ZCode+本地仓库场景的原提示语原文，避免管理员提示变化）。Definitions 各场景取值：LOCAL_REPOSITORY_READ → `["CODEX"]`；其余允许 CLI 的 → `["CODEX","ZCODE"]`；仅 API 的 → 空集。
- 前端行为契约：`AiConfigView.vue` 以编辑中场景的能力字段为唯一过滤依据——`allowedChannelTypes`/`allowedCliTypes` 为 null（未注册编码）时不做能力过滤（保持现状）；通道下拉 = 通过能力过滤的通道（含停用项置灰，规则不变）；模型厂商/接入供应商下拉仅保留"存在至少一条通过能力过滤通道"的组合；`resolveUseCaseProviderConfigId` 使用与通道下拉同一谓词；未匹配提示区分两类原因（组合无通道 / 场景能力限制并注明允许的通道类型）；`useCaseCapabilityText` 改为 capability 映射（LOCAL_REPOSITORY_READ→本地仓库只读、IMAGE_INPUT→文本 / 显式图片、MODEL_ONLY/null→受控模型调用）。
- 幂等、并发、重试：无新增写路径，不涉及。
- 数据归属与迁移：无表结构变更、无数据迁移、无回滚脚本需求。
- 关键业务数据口径：能力取值以 `AiUseCaseDefinitions` 编译期常量为权威，不落库。

## 4. 非功能约束

- 安全与权限：接口权限不变；新增字段为非敏感元数据；不记录敏感配置。
- 性能、容量与时效：能力填充为内存查表，list 接口无额外 IO。
- 失败处理、降级与补偿：无外部依赖变化；未注册编码行返回 null 能力字段，前端兜底为"不限制"，不抛错。
- 日志、监控、告警与审计：不变；保存校验拒绝沿用既有异常与审计路径。

## 5. 交付与回滚

- 影响系统和发布顺序：后端先发（纯新增字段，旧前端无感）；前端后发（依赖新字段，未发前旧逻辑仍工作）。无数据库发布。
- 灰度或开关：不需要。
- 数据备份与前后校验：无数据变更；以接口返回比对 `AiUseCaseDefinitions` 做前后校验（AC-01）。
- 回滚条件、步骤与不可逆部分：回滚=两端代码回退即可，无不可逆部分；后端单独回滚后前端新逻辑因能力字段缺失自动退化为"不限制"（行为等同现状），需在回滚时一并回退前端。
- 生效并核实后需更新的文档：`docs/业务线/BL000012-工作台/现状.md`（AI 任务配置能力下发事实）、`迭代日志.md`；`docs/mcp-server.md` 不涉及。

## 6. 一致性与待确认

- Proposal 目标承接：目标1→§2/§3（Definitions 权威+下发）；目标2→§3 前端行为契约；目标3→§3 提示语区分。
- 非范围保持：能力规则语义不变（AC-05 校验行为不变）；未注册存量行不动（null 兜底）；生产库迁移问题不并入本需求。
- 待确认问题及影响：
  1. 需求名称"AI任务配置通道能力动态下发与选择交互修复"是否认可（影响目录名，编码不变）。
  2. 生产库未执行 V20261104 迁移（缺 cli_type 列）是否另行安排独立任务跟进（与本需求发布顺序的关系：本需求后端上线不依赖该迁移，但当前分支整体上线依赖）。
  3. ZCode+LOCAL_REPOSITORY_READ 的拒绝提示语是否保留原文（默认保留，见 §3）。
- 关联：Proposal v1、Tasks（同目录）；无 ADR、无独立契约文件（契约即 Response record + 前端类型镜像）。
