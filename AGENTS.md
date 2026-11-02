# WorkHub 仓库指引

本仓库是 WorkHub 后端，基于 Java 25、Spring Boot 4、MyBatis 和 MySQL；前端见 [workhub-web 指引](/Users/aslight/IDEAWorkspace/workhub-web/AGENTS.md)。

## 项目边界

- WorkHub 负责研发协作、平台管理、受控运维、监测与集成配置；支付域仅管理接入配置，不编排真实支付交易。
- 本仓库保留源码、测试、SQL、运行资源和研发文档；需求产出统一在 `docs/requirements/`，原始材料在 `docs/requirement-sources/`，系统文档在 `docs/system/`，过程记录在 `docs/notes.md`；公司业务知识、项目资料、接口原文和产品要素在 `docs/company/`。知识库仅维护研发规范、模板和工作流。具体业务线代码在经核实的目标仓库维护，位置与分支按下述运维方法定位。
- 未经用户授权，不安装软件、依赖或脚本，或者修改配置。
- 引用知识库内容时遵守[通用协作指引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/AGENTS.md>)；研发主题补充见[研发指引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/wiki/研发/AGENTS.md>)，未知规范从[唯一索引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/index.md#研发规范与模板>)定位。

## 研发

- **研发、修复、方案、测试与交付**：[SDD 流程](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/SDD/流程.md>)与 [WorkHub 补充](</Users/aslight/IDEAWorkspace/workhub-server/docs/system/AGENTS.md#SDD补充>)；编码前读[代码风格](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/代码风格.md>)。

## 运维

- **业务线代码、业务口径、数据或日志排查**：[业务运维分析方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/业务运维分析方法.md>)，适用于 WorkHub 及其协作业务线。

## 代码Review和审查

- **代码Review或审查**：[代码审查方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/代码审查方法.md>)，适用于 WorkHub 自身及协作范围内的业务仓库。


## 需求/资料维护

- **需求建档、跨仓库归属与材料归档**：[需求归档指引](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/AGENTS.md>)。
- **系统资料与文档维护**：[项目文档指引](</Users/aslight/IDEAWorkspace/workhub-server/docs/system/AGENTS.md#文档归属>)。
- **公司项目资料维护**：[公司项目指引](docs/company/AGENTS.md)与[资料索引](docs/company/README.md)。
