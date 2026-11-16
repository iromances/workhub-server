# Proposal：研发任务评估空拆解治理

## v2 补充：工具接入与执行失败诊断（2026-11-14）

- 来源：用户在获知工具连接和前缀修复后要求“修复下”，随后反馈重启重跑仍报 -32603，继续处理同一故障。
- 范围：沿用已存在的 MCP_CONNECT 和工具前缀修复；协议失败须返回请求阶段、错误码和脱敏错误说明，执行进度须留可定位日志，不保存完整协议、凭据或模型正文。
- 验收：-32603 不再仅显示错误码；连接、提交和格式校正请求可区分；错误中的凭据被掩盖，堆栈和 error.data 不直接输出；已有 CLI 隔离与任务执行测试继续通过。
- 证据边界：第 120 轮（20:35:40–20:45:53）服务日志包含 38 次仓库工具与 1 次需求读取，前序接入修复已生效；603692ms 后失败的原始 error.message 已被旧实现丢弃，不能据耗时认定超时根因。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb |
| 需求名称 | 研发任务评估空拆解治理 |
| 版本/状态 | v1 已确认，已实施；自动化验证通过，真实重跑待部署验证 |
| 目标系统 | WorkHub Server |
| 原始需求来源 | 2026-11-13 用户：202611050003 重跑了研发任务评估，还是没把任务评估写进去；任务拆解里没有任务 |
| 目标分支 | server：codex/development-20261102 |
| 评审人 | 用户（2026-11-13 批准实施计划） |

## 1. 背景（诊断证据）

需求 202611050003（intake 277，BL000011）重跑研发任务评估后任务拆解为空。证据：

- 运行库 `pm_intake_development_analysis`：intake 277 共 8 轮，5 轮状态 DRAFT 但 `workItems` 全为 `[]`，`summary` 均为模型中途叙述（如"我先梳理需求材料……下面开始代码检索"）；run 116 返回纯文本解析失败记 FAILED；run 114/115 报"ZCode 协议执行失败"（根因被摘要丢失）。
- 本地日志与 mcp-audit：run 117（62 秒）执行窗口内评估 Agent 的授权仓库工具（list_repository_files / read_repository_text_file / search_repository_text）**零调用**（窗口内的 search_business_logs 属巡检 Agent）。
- 机制：仓库调查场景不传 CLI schema（防阻断工具调用），模型跳过调查直接交"计划式"答复时，经一次无工具格式校正被包装成 schema 形状（空数组原样保留），管道视为成功 → 存成空拆解 DRAFT。

## 2. 目标与范围

1. 协议层：仓库调查会话终稿前必须有授权工具调用；零调用完成时自动续跑一轮（一次为限），要求先调查再交终稿。
2. 生成器层：研发评估（生成/SDD/调整）解析后任务拆解为空即显式失败，不再保存空评估。
3. 网关层：失败摘要附带根因消息，"ZCode 协议执行失败"类错误在页面可见真实原因。
4. 不回溯修改历史空拆解记录；不改澄清分析生成器的内容语义（其零工具续跑由协议层自然覆盖）。

## 3. 验收标准

| 编号 | 可观察的验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | 仓库调查会话零工具完成时发出一次续跑 send（新 inputId、指令含先调查后终稿），结果不落盘 | ZCodeTaskConversationTest |
| AC-02 | 续跑轮调用授权工具并交 JSON 后正常落盘；续跑仅一次，第二次零工具完成按普通终态收尾 | ZCodeTaskConversationTest |
| AC-03 | 普通会话（非仓库调查）零工具完成行为不变 | 既有 13 项协议用例回归 |
| AC-04 | 评估/调整返回空 workItems 时该轮 FAILED，报错含"AI 未产出任务拆解" | CodexCliDevelopmentAnalysisGeneratorTest |
| AC-05 | 网关失败摘要包含 cause 链根因（240 字符内） | DefaultAiGatewayClientTest 回归 |

设计与任务见 [design.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb-研发任务评估空拆解治理/design.md>)、[tasks.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb-研发任务评估空拆解治理/tasks.md>)。
