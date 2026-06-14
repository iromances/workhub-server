# WorkHub MCP 第一版设计

## 目标

构建 WorkHub 的第一版 MCP 服务，用于支撑 AI 在以下两个只读场景中进行受控操作：

- 通过堡垒机访问数据库进行诊断。
- 通过堡垒机查看服务器状态和日志。

第一版支持多业务线、多环境、多数据库实例、多服务器目标和多权限 profile。它不提供任意 shell 访问、不受限 SQL 执行、生产写入、服务重启或配置变更能力。

## 范围

范围内：

- 独立的 `workhub-mcp-server` Maven 模块。
- 面向本地 AI 客户端的 MCP stdio 传输。
- 从本地 JSON 文件加载资源目录。
- 建模业务线、环境、数据库目标、服务器目标和权限 profile。
- 数据库工具：
  - `list_mcp_targets`
  - `describe_database`
  - `explain_query`
  - `run_readonly_query`
- 服务器工具：
  - `list_mcp_targets`
  - `get_service_status`
  - `read_service_logs`
  - `run_diagnostic_command`
- SQL 只读防护。
- 服务器命令白名单防护。
- JSONL 审计日志。
- 可选：通过本地 `ssh` 命令创建 SSH 本地端口转发。

范围外：

- 写 SQL 执行。
- 数据导出流程。
- 服务重启、部署、回滚或文件编辑。
- 审批流。
- MCP 资源的 Web UI 管理。
- 向 AI 客户端直接暴露堡垒机凭据。

## 架构

MCP 服务以独立 Java 进程通过 stdio 运行。它不复用常规 WorkHub HTTP API 进程，因为 MCP stdio 需要干净的 stdout，不能与 Web 应用日志混用。

```text
AI 客户端
  -> workhub-mcp-server stdio
      -> MCP 工具注册表
      -> 资源目录
      -> 策略防护
      -> 审计日志
      -> 数据库客户端 / SSH 命令执行器
          -> 堡垒机 / MySQL / 目标服务器
```

第一版有意避免依赖 Spring AI starter，因为当前 Maven 镜像可能落后于 Spring AI 最新里程碑版本。stdio MCP 协议直接实现核心的 `initialize`、`tools/list` 和 `tools/call` 方法；如果项目后续统一到 Spring AI MCP，该实现仍可替换。

## 资源模型

资源通过以下维度选择：

```text
businessLineCode + environmentCode + targetKey + profileKey
```

数据库目标：

- 业务线和环境。
- JDBC host、port、schema、username、password 引用。
- 可选 SSH 隧道配置。
- 例如 `metadata`、`readonly`，以及后续可能增加的 `export` profile。

服务器目标：

- 业务线和环境。
- SSH host、port、username、identity file。
- 白名单服务、日志文件和诊断命令 key。
- 例如 `status`、`logs`、`diagnostic` profile。

JSON 目录中的密钥只允许在本地开发时使用明文值；生产使用应引用环境变量，例如 `${PAYMENT_PROD_DB_PASSWORD}`。

## 数据库安全

第一版只允许只读 SQL：

- `SELECT`, `WITH`, `SHOW`, `DESC`, `DESCRIBE`, and `EXPLAIN`.
- 只允许单语句。
- 拒绝注释。
- 使用单词边界匹配拒绝危险关键字。
- `SELECT` 和 `WITH` 查询必须带 `LIMIT`；缺失时由服务端自动追加。
- 结果行数和输出字节数由 profile 限制。
- 查询超时时间由 profile 限制。

不同 profile 应使用不同 MySQL 账号。SQL 防护是第二层控制，不是唯一安全控制。

## 服务器安全

第一版不接受任意 shell 命令。AI 只能调用命名命令：

- `uptime`
- `disk_usage`
- `memory`
- `listening_ports`
- `service_status`
- `service_logs`
- `tail_log_file`

服务名称和日志路径必须在目标白名单中。命令输出按 profile 截断。错误和 stderr 会被摘要并写入审计。

## 审计

每次工具调用都会写入一条 JSONL 记录：

- 时间戳。
- 工具名。
- 业务线。
- 环境。
- 目标 key。
- Profile key。
- SQL 指纹或命令 key。
- 状态。
- 适用时记录行数。
- 耗时。
- 适用时记录错误摘要。

审计日志不得包含密钥。SQL 文本只以归一化和截断后的形式保存，并记录 SHA-256 指纹。

## 第一版验收

- 模块可随根 Maven 构建编译通过。
- 核心 SQL 防护和命令防护具备单元测试。
- MCP `initialize`、`tools/list` 和 `tools/call` 可通过换行分隔 JSON-RPC 工作。
- 数据库工具可针对已配置 MySQL 目标运行。
- 服务器工具可针对已配置 SSH 目标运行白名单命令。
- 所有调用都会写入审计记录。
