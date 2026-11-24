# Proposal：数字员工API通道报告ELI5

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-ec539bf4-eb7d-4e67-a55c-64036eb8fb09 |
| 需求名称 | 数字员工API通道报告ELI5 |
| 涉及仓库与功能域 | workhub-server（AgentApiExecutionAdapter 报告提示；无前端/迁移/配置变更） |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-ec539bf4-eb7d-4e67-a55c-64036eb8fb09-数字员工API通道报告ELI5/ |
| 版本/状态 | v1.0 已确认（用户 2026-11-22 会话指示「数字员工报告的要求，用ELI5的方式」；实施计划经用户批准） |
| 目标环境/分支 | codex/development-20261102 |
| 任务来源 | REQ-5fbc863d（报告 ELI5 与无长度限制）的通道补齐 |

## 1. Why

REQ-5fbc863d 已把 ELI5 应用于 CLI/ZCode 通道报告（`AgentNativeCliAdapter.prompt()` 追加）与格式整理（`AgentResultFormatter` summary）；但 **API 通道** `AgentApiExecutionAdapter.prompt()` 仅渲染 `agent.api.step` 模板（生产 v4 实查不含 ELI5），其 RESULT/NEEDS_INPUT 的 summary 即用户看到的报告，缺少 ELI5 要求，两通道报告文风不一致。

## 2. What 与 Done

API 通道步进提示在模板渲染后追加 ELI5 报告要求，与 CLI 通道同口径：summary 用 ELI5 简单中文（先说结论→发生了什么/影响/下一步；术语首现解释；区分已确认与推测；保留完整证据；不限长度）。

| 编号 | 验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | API 通道发出的模型 prompt 包含 ELI5 要求及「先说结论」要点 | 单测（捕获 executeAgentApi 请求） |
| AC-02 | 不改 `agent.api.step` DB 模板（页面维护优先，代码追加与 CLI 先例一致）；不动 CLI 通道/整理器/需求管理分析（均已有 ELI5） | 代码走查 |

# Design（同文件承载）

- `AgentApiExecutionAdapter.prompt()`（promptRenderer.render 返回后）追加一句 summary ELI5 要求，注释注明与 `AgentNativeCliAdapter` 同口径。回滚：还原该文件。
- 测试：`AgentWorkbenchServiceTest` 新增 `apiPromptRequiresEli5SummaryReport`，仿 `platformGuideInjectedIntoPromptWithFallbackWhenAbsent` 用 mock `AiGatewayClient.executeAgentApi` 捕获 prompt 断言。
- 无迁移、无配置变更、无前端改动；部署后端即生效。

# Tasks

| 任务 | 状态 |
| --- | --- |
| T-01 API 通道 prompt 追加 ELI5 要求 | 已完成 |
| T-02 单测 + 归档 | 已完成 |

## 实施证据（2026-11-22）

- 代码：`AgentApiExecutionAdapter.prompt()` 渲染 `agent.api.step` 模板后追加「summary 必须使用 ELI5 风格：像给第一次接触这件事的人解释，用简单中文先说结论，再说发生了什么、有什么影响、下一步怎么办；术语第一次出现时解释，明确区分已确认和推测，保留完整证据，不为简短删掉问题，不限制长度。」
- 测试：`AgentWorkbenchServiceTest` 76 项 0 失败（1 项既有真实 CLI 跳过），新增 `apiPromptRequiresEli5SummaryReport`（捕获 `AiAgentApiRequest.prompt()` 断言含「ELI5」「先说结论」，且运行正常 COMPLETED）；`mvn -pl workhub-service -am test -Dtest='AgentWorkbenchServiceTest'` 通过。
- 待部署走查：API 通道数字员工跑一轮任务，summary 报告文风为 ELI5 简单中文。

确认记录：用户 2026-11-22 提出 ELI5 指示；实施计划（规格）经用户批准后实施。口径沿用 REQ-5fbc863d。
