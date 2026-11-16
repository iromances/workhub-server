# Design：研发任务评估空拆解治理

## v2：保留可用的失败上下文

- ZCode MCP 会话在订阅后执行 mcp/list(mode=connect)，确认连接与非零工具数量再发送任务；白名单名称统一加 mcp__workhub_task__ 前缀（工作区已有修复）。
- response 的错误分支按固定请求 ID 映射协议方法，保留当前 phase 和错误码；message 先替换本次令牌，再复用 ServerFileContentMasker 脱敏、压缩空白并限制为 120 字。不输出 error.data、stack 或原始协议。
- ZCodeCliClient 记录状态机返回的固定进度消息及受控工具名，补齐执行阶段证据；不记录模型输出或请求参数。
- 不放宽时间预算、不自动重放评估、不修改运行配置。此变更解决错误上下文丢失，不宣称已消除第 120 轮的内部错误。

## 1. 三层防御

| 层 | 文件 | 机制 |
| --- | --- | --- |
| 协议层 | `service/ai/ZCodeTaskConversation` | 仓库调查会话（`structuredNoCliSchema` 工厂构造，仅 ZCode 仓库读取场景使用）新增 `requireToolInvestigation`；`tool.updated`(scheduled) 授权工具计数；`turn.completed`(success) 时零调用且未续跑过 → 重发 `session/send` 续跑指令（新 inputId、phase 回 SEND），一次为限 |
| 生成器层 | `service/intake/CodexCliDevelopmentAnalysisGenerator` | `execute` 解析并归一化后 `workItems` 为空 → 抛 `IllegalArgumentException("AI 未产出任务拆解（仅返回过程叙述或空结果），已拒绝保存空评估，请重跑研发任务评估")`，对生成/SDD/调整统一生效 |
| 网关层 | `service/ai/DefaultAiGatewayClient.summarizeException` | 沿 cause 链取根因，非空且不同于首层消息时拼接"；原因: {根因}"，保留 240 字符截断与空白归一 |

## 2. 协议层细节（关键决策）

- **续跑只对仓库调查场景生效**：`requireToolInvestigation` 仅由 `structuredNoCliSchema` 工厂置 true。普通 ZCode 会话（含非仓库结构化场景，其测试用"有 toolNames 但零调用完成"的语义）完全不变——这是显式标志而非按 toolNames 推断的原因。
- **身份校验跟随最新 send**：`currentInputId` 可变字段（初始为首轮 inputId），`turn.started` 与 `requireTurn` 均改用它；续跑开启新 turn（新 inputId/turnId）时校验自然对齐，协议严格性不降。
- **工具计数累计不重置**：续跑轮的调查与前轮合并计算，续跑后仍零工具即按普通终态收尾（交给生成器层拒绝）。
- **时间预算不变**：续跑沿用原进程 deadline（`ZCodeCliClient.execute` 的秒级预算），不放宽；FORMATTING 校正、wh-send ACK 竞态（先终态后 ACK）等既有行为不变。
- 续跑指令内容：明确"此前的答复只是过程计划，不能作为终稿；先用 workhub_task 只读仓库工具检索代码，再输出最终 JSON"。

## 3. 生成器层边界

- 空拆解在研发评估域永远不合法：确认研发评估创建任务、SDD 确认指派负责人都以 ≥1 任务为前提；"无研发任务"的诉求应走澄清提问或需求类型修正，而不是保存空评估。
- 调整（adjust）同样拒绝空拆解，防止一次调整把既有拆解清空。
- 归一化过滤（title 为空的任务项被剔除）后为空同样触发拒绝——覆盖"模型交了全空 title 任务"的退化形态。

## 4. 兼容与回滚

- 无数据库变更、无接口契约变更。普通 ZCode 调用路径零改动。
- 回滚即恢复"零工具/空拆解可成功"的旧行为；无状态残留（续跑是进程内行为）。

## 5. 验证

- `ZCodeTaskConversationTest` 15 项：新增零工具续跑两用例（续跑后调查成功落盘 / 仅一次），既有 13 项回归保证普通会话语义不变。
- `CodexCliDevelopmentAnalysisGeneratorTest` 3 项：新增生成与调整的空拆解拒绝两用例。
- `DefaultAiGatewayClientTest` 19 项、`DevelopmentAnalysisServiceTest` 16 项、`IntakeSddFlowTest` 15 项、`ZCodeCliClientTest` 7 项回归。
- 真实链路：部署重启后重跑 202611050003——预期产出真实任务拆解，或 FAILED 且报错可见（如"AI 未产出任务拆解…"或带根因的协议错误）。
