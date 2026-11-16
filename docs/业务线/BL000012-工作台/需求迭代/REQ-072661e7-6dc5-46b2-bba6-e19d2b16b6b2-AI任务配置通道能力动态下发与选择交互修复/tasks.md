# Tasks：AI任务配置通道能力动态下发与选择交互修复

> SDD 流程 Tasks。任务来自 Design v1，完整规格统一确认后实施。状态不得凭 AI 自述改为"已完成"。

## 1. 元信息

- 需求编码：REQ-072661e7-6dc5-46b2-bba6-e19d2b16b6b2
- 需求名称与唯一需求目录：AI任务配置通道能力动态下发与选择交互修复；docs/业务线/BL000012-工作台/需求迭代/REQ-072661e7-6dc5-46b2-bba6-e19d2b16b6b2-AI任务配置通道能力动态下发与选择交互修复/
- 涉及仓库与功能域：workhub-server（model/service/dao）、workhub-web（AiConfigView.vue、types）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：v1
- Design/契约版本：v1
- 目标环境与分支：后端 codex/development-20261102、前端 codex/development-20261103（均在现有未提交改动之上继续，不回退他人改动）
- 任务计划状态：草案，待完整规格统一确认
- 负责人/评审人：待确认

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | Definitions 增加 allowedCliTypes 与统一校验、可空查找 | 无 | 无 | §3 保存校验收敛 / AC-05 | 待开始 |
| T-02 | Row/Response 分离 + 能力字段填充 + 单测 | T-01 | 无 | §3 契约 / AC-01、AC-06 | 待开始 |
| T-03 | 前端类型与下拉过滤、提示语、能力文案改造 | T-02（接口字段可用） | 可先写类型与纯函数，联调需 T-02 | §3 前端行为契约 / AC-02、AC-03、AC-04 | 待开始 |
| T-04 | 端到端验证与文档更新 | T-01~T-03 | 无 | §5 / AC-01~AC-06 | 待开始 |

执行顺序：T-01 → T-02 → T-03 → T-04。共享文件协调：workhub-server 与 workhub-web 为不同仓库无写冲突；后端 `AiUseCaseConfigService.java` 仅 T-01/T-02 顺序修改。

## 3. 任务明细

### T-01：能力规则收敛到 AiUseCaseDefinitions

- 描述与目标：`Definition` 增加 `allowedCliTypes` 字段与 `requireProvider(channelType, cliType)` 统一校验；新增 `find(code)` 可空查找；service `toEntity` 的 `:178-181` 两段判断替换为统一校验，语义与提示语保留。
- 输入：Design §3；`AiUseCaseDefinitions.java`、`AiUseCaseConfigService.java` 现状。
- 输出：修改后的 Definitions/Service；`AiUseCaseConfigServiceTest` 新增/调整用例（ZCode+LOCAL_REPOSITORY_READ 拒绝、Codex 通过、API 场景绑 CLI 拒绝）。
- 前置依赖：无。
- 允许修改范围：`AiUseCaseDefinitions.java`、`AiUseCaseConfigService.java` 校验段、相关测试。
- 明确不修改范围：Definitions 既有场景的 capability 与 allowedChannelTypes 取值；错误信息原文（ZCode 场景）；网关调用链（DefaultAiGatewayClient 等）。
- 项目补充任务字段（建议研发任务标题）：后端-场景通道能力规则收敛

验收条件：

- [ ] 条件 1：`AiUseCaseConfigServiceTest` 中 ZCode 绑定启用的需求澄清分析仍被拒绝且提示语原文不变；Codex CLI 绑定通过。
- [ ] 条件 2：`requireProvider` 对 null/未知 channelType、CLI 但 cliType 缺省（历史数据按 `AiCliType` 既有识别）行为与现状一致。

计划验证方式（尚未执行）：

- `mvn -pl workhub-service -am test -Dtest=AiUseCaseConfigServiceTest`（后端单测）。

状态及真实结果：已完成（2026-11-06）。`Definition` 增加 `allowedCliTypes` 与 `allowsCliType`、`find` 可空查找；`toEntity` 校验改为 Definition 驱动，ZCode+LOCAL_REPOSITORY_READ 拒绝提示语原文保留。新增用例 `create_shouldRejectZcodeProviderForLocalRepositoryUseCase`（断言原文）、`create_shouldAllowCodexProviderForLocalRepositoryUseCase` 通过。

### T-02：场景接口下发能力字段

- 描述与目标：新增 `AiUseCaseConfigRow`（=现 Response 减 3 字段），mapper `findAll`/`findDetailById` 返回类型切换、SQL 不变；`AiUseCaseConfigResponse` 增加 `capability/allowedChannelTypes/allowedCliTypes`；service list/detail 统一 Row→Response 映射并按 Definitions 填充；两个控制器无签名变化自动生效。
- 输入：Design §3 契约；`AiUseCaseConfigMapper.java`、`AiUseCaseConfigResponse.java`、service `list()/detail()`。
- 输出：model/dao/service 代码；单测断言已注册场景三字段值与 Definitions 一致、未注册编码（用测试数据模拟 sdd-analyze）三字段为 null；既有构造 `AiUseCaseConfigResponse` 的测试同步修正。
- 前置依赖：T-01。
- 允许修改范围：上述文件及测试；`AiConfigControllerTest`/`AiConfigCompatibilityControllerTest` 如断言字段数需同步。
- 明确不修改范围：接口路径、权限注解、分页结构、MANAGE_COLUMNS SQL 语义。
- 项目补充任务字段：后端-场景能力字段下发

验收条件：

- [ ] 条件 1：单测覆盖"已注册场景字段值正确、未注册场景三字段 null"两类断言（AC-01、AC-06 的服务层部分）。
- [ ] 条件 2：`mvn -pl workhub-controller -am test` 相关用例全绿，证明两套路由（/api 与 /manage）均透出新字段。

计划验证方式（尚未执行）：

- 后端单测 + 本地起服务调用 `/manage/system/ai-use-case/list` 比对 JSON。

状态及真实结果：已完成（2026-11-06）。新增 `AiUseCaseConfigRow`（mapper 行对象，SQL 不变），`AiUseCaseConfigResponse` 追加 3 字段并保留旧紧凑构造器，service list/detail 统一 `toResponse` 填充。新增用例 `detail_shouldFillCapabilityFieldsFromDefinitions`、`detail_shouldReturnNullCapabilityForUnregisteredCode`、兼容控制器 `useCase_shouldExposeCapabilityFields`（MockMvc 断言 JSON 字段）通过。本地起服务比对 JSON 未执行（需登录态），以 MockMvc JSON 断言替代。

### T-03：前端能力驱动下拉与提示语

- 描述与目标：`types/ai-config.ts` 补 3 个可选字段；`AiConfigView.vue` 删除 `requiresLocalRepository` 与 IMAGE 编码硬编码，通道/厂商/供应商下拉与 `resolveUseCaseProviderConfigId` 统一改为能力谓词过滤（null 视为不限制）；`useCaseCapabilityText` 改 capability 映射；未匹配提示区分"组合无通道"与"场景能力限制（允许 X 通道）"。
- 输入：Design §3 前端行为契约；T-02 产出的接口字段。
- 输出：前端代码修改；手工验证记录（截图/步骤）。
- 前置依赖：T-02（联调）；类型与谓词函数可先行编写。
- 允许修改范围：`AiConfigView.vue`、`types/ai-config.ts`、必要时 `api/ai-config.ts` 注释。
- 明确不修改范围：接入配置（provider）编辑弹窗逻辑；既有未提交的 CLI 目录加载改造。
- 项目补充任务字段：前端-场景配置下拉能力过滤

验收条件：

- [ ] 条件 1：编辑"需求澄清分析"时厂商/供应商下拉不再出现仅 ZCode 的先行通+智谱组合（AC-02）；grep 无 `requiresLocalRepository` 与硬编码编码清单残留（AC-03）。
- [ ] 条件 2：构造"组合无通道"场景（如新增空组合数据）提示语与"能力限制"提示语可区分（AC-04）；sdd-analyze 行打开编辑不报错、能力列显示兜底文案（AC-06）。

计划验证方式（尚未执行）：

- `npm run build`（或项目既有 type-check 脚本）+ 本地连开发后端手工验证并记录。

状态及真实结果：已完成（2026-11-06）。`types/ai-config.ts` 补 3 个可选字段；`AiConfigView.vue` 删除 `requiresLocalRepository` 与 IMAGE 编码清单，新增 `useCaseChannelAllowed` 谓词统一用于厂商/厂商/通道下拉与 `resolveUseCaseProviderConfigId`；`unmatchedProviderMessage` 区分三类原因；能力文案改 capability 驱动。`npm run build`（vue-tsc + vite）通过；grep 确认无硬编码残留。页面点击实测待用户重启本地后端后执行（AC-02/AC-04 页面侧核对）。

### T-04：端到端验证与交付记录

- 描述与目标：本地后端+前端连开发库完成 AC-01~AC-06 核对；更新 `现状.md`、`迭代日志.md`；在本文档记录证据与剩余风险。
- 输入：T-01~T-03 产物。
- 输出：验证记录（接口 JSON 摘录、页面验证说明）、两份文档更新。
- 前置依赖：T-01~T-03。
- 允许修改范围：需求目录内文档、`现状.md`、`迭代日志.md`。
- 明确不修改范围：未确认的生产库迁移安排（Design 待确认 2）。
- 项目补充任务字段：交付-验证与归档

验收条件：

- [ ] 条件 1：AC-01~AC-06 逐条有核对结果记录（通过/不通过及证据）。
- [ ] 条件 2：迭代日志记录实际改动范围与验证环境；未完成项与风险显式列出。

计划验证方式（尚未执行）：手工核对 + 文档评审。

状态及真实结果：部分完成（2026-11-06）。自动化验证已完成：后端 `AiUseCaseConfigServiceTest` 8/8、`AiConfigControllerTest` 1/1、`AiConfigCompatibilityControllerTest` 3/3 通过；前端构建通过；`现状.md` 与 `迭代日志.md` 已更新。AC-01（接口字段，MockMvc+单测）、AC-03（grep 无残留）、AC-05（单测断言原文拒绝语）、AC-06（null 兜底单测）已核实；AC-02/AC-04 页面侧与 AC-06 页面展示待管理员重启本地后端后在页面核对。剩余风险：workhub-service 全量测试 2 项失败（BusinessLogSearchServiceTest ELK 范围、KnowledgeArchiveWorkflowTest 文档路径）属工作区并行进行中改动，与本需求无关（与迭代日志 2026-11-05 记录一致）。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-02；AC-02→T-03；AC-03→T-03；AC-04→T-03；AC-05→T-01；AC-06→T-02/T-03。
- Design 接口/数据/非功能约束承接：契约（§3）→T-02/T-03；保存校验收敛（§3）→T-01；回滚与文档（§5）→T-04。
- 未覆盖项及原因：无。
- 已生效事实核实与文档更新：T-04 承接。
- 关联版本链接：Proposal v1、Design v1，同目录。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：Proposal/Design/Tasks v1 全量确认，用户于 2026-11-06 对话确认（"改吧"），待确认三项按默认处理（名称沿用、生产库迁移另行跟进、ZCode 拒绝提示语保留原文）；实施按确认范围完成，页面侧核对项见 T-04 剩余记录。
