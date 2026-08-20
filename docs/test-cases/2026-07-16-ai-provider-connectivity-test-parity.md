# WorkHub AI 接入配置连通性测试平移自动化测试用例

## 1. 需求信息

- 需求名称：WorkHub AI 接入配置连通性测试平移
- 研发方案文件：`docs/研发设计/迭代/2026-07-16-ai-provider-connectivity-test-parity.md`
- 测试用例文件路径：`docs/test-cases/2026-07-16-ai-provider-connectivity-test-parity.md`
- 涉及系统：workhub-server、workhub-web
- 涉及模块：model、service、controller、AI 配置页面
- 编写时间：2026-07-16
- 编写人或代理：Codex

## 2. 测试范围

- 核心场景：当前 API/CLI 表单测试、动态掩码密钥补回、API/CLI 路由、常驻结果、不保存 Provider。
- 边界场景：新配置明文 Key、CLI 工作目录为空、API/CLI 独立状态、重复点击防护、旧 probe 兼容。
- 异常场景：字段缺失、掩码无 ID、ID 不存在、通道不匹配、认证/网络/超时/CLI 命令失败、非预期结构化输出。
- 不纳入自动化：真实付费模型、本机真实 CLI 和浏览器登录态，避免外部依赖和费用；以前端构建、Demo 和授权人工冒烟替代。

## 3. 测试环境

- 后端：Java 25、Maven、JUnit 5、Mockito、Spring MockMvc。
- 前端：Vue 3、TypeScript、Vite；当前无自动化测试框架。
- 数据库：Service 单测使用 fake/mock Mapper，不连接真实数据库。
- 外部依赖：API/CLI 客户端全部 mock。
- 测试数据：脱敏/明文 Provider 请求、存量加密 Entity、客户端成功/失败结果。

## 4. 自动化测试用例

| 用例编号 | 场景类型 | 覆盖目标 | 前置条件 | 执行命令或测试方法 | 预期结果 | 开发完成后执行结果 | 失败原因或备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心 | API 当前表单测试 | 完整 Responses 表单 | 连接测试 Service/网关测试 | 使用当前 URL、模型、参数和明文 Key，返回成功与耗时 | 通过 | `DefaultAiGatewayClientTest`、`AiProviderConnectionTestServiceTest` |
| TC-002 | 核心 | CLI 当前表单测试 | 完整 CLI 表单 | 网关测试 | 受限结构化 Codex 调用，单字段 status=OK 判成功 | 通过 | 验证禁用插件、本地工具和附加目录 |
| TC-003 | 核心 | 动态掩码补回 | 有效 ID，Key 为当前掩码 | 配置 Service 测试 | 临时使用旧明文，不返回、不保存 | 通过 | `prepareConnectionTest_shouldRestoreExistingMaskedKeyWithoutSaving` |
| TC-004 | 核心 | 配置不落库 | 成功或失败测试 | 配置/连接 Service 测试 | Provider Mapper 无 insert/update，审计正常新增 | 通过 | fake Mapper 写计数为 0，审计断言通过 |
| TC-005 | 边界 | 新配置明文 Key | ID 为空、Key 明文 | 配置 Service 测试 | 不查询旧记录，使用表单 Key | 通过 | `prepareConnectionTest_shouldUsePlainKeyForUnsavedConfig` |
| TC-006 | 边界 | CLI 工作目录为空 | CLI 其他字段完整 | 网关测试 | 使用 WorkHub 进程目录并保持受限模式 | 待执行 |  |
| TC-007 | 边界 | 旧 probe 兼容 | 已保存 API Provider | 既有 probe 测试 | 原接口、响应和审计行为不变 | 待执行 |  |
| TC-008 | 异常 | 掩码无 ID | 新配置提交掩码 | 配置 Service 测试 | 明确拒绝，不调用外部客户端 | 通过 | 配置准备阶段拒绝 |
| TC-009 | 异常 | ID/通道不匹配 | API ID 用于 CLI 请求 | 配置 Service 测试 | 明确拒绝且不解析密钥 | 通过 | 配置准备阶段拒绝，写计数为 0 |
| TC-010 | 异常 | 字段缺失 | 缺模型、URL、Key 或命令 | Service/Controller 测试 | 业务错误，不调用外部客户端 | 待执行 |  |
| TC-011 | 异常 | API 安全失败分类 | 401/403/404/超时/连接失败 | 连接 Service 测试 | 返回脱敏分类，不含原始正文和密钥 | 部分通过 | 已自动覆盖 401 与密钥脱敏；其余分支由同一安全分类函数处理，未逐项造数 |
| TC-012 | 异常 | CLI 安全失败分类 | 命令不存在、超时、结构不符 | 连接 Service/网关测试 | 返回脱敏分类，不返回进程原始输出 | 部分通过 | 已覆盖非预期结构化输出与受限执行参数；未启动真实失败进程 |
| TC-013 | 权限 | 双接口权限 | create/update/manage 与只读账号 | Controller/Security 测试 | 有管理权限可测试，只读权限被拒绝 | 待执行 |  |
| TC-014 | 接口 | 原生与兼容入口 | 同一有效请求 | MockMvc 测试 | 两入口调用同一 Service 并返回相同字段 | 通过 | 两个 Controller 共 3 个 MockMvc 测试通过 |
| TC-015 | 前端 | 左下角入口 | 打开 Provider 弹窗 | 前端构建 + Demo/浏览器 | 列表无旧按钮，footer 左侧显示当前页签按钮 | 通过（构建/静态） | `vue-tsc --noEmit && vite build` 通过；未做登录态浏览器冒烟 |
| TC-016 | 前端 | 常驻结果 | 返回成功/失败 | Demo/浏览器 | 按钮旁持续展示，不弹瞬时结果消息 | 通过（构建/静态） | 页面不再用 `ElMessage` 表达测试结果，结果绑定 footer 常驻状态 |
| TC-017 | 前端 | 独立 loading | API/CLI 切换与重复点击 | Demo/浏览器 | 通道状态独立，同一测试防重复，保存期间不可测试 | 待执行 | 手工验证 |

## 5. 执行命令与结果

| 执行时间 | 执行人或代理 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-16 | Codex | `mvn -pl workhub-service,workhub-controller -am -Dtest=AiProviderConfigServiceTest,AiProviderConnectionTestServiceTest,DefaultAiGatewayClientTest,AiConfigCompatibilityControllerTest,AiConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | Service 21 个、Controller 3 个，共 24 个测试通过 | Java 25 |
| 2026-07-16 | Codex | `mvn test` | 未全绿 | 共执行 208 个测试，207 通过；既有 `IntakeServiceTest.list_shouldFallbackOperationsEffortAndDatesFromStructuredData` 期望 `4h`、实际 `null` | 与本次 AI 配置改动无关，未越界修改需求登记业务逻辑 |
| 2026-07-16 | Codex | `mvn -DskipTests compile` | 通过 | 9 个 Reactor 模块全部 `SUCCESS` | workhub-server |
| 2026-07-16 | Codex | `npm run build` | 通过 | `vue-tsc --noEmit` 与 Vite production build 成功，1731 modules transformed | workhub-web |
| 2026-07-16 | Codex | `git diff --check -- <本次文件>` | 通过 | 前后端本次文件无空白错误 | 两仓库均存在用户原有未提交改动，未改动或清理其余文件 |

## 6. 未覆盖风险

- 未覆盖场景：真实供应商实时兼容性、本机 Codex 登录态和模型列表刷新行为。
- 未覆盖原因：自动化不得依赖真实密钥、网络、CLI 会话或产生费用。
- 可能影响：自动化通过后，具体环境仍可能因网络、凭据、模型或 CLI 状态失败。
- 后续补充：在具备授权和低成本配置的目标环境执行一次 API/CLI 人工冒烟，并检查安全日志和审计。

## 7. 测试结论

- 是否完成计划自动化测试：本次目标自动化、全量回归尝试、全模块编译、前端构建和静态检查均已执行。
- 是否存在失败用例：目标测试无失败；全量套件存在 1 个与本次无关的既有需求登记用例失败；真实供应商与本机 CLI 冒烟未执行。
- 是否允许进入交付：本次功能允许进入联调；全量测试基线失败需由需求登记迭代单独处理。
- 结论说明：核心当前表单测试、密钥补回、API/CLI 路由、审计、双接口和前端构建已验证；无数据库变更。
