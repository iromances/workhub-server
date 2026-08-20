# 先行通 AI-Server 接入自动化测试用例

## 1. 需求信息

- 需求名称：先行通 AI-Server 接入
- 研发方案文件：`docs/研发设计/迭代/2026-07-10-xianxingtong-ai-server-integration.md`
- 测试用例文件路径：`docs/test-cases/2026-07-10-xianxingtong-ai-server-integration.md`
- 涉及系统：`workhub-server`，关联同级前端 `workhub-web` 的既有 AI 配置页面
- 涉及模块：AI Provider 配置、凭据加密、运行时路由、Codex CLI Provider、连接探针、操作审计、五类 Codex 业务生成器
- 编写时间：2026-07-10
- 编写人或代理：Codex

## 2. 测试范围

- 本次需求核心场景：API Key 加密落库和脱敏响应；编辑保留和历史明文迁移；先行通 Responses URL 安全限制；运行时路由默认关闭并按 useCase 白名单灰度；Codex CLI 通过环境变量使用 Key；外部供应商工具禁用和空工作目录；无业务数据探针及审计。
- 本次需求边界场景：Key 为空、短 Key、原脱敏值、未配置/错误主密钥、历史明文；HTTPS 显式 `443` 和尾斜杠；路由关闭、未命中白名单、非纯输入输出 useCase、无 useCase 配置行、Provider 停用、系统路由配置读取失败；探针返回非预期 JSON 或额外字段。
- 本次需求异常场景：非 HTTPS、伪造域名、userinfo、非 443 端口、错误路径、query、fragment；密文使用错误主密钥；显式路由的 Provider 配置不安全；Codex CLI 探针失败。
- 不纳入当前自动化测试的范围及原因：真实先行通 API Key、模型 ID、计费账户、限流和错误码未提供；不得猜测模型或发起可能计费的外部调用。真实 MySQL、真实浏览器、生产密钥托管、服务重启后的密钥稳定性和网络连通性需要在目标环境补测。

## 3. 测试环境

- 后端环境：本地 Java 25、Maven、JUnit 5；沙箱内首次运行因 Mockito/Byte Buddy 无法附加而阻塞，使用已授权的本机 Maven 测试命令复跑后正常自附加。
- 前端环境：本次未修改前端；既有页面静态确认可选择 `XIANXINGTONG`、输入自定义协议和模型。
- 数据库环境：自动化测试使用内存 Fake Mapper 或 Mockito，不连接真实 MySQL，不写业务数据。
- 外部依赖：自动化测试不访问先行通 AI-Server，不产生模型调用费用。
- 测试数据准备方式：只使用虚构 Provider 编码、虚构 Key、固定安全 Base URL 和固定 `status=OK` JSON；测试和文档不包含真实凭据。

## 4. 自动化测试用例

| 用例编号 | 场景类型 | 覆盖目标 | 前置条件 | 执行命令或测试方法 | 预期结果 | 开发完成后执行结果 | 失败原因或备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心场景 | AES-GCM 加解密和版本前缀 | 设置测试主密钥和虚构 API Key | 运行 `AiCredentialCryptoServiceTest` | 密文以 `ai:v1:` 开头，同一明文两次密文不同，可还原原值 | 通过 | 5/5 测试通过 |
| TC-002 | 兼容场景 | 历史明文读取 | 输入无版本前缀的虚构 Key | 运行凭据兼容测试 | 原样解析历史明文，空值保持为空 | 通过 |  |
| TC-003 | 异常场景 | 错误主密钥不可解密 | 用主密钥 A 加密、主密钥 B 解密 | 运行错误主密钥测试 | 抛出明确解密异常，不返回错误明文 | 通过 |  |
| TC-004 | 核心场景 | 新建 Provider 加密落库并脱敏响应 | 使用 Fake Mapper 保存安全先行通配置 | 运行 `AiProviderConfigServiceTest` | Mapper 收到 `ai:v1:` 密文，响应只显示末四位脱敏值 | 通过 | 8/8 测试通过 |
| TC-005 | 核心场景 | 编辑传空或原脱敏值保留 Key | 已存在加密 Key | 连续执行空值编辑和脱敏值编辑 | 两次保存均保留原密文，服务端仍可解析原 Key | 通过 |  |
| TC-006 | 兼容场景 | 编辑历史明文时自动迁移 | 已存在历史明文 Key | 回传原脱敏值执行更新 | 保存值升级为 `ai:v1:` 密文且业务 Key 不变 | 通过 |  |
| TC-007 | 边界场景 | 允许标准 URL、显式 443 和尾斜杠 | `XIANXINGTONG + OPENAI_RESPONSES` | 分别保存 `/v1` 和 `:443/v1/` | 保存成功，Base URL 保持可用 | 通过 |  |
| TC-008 | 异常场景 | 拒绝不安全 URL | 构造 HTTP、伪造 host、userinfo、8443、错误路径、query、fragment | 运行 URL 负向参数集 | 所有配置在持久化前被拒绝 | 通过 | 覆盖 8 个非法 URL |
| TC-009 | 核心场景 | 路由总开关默认关闭 | `runtimeRoutingEnabled=false` | 运行 `AiRuntimeConfigResolverTest` | 返回 empty，不查询 useCase 和 Provider，调用方沿用原路径 | 通过 | 8/8 测试通过 |
| TC-010 | 边界场景 | 未命中 useCase 白名单保持原路径 | 总开关开启但白名单不含当前编码 | 运行白名单测试 | 返回 empty，不查询 Provider | 通过 |  |
| TC-011 | 核心场景 | useCase 任务配置优先 | 存在启用任务行并绑定 Provider | 解析 `intake.structured.extract` | 使用任务绑定的 Provider、模型、推理强度和超时 | 通过 |  |
| TC-012 | 核心场景 | 无任务行时使用默认 Provider | 白名单命中且配置 `runtimeProviderCode` | 解析 `intake.sql-draft.generate` | 按 Provider 编码读取默认配置，超时回落 Provider 调用超时 | 通过 | 模型为空时由调用方后续按既有配置兜底，正式启用前仍必须配置真实模型 |
| TC-013 | 异常场景 | 显式选中的停用 Provider 失败可见 | 默认 Provider 已停用 | 运行停用测试 | 抛出“已停用”异常，不静默回退 | 通过 |  |
| TC-014 | 异常场景 | 运行时再次拒绝历史不安全 URL | Mapper 返回绕过保存校验的恶意历史 URL | 调用 `resolveForProbe` | 运行时防御性校验拒绝调用 | 通过 |  |
| TC-015 | 核心场景 | Codex Provider 使用 Responses 和环境变量 | 构造虚构运行时 Provider | 运行 `CodexCliRuntimeProviderTest` | TOML 包含 `wire_api="responses"`、安全 Base URL 和环境变量名，不含 API Key | 通过 | 5/5 测试通过 |
| TC-016 | 安全场景 | 命令行不含运行时 API Key | 构造 Codex 请求 | 检查 `buildCommand` 返回值 | 命令参数中不存在 Key | 通过 |  |
| TC-017 | 核心场景 | 五个业务生成器传稳定 useCaseCode | 构造结构化抽取、SQL 草稿、澄清、研发分析和调整调用 | 运行三个生成器测试类 | 分别传入五个约定编码 | 通过 | 6/6 测试通过；研发分析单测同时覆盖 analyze/adjust |
| TC-018 | 核心场景 | 探针成功只返回安全摘要并审计 | Mock Codex 返回精确 `{"status":"OK"}` | 运行 `AiProviderProbeServiceTest` | 响应成功且不包含供应商正文；审计记录 Provider、模型、操作人和 IP | 通过 | 3/3 测试通过；额外字段单独验证为失败 |
| TC-019 | 异常场景 | 探针失败返回摘要并审计 | Mock Codex 执行失败 | 运行探针失败测试 | 响应失败，审计结果为 FAILURE，不泄露 Key | 通过 |  |
| TC-020 | 集成场景 | 真实数据库加密、脱敏与编辑保留 | 目标环境设置稳定主密钥并允许写测试配置 | 通过管理接口新增、查询、编辑并只读核对数据库 | 数据库为 `ai:v1:` 密文，接口为脱敏值，编辑不丢 Key | 待实测 | 当前未连接真实数据库，不执行写入 |
| TC-021 | 外部场景 | 无业务数据真实连接探针 | 供应商提供真实 Key、真实模型并批准费用 | 调用 `POST /api/system/ai-providers/{id}/probe` | 返回成功、耗时和安全摘要；审计完整；请求不含业务数据 | 待实测 | 缺真实 Key、模型和费用授权，未发起付费调用 |
| TC-022 | 灰度场景 | 单 useCase 真实运行时切换与回滚 | 探针通过，明确低风险 useCase 和测试数据 | 只从 `intake.structured.extract`、`intake.sql-draft.generate` 选择一个加入白名单，开启总开关后再关闭 | 命中先行通且结果可用；关闭后立即恢复原路径 | 待实测 | 需要真实供应商、目标环境和业务安全授权 |
| TC-023 | 页面场景 | 页面录入和脱敏值编辑体验 | 启动前后端并使用授权测试账号 | 浏览器新增/编辑先行通 Provider | 页面保存成功、只展示脱敏 Key、再次保存不要求重输 | 待实测 | 本次未改前端，当前未启动浏览器联调 |
| TC-024 | 运维场景 | 主密钥重启稳定性和错误密钥告警 | 目标环境录入测试 Key | 使用同一主密钥重启并探测，再在隔离环境验证错误主密钥 | 同一主密钥可继续解密；错误主密钥明确失败且不泄露信息 | 待实测 | 需要受控环境，不得在生产直接替换主密钥 |
| TC-025 | 安全场景 | 未设置 AI 主密钥时拒绝加密 | `WORKHUB_AI_MASTER_KEY` 为空 | 运行凭据主密钥必填测试 | 明确失败，不使用内置开发默认密钥 | 通过 | `AiCredentialCryptoServiceTest` 覆盖 |
| TC-026 | 安全场景 | 外部供应商禁用本地和联网工具 | 构造 custom Provider 命令 | 检查 sandbox、approval、disabled features、addDirs 和环境策略 | read-only、never；shell/浏览器/插件/MCP 等关闭；不传敏感目录和 Key | 通过 | `CodexCliRuntimeProviderTest` 覆盖 |
| TC-027 | 异常场景 | 非纯输入输出 useCase 即使白名单命中也拒绝 | 白名单加入 `intake.development.analyze` | 调用运行时解析 | 在查询 Provider 前显式失败，不走先行通或 legacy 回退 | 通过 | `AiRuntimeConfigResolverTest` 覆盖 |
| TC-028 | 异常场景 | 路由系统配置读取失败时 fail-closed | Mock 系统配置读取异常 | 调用运行时解析 | 显式失败，不回退 application 或其他供应商 | 通过 | `AiRuntimeConfigResolverTest` 覆盖 |
| TC-029 | 兼容场景 | 推理强度和默认超时规范化 | 任务推理强度为 `ULTRA`；默认 Provider 有调用超时 | 解析任务与默认 Provider | `ULTRA` 转为 Codex `xhigh`；无任务行时使用 Provider 调用超时 | 通过 | `AiRuntimeConfigResolverTest` 覆盖 |
| TC-030 | 安全场景 | 配置编码不可变且关键写入可审计 | 创建 Provider/useCase 后尝试改编码，并带操作人/IP保存 | 运行 Provider/useCase 服务测试 | 编码修改被拒绝；审计只记录元数据，不记录提示词和 Key | 通过 | `AiProviderConfigServiceTest`、`AiUseCaseConfigServiceTest` 覆盖 |

## 5. 执行命令与结果

开发完成后，必须只填写实际执行过的命令和真实结果。

| 执行时间 | 执行人或代理 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-10 14:31 CST | Codex | `mvn -q -pl workhub-service -am -Dtest=AiCredentialCryptoServiceTest,AiProviderConfigServiceTest,AiRuntimeConfigResolverTest,AiProviderProbeServiceTest,CodexCliRuntimeProviderTest,CodexCliStructuredExtractorTest,CodexCliClarificationAnalysisGeneratorTest,CodexCliDevelopmentAnalysisGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false test` | 失败（沙箱测试环境） | 26 个测试中 9 个 Mockito 用例因 JDK 25 无法在沙箱内动态附加 Byte Buddy Agent 报错；无业务断言失败 | 随后使用已授权本机 Maven 命令复跑 |
| 2026-07-10 14:46 CST | Codex | `mvn -pl workhub-service -am -Dtest=AiCredentialCryptoServiceTest,AiProviderConfigServiceTest,AiUseCaseConfigServiceTest,AiRuntimeConfigResolverTest,AiProviderProbeServiceTest,CodexCliRuntimeProviderTest,CodexCliStructuredExtractorTest,CodexCliClarificationAnalysisGeneratorTest,CodexCliDevelopmentAnalysisGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | 37 个测试通过，0 失败、0 错误 | 覆盖密钥、Provider、审计、路由、工具隔离、Codex Provider、探针和五个稳定 useCaseCode |
| 2026-07-10 14:46 CST | Codex | `mvn -DskipTests compile` | 通过 | 9 个 Maven 模块全部 `SUCCESS` | 最终代码全模块编译通过 |
| 2026-07-10 14:47 CST | Codex | `mvn test` | 失败（非本需求用例） | 执行到 `workhub-service` 共 193 个测试，192 个通过；`IntakeServiceTest.list_shouldFallbackOperationsEffortAndDatesFromStructuredData` 期望 `4h`、实际 `null` | 失败来自工作区既有需求管理改动，本次未修改 `IntakeService` 或该测试；Controller/Job/Bootstrap 因 reactor fail-fast 未继续执行 |

## 6. 未覆盖风险

- 未覆盖场景：真实供应商鉴权、真实模型可用性、计费、限流、错误码、响应差异；真实 MySQL 密文核对；前端浏览器保存和脱敏编辑；目标服务器网络出口；服务重启和主密钥轮换；单 useCase 真实业务灰度；临时目录在进程被强杀时的残留；完整 Maven 测试集被既有 `IntakeServiceTest` 失败中断，未运行后续模块测试；工作区既有两个同版本 `V17__*.sql` 尚未结合目标环境 Flyway 历史处理，因此未启动真实数据库环境。
- 未覆盖原因：供应商手册没有提供真实 API Key、模型 ID、模型列表、限流和错误码；当前没有费用授权，也没有指定目标环境和可写测试数据。
- 可能影响：代码和协议适配正确但真实调用仍可能因模型名、账号权限、余额、网络或供应商响应差异失败；主密钥运维不当会导致已有密文不可读。
- 后续补充计划：供应商信息到位后，先在非生产环境设置独立主密钥并完成 TC-020；经费用授权执行一次 TC-021；确认成功后只选择一个低风险 useCase 执行 TC-022，再补浏览器和重启验证。

## 7. 测试结论

- 是否完成计划自动化测试：是，37 条定向单元测试已通过。
- 是否存在失败用例：本需求 37 条定向自动化无失败；首次 Mockito 运行受沙箱内 JDK 25 Agent 附加限制失败，切换到已授权本机 Maven 测试后全部通过。完整测试集另有 1 条既有需求管理用例失败，与本次先行通改动文件无交集。
- 是否允许进入交付：允许代码和文档交付；不允许据此开启真实供应商业务路由。
- 结论说明：本地自动化已覆盖密钥安全、URL 白名单、默认关闭与 fail-closed 路由、两个纯输入输出场景限制、Codex Responses 工具隔离、探针严格响应和五个稳定业务编码。真实外部联调仍为阻塞项，必须等待真实 API Key、供应商确认的模型 ID、目标环境和费用授权。
