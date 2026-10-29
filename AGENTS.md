# WorkHub 仓库指引

本仓库是 WorkHub 后端，基于 Java 25、Spring Boot 4、MyBatis 和 MySQL；前端见 [workhub-web 指引](/Users/aslight/IDEAWorkspace/workhub-web/AGENTS.md)。

## 项目边界

- WorkHub 负责研发协作、平台管理、受控运维、监测与集成配置；支付域仅管理接入配置，不编排真实支付交易。
- 本仓库保留源码、测试、SQL 和运行资源。具体业务线代码在经核实的目标仓库维护，位置与分支按下述运维方法定位。
- 未经用户授权，不安装软件、依赖或脚本，或者修改配置。

## 按任务直达

只读本次任务所需原文；已知目标直接进入，不遍历索引或无关指引。

- **研发、修复、方案、测试与交付**：[SDD 流程](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/SDD/流程.md>)与 [WorkHub 补充](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/wiki/公司项目/projects/workhub-server/AGENTS.md#SDD补充>)；编码前读[代码风格](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/代码风格.md>)。
- **AI review、代码或分支审查**：[代码审查方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/WorkHub/代码审查方法.md>)。
- **业务线代码、业务口径、数据或日志排查**：[业务运维分析方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/WorkHub/业务运维分析方法.md>)。
- **需求建档、跨仓库归属与材料归档**：[需求归档指引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/wiki/公司项目/研发需求/AGENTS.md>)。
- **系统资料与文档维护**：[项目文档指引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/wiki/公司项目/projects/workhub-server/AGENTS.md#文档归属>)。

只读解释、诊断、审查和文档整理可直接进行；转入系统研发后执行 SDD。
