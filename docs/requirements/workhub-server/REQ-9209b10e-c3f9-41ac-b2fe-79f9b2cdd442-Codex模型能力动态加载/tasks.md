# Tasks：Codex 模型能力动态加载

## 1. 元信息

- 需求编码：REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442
- 目标系统：WorkHub（v2）；asset / asset-portal（v3 移植，见第 6 节）。
- Proposal / Design / 接口契约：v2，见 [proposal.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442-Codex模型能力动态加载/proposal.md>)、[design.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442-Codex模型能力动态加载/design.md>)。
- 目标分支：server `codex/dev-20260820`；web `codex/dev-20260707`。
- 当前状态：v2、v3 均已实施，自动化验证通过；真实账号与页面联调待完成。

## 2. 确认记录

| 日期 | 范围/版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-09-23 | 使用 Codex app-server 动态加载接入弹框模型和推理强度的方向 | 用户 | 用户要求“那就帮我改成这种方式”；此时完整 SDD 规格尚未形成 |
| 2026-09-23 | v1 草案评审修订 | 用户 | 删除 Proposal 冗余目录栏；指出 Codex 动态编码应直接使用，v2 已改为全程原值传递，历史大写值兼容单列 |
| 2026-09-23 | Proposal、Design、Tasks v2 完整规格 | 用户 | 用户明确要求“开始执行任务”，确认按 v2 实施 |

## 3. 任务与依赖

| 任务 | 交付物 | 依赖 | 验收映射 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | Codex 模型发现协议客户端及缓存服务、请求响应 DTO | v2 规格确认 | AC-01、04、05、06 | 已完成 |
| T-02 | 能力查询 Controller 与权限测试 | T-01 | AC-01、04、06 | 已完成 |
| T-03 | CLI 模型与强度原值保存、传递，历史大写强度兼容及回归测试 | v2 规格确认 | AC-03 | 已完成 |
| T-04 | 前端 API/types、接入弹框加载与联动、刷新和失败提示 | T-02 | AC-01、02、04、05、07 | 已完成 |
| T-05 | 定向测试、前端构建、实际结果和系统文档更新 | T-01～04 | AC-01～07 | 自动化验证及文档已完成；页面联调待完成 |

按表中依赖顺序实施，不委派子代理。修改范围为后端 ai 相关 model/service/controller 与测试、前端 ai-config API/types/AiConfigView 及必要局部状态测试、对应文档。其他已有未提交修改保持原状。

## 4. 验证计划（原计划保留，实际执行见第 5 节）

- 协议：握手顺序、带通知的响应匹配、多页目录、重复游标、错误 JSON、RPC error、进程提前退出、空目录、总超时、输出上限及成功失败资源回收。
- 缓存：60 秒有效期、不同命令/目录隔离、刷新绕过缓存、并发合并、失败不缓存。
- 权限：未认证或无接入编辑权限请求被拒绝，有 create/update/manage 任一权限可查询。
- 编码链路：Codex 返回的模型与强度在查询响应、前端选项值、数据库保存、CLI 参数中完全一致；覆盖 minimal、ultra 和新的强度编码，验证没有固定枚举或大小写转换。历史大写值兼容独立验证，ULTRA 不映射成 xhigh；同时验证 CLI 参数字符串转义不改变实际值。
- 前端：打开弹框、启用 CLI、修改目录后加载，模型强度联动，回填不改原值，旧请求失效，失败重试，缺失值提示、原样保存和新组合校验。
- 构建：运行受影响模块 Maven 测试及已有前端构建；缺失依赖时记录，不自行安装。
- 实际联调：待允许的运行环境可用时核对本机模型结果与弹框。不开启 WorkHub 应用、不执行付费模型任务；若尚未联调，交付时明确列出。

## 5. 实际结果

2026-09-23 已完成只读核对：Codex CLI 版本与模型发现 Schema、前后端选项加载、CLI 推理强度转换、已有接口权限及仓库规范。已生成本目录 v1 规格；没有代码改动、数据库改动、软件安装或应用启动。未运行实施测试，不将计划写成验证通过。

Proposal 验收条件均有任务承接。规格确认后在本文件保留确认版本、确认人、日期，并逐项补充实际验证证据；发生范围变更时追加规格版本和确认记录。

2026-09-23 根据用户评审修订为 v2：动态编码全程原值传递，移除新配置的大写入库和调用时小写化设计，历史大写值兼容单独说明。仅更新规格，尚未实施代码或运行实现测试。

### 2026-09-23 实施与验证

- T-01/T-02：新增 `CodexModelCatalogRequest/Response`、`CodexModelCatalogClient/Service` 和 `CodexModelCatalogController`；提供 `/api/manage/system/ai-provider/codex-models`。查询分页、通知处理、错误反馈、10 秒超时、进程回收、60 秒缓存和权限控制已实现。缓存最多 32 项，同时最多 4 个不同环境查询，同环境并发合并。
- T-03：CLI 接入模型及强度原值入库，网关独立兼容历史大写强度；构造 CLI 参数时将强度编码为单个字符串，不再作 ULTRA→xhigh 映射。API 原有路径未改。
- T-04：新增前端类型与查询 API、`codexModelForm.ts`，接入弹框实现动态模型、强度联动、刷新重试、历史值保留及异步结果隔离。
- 后端：`mvn -o -pl workhub-controller -am '-Dtest=CodexModelCatalog*,CodexCliCommandTest,AiProviderConfigServiceTest,DefaultAiGatewayClientTest,OpenAiChatClientTest,OpenAiResponsesClientTest,AiConfigControllerTest,AiConfigCompatibilityControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test` 通过。service 37 项、controller 5 项，共 42 项，0 失败、0 错误。覆盖协议分页/错误/输出上限、超时和进程回收、缓存/环境隔离、保存原值、CLI 参数、权限、查询参数缺省和错误响应；原 API 客户端与控制器回归通过。
- 前端：使用现有 TypeScript 编译 `codexModelForm.test.ts` 至临时目录并由 Node 执行，22 项断言通过；覆盖新编码、历史值、无效组合、失败重试、关闭弹框及环境变化时的旧请求隔离。`npm run build`（已有 Node 22）通过，未安装依赖。
- 首轮受沙箱限制，Mockito JVM 附加及测试 HTTP 端口被拒绝；经工具授权在沙箱外复跑。随后修正两个测试夹具，并修复可选 forceRefresh 的缺省反序列化，最终上述测试全部通过。
- `git diff --check` 在两个仓库均通过；未修改其他任务的业务代码，前端架构说明保留原有未提交内容并追加本次行为。
- 页面验证：发现用户已有的 9529 前端服务并只读打开，但验证浏览器无登录态，停在登录页；已关闭临时页。未启动或重启 WorkHub，未实际查询账号模型目录或执行模型任务。真实账号目录、已登录弹框的成功路径与保存回读仍需后端加载新代码后联调，未记为通过。
- 系统设计与迭代日志已更新实现事实和验证限制；没有数据库迁移。


## 6. v3 asset 移植任务（2026-09-24）

状态：v3 已实施，自动化验证通过；未部署，真实账号页面联调待完成。

| 日期 | 范围/版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-09-24 | 将既有 Codex 模型能力移植到 asset | 用户 | 用户提出移植请求；目标项目技术适配与完整规格尚待确认 |
| 2026-09-24 | Proposal 第 4 节、Design 第 7 节、Tasks 第 6 节组成 v3 补充规格 | 用户 | 用户回复“确认”，批准按 v3 实施；保留 v2 历史结论 |

| 任务 | 交付物及范围 | 依赖 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| A-01 | asset-shared DTO、system 模型查询客户端/服务、接入查询端点和会话保护 | v3 确认 | AC-A03、05 | 已完成 |
| A-02 | CLI 原值保存及执行、后端历史兼容和有效组合校验 | A-01 | AC-A02、04 | 已完成 |
| A-03 | asset-portal Vue 2 弹框、API 封装、加载/刷新/重试/请求隔离 | A-01、02 | AC-A01、03、04 | 已完成 |
| A-04 | 后端及前端定向测试、编译/lint/构建、文档和迭代日志更新 | A-01～03 | AC-A01～06 | 已完成 |

允许修改范围：asset AI 接入与 Codex 相关服务/DTO/Controller、必要会话入口和相关测试；asset-portal AI 配置 API、页面及测试；两个系统的现状文档和日志。保留 asset 已暂存的 .DS_Store 和新增复盘资料，不读取或修改数据库凭据文件，不安装依赖、不启动应用、不创建交易或模型推理任务。

实际核对结果：asset 为个人投资研究项目，非 asset-plateform；两仓库分支均为 codex/dev-20260822。已阅读两仓库 AGENTS、asset 文档指引及代码风格、asset-portal 前端风格和相关实现；发现前端兼容逻辑职责、Result 响应、会话认证与 WorkHub 不同，已在 v3 中具体承接。未执行 asset 编译、测试、构建或页面验证。

### 2026-09-24 v3 实施与验证结果

- A-01：asset-shared 增加目录 DTO；system 增加协议客户端、原始目录缓存和带历史选项的查询服务；app 增加同接入路径下的独立 Controller。查询保持分页、10 秒超时、输出限制和资源回收；新增查询要求有效 X-Token 会话，未登录 HTTP 401。
- A-02：CLI 默认模型/强度原值保存并传至 Codex 参数，历史大写仅在执行边界转成对应小写，ULTRA→ultra。新增/重新启用/修改组合时查询有效目录并校验；未改组合的旧记录和关闭 CLI 不强制查询。历史展示选项由后端逐记录装配，不污染共享缓存。最终检查补充带空格通道编码的归一化，防止跳过保存校验。
- A-03：Vue 2 / Element UI 弹框打开或启用 CLI 时查询，命令/目录变化后重新加载；支持刷新和失败重试；原值缺失提示、模型强度联动、关闭弹框及变更环境后的旧请求隔离均实现。前端无旧值映射表；API 通道、场景编辑器及速度选项不变。
- 字段长度核对：`V20260711_153520__add_ai_provider_channel_defaults.sql` 中模型 varchar(128)、强度 varchar(32)，新增校验与现表一致，无数据库变更。
- 编译：原 `./mvnw` 文件无可执行权限，直接调用得到 permission denied；改用 `sh ./mvnw -o -q -DskipTests compile` 成功，使用本地已有 Maven 3.8.4 分发和缓存依赖，未下载或安装。
- 后端最终命令：`sh ./mvnw -o -q -pl asset-app -am '-Dtest=CodexModelCatalog*,CodexCliClientImplTest,CliAiClientImplTest,AiProviderConfigServiceImplTest,AiGatewayClientImplTest,AiUseCaseConfigServiceImplTest,UserSessionInterceptorTest,OpenAiResponsesClientImplTest,OpenRouterAiClientImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`。12 个测试类共 70 项，失败/错误/跳过均为 0。覆盖协议、超时回收、缓存、组合/会话校验、原值保存与运行继承、参数转义、缺省参数、HTTP 401 与业务错误 Result，以及既有 API 客户端回归。报告位于 asset-domain-system、asset-app 的 target/surefire-reports，可由上述命令重建。
- 前端：`node node_modules/@vue/cli-service/bin/vue-cli-service.js test:unit --runInBand tests/unit/views/SystemAiGateway.spec.js`，17 项通过；首次新增弹框测试因旧浅挂载表单缺少 clearValidate stub 失败，补齐测试夹具后通过，未为测试修改生产逻辑。`npm run lint` 与 `NODE_OPTIONS=--openssl-legacy-provider npm run build:prod` 均成功，使用已有依赖，未升级技术栈。
- 三仓库 `git diff --check` 通过，asset 既有暂存 .DS_Store 和复盘文件保留。asset 系统设计与迭代日志已更新并链接本目录，未复制规格。
- 未启动/重启应用、未执行真实模型推理或实际账号目录查询、未部署；真实账号目录和已登录弹框的加载/保存回读及视觉联调仍需加载新代码后完成，不记为已验收。测试只使用 mock、模拟进程/HTTP 服务，无业务数据写入。
