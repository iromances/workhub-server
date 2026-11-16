# Tasks：ZCode 通道图片附件支持

## 1. 元信息

- 需求编码（沿用 Proposal 的固定编码，不重新生成）：REQ-1ad2035e-f16c-4ac6-ab8e-223501379e4a
- 需求名称与唯一需求目录：ZCode 通道图片附件支持；docs/业务线/BL000012-工作台/需求迭代/REQ-1ad2035e-f16c-4ac6-ab8e-223501379e4a-ZCode通道图片附件支持/
- 涉及仓库与功能域：workhub-server / workhub-service（AI 网关、需求录入识别）
- 所属项目或业务线（适用时）：BL000012-工作台
- Proposal 版本：草案 v1
- Design/契约版本：草案 v1
- 目标环境与分支：本地开发环境；codex/development-20261102
- 任务计划状态：草案，随完整规格实施前统一确认
- 负责人/评审人：用户（确认）；AI 协作（实施）

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | proposal.md / design.md 起草 | 无 | 与 T-02+ 串行（先确认规格） | Design 全文 | 待开始 |
| T-02 | DefaultAiGatewayClient ZCODE 分支解除拦截并透传 | T-01 | — | §3.2 / AC-01 | 待开始 |
| T-03 | ZCodeCliClient.execute 签名扩展与透传 | T-02 | 与 T-02 同批 | §3.2 | 待开始 |
| T-04 | ZCodeTaskConversation 组装 attachments | T-03 | 与 T-03 同批 | §3.3 / AC-03 | 待开始 |
| T-05 | 网关与会话测试同步 | T-04 | — | AC-02/03/04 | 待开始 |
| T-06 | 模块单测执行 | T-05 | — | AC-02 | 待开始 |
| T-07 | intakeId=273 端到端重新识别验证 | T-06 | — | AC-01/AC-05 | 待开始 |
| T-08 | 交付：证据回写 Tasks、现状与迭代日志更新 | T-07 | — | Design §5 | 待开始 |

执行顺序：T-01 → T-02 → T-03 → T-04 → T-05 → T-06 → T-07 → T-08（线性；T-02~T-04 属同一改动链，按依赖顺序同批提交）。共享文件：DefaultAiGatewayClient.java 同时被 T-02（守卫删除）与 T-03（调用点签名）触碰，在同一次编辑链中完成，不并行。

## 3. 任务明细

### T-01：SDD 规格起草

- 描述与目标：按 SDD 模板起草 Proposal/Design/Tasks，固定编码 REQ-1ad2035e-f16c-4ac6-ab8e-223501379e4a。
- 输入：故障日志（intakeId=273）、WorkHub 侧代码核实、ZCode 0.17.2 官方源码核实。
- 输出：唯一需求目录下 proposal.md、design.md、tasks.md。
- 前置依赖：无。
- 允许修改范围：仅本需求目录文档。
- 明确不修改范围：其他需求文档、知识库。

验收条件：

- [ ] 条件 1：三份文档齐备且编码、目录、仓库一致。
- [ ] 条件 2：范围与非范围逐条对应（明确不做 5 项在 Design §6 有承接）。

计划验证方式（尚未执行）：用户实施前统一确认（本任务随规格确认视为通过）。

状态及真实结果：待开始；实际执行后再填写证据、失败原因和剩余风险。

### T-02：网关解除 ZCode 图片拦截

- 描述与目标：删除 DefaultAiGatewayClient.executeCli ZCODE 分支的图片拦截抛错（原 332 行），把 `request.imagePaths()` 传给 `zcodeCliClient.execute(...)`。
- 输入：Design §1 现状、§2 架构。
- 输出：DefaultAiGatewayClient.java 修改。
- 前置依赖：T-01。
- 允许修改范围：executeCli ZCODE 分支及同文件连接测试调用点（209 行）签名适配。
- 明确不修改范围：速度限制（STANDARD）校验、LOCAL_REPOSITORY_READ 拦截、API 分支、Codex 分支。

验收条件：

- [ ] 条件 1：带图请求到达 ZCODE 分支时不再抛"尚不接受图片附件"，imagePaths 原样透传。
- [ ] 条件 2：无图请求（含连接测试）参数与改动前一致。

计划验证方式（尚未执行）：T-05 测试用例 + T-06 单测。

状态及真实结果：待开始。

### T-03：ZCodeCliClient 链路透传

- 描述与目标：`execute` 增加图片路径参数并沿 `prepare` → `CliTaskLaunch` 传入 `ZCodeTaskConversation`。
- 输入：T-02 产物；ZCodeCliClient.java 现有结构（execute:91、prepare:45）。
- 输出：ZCodeCliClient.java 修改。
- 前置依赖：T-02。
- 允许修改范围：execute/prepare 参数链与进程执行输入校验（既有 prompt 上限等不变）。
- 明确不修改范围：preflight、目录/环境隔离逻辑、协议读取、进程停止与清理。

验收条件：

- [ ] 条件 1：imagePaths 列表完整到达 Conversation 构造。
- [ ] 条件 2：imagePaths 为空/null 时各环节行为与改动前一致。

计划验证方式（尚未执行）：T-05 测试用例 + T-06 单测。

状态及真实结果：待开始。

### T-04：会话组装 attachments

- 描述与目标：`ZCodeTaskConversation.start()` 的 wh-send params 在有图片时增加 `attachments` 数组：`{kind:"image", filename:<路径文件名>, mimeType:<扩展名映射>, localPath:<绝对路径>}`；映射 png→image/png、jpg/jpeg→image/jpeg、gif→image/gif、bmp→image/bmp、webp→image/webp；空列表不携带该键。
- 输入：T-03 产物；Design §3 契约；ZCode 协议类型 `ZCodePromptImageAttachment`。
- 输出：ZCodeTaskConversation.java 修改。
- 前置依赖：T-03。
- 允许修改范围：start() params 组装与构造器参数；新增 mimeType 私有映射方法。
- 明确不修改范围：会话创建参数、协议帧读取/校验、终态与格式校正逻辑、toolAllowlist 与 mode。

验收条件：

- [ ] 条件 1：wh-send params 含 attachments 且字段值符合 Design §3 语义。
- [ ] 条件 2：无图片时不写 attachments 键（zcodeSessionSendParamsSchema 可选字段兼容）。

计划验证方式（尚未执行）：T-05 测试用例 + T-06 单测。

状态及真实结果：待开始。

### T-05：测试同步

- 描述与目标：改写 `DefaultAiGatewayClientTest.zcodeRejectsUnsupportedLegacyRepositoryImagesAndSpeedBeforeStarting`（图片断言改为透传断言，速度限制断言保留）；`ZCodeTaskConversationTest` 增补 wh-send attachments 结构断言与无图不带键断言。
- 输入：T-02~T-04 产物。
- 输出：两个测试文件修改。
- 前置依赖：T-04。
- 允许修改范围：上述两个测试文件。
- 明确不修改范围：其余测试文件与测试基础设施。

验收条件：

- [ ] 条件 1：新断言覆盖带图透传、无图不带键两个场景。
- [ ] 条件 2：既有其他用例不受改动影响。

计划验证方式（尚未执行）：T-06。

状态及真实结果：待开始。

### T-06：模块单测

- 描述与目标：运行 AI 网关与识别相关单测确认全绿。
- 输入：T-05 产物。
- 输出：测试执行记录（命令与结果摘要）。
- 前置依赖：T-05。
- 允许修改范围：测试暴露的实现缺陷修正（限本需求四文件内）。
- 明确不修改范围：无关模块。

验收条件：

- [ ] 条件 1：`mvn -pl workhub-service test -Dtest='DefaultAiGatewayClientTest,ZCodeTaskConversationTest,ZCodeCliLocalProtocolTest,CodexCliStructuredExtractorTest'` 通过。

计划验证方式（尚未执行）：本任务即验证。

状态及真实结果：待开始。

### T-07：端到端验证

- 描述与目标：本地启动服务，对 intakeId=273（嘉泰设备租赁，带 image.png 与 xlsx 附件）执行重新识别。
- 输入：T-06 通过后的服务构建。
- 输出：识别结果与日志摘录（wh-send attachments、enrichment 终态）。
- 前置依赖：T-06。
- 允许修改范围：无代码修改；仅执行验证与记录。
- 明确不修改范围：AI 场景/Provider 配置。

验收条件：

- [ ] 条件 1：识别终态 SUCCEEDED 且结构化字段写入。
- [ ] 条件 2：日志可见 wh-send 携带 attachments。
- [ ] 条件 3（失败分支）：若识别失败，失败摘要经 warnings 可见且状态 FAILED（AC-05），记录原因。

计划验证方式（尚未执行）：重试识别接口/页面操作 + 日志核对。

状态及真实结果：待开始。

### T-08：交付

- 描述与目标：回写验证证据，更新 BL000012 现状与迭代日志。
- 输入：T-07 证据。
- 输出：tasks.md 状态与证据、现状.md、迭代日志.md 更新。
- 前置依赖：T-07。
- 允许修改范围：本需求 tasks.md、docs/业务线/BL000012-工作台/现状.md 与 迭代日志.md 对应小节。
- 明确不修改范围：知识库、其他业务线文档。

验收条件：

- [ ] 条件 1：Tasks 各任务状态与真实结果（含证据摘录）已回写，无凭自述的"已完成"。
- [ ] 条件 2：现状/迭代日志记录本次迭代事实。

计划验证方式（尚未执行）：文档核对。

状态及真实结果：待开始。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-07；AC-02→T-05/T-06；AC-03→T-04/T-05；AC-04→T-05（连接测试用例回归）；AC-05→T-07（失败分支）。全部覆盖。
- Design 接口/数据/非功能约束承接：契约→T-04；安全边界→T-02/03/04 明确不修改范围；失败处理→T-07 条件 3；日志→T-07。
- 未覆盖项及原因：无。
- 已生效事实的核实与系统设计、迭代日志更新：T-08 承接。
- 关联版本链接：Proposal 草案 v1、Design 草案 v1（本目录）。
- 完整规格统一确认：范围（Proposal §2）、规格版本（v1）、结论（待确认）、确认人（用户）、日期（待填写）。

## 5. 实施与验证结果（2026-11-10 回写）

| 任务 | 状态 | 证据摘要 |
| --- | --- | --- |
| T-01 | 已完成 | proposal/design/tasks 三份规格建档，需求编码与目录一致已核（含用户批准的实施计划） |
| T-02 | 已完成 | DefaultAiGatewayClient ZCODE 分支删除图片拦截，透传 `request.imagePaths()` 与 `outputSchema`；连接测试传空列表与 null 保持原行为 |
| T-03 | 已完成 | ZCodeCliClient `execute` 增加 `imagePaths`、`finalResultSchema` 参数，沿 `prepare`（新增 11 参重载，原 9/10 参保留委托）传入会话 |
| T-04 | 已完成 | ZCodeTaskConversation `session/send` 有图时组装 `attachments`（kind=image / filename / mimeType 按扩展名映射 / localPath 绝对路径），无图不带该键；沙箱、toolAllowlist、mode 未动 |
| T-05 | 已完成 | 网关透传用例 `zcodePassesImageAttachmentsThroughToNativeClient`、会话 attachments 结构用例、无图不带键断言、连接测试/文本任务 stub 适配；速度与 LOCAL_REPOSITORY_READ 拦截回归保留 |
| T-06 | 已完成 | 4 个测试类 39 用例（33 执行 6 跳过）全部通过，0 失败 |
| T-07 | 已完成 | 两轮端到端：第一轮 14:05 识别暴露格式校正未激活（带围栏文本解析失败 → FAILED，失败原因可见）；补 schema 传递后第二轮 14:16 重新识别 `SUCCEEDED`，mergedFieldCount=12（此前仅 3），需求材料导出正常 |
| T-08 | 已完成 | 本节回写 + 迭代日志已更新 |

### 过程说明与剩余风险

- 端到端第一轮暴露的缺口属于既有实现：`execute` 链路从未传递输出 schema，格式校正机制此前不会被激活（纯文本场景依赖模型自觉输出裸 JSON）。本需求内已一并修复，纯文本场景同样受益。
- 模块全量 `mvn test` 被工作区在途改动阻塞（`AgentWorkbenchService` 引用 `AgentMapper` 未定义方法及 `AgentTaskUpdateRequest` 缺失，与本次无关）；相关测试经定向编译运行验证，后续该在途改动收敛后建议全量回归。
- `localPath` 附件由 ZCode agent 侧按路径读取的范围边界未做源码级确认，以端到端成功收口；若后续 agent 版本收紧路径策略，失败会以识别警告可见（AC-05 链路）。

### 完整规格统一确认

- 范围与规格版本：Proposal/Design/Tasks 草案 v1；结论：用户批准实施计划并配合两轮端到端验证，视为本轮确认；确认人：用户；日期：2026-11-10。
