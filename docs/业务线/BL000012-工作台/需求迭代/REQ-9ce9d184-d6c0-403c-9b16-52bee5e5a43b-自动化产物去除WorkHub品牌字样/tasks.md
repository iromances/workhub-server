# Tasks：自动化产物去除 WorkHub 品牌字样

## 1. 元信息

- 需求编码：REQ-9ce9d184-d6c0-403c-9b16-52bee5e5a43b
- 目标系统：WorkHub Server
- Proposal / Design：v1，见 [proposal.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-9ce9d184-d6c0-403c-9b16-52bee5e5a43b-自动化产物去除WorkHub品牌字样/proposal.md>)、[design.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-9ce9d184-d6c0-403c-9b16-52bee5e5a43b-自动化产物去除WorkHub品牌字样/design.md>)
- 目标分支：server `codex/development-20261102`
- 当前状态：已实施，自动化验证通过；真实 GitLab MR / 知识库归档效果待部署后观察

## 2. 确认记录

| 日期 | 范围/版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-11-13 | 清理范围：GitLab MR 标题/描述、Git 提交人与提交信息、知识库/归档文案——全部；机器标记（WorkHub-Delivery、workhub-analysis）也去掉（含核验/去重逻辑改造与历史兼容） | 用户 | 会话逐项选择确认，按 v1 规格实施 |

## 3. 任务与依赖

| 任务 | 交付物 | 依赖 | 验收映射 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | `AgentDeliveryService`：MR 标题去前缀、描述去"以 WorkHub 记录为准"、标记改 `Delivery-Ref: {id}` 并双标记核验 | 规格确认 | AC-01、02 | 已完成 |
| T-02 | `AgentCodeGit` 提交身份改 `Agent <agent@noreply.invalid>`；`AgentCodeWorkspaceService` 提交信息改 `Agent task …` | 规格确认 | AC-03 | 已完成 |
| T-03 | `ProjectKnowledgeBaseService`：小节标题、幂等注释、绑定目录、归档文件头四处去品牌 + 旧记录兼容 | 规格确认 | AC-04、05、06 | 已完成 |
| T-04 | 测试断言更新与定向回归、文档记录 | T-01～03 | AC-01～06 | 已完成 |

修改范围：server 的 `AgentDeliveryService`、`AgentCodeGit`、`AgentCodeWorkspaceService`、`ProjectKnowledgeBaseService`、`KnowledgeArchiveIntegrationTest`；另含禅道链路 `ZentaoClient`（两处 desc 载荷，记入 REQ-a71851db 后续变更⑤）。其他已有未提交修改保持原状。

## 4. 实际结果（2026-11-13）

- 定向回归 94/94 通过：KnowledgeArchiveIntegrationTest 12/12（含旧目录绑定回退用例）、AgentGitLabClientTest 11/11、AgentChangeServiceTest 42/42、AgentCodeWorkspaceServiceTest 12/12、AgentGitPublishTest 2/2、IntakeSddFlowTest 15/15；`mvn compile`/`test-compile` 通过。
- 未覆盖项：真实 GitLab 建 MR（新标题/标记落地效果）、真实知识库归档（新目录落盘）待部署后观察；部署前已建的带旧标记 MR 与旧目录绑定依赖读取侧双认兼容，已由代码路径与桩用例覆盖。
- 系统设计更新：现状.md 无受影响章节（其未记载品牌字样细节，本次说明原因不修改）；迭代日志已记录。
