# Proposal：数字员工巡检预警级别与预警方式

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-2020a468-0f59-4ee4-bcee-a99d96550977 |
| 需求名称 | 数字员工巡检预警级别与预警方式 |
| 涉及仓库与功能域 | workhub-server（workhub-model、workhub-service、db 迁移无）；workhub-web（Agent 配置对话框、任务对话框） |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-2020a468-0f59-4ee4-bcee-a99d96550977-巡检预警级别与预警方式/ |
| 版本/状态 | 草案 v1，待完整规格统一确认 |
| 目标系统 | WorkHub 后端、WorkHub 前端 |
| 原始需求来源及版本 | 用户提出（2026-11-16：巡检报告增加预警级别默认 P0；预警方式站内信、webhook 等；确认方式配 Agent 级、webhook 地址任务级加密存储） |
| 目标环境/分支 | workhub-server：codex/development-20261102；workhub-web：codex/development-20261103 |
| 负责人/评审人 | 待确认 |

## 1. Why：背景与问题

- 当前问题及证据：巡检完成后的站内信通知触发写死为"报告含 P0/P1"（`AgentRunNotifier.java:58`），无法按需收紧/放宽；企微推送依赖 AI 在巡检中自主调用 MCP 工具（REQ-47f4429c），平台不保证送达。
- 受影响用户/角色：数字员工使用者与告警接收人（业务线成员、企微群）。
- 不解决的后果：告警阈值不可配（要么被 P1 打扰要么漏报期望级别），企微推送不可靠、不可审计为平台行为。

## 2. What：目标与范围

### 本期目标

1. **Agent 级预警配置**：数字员工新增"预警级别"（P0/P1/P2，默认 P0——报告出现 ≥ 该级别才触发预警）与"预警方式"多选（站内信 / 企微机器人，默认站内信）。
2. **任务级 webhook 地址**：预警方式含企微机器人时，任务分派可填该任务自己的机器人 webhook 地址（AES 加密存储，平台在巡检完成后自动推送）。
3. 通知执行改为按上述配置驱动：级别命中才触发，按所选渠道分发；各渠道独立成败、独立审计。

### 本期交付范围

1. workhub-server：`AgentConfiguration` 增 alertLevel/alertChannels（兼容构造器，构造点零改动）；`AgentTaskSpecification` 增 webhookUrl（存储加密）；`AgentRunNotifier` 按级别+渠道重构（企微复用 `WecomRobotMessageSender`）；前端两个对话框。
2. workhub-web：Agent 配置对话框增两个字段；任务对话框按 Agent 配置条件显示 webhook 输入。

### 明确不做

1. 不做短信/邮件等其他渠道（"等"预留扩展，枚举校验留余地）。
2. 不改巡检报告本身的分级格式（沿用 【P0】/【P1】/【P2】 标记约定）。
3. 不做每渠道独立级别阈值（一个级别管全部渠道）。

### 约束与依赖

- 依赖：`WecomRobotMessageSender`（系统级实现，REQ-47f4429c）、`PaymentCryptoService`（AES，SysConfigService 同源）、站内信 `NotificationService`。
- 兼容：存量 Agent 无预警字段 → 默认 P0+站内信（行为较现状收紧：仅 P0 触发）；存量任务无 webhookUrl → 企微渠道跳过并审计。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | Agent 可配置预警级别/方式，缺省为 P0+站内信；非法值被拦截 | 后端单测 + 前端构建 | 用户 |
| AC-02 | 报告含 ≥ 配置级别标记才触发预警：P0 仅 P0、P1 含 P0/P1、P2 全部 | 后端单测（AgentRunNotifierTest） | 用户 |
| AC-03 | 方式含站内信 → 按既有收件人规则发送；方式含企微 → 平台自动向任务 webhook 地址推送 markdown 摘要 | 后端单测 | 用户 |
| AC-04 | 任务 webhookUrl 加密落库（密文不可逆读原文），读取解密仅用于发送；未配地址或发送失败记审计，不影响其他渠道与运行结果 | 后端单测 | 用户 |
| AC-05 | 存量 Agent/任务（无新字段）行为兼容：默认 P0+站内信、企微跳过 | 后端单测 | 用户 |
