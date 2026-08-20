# 业务线访问地址与网关配置自动化测试用例

## 1. 需求信息

- 需求名称：业务线访问地址与网关配置
- 研发方案文件：`docs/研发设计/迭代/2026-07-22-business-line-access-config.md`
- 测试用例文件路径：`docs/test-cases/2026-07-22-business-line-access-config.md`
- 涉及系统：WorkHub 后端、WorkHub Web
- 涉及模块：项目管理-业务线
- 编写时间：2026-07-22
- 编写人或代理：Codex

## 2. 测试范围

- 核心：业务线访问配置新增、回显、保留、清空。
- 边界：环境和类型大小写规范化、默认名称、多环境多地址。
- 异常：非法 URL、重复身份、非网关前缀、字段缺失。
- 不纳入自动化：浏览器视觉布局，以生产构建和页面手工回归替代。

## 3. 测试环境

- 后端环境：本机 Java 25 / Maven 测试环境。
- 前端环境：本机 Node.js / Vite 生产构建环境。
- 数据库环境：未连接数据库；迁移脚本做静态校验，实际部署由 Flyway 执行。
- 外部依赖：全部 Mock，不调用业务系统或网关。
- 测试数据：测试代码构造业务线和脱敏示例 URL。

## 4. 自动化测试用例

| 用例编号 | 场景类型 | 覆盖目标 | 前置条件 | 执行命令或测试方法 | 预期结果 | 开发完成后执行结果 | 失败原因或备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心 | 新增多类访问地址 | 新业务线 | Service 单测 | 规范化并保存，响应完整回显 | 通过 | 2 条配置回显正确 |
| TC-002 | 兼容 | 更新未传配置字段 | 已有访问配置 | Service 单测 | 保留原配置，不执行删除 | 通过 | 已验证 Mapper 未删除 |
| TC-003 | 核心 | 显式空数组清空 | 已有访问配置 | Service 单测 | 删除全部配置，返回空数组 | 通过 | 已验证删除调用 |
| TC-004 | 异常 | 非 HTTP/HTTPS URL | `ftp://` 地址 | Service 单测 | 拒绝保存并返回明确错误 | 通过 | 错误文案符合预期 |
| TC-005 | 异常 | 同环境类型名称重复 | 大小写不同的重复名称 | Service 单测 | 规范化后识别并拒绝 | 通过 | 重复身份被拒绝 |
| TC-006 | 异常 | 非网关配置路径前缀 | 客户端地址带前缀 | Service 单测 | 拒绝保存 | 通过 | 类型约束生效 |
| TC-007 | 构建 | 前端类型与模板编译 | 前端代码完成 | `npm run build` | 类型检查和 Vite 构建成功 | 通过 | 2026-07-22 已执行 |
| TC-008 | 迁移 | Flyway 新表迁移 | 可用 MySQL | 应用启动并查询历史表 | 迁移成功且新表结构正确 | 待执行 | 本轮未启动数据库 |
| TC-009 | 页面 | 新增/编辑/删除地址行 | 后端和前端启动 | 浏览器手工验证 | 操作、空态、回显、错误提示正确 | 待执行 | 本轮未启动浏览器联调 |
| TC-010 | MCP | 测试上下文读取业务线访问入口 | 已配置测试与生产入口，包含停用项 | `McpRuntimeServiceTest` | 指定测试环境仅返回测试环境启用项，过滤生产及停用项；网关返回组合后的 `effectiveUrl` | 通过 | 同时覆盖 `get_business_line_context` 返回全部启用环境入口 |

## 5. 执行命令与结果

| 执行时间 | 执行人或代理 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-22 09:09 | Codex | `mvn -pl workhub-service -am -Dtest=ProjectServiceBusinessLineAccessConfigTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | 5 tests，0 failure/error | Java 25 |
| 2026-07-22 09:10 | Codex | `npm run build` | 通过 | vue-tsc 与 Vite 构建成功 | workhub-web |
| 2026-07-22 09:12 | Codex | `mvn -q -DskipTests compile` | 通过 | 全模块编译成功 | 无输出即成功 |
| 2026-07-22 09:12 | Codex | `mvn -pl workhub-bootstrap -am -Dtest=FlywayMigrationResourceContractTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | 2 tests，0 failure/error | 迁移命名和版本唯一性通过 |
| 2026-07-23 11:45 | Codex | `mvn -pl workhub-service -am -Dtest=McpRuntimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | 13 tests，0 failure/error | 验证 MCP 环境过滤、停用过滤和网关有效地址组合 |

## 6. 未覆盖风险

- 未覆盖：真实 MySQL Flyway 执行、浏览器与后端接口联调。
- 原因：本轮不连接业务数据库，且尚未启动完整本地服务。
- 影响：DDL 方言和最终页面视觉仍需部署前验证。
- 后续：本地联调环境执行 Flyway 后完成接口与页面回归。

## 7. 测试结论

- 是否完成计划自动化测试：核心 Service 单测和前端构建已完成。
- 是否存在失败用例：否。
- 是否允许进入交付：允许进入本地数据库与浏览器联调。
- 结论说明：核心保存、兼容和校验逻辑、全模块编译、迁移资源契约与前端生产构建均已通过。
