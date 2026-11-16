# Tasks：数字员工企微机器人巡检推送

## 1. 元信息

- 需求编码：REQ-47f4429c-edfd-4572-86e9-cb98647c86d2
- 需求名称与唯一需求目录：数字员工企微机器人巡检推送；`docs/业务线/BL000012-工作台/需求迭代/REQ-47f4429c-edfd-4572-86e9-cb98647c86d2-数字员工企微机器人巡检推送/`
- 涉及仓库与功能域：workhub-server（MCP 工具、系统配置、测试、运行环境建档）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：v1 草案
- Design/契约版本：v1 草案
- 目标环境与分支：workhub-server `codex/development-20261102`
- 任务计划状态：草案，待完整规格统一确认
- 负责人/评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | `WecomRobotMessageSender`（integration/wecom）+ 单测 | 无 | 可先行 | Design §3 契约 / AC-02、AC-03 | 已完成（2026-11-13） |
| T-02 | MCP 工具注册 `send_wecom_robot_markdown` + 服务测试 | T-01 | — | Design §3 / AC-01、AC-02、AC-03 | 已完成（2026-11-13） |
| T-03 | `docs/mcp-server.md` 工具清单与安全边界更新 | T-02 | 可与 T-04 并行 | Design §5 | 已完成（2026-11-13） |
| T-04 | 数字员工/任务建档文案（职责描述、任务规格） | 无（范围明细依赖用户输入） | 可与 T-01/T-02 并行 | Design §3 / AC-04 | 文案已完成；【待填】项待用户补充 |
| T-05 | 运行环境操作：录入 webhook 配置、建档、手动运行验证 | T-02、T-03、T-04 | — | AC-01、AC-04 | 待开始（需发布；建档由用户页面自助，底稿已备） |
| T-06 | 全量回归 `mvn test` + 验证说明归档 | T-02、T-05 | — | AC-05 | 已完成（2026-11-13，代码与自动化部分；运行实测随 T-05） |

执行顺序：T-01 → T-02 →（T-03 ∥ T-04）→ T-05 → T-06。T-05 涉及运行库写入，单独执行并在验证说明留证。

## 3. 任务明细

### T-01：企微机器人 markdown 发送组件

- 描述与目标：新增 `cn.aslight.workhub.integration.wecom.WecomRobotMessageSender`：从 `SysConfigService.requireSecretValue("wecom.agent","webhook")` 读解密 webhook，校验 content（UTF-8 1–4096 字节），POST `cgi-bin/webhook/send`（markdown 消息，10 秒超时），返回 `sent/errcode/errmsg`；不打印 webhook 与 content 全文。
- 输入：Design §3/§4；既有 `WecomRobotNotifier` 的 HttpClient 用法（仅参考，不改动它）。
- 输出：新组件 + 单测（未配置拒绝、空/超限拒绝、成功、企微非 0 errcode、HTTP 失败）。
- 前置依赖：无。
- 允许修改范围：`workhub-service` 新增文件及其测试。
- 明确不修改范围：`WecomRobotNotifier`、`WecomRobotProperties`、application.yml。
- 项目补充任务字段：webhook 配置组名 `wecom.agent`、键 `webhook` 在代码中集中常量化。

验收条件：

- [x] 条件 1：webhook 已配置且企微返回 errcode=0 时 `sent=true`。
- [x] 边界：未配置/停用返回明确错误；空内容与 4097 字节内容被拒；errcode!=0 透传且 `sent=false`；异常不向调用方泄漏 webhook。

计划验证方式（尚未执行）：单元测试（HttpClient 可注入 stub）。

状态及真实结果：已完成（2026-11-13）。交付 `WecomRobotMessageSender.java`、`WecomRobotSendResult.java` 与 `WecomRobotMessageSenderTest`（9 项：成功/审计不落全文与 webhook/errcode 透传/空内容/超限/webhook 未配置/IO 失败/非 200/响应不可解析），随模块测试通过。实现差异：内容发送结果日志只含字节数不含全文；被中断按契约返回 sent=false 并恢复中断标记。

### T-02：MCP 工具注册与审计

- 描述与目标：在 `McpRuntimeService` 注册 `send_wecom_robot_markdown`（必填 `content`），调用 T-01 组件；接入 `McpAuditLogger`（记录内容字节数、结果、耗时，不记录全文与 webhook）。
- 输入：T-01 组件；`registerBusinessLogTools` 注册模式。
- 输出：工具注册代码 + 服务测试（tools/list 含新工具、参数校验、审计字段）。
- 前置依赖：T-01。
- 允许修改范围：`McpRuntimeService`（注册与 handler）、相关测试。
- 明确不修改范围：鉴权过滤器、`McpToolRegistry` 结构、其他工具。

验收条件：

- [x] 条件 1：`tools/list` 返回新工具及 inputSchema（required=[content]）。
- [x] 边界：缺 content/空 content 返回参数错误；审计条目无 content 全文与 webhook。

计划验证方式（尚未执行）：`McpRuntimeService` 服务测试。

状态及真实结果：已完成（2026-11-13）。`McpRuntimeService` 以 `@Autowired(required=false)` setter 装配发送组件（沿 `integrationQueryService` 先例，未装配时 tools/list 不出现该工具），注册 `send_wecom_robot_markdown`（required=[content]）。`McpRuntimeServiceTest` 新增 2 项（装配后暴露并透传结果、缺 content 报错且未装配时清单隐藏），该类 24 项全部通过。

### T-03：MCP 能力文档更新

- 描述与目标：`docs/mcp-server.md` 工具清单新增行、配置与安全边界补 `wecom.agent.webhook` 说明（SECRET 存储、掩码回显、停用即关闭）。
- 输入：T-02 实际契约。
- 输出：文档更新。
- 前置依赖：T-02（可并行 T-04）。
- 允许修改范围：`docs/mcp-server.md`。
- 明确不修改范围：其他章节事实。

验收条件：

- [x] 条件 1：文档工具清单与代码注册一致（名称、参数、边界）。

计划验证方式（尚未执行）：对照代码核对。

状态及真实结果：已完成（2026-11-13）。`docs/mcp-server.md`：工具清单新增行、概述句注明唯一外部消息出口、"禅道/Jenkins"段后新增企微机器人边界段（配置、限长、透传、审计、停用即关闭）、相关需求追加本需求链接。

### T-04：数字员工与任务建档文案

- 描述与目标：产出建档请求体：Agent（名称暂定"运维消息巡检员"、职责描述、providerConfigId/model/scopes/maxSteps/maxRunSeconds 对齐运行库现有巡检配置）+ MANUAL 任务（objective、acceptanceCriteria=每轮必须检索并成功发送一条企微汇总（含窗口/统计/结论，无命中也要发）、expectedArtifacts=[REPORT]、analysisScope=INCREMENTAL、无 completionTargets）。职责描述含检索范围占位，用户提供明细后定稿。
- 输入：Design §3；用户提供的检索范围明细（待输入）。
- 输出：建档请求 JSON 与职责描述文案（存本目录 `建档/`）。
- 前置依赖：检索范围明细（阻塞定稿，不阻塞框架起草）。
- 允许修改范围：本需求目录文档。
- 明确不修改范围：平台代码。

验收条件：

- [x] 条件 1：文案字段与 `AgentConfiguration`/`AgentTaskSpecification`/`AgentScheduleRequest` 校验一致（长度、枚举、MANUAL 约束）。
- [ ] 边界：MANUAL 不带 cron 且 enabled=false；REPORT 任务不带 completionTargets。（随 T-05 页面实际提交复核）

计划验证方式（尚未执行）：对照模型校验规则核对 + 建档接口实际提交。

状态及真实结果：文案已完成（2026-11-13），见 [建档/数字员工与任务建档文案.md](建档/数字员工与任务建档文案.md)；检索范围【待填】项待用户补充；实际提交归 T-05。分工变更：经用户确认，建档由用户在页面自助完成，本文案为粘贴底稿。

### T-05：运行环境操作与实测

- 描述与目标：运行库录入 `wecom.agent.webhook`（SECRET，经系统配置维护通道，不进仓库）；建档数字员工与任务；手动运行一轮，核对群内收到一条汇总消息且报告声明发送成功。
- 输入：T-02 已发布、T-04 文案、用户提供的 webhook（已提供）。
- 输出：验证证据（群消息截图/运行记录引用）入本目录 `验证/`。
- 前置依赖：T-02、T-03、T-04。
- 允许修改范围：运行库配置与产品数据；本目录验证产物。
- 明确不修改范围：源码。

验收条件：

- [ ] 条件 1：`tools/list` 可见新工具；实测发送 errcode=0 且群内可见 markdown 消息。
- [ ] 边界：停用配置后工具返回未配置错误；验证后恢复。

计划验证方式（尚未执行）：运行环境实测。

状态及真实结果：待开始。

### T-06：回归与交付归档

- 描述与目标：全量 `mvn test` 相关模块 0 失败；更新 `现状.md` 数字员工章节与迭代日志；验证说明归档。
- 输入：T-01..T-05 结果。
- 输出：测试证据、文档更新、验证说明。
- 前置依赖：T-02、T-05。
- 允许修改范围：`docs/`、测试执行。
- 明确不修改范围：无关模块。

验收条件：

- [x] 条件 1：`mvn test` 本需求范围 0 失败（真实结果见验证说明）。
- [x] 条件 2：现状/迭代日志反映实际进展与部署状态。

计划验证方式（尚未执行）：mvn test + 文档核对。

状态及真实结果：已完成（2026-11-13）。定向 33/33 通过（Sender 9、McpRuntimeServiceTest 24 含新增 2）；workhub-service 全量 1111 项中 3 项失败（KnowledgeArchiveWorkflowTest、BusinessLogSearchServiceTest）经 HEAD 干净基线核对（git worktree d0e3ab1，同用例 10/10 通过）确认为工作区其他在途改动所致，与本需求无关；controller 测试编译阻塞为工作区在途遗留 `AgentToolControllerTest`（HEAD 无此文件）；主代码全链路 `mvn -pl workhub-bootstrap -am -Dmaven.test.skip=true package` 通过。证据与归因链详见 [验证/20261113-企微机器人发送工具/验证说明.md](验证/20261113-企微机器人发送工具/验证说明.md)。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-02/T-05；AC-02→T-01/T-02；AC-03→T-01/T-02；AC-04→T-04/T-05；AC-05→T-06。全覆盖。
- Design 约束承接：契约→T-01/T-02；安全→T-01/T-02/T-05；文档与交付→T-03/T-06。
- 未覆盖项及原因：无。
- 已生效事实的核实与文档更新：已按 Design §5 更新 `docs/mcp-server.md`、`现状.md`（新增"数字员工企微机器人巡检推送"节）、迭代日志（2026-11-13 条目）。
- 关联链接：[proposal.md](proposal.md)、[design.md](design.md)、[验证说明](验证/20261113-企微机器人发送工具/验证说明.md)。
- 完整规格统一确认：范围 v1（Proposal/Design/Tasks v1，T-05 建档执行人调整为用户页面自助），结论"可以，开工吧"，确认人：用户，日期：2026-11-13。
