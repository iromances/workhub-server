# WorkHub 项目指引

适用于 WorkHub 及明确采用其流程的业务线。上级：[WorkHub 仓库指引](../../AGENTS.md)。源码操作遵守[后端仓库指引](/Users/aslight/IDEAWorkspace/workhub-server/AGENTS.md)或[前端仓库指引](/Users/aslight/IDEAWorkspace/workhub-web/AGENTS.md)。

## SDD补充

- 研发、修复、正式方案、测试与交付采用 [流程](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/SDD/流程.md>)；编码前读 [代码风格](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/代码风格.md>)。本节仅补充项目字段。
- Proposal 的需求名称取系统 `requirementName`，为空时取 `approvalTitle`；独立任务使用用户明确的任务名称。
- 业务线需求在 Proposal 填写系统 `businessLineCode`、`businessLineName`；Design 与 Tasks 的“所属项目或业务线”沿用同一业务线。
- WorkHub 的研发任务在 Tasks 的项目补充字段中按需填写“建议研发任务标题”。

## 文档归属

- **公司业务与项目资料**：[公司项目指引](../company/AGENTS.md)与[资料索引](../company/README.md)，在 `docs/company/` 维护。

- **系统现状与进展**：[系统设计](</Users/aslight/IDEAWorkspace/workhub-server/docs/system/系统设计.md>)、[迭代日志](</Users/aslight/IDEAWorkspace/workhub-server/docs/system/迭代日志.md>)。
- **归档业务线系统文档**：本仓库 `docs/system/业务线/{业务线编码}-{业务线名称}/`，已有条目见 [业务线文档索引](../README.md#WorkHub归档业务线)；各业务线事实与特殊规则写入自己的系统设计。
- **需求规格、验证证据与自动评估**：[AGENTS](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/AGENTS.md>)，包含目录、跨仓库归属和评估历史规则。
- **原始需求与附件**：[AGENTS](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/AGENTS.md>)。
- **知识库研发规范**：[研发主题指引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/wiki/研发/AGENTS.md>)；修改规范或模板时读取[规范维护指引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/AGENTS.md>)，使用规范直接读取原文。

查找尚未明确的文档时使用 [项目文档](../README.md)或[知识库唯一索引的研发入口](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/index.md#研发规范与模板>)。

研发需求产出、系统设计和迭代事实统一保存在本仓库 `docs/`；知识库只维护共用规范、模板、工作流及其修订记录。
