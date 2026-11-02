# Design：统一知识库分层归档与重评历史保留

需求编码：REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e

唯一目录：`wiki/公司项目/研发需求/workhub-server/REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e-统一知识库分层归档与重评历史保留/`

涉及仓库：`/Users/aslight/IDEAWorkspace/workhub-server`，当前分支 `codex/dev-20260820`，基线 `fa56c77`，工作区已有未提交修改。

规格版本：v1；状态：用户于 2026-10-18 在本聊天统一确认，源码实施与离线定向验证已完成；运行联调待启动后验证。需求来源：用户于 2026-10-18 在本聊天明确要求统一 raw/wiki/notes、修正归档逻辑与现有混放、保留重评历史。

对应 [Proposal v1](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e-统一知识库分层归档与重评历史保留/proposal.md>)，实施任务见 [Tasks](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e-统一知识库分层归档与重评历史保留/tasks.md>)。

## 1. 变更前核实事实（保留诊断基线）

- ProjectKnowledgeBaseService.upsertRequirementIterationNote 直接 Files.writeString 覆盖 raw 与 wiki；buildWikiRequirementMarkdown 调用 buildRawRequirementMarkdown 复制原始材料。
- DevelopmentAnalysisService.executeAnalysis 的 DRAFT 存入 draftJson，confirm 为 CONFIRMED 并创建工作项；chat/updateDraft/updateOwners 会改变分析记录。
- IntakeClarificationAnalysisService 将澄清与回复保存 itemsJson；没有 notes 归档。
- RequirementMaterialExportService 已复用 AttachmentService 读取原始附件、校验归属及摘要，可用于 raw 附件归档，不能把 AI 附件摘要当成原始字节。
- DevelopmentPlanFolderService 使用 wikiPath 的父目录作为开发方案目录，目前多个需求同属年份目录；本次使用独立需求目录。

## 2. 目标目录与文档身份

- 原始材料：`raw/公司项目/requirements/{业务线}/{项目}/{年份}/{审批编号}/source.md`；附件位于同级 `附件/`，使用附件 ID 和安全文件名，保存原始字节及校验值。
- 原始材料历史：同需求下 `历史版本/{版本ID}/`，同时记录来源版本、输入摘要与时间。来源解析元信息可保留，AI 判断、风险和任务拆解不得混入原文。
- 临时分析：根 `notes.md` 的 `## 公司项目` 下追加独立记录，以需求 ID、分析 ID、阶段和版本 ID 定位；保留既有章节，不生成新的主题 notes 文件。
- 正式自动产出：`wiki/公司项目/研发需求/{业务线}/需求迭代/{项目}/{年份}/{审批编号}-{需求名称}/研发评估.md`；只有明确确认后发布。历史位于同需求目录 `历史版本/{版本ID}/`。
- 现有 WorkHub 平台 SDD、业务线 SDD 继续沿用原编码、原唯一目录及 Proposal/Design/Tasks，不因自动归档改变或覆写。

## 3. 实现职责与契约

### 3.1 归档服务

保留 ProjectKnowledgeBaseService 的检索职责；抽出必要的文件版本写入与分层归档方法，供评估、澄清、确认调用。输入包括 intake、已解析需求、项目、分析 ID/版本、业务状态、操作者与实际 AI 结果；临时/正式身份由调用方状态决定，不能根据文件名猜测确认状态。

RequirementKnowledgeNote.asPromptContext 返回 raw 材料路径与内容；requirementWikiPath/URL 只指向实际存在的正式产出，尚未产生时为空。预测的正式路径由 RequirementKnowledgeLocation 提供给文件夹入口，不再创建原始材料 wiki 文件。MCP 返回 raw、notes、正式 wiki 和历史路径语义，模板路径不再是材料副本路径。

### 3.2 业务调用顺序

1. AI 分析前先归档原始材料及来源快照，提示词使用 raw 来源；历史正式文档保留。
2. AI 成功后继续保存数据库草稿，并将原始 AI 结果、风险、待确认项、工时与任务信息追加 notes，标为待确认。澄清回复、AI 调整及人工修改草稿同样生成新版本。
3. confirm 在数据库事务成功提交后发布正式研发评估 Markdown，标记确认人和时间，链接本轮来源与 notes 记录，不把旧模型结果自动标记为新版本确认。
4. 已确认后的允许修改仍按现有生命周期判断；每次正式变更先保存上一文件版本，再更新当前文档。
5. 文件归档失败记录可区分的归档错误；不把 AI 成功误记为 AI 失败，不伪称已归档。提供按 intakeId 的显式重新归档能力，读取已有数据库结果重试，不重新执行 AI 或创建工作项。接口由现有需求权限控制，禁止因此绕过正式确认。

### 3.3 一致性与版本

对每次评估分配不可碰撞的版本 ID，记录同一轮输入/临时输出/正式输出关系。保存新版本前先持久化上一内容，校验值判同后可复用已存在快照；重复回调按分析 ID+版本 ID+阶段幂等。同一需求文件串行化，notes 使用独立文件锁，已有文件拒绝符号链接与越界路径；临时文件同目录原子替换。数据库事务回滚不发布已确认文档；历史文件不得因 AI 失败而删除。

检索时排除历史版本目录和机器日志，避免旧版材料优先占据 MAX_SCAN_FILES 并被视为当前系统事实。notes 条目和 wiki 来源链接必须注明实际版本。

## 4. 存量整理（可独立执行的文档整理）

先在知识库外备份完整受影响文件并生成 SHA-256 清单；28 份材料副本以原样快照保存进对应 raw 需求的 `历史版本/存量迁移-{内容摘要}/`，既有 source.md 原文保留，遇到不同内容不能覆盖。旧 raw/requirements 的来源逐字节迁入公司主题，再清除空旧目录。将源材料链接映射到 raw，对公司研发需求索引保留正式 SDD，另建 raw 来源索引；原始材料的历史路径叙述不据迁移改造其事实，迁移记录解释旧路径。核验附件、全部目标文件以及链接；保留历史审批与原始时点。

不从原始材料伪造 AI 正式结果，也不从现有文件臆造数据库未导出的历史 AI 分析。资料解析片段作为历史来源快照保留，其性质与正式分析区分。

## 5. 验证、发布与回滚

文件系统测试覆盖首次归档、内容变化、同内容幂等、同秒重评、并发追加、符号链接、来源与附件校验和、版本恢复、历史检索排除；业务测试覆盖 DRAFT 仅 notes、CONFIRMED 发布 wiki、事务回滚、归档失败和重新归档不重复创建工作项。Maven 离线运行相关定向测试，使用临时目录，不启动 Spring 应用，不发真实 AI/数据库请求。

不需要数据库迁移。代码交付后由用户授权启动/重启再验证运行链路。文档可凭备份与迁移清单恢复；代码只回退本次增量，不撤销工作区已有修改。现有数据库分析结果批量补导不在未核验情况下执行。

## 6. 实施落地（2026-10-18，源码已实现、运行实例未核验）

- `KnowledgeArchiveFiles` 提供知识库文件锁、路径检查、历史内容留存及原子替换；原始附件复用 `RequirementMaterialExportService.synchronizeVersioned` 读取已校验的原始字节。每轮输入按 UUID 保存独立来源与当轮附件快照，manifest.json 记录 SHA-256；旧当前内容按摘要保存，重复写同内容不产生伪修订。
- `ProjectKnowledgeBaseService` 评估前只写 raw；AI 识别的摘要和任务判断不再复制进原始正文。`RequirementKnowledgeNote.rawRelativePath` 返回本轮不可变来源快照，未产生正式文件时 wiki 路径/URL 为空。无审批编号用 `INTAKE-{intakeId}` 隔离，避免不同需求混用来源。
- 临时 notes 以需求 ID、分析 ID、分析类别、输入版本和结果内容摘要去重，澄清回复通过来源绑定关联原输入；不会创建新的主题笔记文件。版本快照、机器来源绑定和 notes 不参与当前知识检索。
- 数据库提交后归档；回滚不发布。归档错误单独写需求历史，即使通知失败也不让异步调用方清空已保存草稿。正式发布锁定分析记录并核对当前内容，迟到的历史回调留存 notes，不覆盖新人工修改；正式文件与 raw 来源均自动补入各自索引。
- 独立重试：`POST /api/intake-records/{id}/knowledge-archive`（兼容 `/api/intake/{id}/knowledge-archive`），权限 `intake:ai:operate`。读取现有结果，未确认只写 notes，已确认发布 wiki；不调用 AI、不创建工作项、不绕过确认。返回明确成功或失败。
- 开发方案文件夹改为单个需求目录，不再共用年份目录；MCP 返回正式 wiki 路径、notes 路径、历史目录与发布规则。
- 编译和 79 项离线定向测试通过；未启动或重启应用，未对真实需求触发 AI 或补导数据库历史结果。
