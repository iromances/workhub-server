# Proposal：自动化产物去除 WorkHub 品牌字样

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-9ce9d184-d6c0-403c-9b16-52bee5e5a43b |
| 需求名称 | 自动化产物去除 WorkHub 品牌字样 |
| 版本/状态 | v1 已确认，已实施；自动化验证通过，真实链路待部署观察 |
| 目标系统 | WorkHub Server |
| 原始需求来源 | 2026-11-13 用户：需求管理自动向 WorkHub 提测或向禅道建任务时不要带上"WorkHub 提测确认自动创建"这类备注；追问后确认全部自动化产物的 WorkHub 字眼都去掉，含两处功能性机器标记 |
| 目标分支 | server：codex/development-20261102 |
| 评审人 | 用户（2026-11-13 会话确认清理范围"全部"、机器标记"也去掉"，见 Tasks 确认记录） |

## 1. 背景

WorkHub 自动化链路在多个外部产物中写入品牌字样：禅道自动创建的测试单与版本携带 `desc="WorkHub 提测确认自动创建"`；Agent 交付创建的 GitLab MR 标题带 `Draft: [WorkHub]` 前缀、描述含"以 WorkHub 记录为准"与 `WorkHub-Delivery: {id}` 机器标记；Agent 代码提交的作者为 `WorkHub Agent <agent@workhub.invalid>`、提交信息为 `WorkHub Agent task …`；知识库归档的 notes.md 小节标题为"WorkHub 临时分析"、幂等注释为 `<!-- workhub-analysis:… -->`、来源绑定目录为 `.workhub-analysis-sources`、原始材料归档文件头为"本文件由 WorkHub … 自动归档"。

用户要求这些自动化产物不携带 WorkHub 品牌字样（禅道提测部分见 [REQ-a71851db 后续变更⑤](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-a71851db-1d1c-4f20-b99e-330950af6ad9-需求任务评估SDD化与AI开发提测流程升级/design.md>)，本需求覆盖其余部分）。

## 2. 目标与范围

1. GitLab MR：标题去掉 `[WorkHub]` 前缀；描述去掉"以 WorkHub 记录为准"字样；归属核验标记改为无品牌的中立名。
2. Agent 代码提交：作者/提交者改为 `Agent <agent@noreply.invalid>`，提交信息改为 `Agent task {id} revision {n}`。
3. 知识库归档：notes.md 小节标题改为"临时分析"、幂等注释改为 `<!-- analysis-record:… -->`、来源绑定目录改为 `requirement-sources/.analysis-sources`、原始材料归档文件头"由 WorkHub"改为"由系统"。
4. 历史兼容：MR 归属核验同时接受新旧标记；notes.md 幂等去重同指纹新旧注释皆认；来源绑定读取回退旧目录——保证部署前已创建的 MR、已追加的 notes、已写入的绑定继续可用，不回溯修改历史产物。
5. 不改内部提示语、错误信息、配置键、SQL 注释等仅在 WorkHub 自身界面/日志可见的文本；不改数据库结构；不回溯修改 GitLab 已建 MR、业务仓库既有提交与知识库既有文件。

约束：不安装依赖，不自行启动 WorkHub 应用。

## 3. 验收标准

| 编号 | 可观察的验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | 新建 MR 标题为 `Draft: {摘要}`，描述含 `Delivery-Ref: {id}` 标记与"验收建议："字样，无任何 WorkHub 字样 | 代码与测试核对 |
| AC-02 | 已带 `WorkHub-Delivery: {id}` 旧标记的 opened MR 仍通过归属核验，不被误判冲突 | 代码路径核对（新旧标记皆认） |
| AC-03 | Agent 候选提交的作者/提交者为 `Agent <agent@noreply.invalid>`，提交信息为 `Agent task {id} revision {n}` | Agent 域测试回归 |
| AC-04 | notes.md 新追加小节标题为"临时分析"，幂等注释为 `<!-- analysis-record:{指纹} -->`；同指纹旧注释 `<!-- workhub-analysis:{指纹} -->` 仍触发去重不重复追加 | KnowledgeArchiveIntegrationTest |
| AC-05 | 来源绑定新写入 `requirement-sources/.analysis-sources/`；旧目录 `.workhub-analysis-sources/` 下的绑定仍可读取关联 | KnowledgeArchiveIntegrationTest（旧目录桩用例） |
| AC-06 | 原始材料归档文件头为"本文件由系统在 AI 任务评估前自动归档…" | 代码核对 |

设计与任务见 [design.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-9ce9d184-d6c0-403c-9b16-52bee5e5a43b-自动化产物去除WorkHub品牌字样/design.md>)、[tasks.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-9ce9d184-d6c0-403c-9b16-52bee5e5a43b-自动化产物去除WorkHub品牌字样/tasks.md>)。
