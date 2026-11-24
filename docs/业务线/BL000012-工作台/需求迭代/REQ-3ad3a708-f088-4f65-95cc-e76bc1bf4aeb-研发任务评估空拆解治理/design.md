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


## v3 草案设计：终稿校验可诊断与有界整理

- 对应本目录 Proposal v3；编码 REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb；仓库 workhub-server；业务线 BL000012；状态待确认。
- 将协议接收、格式校正、CLI 文件读取的最终结果上限提取为共享常量 24,000 UTF-8 字节，保留现有存储容量口径；用于一次格式校正的原始字符串输入上限单独设为 96,000 字节（拟议保护值，并非本次实际产物大小）。
- 先校验 response 是否存在、是否字符串、是否空白，分别失败；对超过最终上限且有结构化输出要求的字符串，进入既有 FORMATTING 通路。没有结构化输出要求的超长结果明确失败。调查续跑规则保持，禁止通过格式校正绕过调查要求。
- 有结构化要求且需要格式转换或长度压缩时，每会话最多一次 workspace/generateText，沿用所选模型、原 deadline、operationId、空工具列表和 8192 输出 token 预算。提示须要求保留任务、验收及关键事实，精简重复解释；无法容纳时明确失败，不直接截断。原文按 JSON 字符串作为数据传入。
- 格式校正返回时独立检查模型/阶段、finishReason、toolCalls、文本类型/大小、JSON 对象。非 stop 仍失败；不自动重试截断结果，不将 length 等终止原因当作成功。
- 所有错误摘要仅使用阶段、字段类型、字节数、限制、受控终止原因和工具数量/类型；不打印正文、提示词、凭据或完整协议。沿用网关摘要脱敏与长度限制。CLI 文件缺失、非普通文件、超长分别提供明确说明。
- 有效 JSON 仍交既有生成器做结构解析及空拆解校验。没有数据库/API/配置变更。无需引入新依赖；保留工作区既有改动。
- 风险：压缩仍可能失败或遗漏语义，需用含多任务与验收字段的夹具验证，并人工核对真实成功草稿；不承诺本次旧故障被追溯还原。若新证据需要提高最终上限或模型预算，再补充容量评估和规格确认。
- 交付：代码与离线测试通过后更新工作台现状与迭代日志；部署版本和真实重跑独立记录。回滚只撤本次代码差异，历史记录保持不变。


### v3.1 确认与堆栈展示补充（2026-11-20）

用户明确要求“要把错误堆栈显示出来，方便排查问题，然后把这个功能修复好”，据此确认 v3 修复与新增堆栈展示，开始实施。范围扩展至 workhub-web 的评估轮次详情与类型；后端保留网关异常 cause，失败时将脱敏堆栈（最多 12,000 字符，截断标识）写入独立 error_stack TEXT 字段，通过原鉴权的评估详情/历史响应 errorStack 返回；最新失败和历史轮次均可展开纯文本堆栈。历史空值明确无堆栈，不伪造。新增 Flyway 可空列迁移，需先于新代码启用，回滚应用时可保留该列。测试覆盖原因链传递、脱敏/长度、持久化与响应、协议边界及前端构建。不执行运行库手工 DDL，不安装依赖。既有“无数据库变更”描述由本补充覆盖。部署和真实评估仍须单独记录，不以离线测试宣称原需求已成功。

## v4 实施设计（2026-11-20）

对应 Proposal v4；唯一目录与编码不变。用户已在本聊天确认实施。

1. 两个分析服务使用 REQUIRES_NEW TransactionTemplate 包裹准备记录阶段，返回后才提交线程；队列拒绝在独立事务回写失败。后台 AI 调用不占用长事务。
2. 复用无工具结果整理协议，支持任意需求 schema，移除业务结果字节与输出 token 人为上限，length 时续接，有限次数格式纠错；保留仓库调查准入及非空任务校验。报告提示 ELI5，不简化事实或删除验收项。
3. 两个既有分析表新增 nullable raw_output LONGTEXT / input_fingerprint CHAR(64) 及 recovery_pending TINYINT NOT NULL DEFAULT 0；新生成器参数以服务端回调保存本轮原文、读取同需求失败记录。原文脱敏保存，不在日志输出。
4. 网关指纹覆盖模板渲染后的提示、schema、模型/推理、useCase 和仓库 HEAD；本地未提交变更或无法核实版本时不复用历史结果。恢复只走 workspace/generateText，禁止创建业务会话/连接仓库 MCP/重做调查。
5. 原文保存失败停止格式调用；模型失败、取消、解析失败仍记失败，不将空结果作为完成。数据库新增列先迁移再发布代码；本轮仅编写迁移并用隔离库验证，不执行运行库 DDL。
6. 设计完成前验证是否有可用数字员工及任务业务线/环境授权，明确错误提示，不自动选择员工或扩大授权。完整开发准入仍由实际任务创建复核。

交付：维护 Tasks 实际结果、现状及迭代日志。回退应用可保留新增可空列与原文，避免删除证据；真实模型效果及重启后验证单列。


v4 补充：仅同需求同指纹最新且 recovery_pending=1 的失败记录可恢复。格式成功立即清除标记；后续业务校验失败必须重新分析，不能回退更早原文。适用于三个 ZCode 仓库分析入口 intake.clarification.analyze、intake.development.analyze、intake.development.sdd-analyze；既有草稿对话调整未接入持久化恢复。原文已满足 schema 时直接校验落盘，否则无工具整理。回滚应用可保留新增三列。


## v5：AI 执行不设人为并发上限（2026-11-21）

实现：workhubTaskExecutor 使用可关闭的 SimpleAsyncTaskExecutor 开启虚拟线程，不设置 concurrencyLimit。AgentExecutionJob 不再读取 max-concurrent-runs；协调器持续领取可运行队列直到无可领取任务。AgentRunStore 保留数据库互斥和条件更新，取消配额计数。旧 maxConcurrentRuns 字段为存量 JSON/API 兼容保留但不参与调度，不再限制其数值；前端取消编辑该字段。实际硬件/供应商限制及运行超时保留。无需数据库迁移、依赖安装或运行配置修改。

v5 实施补充：候选运行查询仅纳入启用员工，领取时仍锁定并复核启用状态；单批查询 100 条后持续领取，不形成并发配额。共享执行器关闭时等待任务 5 秒，执行任务原有超时和取消机制保持。
