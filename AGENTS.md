# WorkHub 仓库指引

WorkHub 是负责研发协作、平台管理、受控运维、监测与集成配置的工作台。
本仓库是 WorkHub 后端，基于 Java 25、Spring Boot 4、MyBatis 和 MySQL;配套的前端是workhub-web；

## 工具能力

- 优先使用内置浏览器
- 工具清单、使用方式与安全边界见 [MCP 能力总览](docs/mcp-server.md)。

## 研发

- 研发、修复、正式方案付须按 [SDD流程](docs/研发规范/SDD/流程.md) 执行，并在实施前确认规格。
- 编码与审查遵循[代码风格](docs/研发规范/代码风格.md)。

## 运维

- 业务线代码、业务口径、数据或日志排查：[业务运维分析方法](docs/研发规范/业务运维分析方法.md)。

## 代码Review和审查

- 代码Review或审查：[代码审查方法](docs/研发规范/代码审查方法.md)，适用于 WorkHub 自身及协作范围内的业务仓库。

## 资料维护

- 研发需求、系统设计与迭代记录按所属仓库维护；研发规范与模板直接读取对应代码仓库，不从知识库查找。
- 不确定从哪里开始时，先看[项目文档索引](docs/README.md)。
- WorkHub 平台自身见[工作台现状](docs/业务线/BL000012-工作台/现状.md)；
- 协作业务线与支付中台见同级仓库的[归档指引](../agents-playbook/docs/AGENTS.md)。
- 历史原始材料见[材料索引](docs/requirement-sources/index.md)，过程记录见[排查笔记](docs/notes.md)。

## 安全规范

- 未经用户授权，不安装软件、依赖或脚本，或者修改配置。
