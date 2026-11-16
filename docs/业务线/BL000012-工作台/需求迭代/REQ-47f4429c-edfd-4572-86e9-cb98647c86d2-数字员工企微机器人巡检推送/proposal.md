# Proposal：数字员工企微机器人巡检推送

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-47f4429c-edfd-4572-86e9-cb98647c86d2 |
| 需求名称 | 数字员工企微机器人巡检推送 |
| 涉及仓库与功能域 | workhub-server：MCP 企微机器人发送工具（service/mcp + system 配置）、数字员工与任务建档（运行环境操作）、测试 |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-47f4429c-edfd-4572-86e9-cb98647c86d2-数字员工企微机器人巡检推送/` |
| 版本/状态 | v1 已确认（2026-11-13 用户"可以，开工吧"；企微通道为新增受控 MCP 工具方案，手动触发为主、每轮都发，webhook 由用户提供） |
| 目标系统 | workhub-server 后端；运行环境需配置企业微信群机器人 webhook |
| 原始需求来源及版本 | 用户 2026-11-13："新增一个数字员工，去 ES 里检索特定的消息，汇总后发送到企微的机器人" |
| 目标环境/分支 | workhub-server `codex/development-20261102` |
| 负责人/评审人 | 用户 |

## 1. Why：背景与问题

- 当前问题及证据：数字员工平台已能按职责经 MCP 工具检索受控 ELK 范围（`search_business_logs`），但没有任何通道把汇总结果推送到企业微信群——运行通知只有站内信（`AgentRunNotifier`），现有 `WecomRobotNotifier` 为工作项专用（单一全局 yml webhook、消息模板硬编码），MCP 工具清单无企微发送能力。用户需要在群里直接看到巡检汇总。
- 受影响用户/角色：关注巡检消息的群成员；`agent:manage` / `agent:task:assign` 管理员；MCP 管理员。
- 不解决的后果：数字员工巡检结果仍需登录 WorkHub 查看站内信或运行报告，消息触达滞后。

## 2. What：目标与范围

### 本期目标

1. 为数字员工提供受控的企业微信群机器人消息发送能力（新增 MCP 工具），webhook 凭据加密存储、AI 不经手。
2. 建档一个数字员工及 MANUAL 任务：按职责描述去 ES（受控 ELK 范围）检索特定消息、汇总，每轮巡检都向指定企微群机器人发送汇总。

### 本期交付范围

1. `workhub-server`：新增受控 MCP 工具 `send_wecom_robot_markdown`（参数、边界、审计见 design）；webhook 以加密值存 `sys_config`，沿用现有加密通道，不进仓库。
2. 数字员工建档产物：职责描述文案、任务规格（objective/验收标准/范围/MANUAL 调度），经平台既有接口创建；检索范围明细由用户提供后填入职责描述。
3. 单元/服务测试与验证说明。

### 明确不做

1. 不改造 `WecomRobotNotifier` 及工作项通知链路（两者互不影响）。
2. 不做多机器人/按业务线分组路由（本期单机器人，后续需要再立项）。
3. 不新增企微其他消息类型（text/image 等），仅 markdown。
4. 不做巡检消息的二次确认或撤回机制。

### 约束与依赖

- 依赖的代码和文档：MCP 运行时（`McpRuntimeService`）、`SysConfigService` 加密值、CLI 渠道 MCP 注入（`AgentNativeCliAdapter`）、`search_business_logs` 工具。
- 业务/合规约束：webhook key 属敏感凭据，加密存储、不回显明文、不入仓库；企微机器人 webhook 有频控（每机器人每分钟不超过 20 条），工具侧须防刷。
- 既有系统/接口/数据约束：企微群机器人 markdown 消息 `content` 上限 4096 字节；ZCode 会话允许清单为 `McpRuntimeService.toolNames()` 全量，新工具自动对 CLI 数字员工可见。
- 外部依赖与前置条件：用户已提供群机器人 webhook；检索范围明细待用户提供（不影响通道开发，影响职责描述定稿）。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | MCP `tools/list` 出现 `send_wecom_robot_markdown`；合法调用（webhook 已配置）能向目标群发出 markdown 消息且返回企微 `errcode=0` | 运行环境实测 + 群内消息核对 | 待确认 |
| AC-02 | webhook 未配置时工具返回明确错误且不发送；内容为空或超 4096 字节被拒绝；单字段超限收敛 | 服务测试 | 待确认 |
| AC-03 | webhook 全链路不出现明文回显：配置读取经解密、审计不记录 webhook 与消息全文、接口不返回明文 | 代码核对 + 服务测试 | 待确认 |
| AC-04 | 数字员工与 MANUAL 任务创建成功；手动运行一轮后，无论有无命中都发出一条汇总消息（含检索窗口、命中概要、结论） | 运行环境实测 | 待确认 |
| AC-05 | 全量 `mvn test` 本需求范围 0 失败 | mvn test | 待确认 |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
