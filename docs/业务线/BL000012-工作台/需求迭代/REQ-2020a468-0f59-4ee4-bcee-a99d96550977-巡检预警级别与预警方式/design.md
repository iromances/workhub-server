# Design：数字员工巡检预警级别与预警方式

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-2020a468-0f59-4ee4-bcee-a99d96550977 |
| 需求名称与唯一需求目录 | 数字员工巡检预警级别与预警方式；docs/业务线/BL000012-工作台/需求迭代/REQ-2020a468-0f59-4ee4-bcee-a99d96550977-巡检预警级别与预警方式/ |
| 涉及仓库与功能域 | workhub-server：AgentConfiguration、AgentTaskSpecification、AgentWorkbenchService、AgentRunNotifier；workhub-web：AgentConfigDialog、AgentTaskDialog |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | 草案 v1 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server codex/development-20261102；workhub-web codex/development-20261103；2026-11-16 |

## 1. 现状与证据

- 站内信触发写死：`AgentRunNotifier.notifyOnce`（:56-58）COMPLETED 且 resultText 含 "P0"/"P1" 即向任务 scopes 各业务线成员发 AGENT_PATROL 站内信（成员空回退 admin；dedupe=AGENT_RUN:{runId}；失败退避重试 3 次并审计 NOTIFY_FAILED）。
- 企微发送器：`WecomRobotMessageSender.send(content)`（@Component；webhook 当前取系统配置 wecom.agent.webhook；UTF-8 ≤4096 字节；errcode 透传不重试）；现仅作为 MCP 工具由 AI 自主调用。
- 加密：`PaymentCryptoService.encrypt/decrypt`（AES，SysConfigService 密文同源），可直接注入复用。
- 规格存储：`pm_agent_task.specification_json` 明文 LONGTEXT；`AgentTaskSpecification` 已有多个兼容构造器先例；`AgentConfiguration` 无兼容构造器（加字段将波及 14 处构造点，故同步补兼容构造器）。
- 尚未核实的假设：WecomRobotMessageSender 发送方法可直接被平台服务调用（无 MCP 上下文依赖）。

## 2. 系统架构

- 预警决策集中到 `AgentRunNotifier`：巡检 COMPLETED → 读 Agent 配置（级别+渠道）与任务规格（webhook）→ 级别命中（报告文本含 ≥级别 标记）→ 按渠道分发。
- webhook 地址生命周期：任务创建/更新时前端交明文 → service 序列化前加密（前缀 `enc:` 标识密文）落 specification_json → 通知时解密发送；有权限查看任务详情可见明文（存储加密满足保密要求，权限由 agent:task:view 把守）。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AgentConfiguration | alertLevel/alertChannels 字段与默认归一 | 触发判断 | record | bean validation |
| AgentTaskSpecification | webhookUrl 字段（+兼容构造器） | 加密 | record | — |
| AgentWorkbenchService | webhookUrl 加密落库/解密回显 | 触发判断 | create/update/detail | PaymentCryptoService |
| AgentRunNotifier | 级别判定+渠道分发+审计 | webhook 存储 | 运行完成事件 | NotificationService、WecomRobotMessageSender、PaymentCryptoService |
| AgentConfigDialog | 级别下拉+方式多选 | — | 表单 | options |
| AgentTaskDialog | 条件显示 webhook 输入 | — | 表单 | agent 配置 |

## 3. 契约与数据模型

- `AgentConfiguration` 末尾新增：`@Pattern(regexp="P0|P1|P2") String alertLevel`（紧凑构造器 null/空→"P0"）、`List<@Pattern(regexp="INBOX|WECOM") String> alertChannels`（null/空→["INBOX"]，去重保序）；新增旧 8 参兼容构造器（预警字段取 null，构造点零改动）。
- `AgentTaskSpecification` 末尾新增：`@Size(max=512) String webhookUrl`（协议：service 层加密后存 `enc:{cipher}`，读取时解密；null=未配置）；既有 6/7 参构造器改委托新 8 参（webhookUrl=null）。
- 判定语义：`P0`→contains("P0")；`P1`→contains("P0")||contains("P1")；`P2"→任一。渠道：INBOX=既有站内信；WECOM=向 spec.webhookUrl 发 markdown（标题+报告前 1000 字，超 4096 字节按发送器既有截断/报错语义）。
- 审计：站内信成功 NOTIFIED（既有）；企微成功 WEBHOOK_SENT（地址不落审计，仅长度/结果）；企微无地址 WEBHOOK_SKIPPED；企微失败 WEBHOOK_FAILED（原因截断）——均为 RUN 维度，不影响运行结果。
- 回显：任务详情/编辑响应 webhookUrl 以明文返回（存储态加密；权限 agent:task:view）。
- 无数据库变更（configuration_json/specification_json 兼容扩字段）。

## 4. 非功能约束

- 安全：webhook 密文落库、解密仅存在于发送路径与有权限的详情读取；审计不含地址原文。
- 时效：企微发送在通知退避重试窗口内同步执行一次（不重试，沿用发送器语义）；失败不阻断站内信。
- 兼容：存量 Agent 默认 P0+INBOX（较现状 P0/P1 收紧，符合用户"默认 P0"要求）；存量任务企微渠道自动跳过。

## 5. 交付与回滚

- 发布顺序：后端先发、前端随后（前端先行会出现字段不识别，无害但不可用）。
- 回滚：还原两端提交；JSON 新字段旧代码忽略（Jackson 未知字段容忍），无数据不可逆。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → AgentConfiguration+前端；目标 2 → webhookUrl 加密链路；目标 3 → AgentRunNotifier。
- 非范围保持：不做新渠道、不改报告格式、不做分渠道级别。
- 待确认问题：无（回显明文 vs 不回显选了明文——存储加密+权限把守，编辑体验优先；如需"不回显、留空保留"模式后续可调）。
