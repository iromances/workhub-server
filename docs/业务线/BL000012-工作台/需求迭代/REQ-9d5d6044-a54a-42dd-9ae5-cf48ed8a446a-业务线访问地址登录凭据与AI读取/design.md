# Design：业务线访问地址登录凭据与AI读取

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-9d5d6044-a54a-42dd-9ae5-cf48ed8a446a |
| 需求名称与唯一需求目录 | 业务线访问地址登录凭据与AI读取；`docs/业务线/BL000012-工作台/需求迭代/REQ-9d5d6044-a54a-42dd-9ae5-cf48ed8a446a-业务线访问地址登录凭据与AI读取/` |
| 涉及仓库与功能域 | workhub-server（project/mcp/db schema）；workhub-web（业务线编辑弹窗） |
| 所属项目或业务线 | 不适用（WorkHub 平台自身功能） |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 已确认（2026-11-10 实施计划批准） |
| 当前事实核对环境、分支、时间 | server `codex/development-20261102` 工作区；web 工作区；2026-11-10 |

## 1. 现状与证据

- 表结构：`pm_business_line_access_config`（`V20260722_100000` 建表）列含 business_line_code、environment_code、endpoint_type、endpoint_name、endpoint_url、path_prefix、sort_order、enabled，无凭据列；唯一键 `uk_pm_bl_access_identity`(business_line_code, environment_code, endpoint_type, endpoint_name)。
- 保存链路：`ProjectService.replaceAccessConfigs` 删除全量后逐行 `insert`；`normalizeAccessConfigs` 以“环境+\u0000+类型+\u0000+名称小写”为身份键做重复校验；`request.getAccessConfigs() == null` 表示保留存量不替换。
- 管理端响应：`BusinessLineAccessConfigResponse` record 输出全部非敏感列。
- MCP 链路：`McpRuntimeService.accessEndpointSummaries/accessEndpointSummary` 按“启用+环境匹配”过滤后输出 environmentCode/endpointType/endpointName/endpointUrl/pathPrefix/effectiveUrl，被 `get_business_line_context`、`get_requirement_test_context`、`get_business_line_test_context` 共用；输出为结构化 map，不经过查询/日志结果脱敏管线。
- 加密先例：`McpCryptoService.encrypt/decrypt`（AES-GCM、IV 随机、`mcp:v1:` 前缀、密钥派生自 `workhub.mcp.master-key`）；`mcp_resource_config.password_encrypted` TEXT 存密文，`McpResourceResponse` 只给 `passwordConfigured` 布尔，`McpResourceService.encryptOrKeep` 实现“留空保留”。
- 前端：`ProjectListView.vue` 的 `access-config-row` 单行布局（环境/类型/名称/URL/网关前缀/开关/删除）；`McpResourceView.vue` 已有密码框先例（`type="password" show-password`，placeholder “已配置，留空则不修改”）。
- 尚未核实的假设：无关键假设；实施以各文件现状为准。

## 2. 系统架构

- 总体方案：访问地址行内嵌登录凭据，账号明文、密码 AES-GCM 加密存储；管理端只下发 configured 布尔；MCP 运行时按需解密下发，供 AI 浏览器自动登录。
- 数据流：业务线编辑（web）→ `BusinessLineAccessConfigRequest.loginUsername/loginPassword` → `ProjectService` 归一化（账号 trim ≤128；密码非空加密、留空按身份键复用存量密文）→ `pm_business_line_access_config.login_username/login_password_encrypted` → 管理端 `loginPasswordConfigured`；MCP `accessEndpointSummary` → 解密输出 `loginUsername/loginPassword`。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-server project | 凭据列存取、账号校验、密码加密与保留、响应 configured 标志 | MCP 下发明文 | BusinessLineAccessConfigRequest/Response | pm_business_line_access_config、McpCryptoService |
| workhub-server mcp 运行时 | accessEndpoints 解密输出登录凭据 | 凭据维护 | accessEndpointSummary map | McpCryptoService、BusinessLineAccessConfigMapper |
| workhub-web | 账号/密码输入框、留空语义提示、提交载荷 | 加密与校验 | /projects/business-lines | 无新增 |

## 3. 契约与数据模型

- 契约变更（权威文件：workhub-model 对应类 + workhub-web `types/work-item.ts`）：
  - `BusinessLineAccessConfigEntity` + `loginUsername`（String）、`loginPasswordEncrypted`（String）。
  - `BusinessLineAccessConfigRequest` + `loginUsername`（≤128，trim）、`loginPassword`（明文；null/空白=保留存量）。
  - `BusinessLineAccessConfigResponse` + `loginUsername`（String）、`loginPasswordConfigured`（Boolean，密文列非空即 true）；不回显明文密码。
- 错误语义：账号超长 → `IllegalArgumentException`“登录账号不能超过 128 个字符”；沿用全局异常处理。
- 幂等/并发：保存仍是“整体替换”；删除前按身份键（环境+类型+名称小写）查存量建映射，新行密码留空且身份匹配时复用存量密文；身份变化视为新地址，密码需重新填写。
- 数据库变更（Flyway：`V20261110_120000__business_line_access_login_credential.sql`）：
  - `ALTER TABLE pm_business_line_access_config ADD COLUMN login_username VARCHAR(128) NULL AFTER path_prefix, ADD COLUMN login_password_encrypted TEXT NULL AFTER login_username;`
  - 纯加列，无数据迁移；可重复性由 Flyway 版本控制；回滚 = 删除两列。
- 兼容：旧客户端不传新字段 → 账号为空、密码走“保留存量”分支，行为与现状一致。

## 4. 非功能约束

- 安全与权限：密码密文落库（复用 `workhub.mcp.master-key`）；管理端 API 与前端不回显明文；明文仅经 MCP 访问令牌保护的通道输出；接口权限沿用 `project:business-line:create/update/manage`，无新增端点。
- 性能：每行业 1 次加解密，业务线规模（数十行）无影响。
- 失败处理：加解密失败沿用 `McpCryptoService` 既有的 IllegalStateException 语义，不静默吞错。

## 5. 交付与回滚

- 发布顺序：先 workhub-server（加列向后兼容），后 workhub-web。
- 灰度/开关：无开关。
- 回滚条件：凭据读写异常；回滚步骤 = 回退代码 + `ALTER TABLE ... DROP COLUMN login_username, DROP COLUMN login_password_encrypted`（数据丢失仅限凭据本身）。
- 文档更新：`docs/mcp-server.md`（工具输出与安全边界）、`docs/业务线/BL000012-工作台/现状.md`、`迭代日志.md`、本目录 tasks 验证证据。

## 6. 一致性与待确认

- 目标 1（凭据配置）→ §3 契约与 §2 project 职责；目标 2（AI 读取）→ §2 mcp 运行时职责。
- 非范围保持：不做清除机制、不做新权限点、不改保存语义。
- 待确认问题：无（实施计划已于 2026-11-10 经用户批准）。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
