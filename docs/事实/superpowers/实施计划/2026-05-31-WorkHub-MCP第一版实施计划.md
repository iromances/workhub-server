# WorkHub MCP 第一版实施计划

> **给 AI 执行者：** 必须使用子技能 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务逐步执行本计划。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 构建一个独立的第一版 MCP stdio 服务，用于数据库和服务器的只读诊断。

**架构：** 新增 `workhub-mcp-server` Maven 模块，直接实现基于 stdio JSON-RPC 的 MCP 协议。加载 JSON 资源目录，在执行前校验 SQL 和命令策略，并写入 JSONL 审计日志。

**技术栈：** Java 25、Maven、Jackson、JUnit 5、JDBC、本地 `ssh` 命令。

---

### 任务 1：模块和防护测试

**文件：**
- 修改：`pom.xml`
- 创建：`workhub-mcp-server/pom.xml`
- 创建：`workhub-mcp-server/src/test/java/cn/aslight/workhub/mcp/security/SqlPolicyGuardTest.java`
- 创建：`workhub-mcp-server/src/test/java/cn/aslight/workhub/mcp/security/CommandPolicyGuardTest.java`

- [ ] 添加新的 Maven 模块和测试依赖。
- [ ] 编写预期失败的 SQL 防护测试，覆盖允许 select、自动 limit、拒绝 DML、拒绝多语句和拒绝注释。
- [ ] 编写预期失败的命令防护测试，覆盖白名单服务/日志和拒绝任意命令。
- [ ] 运行 `mvn -pl workhub-mcp-server test`，确认测试因生产类尚不存在而失败。

### 任务 2：核心目录和策略

**文件：**
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/config/McpResourceCatalog.java`
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/security/SqlPolicyGuard.java`
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/security/CommandPolicyGuard.java`

- [ ] 为业务线、环境、数据库目标、服务器目标和 profile 实现不可变目录记录。
- [ ] 实现 SQL 归一化和只读校验。
- [ ] 实现命令白名单校验。
- [ ] 运行 `mvn -pl workhub-mcp-server test`，确认防护测试通过。

### 任务 3：MCP 协议和工具注册

**文件：**
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/WorkhubMcpServerApplication.java`
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/protocol/McpStdioServer.java`
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/tool/McpToolRegistry.java`

- [ ] 为 `initialize`、`tools/list`、`tools/call` 和通知实现换行分隔 JSON-RPC 处理。
- [ ] 注册第一版工具 schema。
- [ ] 所有工具结果都返回 MCP text content。
- [ ] 实现后如有必要，补充一个小型协议测试。

### 任务 4：数据库和服务器执行器

**文件：**
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/db/DatabaseDiagnosticService.java`
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/server/ServerDiagnosticService.java`
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/ssh/SshTunnel.java`
- 创建：`workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/audit/McpAuditLogger.java`

- [ ] 使用 JDBC 实现数据库元数据、explain 和受限查询执行。
- [ ] 为数据库目标实现可选 SSH 本地端口转发。
- [ ] 使用命名命令模板实现服务器 SSH 命令执行。
- [ ] 为成功和失败场景实现 JSONL 审计日志。

### 任务 5：示例配置和文档

**文件：**
- 创建：`workhub-mcp-server/src/main/resources/mcp-resource-catalog.example.json`
- 修改：`docs/AGENTS.md`
- 修改：`docs/事实/系统架构与设计.md`

- [ ] 添加一个多业务线、多环境的示例目录。
- [ ] 记录如何启动 MCP 服务，以及如何让 AI 客户端连接它。
- [ ] 记录第一版安全边界。
- [ ] 运行 `mvn -q -DskipTests compile`。
