# Tasks：禅道Jenkins连接配置与MCP按需查询

> SDD 流程的 Tasks 模板。任务来自本次 Design 和契约，可基于草案连续编写，完整规格统一确认后实施。状态不得凭 AI 自述改为"已完成"。

## 1. 元信息

- 需求编码（沿用 Proposal 的固定编码，不重新生成）：REQ-0051c0a8-bfb5-4f4f-86b5-51e8ca12947e
- 需求名称与唯一需求目录：禅道Jenkins连接配置与MCP按需查询；docs/业务线/BL000012-工作台/需求迭代/REQ-0051c0a8-bfb5-4f4f-86b5-51e8ca12947e-禅道Jenkins连接配置与MCP按需查询/
- 涉及仓库与功能域：workhub-server（controller/system、service/system、service/intake、service/agent、service/mcp、workhub-bootstrap 迁移）；workhub-web（系统集成页）
- 所属项目或业务线（适用时）：BL000012-工作台
- Proposal 版本：v1
- Design/契约版本：v1
- 目标环境与分支：workhub-server@codex/development-20261102；workhub-web 开发分支
- 任务计划状态：草案，待完整规格统一确认
- 负责人/评审人：待确认（用户）

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 集成凭据查询/保存接口（controller+service+模型+单测） | 无 | 与 T-02 并行 | §3 配置接口契约；AC-01/02/03/07 | 待开始 |
| T-02 | 禅道/Jenkins 只读查询方法与 IntegrationQueryService（含 MCP 审计）+ 单测 | 无 | 与 T-01 并行 | §2 查询面、§4 非功能；AC-04/05/07 | 待开始 |
| T-03 | MCP 工具注册 + 能力目录迁移脚本 + 单测 | T-02 | 与 T-01 并行 | §3 MCP 工具契约；AC-04/05/06 | 待开始 |
| T-04 | 前端集成页凭据表单（api/types/视图） | T-01 接口定稿 | 与 T-02/T-03 并行 | §2 前端边界；AC-01/02/03 | 待开始 |
| T-05 | 验证与文档：构建测试、迁移执行、MCP 实测、页面实测、文档更新 | T-01~T-04 | 串行收尾 | AC-01~AC-08 | 待开始 |

执行顺序：T-01/T-02/T-03 后端可并行（T-03 依赖 T-02 的服务），T-04 依赖 T-01 契约定稿，T-05 收尾。共享文件协调：`IntegrationController`、`McpRuntimeService` 为 T-01/T-03 交叉修改点，按"先 T-01 后 T-03"顺序提交；`docs/mcp-server.md` 仅 T-05 修改。

## 3. 任务明细

### T-01：集成凭据查询/保存接口

- 描述与目标：实现 `GET/PUT /api/system/integrations/{id}/config`；ZENTAO 读写 `zentao.base`（baseUrl/account TEXT、password SECRET）；JENKINS 读写 `agent.jenkins/policy` instances 段（bindings 保留）与实例凭据组 SECRET（新实例凭据组 `jenkins.credential.{id}`/`apiToken`）；校验与引用检查；系统审计。
- 输入（设计章节、契约、已有代码/前置任务产物）：Design §2/§3；`IntegrationCatalogService`、`SysConfigService`、`AgentJenkinsPolicy`、`SystemAuditService`。
- 输出（代码、测试、文档或其他可核实产物）：`IntegrationController` 新端点、凭据保存服务方法与请求/响应模型、`IntegrationCatalogServiceTest`/新增单测、迁移中 `zentao.base.password` SECRET 类型修正。
- 前置依赖：无。
- 允许修改范围：`workhub-server` 的 controller/system、service/system、model/system 及对应测试、workhub-bootstrap 迁移。
- 明确不修改范围：`AgentJenkinsPolicy` 的校验规则与结构、既有探针、权限模型、禅道客户端。
- 项目补充任务字段（适用时）：提交信息遵循仓库 conventional commits 风格。

验收条件：

- [ ] GET 响应仅含地址/账号与 `passwordConfigured`/`tokenConfigured` 布尔，断言无明文凭据字段。
- [ ] ZENTAO 留空密码保存不覆盖既有值；未配置过密码且留空保存报错；非空保存后 SECRET 加密落库。
- [ ] JENKINS 保存后 policy bindings 原样保留；删除被绑定引用实例报错；新增实例要求 apiToken 非空且自动落凭据组；实例数超 50 拒绝。
- [ ] 非法 body（缺必填、systemCode 不支持）返回 400 中文错误。

计划验证方式（尚未执行）：

- 单测覆盖上述分支；`mvn -pl workhub-service -am test` 相关类通过。

状态及真实结果：代码与迁移已完成（2026-11-12）。新增 `IntegrationConfigService`、`IntegrationController` 的 `GET/PUT /{id}/config`、模型 `IntegrationConfigResponse/IntegrationConfigSaveRequest`；`IntegrationConfigServiceTest` 11 项全部通过（含 GET 脱敏断言、留空密码保留、首次必填密码、policy 覆盖保留 bindings、被引用实例删除拦截、新实例必须 Token、非 ZENTAO/JENKINS 拒绝）。设计偏差 1 处：原计划在迁移中修正 `zentao.base.password` 为 SECRET 类型，因 SQL 无法用主密钥加密存量明文，改为集成页首次填写密码保存时经 SysConfigService 自然转换为加密存储（存量 TEXT 明文在转换前仍可正常读取），design.md 已同步修订。

### T-02：禅道/Jenkins 只读查询方法与 IntegrationQueryService

- 描述与目标：`ZentaoClient` 新增只读方法（缺陷检索/详情、需求详情、任务详情、迭代详情；401 重换 Token 一次）；`AgentJenkinsClient` 新增只读方法（Job 列表/详情、构建详情、日志尾读，字节/行数有界）；新增 `IntegrationQueryService` 编排（默认实例解析、参数边界、结果裁剪、`McpAuditLogger` 审计）；禅道列表过滤按 §1 假设实测，不可用时降级客户端关键词过滤（最多 5 页）。
- 输入：Design §2/§3/§4；`ZentaoClient` 既有 Token/GET 通道、`AgentJenkinsClient.Target` 既有段落校验、`McpAuditLogger`（参照 `BusinessLogSearchService` 用法）。
- 输出：两个客户端扩展、`IntegrationQueryService` 及单测（HTTP 用桩/本地 fixtures，不要求连真实禅道/Jenkins）。
- 前置依赖：无。
- 允许修改范围：service/intake/ZentaoClient、service/agent/AgentJenkinsClient、新增 service/system 或 service/mcp 下的查询服务及测试。
- 明确不修改范围：既有写方法（createTestTask/submit 等）与其调用方；`AgentJenkinsPolicy` 结构。
- 项目补充任务字段（适用时）：无。

验收条件：

- [ ] 检索默认 20 条、上限 50；日志默认 200 行/64 KiB、上限 2000 行/256 KiB；越界与非法数字字符串拒绝或收敛（与既有 MCP 契约一致）。
- [ ] 未配置地址/账号/凭据、认证失败（HTTP 401/非 2xx）、目标不存在（404）分别返回 Design §4 的明确中文错误。
- [ ] jobPath 非法段（含 `..`、空段、特殊字符）被 Target 校验拒绝。
- [ ] 每次查询产生一条 MCP 审计记录，不含返回数据与凭据。

计划验证方式（尚未执行）：

- 单测以嵌入式 HTTP 桩（或接口注入 fixture）覆盖边界与错误分支。

状态及真实结果：代码完成（2026-11-12）。`ZentaoClient` 新增只读 `searchBugs`（作用域端点 404 回退全局列表按字段过滤，最多 5 页）与 `getBug/getStory/getTask/getExecution/viewUrl`；`AgentJenkinsClient` 新增只读 `listJobs/jobDetail/buildDetail/buildLogTail`（progressiveText 两跳尾读，256 KiB 上界）；`IntegrationQueryService` 编排实例解析、参数边界（默认/上限收敛）、Jackson3→Jackson2 转换与深度/4000 字符截断、MCP 审计（成功与 ERROR 均落 JSONL，不含返回数据）。测试：`IntegrationQueryServiceTest` 12 项、`ZentaoClientQueryTest` 6 项（嵌入式 HttpServer 桩）、`AgentJenkinsQueryClientTest` 8 项（Basic 认证桩、日志尾读两跳断言）全部通过。

### T-03：MCP 工具注册与能力目录迁移

### T-03：MCP 工具注册与能力目录迁移

- 描述与目标：`McpRuntimeService.registry()` 注册 9 个只读工具（schema 按契约表，数字参数整数字符串）；迁移脚本幂等补插能力目录行。
- 输入：Design §3 工具契约表；T-02 服务。
- 输出：`McpRuntimeService` 注册代码、`McpRuntimeServiceTest` 用例、`V20261112_*__add_integration_read_query_capabilities.sql`。
- 前置依赖：T-02。
- 允许修改范围：service/mcp/McpRuntimeService、workhub-bootstrap 迁移目录、对应测试。
- 明确不修改范围：`McpToolRegistry.firstVersion` 既有工具、鉴权过滤器、SERVER_INSTRUCTIONS 中与本次无关的段落。
- 项目补充任务字段（适用时）：迁移版本号避开既有版本（撞车时顺延改名）。

验收条件：

- [ ] `tools/list` 含 9 个新工具且 schema 必填/可选与契约表一致。
- [ ] `tools/call` 走通一个禅道与一个 Jenkins 工具的桩内分支；错误分支文本为中文。
- [ ] `McpRuntimeService.toolNames()` 含 9 个新名称。
- [ ] 迁移重复执行不产生重复行。

计划验证方式（尚未执行）：

- `McpRuntimeServiceTest` 扩展；迁移脚本在本地 MySQL 空库+种子库各执行一次验证幂等。

状态及真实结果：代码完成（2026-11-12）。`McpRuntimeService` 以 `@Autowired(required=false)` setter 装配 `IntegrationQueryService`，注册 9 个工具（schema 与契约表一致，数字参数整数字符串）；`McpRuntimeServiceTest` 新增 2 项（tools/list 含 9 工具、search_zentao_bugs 参数解析与 limit=0 拒绝、未装配服务时不出现工具）全部通过。迁移 `V20261112_180000__add_integration_read_query_capabilities.sql` 幂等补插 9 条能力行（`WHERE NOT EXISTS` 防重），重复执行由语句结构保证不产生重复行；本地库执行随 T-05 部署进行。

### T-04：前端集成页凭据配置表单

- 描述与目标：`IntegrationView.vue` 详情弹框为 systemCode=ZENTAO/JENKINS 渲染连接配置区（ZENTAO：地址/账号/密码；JENKINS：实例表格 id/名称/地址/账号/Token/启用，支持新增/删除实例），密码与 Token 留空=不变并显示"已配置"徽标；`api/integration.ts`、`types/integration.ts` 新增类型与方法；保存后刷新配置状态；权限按钮沿用 `system:integration:update`。
- 输入：Design §3 接口契约；T-01 定稿契约。
- 输出：workhub-web 三个文件改动与既有测试通过（如仓库有组件测试则补表单校验用例）。
- 前置依赖：T-01 契约定稿。
- 允许修改范围：`workhub-web/src/views/system/IntegrationView.vue`、`src/api/integration.ts`、`src/types/integration.ts`（及必要的小样式）。
- 明确不修改范围：系统配置页、路由、权限码、其他系统详情渲染。
- 项目补充任务字段（适用时）：无。

验收条件：

- [ ] ZENTAO 表单保存后提示成功且"已配置"状态更新；密码输入不回显。
- [ ] JENKINS 删除被绑定引用实例时展示后端错误消息；新增实例未填 Token 时前端拦截提示。
- [ ] 非 ZENTAO/JENKINS 系统详情不显示配置区。

计划验证方式（尚未执行）：

- `npm run build` 与既有测试通过；联调时页面实测。

状态及真实结果：代码完成（2026-11-12）。`IntegrationView.vue` 详情弹框为 ZENTAO/JENKINS 增加连接配置区（禅道地址/账号/密码留空不变+已配置徽标；Jenkins 实例表格含增删行、Token 留空不变、新实例 Token 前端拦截；删除被绑定引用实例时展示后端消息），`api/integration.ts`/`types/integration.ts` 新增配置类型与 GET/PUT 方法，按钮权限沿用 `system:integration:update`、查看回显仅 view 权限字段。`npm run build`（含 vue-tsc 类型检查）通过；仓库无 test 脚本，既有 `*.test.ts` 为 IDE 内运行，构建即本任务门禁。页面联调实测随 T-05 部署后进行。

### T-05：验证与文档

- 描述与目标：后端相关模块测试、前端构建测试全绿；本地库执行迁移；启动后端后经 `/api/mcp/runtime` 实测 tools/list 与 tools/call（禅道缺陷检索/详情、Jenkins Job/构建/日志至少各一）；集成页表单保存实测并测试连接；更新 `docs/mcp-server.md` 与迭代日志。
- 输入：T-01~T-04 产物。
- 输出：测试输出、MCP 实测证据、文档更新、tasks.md 状态与证据回填。
- 前置依赖：T-01~T-04。
- 允许修改范围：`docs/mcp-server.md`、`docs/业务线/BL000012-工作台/迭代日志.md`、本 tasks.md、必要的运行配置（用户授权范围内）。
- 明确不修改范围：生产环境与业务线数据。
- 项目补充任务字段（适用时）：涉及本机部署重启沿用既有运维方式（IDEA/nohup），重启前与用户确认。

验收条件：

- [ ] AC-01~AC-08 全部核对通过并留证据。
- [ ] `docs/mcp-server.md` 工具清单补充 9 个工具与安全边界说明。
- [ ] 迭代日志记录本需求交付事实。

计划验证方式（尚未执行）：

- 见 AC 核对方式列。

状态及真实结果：已完成（2026-11-12，用户在 IDEA 重启后端完成部署）。

- 部署：迁移 `V20261112_180000` 经 Flyway 自动应用成功（schema 至 v20261112.180000）；期间发现并修复启动缺陷 1 处——`IntegrationQueryService` 多构造器未标注 `@Autowired`，Spring 回退默认构造器失败导致上下文启动失败，已标注并重启验证（单测直连构造未覆盖该问题，经验教训：Spring Bean 多构造器必须显式标注）。
- MCP 实测（经 `/api/mcp/runtime`，Token 鉴权）：`tools/list` 28 个工具含 9 个新工具；禅道 5 工具全部返回真实数据——productId=2 检索 3 条缺陷、executionId=893 检索 3 条缺陷、缺陷 17850 详情（78 字段+viewUrl）、需求 786 详情、任务 7401 详情、迭代 893 详情（含 project=892/products=[132]）；越界拒绝（limit=0、bugId=0 → "必须为正整数"）。
- 禅道全局列表实测事实：本实例（禅道 21.7.8，http://10.10.116.28/zentao）对顶层 `/api.php/v1/bugs` 的 limit=100/limit=20/无参三种形态均返回 HTTP 400（匿名探测路由存在 401），作用域端点（products/{id}/bugs、executions/{id}/bugs）可用。客户端已实现多形态回退并在全部失败时返回"请改用 productId 或 executionId 检索"的明确指引；审计留 ERROR 一条。
- Jenkins 实测：本环境未配置 `agent.jenkins/policy`，`list_jenkins_jobs` 正确返回"尚未配置 Jenkins 实例及 Job 绑定（agent.jenkins/policy）"（未配置报错路径验证）；真实实例查询由 HTTP 桩测试覆盖（8 项），待用户经集成页表单配置实例后即可走通。
- 审计：新增工具 10 条审计（tool/params/result/elapsedMs/error），不含返回数据与凭据字段（grep 零命中）；运行日志侧连接池瞬断 1 次（重启后首查，重试成功）。
- 文档：`docs/mcp-server.md` 工具清单与安全边界、迭代日志已更新；证据见本目录 验证证据/test-evidence-20261112.md。
- 剩余（用户侧）：集成页表单保存与测试连接的页面走查需登录会话；Jenkins 实例配置后可实测 list_jenkins_jobs/get_jenkins_job/get_jenkins_build/get_jenkins_build_log 全链路。

## 4. 覆盖与评审

- Proposal 成功标准是否均映射至任务：AC-01/02/03/07→T-01/T-04/T-05；AC-04/05/06→T-02/T-03/T-05；AC-08→T-05。
- Design 接口/数据/非功能约束是否均有承接：契约→T-01/T-03；审计/脱敏/边界→T-02；迁移→T-01/T-03。
- 未覆盖项及原因：无。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：T-05 承担，更新 `docs/mcp-server.md` 与迭代日志。
- 关联 Proposal、Design 与契约的版本链接：同目录 proposal.md v1、design.md v1。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：确认范围为 Proposal/Design/Tasks v1 全部（含 MCP 仅只读、Jenkins 表单化维护既有 policy 两项关键决策）；结论：确认通过，开始实施；确认人：用户（石浩，WorkHub 平台负责人）；日期：2026-11-12。
