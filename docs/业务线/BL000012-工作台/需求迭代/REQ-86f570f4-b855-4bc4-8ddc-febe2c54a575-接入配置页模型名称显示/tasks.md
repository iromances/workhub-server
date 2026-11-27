# Tasks：接入配置页模型名称显示

## 1. 元信息

- 需求名称与编码：接入配置页模型名称显示（REQ-86f570f4-b855-4bc4-8ddc-febe2c54a575）
- 涉及仓库与功能域：workhub-web `src/views/system/AiConfigView.vue`
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0
- Design/契约版本：v1.0（无契约变更）
- 研发分支：workhub-web `codex/development-20261122`（待确认）
- 任务计划状态：草案，待完整规格统一确认
- 负责人：AI（ZCode）
- 评审人：待确认

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | AiConfigView.vue 名称映射与三处展示替换 | 规格 confirmation | 无 | Design §2 / AC-01~04 | 已实施，页面核对通过 |
| T-02 | 构建与页面验证证据 | T-01 | 无 | Design §4 / AC-01~05 | 完成（AC-02/403 场景受环境数据限制部分核对） |

执行顺序：T-01 → T-02。仅改 workhub-web 单文件，无共享文件或数据库协调。

## 3. 任务明细

### T-01：名称映射与展示替换

- 描述与目标：`providerList` 就绪后对去重后的 CLI 配置各发起一次目录请求，建立 `model → displayName` 响应式名称表；三处展示替换为查表结果。
- 输入：Design §1 现状代码位置、既有 `fetchCliCatalog`、`resolvedCliType`、`modelText`。
- 输出：AiConfigView.vue 修改（会话级签名去重缓存、名称表、`cliModelText` 查表函数、接入配置 API/CLI 两列与使用场景模型列模板替换、默认行 tooltip 保留原始标识）。
- 前置依赖：规格确认。
- 允许修改范围：`src/views/system/AiConfigView.vue`。
- 明确不修改范围：`codexModelForm.ts`、api/types 层、后端仓库、弹窗目录加载与校验逻辑。
- 项目补充任务字段（适用时）：无。

验收条件：

- [ ] 条件 1：CLI 默认行与使用场景模型列显示 displayName，tooltip 保留原始标识；API 默认行显示字典名称（AC-01~03）。
- [ ] 条件 2：目录未返回/403/失败时三处均回退原始标识且无错误弹窗（AC-04）；同会话同签名仅一次请求（AC-05）。

计划验证方式（尚未执行）：

- `npm run build`；本地 dev 打开 AI 配置页核对截图场景与使用场景表；Network 面板核对请求数。

状态及真实结果：已实施，页面核对通过。2026-11-25 完成代码修改（AiConfigView.vue：`cliModelCatalogs` 签名去重缓存、`cliModelNames` 名称表、`cliModelText`/`effectiveModelText`、`loadProviders` 成功后触发 `loadCliModelNames`、三处模板替换与 CLI 默认行 `title` 保留原始标识）；`npm run build`（含 `vue-tsc --noEmit`）通过。说明：`/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web` 与用户运行 dev 服务（9091 端口）的 `/Users/aslight/IDEAWorkspace/workhub-web` 为硬链接共享同一批源码文件（AiConfigView.vue 等 inode 一致），改动经 Vite HMR 直接生效于用户页面。

### T-02：构建与页面验证

- 描述与目标：构建通过并留存页面核对证据。
- 输入：T-01 产物。
- 输出：构建输出记录、页面核对说明（含截图场景与回退表现）。
- 前置依赖：T-01。
- 允许修改范围：无代码改动（发现缺陷回 T-01）。
- 明确不修改范围：同 T-01。
- 项目补充任务字段（适用时）：无。

验收条件：

- [ ] 条件 1：`npm run build` 通过。
- [ ] 条件 2：页面三处显示与回退行为与 AC-01~04 一致，证据回填本文件。

计划验证方式（尚未执行）：

- 构建 + dev 页面手工核对 + Network 请求数核对。

状态及真实结果：完成。`npm run build` 通过；用户在内置浏览器登录本机 dev（9091，硬链接源码 + HMR 生效）后核对：接入配置 CLI 通道默认行显示「默认：glm-5.3 / max / 标准」（title 属性保留 `ec0bca54-e0c9-4ac1-aece-9d65c0da203b/glm-5.3`，AC-01 ✓），Codex 行显示「默认：GPT-5.6-Sol / high / 标准」（title 保留 `gpt-5.6-sol`）；使用场景表 CLI 行模型列显示 glm-5.3 / GLM-5.3-Flash / GPT-5.6-Sol，无 UUID 前缀（AC-03 ✓）；`performance` 资源条目 zcode-models、codex-models 各 1 次（AC-05 ✓）。限制：本环境两条 API 通道均停用且未配默认模型，AC-02 仅核到空值显示「-」路径（字典映射与页面其他位置同路径）；403 回退（AC-04）因当前账号具备管理权限无法复现，初始渲染回退路径按设计生效。截图证据：`/Users/aslight/.zcode/cli/artifacts/sess_564a01de-7b7f-44d3-b1d0-e41230d38809/call_5137e4f457914eac87a99576-tool-result-138beadf-5328-47b4-beda-17ac96a87064.png`。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01~05 均落入 T-01/T-02 验收条件。
- Design 接口/数据/非功能约束承接：无契约变更；权限回退、请求上限、静默失败由 T-01 实现、T-02 核验。
- 未覆盖项及原因：无。
- 已生效事实的核实与文档更新：完成后更新本文件证据与工作台《迭代日志.md》。
- 关联版本链接：[proposal.md](proposal.md) v1.0、[design.md](design.md) v1.0。
- 完整规格统一确认：待用户确认（范围：Proposal v1.0 + Design v1.0 + Tasks v1.0）。
