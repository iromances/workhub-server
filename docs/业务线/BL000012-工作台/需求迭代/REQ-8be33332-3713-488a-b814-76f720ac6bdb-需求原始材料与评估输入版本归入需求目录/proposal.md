# Proposal：需求原始材料与评估输入版本归入需求目录

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 需求原始材料与评估输入版本归入需求目录（REQ-8be33332-3713-488a-b814-76f720ac6bdb） |
| 涉及仓库与功能域 | workhub-server：研发评估与需求澄清的归档（`workhub-service` 的 `service/intake`、`service/mcp`，`workhub-dao` 附件查询，`workhub-mcp-server`）及本仓库文档；agents-playbook：仅补充 `docs/AGENTS.md` 说明和 `.gitignore`，无代码改动 |
| 所属项目或业务线 | 工作台（BL000012） |
| 版本 | v1.0 |
| 状态 | 已确认（用户 2026-11-25 在会话中确认，含 Design 第 6 节四点） |
| 目标系统 | WorkHub 后端 |
| 原始需求来源及版本 | 2026-11-25 用户在 WorkHub 仓库会话中逐点确认：归属沿用业务线分流；放在需求自己的目录下，每轮只存需求原文版本，附件按 ID 与 SHA-256 引用；`docs/notes.md` 的公司项目段删除 |
| 研发分支 | `codex/development-20261122` |
| 负责人 | AI（ZCode）实施 |
| 评审人 | 用户 |
| 版本关系 | 替代 REQ-898b9c28（研发产出回归项目仓库）中“原始材料进入 `docs/requirement-sources/`、过程记录进入 `docs/notes.md`”的位置约定；不可变版本、原子写入和归档锁机制沿用 |

## 1. Why：背景与问题

- 当前问题及证据：
  1. AI 评估前，系统把需求原文和附件写入 WorkHub 仓库 `docs/requirement-sources/{业务线}/{项目}/{年份}/{审批编号}/`，每轮再复制一遍附件。2026-11-25 核对时为 17 个需求、48 个输入版本、290 个文件、约 30 MB，其中约 150 个是附件副本；运行实例仍在写入。入口是 `ProjectKnowledgeBaseService.upsertRequirementIterationNote`。
  2. AI 评估并不读取这些文件：`runAnalysis` 把同一份需求 Markdown 字符串和附件摘要直接传给 `CodexCliDevelopmentAnalysisGenerator`。附件以数据库为准，MCP 已有 `get_intake_detail`、`get_intake_attachment`。
  3. 协作业务线的已确认评估已归档到 agents-playbook 的需求目录（现有 27 份 `研发评估.md`），原始材料却固定在 WorkHub 仓库（`docs/业务线/AGENTS.md`：“不随业务线归档根分流”），同一需求的材料分散在两个仓库。
  4. 每轮分析结果还镜像写入 WorkHub 仓库 `docs/notes.md` 的公司项目段（删除前约 7,200 行），与数据库中的历次分析记录重复。
- 受影响用户/角色：使用研发评估和需求澄清的人、在 agents-playbook 查阅需求资料的人和 AI。
- 不解决的后果：WorkHub 仓库持续累积业务线附件副本，需求材料与正式评估分离，`docs/notes.md` 持续增长。

## 2. What：目标与范围

### 本期目标

1. 原始材料和每轮评估的输入版本归档到需求自己的目录，与 `研发评估.md` 同级，并沿用业务线分流：WorkHub 平台自身（BL000012）在 WorkHub 仓库，其余业务线在 agents-playbook 仓库。
2. 每轮只保存需求原文版本；附件不落盘，按附件 ID 与 SHA-256 清单引用。
3. 不再向 `docs/notes.md` 追加临时分析记录。
4. 退役 `docs/requirement-sources/` 及其 MCP 路径约定；AI 读取需求和附件统一经 MCP 访问数据库。

### 本期交付范围

1. workhub-server 代码：`ProjectKnowledgeBaseService`、`RequirementMaterialExportService`、`RepositoryDocumentPaths`、`DevelopmentAnalysisService`、`IntakeClarificationAnalysisService`、`AttachmentService` 与 `AttachmentMapper`（新增附件元数据查询）、`McpResourceService`、`McpRuntimeService`、`McpToolRegistry` 及对应测试。
2. workhub-server 文档与配置：`AGENTS.md`、`docs/业务线/AGENTS.md`、`docs/研发规范/业务运维分析方法.md`、`docs/业务线/BL000012-工作台/现状.md` 第 10 节、迭代日志、`.gitignore`。
3. agents-playbook：`docs/AGENTS.md` 补充自动归档需求目录中 `原始材料/` 的说明；新增该仓库自己的 `.gitignore` 忽略归档锁文件。
4. 存量处理：新逻辑生效并核对后删除 `docs/requirement-sources/`；`docs/notes.md` 公司项目段已于 2026-11-25 删除（仅工作区，未提交）。

### 明确不做

1. 不把 `docs/requirement-sources/` 的存量文件迁入需求目录，历史按 git 历史定位。
2. 不改数据库结构，不批量改写存量 `draft_json` 中的旧路径。
3. 不改 AI 评估的提示词和生成器，传入的需求 Markdown 内容不变。
4. 不扩大知识检索范围，agents-playbook 不加入检索根。
5. 不改“需求本地文件夹”（`RequirementFolderService`，向桌面目录导出附件）和“打开开发方案文件夹”（`DevelopmentPlanFolderService`）。
6. 不改前端，草稿的 `requirementRawPath`、`requirementWikiPath`、`requirementWikiUrl` 字段保留。

### 约束与依赖

- 依赖的代码和文档：agents-playbook 独立仓库（`knowledge.project/playbookPath`，未配置时取 WorkHub 仓库同级的 `agents-playbook`）及其文档指引中的标准需求目录。
- 业务/合规约束：仓库安全规范，未经授权不安装软件、不重启运行实例、不修改运行配置。
- 既有系统/接口/数据约束：存量 `draft_json.requirementRawPath` 为 `requirement-sources/…` 或更早的 `raw/…`；已发布的 `研发评估.md` 内含指向旧路径的链接，按原字节保留。
- 外部依赖与前置条件：代码生效需重新加载运行实例；删除存量目录须在新逻辑生效并核对之后。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 新评估或澄清一轮后，需求目录下出现 `原始材料/source.md` 和 `原始材料/历史版本/{版本ID}/` 下的 `source.md`、`manifest.json`；BL000012 落在 WorkHub 仓库，其余业务线落在 agents-playbook 仓库 | 集成测试（临时目录）；生效后实跑一轮核对 | 用户（2026-11-25 确认规格） |
| AC-02 | 需求目录内不出现附件文件；`manifest.json` 的 `attachments` 列出该需求全部截图与附件的 ID、文件名、大小、SHA-256，与数据库一致 | 集成测试比对附件记录 | 用户（2026-11-25 确认规格） |
| AC-03 | 输入与最近一个版本完全相同时不新增版本，复用已有版本 ID | 单元测试 | 用户（2026-11-25 确认规格） |
| AC-04 | 各流程执行后 `docs/notes.md` 内容不变，`docs/requirement-sources/` 无新写入 | 集成测试断言 | 用户（2026-11-25 确认规格） |
| AC-05 | 输入版本归档失败（目录不可写、agents-playbook 不可用）不阻断 AI 评估，失败写入需求历史 | 集成测试 | 用户（2026-11-25 确认规格） |
| AC-06 | 存量草稿的旧路径在确认发布和重试归档时不报错；来源文件缺失时发布正文注明“未留存” | 单元与集成测试 | 用户（2026-11-25 确认规格） |
| AC-07 | MCP 目录和上下文不再提供 `businessLineRawRoot`、`temporaryAnalysisPath`；`requirementRawPathPattern`、`wikiPublishRule` 与新规则一致；两个 MCP 入口同步 | MCP 单元测试 | 用户（2026-11-25 确认规格） |
| AC-08 | AI 评估收到的需求 Markdown 中，需求原文与元信息字段与改动前一致；差异仅限“项目文档原始材料版本”一行、路径元信息的取值和结尾说明文字 | 单元测试 | 用户（2026-11-25 确认规格） |
| AC-09 | 现行指引（`AGENTS.md`、`docs/业务线/AGENTS.md`、`业务运维分析方法.md`、`现状.md`）不再链接 `docs/requirement-sources/`；新逻辑核对后该目录已删除 | 链接检查与 `git status` | 用户（2026-11-25 确认规格） |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
