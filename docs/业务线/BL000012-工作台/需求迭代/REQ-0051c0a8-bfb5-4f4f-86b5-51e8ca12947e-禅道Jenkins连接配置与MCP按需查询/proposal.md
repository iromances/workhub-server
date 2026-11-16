# Proposal：禅道Jenkins连接配置与MCP按需查询

> SDD 流程的 Proposal 模板，作为 Design 的需求边界；可与 Design、Tasks 连续起草，随完整规格在实施前统一确认。AI 不得自行填写"已批准"。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-0051c0a8-bfb5-4f4f-86b5-51e8ca12947e |
| 需求名称 | 禅道Jenkins连接配置与MCP按需查询 |
| 涉及仓库与功能域 | workhub-server（后端：系统集成配置接口、禅道/Jenkins 只读查询、MCP 工具、迁移脚本）；workhub-web（前端：系统集成页凭据配置表单） |
| 所属项目或业务线（适用时） | BL000012-工作台（WorkHub 平台自身） |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-0051c0a8-bfb5-4f4f-86b5-51e8ca12947e-禅道Jenkins连接配置与MCP按需查询/ |
| 版本/状态 | v1 草案，待完整规格统一确认 |
| 目标系统 | WorkHub 后端（系统集成、MCP 运行时）、WorkHub 前端（系统集成页）；外部依赖禅道开源版 21.7.8 REST API、Jenkins REST API |
| 原始需求来源及版本 | 用户 2026-11-12 口述：系统集成里的禅道、Jenkins 支持配置后台地址/账号/密码，并通过 MCP 提供给 AI 按需使用 |
| 目标环境/分支 | workhub-server 分支 codex/development-20261102；workhub-web 对应开发分支 |
| 负责人/评审人 | 待确认（用户） |

## 1. Why：背景与问题

- 当前问题及证据：
  - 系统集成页（REQ-49bc7dcd 建立的台账）只展示配置组状态，不回值；禅道地址/账号/密码需跳转系统配置页逐项维护（`zentao.base` 组），Jenkins 实例地址/账号/Token 需在系统配置页手工编辑 `agent.jenkins/policy` JSON 及其凭据组，操作繁琐且易错（JSON 手编无结构校验入口）。
  - MCP 运行时现有工具不含禅道与 Jenkins：AI（含数字员工 CLI 通道）排查缺陷、核对迭代/需求、查看构建状态与日志时无法按需访问这两个系统，只能由人工代查。
- 受影响用户/角色：平台管理员（系统集成页维护凭据）、研发与测试人员（AI 辅助排查时受益）。
- 不解决的后果：凭据维护依赖系统配置页手工操作；AI 无法按需读取禅道缺陷/需求/迭代与 Jenkins 构建信息，排查链路中断。

## 2. What：目标与范围

### 本期目标

1. 系统集成页对 ZENTAO、JENKINS 提供连接配置表单：禅道为后台地址、账号、密码；Jenkins 为实例列表（地址、账号、API Token、启用态），表单化维护既有 `agent.jenkins/policy` 实例段，Job 绑定段保持不变。
2. MCP 新增禅道/Jenkins 只读查询工具，经既有 `/api/mcp/runtime` 鉴权与审计提供给 AI（含数字员工 CLI 通道 `toolNames()` 全量放开），按需使用。

### 本期交付范围

1. 后端：`GET/PUT /api/system/integrations/{id}/config` 凭据查询（脱敏）与保存接口；禅道 `ZentaoClient` 只读查询方法（缺陷检索/详情、需求详情、任务详情、迭代详情）；Jenkins 只读查询（Job 列表/详情、构建详情、构建日志尾读）；MCP 工具注册与审计；能力目录种子迁移（新增只读查询能力行）。
2. 前端：系统集成详情弹框新增连接配置区（仅 ZENTAO/JENKINS 显示），密码/Token 留空表示不变、回显已配置状态。
3. 文档：`docs/mcp-server.md` 工具清单、迭代日志更新。

### 明确不做

1. 禅道写操作（创建缺陷/任务等）——仍走既有提测链路；Jenkins 触发构建——仍走数字员工审批链路，MCP 只开放只读查询（用户已确认）。
2. Jenkins Job 绑定（bindings）的表单化维护——仍由系统配置页维护 policy JSON；本表单仅维护实例段。
3. 其他集成系统（GITLAB、SONARQUBE 等）的连接配置表单——沿用系统配置页。
4. 不改变 MCP 鉴权模型、白名单与既有工具行为。

### 约束与依赖

- 依赖的代码和文档：`SysConfigService`（SECRET 加密存储）、`ZentaoClient`（已验证 21.7.8 账密换 Token）、`AgentJenkinsPolicy/AgentJenkinsClient`（实例模型与 HTTP 客户端）、`McpRuntimeService`（工具注册）、`SystemAuditService`；前端 `IntegrationView.vue`、`api/integration.ts`。
- 业务/合规约束：凭据加密存储（既有主密钥方案）、接口不回显明文、MCP 输出不携带凭据；数字员工约束"AI 不能提供 Jenkins 地址、凭据或任意参数"保持——MCP 工具不新增任何由 AI 提供地址/凭据的入口。
- 既有系统/接口/数据约束：`agent.jenkins/policy` 结构与数量上限（实例≤50）不变；bindings 引用校验（删除被引用实例必须失败）；禅道 API 在开源版 21.7.8 已验证范围内实现。
- 外部依赖与前置条件：运行环境已配置禅道/Jenkins 地址与凭据后，MCP 工具方可返回数据；未配置时工具返回明确错误。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 禅道表单保存地址/账号/密码后，系统集成"测试连接"返回成功，zentao.base 三项配置生效（密码为加密存储） | 页面操作 + 数据库/配置查询核对 | 待确认 |
| AC-02 | Jenkins 实例表单保存后 policy 结构校验通过、既有 bindings 原样保留；删除仍被绑定引用的实例时保存失败并提示；测试连接返回成功 | 页面操作 + 保存前后 policy 对比 | 待确认 |
| AC-03 | `GET config` 响应不含明文密码/Token，仅返回地址/账号与 `passwordConfigured`/`tokenConfigured` 布尔；留空保存不覆盖既有凭据 | 接口响应断言（测试） | 待确认 |
| AC-04 | MCP `tools/list` 出现 9 个新只读工具；`tools/call` 查询返回限定条数（默认 20、上限 50）与日志尾读（默认 200 行、上限 2000 行/256 KiB）结果；越界参数拒绝 | /api/mcp/runtime 实测 | 待确认 |
| AC-05 | 凭据未配置、认证失败、目标不存在时工具返回明确中文错误，不泄露凭据 | MCP 实测 | 待确认 |
| AC-06 | 数字员工 CLI 通道 `McpRuntimeService.toolNames()` 含新工具，无需额外配置即可按职责选用 | 代码断言 + 会话实测 | 待确认 |
| AC-07 | 凭据保存写入系统审计；MCP 查询写入 MCP 审计（不记录返回数据与凭据） | 审计文件/页面核对 | 待确认 |
| AC-08 | 后端 `mvn test` 相关模块通过；前端构建与既有测试通过 | 构建与测试输出 | 待确认 |
