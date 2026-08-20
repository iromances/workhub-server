# WorkHub 统一 AI 网关与 AI 场景配置化测试用例

## 1. 需求信息

- 需求名称：WorkHub 统一 AI 网关与 AI 场景配置化
- 研发方案文件：`docs/研发设计/迭代/2026-07-10-workhub-unified-ai-gateway.md`
- 测试用例文件路径：`docs/test-cases/2026-07-10-workhub-unified-ai-gateway.md`
- 涉及系统：workhub-server、workhub-web
- 涉及模块：AI 网关、AI 配置、需求结构化、SQL 草稿、澄清分析、研发分析
- 编写时间：2026-07-10
- 编写人或代理：Codex

## 2. 测试范围

- 核心场景：配置解析、提示词渲染、API/CLI 协议分流、五个业务任务迁移、页面构建。
- 边界场景：本地仓库任务误绑 API、未知协议、缺失模型、多个 `output_text`、图片大小和类型。
- 异常场景：Provider 停用、HTTP 非 2xx、响应缺少结构化正文、探针返回额外字段。
- 不纳入自动化测试：真实供应商正向调用，原因是缺少真实模型、凭据与计费授权。

## 3. 测试环境

- 后端环境：本机 Java 25、Maven 3.9.14。
- 前端环境：本机 Node/Vite 项目环境。
- 数据库环境：未执行数据库写入；仅检查 Flyway 脚本内容。
- 外部依赖：HTTP 合约使用本机回环 Mock Server，不调用真实供应商。
- 测试数据：固定无敏感信息 JSON 和内置 useCase。

## 4. 自动化测试用例

| 用例编号 | 场景类型 | 覆盖目标 | 前置条件 | 执行命令或测试方法 | 预期结果 | 开发完成后执行结果 | 失败原因或备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心 | 场景解析和 Responses 路由 | 启用 API Provider | `DefaultAiGatewayClientTest` | 渲染 `${prompt}` 并调用 Responses | 通过 |  |
| TC-002 | 安全边界 | 仓库任务拒绝 API | 研发分析绑定 API | `DefaultAiGatewayClientTest` | 调用前显式失败 | 通过 |  |
| TC-003 | 核心 | Responses 请求合约 | 本地 Mock Server | `OpenAiResponsesClientTest` | `/responses`、store=false、reasoning、Schema 正确 | 通过 |  |
| TC-004 | 核心 | Chat 请求合约 | 本地 Mock Server | `OpenAiChatClientTest` | `/chat/completions`、reasoning_effort、Schema 正确 | 通过 |  |
| TC-005 | 核心 | Provider 探针与审计 | Mock 网关结果 | `AiProviderProbeServiceTest` | 成败结果和审计正确，不暴露正文 | 通过 |  |
| TC-006 | 配置 | 内置场景保存校验 | 启用兼容 Provider | `AiUseCaseConfigServiceTest` | 保存、checksum、审计正确 | 通过 |  |
| TC-007 | 回归 | 五个业务生成器迁移 | Recording 网关 | 三组 Generator Test | useCase、Prompt、目录、解析保持正确 | 通过 |  |
| TC-008 | 编译 | 后端全模块编译 | 依赖可用 | `mvn -q -DskipTests compile` | 成功 | 通过 |  |
| TC-009 | 前端 | AI 场景页面类型和构建 | 前端依赖已安装 | `npm run build` | vue-tsc 和 Vite 构建成功 | 通过 |  |
| TC-010 | 全量回归 | workhub-service 全测试 | JVM attach 可用 | `mvn -q -pl workhub-service -am test` | 全部通过 | 未通过 | 184 条中 183 条通过；既有 `IntakeServiceTest` 工时回填断言失败，与 AI 网关无关 |
| TC-011 | 页面 | 真实 AI 配置页面 | 有效登录账号 | 浏览器访问 `/system/ai-config` | 展示“AI场景配置”和执行能力 | 阻塞 | 本地服务可访问，但当前浏览器无有效登录态；旧开发账号已失效 |

## 5. 执行命令与结果

| 执行时间 | 执行人或代理 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-10 16:14 | Codex | `mvn -q -pl workhub-service -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='DefaultAiGatewayClientTest,OpenAiResponsesClientTest,OpenAiChatClientTest,AiProviderProbeServiceTest,AiUseCaseConfigServiceTest,CodexCliStructuredExtractorTest,CodexCliClarificationAnalysisGeneratorTest,CodexCliDevelopmentAnalysisGeneratorTest' test` | 通过 | exit 0 | 最终状态复跑；沙箱外执行以支持 Mockito attach 和回环端口 |
| 2026-07-10 16:08 | Codex | `mvn -q -pl workhub-service -am test` | 未通过 | 184 tests，1 failure | `IntakeServiceTest:268` 期望 4h、实际 null；非本需求引入 |
| 2026-07-10 16:08 | Codex | `npm run build` | 通过 | vue-tsc、Vite build 完成 | workhub-web |
| 2026-07-10 16:08 | Codex | `mvn -q -DskipTests compile` | 通过 | exit 0 | 全模块编译 |

## 6. 未覆盖风险

- 未覆盖：真实先行通/OpenRouter 调用、费用、限流、真实模型能力和生产网络。
- 原因：没有本次授权的真实凭据、模型 ID 和费用范围。
- 影响：只能确认代码与 HTTP 合约，不能宣称供应商联调通过。
- 后续计划：上线前用固定无业务数据探针验证一个真实模型，再单场景灰度。

## 7. 测试结论

- 是否完成计划自动化测试：核心定向测试已完成；全量回归已执行但存在一条既有失败。
- 是否存在失败用例：存在一条与本需求无关的 `IntakeServiceTest` 失败；页面因登录态阻塞。
- 是否允许进入交付：允许代码交付，不允许直接开启真实外部 Provider。
- 结论说明：统一网关核心链路、协议合约、能力边界、五个任务迁移、后端编译和前端构建均通过。
