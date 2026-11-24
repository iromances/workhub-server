# Tasks：数字员工巡检预警级别与预警方式

## 1. 元信息

- 需求编码：REQ-2020a468-0f59-4ee4-bcee-a99d96550977
- 需求名称与唯一需求目录：数字员工巡检预警级别与预警方式；docs/业务线/BL000012-工作台/需求迭代/REQ-2020a468-0f59-4ee4-bcee-a99d96550977-巡检预警级别与预警方式/
- 涉及仓库与功能域：workhub-server（model/service）；workhub-web（AgentConfigDialog、AgentTaskDialog）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：草案 v1
- Design/契约版本：草案 v1
- 目标环境与分支：workhub-server codex/development-20261102；workhub-web codex/development-20261103
- 任务计划状态：规格已确认并实施完成
- 负责人/评审人：用户（2026-11-16 确认 Agent 级配置 + 任务级 webhook 地址加密存储，随后确认实施）

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端模型字段（含兼容构造器）+加密链路+Notifier 重构 | 规格确认 | 无 | §2/§3、AC-01～05 | 已完成 |
| T-02 | 后端单测 | T-01 | 无 | AC-01～05 | 已完成 |
| T-03 | 前端两个对话框 | 规格确认 | 契约冻结 | AC-01 | 已完成 |
| T-04 | 验证与交付记录 | T-01～T-03 | 无 | §5 | 已完成 |

执行顺序：T-01 → T-02 → T-03 → T-04；后端先于前端发布。

## 3. 任务明细

### T-01：后端预警链路

- 描述与目标：`AgentConfiguration` 末尾增 alertLevel/alertChannels（归一化默认 P0/[INBOX]，旧 8 参兼容构造器）；`AgentTaskSpecification` 末尾增 webhookUrl（旧构造器改委托）；`AgentWorkbenchService` create/update/detail 对 webhookUrl 加密（`enc:` 前缀）落库与解密回显（PaymentCryptoService）；`AgentRunNotifier` 按级别判定（P0→P0、P1→P0/P1、P2→任一）+渠道分发（INBOX 既有；WECOM 解密直发 WecomRobotMessageSender，审计 WEBHOOK_SENT/SKIPPED/FAILED）。
- 输入：design §2/§3。
- 输出：上述代码；无迁移。
- 明确不修改范围：报告格式约定、WecomRobotMessageSender 内部、站内信收件人规则。

验收条件：

- [ ] 级别判定与渠道分发按契约（AC-02/03）；webhook 密文落库、解密仅发送/回显两处（AC-04）；存量默认兼容（AC-05）。

计划验证方式（尚未执行）：`mvn -pl workhub-service -am test -Dtest='AgentRunNotifierTest,AgentWorkbenchServiceTest'`。

状态及真实结果：已完成（2026-11-16）。`AgentConfiguration` 末尾增 alertLevel/alertChannels（归一化 P0/[INBOX]，新增 9 参兼容构造器，既有构造点零改动）；`AgentTaskSpecification` 末尾增 webhookUrl（7 参构造器改委托）；`AgentWorkbenchService` 注入 PaymentCryptoService——createTask/updateTask 以 `encodeForStorage` 加密落库（requestHash 仍按明文请求计算保幂等）、CREATE/UPDATE_SPEC 审计经 maskWebhook 脱敏、task/run 详情 revealWebhook 回显、`AgentRunStore.context` 对进入提示词的规格 maskWebhook；`AgentRunNotifier` 重构为级别判定（levelHit：P0→P0、P1→P0/P1、P2→任一）+ INBOX 既有站内信 + WECOM 解密直发（新增 `WecomRobotMessageSender.sendTo(webhook, content)` 公共方法），审计 WEBHOOK_SENT/SKIPPED/FAILED，渠道独立成败。无数据库迁移。

### T-02：后端单测

- 描述与目标：AgentRunNotifierTest 扩展（P0 默认仅 P0 触发、P1/P2 命中、仅 INBOX/仅 WECOM/双渠道、WECOM 无地址 SKIPPED、发送失败不阻断站内信）；AgentWorkbenchServiceTest 扩展（webhookUrl 加密落库可解密回显、旧构造兼容、Agent 配置非法级别/渠道被拦截）。
- 验收条件：
  - [ ] 全部通过且既有无回归。

计划验证方式（尚未执行）：同 T-01 + 相关套件抽查。

状态及真实结果：已完成（2026-11-16）。`AgentRunNotifierTest` 重写并扩展至 8 项（默认 P0 忽略仅 P1、P1/P2 级别命中、企微解密直发与 markdown 内容断言、无地址 SKIPPED、企微失败不阻断站内信、站内信异常不传播、非 COMPLETED/无标记不通知）；`AgentWorkbenchServiceTest` 新增 `taskWebhookUrlIsEncryptedAtRestAndRevealedOnDetail`（明文不落库、enc: 密文、详情回显明文），并适配 PaymentCryptoService 构造注入。定向结果：Notifier 8/8、Workbench 70（69+1，1 项既有跳过）、Autonomy 16、Inspection 27 全绿；`mvn compile` 全模块通过。

### T-03：前端对话框

- 描述与目标：AgentConfigDialog 增"预警级别"下拉（P0/P1/P2 默认 P0）与"预警方式"多选（站内信/企微机器人，默认站内信）；AgentTaskDialog 在所选 Agent 方式含企微时显示"预警 webhook"输入（可空，编辑回显明文），提交携带。
- 验收条件：
  - [ ] 表单字段与条件显示生效；`npm run build` 通过。

计划验证方式（尚未执行）：`npm run build`；部署后走查。

状态及真实结果：已完成（2026-11-16，分支 codex/development-20261103）。`AgentConfiguration` 类型加可选 alertLevel/alertChannels；AgentConfigDialog 新增"预警级别"下拉（P0 默认，附说明）与"预警方式"多选（站内信/企微机器人，默认站内信，附说明），存量配置兜底默认、提交校验至少一种方式；AgentTaskDialog 在 Agent 方式含 WECOM 时显示"预警 webhook"输入（编辑回显明文、提交 trim 空转 null）。`npm run build` 通过；页面走查待部署。

### T-04：验证与交付记录

- 验收条件：
  - [ ] 证据写入本文件；迭代日志/现状更新。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01/02/03/05 → T-01/T-02；AC-04 → T-01/T-02；AC-01 → T-03。
- 未覆盖项及原因：无。
- 完整规格统一确认：范围=proposal v1 + design v1 + tasks v1；用户于 2026-11-16 确认实施（前置决定：Agent 级方式配置、任务级 webhook 加密存储）；实施完成后证据见各任务明细。生产生效待部署（后端先发、前端随后；真实预警联调部署后进行）。

状态及真实结果（T-04）：已完成（2026-11-16）。证据写入本文件；迭代日志与现状已更新。
