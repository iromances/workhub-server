# Tasks：需求原始材料与评估输入版本归入需求目录

## 1. 元信息

- 需求名称与编码：需求原始材料与评估输入版本归入需求目录（REQ-8be33332-3713-488a-b814-76f720ac6bdb）
- 涉及仓库与功能域：workhub-server 归档服务、两个分析服务、附件元数据查询、MCP 目录与上下文、本仓库文档；agents-playbook 仅 `docs/AGENTS.md` 和 `.gitignore`
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0
- Design/契约版本：v1.0（无接口和数据库结构变更）
- 研发分支：`codex/development-20261122`
- 任务计划状态：已完成。T-01～T-06 已实施并离线验证；T-07 已生效、已用真实评估核对并删除存量目录，仅 BL000012 分支没有可触发的需求
- 负责人：AI（ZCode）
- 评审人：用户
- 建议研发任务标题：需求原始材料与评估输入版本归入需求目录

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 输入版本归档改造 | 无 | 与 T-02 修改同一批类，串行 | design §2 / AC-01、AC-02、AC-03、AC-08 | 已实施 |
| T-02 | 移除 notes 追加与绑定 | 无 | 与 T-01 串行 | design §2 / AC-04、AC-05 | 已实施 |
| T-03 | 旧路径兼容与确认发布降级 | T-01 | — | design §3 / AC-06 | 已实施 |
| T-04 | MCP 目录与上下文同步 | 无 | 可与 T-01 并行 | design §3 / AC-07 | 已实施 |
| T-05 | 测试更新与离线回归 | T-01～T-04 | — | 全部 AC | 已通过 |
| T-06 | 文档与指引更新 | T-05 | — | AC-09 | 已完成（目录删除随 T-07） |
| T-07 | 生效验证与存量清理 | T-05、T-06；运行实例已重新加载 | — | AC-01、AC-09 | 已完成（条件 2 除外） |

执行顺序：T-01 与 T-02 串行，T-04 可并行 → T-03 → T-05 → T-06 → T-07。

## 3. 任务明细

### T-01：输入版本归档改造

- 描述与目标：`upsertRequirementIterationNote` 改为向需求目录 `原始材料/` 写入 `source.md`、不可变版本和 `manifest.json`；附件只列清单；输入未变化时复用版本。
- 输入：design §2、§3。
- 输出：`ProjectKnowledgeBaseService`、`RequirementMaterialExportService`（删除版本化导出）、`RepositoryDocumentPaths`（`SOURCES` 只用于旧路径解析）；附件清单查询为 `AttachmentService.listIntakeMaterialDigests`，对应新增 `AttachmentMapper.findDigestsByBiz`（不读取附件内容）。
- 允许修改范围：上述类与 `AttachmentService`。
- 明确不修改范围：`RequirementFolderService`、`DevelopmentPlanFolderService`、提示词与生成器、数据库结构。

验收条件：

- [x] 条件 1：归档后需求目录下存在 `原始材料/source.md`、`原始材料/历史版本/{版本ID}/source.md` 和 `manifest.json`，目录内没有附件文件。
- [x] 条件 2：BL000012 的需求落在 WorkHub 仓库，其余业务线落在 agents-playbook 仓库。
- [x] 条件 3：连续两轮输入相同只产生一个版本目录；附件清单或原文变化时新增版本。
- [x] 条件 4：归档失败时方法正常返回且来源路径为空，不向上抛异常。
- [x] 条件 5：需求原文与元信息字段与改动前一致，仅路径取值与结尾说明按 Design §2 调整。

计划验证方式：`ProjectKnowledgeBaseServiceTest`、`KnowledgeArchiveIntegrationTest`、`RequirementMaterialExportServiceTest`。

状态及真实结果：已实施。`RequirementKnowledgeNote` 新增 `failureReason`，保留原 5 参构造。条件 2 的“BL000012 落在 WorkHub 仓库”由现有 `wikiArchiveRoot` 分流保证，本轮用例覆盖协作业务线分支，BL000012 分支留待 T-07 实跑核对。相关用例通过。

### T-02：移除 notes 追加与绑定

- 描述与目标：删除 `appendAnalysisNote` 及调用，去掉重评前保留和迟到回调的文件留存，调整相关需求历史文案。
- 输出：`ProjectKnowledgeBaseService`、`DevelopmentAnalysisService`、`IntakeClarificationAnalysisService`。
- 允许修改范围：上述三个类。
- 明确不修改范围：分析执行、结果保存和状态流转。

验收条件：

- [x] 条件 1：评估、澄清、回复澄清、调整任务、重评各流程不再写 `docs/notes.md`，不再创建 `.analysis-sources/`。
- [x] 条件 2：重评不再改动旧结果行（`failInFlightByIntakeId` 只处理执行中的行）。
- [x] 条件 3：新发布的 `研发评估.md` 不含“临时分析”链接行。
- [x] 条件 4：迟到的历史评估回调不覆盖当前正式文档，需求历史有说明。
- [x] 条件 5：输入版本归档失败时需求历史出现“输入版本归档失败”，澄清继续执行并保存结果。

计划验证方式：`DevelopmentAnalysisServiceTest`、`IntakeClarificationAnalysisServiceTest`、`KnowledgeArchiveWorkflowTest`。

状态及真实结果：已实施。`scheduleArchive` 改为只归档已确认评估，未确认草稿不再触发归档回调。新增用例 `archiveFailureIsRecordedInHistoryWithoutStoppingClarification`、`reevaluationKeepsPreviousResultRowAndNoLongerArchivesNotes`。研发评估侧的失败历史写入（条件 5 的评估分支）只有代码路径，没有独立用例。`docs/notes.md` 公司项目段已于 2026-11-25 13:35 删除（7,726 行减为 494 行，未提交）。

### T-03：旧路径兼容与确认发布降级

- 描述与目标：来源路径按 design §3 解析；文件缺失时确认发布和重试归档不报错；重试归档只处理已确认评估。
- 输出：`ProjectKnowledgeBaseService`（来源解析、`publishConfirmedAnalysis`）、`DevelopmentAnalysisService.retryKnowledgeArchive`。

验收条件：

- [x] 条件 1：旧前缀路径和新布局路径都能解析到正确文件。
- [x] 条件 2：来源文件缺失时正文写“未留存”，发布成功。
- [x] 条件 3：没有已确认评估时重试归档返回“尚无可归档的已确认评估”，不写文件。
- [x] 条件 4：路径越出归档根或含符号链接时仍被拒绝。

计划验证方式：`ProjectKnowledgeBaseServiceTest`、`KnowledgeArchiveIntegrationTest`、`KnowledgeArchiveWorkflowTest`。

状态及真实结果：已实施。新增用例 `legacySourcePathsResolveReadOnlyAndMissingFilesAreMarkedUnretained`、`linkedMaterialFoldersAndEscapingSourcesCannotTouchOutsideFiles`、`retryOfUnconfirmedDraftIsRejectedWithoutWritingFiles`。运行库核对：122 条带草稿的评估行中 54 条使用旧路径，确认依赖这组兼容逻辑。

### T-04：MCP 目录与上下文同步

- 描述与目标：按 design §3 调整 `knowledgeCatalog` 和两个入口的 `knowledgeContext`。
- 输出：`McpResourceService`、`McpRuntimeService`、`McpToolRegistry`。

验收条件：

- [x] 条件 1：目录和上下文中不再出现 `businessLineRawRoot`、`temporaryAnalysisPath`。
- [x] 条件 2：`requirementRawPathPattern` 文案与新规则一致；`wikiPublishRule` 已改写，未加断言。

状态及真实结果：已实施；三个 MCP 测试类通过。`McpToolRegistry` 中 `businessLineWikiRoot` 仍使用旧的 `/requirements/` 前缀，不在本次范围。

### T-05：测试更新与离线回归

- 描述与目标：更新受影响测试类的断言并离线执行回归；只读核对运行库。
- 输出：测试代码与 [验证](验证/validation.json) 目录下的日志。

验收条件：

- [x] 条件 1：受影响测试类全部通过，日志已存入 `验证/`。
- [x] 条件 2：运行库核对结果已记入本任务。

状态及真实结果：已通过，均为 2026-11-25 在源码副本上离线执行（`mvn -o`，未改写运行实例使用的 `target/classes`，未下载依赖）。

- 定向回归：11 个测试类 108 个用例，0 失败。
- 全量回归：`workhub-mcp-server` 94 个用例通过；`workhub-service` 1325 个用例中 1 个失败、1 个错误、12 个跳过；`workhub-controller` 63 个用例通过，`workhub-job`、`workhub-bootstrap` 无匹配的测试类。
- 两个失败为存量问题：`AgentWorkbenchServiceTest.apiPromptRequiresEli5SummaryReport`、`BusinessLogSearchServiceTest.shouldPreserveActualExecutionAndExposeContentTruncationWithoutSensitiveFilters`，在未含本次改动的 HEAD `e7b7b7b` 上以相同结果失败，且不引用本次改动的类。
- 运行库只读核对（仅聚合计数）：评估行 135 条，带草稿 122 条，已确认 112 条，带 `requirement-sources/` 前缀 22 条、`raw/` 前缀 32 条。截图 271 个中 156 个、附件 228 个中 133 个没有 SHA-256 和大小，全部上传于 2026-09 及以前；2026-10 起的 133 个附件字段齐全。

### T-06：文档与指引更新

- 描述与目标：把现行规则写入 design §5 列出的文档。

验收条件：

- [x] 条件 1：现行指引不再链接 `docs/requirement-sources/`，不再写“过程记录归入 `docs/notes.md`”。
- [x] 条件 2：agents-playbook `docs/AGENTS.md` 说明自动归档需求目录中的 `原始材料/`。
- [x] 条件 3：历史文档和已发布的 `研发评估.md` 不改写。

状态及真实结果：已完成。本仓库更新 `AGENTS.md`、`docs/业务线/AGENTS.md`、`docs/研发规范/业务运维分析方法.md`、`docs/业务线/BL000012-工作台/现状.md` 第 10 节、`.gitignore`（`docs/.workhub-archive.lock` 放宽为任意层级）、迭代日志和 `docs/notes.md`；agents-playbook 更新 `docs/AGENTS.md` 并新增 `.gitignore`。`业务运维分析方法.md` 中指向已删除的 `docs/README.md` 的链接一并改为仓库指引和工作台现状。全部改动未提交。

### T-07：生效验证与存量清理

- 描述与目标：重新加载运行实例后实跑核对，再删除 `docs/requirement-sources/`。
- 前置依赖：运行实例已由用户于 2026-11-25 16:10 重新加载。

验收条件：

- [x] 条件 1：协作业务线一轮评估后，`原始材料/` 出现在 agents-playbook 对应需求目录。
- [ ] 条件 2：BL000012 一轮评估后，`原始材料/` 出现在 WorkHub 仓库对应需求目录。
- [x] 条件 3：核对期间 `docs/requirement-sources/` 和 `docs/notes.md` 公司项目段无新写入。
- [x] 条件 4：记录最后包含该目录的提交号后删除目录，`git status` 无残留。

状态及真实结果：已完成，条件 2 除外。

生效核对（2026-11-25 16:10～16:30）：

- 新实例 PID 43077，16:10:37 启动完成；`target/classes` 于 16:10:32 重新编译，含 `InputVersionManifest`、`AttachmentDigest` 新类。
- 运行实例的 MCP 目录返回新的 `requirementRawPathPattern` 和 `wikiPublishRule`，不再返回 `temporaryAnalysisPath`；`projectPlaybookPath` 为 `/Users/aslight/IDEAWorkspace/agents-playbook/docs/业务线`。
- 重启后日志 0 个 ERROR（2 个 WARN 为 Flyway 例行提示）；运行库中重启后没有新增分析、澄清和需求历史记录。
- 重启后到删除前 `docs/requirement-sources/` 无新写入，`docs/notes.md` 未重新出现公司项目段。

存量清理（2026-11-25 16:31，用户回复“继续”后执行）：

- 最后包含该目录的提交：`e7b7b7b676287aa8cb4110d22cb71f44490f3a00`（2026-11-24 14:55）。已提交的 280 个文件可按该提交恢复。
- 删除前目录内 290 个文件（280 个已跟踪、10 个未提交）。10 个未提交文件已逐字节比对备份到 `/tmp/workhub-requirement-sources-untracked-20261125/`（588 KB，临时目录，由系统清理）。使用工作区删除，未改暂存区，`git status` 中 280 个文件显示为未暂存的删除。

真实评估核对（2026-11-25 16:35，用户指示“你自己去跑评估”）：

- 在内置浏览器以 admin 对需求 286（审批编号 202611170005，嘉泰租赁资产保理，待评估）点击“重新分析”，产生第 5 轮评估（分析行 137）。
- 16:36:06 在 `agents-playbook/docs/业务线/BL000009-嘉泰租赁资产保理/需求/202611170005-恒昌项目-切换嘉泰保理扣款/原始材料/` 下生成 `source.md`、`历史版本/bfc4d53d-fe43-4435-977f-ce20fcabb600/source.md` 与 `manifest.json`。该需求目录此前不存在，由本轮新建。
- `manifest.json` 记录 intakeId 286、`sourceSha256`（与 `source.md` 实际哈希一致）及 2 个附件的 ID、大小、SHA-256；目录内没有附件文件。
- 归档阶段没有写入 `docs/requirement-sources/`，没有追加 `docs/notes.md`，需求历史没有“输入版本归档失败”。归档锁文件 `agents-playbook/docs/业务线/.workhub-archive.lock` 被该仓库新增的 `.gitignore` 忽略。
- 该需求此前 4 轮评估均失败（最近一轮 13:36 因 AI 输出解析失败）。本轮 16:51:20 成功：分析行 137 状态 DRAFT，生成 7 个工作项，草稿中的 `requirementRawPath` 为 `../agents-playbook/docs/业务线/BL000009-嘉泰租赁资产保理/需求/202611170005-恒昌项目-切换嘉泰保理扣款/原始材料/历史版本/bfc4d53d-fe43-4435-977f-ce20fcabb600/source.md`，需求历史记录“生成研发拆解草稿”。未确认草稿没有生成 `研发评估.md`，整轮结束后需求目录内只有上述 3 个文本文件。
- 本轮期间应用日志没有归档相关告警。16:48 有一条 `ServletResponse failed to flushBuffer: Broken pipe`（客户端断开连接），与归档无关。
- agents-playbook 仓库中新增的需求目录保持未跟踪，由该仓库自行评审提交。

未核对：条件 2。待评估、待澄清的需求都属于协作业务线，没有 BL000012 的需求可以触发，WorkHub 仓库这条分支目前只有单元测试覆盖。

## 4. 覆盖与评审

- Proposal 成功标准是否均映射至任务：AC-01、AC-02、AC-03、AC-08 → T-01（AC-01 的 BL000012 分支与生效核对 → T-07）；AC-04、AC-05 → T-02（AC-05 另见 T-01 条件 4）；AC-06 → T-03；AC-07 → T-04；AC-09 → T-06、T-07。
- Design 接口/数据/非功能约束是否均有承接：接口行为、草稿字段 → T-03；MCP 目录 → T-04；失败处理 → T-01 条件 4、T-02 条件 5。
- 未覆盖项及原因：
  - 研发评估侧归档失败写需求历史的代码路径没有独立用例。
  - `wikiPublishRule` 文案没有断言。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：`现状.md` 第 10 节、迭代日志、两仓库指引已更新；生效核对待 T-07。
- 关联 Proposal、Design 与契约的版本链接：Proposal v1.0 / Design v1.0（本目录）。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：范围为本目录三份规格；v1.0；用户于 2026-11-25 在会话中回复“确认，gitignore各自仓库维护”，Design 第 6 节四点全部确认。

## 5. 确认后的文字修正与剩余事项

- 文字修正（不改变范围）：重试归档只重新发布已确认评估，SDD 文件仍只在确认时发布；AC-08 与 T-01 条件 5 按 Design §2 的两处说明文字调整对齐；附件清单查询需要新增不读取附件内容的 Mapper 方法。
- 剩余事项：
  1. BL000012 分支没有真实评估覆盖：待评估、待澄清的需求都属于协作业务线，WorkHub 仓库这条分支目前只有单元测试覆盖（T-07 条件 2）。
  2. 2026-09 及以前上传的 289 个附件在数据库中没有 SHA-256 和大小，老需求的清单只能按附件 ID 引用。补齐需要一次性回填，属于数据库写入，未纳入本次。
  3. 数据库中的数字员工提示词如果引用了 `businessLineRawRoot` 或 `notes.md#公司项目`，需要人工调整；本次未核对提示词存储内容。
  4. `验证/` 下的 `.log` 文件受 `.gitignore` 的 `*.log` 忽略，需要入库时要 `git add -f`，与 REQ-898b9c28 的做法一致。
  5. 两个仓库的改动都未提交。
