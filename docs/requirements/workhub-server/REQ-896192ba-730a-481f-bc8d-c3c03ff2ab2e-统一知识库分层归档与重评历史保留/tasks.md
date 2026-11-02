# Tasks：统一知识库分层归档与重评历史保留

需求编码：REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e

唯一目录：`wiki/公司项目/研发需求/workhub-server/REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e-统一知识库分层归档与重评历史保留/`

涉及仓库：`/Users/aslight/IDEAWorkspace/workhub-server`，当前分支 `codex/dev-20260820`，基线 `fa56c77`，工作区已有未提交修改。

规格版本：v1；状态：用户于 2026-10-18 在本聊天统一确认，源码实施与离线定向验证已完成；运行联调待启动后验证。需求来源：用户于 2026-10-18 在本聊天明确要求统一 raw/wiki/notes、修正归档逻辑与现有混放、保留重评历史。

Proposal/Design：v1。负责人：本聊天 AI；规格确认人：用户（待确认）；计划与实际分开维护。

## 1. 完整规格确认

用户于 2026-10-18 明确回复“确认”，并询问 notes 追加的含义；已解释为根 notes 公司章节新增带需求、来源、时间与版本的未确认分析记录，保留旧分析。v1 规格已获确认。用户已授权的知识库指引维护与存量目录整理属于独立可执行的文档工作。

## 2. 任务与验收映射

| 任务 | 内容 | 对应验收 | 状态 |
| --- | --- | --- | --- |
| T-01 | 盘点、备份、迁移 28 份材料副本及旧来源，修正索引和引用 | AC-07 | 已完成文档迁移，清单见下 |
| T-02 | 统一根/公司/WorkHub 指引、规范修订记录与材料归属说明 | AC-08 | 已完成，应用实现状态明确区分 |
| T-03 | 实现 raw 材料与附件保留、版本化写入、notes 追加和正式 wiki 归档 | AC-01 至 AC-05 | 已开发、定向测试通过 |
| T-04 | 接入澄清/草稿/确认/修改，提供独立重新归档，更新 MCP 与文件夹路径 | AC-02、AC-03、AC-06 | 已接入、定向测试通过 |
| T-05 | 文件与业务事务定向测试、修改自查、更新系统设计和迭代日志 | AC-09 | 离线验证与交付文档完成，运行联调待验证 |

## 3. 依赖与边界

T-01、T-02 为文档整理，不依赖应用改动；T-03 至 T-05 依赖完整规格确认。禁止在测试中启动应用、访问真实业务库、调用 AI 或外部禅道。既有未提交修改保留；不提交真实来源/附件数据到代码仓库。

## 4. 实际证据

- 初始盘点：raw/公司项目/requirements 28 份 Markdown；raw/requirements 1 份；公司研发需求 27 份带分析前归档标记、9 份 SDD 文档与索引；wiki/projects 1 份带归档标记。
- 尚未实施 WorkHub 代码；尚未执行本需求测试；应用未启动/重启。
- 规格审批结论：待确认，不把本需求原始授权伪写为完整方案已经获批。

- 2026-10-18 文档整理：28 份材料副本已迁回 raw 并逐字节验证，1 份旧 raw 来源已归入公司主题；raw 原始需求入口共 29 份来源。旧 wiki/projects 与 raw/requirements 已移除。
- 指引、公司正式需求索引、原始材料索引及引用已同步；迁移前 2592 处有效文件引用的目标均存在，维护文档未新增失效链接。
- 备份与 SHA-256 清单：`/private/tmp/company-archive-unification-20261018-u350dg_q`。T-03 至 T-05 仍待完整规格确认，未修改应用代码。

## 5. 已确认后的实际实施与验证

- 完整规格确认：v1 Proposal/Design/Tasks，用户于 2026-10-18 在本聊天确认；未增加启动、安装或真实 AI 调用授权。
- T-03/T-04：raw 来源及原始附件快照、校验清单、notes 追加与去重、确认后正式发布、迟到回调保护、独立重试、索引与 MCP 路径契约已实现。输入缺失审批号时按 intake ID 隔离；澄清回复保留来源关联。
- T-05：离线编译通过；定向测试 79 项通过（service 61、controller 18），0 失败、0 错误。覆盖原始附件字节与版本、同轮重试、同秒重评、notes 并发、事务回滚、错误通知失败、迟到回调、确认与重试不重复建任务、源码目录与 MCP 契约。
- 实际验证命令：`mvn -o -pl workhub-controller -am -Dtest=KnowledgeArchiveIntegrationTest,KnowledgeArchiveWorkflowTest,ProjectKnowledgeBaseServiceTest,DevelopmentPlanFolderServiceTest,RequirementMaterialExportServiceTest,DevelopmentAnalysisServiceTest,IntakeClarificationAnalysisServiceTest,McpResourceServiceTest,IntakeControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- 测试日志：`/private/tmp/archive-unification-verified-tests.log`；本次改动前代码备份：`/private/tmp/archive-unification-code-baseline`。测试使用临时目录和 mock，未启动应用、未访问真实业务库、未安装依赖。
- `git diff --check` 通过；保留原工作区所有无关改动，未创建提交。
- 发布状态：应用未启动/重启，运行实例是否加载新代码尚未验证；新逻辑在用户下次启动/部署后生效。数据库既存分析结果未批量补导；独立重试可按需求重新归档已有结果。
- 历史段落中的“待确认”记录保留原时点含义，现阶段状态以本节为准。

## 6. 根工作笔记归位与后续调整（2026-10-18）

- 核查原记录：28 份分析前材料副本和旧 `raw/requirements` 的 1 份来源已归 raw，迁移保持原字节；不据此补造 AI 结论。
- 随后实施记录：v1 源码及 79 项离线测试已完成，见第 5 节；运行实例未重启，数据库既存澄清与草稿未批量补导核验。早期“源码仍覆盖/待确认”的描述只对应初查时点，不是最终实施状态。
- 用户随后明确不需要自动追加 notes；已同意将 notes 限定为手工临时笔记。该调整尚未修改 WorkHub 代码，现有未提交 v1 代码仍包含自动追加；不能把当前知识库整理记为代码修复或 DB 历史留存已实现。
- [ ] 撤除自动 notes 输出，同时落实分析草稿、修改及重评历史在 WorkHub 的版本留存，再验证重试和历史追溯；保留既有 raw/wiki 来源及正式版本历史。
