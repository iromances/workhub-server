# WorkHub 仓库指引

WorkHub 是负责研发协作、平台管理、受控运维、监测与集成配置的工作台。 本仓库是 WorkHub 后端，配套的前端是workhub-web；

## 工具能力

- 打开网页、操作页面、登录及浏览器测试时，优先使用当前 Agent 或宿主环境提供的内置浏览器。 内置浏览器不可用或无法完成任务时，先说明原因然后使用其他可用的浏览器工具。
- MCP能力清单 [MCP 能力总览](docs/mcp-server.md)。

## 研发

- WorkHub 自身和公司各业务线的研发、修复、正式方案编写须按本仓库 [SDD 流程](docs/研发规范/SDD/流程.md)执行，并在实施前确认规格；
- WorkHub 自身的需求产出按[本仓库归档指引](docs/业务线/AGENTS.md)归档在本仓库；公司业务线的需求产出归档在同级 `agents-playbook` 仓库，按其[需求归属与建档规则](/Users/aslight/IDEAWorkspace/agents-playbook/docs/AGENTS.md#需求归属与建档)执行。
- 编码与审查遵循本仓库[代码风格](docs/研发规范/代码风格.md)。

## 运维

- 业务线代码、业务口径、数据或日志排查：[业务运维分析方法](docs/研发规范/业务运维分析方法.md)。

## 代码Review和审查

- WorkHub 自身的代码 和公司业务线的代码 Review 或审查遵循本仓库[代码审查方法](docs/研发规范/代码审查方法.md)。

## 资料维护

- WorkHub 平台自身见[工作台现状](docs/业务线/BL000012-工作台/现状.md)；
- 协作业务线与支付中台见同级仓库的[归档指引](/Users/aslight/IDEAWorkspace/agents-playbook/docs/AGENTS.md)。

## 安全规范

- 未经用户授权，不安装软件、依赖或脚本，或者修改配置。
