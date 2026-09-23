# Design：Codex 模型能力动态加载

- 需求编码：REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442
- 对应 Proposal：v2；本设计：v2，草案，待完整规格统一确认。
- 核对日期：2026-09-23；server 分支 `codex/dev-20260820`，web 分支 `codex/dev-20260707`。

## 1. 现状与证据

- 前端 `src/views/system/AiConfigView.vue`：`loadDicts`、`openProviderDialog`、`saveProviderForm`，API/CLI 共用模型与强度选项。
- 前端 `src/api/ai-config.ts` 使用 `/api/manage/system` 兼容接口，HTTP 默认超时 15 秒。
- 后端 `AiConfigCompatibilityController` 提供接入保存和当前表单连通测试；`AiProviderConfigService` 保存大写推理强度，目前只检查默认字段非空。
- `DefaultAiGatewayClient.normalizeCliReasoning` 复用 `AiHttpPayloadSupport.normalizeReasoning`，后者将 `ULTRA` 映射成 `xhigh`。
- `CodexCliClient` 使用 `ProcessBuilder` 执行 `codex exec`，其输出协议不同于 app-server，不直接复用执行方法。
- 本机生成的 `ModelListParams` 只有 cursor、limit、includeHidden，没有强制刷新云端目录的参数。
- 协议参考：[Codex App Server](https://learn.chatgpt.com/docs/app-server#list-models-modellist)；模型发现与推理选项以返回数据为准。

## 2. 流程与职责

前端打开弹框 → 查询能力接口 → 服务解析 CLI 命令和目录 → 读取有效缓存或启动 Codex app-server → initialize 应答 → initialized 通知 → model/list 分页 → 返回模型能力 → 前端模型与强度联动。

- Controller：请求参数与权限控制，沿用统一响应、异常处理。
- Service：管理短期缓存与查询；专用 Codex 客户端负责 stdio 协议和进程生命周期。只新增这次发现所需能力，不改造为通用 RPC 框架。
- 前端：处理弹框状态、加载、错误、刷新、历史值提示、联动与保存前的组合检查。
- 编码传递：Codex 返回的模型及强度编码是选项值的权威来源，查询响应、前端选项、保存入库、CLI 参数全程使用原值。CLI 保存不再调用强制转大写逻辑，CLI 网关不再复用 HTTP 强度转换。显示名称、中文标签与选项值分离。
- CLI 参数：本机 Schema 将 ReasoningEffort 定义为非空字符串，不维护固定的新强度枚举。沿用现有字段长度约束，超长值明确报错；构造 CLI 配置参数时正确编码字符串，避免引号等字符改变参数语义，不能借参数转义改写实际值。模型是否支持以发现目录和 Codex 执行结果为准。
- 历史兼容：只针对旧字典产生的全大写强度 `NONE/LOW/MEDIUM/HIGH/XHIGH/MAX/ULTRA`，在历史值匹配及 CLI 执行边界兼容对应的小写编码；不使用语义别名，`ULTRA` 对应 `ultra`，不能变成 `xhigh`。打开弹框或刷新不自动修改持久化值，未修改的历史值继续保留；用户重新选择后保存 Codex 返回的原值。模型编码不做大小写兼容。此分支只处理历史数据，不用于转换新的动态选项。

## 3. 接口契约

新增 `POST /api/manage/system/ai-provider/codex-models`。

请求字段：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| cliCommand | string，必填 | 单个 Codex 可执行文件名称或路径，与现有 CLI 命令字段语义相同；不支持 shell 命令串或附加参数 |
| cliWorkingDirectory | string，可空 | 为空时采用后端工作目录，否则必须为存在的目录 |
| forceRefresh | boolean，默认 false | 绕过 WorkHub 的短期缓存，不代表强制刷新 Codex 云端目录 |

成功响应为 `ApiResponse<CodexModelCatalogResponse>`，data 字段：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| models | array | 全部分页汇总后的可见模型，保留 Codex 返回顺序 |
| models[].model | string | 模型标识，保存至原 defaultModel 字段 |
| models[].displayName | string | 下拉显示名称 |
| models[].isDefault | boolean | Codex 建议默认模型，打开已有配置不据此覆盖 |
| models[].defaultReasoningEffort | string | Codex 返回的默认强度原值 |
| models[].supportedReasoningEfforts | array | reasoningEffort（Codex 原值）、description（说明） |
| fetchedAt | ISO 时间字符串 | 本次实际查询完成时间，命中缓存时保持原值 |
| cached | boolean | 是否命中 WorkHub 缓存 |

不接收 API Key，不创建或保存 Provider。权限为 `system:ai-config:create/update/manage` 任一，沿用现有连通测试权限。无权限由 Spring Security 拒绝；非法命令/目录用参数错误，启动失败、超时、RPC 错误、异常 JSON 或空目录用明确业务错误，沿用全局异常响应，不返回成功空数组。日志只记录必要的耗时和错误类别，不返回原始 stderr 或登录信息。

## 4. 进程、缓存与交互边界

### 4.1 进程

使用 `ProcessBuilder` 参数数组运行 `<cliCommand> app-server --listen stdio://`，不经过 shell。执行入口限定为 Codex 可执行文件（命令名 codex 或文件名 codex/codex.exe 的路径），不接受任意工具命令。工作目录与接入运行目录保持一致，继承后端运行用户的 Codex 登录与配置环境。

通过标准输入输出传输逐行 JSON；stderr 独立消费并限长，不能合并进 JSON 输出流。匹配请求 id、检查 error、忽略合法通知，完整读取 nextCursor；检测重复游标。初始化和全部分页共用 10 秒截止时间；输出设合理总量上限，避免异常进程无限输出。查询只发送初始化和 model/list，不创建任务或发起推理。所有路径关闭流、终止并等待所启动进程及其子进程退出，处理中断时恢复线程中断标记。

### 4.2 缓存

同一解析后的可执行文件路径及工作目录共用 60 秒成功结果；缓存有容量上限，同一键并发查询合并，避免连续开窗堆积进程。失败不缓存，不在失败时自动返回过期值。刷新按钮绕过缓存；缓存语义和 fetchedAt 可用于用户识别来源时效。短期缓存并不保证 Codex 自身目录已联网刷新。

### 4.3 前端

打开新建/编辑弹框且 CLI 已启用时加载；之后启用 CLI 也加载。命令或目录修改后清除该表单的能力结果，并在字段 change（结束编辑）时重新请求；提供刷新/重试按钮。API 页签沿用字典数据，CLI 独立保存能力结果。即便当前停留 API 页签，启用的 CLI 也可完成加载。

每次开窗及查询使用递增请求标识；关闭弹框、改变命令/目录后，旧结果不得覆盖当前状态。失败反馈保留在 CLI 表单中，模型选择在加载中或加载失败时不可新增选择。原配置值始终可见，缺失值以禁用选项和“当前不可选”提示显示。

仅用户主动切换模型时联动强度：原强度支持则保留，否则使用该模型默认强度。开窗回填、刷新结果及异步 watch 不能覆盖旧值。新建时由用户选择模型，再带入模型默认强度。

原有模型/强度及 CLI 环境均未改变时，允许保存其他字段，即使查询失败或模型已下架。新建或主动改变模型、强度、命令、目录后，启用的 CLI 必须先成功查询并选择目录中的有效组合；禁用 CLI 不阻塞 API 保存。后端保存仍使用现有配置契约，不在保存流程强制启动第二次查询；CLI 强度保存原值，历史值仅按第 2 节单独兼容。

## 5. 验证、交付与回滚

重点测试协议握手、分页、错误和超时、进程回收、缓存隔离与过期、权限、前端请求竞态、缺失历史值以及 CLI 强度透传。测试使用可控协议输入或模拟进程，不依赖真实账号发起推理。执行相关 Maven 测试和前端 `npm run build`，不安装依赖，不自行启动 WorkHub。

先发布后端再发布前端；无需数据库迁移。回滚时前端退回字典选项、后端回退新增查询能力；对已经保存的新强度要核对旧版调用兼容性，不能自动改成其他强度。实施验证后更新 WorkHub 系统设计、迭代日志和前端架构中的实际行为，保留两仓库既有未提交修改。

## 6. 一致性与确认

Proposal AC-01～07 分别由上述协议、前端、Codex 编码原值传递、错误、缓存、权限及历史值规则承接。不存在需要另建 ADR 的架构决策；本文件为接口契约的权威定义。待统一确认 v2 完整规格后进入 [tasks.md](tasks.md) 实施步骤。
