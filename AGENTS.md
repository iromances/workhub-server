# WorkHub 仓库指引

WorkHub 负责研发协作、平台管理、受控运维、监测与集成配置的工作台， 本仓库是 WorkHub 后端，基于 Java 25、Spring Boot 4、MyBatis 和 MySQL；配套的前端项目是workhub-web。

## MCP工具能力

- MCP 能力文档为 `docs/mcp-server.md`

## 研发

- 研发、修复、正式方案、测试与交付须按 [SDD流程](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/SDD/流程.md>) 执行，并在实施前确认规格。
- 编码前后代码风格规范遵循[代码风格](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/代码风格.md>)：
- 访问业务方数据库必须先在「运维 → Function Call」登记场景 SQL（func_call_sql），代码经 `FunctionCallService` 按编码调用；禁止在业务代码里直接拼接业务库 SQL。详见 [FunctionCall数据库访问规范](docs/研发/FunctionCall数据库访问规范.md)。
- 系统定时任务统一实现 `WorkhubJob` 并由 `DynamicJobScheduler` 按 `sys_job_config` 调度；调度值以数据库为准（页面改后即时生效），不得新增静态 `@Scheduled` 调度。

## 运维

- 业务线代码、业务口径、数据或日志排查：[业务运维分析方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/业务运维分析方法.md>)。

## 代码Review和审查

- 代码Review或审查：[代码审查方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/代码审查方法.md>)，适用于 WorkHub 自身及协作范围内的业务仓库。

## 资料维护

- 文档归属：研发需求产出、系统设计和迭代事实统一保存在本仓库 `docs/`；知识库只维护共用规范、模板、工作流及其修订记录。
- 引用知识库内容时遵守[通用协作指引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/AGENTS.md>)。
- 需求规格、验证证据与自动评估：[AGENTS](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/AGENTS.md>)，包含目录、跨仓库归属和评估历史规则。
- 查找尚未明确的文档时使用 [项目文档](docs/README.md)或[知识库唯一索引的研发入口](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/index.md#研发规范与模板>)。
- 本仓库保留源码、测试、SQL、运行资源和研发文档；各业务线现状、迭代日志与需求产出统一在 `docs/业务线/`（WorkHub 平台即业务线 `BL000012-工作台`），过程记录在 `docs/notes.md`。

## 安全规范

- 未经用户授权，不安装软件、依赖或脚本，或者修改配置。
