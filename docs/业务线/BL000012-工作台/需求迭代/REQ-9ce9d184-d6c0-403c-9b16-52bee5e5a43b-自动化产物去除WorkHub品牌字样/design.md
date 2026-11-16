# Design：自动化产物去除 WorkHub 品牌字样

## 1. 变更总览

| 产物 | 位置 | 旧 | 新 |
| --- | --- | --- | --- |
| GitLab MR 标题 | `AgentDeliveryService` | `Draft: [WorkHub] {摘要}` | `Draft: {摘要}` |
| GitLab MR 描述 | 同上 | `验收建议（实际结果以 WorkHub 记录为准）：` | `验收建议：` |
| MR 归属标记 | 同上 | `WorkHub-Delivery: {id}` | `Delivery-Ref: {id}` |
| Git 提交身份 | `AgentCodeGit` | `WorkHub Agent <agent@workhub.invalid>` | `Agent <agent@noreply.invalid>` |
| Git 提交信息 | `AgentCodeWorkspaceService` | `WorkHub Agent task {id} revision {n}` | `Agent task {id} revision {n}` |
| notes 小节标题 | `ProjectKnowledgeBaseService` | `### WorkHub 临时分析 · …` | `### 临时分析 · …` |
| notes 幂等注释 | 同上 | `<!-- workhub-analysis:{指纹} -->` | `<!-- analysis-record:{指纹} -->` |
| 来源绑定目录 | 同上 | `requirement-sources/.workhub-analysis-sources/` | `requirement-sources/.analysis-sources/` |
| 归档文件头 | 同上 | `本文件由 WorkHub 在 AI 任务评估前自动归档` | `本文件由系统在 AI 任务评估前自动归档` |

禅道测试单/版本的 `desc="WorkHub 提测确认自动创建"` 移除见 REQ-a71851db design §11（后续变更⑤）。

## 2. 兼容设计（关键决策：双标记容忍，不回溯改写）

三处机器命名承担核验/幂等职责，改名必须兼容部署前已存在的记录，策略统一为"新写新名、读取双认、不迁移"：

- **MR 归属核验**（`AgentDeliveryService.verify`）：期望标记行匹配 `Delivery-Ref: {id}` **或** 旧 `WorkHub-Delivery: {id}`。MR 按候选/目标分支对查找，标记用于证明该 MR 确为本交付记录所建（区分人工 MR）；旧 MR 不改描述，核验语义不变，失配仍报"MR 关联标记或状态与候选交付不符"。
- **notes 幂等去重**（`appendAnalysisNote`）：指纹算法不变（输入不含标记名，跨版本指纹稳定），去重判断 `contains` 新注释**或**旧注释——部署前已追加的分析重试时不会重复追加。
- **来源绑定**（同方法内）：绑定文件名 `{intakeId}-{analysisId}-{family}.txt` 不变；读取时新目录缺失则回退旧目录 `.workhub-analysis-sources`，写入只落新目录。旧目录文件保留原样（不可变历史），新绑定逐步覆盖。

## 3. 不改的部分与理由

- WorkHub 自身界面/日志/提示词可见的"WorkHub"字样（错误信息、MCP 资源指引、AI 提示变量、SQL 注释、配置键、env 变量）：不在外部产物中，平台自称属事实陈述，保留。
- `SystemAlertCleanupRuleService` 的"由系统预警…自动创建"备注：无 WorkHub 字样，且属另一功能域。
- GitLab 已建 MR、业务仓库既有提交、知识库既有文件：历史产物不回溯修改（用户已知悉，兼容靠读取侧双认）。
- `AgentGitLabClientTest` 桩数据中的旧标记字符串：测试本地固定载荷，与生产行为无关，保留。

## 4. 验证

- `KnowledgeArchiveIntegrationTest`：并发追加计数断言改按 `<!-- analysis-record:`；既有用例 `migratedLegacySourcesAndBindingsSupportRetriesWithoutChangingSnapshots` 原样保留（其桩正写入旧目录 `.workhub-analysis-sources`，天然覆盖旧绑定回退读取路径）。
- Agent 域回归：`AgentGitLabClientTest`、`AgentChangeServiceTest`、`AgentCodeWorkspaceServiceTest`、`AgentGitPublishTest`。
- 提测链路回归：`IntakeSddFlowTest`（禅道 desc 移除，REQ-a71851db）。

## 5. 回滚

单提交代码变更，无数据库迁移；回滚即恢复品牌字样输出。改名期间新旧标记双认，回滚后新标记 MR（`Delivery-Ref:`）会被旧代码判失配——回滚前需确认无在途交付，或手工在 MR 描述补一行旧标记。
