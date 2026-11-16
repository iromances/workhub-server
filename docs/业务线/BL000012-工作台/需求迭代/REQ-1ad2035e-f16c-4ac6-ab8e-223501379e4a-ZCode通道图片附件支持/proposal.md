# Proposal：ZCode 通道图片附件支持

> SDD 流程的 Proposal 模板，作为 Design 的需求边界；可与 Design、Tasks 连续起草，随完整规格在实施前统一确认。AI 不得自行填写"已批准"。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-1ad2035e-f16c-4ac6-ab8e-223501379e4a |
| 需求名称 | ZCode 通道图片附件支持 |
| 涉及仓库与功能域 | workhub-server（workhub-service：AI 网关、intake 需求识别）；分支 codex/development-20261102 |
| 所属项目或业务线（适用时） | BL000012-工作台（WorkHub 平台自身） |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-1ad2035e-f16c-4ac6-ab8e-223501379e4a-ZCode通道图片附件支持/ |
| 版本/状态 | 草案 v1，随完整规格实施前统一确认 |
| 目标系统 | WorkHub 后端（AI 网关 ZCode CLI 通道；外部依赖 ZCode CLI 0.17 app-server 协议） |
| 原始需求来源及版本 | 用户需求录入识别失败排查（intakeId=273，2026-11-10 两次失败）后提出"删掉 workhub 自身对 ZCode 的限制" |
| 目标环境/分支 | 本地开发环境；分支 codex/development-20261102 |
| 负责人/评审人 | 用户（发起与确认）；实施：AI 协作 |

## 1. Why：背景与问题

- 当前问题及证据：
  - 需求录入识别链路（`IntakeEnrichmentService.enrichUploadedIntake`）把图片截图传给"需求结构化提取"场景，但该场景绑定的 ZCode CLI 通道在网关入口直接拒绝带图请求：`DefaultAiGatewayClient.executeCli` ZCODE 分支抛 `IllegalArgumentException("ZCode 当前文本场景尚不接受图片附件")`（DefaultAiGatewayClient.java:332）。
  - 日志证据（logs/workhub-server.log，2026-11-10）：intakeId=273 两次识别均以 `AI 网关执行失败: ZCode 当前文本场景尚不接受图片附件` 失败，`nextStatus=FAILED`。
  - 能力核实：绑定模型 GLM-5.3-Flash 为视觉模型（智谱官方 VLM 分类）；ZCode 0.17 app-server `session/send` 协议原生带 `attachments` 参数，图片项 `{kind:"image", filename, mimeType, localPath}`（本地零拷贝路径），第一方桌面/手机带附件输入即走该通路（zcodeTaskServiceAdapter.ts:427-451）；WorkHub 的 `CodexCliStructuredExtractor` 已把图片物化为绝对路径传入 `AiGatewayRequest.imagePaths`，仅 ZCode 分支丢弃。
- 受影响用户/角色：通过"需求录入"上传带截图需求的用户（识别必然失败）；WorkHub 平台管理员。
- 不解决的后果：ZCode 通道上带图需求识别持续失败，只能改绑 Codex/API 通道或去截图录入；通道能力与模型/CLI 实际能力不匹配。

## 2. What：目标与范围

### 本期目标

1. 解除 WorkHub AI 网关 ZCode 通道对图片附件的拦截，图片以 `session/send` 协议 `attachments` 字段（image + localPath）提交给 ZCode 会话。
2. 带图需求的"需求结构化提取"在 ZCode 通道可成功完成识别（不改动模型选择、提示词模板、通道绑定）。

### 本期交付范围

1. `DefaultAiGatewayClient` ZCODE 分支：删除图片拦截守卫，向 `ZCodeCliClient.execute` 透传 `imagePaths`。
2. `ZCodeCliClient`：`execute`/`prepare` → `ZCodeTaskConversation` 链路增加图片路径参数。
3. `ZCodeTaskConversation`：`session/send` 参数增加 `attachments` 数组（kind= image / filename / mimeType 按扩展名映射 / localPath 绝对路径）；无图片时不带该字段，协议行为保持兼容。
4. 同步更新 `DefaultAiGatewayClientTest`（图片拦截断言改为透传断言）与 `ZCodeTaskConversationTest`（wh-send attachments 结构断言）。
5. SDD 规格（Proposal/Design/Tasks）与验证证据归档于唯一需求目录。

### 明确不做

1. 不动 ZCode CLI 速度限制（仅允许 STANDARD）。
2. 不动 `LOCAL_REPOSITORY_READ` 能力场景对 ZCode 的拦截（Agent 工作台授权语义，属另一需求）。
3. 不改动 Codex CLI 路径、API 通道、toolAllowlist、沙箱 config.json、提示词模板与 AI 场景配置。
4. 不新增图片数量/大小上限（复用 intake 附件既有约束与 `CodexCliStructuredExtractor.isImage` 的图片类型集合 png/jpg/jpeg/gif/bmp/webp）。
5. 不实现 ZCode 内置文件工具的开放（本方案不依赖任何工具授权）。

### 约束与依赖

- 依赖的代码和文档：workhub-service 现有 `AttachmentTemporaryFileSet`（图片临时物化）、`AiGatewayRequest.imagePaths`；ZCode CLI 0.17 app-server 协议（`zcodeSessionSendParamsSchema.attachments`，`ZCodePromptImageAttachment` 类型）；ZCode 官方源码核实记录（本 Design 第 1 节）。
- 业务/合规约束：ZCode CLI 复用已安装分发的安全边界不变（不安装/升级/改写用户配置，会话沙箱与工具面不放开）。
- 既有系统/接口/数据约束：`ZCodeCliClient.execute` 现有两个调用方（AI 场景执行、连接测试）；`ZCodeTaskConversation.start` 的 wh-send 协议参数结构；preflight 锁定 0.17.x。
- 外部依赖与前置条件：ZCode CLI 已安装且通过 preflight（版本 0.17.x、CJS 分发可启动）；GLM-5.3-Flash 通道可用。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 对 intakeId=273（带 image.png）执行重新识别，enrichment 完成状态为 SUCCEEDED，识别日志可见 wh-send 携带 attachments | 本地环境重试识别 + 日志核对 | 用户发起需求即问题来源 |
| AC-02 | `DefaultAiGatewayClientTest`、`ZCodeTaskConversationTest`、`ZCodeCliLocalProtocolTest`、`CodexCliStructuredExtractorTest` 全部通过 | `mvn -pl workhub-service test -Dtest=...` | 本仓库测试规范 |
| AC-03 | 无图片的 ZCode 会话 `session/send` 不携带 attachments 字段，纯文本需求识别路径行为不变（回归） | ZCodeTaskConversationTest 无图用例 + 既有测试全绿 | Design 兼容性约束 |
| AC-04 | 连接测试（`DefaultAiGatewayClient` 连接测试路径）不带图片时行为与改动前一致 | DefaultAiGatewayClientTest 既有用例回归 | Design 兼容性约束 |
| AC-05 | 识别失败时（如附件被拒）失败原因经 enrichment warnings 可见，识别状态为 FAILED 且可重新识别 | 现有失败链路（IntakeEnrichmentStatus）验证 | 既有失败处理语义 |
