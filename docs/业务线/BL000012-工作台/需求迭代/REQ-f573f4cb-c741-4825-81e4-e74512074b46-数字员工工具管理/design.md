# Design：数字员工工具管理

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-f573f4cb-c741-4825-81e4-e74512074b46 |
| 需求名称与唯一需求目录 | 数字员工工具管理；`docs/业务线/BL000012-工作台/需求迭代/REQ-f573f4cb-c741-4825-81e4-e74512074b46-数字员工工具管理/` |
| 涉及仓库与功能域 | workhub-server：迁移/model/dao/service/controller；workhub-web：api/types、AgentToolsDialog、两个选择器 |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1 |
| 状态 | 已确认（与 Proposal 同时确认） |
| 当前事实核对环境、分支、时间 | server `codex/development-20261102`、web `codex/development-20261103`，2026-11-07 静态核对源码 |

## 1. 现状与证据

- 工具定义：`workhub-service/.../agent/*Tools.java` 共 11 个 `@Configuration`、50 个 `@Bean AgentTool`（code 形如 `workhub.bugs.list`、`change.publish`），`AgentToolCatalog` 按 code 聚合去重。
- options 契约：`AgentOptionsService.options()` 过滤当前用户权限后返回 `AgentOptionsResponse.Tool(code, description)`；前端 `AgentConfigDialog.vue:55`、`AgentTaskDialog.vue:33/74` 以 `description` 作为下拉 label。
- 消费方：AI 目录（`AgentMcpBridge.java:119`、`AgentApiExecutionAdapter.java:162`）与管理端 options；两者共用 `description()`。
- 存储惯例：`pm_agent_*` 表（VARCHAR(36) 主键、DATETIME(6)、version 乐观锁、utf8mb4）；Flyway 迁移在 `workhub-bootstrap/src/main/resources/db/schema/mysql/`；审计经 `AgentMapper.insertAudit` 写 `pm_agent_audit`。
- 权限惯例：controller 用 `@PreAuthorize("hasAuthority('...')")`；agent 域已有 `agent:view`/`agent:manage`。

## 2. 系统架构

代码目录（`AgentToolCatalog`）为工具权威；`pm_agent_tool` 仅存人读展示文案，读时合并：

- 管理列表 = 目录 all() 左连表行：表缺失 → 名称/描述回退（name=code，description=空），`maintained=false`；表有目录无 → `retired=true`（已下线，不可编辑）。
- 保存 = 校验 code 在目录中 + upsert 表行（version 乐观锁）+ 审计。
- options 的 tools[] 合并表行后输出 `name`（缺省 code）与短 `description`（缺省空串）；AI 目录链路零改动。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AgentToolAdminService（server） | 目录与表合并、保存校验、审计 | 工具执行、AI 目录 | GET/PUT /api/agent-tools | AgentToolCatalog、AgentToolMapper、AgentMapper(审计) |
| AgentOptionsService（server） | options.tools 回填 name/短描述 | AI 目录文案 | AgentOptionsResponse | AgentToolMapper |
| AgentToolsDialog（web） | 工具列表展示与编辑 | 工具增删/启停 | /api/agent-tools | Element Plus 表格+对话框 |
| 选择器（web） | 按名称选择工具 | — | options.tools | — |

## 3. 契约与数据模型

- 新增 `GET /api/agent-tools`（agent:view）：`[{code, name, description, readOnly, permissions, maintained, retired, version}]`，按 code 排序。
- 新增 `PUT /api/agent-tools/{code}`（agent:manage）：body `{name(必填≤64), description(必填≤256), expectedVersion(≥0)}`；成功返回更新后条目。错误：目录不存在或校验失败 400；版本冲突沿用既有机制（IllegalStateException「版本已变化，请刷新后重试」，与 Agent 工作台一致）。
- 修改 `GET /api/agents/options`：`tools[]` 由 `{code, description}` → `{code, name, description}`，description 语义改为「维护的短描述」（可能为空串），前端不再把长约束文案当展示文案。
- 新表 `pm_agent_tool`：`code VARCHAR(64) PK`、`name VARCHAR(64) NOT NULL`、`description VARCHAR(256) NOT NULL`、`version BIGINT NOT NULL DEFAULT 0`、`updated_by VARCHAR(100) NOT NULL`、`created_at/updated_at DATETIME(6) NOT NULL`；无外键（目录在代码中）。
- 迁移 `V20261107_120000__add_agent_tool_catalog.sql`：建表 + 50 条种子 INSERT（整理后中文名称≤6字左右、描述≤30字左右，分组：缺陷2/监测8/巡检3/代码10/构建测试3/Jenkins2/候选变更3/交付2/发布3/问题协作8/验证6）。新表初始为空插入无需防重；重复执行由 Flyway 版本控制。
- 幂等/并发：保存按 version 条件更新，冲突不重试（提示用户刷新）；审计 action=UPDATE，object_id=code。
- 历史兼容：表为纯新增，回滚 drop 即可；Agent 配置内 `toolCodes` 语义不变。

## 4. 非功能约束

- 权限：列表 agent:view、编辑 agent:manage；服务层再经 `AgentAccessService.Actor.require` 复核（与 options 一致）。
- 审计：每次保存写 `pm_agent_audit`（object_type=AGENT_TOOL），payload 含新旧名称/描述。
- 性能：管理列表 ≤50 行单表 join 内存合并，无分页需求；options 增加一次 50 行全表读。
- 测试数据：H2 + 真实迁移（沿用 AgentWorkbenchServiceTest 模式），不连业务库。

## 5. 交付与回滚

- 顺序：先 server（迁移+接口+测试），后 web（界面+选择器）；可独立发布，web 未上线前 options 新增字段对旧前端无影响（旧前端仍读 description，为短文案）。
- 回滚：应用回滚保留表与审计；彻底回滚 drop `pm_agent_tool`；无既有数据改写。
- 生效后更新：`现状.md`（数字员工节）、`迭代日志.md`、web `docs/frontend-architecture.md`（如涉及页面清单）；验证证据归档至本目录 `验证/20261107-工具管理/`。

## 6. 一致性与待确认

- Proposal 目标1→管理接口+AgentToolsDialog；目标2→options 契约与两个选择器；目标3→迁移种子。
- 非范围保持：`AgentTool` 接口、AgentMcpBridge/AgentApiExecutionAdapter、菜单权限零改动；无工具增删。
- 待确认：无（入口位置与 AI 文案不动两项已按推荐方案纳入确认计划）。
- 关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
