# Design：需求原始材料与评估输入版本归入需求目录

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 需求原始材料与评估输入版本归入需求目录（REQ-8be33332-3713-488a-b814-76f720ac6bdb） |
| 涉及仓库与功能域 | workhub-server：归档服务、两个分析服务、附件元数据查询、MCP 目录与上下文；agents-playbook：仅 `docs/AGENTS.md` 和 `.gitignore` |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随 Proposal 一并确认） |
| 当前事实核对环境、分支、时间 | 2026-11-25；workhub-server 分支 `codex/development-20261122`，HEAD `e7b7b7b`；只读核对源码和本机文件，未查询运行库 |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 输入归档：`DevelopmentAnalysisService.runAnalysis`（约 L950）和 `IntakeClarificationAnalysisService.runAnalysis`（约 L221）在调用 AI 前执行 `ProjectKnowledgeBaseService.upsertRequirementIterationNote`（L165）：写 `source.md`，经 `RequirementMaterialExportService.synchronizeVersioned` 导出附件，写 `历史版本/{UUID}/` 快照和 `manifest.json`，更新 `index.md`。失败时抛出“原始材料归档失败，尚未执行 AI 分析”。
  - 结果归档：`scheduleArchive`（生成研发评估、AI 调整研发拆解、调整研发负责人、调整研发任务、确认研发评估）和澄清的生成、回复，都经 `archiveAfterCommit` 调用 `appendAnalysisNote`，向 `docs/notes.md` 公司项目段追加记录并写 `.analysis-sources/` 绑定；已确认的走 `publishConfirmedAnalysis`。
  - 重评与迟到回调：重评前把旧结果追加到 notes；状态已被更新的迟到回调也只追加到 notes。
  - 路径：`resolveRequirementIterationLocation` 计算 `rawPath`（WorkHub 仓库 `docs/requirement-sources/…`）和 `wikiPath`（按业务线编码分流），需求目录取 `wikiPath` 的上级，`研发评估.md` 和 SDD 三件套在此。
  - MCP：`McpResourceService.knowledgeCatalog` 提供 `requirementRawPathPattern`、`temporaryAnalysisPath`、`wikiPublishRule`；`McpRuntimeService` 和 `McpToolRegistry` 的 `knowledgeContext` 提供 `businessLineRawRoot`。
- 当前接口、数据及配置：`POST /api/intake-records/{id}/knowledge-archive` 重试归档；`pm_intake_development_analysis`、`pm_intake_clarification_analysis` 每次执行插入新行，重评只把执行中的行置为失败，已有结果行保留；`draft_json.requirementRawPath` 记录来源版本路径；`pm_intake_history` 只存动作摘要。配置项只有 `knowledge.project` 下的 `repositoryPath`、`playbookPath`、`vaultPath`、`sddProcessPath`，没有归档开关。
- 事实来源：上述源码文件；本机 `docs/requirement-sources/`（2026-11-25 核对：17 个需求、48 个输入版本、290 个文件，运行实例仍在写入）和 agents-playbook 仓库（27 份 `研发评估.md`）。
- 目标系统当前实现证据及本次重新核对结果：评估生成器 `CodexCliDevelopmentAnalysisGenerator` 只接收 Markdown 字符串和附件摘要，不读取归档文件；前端只用 `requirementWikiUrl` 渲染链接。
- 尚未核实的假设：无。运行库中带旧路径的评估行数量、存量附件 `file_sha256` 是否齐全，已于 2026-11-25 只读核对，结果见 [Tasks](tasks.md) 与 [validation.json](验证/validation.json)。

## 2. 系统架构

- 总体方案和设计目标：输入版本随需求目录归档，只保存文本；附件留在数据库；AI 评估不依赖归档文件。

```text
{审批编号}-{需求名称}/
├── 研发评估.md                         已确认评估（现有）
├── proposal.md / design.md / tasks.md  SDD 模式确认后发布（现有）
└── 原始材料/
    ├── source.md                       最近一轮输入
    └── 历史版本/{版本ID}/
        ├── source.md                   不可变
        └── manifest.json
```

- 需求目录位置沿用 `wikiPath` 的分流：BL000012 在 WorkHub 仓库 `docs/业务线/BL000012-工作台/需求迭代/{项目}/{年份}/{审批编号}-{需求名称}/`；其余业务线在 agents-playbook 仓库 `docs/业务线/{业务线编码}-{业务线名称}/需求/{审批编号}-{需求名称}/`。
- `manifest.json`：`intakeId`、`versionId`、`recordedAt`、`sourceSha256`、`attachments[]`（`id`、`fileName`、`fileSize`、`fileSha256`），覆盖该需求的截图与附件。数据取附件元数据（新增不读取附件内容的查询），不读取附件内容。
- 版本规则：每轮评估或澄清执行归档一次。若需求原文哈希和附件清单（按 ID 与 SHA-256 逐项比较）都与最近一个版本相同，则复用该版本 ID，不新增目录。版本文件继续用不可变写入。版本清单缺失或不可解析的目录不参与复用。
- `source.md` 正文不变（元信息加原始内容），两处说明文字调整：路径元信息改为需求目录下的相对路径；结尾改为“截图与附件保存在数据库，清单见 manifest.json”。
- 主要流程：构建 Markdown → 归档输入版本（失败不阻断）→ AI 生成 → 结果保存数据库 → 确认后发布 `研发评估.md`，其中“来源版本”链接指向 `原始材料/历史版本/{版本ID}/source.md`。
- 失败处理：输入版本归档失败时记 WARN 日志和需求历史“输入版本归档失败”，本轮按未留存版本继续评估。
- 不再写 notes：删除 `appendAnalysisNote` 及全部调用；`publishConfirmedAnalysis` 新发布的正文去掉“临时分析”链接行。
- 知识检索：WorkHub 仓库 `docs` 下的 `requirement-sources/**/source.md` 目前会被检索。迁移后协作业务线的原文不再进入检索范围；BL000012 的 `原始材料/source.md` 仍会被检索，`历史版本/` 继续排除。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| `ProjectKnowledgeBaseService` | 输入版本归档、版本复用、来源路径解析、确认发布 | notes 追加、绑定文件、`index.md` | 需求、结构化数据、项目 → `RequirementKnowledgeNote` | `RepositoryDocumentPaths`、`AttachmentService`、`KnowledgeArchiveFiles` |
| `AttachmentService`、`AttachmentMapper` | 附件元数据清单（不读取附件内容） | 附件内容 | 需求 ID → `AttachmentDigest` 列表 | `pm_attachment` |
| `DevelopmentAnalysisService`、`IntakeClarificationAnalysisService` | 调用归档并记录失败历史 | 文件布局 | 分析结果 | `ProjectKnowledgeBaseService` |
| `RequirementMaterialExportService` | 桌面需求文件夹的附件导出（不变） | 评估输入版本的附件复制 | 不变 | 不变 |
| `McpResourceService`、`McpRuntimeService`、`McpToolRegistry` | 目录与上下文同步 | 不变 | 不变 | 不变 |

- 前端页面、交互与后端规则边界：前端无改动。

## 3. 契约与数据模型

- API/事件提供方与消费者：无新增或废弃接口。
- 接口行为变化：`POST /api/intake-records/{id}/knowledge-archive` 只重新发布已确认的评估（SDD 文件仍只在确认时发布）；没有已确认评估时返回“尚无可归档的已确认评估”。
- 草稿字段（`DevelopmentAnalysisDraft`，字段名保留）：
  - `requirementRawPath`：新值为需求目录下 `原始材料/历史版本/{版本ID}/source.md` 的 WorkHub 仓库根相对路径，基准与 `requirementWikiPath` 相同（协作业务线形如 `../agents-playbook/…`）；本轮未留存版本时为空。
  - 解析规则：路径以 `原始材料/历史版本/{版本ID}/source.md` 结尾时，挂到当前需求目录下定位，不依赖相对前缀；`requirement-sources/…` 和 `raw/…` 旧前缀仍解析到 WorkHub 仓库 `docs/` 下，只读；无法识别的旧路径按未留存处理。
  - `requirementWikiPath`、`requirementWikiUrl` 含义不变。
- MCP knowledgeCatalog：
  - 移除 `businessLineRawRoot`（内置与独立两个 MCP 入口同步）和 `temporaryAnalysisPath`。
  - `requirementRawPathPattern` 改为需求目录下 `原始材料/历史版本/{版本ID}/source.md` 的说明。
  - `wikiPublishRule` 改写为：原始材料与输入版本随需求目录归档；附件以数据库为准，经 `get_intake_attachment` 读取；分析结果保存在数据库。
  - `projectDocumentPath`、`projectPlaybookPath`、`requirementWikiPathPattern`、`historyDirectory` 不变。
- 幂等、并发、重试和重复执行行为：文件写入继续使用归档根文件锁、原子写和不可变写；`publishConfirmedAnalysis` 的指纹标记去重不变；notes 的 `analysis-record` 标记和 `.analysis-sources/`、`.workhub-analysis-sources/` 绑定随之废止。
- 数据归属、关键约束、迁移及历史兼容：附件二进制只在数据库，需求目录只保存需求原文版本和清单；存量路径不迁移、不改写。运行库中 2026-09 及以前上传的 289 个附件没有 SHA-256 和大小（截图 156 个、附件 133 个），清单中对应字段为空，只按附件 ID 引用；2026-10 起上传的附件字段齐全。
- 数据库变更：无结构变更，无数据迁移。

## 4. 非功能约束

- 安全与权限：沿用 `KnowledgeArchiveFiles.checked` 的越界和符号链接校验；清单不含附件内容；接口权限不变。
- 性能、容量和时效：输入归档不再读取附件内容，每轮最多写两个小文本文件；仓库不再增长附件副本。
- 失败处理、降级与补偿：输入版本归档失败不阻断评估；agents-playbook 不可用时评估照常进行，确认发布失败仍按现有方式记入需求历史并可重试归档。
- 日志、监控、告警与审计：归档失败写 WARN 日志和需求历史。

## 5. 交付与回滚

- 影响系统和发布顺序：合入代码 → 用户授权后重新加载运行实例 → 实跑核对一轮协作业务线和一轮 BL000012 的归档位置 → 更新文档 → 删除 `docs/requirement-sources/`。
- 灰度或开关：无新增开关。
- 数据备份与前后校验：删除前记录最后包含 `docs/requirement-sources/` 的提交号；`docs/notes.md` 删除前的内容在暂存区 blob `69f243c6fc3d0f8416d0e06eb1aa3a66e187e8de`。
- 回滚条件、步骤与不可逆部分：还原代码并重新加载即恢复旧写入位置；已写入需求目录的 `原始材料/` 可保留或手工删除；已删除的 `docs/requirement-sources/` 按提交号恢复。不可逆部分只有一个：该目录中尚未提交的文件（2026-11-25 13:40 核对为 10 个，运行实例仍在新增）删除后无法从 git 找回，其中附件在数据库仍有原件。
- 生效并核实后需要更新的文档：`现状.md` 第 10 节、迭代日志、`AGENTS.md`、`docs/业务线/AGENTS.md`、`docs/研发规范/业务运维分析方法.md`、agents-playbook `docs/AGENTS.md`。
- 归档锁文件：`.workhub-archive.lock` 由各仓库的 `.gitignore` 忽略。WorkHub 仓库规则由 `docs/.workhub-archive.lock` 放宽为任意层级；agents-playbook 仓库新增 `.gitignore`。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → §2 布局与分流；目标 2 → §2 清单与版本规则；目标 3 → §2 不再写 notes；目标 4 → §3 MCP。
- 明确非范围是否被保持：存量文件、数据库结构、提示词、检索范围、两个文件夹功能、前端均未改动。
- 待确认问题及影响（用户 2026-11-25 逐点确认）：
  1. 存量 `docs/requirement-sources/` 不迁移，生效后删除。已确认。
  2. 输入版本归档失败不再阻断 AI 评估。已确认。
  3. 重评前的旧结果和迟到的历史评估回调不再另行留存到文件。历次分析结果行仍在数据库；迟到回调的内容会丢弃，只保留需求历史说明。已确认。
  4. agents-playbook 的 `.gitignore` 和文档指引由谁维护。已确认：各仓库自行维护。
- 契约、ADR 及 Tasks 的关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)；无 ADR。
