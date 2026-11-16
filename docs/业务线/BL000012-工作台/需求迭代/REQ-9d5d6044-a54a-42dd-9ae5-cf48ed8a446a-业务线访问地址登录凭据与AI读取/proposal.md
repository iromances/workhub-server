# Proposal：业务线访问地址登录凭据与AI读取

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-9d5d6044-a54a-42dd-9ae5-cf48ed8a446a |
| 需求名称 | 业务线访问地址登录凭据与AI读取 |
| 涉及仓库与功能域 | workhub-server：project（业务线访问配置）、mcp（运行时上下文）、db schema；workhub-web：项目管理-业务线编辑弹窗 |
| 所属项目或业务线 | 不适用（WorkHub 平台自身功能） |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-9d5d6044-a54a-42dd-9ae5-cf48ed8a446a-业务线访问地址登录凭据与AI读取/` |
| 版本/状态 | v1 已确认并实施（2026-11-10） |
| 目标系统 | WorkHub Server、WorkHub Web |
| 原始需求来源及版本 | 2026-11-10 用户会话：“业务线里新增‘访问地址与网关’时，增加两个输入框，账号和密码，允许AI读取并输入浏览器支持自动测试” |
| 目标环境/分支 | server：`codex/development-20261102`；web：当前开发分支 |
| 负责人/评审人 | 用户 |

## 1. Why：背景与问题

- 业务线“访问地址与网关”目前只存环境、类型、名称、URL 与网关前缀（`pm_business_line_access_config`），无登录凭据；AI 通过 MCP 上下文拿到地址后无法登录页面，浏览器自动测试只能覆盖免登录页面。
- MCP 资源已有成熟凭据先例：`mcp_resource_config.password_encrypted` 以 `McpCryptoService`（AES-GCM，`workhub.mcp.master-key`）加密存储，管理端响应只给 `passwordConfigured` 布尔，保存留空表示保留原值；访问地址沿用同一模式即可，无需新机制。

## 2. What：目标与范围

### 本期目标

1. 每条访问地址配置可维护登录账号与登录密码两个输入框；密码加密存储，管理端接口不回显明文。
2. MCP 上下文工具（`get_business_line_context`、`get_business_line_test_context`、`get_requirement_test_context` 共用的 accessEndpoints）在已配置时输出解密后的账号与密码，供 AI 在浏览器自动测试时输入登录。

### 本期交付范围

1. workhub-server：`pm_business_line_access_config` 新增 `login_username`、`login_password_encrypted` 列（Flyway）；访问配置模型/DAO/校验/加密保存；MCP 运行时凭据输出；单测；`docs/mcp-server.md`、现状与迭代日志更新。
2. workhub-web：访问地址行增加账号、密码输入框（密码不回显，留空表示不修改），类型与保存载荷适配。

### 明确不做

1. 不提供密码显式清除机制（留空=保留，与 MCP 资源密码一致）。
2. 不新增独立权限点（沿用 `project:business-line:*` 既有权限与 MCP 访问令牌）。
3. 不改变访问配置“整体替换”的保存语义与身份唯一键。

### 约束与依赖

- 依赖的代码和文档：WorkHub 后端仓库指引、代码风格规范；`McpCryptoService` 既有加解密能力。
- 既有系统/数据约束：访问配置保存为“删全量+重插”，保留原密码需在删除前按身份键（环境+类型+名称小写）复用存量密文。
- 外部依赖与前置条件：无新增外部依赖；迁移 SQL 随 Flyway 启动执行。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 业务线编辑弹窗每条访问地址可填写账号与密码；密码框不回显明文，已配置时提示“留空则不修改” | 页面核对 + 前端构建 | 用户 |
| AC-02 | 保存后落库：账号明文、密码为 `mcp:v1:` 前缀密文；编辑时密码留空保存，密文保持不变 | 后端单元测试 | 用户 |
| AC-03 | 管理端业务线响应含 `loginUsername` 与 `loginPasswordConfigured`，不含明文密码 | 后端单元测试 | 用户 |
| AC-04 | MCP `get_business_line_test_context` 等工具的 accessEndpoints 在已配置时输出解密的 `loginUsername`/`loginPassword`，未配置时不输出该两字段 | 后端单元测试 | 用户 |
| AC-05 | 后端相关模块测试全绿；前端 `npm run build` 通过 | 构建与测试证据（tasks.md 记录） | 用户 |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
