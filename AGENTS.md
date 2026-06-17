# AGENTS.md

本文件是 `workhub-server` 仓库的 AI 协作规则入口。它只保留项目定位和文档入口；详细业务、架构、接口、研发要求和专题等说明以 `docs/` 下文档为准。

## 1. 项目概览

这是 WorkHub 后端仓库，基于 Java 25、Spring Boot 4、MyBatis、MySQL，用于承载：

- 公司各业务线需求管理与系统研发
- 面向 AI 客户端的受控 MCP 只读运维通道。

关联前端仓库在同级目录 `../workhub-web`，可读写。

嘉泰资产平台前端仓库特别注意：`jiatai-amp-opadmin` 是嘉泰当前运营后台前端；`jiatai-amp-admin` 是演示用旧系统。除非用户明确点名 `jiatai-amp-admin`，嘉泰资产平台业务前端需求不要改到 `jiatai-amp-admin`。


## 2. 文档索引

### 工具和方法

- `docs/事实/业务运维分析方法.md`：业务线代码、知识库、数据库和服务器资源定位与排查方法。
- `docs/事实/MCP能力清单.md`：WorkHub MCP 只读能力清单和工具边界。

### 事实

- `docs/事实/系统架构与设计.md`：系统架构、后端模块边界、核心数据模型和 API 分组等系统/项目事实。
- `docs/事实/控制器接口说明.md`：Controller 层接口说明。
- `docs/事实/MCP能力清单.md`：WorkHub MCP 只读能力清单和工具边界。
- `docs/事实/ELK日志监控.md`：本地 ELK、Filebeat、Kibana 看板和日志监控说明。
- `docs/事实/superpowers/`：历史方案、计划、测试用例和执行记录归档。

### 规则与约定

- `docs/规则与约定/研发要求与约定.md`：研发要求、工程约定、安全边界、AI 协作规则和 WorkHub 项目专项业务规则。
- `docs/规则与约定/研发设计规范.md`：中等及以上需求开发前的方案输出规范。

### 模版

- `docs/模版/自动化测试用例模板.md`：自动化测试用例输出模板。
