# Tasks：堡垒机共享连接按配置隔离

## 1. 元信息与任务来源

- 需求编码：REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3。
- 唯一需求目录：当前目录；[Proposal v1](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离/proposal.md>)、[Design v1](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离/design.md>)。
- 目标：WorkHub；工作区 workhub-server；分支 codex/dev-20260820；核对提交 fa56c77e5c9ff407c61e48a636e9cf4ef6eee816。
- 来源：2026-10-09 本次聊天的保活排查与用户“改下吧”的修复请求。
- 状态：用户已统一确认完整 v1 规格，代码与相关自动化验证完成；运行验证待用户运行新版应用。
- 负责人：AI；规格确认人：用户。

## 2. 规格确认记录

| 日期 | 范围与版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-10-09 | 用户要求修复已定位的跨配置连接替换问题 | 用户（本次聊天） | 已提出修复请求；本目录完整 v1 规格统一确认待完成 |
| 2026-10-09 | proposal/design/tasks v1；配置 ID 传递、共享槽隔离、定向失效及回归验证 | 用户（本次聊天） | 用户回复“开干”，统一确认并授权实施；不包含应用启动或重启授权 |

## 3. 任务总览与依赖

| 任务 | 交付物 | 前置 | 对应验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 隧道模型、catalog、数据库连接测试传递配置 ID | 完整规格确认 | AC-04 | 已完成 |
| T-02 | 按 ID 与端点隔离共享槽，配置更新按 ID 失效 | T-01 | AC-01/02/03/05 | 已完成 |
| T-03 | 隔离行为与兼容性测试、相关离线回归及编译 | T-01/02 | AC-01 至 AC-06 | 已完成，69 项通过、后端离线构建通过 |
| T-04 | 实际结果、系统设计与迭代日志更新 | T-03 | AC-06 | 已完成 |
| T-05 | 用户运行新版后，受控 MCP 交替连通性与连接复用验证 | 用户启动或明确授权本次重启 | 运行验证 | 待运行环境具备 |

任务顺序实施，不使用子代理。保留现有其他需求与工作区修改，不将其他任务的结果计入本需求。

## 4. 明细与验证计划（实施前计划）

### T-01：配置身份传递

- 修改范围：McpResourceCatalog、McpResourceService、McpDatabaseConnectionTester、McpDatabaseConnectionTestService 及相关测试。
- 检查 catalog 中 bastionId 等于所选保存配置 ID；数据库连接测试取得同一 ID。
- 检查旧 JSON 缺字段与现有五参数构造仍可用；无 ID 输入与有 ID 配置不合并。

### T-02：隔离缓存与定向失效

- 修改范围：SshConnectionManager、McpBastionService 及相关测试。
- 不同 ID 相同端点建立两个连接；各自往返获取租约不增加认证次数。
- 同 ID 多目标共享连接并复用同目标转发；同槽凭据变化或死连接依旧重建。
- 更新配置仅在提交后失效该 ID；回滚与只改备注不失效；活跃租约结束后关闭失效旧连接。
- 一个 ID 认证失败及失败抑制，不阻止另一个同端点 ID 成功。

### T-03：自动化验证

- 对照新增隔离测试在原端点共享实现下应失败，验证修复后通过。
- 运行 SshConnectionManagerTest、OpenSshConnectionTest、SshTunnelTest、模型解析测试，以及 McpResourceServiceTest、McpBastionServiceTest、数据库连接测试相关服务/执行器测试。
- 使用已安装 Maven 离线执行相关模块 test 与编译；准确记录命令、计数、退出码、未运行项与限制。
- 使用 git diff --check 并按代码风格自查；无依据不扩大测试或重构范围。

### T-04：文档与交付

- 本目录记录实际修改和测试结果；实施完成后更新 WorkHub 系统设计中的已核实连接管理事实。
- 迭代日志记录实际阶段，自动化通过不等同于运行验证或发布。

### T-05：运行验证计划

- 由用户运行新版 WorkHub；本需求未获得本次启动/重启授权。
- 经受控 MCP 按消费分期、物流平台、消费分期、物流平台顺序执行 SELECT 1，检查各配置保留独立 SSH 主连接及控制 socket。
- 核实云端同用户并行会话约束与实际 SSH 心跳；长期空闲行为只在实际观察后填写结论。
- 不更改生产凭据、云端会话策略或业务数据。

## 5. 实际证据与覆盖

- 2026-10-09 已完成只读诊断，证据与边界见 Proposal；根因代码链路与所需兼容调用均已核对。
- 新版本地代码与相关自动化验证已完成；现有运行实例尚未验证新版实现。
- AC-01 至 AC-06 均有对应任务；运行环境限制及 legacy 无 ID 输入边界已写入 Design。

### 2026-10-09 实施与自动化验证

- 隧道模型增加可空 bastionId，五参数构造继续可用；资源 catalog 和数据库连接测试传递所选实体 ID。新增模型测试确认旧 JSON 缺字段可解析，新 catalog 序列化后保留 ID。
- 管理器以 bastionId 与 Endpoint 作为共享槽键；保存配置彼此隔离，也与无 ID 内联输入隔离。同配置多目标及并发请求继续复用连接和转发。
- 配置更新改为提交后按 ID 失效该配置全部端点的槽。测试覆盖端点变化、活跃租约延迟关闭、另一 ID 与 legacy 连接不受影响、回滚不失效和只改备注不失效。
- 管理器测试覆盖不同 ID 相同凭据仍隔离、不同凭据往返访问只建立两个连接、一个配置的失败抑制不影响另一配置、同配置凭据变化及死连接重建；慢配置不阻塞同端点另一 ID。
- 新增真实执行器路径的静态模拟测试：捕获实际传给 SshTunnel.open 的 DatabaseTarget，确认 ID 保留；模拟隧道失败阻止真实 SSH 和 JDBC 访问。
- 修复前对照：模型增加 ID 后、管理器仍使用原 Endpoint 键，执行三项新增隔离测试，均失败；往返访问期望 2 次建连，实际 6 次；相同凭据不同 ID、保存配置与 legacy 输入均错误合并为 1 次建连。对应离线 Maven 命令退出码 1，确认测试能够捕捉原故障。
- 修复后命令：`mvn -o -q -pl workhub-service -am '-Dtest=SshConnectionManagerTest,OpenSshConnectionTest,SshTunnelTest,McpResourceCatalogTest,McpResourceServiceTest,McpBastionServiceTest,McpDatabaseConnectionTestServiceTest,McpDatabaseConnectionTesterTest,McpServerConnectionTesterTest,McpServerConnectionTestServiceTest' -Dsurefire.failIfNoSpecifiedTests=false '-DargLine=-javaagent:/Users/aslight/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar' test`，退出码 0；69 项测试全部通过，0 失败、0 错误、0 跳过。mcp-server 34 项、service 35 项；报告位于对应模块 target/surefire-reports。
- 后端完整离线构建：`mvn -o -q -pl workhub-bootstrap -am -DskipTests package`，退出码 0，相关模块编译、打包与启动模块可执行 jar 构建完成；构建阶段不重复运行测试或启动应用。
- 本次新增 9 项测试（管理器 6 项、catalog 2 项、数据库执行器 1 项），并调整既有共享、并发与失效测试以覆盖保存配置 ID。测试使用既有依赖与模拟连接，没有启动 WorkHub、真实 SSH 或数据库。
- `git diff --check` 通过；按代码风格核对改动，配置 ID 来自既有资源/实体，未新增输入校验或无依据的重试、兜底。工作区此前其他需求修改保留。
- 运行验证仍待用户运行新版应用；此前只读排查的成功查询不作为本次修复后运行验证证据。云端同用户并行会话限制、实际长时间保活尚未验证。
