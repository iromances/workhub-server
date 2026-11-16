# Design：禅道Jenkins连接配置与MCP按需查询

> SDD 流程的 Design 模板。以本次 Proposal 和已核实的目标环境/分支事实为输入，可基于 Proposal 草案连续起草，随完整规格在实施前统一确认。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-0051c0a8-bfb5-4f4f-86b5-51e8ca12947e |
| 需求名称与唯一需求目录 | 禅道Jenkins连接配置与MCP按需查询；docs/业务线/BL000012-工作台/需求迭代/REQ-0051c0a8-bfb5-4f4f-86b5-51e8ca12947e-禅道Jenkins连接配置与MCP按需查询/ |
| 涉及仓库与功能域 | workhub-server：controller/system、service/system、service/intake（禅道客户端）、service/agent（Jenkins 客户端）、service/mcp、workhub-bootstrap（迁移）；workhub-web：系统集成页 |
| 所属项目或业务线（适用时） | BL000012-工作台 |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server@codex/development-20261102，2026-11-12 源码核对（路径见下文） |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 系统集成台账：`workhub-service/.../service/system/IntegrationCatalogService.java`（系统/能力 CRUD，配置组只统计状态不回值）、`IntegrationProbeService.java`（按 systemCode 分派 ZENTAO/JENKINS/GITLAB 只读探针）、`workhub-controller/.../controller/system/IntegrationController.java`（`/api/system/integrations`：GET 列表、GET/POST/PUT、`POST /{id}/probe`）。
  - 禅道：`workhub-service/.../service/intake/ZentaoClient.java`——配置组 `zentao.base`（baseUrl/account/password/defaultMapping）、`zentao.mapping.{业务线编码}`；`exchangeToken()`（POST /api.php/v1/tokens）、GET 通道（stories/executions/projects/builds）已在开源版 21.7.8 真实验证；无缺陷（bug）、任务（task）查询。
  - Jenkins：`workhub-service/.../service/agent/AgentJenkinsPolicy.java`——实例与 Job 绑定存于 sys_config `agent.jenkins/policy` JSON（Instance：id/name/baseUrl/userName/credentialGroup/tokenKey/enabled，≤50 实例；凭据在实例指定凭据组的 SECRET 配置，经 `requireSecretValue` 解密）；`AgentJenkinsClient.java`——Basic 认证 HTTP 客户端，已有 `probe/probeInstance/submit/queue/build/find/receipt`，无 Job 列表、构建日志查询。
  - MCP：`workhub-service/.../service/mcp/McpRuntimeService.java`——`registry()` 每请求组装 `McpToolRegistry.firstVersion` 并追加注册 intake/gitlab/业务日志/仓库文件工具；`toolNames()` 为数字员工 CLI 全量放开清单。`docs/mcp-server.md` 为能力文档。
  - 前端：`workhub-web/src/views/system/IntegrationView.vue` 详情弹框仅展示配置组状态并跳转系统配置页；`src/api/integration.ts`、`src/types/integration.ts`。
- 当前接口、数据及配置：凭据均在 `sys_config`（SECRET 值经 `SysConfigService.cryptoService` AES 加密，`resolvePlainValue` 服务端解密；响应只回 masked 值）；无新增表。
- 事实来源（路径、提交、环境或只读证据）：上述源码文件（本分支工作区）+ `V20261106_000001__add_integration_catalog.sql` 种子。
- 目标系统当前实现证据及本次重新核对结果：`SysConfigService.findPlainValue` 对 SECRET 类型解密返回（ZentaoClient 以其读密码，故 `zentao.base.password` 须为 SECRET 类型方可正常解密）；`AgentJenkinsClient.Target` 构造器自带 jobSegments 校验，可复用于任意只读 Job 路径。
- 尚未核实的假设：禅道 API 按产品/迭代过滤缺陷列表（`GET /api.php/v1/products/{id}/bugs`、`/executions/{id}/bugs`）及字段级检索参数在 21.7.8 的确切行为需实现时实测；实现时以"端点存在性 + 返回结构"实测为准，检索过滤不可用时降级为客户端关键词过滤（分页扫描有界）。

## 2. 系统架构

- 总体方案和设计目标：
  1. 凭据配置面：在既有集成台账上为 ZENTAO/JENKINS 增加结构化凭据查询/保存接口，全部落既有 `sys_config` 存储，不新增表；Jenkins 实例表单写回 `agent.jenkins/policy` 的 instances 段并保留 bindings 段。
  2. MCP 查询面：新增 `IntegrationQueryService`（workhub-service）承载禅道/Jenkins 只读查询与 MCP 审计，`McpRuntimeService` 注册 9 个只读工具；凭据仅在服务端解密使用，任何响应不携带凭据。
- 主要流程、数据流和状态流：
  - 保存流：前端表单 → `PUT /{id}/config` → 校验（必填、实例数上限、bindings 引用）→ 写 sys_config（SECRET 加密）/更新 policy JSON → `AgentJenkinsPolicy.current()` 结构复核 → 系统审计。
  - 查询流：AI → `/api/mcp/runtime` tools/call → 工具处理器 → `IntegrationQueryService`（读配置、换 Token/取凭据、调用只读 API、裁剪与脱敏、MCP 审计）→ 结果文本。
- 前端页面、交互与后端规则边界：前端仅做表单展示与提交（密码/Token 输入框留空=不变、显示"已配置"徽标）；必填校验、引用校验、加密、审计全部在后端。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| IntegrationController（新增 GET/PUT `/{id}/config`） | 凭据表单读写入口、权限 | 加密细节、Jenkins policy 结构 | JSON 请求/响应 | IntegrationCatalogService、新增凭据保存逻辑 |
| ZentaoClient（扩展） | 只读：换 Token、缺陷检索/详情、需求详情、任务详情、迭代详情 | 写操作（既有 createTestTask 不动） | 方法调用 → Map/record | SysConfigService、HttpClient |
| AgentJenkinsClient（扩展） | 只读：Job 列表/详情、构建详情、构建日志尾读 | 触发构建 | Target → JsonNode/record | 凭据经调用方传入 |
| IntegrationQueryService（新增） | MCP 面向的禅道/Jenkins 查询编排：解析实例/默认实例、参数边界、结果裁剪、MCP 审计 | 配置写入、权限判断 | MCP 工具参数 → Map | ZentaoClient、AgentJenkinsPolicy/Client、SysConfigService、McpAuditLogger |
| McpRuntimeService（注册） | 9 个只读工具的 schema 与分发 | 业务查询逻辑 | JSON-RPC | IntegrationQueryService |
| workhub-web IntegrationView | ZENTAO/JENKINS 凭据表单、状态徽标 | 后端校验替代 | REST | api/integration.ts |

## 3. 契约与数据模型

- API/事件提供方与消费者：新接口由登录用户（前端）消费；MCP 工具由 AI 客户端（含数字员工 CLI）消费。
- 新增、修改或废弃的契约及权威文件路径：
  - `GET /api/system/integrations/{id}/config`（权限 `system:integration:view`）→
    `{"systemCode":"ZENTAO","zentao":{"baseUrl":..., "account":..., "passwordConfigured":true},"jenkins":null}`；
    JENKINS 时 `jenkins.instances":[{"id","name","baseUrl","userName","enabled","tokenConfigured"}]`，`zentao` 为 null。响应永不含明文密码/Token。
  - `PUT /api/system/integrations/{id}/config`（权限 `system:integration:update`）请求体仅含对应系统段：
    - ZENTAO：`{"zentao":{"baseUrl":"必填","account":"必填","password":null}}`——`password` 空白/缺省=保留既有值；非空=加密覆盖。未配置过密码且留空则报错。
    - JENKINS：`{"jenkins":{"instances":[{"id":"必填唯一","name":"必填","baseUrl":"必填","userName":"必填","apiToken":null,"enabled":true}]}}`——`apiToken` 空白=保留既有凭据（新实例/无既有凭据时必须非空）；新增实例自动分配凭据组 `jenkins.credential.{id}`、tokenKey `apiToken`；instances 覆盖 policy.instances，bindings 原样保留；删除被 bindings 引用的实例报错；实例数沿用 ≤50 上限。
    - 保存成功返回与 GET 相同结构（脱敏）。
  - MCP 工具（全部只读，名称与参数）：
    | 工具 | 必填参数 | 可选参数 | 说明 |
    | --- | --- | --- | --- |
    | `search_zentao_bugs` | 无 | productId、executionId、keyword、limit | 有 productId/executionId 走对应列表端点，否则全局列表；keyword 为标题/关键词客户端过滤；默认 20 条、上限 50 |
    | `get_zentao_bug` | bugId | 无 | 缺陷详情 |
    | `get_zentao_story` | storyId | 无 | 需求详情 |
    | `get_zentao_task` | taskId | 无 | 任务详情 |
    | `get_zentao_execution` | executionId | 无 | 迭代详情（含所属项目/产品） |
    | `list_jenkins_jobs` | 无 | instanceId、folder、limit | 列出实例（默认首个启用实例）顶层或指定 folder 下 Job；默认 50、上限 200 |
    | `get_jenkins_job` | jobPath | instanceId | Job 详情（可构建性、健康度、最近构建号） |
    | `get_jenkins_build` | jobPath、buildNumber | instanceId | 构建状态（result、duration、参数、时间戳） |
    | `get_jenkins_build_log` | jobPath、buildNumber | instanceId、lines、maxBytes | 控制台日志尾读；默认 200 行/64 KiB，上限 2000 行/256 KiB |
    - `jobPath` 形如 `folder/job`（斜杠分隔段），经 `AgentJenkinsClient.Target` 既有段落校验；数字参数按 MCP 契约用整数字符串，非法/零/负拒绝，超上限收敛。
- 请求、响应、字段语义、默认值/空值、错误码和状态：保存接口校验失败返回 400 与中文消息（沿用全局异常处理）；MCP 工具失败经既有 `isError` 文本通道返回中文错误（配置缺失/认证失败/目标不存在/越界）。
- 幂等、并发、重试和重复执行行为：保存为全量覆盖式（最后写入生效），PUT 幂等；MCP 查询天然只读可重试；禅道 Token 每次查询现取现用（与既有 401 重试一致）。
- 数据归属、关键约束、迁移及历史兼容：数据仍在 `sys_config`；`agent.jenkins/policy` 结构不变（instances 字段集与现状一致，不新增字段）；无表结构变更。
- 数据库变更的脚本落点、执行方式、顺序、可重复性和回滚：新增迁移 `workhub-bootstrap/src/main/resources/db/schema/mysql/V20261112_180000__add_integration_read_query_capabilities.sql`：向 `sys_integration_capability` 幂等补插 ZENTAO/JENKINS 新只读查询能力行（READ/ACTIVE，`WHERE NOT EXISTS` 防重）。`zentao.base.password` 的 SECRET 类型转换不在 SQL 中执行（SQL 无法用主密钥加密存量明文），由集成页首次填写密码保存时经 `SysConfigService` 自然转换为加密存储；存量 TEXT 明文在转换前仍可正常读取。回滚：能力行删除不影响运行，凭据值更新不可自动回滚（依赖审计快照人工恢复）。
- 关键业务数据的来源、口径与质量边界：凭据来源系统集成页人工维护；禅道/Jenkins 返回数据按目标系统实时口径，条数/字节边界内不保证完整（truncated 标记）。

## 4. 非功能约束

- 安全与权限、授权边界和测试数据处理：
  - 凭据加密：沿用 `SysConfigService` SECRET 通道（既有主密钥）；GET/PUT 响应不回显明文；测试连接（既有探针）不回传凭据。
  - MCP 工具不提供配置写入；AI 不能经 MCP 提供地址/凭据/触发参数；只读查询不改变禅道/Jenkins 任何状态。
  - 权限：配置读 `system:integration:view`、写 `system:integration:update`；MCP 端点维持既有 `ROLE_MCP` Token 鉴权。
  - 审计：凭据保存经 `SystemAuditService.operation`（记录字段键与状态，不含凭据值）；MCP 查询经 `McpAuditLogger`（工具名、目标实例/系统、参数摘要、结果状态与耗时，不含返回数据与凭据）。
  - 输出脱敏：查询结果复用既有脱敏策略（禅道/Jenkins 域数据不含凭据；日志尾读经 `AgentJenkinsClient` 既有通道，发现常见敏感串按既有口径处理，实施时按仓库代码风格与既有脱敏实现对齐）。
- 性能、容量和时效：HTTP 超时沿用客户端既有值（禅道 20s、Jenkins 30s 连接 15s）；列表/日志边界见契约表；禅道检索最多扫描 5 页（每页 100）后停止。
- 失败处理、降级与补偿：配置缺失/认证失败/目标 404 返回明确中文错误；不重试写语义（无写操作）；禅道 401 重换 Token 一次重试（与既有一致）。
- 日志、监控、告警与审计：MCP 审计文件 `logs/mcp-audit.jsonl`（既有）；不新增告警。

## 5. 交付与回滚

- 影响系统和前后端/数据库发布顺序：先执行迁移（仅能力目录与配置类型修正，可重复），再发后端，最后发前端；前端未发时新接口不影响既有页面。
- 灰度或开关：无开关；MCP 工具在未配置禅道/Jenkins 凭据时调用即报"未配置"，天然灰度。
- 数据备份与前后校验：保存前在审计快照中记录 policy/配置键变更前状态（不含凭据值）；无批量数据迁移。
- 回滚条件、步骤与不可逆部分：应用回退即恢复旧行为；能力目录迁移无破坏性；凭据值更新不可自动回滚（依赖审计快照人工恢复）。
- 生效并核实后需要更新的目标项目系统设计、迭代日志、需求及专项文档内容与证据：`docs/mcp-server.md`（工具清单、安全边界补充禅道/Jenkins 只读工具）、`docs/业务线/BL000012-工作台/迭代日志.md`、本目录 tasks.md 证据。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → §2 凭据配置面、§3 配置接口契约；目标 2 → §2 MCP 查询面、§3 MCP 工具契约。
- 明确非范围是否被保持：不开放写操作（§2 工具全部只读）；Job 绑定不表单化（§3 PUT 仅覆盖 instances）；其他系统不做表单。
- 待确认问题及影响：禅道列表过滤端点在 21.7.8 的行为（§1 假设）——实现时实测，必要时降级为客户端过滤，不影响验收标准。
- 契约、ADR 及 Tasks 的关联链接：契约见 §3；无 ADR（无争议性架构决策）；Tasks 同目录 tasks.md。
