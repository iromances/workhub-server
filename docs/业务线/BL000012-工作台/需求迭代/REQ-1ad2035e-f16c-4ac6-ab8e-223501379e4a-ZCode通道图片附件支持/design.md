# Design：ZCode 通道图片附件支持

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-1ad2035e-f16c-4ac6-ab8e-223501379e4a |
| 需求名称与唯一需求目录 | ZCode 通道图片附件支持；docs/业务线/BL000012-工作台/需求迭代/REQ-1ad2035e-f16c-4ac6-ab8e-223501379e4a-ZCode通道图片附件支持/ |
| 涉及仓库与功能域 | workhub-server / workhub-service：AI 网关（cn.aslight.workhub.service.ai）、需求录入识别（cn.aslight.workhub.service.intake） |
| 所属项目或业务线（适用时） | BL000012-工作台 |
| 对应 Proposal 版本 | 草案 v1 |
| 状态 | 草案，随完整规格实施前统一确认 |
| 当前事实核对环境、分支、时间 | 本机 workhub-server 分支 codex/development-20261102；ZCode 官方分发 0.17.2 源码只读核实；2026-11-10 |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 录入识别：`IntakeEnrichmentService.enrichUploadedIntake` → `CodexCliStructuredExtractor.extract`（workhub-service/src/main/java/cn/aslight/workhub/service/intake/CodexCliStructuredExtractor.java:102-138）。
  - 图片物化：`AttachmentTemporaryFileSet.materialize`（.../attachment/AttachmentTemporaryFileSet.java:91-106，图片判定 `image/*` 或 png/jpg/jpeg/gif/bmp/webp 扩展名）把附件写到 `workhub-attachments-*` 临时目录，`storagePath` 为绝对路径。
  - 图片路径构建：`CodexCliStructuredExtractor.buildInvocation`（:140-170）收集 `imagePaths` 并经 `AiGatewayRequest.withVariables` 传入网关。
  - 拦截点：`DefaultAiGatewayClient.executeCli` ZCODE 分支（.../ai/DefaultAiGatewayClient.java:332）`if (request.imagePaths() != null && !request.imagePaths().isEmpty()) throw ...`。
  - ZCode 执行链：`ZCodeCliClient.execute(provider, model, reasoning, prompt, seconds)`（ZCodeCliClient.java:91）→ `prepare(..., toolNames=List.of(), mcpUrl=null, ...)`（:99）→ `ZCodeTaskConversation.start()` 的 `wh-send`（session/send）仅传 `content=prompt` 字符串（ZCodeTaskConversation.java:142）。
  - `execute` 调用方仅两处：`DefaultAiGatewayClient.java:209`（连接测试，无图）与 `:335`（AI 场景执行）。
- 当前接口、数据及配置：AI 场景 `intake.structured.extract`（`AiUseCaseDefinitions`，Capability=IMAGE_INPUT，允许通道 API+CLI、CLI 类型 CODEX+ZCODE）；Provider `xianxingtong-zhipu-cli-zcode`（cliType=ZCODE，模型 GLM-5.3-Flash）。无数据库、无配置文件变更。
- 事实来源（只读证据）：
  - ZCode 0.17.2 官方分发源码 `packages/shared/src/zcode-protocol/index.ts:1602/2182`：`zcodeSessionSendParamsSchema` 含 `attachments: z.array(jsonObjectSchema).optional()`，`content` 保持字符串。
  - `packages/shared/src/zcode-task-types-core.ts:200-205`：`ZCodePromptImageAttachment { kind:"image", filename, mimeType, sizeBytes?, dataBase64?, localPath? }`，`localPath` 注释"桌面端真实本地路径；大图片不再塞进协议正文，由 agent 侧按路径处理"。
  - `packages/services/src/zcode-agent/zcodeTaskServiceAdapter.ts:427-451`：第一方带附件输入注释"带附件输入保留旧 session/send，避免手机 replayable 图片/文件输入回归"——即旧协议 attachments 为官方在用图片通路。
  - WorkHub 日志（logs/workhub-server.log 2026-11-10T12:45 与 13:09）：intakeId=273 两次识别失败，警告均为"AI 网关执行失败: ZCode 当前文本场景尚不接受图片附件"。
- 目标系统当前实现证据及本次重新核对结果：WorkHub 侧链路（extractor → gateway → conversation）已逐文件核实；ZCode 侧 attachments 通路以 0.17.2 源码核实。
- 尚未核实的假设：agent 侧对 `localPath` 附件的读取范围限制（是否要求路径位于工作区）未从源码完全确认；以端到端验证（AC-01）收口，失败时错误经识别 warnings 可见（AC-05）。

## 2. 系统架构

- 总体方案：沿用既有调用链，把 `AiGatewayRequest.imagePaths`（绝对路径）转换为 `session/send` 的 `attachments` 数组提交给 ZCode 会话；不改变会话创建参数、工具面与沙箱。
- 主要流程：识别调度 → 附件物化（既有）→ 网关 ZCODE 分支透传 imagePaths → `ZCodeCliClient.execute` → `ZCodeTaskConversation.start` 组装 `attachments` → `wh-send` → 终态回写（既有）。
- 前端无改动；识别状态、警告与重试 UI 均复用既有实现。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| CodexCliStructuredExtractor | 图片物化与路径收集（不变） | 附件协议组装 | attachments → imagePaths | AttachmentTemporaryFileSet |
| DefaultAiGatewayClient（ZCODE 分支） | 删除拦截并透传 imagePaths | 图片内容转换 | imagePaths → ZCodeCliClient.execute | AiGatewayRequest |
| ZCodeCliClient | 进程与目录生命周期（不变）；新参数透传 | mimeType 语义校验 | imagePaths → Conversation | 进程环境/preflight |
| ZCodeTaskConversation | 组装 attachments（kind/filename/mimeType/localPath） | 路径存在性校验（网关前已物化） | session/send params | ZCode 0.17 协议 |
| ZCode CLI agent | 按 localPath 读取图片并构造模型图像内容 | — | attachments → 模型输入 | GLM-5.3-Flash VLM 能力 |

## 3. 契约与数据模型

- API/事件提供方与消费者：WorkHub → ZCode app-server（stdio JSON-RPC）。仅修改 `session/send` 请求参数；`session/create`、订阅、事件处理不变。
- 修改的契约及权威文件路径：`ZCodeTaskConversation.start()` 的 `wh-send` params；WorkHub 内部 `ZCodeCliClient.execute` 签名（进程内契约，两处调用方同步更新）。
- 字段语义：`attachments[].kind="image"`；`filename` 取路径文件名；`mimeType` 按扩展名映射 png→image/png、jpg/jpeg→image/jpeg、gif→image/gif、bmp→image/bmp、webp→image/webp（与 extractor 图片集合一致）；`localPath` 为物化绝对路径。`attachments` 仅在列表非空时写入 params；空列表不携带该键（`zcodeSessionSendParamsSchema` 中为 optional）。
- 默认值/空值：imagePaths 为 null 或空 → 行为与改动前完全一致。
- 幂等/并发/重试：沿用 enrichment 既有语义（单次异步执行、失败可重试），无新增重复执行面。
- 数据归属与迁移：无数据库变更。图片临时文件仍由 `AttachmentTemporaryFileSet` 在调用结束删除，不新增持久化。

## 4. 非功能约束

- 安全与权限：不放开 ZCode 内置工具、toolAllowlist 保持空、不启用 MCP、不扩大目录授权；`localPath` 仅为向 agent 提交附件内容的载体（与桌面端用户粘贴图片等价）。凭据与个人配置复制逻辑不变。
- 性能/容量：图片不进协议正文（仅路径），协议帧大小与 prompt 上限（180000 字符）不受影响；图片大小与数量沿用 intake 附件上传约束。
- 失败处理：agent 对附件的拒绝（如格式不支持）表现为 `session/send` 被拒或 `turn.failed` → 既有 `ZCode CLI 协议执行失败/未返回有效终态` 异常链路 → enrichment FAILED + 警告可见（AC-05）。
- 日志：沿用 `ZCode CLI enrichment finished` 摘要与网关失败日志；不新增敏感信息输出。

## 5. 交付与回滚

- 发布顺序：单仓库单模块，无前后端/数据库顺序约束。
- 灰度/开关：无开关（行为变化仅影响 ZCode 通道带图请求，此前该请求必然失败，不存在回归基线）。
- 数据备份与前后校验：不涉及。
- 回滚：恢复 332 行守卫与 `execute` 原签名即可（git revert 单次提交）；无不可逆部分。
- 生效后更新：Tasks 验证证据；`docs/业务线/BL000012-工作台/现状.md`（AI 网关 ZCode 通道能力描述）与 `迭代日志.md`。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → 3.1/3.2/3.3 契约设计；目标 2 → AC-01。
- 非范围保持：第 4 节"明确不做"各项在设计中未触碰（速度限制、LOCAL_REPOSITORY_READ 拦截、Codex/API 路径、toolAllowlist、沙箱均未改动）。
- 待确认问题：无阻塞项；`localPath` 读取范围假设以 AC-01 端到端验证收口。
- 关联：Proposal（本目录 proposal.md）、Tasks（tasks.md）；无 ADR 与接口契约文件。
