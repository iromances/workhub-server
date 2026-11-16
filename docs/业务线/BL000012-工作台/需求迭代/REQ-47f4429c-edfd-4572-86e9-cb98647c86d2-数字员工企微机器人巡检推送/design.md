# Design：数字员工企微机器人巡检推送

| 项目 | 内容 |
| --- | --- |
| 需求编码与唯一需求目录 | REQ-47f4429c-edfd-4572-86e9-cb98647c86d2；`docs/业务线/BL000012-工作台/需求迭代/REQ-47f4429c-edfd-4572-86e9-cb98647c86d2-数字员工企微机器人巡检推送/` |
| 涉及仓库与功能域 | workhub-server：`workhub-service`（MCP 工具注册与企微发送、系统配置）、`workhub-bootstrap`（如需迁移）、测试 |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1 草案 |
| 状态 | v1 已确认（2026-11-13 用户确认实施） |
| 当前事实核对环境、分支、时间 | workhub-server `codex/development-20261102`，2026-11-13 源码核对 |

## 1. 现状与证据

- 数字员工执行模型：Agent 配置（`AgentConfiguration`：name/instructions/providerConfigId/scopes/maxSteps/maxRunSeconds/maxConcurrentRuns）+ 任务（`AgentTaskSpecification`：objective/context/acceptanceCriteria/expectedArtifacts/scopes/completionTargets/analysisScope）+ cron 或 MANUAL 调度（`AgentSchedule.validate`）。`AgentExecutionJob` 每 5 秒分发，CLI 渠道（Codex/ZCode）经 `AgentNativeCliAdapter` 注入 MCP 端点与 Token，AI 按职责自行选工具（`docs/mcp-server.md`"接入方式"节）。
- ES 检索能力已存在：MCP 工具 `search_business_logs`（`McpRuntimeService.registerBusinessLogTools`），按业务线/环境受控 ELK 范围检索，支持服务/级别/时间窗/短语/traceId/requestId/limit，结果脱敏并写审计。
- 企微现状：仅 `WecomRobotNotifier`（`integration/wecom`），webhook 取 `workhub.wecom.robot.webhook`（application.yml，`enabled=false` 默认关闭），消息模板硬编码服务工作项通知；`AgentRunNotifier` 只发站内信。MCP 无任何写外渠道的工具（禅道/Jenkins 工具明确只读）。
- 配置加密能力已存在：`SysConfigService` 支持 `valueType=SECRET` 加密配置（`cryptoService.encrypt/decrypt`、`requireSecretValue(configGroup, configKey)`、响应仅回 `maskedValue`）。
- 事实来源：`workhub-service/src/main/java/cn/aslight/workhub/service/mcp/McpRuntimeService.java`、`service/system/SysConfigService.java`、`integration/wecom/WecomRobotNotifier.java`、`workhub-model/.../agent/AgentTaskSpecification.java`、`docs/mcp-server.md`。
- 尚未核实的假设：企微群机器人对 markdown 的 4096 字节上限与每机器人每分钟 20 条频控以官方文档口径实施，实测可能在验证阶段微调提示词压缩策略。

## 2. 系统架构

- 总体方案：新增一个受控 MCP 工具 `send_wecom_robot_markdown`，作为数字员工唯一企微出口；webhook URL 存加密系统配置，AI 只能传消息内容；数字员工与 MANUAL 任务按平台既有建档流程创建，职责描述驱动"检索→汇总→发送"。
- 数据流：手动触发任务 → `AgentExecutionCoordinator` 启动 CLI 运行 → AI 调 `search_business_logs`（按职责中的检索范围与 dataWindow 时间窗）→ 汇总 markdown → 调 `send_wecom_robot_markdown` → 工具读解密 webhook → POST 企微 `cgi-bin/webhook/send` → 透传 errcode → AI 报告发送结果 → `pm_agent_run` 留痕。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| `send_wecom_robot_markdown`（MCP 工具） | 校验内容、读解密 webhook、发送 markdown、透传企微结果、审计 | 生成内容、管理 webhook、重试 | 入：content；出：企微 errcode/errmsg | `SysConfigService.requireSecretValue`、HttpClient、`McpAuditLogger` |
| 加密系统配置 `wecom.agent.webhook` | webhook 凭据存储（SECRET 类型，加密+掩码） | 向 AI 或接口回显明文 | 管理员经系统配置维护 | `cryptoService`（master key） |
| 数字员工/任务 | 按职责完成检索、汇总、调用发送 | 绕过工具直发、修改配置 | 平台既有 Agent/任务模型 | `search_business_logs`、`send_wecom_robot_markdown` |
| 前端 workhub-web | 无新增页面；数字员工/任务经现有页面维护 | 企微发送入口 | 复用现有 | 无 |

## 3. 契约与数据模型

- 新增 MCP 工具 `send_wecom_robot_markdown`（注册于 `McpRuntimeService`，沿用 `objectSchema` 模式；文档同步 `docs/mcp-server.md` 工具清单）：
  - 入参：`content`（必填字符串，markdown 消息正文；UTF-8 编码后 1–4096 字节，空/超限拒绝）。
  - 出参：`{"sent": true, "errcode": 0, "errmsg": "ok"}`；webhook 未配置返回明确错误（"企微机器人 webhook 未配置"）；HTTP 失败或企微 `errcode!=0` 时 `sent=false` 并透传 errcode/errmsg，不抛出掩盖原因的异常。
  - 幂等/重试：工具层不重试不幂等去重（手动巡检低频，重复发送由 AI 与职责约束避免）；企微侧频控（约 20 条/分钟/机器人）超限时以 `sent=false` + 企微 errmsg 返回。
- 系统配置新增条目（数据归属：`sys_config` 表，无新增表与迁移脚本；经系统配置维护接口或数据库后台录入，`valueType=SECRET`）：
  - 分组 `wecom.agent`，键 `webhook`，名称"数字员工企微机器人 webhook"；运行库手工录入，不入仓库、不写迁移明文。
  - 未配置 = 功能未启用：工具返回明确错误，不静默吞掉。
- 数字员工与任务建档（运行环境操作，产品数据非代码）：`POST /api/agents` + `POST /api/agents/{id}/tasks`，规格见 tasks T-04；MANUAL 调度（enabled=false、无 cron）、`expectedArtifacts=[REPORT]`（不设 completionTargets）、`analysisScope=INCREMENTAL`（手动运行间隔即增量窗口）。

## 4. 非功能约束

- 安全与权限：webhook 属凭据，SECRET 加密存储、响应仅掩码、审计与日志不落明文；工具经 MCP Token 鉴权（`ROLE_MCP`）与既有审计；内容侧复用巡检职责要求脱敏（密码/token/账号不外发），工具层不重复实现全文脱敏（`search_business_logs` 已在查询侧脱敏）。
- 性能/容量：单次发送为一次同步 HTTP 调用，超时取固定 10 秒；content 上限 4096 字节，工具描述与职责要求超限时由 AI 压缩明细保留统计。
- 失败处理：发送失败不抛中断运行——返回 `sent=false`，AI 在报告中说明失败原因；不自动补偿重发。
- 日志/审计：沿用 `McpAuditLogger`（工具、内容字节数、发送结果、耗时；不记录 webhook 与 content 全文）；每次建档与运行留平台既有轨迹。

## 5. 交付与回滚

- 发布顺序：仅后端发布（无前端、无数据库迁移）；发布后在运行库录入 webhook 配置，再建档数字员工与任务。
- 灰度/开关：webhook 未配置即天然关闭；已配置后如需紧急停用，将 `wecom.agent.webhook` 配置停用（enabled=false）即可，无需回滚代码。
- 回滚：代码回滚仅移除工具；`sys_config` 加密条目可停用保留，数字员工/任务可 disable。
- 生效后更新：`docs/mcp-server.md` 工具清单与安全边界说明、`docs/业务线/BL000012-工作台/现状.md` 数字员工章节、迭代日志、本需求验证说明。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → 设计第 2/3/4 节（工具+配置+安全）；目标 2 → 设计第 3 节建档规格 + tasks T-04/T-05；AC-01..05 映射 tasks T-01..T-06。
- 非范围保持：不改动 `WecomRobotNotifier`、无多机器人路由、仅 markdown、无撤回机制。
- 待确认问题：①检索范围明细（业务线/环境/服务/级别/关键词/时间窗策略）待用户提供后填入职责描述；②数字员工名称与挂靠 AI 接入配置（providerConfigId）以运行库现有巡检配置为准，建档时核对。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)、`docs/mcp-server.md`。
