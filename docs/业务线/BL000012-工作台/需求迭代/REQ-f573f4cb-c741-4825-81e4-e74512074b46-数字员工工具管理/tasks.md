# Tasks：数字员工工具管理

## 1. 元信息

- 需求编码：REQ-f573f4cb-c741-4825-81e4-e74512074b46
- 需求名称与唯一需求目录：数字员工工具管理；`docs/业务线/BL000012-工作台/需求迭代/REQ-f573f4cb-c741-4825-81e4-e74512074b46-数字员工工具管理/`
- 涉及仓库与功能域：workhub-server（迁移/model/dao/service/controller/测试）、workhub-web（api/types/对话框/选择器）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：v1
- Design/契约版本：v1
- 目标环境与分支：server `codex/development-20261102`；web `codex/development-20261103`；均含用户在途改动，仅追加式修改
- 任务计划状态：已确认
- 负责人/评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 迁移 `pm_agent_tool` + 50 条种子文案 | 无 | 与 T-02 并行（约定表结构即可） | §3 / AC-03 | 已完成（H2 验证） |
| T-02 | model/dao/service/controller 管理接口与 options 扩展 | T-01 表结构约定 | — | §2 §3 / AC-02 AC-04 AC-05 | 已完成 |
| T-03 | 后端测试（controller + service） | T-02 | — | §4 / AC-02 AC-04 AC-05 AC-06 | 已完成 |
| T-04 | 前端工具管理对话框与选择器改造 | T-02 契约 | — | §2 / AC-01 AC-03 | 已完成 |
| T-05 | 后端全量测试 + 前端构建与证据归档 | T-03 T-04 | — | §5 / AC-06 | 已完成（自动验证；联调待部署） |
| T-06 | 交付文档（现状.md、迭代日志.md、frontend-architecture.md） | T-05 | — | §5 | 已完成 |

执行顺序：T-01 → T-02 → T-03/T-04 并行 → T-05 → T-06。共享数据库对象仅 `pm_agent_tool` 新表，无跨任务写冲突。

## 3. 任务明细

### T-01：数据库迁移与种子文案

- 描述与目标：新增 `pm_agent_tool` 展示文案表，并种子 50 个工具的整理后中文名称与短描述。
- 输入：design §3 表结构；`*Tools.java` 全量 code 清单与现描述。
- 输出：`workhub-bootstrap/src/main/resources/db/schema/mysql/V20261107_120000__add_agent_tool_catalog.sql`。
- 允许修改范围：仅新增该迁移文件。
- 明确不修改范围：既有迁移、sys_permission 数据。

验收条件：

- [ ] DDL 符合 pm_agent_* 惯例（utf8mb4、DATETIME(6)、version），code 主键。
- [ ] 种子覆盖全部 50 个工具编码，名称非空且 ≤64 字符、描述 ≤256 字符；H2/MySQL 均可执行。

计划验证方式（尚未执行）：随 T-03 服务测试在 H2 上真实执行迁移。

状态及真实结果：已完成：迁移 V20261107_120000 在 H2 随服务测试真实执行，50 条种子加载与短文案断言通过；未执行真实 MySQL。

### T-02：后端管理接口与 options 扩展

- 描述与目标：实现工具管理列表/保存（含目录合并、下线标记、审计）与 options `name`/短描述回填。
- 输入：design §2 §3；`AgentToolCatalog`、`AgentMapper.insertAudit`、`AgentOptionsService` 现实现。
- 输出：`AgentToolRegistrationEntity`、`AgentToolSaveRequest`、`AgentToolAdminResponse`（model）；`AgentToolMapper`（dao）；`AgentToolAdminService`（service）；`AgentToolController`（controller）；`AgentOptionsResponse.Tool` 增 `name`。
- 允许修改范围：上述新文件 + `AgentOptionsService`/`AgentOptionsResponse` 的 tools 装配处。
- 明确不修改范围：`AgentTool` 接口与全部 `*Tools.java`、AgentMcpBridge、AgentApiExecutionAdapter、AgentWorkbenchService。

验收条件：

- [ ] 列表合并逻辑：目录为准；未维护回退 name=code/description=""；表有目录无标记 retired。
- [ ] 保存校验 code 在目录内；upsert 带 version 乐观锁；写审计 AGENT_TOOL/UPDATE。
- [ ] options.tools 输出 name 与短描述，权限过滤行为不变。

计划验证方式（尚未执行）：T-03 单测 + 启动后接口手工验证。

状态及真实结果：已完成：AgentToolMapper/AgentToolAdminService/AgentToolController/AgentOptionsService 落地；AgentTool 实现与 AI 目录桥接零改动。

### T-03：后端测试

- 描述与目标：controller 绑定/权限测试与 service 合并/保存/冲突测试。
- 输入：T-02 产物；`AgentVerificationReadControllerTest`、`AgentWorkbenchServiceTest` 测试范式。
- 输出：`AgentToolControllerTest`、`AgentToolAdminServiceTest`。
- 允许修改范围：仅新增测试文件。

验收条件：

- [ ] controller：PUT 参数绑定与 400/403 路径、GET 列表路由。
- [ ] service（H2+真实迁移）：种子加载、保存后 options 回读、版本冲突、下线工具不可保存、审计落库。

计划验证方式（尚未执行）：`mvn test` 相关模块。

状态及真实结果：已完成：AgentToolAdminServiceTest 7 项、AgentToolControllerTest 3 项全部通过。

### T-04：前端工具管理与选择器

- 描述与目标：数字员工页新增「工具管理」对话框（列表+编辑）；两个工具选择器改按名称显示。
- 输入：design §2；`IntegrationView.vue` 表格+对话框范式；`AgentWorkbenchView.vue`、`AgentConfigDialog.vue`、`AgentTaskDialog.vue`。
- 输出：`AgentToolsDialog.vue`（新）；`api/agent.ts`、`types/agent.ts` 扩展；三个视图组件追加式修改。
- 允许修改范围：上述文件；不动菜单/路由。
- 明确不修改范围：其他页面、公共组件、依赖清单。

验收条件：

- [ ] `agent:manage` 可见入口与编辑；无权限仅不可编辑（列表随 agent:view 页面可见性）。
- [ ] 选择器 label 为 `tool.name`，短描述作次要提示；已选标签显示名称。

计划验证方式（尚未执行）：`npm run build`（vue-tsc）+ 手工联调。

状态及真实结果：已完成：AgentToolsDialog.vue 新增，入口挂在数字员工页头部（agent:manage），配置/任务选择器按名称显示；vue-tsc 构建通过。

### T-05：整体验证与证据

- 描述与目标：后端全量 `mvn test`、前端 `npm run build`，证据归档至 `验证/20261107-工具管理/`。
- 验收条件：
- [ ] 后端测试 0 失败（0 跳过新增）；前端构建通过。
- [ ] 构建日志与手工验证记录归档。

计划验证方式（尚未执行）：见验收条件。

状态及真实结果：已完成：controller 91/91、job 67/67；service 1025 项仅 2 项在途既有失败 + 9 跳过、bootstrap 15 项仅 1 项在途既有错误（均与本需求无关，清单见验证说明）；证据 [验证/20261107-工具管理](../../验证/20261107-工具管理/验证说明.md)。

### T-06：交付文档

- 描述与目标：更新 `现状.md` 数字员工节、`迭代日志.md`、web `docs/frontend-architecture.md`。
- 验收条件：
- [ ] 三处文档反映工具管理能力与验证结论，历史条目不改写。

计划验证方式（尚未执行）：文档核对。

状态及真实结果：已完成：现状.md 数字员工节、迭代日志、web frontend-architecture.md 已更新。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-04；AC-02→T-02/T-03；AC-03→T-04；AC-04→T-02/T-03/T-04；AC-05→T-02/T-03；AC-06→T-02 范围约束+T-05。
- 非功能约束（权限/审计/测试数据）由 T-02/T-03 承接。
- 未覆盖项：真实 MySQL 迁移执行（部署时进行，回滚见 design §5）。
- 完整规格统一确认：范围=Proposal v1 + Design v1 + 本 Tasks v1；结论=批准实施；确认人=用户；日期=2026-11-07（会话内计划批准）。
- 关联：[proposal.md](proposal.md)、[design.md](design.md)。
