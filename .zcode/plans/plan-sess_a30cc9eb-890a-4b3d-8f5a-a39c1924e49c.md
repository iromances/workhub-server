# 研发任务评估空拆解治理（202611050003 重跑仍无任务）

## Context（诊断结论）

需求 202611050003（intake 277）重跑研发任务评估后任务拆解为空。证据链：

- DB `pm_intake_development_analysis`：intake 277 共 8 轮，5 轮 DRAFT 的 `workItems` 全为 `[]`，`summary` 全是 AI 的**中途叙述**（"我先梳理需求材料…下面开始代码检索"）；run 116 直接返回 76 字符纯文本（解析失败 FAILED）。
- 本地日志 + mcp-audit.jsonl：run 117（03:40:47–03:41:50 UTC）执行窗口内，评估 Agent 的授权工具（`list_repository_files`/`read_repository_text_file`/`search_repository_text`）**零调用**（窗口内的 search_business_logs 属巡检 Agent）。
- 机制：仓库调查场景不传 CLI schema（防阻断工具调用），`turn.completed` 的答复是叙述文本或“计划式 JSON"时，经一次无工具格式校正被包装成 schema 形状（空数组保留），管道视为成功 → 存成空拆解 DRAFT。即**模型跳过调查直接交“计划”，WorkHub 照单全收**。

## 修复方案（三层）

### 1. 协议层：零工具调查自动续跑一轮（`ZCodeTaskConversation`）

- 新增字段：授权工具调度计数（复用现有 `tool.updated`/`scheduled` 分支，非 FORMATTING 阶段累加）；可变的 `currentInputId`（续跑新 turn 的身份校验，`turn.started`/`requireTurn` 改用它）；`requireToolInvestigation` 标志**仅由 `structuredNoCliSchema` 工厂置 true**（仓库调查场景专用；普通 ZCode 会话与既有测试语义不变）。
- `turn.completed`(success) 时：若 `requireToolInvestigation && 工具计数==0 && 未续跑过` → 重新 `session/send` 续跑指令（"你尚未调用任何授权工具完成代码调查，请先用 workhub_task 工具检索代码，再输出符合 schema 的最终 JSON"），phase 回 SEND、换新 inputId，**一次为限**；续跑仍零工具则走既有流程（交给第 2 层拦截）。时间预算沿用原进程 deadline，不额外放宽。
- 续跑轮的工具计数累计，不重置。

### 2. 生成器层：空拆解拒绝保存（`CodexCliDevelopmentAnalysisGenerator.execute`）

- `normalizeWorkItems` 之后：`workItems` 为空 → 抛 `IllegalArgumentException("AI 未产出任务拆解（仅返回过程叙述或空结果），已拒绝保存空评估，请重跑研发任务评估")` → 该轮记 FAILED，页面可见明确报错（run 116 已验证此通路）。
- 对 generate / generateSdd / adjust 统一生效：调整返回空同样视为失败，防止把已有拆解清空。

### 3. 网关层：失败摘要带根因（`DefaultAiGatewayClient.summarizeException`）

- 现仅取 `ex.getMessage()`，cause 链丢失（run 114/115 只见"ZCode 协议执行失败"）。改为拼接根因消息（"…；原因: {root cause}"），保留 240 字符截断。

## 测试

- `ZCodeTaskConversationTest` 新增：①仓库调查会话零工具完成 → 发出续跑 send（新 inputId、内容含工具指令）、结果未落盘；②续跑后调用工具并交 JSON → 正常落盘，session/send 共 2 次；③续跑仅一次；④普通会话（非 structuredNoCliSchema）零工具完成不受影响（既有 11 项用例即回归）。
- 新增 `CodexCliDevelopmentAnalysisGeneratorTest`（intake 包，Mockito 桩 AiGatewayClient）：空 workItems JSON → 报"未产出任务拆解"；含任务 JSON → 通过。
- 回归：`DevelopmentAnalysisServiceTest`、`DefaultAiGatewayClientTest`、`IntakeSddFlowTest`。

## SDD 文档

- 新 REQ 目录 `docs/业务线/BL000012-工作台/需求迭代/REQ-{uuid}-研发任务评估空拆解治理/`（proposal/design/tasks，记录诊断证据与确认人=用户 2026-11-13）。
- `迭代日志.md` 加行；`现状.md` 评估链路相关小节（§11a）补记“空拆解拒绝保存、零工具调查续跑一轮”。

## 验证

1. `mvn compile` + 定向运行上述测试类。
2. 用户重启本地实例（顺带使今日品牌去除变更生效）后重跑 202611050003 研发任务评估：预期要么产出真实任务拆解，要么 FAILED 且报错可见可行动；不再出现“成功但空拆解”。
3. 既有空拆解历史行（110-113/117）不回溯修改，重跑产生新行。

## 边界

- 不改澄清分析生成器的结果语义（其零工具续跑由第 1 层自然覆盖，内容层不设空值硬校验）。
- 模型是否配合调查无法强制，本方案保证：给一次纠偏机会 + 假成功转为显式失败。