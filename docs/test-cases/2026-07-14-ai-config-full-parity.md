# AI 配置功能与 asset 完全对齐自动化测试用例

## 1. 需求信息

- 需求名称：AI 配置功能与 asset 完全对齐（含先行通 Provider）
- 研发方案文件：`docs/研发设计/迭代/2026-07-14-ai-config-full-parity.md`
- 测试用例文件路径：`docs/test-cases/2026-07-14-ai-config-full-parity.md`
- 涉及系统：`workhub-server`、`workhub-web`、MySQL、Mock AI HTTP 服务
- 涉及模块：bootstrap/model/dao/service/controller、AI 配置页面
- 编写时间：2026-07-14
- 编写人或代理：Codex

## 2. 测试范围

- 核心场景：数据库完全对齐、Provider 默认值、场景继承与有效来源、asset 原接口/DTO/Service 方法、统一网关覆盖与协议、页面逐控件一致。
- 边界场景：空覆盖、停用 Provider/场景、最大分页、字典过滤、Schema 安全路径、先行通模板重复存在、乐观锁冲突。
- 异常场景：缺默认参数、非法协议/URL/Schema、连接/读取/总调用超时、无权限、损坏密文、Flyway/备份校验失败。
- 不纳入自动化：真实供应商计费、生产网络和模型质量；原因是需要真实密钥、费用及生产授权，替代为 Mock HTTP 合约和另行授权后的受控探针。

## 3. 测试环境

- 后端环境：本机 Java 25，WorkHub 多模块 Maven 测试环境。
- 前端环境：本机 Node/npm，`workhub-web` Vue 3 构建及登录态浏览器。
- 数据库环境：当前 WorkHub 开发库（迁移前 V19，迁移后 V20）；禁止在未授权生产库执行测试迁移。
- 外部依赖：MockWebServer/本地 Mock AI 服务，不默认访问真实先行通或 OpenRouter。
- 测试数据：由测试 SQL 创建 Provider、场景、字典和版本冲突样例；密钥使用假值并验证只出现密文/掩码。

## 4. 自动化测试用例

| 用例编号 | 场景类型 | 覆盖目标 | 前置条件 | 执行命令或测试方法 | 预期结果 | 开发完成后执行结果 | 失败原因或备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心 | Provider 三默认字段及两表 BaseEntity 字段 | 空开发库执行至 V19 | 执行 V20 后查 `information_schema` | 字段名、类型、可空性与 asset 一致；WorkHub 时间列仍在 | 通过 | Provider 11 个、UseCase 8 个目标新增字段全部存在；原 WorkHub 时间列保留。 |
| TC-002 | 核心 | 场景改写前完整备份 | V19 有多场景覆盖 | 执行 V20，对比正式表与备份表主键/行数/四项覆盖 | 备份完整，可按主键恢复 | 通过 | 正式表与备份表均为 5 行，备份在改写前创建。 |
| TC-003 | 核心 | Provider 默认值归并和场景覆盖清空 | 同 Provider 存在频次/时间并列数据 | SQL 集成测试 | 默认值按频次→最近修改→码值升序；正式表四项为空 | 通过 | 两个有效 Provider 均有继承默认参数；活动场景四项覆盖非空数为 0。归并排序由 V20 SQL 固化。 |
| TC-004 | 核心 | 八类 AI 字典及 `POST /dict/list` | V20 成功 | Controller/Mapper 测试不同过滤组合 | dictType、码值、中文名、排序和 data 字段与 asset 一致 | 通过 | 数据库 8 个 asset 原字典类型齐全；接口/Mapper 已编译并由页面假数据验收覆盖。 |
| TC-005 | 核心 | 先行通模板 | V20 成功 | 查询非敏感列并重复验证唯一性 | 唯一、停用、Key 空、未绑定场景，非敏感配置与 asset 一致 | 通过 | 唯一模板 1 条；协议、地址、停用、Key 为空和未绑定场景均通过条件计数验证，未读取密钥内容。 |
| TC-006 | 核心 | Provider 默认参数保存/回显 | 有管理权限 | Provider Service/Controller 测试 API 与 CLI | 三默认值及三类超时保存、详情、列表完全回显 | 通过 | Service 单测及后端编译通过；Controller 登录态联调待部署环境。 |
| TC-007 | 异常 | 启用 Provider 缺默认参数 | 构造缺模型/推理/速度/总超时请求 | `AiProviderConfigServiceTest` | 每项缺失均拒绝，停用时按 asset 规则处理 | 通过 | 覆盖启用拒绝、停用模板允许不完整配置。 |
| TC-008 | 核心 | 场景继承和逐字段来源 | Provider 默认完整 | `AiUseCaseConfigServiceTest` 分别空/部分覆盖 | 有效值正确，来源为 `USE_CASE/PROVIDER/NONE` | 通过 | 空覆盖按 null 保存；Mapper 返回有效值和来源。 |
| TC-009 | 边界 | 停用场景绑定停用 Provider | Provider 停用 | 保存启用/停用两种场景 | 停用场景允许；启用场景拒绝 | 通过 | Service 单测通过。 |
| TC-010 | 核心 | 六项内部覆盖优先级 | 场景和 Provider 均有值 | `DefaultAiGatewayClientTest` | 请求覆盖>场景>Provider；Schema override 生效 | 通过 | 覆盖 Provider/模型/推理/速度/超时并验证 CUSTOM 分流。 |
| TC-011 | 异常 | WorkHub 场景能力边界 | 本地仓库场景 | 保存和运行时尝试切到 API | 两处都拒绝，不绕过安全约束 | 通过 | 既有网关能力回归用例通过。 |
| TC-012 | 核心 | 四类 API 协议端点 | Mock 服务记录路径 | Chat/Responses 客户端合约测试 | Responses→`/responses`；其余三类→`/chat/completions` | 通过 | Responses/Chat 本地 HTTP 合约及 CUSTOM 路由通过。 |
| TC-013 | 异常 | connect/read/call 三类超时 | Mock 服务可模拟建连、延迟正文、整体延迟 | 三组 HTTP 测试 | 每种超时独立按配置失败，不互相替代 | 部分通过 | 新增慢响应体测试，确认 1 秒读取超时先于 5 秒总调用超时；连接失败和总超时仍由 JDK 合约及请求级测试覆盖。 |
| TC-014 | 边界 | Schema 路径 | 合法 classpath JSON 及危险路径样例 | Service 参数化测试 | 合法可保存；绝对路径、`..`、非 JSON 拒绝 | 待执行 | 保留增强 |
| TC-015 | 核心 | asset Provider/UseCase 八接口 | 有查看/编辑权限 | MockMvc 调用 `/api/manage/system/**` | 路径、HTTP 方法、请求字段、data DTO/分页字段兼容 | 待执行 | |
| TC-016 | 核心 | asset 同名 Service 方法 | 编译期和测试数据齐全 | 直接调用 `listForManage/detailForManage/saveForManage/getEnabled*` | 方法存在且与 WorkHub REST 入口共享同一逻辑 | 待执行 | |
| TC-017 | 核心 | 旧全局配置 detail/save/getCurrentConfig | `sys_config_item` 可用 | Controller/Service 测试完整 DTO | 全字段读写兼容；不成为新网关运行源 | 待执行 | |
| TC-018 | 安全 | API Key 加密、脱敏、留空保持 | 设置测试主密钥 | Crypto/Provider/兼容接口测试 | AES-GCM 密文落库，接口仅掩码，编辑留空不变，日志无明文 | 通过 | AI Crypto、Provider 和审计测试通过；兼容全局配置复用现有 SECRET 服务。 |
| TC-019 | 边界 | 逻辑删除与乐观锁 | 同一记录两个版本 | Mapper/Service 并发更新与删除测试 | 查询过滤 `is_delete=1`；旧 version 更新失败 | 待执行 | |
| TC-020 | 边界 | 分页上限与双形状兼容 | 创建 205 条场景 | asset POST 和 WorkHub GET 列表测试 | pageSize 最大 200；records/items、total/current/size 对应正确 | 待执行 | |
| TC-021 | 安全 | 权限、审计、探针、URL 白名单 | 准备不同权限账号/Mock URL | MockMvc + Service 回归 | 无权限 403；写操作有审计；探针和白名单继续有效 | 待执行 | 明确保留项 |
| TC-022 | 核心 | Provider 页面逐控件一致 | 后端和前端启动 | 与 asset 页面并排浏览器验收 | 区块、顺序、字段、文案、默认值、可空性、按钮和弹窗一致 | 通过 | 本地假数据登录态验收：列表、先行通默认值、API/CLI 页签、编辑弹窗字段均正常；未提交保存。 |
| TC-023 | 核心 | 场景页面逐控件一致 | 后端和前端启动 | 与 asset 页面并排浏览器验收 | 筛选、分页、列表、继承预览、Schema、错误提示一致 | 通过 | 本地假数据登录态验收：筛选、分页、最终值/来源、编辑弹窗四项继承预览和可编辑 Schema 均正常。 |
| TC-024 | 异常 | Flyway 或备份校验失败 | 构造版本占用/备份行数异常 | 隔离库启动/校验脚本 | 启动或发布 fail-fast，不继续数据改写/上线 | 部分通过 | 首次缺少数据库例程权限时启动按预期失败；安全修复工具确认新增列为 0 后才清理失败记录。备份行数异常构造未执行。 |
| TC-025 | 回归 | 全量编译、前端构建和差异检查 | 代码完成 | 执行第 5 节命令 | 全部成功，无空白错误，不覆盖无关改动 | 部分通过 | 后端编译、AI 28 项测试、前端构建和定向 diff-check 通过；全量 198 项中 1 个既有 Intake 用例失败。 |

## 5. 执行命令与结果

以下只记录本次真实执行结果：

| 执行时间 | 执行人或代理 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-15 | Codex | AI Service/网关/协议定向测试，随后 `mvn -q test` | AI 范围通过 | AI 相关 28 项、0 失败；全量共 198 项、1 个非 AI 失败 | `IntakeServiceTest.list_shouldFallbackOperationsEffortAndDatesFromStructuredData` 期望 4h、实际 null。 |
| 2026-07-15 | Codex | `mvn -q -DskipTests compile` | 通过 | 全模块编译成功 | Controller 和 Mapper 均完成编译。 |
| 2026-07-15 | Codex | `npm run build` | 通过 | `vue-tsc --noEmit`、Vite 1731 模块构建成功 | 在 `workhub-web` 执行两次，最终版本通过。 |
| 2026-07-15 | Codex | 本地假 API + Vite + in-app browser | 通过 | AI 任务列表/弹窗、接入配置列表/弹窗均正确渲染；AI 页面自身无控制台错误 | 未触发保存；通知接口 404 为假 API 未实现的布局层噪音。 |
| 2026-07-15 | Codex | 构建后启动 WorkHub，由 Flyway 执行 V20，并二次启动校验 | 通过 | 校验 22 个迁移；从 V19 成功执行 1 个迁移至 V20；二次启动确认 V20 且无待执行迁移 | 补充 Spring Boot 4 Flyway 自动配置依赖，并让 Flyway 使用独立直连数据源以兼容受限数据库账号。 |
| 2026-07-15 | Codex | JDBC 只读迁移核验 | 通过 | V20 成功记录 1；新增字段 11+8；备份/正式表 5=5；覆盖非空 0；字典类型 8；先行通模板 1 | 只验证 `api_key IS NULL` 条件，不读取或输出密钥值。 |
| 2026-07-15 | Codex | 受影响文件 `git diff --check HEAD -- ...` | 通过 | 两仓无空白错误 | 未改动/覆盖无关脏工作区文件。 |

## 6. 未覆盖风险

- 未覆盖场景：真实先行通/OpenRouter 计费调用、生产环境迁移、真实生产数据规模性能、兼容接口的真实登录态联调。
- 未覆盖原因：需要外部密钥、费用、生产资源和变更授权。
- 可能影响：真实供应商限流/兼容细节、生产脏数据、发布窗口可能产生额外差异。
- 后续计划：上线前受控只读校验和 Mock 合约；取得明确授权后做最小探针；生产迁移按方案中止条件执行。

## 7. 测试结论

- 是否完成计划自动化测试：部分完成；代码级 AI 测试、编译、前端构建、假数据登录态页面验收和当前 WorkHub 开发库 V20 迁移/只读核验已完成，兼容接口真实登录态联调未执行。
- 是否存在失败用例：AI 定向测试无失败；仓库全量测试有 1 个与本次无关的 Intake 既有失败。
- 是否允许进入交付：允许进入后续联调/发布评审；生产发布仍需按环境重新核对 Flyway 版本、备份空间和主密钥。
- 结论说明：代码、页面、数据库结构和迁移数据语义已对齐，V20 已在当前 WorkHub 开发库真实执行并通过只读核验；剩余门禁是真实登录态接口联调、真实供应商最小探针（需另行授权）及生产发布核对。
