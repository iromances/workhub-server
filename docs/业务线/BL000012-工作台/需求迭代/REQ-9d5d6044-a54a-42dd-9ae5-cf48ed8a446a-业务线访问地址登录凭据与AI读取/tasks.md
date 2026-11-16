# Tasks：业务线访问地址登录凭据与AI读取

## 1. 元信息

- 需求编码：REQ-9d5d6044-a54a-42dd-9ae5-cf48ed8a446a
- 需求名称与唯一需求目录：业务线访问地址登录凭据与AI读取；`docs/业务线/BL000012-工作台/需求迭代/REQ-9d5d6044-a54a-42dd-9ae5-cf48ed8a446a-业务线访问地址登录凭据与AI读取/`
- 涉及仓库与功能域：workhub-server（project/mcp/db schema）、workhub-web（业务线编辑弹窗）
- 所属项目或业务线：不适用（WorkHub 平台自身功能）
- Proposal 版本：v1
- Design/契约版本：v1
- 目标环境与分支：server `codex/development-20261102`；web 当前开发分支
- 任务计划状态：v1 已确认（2026-11-10 实施计划批准），已实施（2026-11-10）
- 负责人/评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 迁移 SQL（V20261110_120000） | 无 | 与 T-02 并行 | §3 数据库变更 / AC-02 | 已完成 |
| T-02 | 后端模型/DAO/ProjectService 凭据保存 | T-01（列定义） | 与 T-03 并行 | §3 契约 / AC-02、AC-03 | 已完成 |
| T-03 | 后端 McpRuntimeService 凭据输出 | T-02（实体字段） | 与 T-02 收口并行 | §2 mcp 运行时 / AC-04 | 已完成 |
| T-04 | 后端单测补充与回归 | T-02、T-03 | 串行收口 | AC-02、AC-03、AC-04、AC-05 | 已完成 |
| T-05 | 前端类型、API 载荷与表单输入框 | T-02（契约确定） | 与 T-04 并行 | AC-01 | 已完成 |
| T-06 | 前端构建验证 | T-05 | 串行收口 | AC-01、AC-05 | 已完成 |
| T-07 | 文档更新（mcp-server.md、现状、迭代日志） | T-04、T-06 | 串行收口 | §5 文档更新 | 已完成 |

执行顺序：T-01/T-02 → T-03 → T-04、T-05 → T-06 → T-07。

## 3. 任务明细

### T-01：迁移 SQL

- 描述与目标：新增 Flyway 脚本为访问配置表增加登录账号与密码密文两列。
- 输出：`workhub-bootstrap/src/main/resources/db/schema/mysql/V20261110_120000__business_line_access_login_credential.sql`。
- 允许修改范围：db/schema/mysql 新增一个文件。
- 项目补充任务字段：建议研发任务标题“访问地址登录凭据迁移脚本”。
- 验收条件：
  - [x] `login_username VARCHAR(128) NULL`、`login_password_encrypted TEXT NULL` 两列添加，纯加列无数据迁移。
- 计划验证方式：SQL 语句静态核对 + 启动日志确认 Flyway 执行成功。
- 状态及真实结果：已完成；脚本按 `AFTER path_prefix` 顺序加两列，Flyway 执行随应用下次启动生效（待部署验证）。

### T-02：后端凭据保存链路

- 描述与目标：Entity/Request/Response 增加字段；Mapper SELECT/INSERT 增列；`ProjectService` 注入 `McpCryptoService`，账号校验（trim、≤128）、密码非空加密、留空按身份键复用存量密文，响应输出 `loginPasswordConfigured`。
- 允许修改范围：`workhub-model/model/project` 三类、`workhub-dao/BusinessLineAccessConfigMapper`、`workhub-service/ProjectService` 及其测试构造点。
- 项目补充任务字段：建议研发任务标题“访问地址登录凭据后端保存”。
- 验收条件：
  - [x] 保存后落库账号明文、密码 `mcp:v1:` 密文；编辑留空保留原密文（AC-02）。
  - [x] 响应含 loginUsername/loginPasswordConfigured，不含明文密码（AC-03）。
- 计划验证方式：`ProjectServiceBusinessLineAccessConfigTest` 扩展。
- 状态及真实结果：已完成；身份键抽取为 `accessConfigIdentity`，`replaceAccessConfigs` 删除前经 `existingLoginPasswords` 复用存量密文；账号可清空（提交 null），密码留空保留。

### T-03：MCP 运行时凭据输出

- 描述与目标：`McpRuntimeService` 主构造器注入 `McpCryptoService`，`accessEndpointSummary` 在已配置且加密服务可用时输出解密的 `loginUsername`/`loginPassword`；未配置不输出字段。
- 允许修改范围：`workhub-service/mcp/McpRuntimeService`；既有 telescoping 构造器保持兼容。
- 项目补充任务字段：建议研发任务标题“MCP 访问地址凭据下发”。
- 验收条件：
  - [x] `get_business_line_test_context` 等工具的 accessEndpoints 输出解密凭据；未配置不输出（AC-04）。
- 计划验证方式：`McpRuntimeServiceTest` 新增用例。
- 状态及真实结果：已完成；主构造器追加第 8 参，telescoping 构造器委托传 null 保持既有测试兼容；`runtimeWithAttachment` 直连主构造的调用点补 null。

### T-04：后端单测补充与回归

- 描述与目标：`ProjectServiceBusinessLineAccessConfigTest` 增加密文落库/留空保留/响应标志用例；`McpRuntimeServiceTest` 增凭据输出用例；全量回归。
- 验收条件：
  - [x] 相关模块 `mvn test` 全绿（AC-05）。
- 状态及真实结果：已完成；相关三测试类 42 项（9+20+13）全部通过；controller 模块 67 项通过；全仓库编译通过。service 全量 1031 项中 2 项失败（BusinessLogSearchServiceTest、KnowledgeArchiveWorkflowTest）经核对为工作区既有未提交改动所致（两文件均在本次改动前已被用户修改），与本次无关。Flyway 迁移执行与应用冒烟待部署。

### T-05：前端类型与表单

- 描述与目标：`types/work-item.ts`、`api/project.ts` 载荷适配；`ProjectListView.vue` 访问地址行增加账号输入框与密码框（show-password、留空不修改提示、已配置状态展示），行布局调整。
- 允许修改范围：`src/types/work-item.ts`、`src/api/project.ts`、`src/views/project/ProjectListView.vue`。
- 验收条件：
  - [x] 表单可填写账号密码，编辑回显账号、密码留空，提交载荷语义正确（AC-01）。
- 状态及真实结果：已完成；行布局折为两行（`access-config-main` + `access-config-credential`），账号列与名称列对齐（240px）、密码列与 URL 列对齐；提交 `loginPassword: undefined` 不序列化（留空保留）、`loginUsername: null` 清空账号。

### T-06：前端构建验证

- 验收条件：
  - [x] `npm run build`（vue-tsc + vite）通过（AC-05）。
- 状态及真实结果：已完成；2026-11-10 `npm run build` 通过（vue-tsc 类型检查 + vite 构建 4.58s）。页面交互人工核对待部署后进行。

### T-07：文档更新

- 描述与目标：`docs/mcp-server.md` 工具输出与安全边界补充；`现状.md`、`迭代日志.md` 更新。
- 验收条件：
  - [x] 文档反映凭据配置能力与下发边界。
- 状态及真实结果：已完成；mcp-server.md 更新 get_business_line_context 行说明与“安全边界·凭据”条目；现状.md 追加章节、迭代日志.md 新增 2026-11-10 条目。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-05/T-06、AC-02→T-01/T-02/T-04、AC-03→T-02/T-04、AC-04→T-03/T-04、AC-05→T-04/T-06，均已覆盖。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：Proposal/Design/Tasks v1，2026-11-10 用户批准实施计划确认。
- 关联版本链接：[proposal.md](proposal.md)、[design.md](design.md)。

## 5. 实施记录（2026-11-10）

- 后端改动（workhub-server，分支 `codex/development-20261102` 工作区）：迁移 `V20261110_120000`；`BusinessLineAccessConfigEntity/Request/Response`、`BusinessLineAccessConfigMapper`、`ProjectService`（+McpCryptoService 注入）、`McpRuntimeService`（主构造器 + accessEndpointSummary）；测试 `ProjectServiceBusinessLineAccessConfigTest`（构造器与 2 个新用例）、`McpRuntimeServiceTest`（1 个新用例 + 主构造调用点补参）。
- 前端改动（workhub-web 工作区）：`src/types/work-item.ts`、`src/api/project.ts`、`src/views/project/ProjectListView.vue`。
- 验证命令与结果：`mvn test -pl workhub-service -am -Dtest='ProjectServiceBusinessLineAccessConfigTest,McpRuntimeServiceTest,McpResourceServiceTest'`（42 项通过）；`mvn test -pl workhub-controller -am`（排除 2 项既有失败后 67 项通过）；`mvn compile -T1C` 全仓库通过；`npm run build` 通过。
- 交付冒烟（管理接口响应只见 configured 布尔、MCP tools/call 下发解密凭据、页面填写）：应用未重启，待部署后由用户环境验证。
- 剩余风险：明文密码经 MCP 令牌通道按需下发是用户明确要求的预期行为；Flyway 迁移与真实加密往返（生产 master-key）待应用启动验证；两仓库改动均未提交 git，留待用户确认。
