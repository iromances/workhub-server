# Design：Codex 模型能力动态加载

- 需求编码：REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442
- 对应 Proposal：v2；本设计：v2，已确认，已实施；自动化验证通过，待页面联调。
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

通过标准输入输出传输逐行 JSON；stderr 独立消费并直接丢弃，不保留内容，不能合并进 JSON 输出流。匹配请求 id、检查 error、忽略合法通知，完整读取 nextCursor；检测重复游标。初始化和全部分页共用 10 秒截止时间；stdout 累计上限为 4 Mi 字符，避免异常进程无限输出。查询只发送初始化和 model/list，不创建任务或发起推理。所有路径关闭流、终止并等待所启动进程及其子进程退出，处理中断时恢复线程中断标记。

### 4.2 缓存

同一解析后的可执行文件路径及工作目录共用 60 秒成功结果；缓存最多保留 32 项，最多同时查询 4 个不同环境，同一键并发查询合并，避免连续开窗堆积进程。失败不缓存，不在失败时自动返回过期值。刷新按钮绕过缓存；缓存语义和 fetchedAt 可用于用户识别来源时效。短期缓存并不保证 Codex 自身目录已联网刷新。

### 4.3 前端

打开新建/编辑弹框且 CLI 已启用时加载；之后启用 CLI 也加载。命令或目录修改后清除该表单的能力结果，并在字段 change（结束编辑）时重新请求；提供刷新/重试按钮。API 页签沿用字典数据，CLI 独立保存能力结果。即便当前停留 API 页签，启用的 CLI 也可完成加载。

每次开窗及查询使用递增请求标识；关闭弹框、改变命令/目录后，旧结果不得覆盖当前状态。失败反馈保留在 CLI 表单中，模型选择在加载中或加载失败时不可新增选择。原配置值始终可见，缺失值以禁用选项和“当前不可选”提示显示。

仅用户主动切换模型时联动强度：原强度支持则保留，否则使用该模型默认强度。开窗回填、刷新结果及异步 watch 不能覆盖旧值。新建时由用户选择模型，再带入模型默认强度。

原有模型/强度及 CLI 环境均未改变时，允许保存其他字段，即使查询失败或模型已下架。新建或主动改变模型、强度、命令、目录后，启用的 CLI 必须先成功查询并选择目录中的有效组合；禁用 CLI 不阻塞 API 保存。后端保存仍使用现有配置契约，不在保存流程强制启动第二次查询；CLI 强度保存原值，历史值仅按第 2 节单独兼容。

## 5. 验证、交付与回滚

重点测试协议握手、分页、错误和超时、进程回收、缓存隔离与过期、权限、前端请求竞态、缺失历史值以及 CLI 强度透传。测试使用可控协议输入或模拟进程，不依赖真实账号发起推理。执行相关 Maven 测试和前端 `npm run build`，不安装依赖，不自行启动 WorkHub。

先发布后端再发布前端；无需数据库迁移。回滚时前端退回字典选项、后端回退新增查询能力；对已经保存的新强度要核对旧版调用兼容性，不能自动改成其他强度。实施验证后更新 WorkHub 系统设计、迭代日志和前端架构中的实际行为，保留两仓库既有未提交修改。

## 6. 一致性与确认

Proposal AC-01～07 分别由上述协议、前端、Codex 编码原值传递、错误、缓存、权限及历史值规则承接。不存在需要另建 ADR 的架构决策；本文件为接口契约的权威定义。v2 完整规格已由用户确认，进入 [tasks.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442-Codex模型能力动态加载/tasks.md>) 实施步骤。


## 7. v3：asset 移植设计（已确认）

### 7.1 当前事实

2026-09-24 核实：asset 后端 Spring Boot 4.1.0 / Java 25，后端、前端均在 `codex/dev-20260822`。后端已有若干暂存 `.DS_Store` 及复盘资料，前端工作区无改动，本次保持无关内容原状。未读取含凭据的环境配置。

- `asset-app/.../controller/system/AiProviderConfigController.java` 提供 `/manage/system/ai-provider/list|save|test-connection`，返回 `Result<T>`。
- `asset-domain-system/.../AiProviderConfigServiceImpl.apply` 对默认强度强制大写；`CodexCliClientImpl.appendReasoningConfig` 调用 `AiModelRequestSupport.normalizeReasoningLevel`，将 ultra 变为 xhigh，均需按 CLI 路径适配。
- `CodexCliClientImpl` 还包含 Windows、带参数命令和自动查找能力。本期目录查询限定显式 Codex 可执行文件（codex 或 codex.exe 名称/路径），不启动 npx、不下载 CLI；原执行客户端已有命令候选机制不扩大重构。查询不支持的命令给出明确提示。
- asset 的 `UserSessionInterceptor` 将 X-Token 解析为 `UserSession`，当前不对 AI 接入端点强制登录，因此不能仅复制 WorkHub 注解，也不能误认为已有登录检查会拒绝匿名模型查询。
- 前端 `src/views/system/aigateway/index.vue`、`src/api/SystemAiConfigApi.js` 使用字典选项；已有 `tests/unit/views/SystemAiGateway.spec.js` 可承接组件回归。

### 7.2 模块及接口

在 asset-shared 的 `model/dto/aigateway` 定义查询 Request/Response；asset-domain-system 的 system 服务及 impl 承载协议、缓存和配置校验；asset-app 在接入配置路径下通过独立 Controller 增加 `POST /manage/system/ai-provider/codex-models`。实际外部路径带部署 context path，由前端现有 `VUE_APP_ASSET` 拼接。

协议请求/响应沿用 v2 第 3 节的 cliCommand、cliWorkingDirectory、forceRefresh、models、fetchedAt、cached；使用 asset 的 Result 包装。补充可选 providerId，用于后端核对已保存 CLI 配置及历史强度，不接收 API Key。providerId 不存在或指向非 CLI 记录时明确报错。

为了将旧值兼容留在后端，每个模型可额外返回 `preservedReasoningEffort`：当已有记录的旧大写强度与该模型某个原始强度匹配时，返回 `{value: 历史原值, label: 对应 Codex 原值}`；否则为 null。`supportedReasoningEfforts` 仍完全保留 Codex 原始编码，不添加或改写供应商能力。前端将该可选历史展示项与原始选项一起显示，仅作原值比较；不内置 HIGH→high 或 ULTRA→ultra 映射表。用户主动选择新强度后保存原始编码，打开及刷新不修改旧配置。

前端切换模型时，保留该模型返回的选项中已有强度，否则使用该模型的 defaultReasoningEffort；默认值来自后端。关闭弹框、更换命令或目录时使旧请求失效，避免覆盖当前状态。

### 7.3 保存、执行和认证

CLI 默认模型/强度原值保存；默认长度约束沿用表结构并核实实际字段长度。API 路径继续使用原转换。沿实际运行配置解析链核对模型/强度，CLI 参数使用安全字符串编码，不通过 shell 拼接。

`saveForManage` 基于数据库原记录判断组合是否改变：启用的 CLI 新建、重新启用或改变命令/目录/模型/强度时，使用同一目录服务的有效缓存或查询结果校验；失败或无效组合抛 AssetException，不保存。已有启用记录且上述字段未变时允许保存其他字段，不强制查询。旧大写值只能在与该记录原值相同且对应 Codex 强度可用时兼容，不能把任意输入都改写后当作有效配置。测试连通参数同样准确保留动态编码。

沿用 asset X-Token 会话，不引入角色或权限体系：模型查询入口与因管理保存而触发目录查询的路径检查有效 UserSession；匿名请求在启动进程前拒绝。新增查询未登录采用 HTTP 401；其余业务失败沿用 AssetException/Result 的错误协议。保持 API 接入及无关管理端点原有认证行为。

### 7.4 进程及缓存

适配 WorkHub v2 客户端及服务，保留 initialize→initialized→model/list 分页、隐藏项过滤、10 秒总查询超时、stdout 4 Mi 字符限制、stderr 不保留、成功失败均回收进程。缓存仍按命令实路径和工作目录隔离，TTL 60 秒、最多 32 项、同键合并、最多 4 个不同环境查询。providerId 只用于单次展示/兼容，不把某记录的历史项写入共享原始目录缓存。

### 7.5 验证和发布

- 后端：移植协议、缓存和进程测试，扩展接入保存、运行配置、CLI 参数测试；增加会话拦截、缺省刷新参数、失败响应测试。用模拟进程及目录，无真实模型推理。
- 前端：扩展 SystemAiGateway Jest 组件测试，覆盖加载、联动、后台返回的历史选项、错误重试、关闭弹框及环境变化竞态；执行 `npm run lint`、定向 Jest、`npm run build:prod`。
- 按 asset-portal 指引执行 `./mvnw -q -DskipTests compile`，先检查本地 wrapper 分发缓存，禁止触发下载；本地缺失时使用已有 Maven 并明确记录差异，不安装组件。
- 先更新后端再前端；无需数据库迁移。部署、应用启动与真实账号联调需具备相应授权；未执行的验证不得标通过。
- 回滚前端选项与新增查询代码时核对已保存的新强度在旧 CLI 中的兼容性，不自动覆盖配置。

### 7.6 规格归属

本节是同一需求的 v3 移植补充，WorkHub v2 历史设计与结果保留原位。asset 的系统设计/迭代日志维护实现摘要并链接此唯一规格；不在 asset 或 agents-playbook 复制完整需求。asset/asset-portal 指引要求本次完整规格及接口调整确认后实施，用户于 2026-09-24 确认，开始实施。
