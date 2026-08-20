# MCP 堡垒机统一管理自动化测试用例

## 1. 需求信息

- 需求名称：MCP 堡垒机统一管理与数据库目标关联
- 研发方案文件：本次 Codex 任务内确认的开发方案
- 测试用例文件路径：`docs/test-cases/2026-07-20-mcp-bastion-management.md`
- 涉及系统：WorkHub 后端、WorkHub Web、MySQL
- 涉及模块：MCP 资源配置、MCP Catalog、堡垒机配置
- 编写时间：2026-07-20
- 编写人或代理：Codex

## 2. 测试范围

- 核心场景：堡垒机新增、修改、密码加密、当前表单连接测试、数据库目标关联、数据库和服务器目标直连/经堡垒机自动测试、Catalog 解析关联堡垒机。
- 边界场景：连接测试结果显示在按钮旁、修改时测试密码留空复用已保存密码、密码与私钥二选一、历史内嵌配置迁移。
- 异常场景：连接测试认证失败、超时、SSH/sshpass 不可用、数据库认证/网络/Schema 失败、服务器目标 SSH 失败、服务器堡垒机隧道失败、名称重复、端口越界、选择停用堡垒机、停用被引用堡垒机。
- 不纳入自动化测试的范围及原因：真实 SSH 连通仅在用户手工录入并测试后做一次同逻辑复核，不纳入可重复自动化；页面交互使用前端构建和联调路径验证。

## 3. 测试环境

- 后端环境：本地 Java 25、Maven 多模块工程。
- 前端环境：本地 Node.js、Vue 3、TypeScript、Vite。
- 数据库环境：WorkHub 开发库，MySQL 8.4；迁移脚本随 Flyway `V20260720_153544` 交付。
- 外部依赖：无真实堡垒机、SSH 或数据库连接。
- 测试数据准备方式：Mockito 构造堡垒机和数据库目标配置。

## 4. 自动化测试用例

| 用例编号 | 场景类型 | 覆盖目标 | 前置条件 | 执行命令或测试方法 | 预期结果 | 开发完成后执行结果 | 失败原因或备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心场景 | 新增堡垒机密码加密 | 提供合法名称、地址、用户和密码 | `McpBastionServiceTest` | 密码以 `mcp:v1:` 密文保存，响应只返回已配置标记 | 通过 | 无 |
| TC-002 | 边界场景 | 编辑时保留原密码 | 已存在加密密码，编辑请求密码留空 | `McpBastionServiceTest` | 原密文保持不变 | 通过 | 无 |
| TC-003 | 异常场景 | 禁止停用被引用堡垒机 | 数据库目标已关联堡垒机 | `McpBastionServiceTest` | 返回明确校验异常，不更新配置 | 通过 | 无 |
| TC-004 | 核心场景 | 数据库目标保存堡垒机关联 | 存在已启用堡垒机 | `McpResourceServiceTest` | 保存 `bastionId`，响应返回堡垒机名称 | 通过 | 无 |
| TC-005 | 核心场景 | Catalog 解析关联堡垒机 | 数据库目标存在 `bastionId` | `McpResourceServiceTest` | `sshTunnel` 使用关联堡垒机主机、用户和解密密码 | 通过 | 无 |
| TC-006 | 核心场景 | 前端类型检查和生产构建 | MCP 页面新增 Tab、表单和下拉选择 | `npm run build` | `vue-tsc` 和 Vite 构建成功 | 通过 | 无 |
| TC-007 | 数据库场景 | `V20260720_153544` 历史迁移 | 存在旧数据库目标内嵌堡垒机配置 | 在开发库执行 Flyway 并查询关联 | 生成独立堡垒机且 `bastion_id` 正确 | 通过 | 迁移前符合条件的历史记录为 0；空集迁移成功，堡垒机表、关联列和索引均已落库 |
| TC-008 | 核心场景 | 测试当前未保存表单 | 填写主机、端口、用户和新密码 | `McpBastionServiceTest` | 不保存配置，使用当前表单参数测试并返回安全结果 | 通过 | 无 |
| TC-009 | 边界场景 | 编辑态复用已保存密码 | 已有堡垒机，测试请求密码留空 | `McpBastionServiceTest` | 服务端解密复用原密码，响应不包含密码 | 通过 | 无 |
| TC-010 | 异常场景 | SSH 认证失败和超时 | 模拟 SSH 退出码和超时 | `McpBastionConnectionTesterTest` | 返回分类后的认证失败/连接超时，不返回原始输出 | 通过 | 无 |
| TC-011 | 异常场景 | sshpass 不可用 | 密码认证且服务环境没有 sshpass | `McpBastionConnectionTesterTest` | 返回明确安全提示 | 通过 | 无 |
| TC-012 | 接口场景 | 连接测试接口安全响应 | 调用测试接口 | `McpResourceControllerTest` | 返回成功、耗时和消息，不回显密码 | 通过 | 无 |
| TC-013 | 前端场景 | 按钮旁显示测试结果 | 打开堡垒机新增/编辑抽屉 | `npm run build` 和页面联调 | 测试中、成功、失败结果紧邻测试按钮展示 | 部分通过 | 类型检查和生产构建通过；尚未使用真实堡垒机完成浏览器联调 |
| TC-014 | 核心场景 | 数据库目标直连测试 | 当前数据库表单未开启堡垒机 | `McpDatabaseConnectionTesterTest` | 直接建立 JDBC 连接并只执行 `SELECT 1`，返回 `DIRECT` 模式 | 通过 | 使用测试探针验证路由和 SQL，不访问真实业务库 |
| TC-015 | 核心场景 | 数据库目标经堡垒机测试 | 当前数据库表单已选择有效堡垒机 | `McpDatabaseConnectionTesterTest` | 建立 SSH 本地端口转发后连接 JDBC，返回 `BASTION` 模式 | 通过 | SSH 隧道复用 Control Socket 就绪检测 |
| TC-016 | 边界场景 | 编辑数据库目标复用密码 | 已保存数据库目标，测试表单密码留空 | `McpDatabaseConnectionTestServiceTest` | 服务端解密复用已保存数据库密码，不回显凭据 | 通过 | 无 |
| TC-017 | 异常场景 | 区分堡垒机与数据库失败 | 分别模拟隧道失败和 JDBC 失败 | `McpDatabaseConnectionTesterTest` | 响应 `failureStage` 分别为 `BASTION`、`DATABASE`，消息为安全分类 | 通过 | 不返回 SSH 原始输出、JDBC URL 或密码 |
| TC-018 | 接口场景 | 数据库测试接口权限与响应 | 调用数据库连接测试接口 | `McpResourceControllerTest` | 返回模式、失败阶段、耗时和消息，新增/编辑分别校验对应权限 | 通过 | 无 |
| TC-019 | 前端场景 | 数据库测试结果显示在按钮旁 | 打开数据库目标新增/编辑抽屉 | `npm run build` | 根据开关提交直连或堡垒机参数，测试状态紧邻按钮展示 | 通过 | `vue-tsc` 和 Vite 生产构建通过 |
| TC-020 | 核心场景 | 服务器目标 SSH 直连测试 | 当前服务器表单未开启堡垒机 | `McpServerConnectionTesterTest` | 直接验证目标服务器 SSH，返回 `DIRECT` 模式且不执行远端命令 | 通过 | 使用测试探针验证路由 |
| TC-021 | 核心场景 | 服务器目标经堡垒机测试 | 当前服务器表单已填写堡垒机配置 | `McpServerConnectionTesterTest` | 先建立本地 SSH 转发，再通过转发端口验证目标 SSH，返回 `BASTION` 模式 | 通过 | 无 |
| TC-022 | 边界场景 | 编辑服务器目标复用两套密码 | 已保存目标 SSH 密码和堡垒机密码，当前表单均留空 | `McpServerConnectionTestServiceTest` | 两套密码分别在服务端内存中解密复用，不回显凭据 | 通过 | 无 |
| TC-023 | 异常场景 | 区分堡垒机与服务器失败 | 分别模拟隧道失败和目标 SSH 失败 | `McpServerConnectionTesterTest` | 响应 `failureStage` 分别为 `BASTION`、`SERVER`，不继续错误阶段后的连接 | 通过 | 无 |
| TC-024 | 接口场景 | 服务器测试接口安全响应 | 调用服务器连接测试接口 | `McpResourceControllerTest` | 返回模式、失败阶段、耗时和安全消息，不包含两套密码 | 通过 | 无 |
| TC-025 | 前端场景 | 服务器测试结果显示在按钮旁 | 打开服务器目标新增/编辑抽屉 | `npm run build` | 自动提交直连或堡垒机表单，测试状态紧邻按钮展示 | 通过 | `vue-tsc` 和 Vite 生产构建通过 |

## 5. 执行命令与结果

| 执行时间 | 执行人或代理 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-20 | Codex | `mvn -q -DskipTests compile` | 通过 | Maven 返回 0 | Java 25 |
| 2026-07-20 | Codex | `mvn -q -pl workhub-service -am -Dtest=McpBastionServiceTest,McpResourceServiceTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-javaagent:.../byte-buddy-agent-1.17.8.jar test` | 通过 | 12 个定向用例通过 | 显式加载 Mockito 所需测试代理 |
| 2026-07-20 | Codex | `mvn -q -DargLine=-javaagent:.../byte-buddy-agent-1.17.8.jar test` | 未完全通过 | 214 个用例中 213 个通过，`IntakeServiceTest.list_shouldFallbackOperationsEffortAndDatesFromStructuredData` 失败 | 失败位于当前工作树已有需求工时回填改动，与 MCP 变更无关 |
| 2026-07-20 | Codex | `npm run build` | 通过 | `vue-tsc --noEmit` 与 Vite 构建成功 | 无 |
| 2026-07-20 | Codex | 应用启动执行 Flyway `V20260720_153544`（首次） | 失败并按预期阻止启动 | 建表、加列和索引已提交；创建外键时因开发库账号无 `REFERENCES` 权限失败 | 已保留并核对现场；移除非必需数据库外键，引用完整性由服务层校验；清理、`repair` 和重试结果见后续记录 |
| 2026-07-20 | Codex | 精确核对部分 DDL，清理空表/空列并执行 Flyway `repair` | 通过 | 部分结构清理完成；schema history 修复成功 | 修复前确认堡垒机表 0 行、目标关联 0 行且外键未创建 |
| 2026-07-20 | Codex | `mvn -q -DskipTests clean package` 后重新启动执行 Flyway | 通过 | `V20260720_153544` 成功执行，`success=1` | `mcp_bastion_config`、`bastion_id` 和索引存在；历史迁移记录数为 0 |
| 2026-07-20 | Codex | `mvn -pl workhub-bootstrap flyway:validate flyway:info`（凭据通过环境变量注入） | 通过 | 23 个迁移校验成功，schema 版本为 `20260720.153544`，无 pending/failed | MySQL 8.4 高于 Flyway 11.14.1 官方日志所示最新验证版本 8.1，已记录兼容性风险 |
| 2026-07-20 | Codex | 随机端口二次启动应用 | 通过 | Flyway 提示 schema 已是最新且无需迁移，应用启动成功 | 启动完成后主动停止烟测进程 |
| 2026-07-20 | Codex | `FlywayMigrationResourceContractTest` | 通过 | 2 个契约测试通过 | 校验版本唯一、V20 后时间戳命名及严格 Flyway 默认配置 |
| 2026-07-20 | Codex | `McpBastionServiceTest,McpResourceServiceTest,FlywayMigrationResourceContractTest` | 通过 | Maven 返回 0 | MCP 服务 12 个用例及 Flyway 2 个契约用例通过 |
| 2026-07-20 | Codex | `McpBastionConnectionTesterTest,McpBastionServiceTest,McpResourceControllerTest,McpResourceServiceTest` | 通过 | 19 个定向用例通过 | 覆盖连接成功、认证失败、超时、sshpass 缺失、密码复用和接口安全响应 |
| 2026-07-20 | Codex | `mvn -q -DskipTests compile` | 通过 | Maven 返回 0 | 新增连接测试接口全仓编译通过 |
| 2026-07-20 | Codex | `npm run build`（连接测试交互） | 通过 | `vue-tsc --noEmit` 与 Vite 构建成功 | 测试按钮及旁侧状态通过类型检查 |
| 2026-07-21 | Codex | 核对最近堡垒机连接测试审计 | 定位完成 | 首次为认证失败；更新密码后约 1 秒返回通用 SSH 失败 | 原实现认证后执行 `true`，与 JumpServer 类堡垒机的受限会话不兼容 |
| 2026-07-21 | Codex | `McpBastionConnectionTesterTest,McpBastionServiceTest,McpResourceControllerTest`（Control Socket 修复） | 通过 | 仅建立 `ssh -N` 会话、不执行远端命令，并覆盖认证失败、超时与进程清理 | Maven 返回 0 |
| 2026-07-21 | Codex | 使用开发库已保存堡垒机配置执行真实 SSH 握手 | 通过 | 约 1 秒建立 Control Socket，返回“连接成功” | 不输出主机、用户或密码，不执行远端命令，不访问业务数据库 |
| 2026-07-21 | Codex | `McpDatabaseConnectionTesterTest,McpDatabaseConnectionTestServiceTest,McpResourceControllerTest,McpBastionConnectionTesterTest,McpBastionServiceTest` | 通过 | 17 个定向用例通过 | 覆盖数据库直连、堡垒机路由、密码复用、失败阶段分类和接口响应 |
| 2026-07-21 | Codex | `npm run build`（数据库目标测试连接） | 通过 | `vue-tsc --noEmit` 与 Vite 构建成功，1731 个模块完成转换 | 数据库测试按钮和旁侧结果通过类型检查 |
| 2026-07-21 | Codex | `mvn -q -DskipTests compile` | 通过 | Maven 返回 0 | 数据库测试接口及 SSH 隧道改动全仓编译通过 |
| 2026-07-21 | Codex | 后端和前端 `git diff --check` | 通过 | 无空白错误 | 仅执行差异格式检查，不修改现有其他工作树内容 |
| 2026-07-21 | Codex | `McpServerConnectionTesterTest,McpServerConnectionTestServiceTest,McpResourceControllerTest` | 通过 | 9 个定向用例通过 | 覆盖服务器直连、经堡垒机、两阶段失败、密码复用和接口响应 |
| 2026-07-21 | Codex | `npm run build`（服务器目标测试连接） | 通过 | `vue-tsc --noEmit` 与 Vite 构建成功，1731 个模块完成转换 | 服务器测试按钮和旁侧结果通过类型检查 |

## 6. 未覆盖风险

- 未覆盖场景：重启当前 IntelliJ 运行进程后，通过页面按钮验证数据库和服务器的直连、经堡垒机真实目标。
- 未覆盖原因：自动化已覆盖各类路由和错误分类，但当前 8080 进程仍加载本次修改前的类；为避免未经确认访问真实业务数据库和服务器，本次未主动执行真实目标探测。
- 可能影响：当前运行进程重启前不会提供最新目标测试接口；真实目标仍可能受白名单、堡垒机转发策略或网络路由影响。
- 后续补充计划：重启应用后，使用已确认的开发环境测试目标分别验证直连和堡垒机路径。

## 7. 测试结论

- 是否完成计划自动化测试：除重启后的浏览器按钮和真实数据库/服务器目标复测外已完成。
- 是否存在失败用例：MCP 定向测试无失败；全量测试存在 1 个与本需求无关的既有需求工时回填失败用例。
- 是否允许进入交付：允许进入开发环境真实数据库目标联调。
- 结论说明：服务逻辑、自动路由、连接测试安全分类、接口、Catalog 关联、开发库迁移、二次空迁移、Maven 插件校验和应用启动烟测均已通过；本次前端构建结果见执行记录。
