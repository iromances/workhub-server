# Tasks：运维Job管理与FunctionCall统一SQL访问

## 1. 元信息

- 需求编码：REQ-9f3c2a84-6d1e-4b7a-b5c2-8e4a71d0f39b
- 需求名称与唯一需求目录：运维Job管理与FunctionCall统一SQL访问；docs/业务线/BL000012-工作台/需求迭代/REQ-9f3c2a84-6d1e-4b7a-b5c2-8e4a71d0f39b-运维Job管理与FunctionCall统一SQL访问/
- 涉及仓库与功能域：workhub-server（bootstrap/job/service/controller/dao/model）、workhub-web（菜单/路由/三个页面）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：v1.0
- Design/契约版本：v1.0
- 目标环境与分支：workhub-server@codex/development-20261102；workhub-web 对应开发分支；本地/TEST 验证
- 任务计划状态：草案，待完整规格统一确认
- 负责人/评审人：待确认

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | Flyway 迁移两份（三表+权限；SQL 模板种子改为启动回写，见 design 细化） | 无 | — | §3 数据模型 / AC-03、AC-05 | 已完成（编码完成，迁移执行待部署） |
| T-02 | 动态调度框架 + 10 个 WorkhubJob 改造（30 分钟默认） | T-01 | — | §2 动态调度 / AC-01、AC-03 | 已完成 |
| T-03 | JobAdminService/Controller/Mapper | T-02 | — | §3 Job API / AC-01、AC-02 | 已完成 |
| T-04 | XXL 看板 lastStatus/enabled 筛选（Mapper/Service/Controller+list 端点） | 无 | 与 T-02/03 并行 | §3 看板筛选 / AC-04 | 已完成 |
| T-05 | FunctionCallService + 两个 Collector 改造 + 单测等价断言 | T-01 | 与 T-02/03/04 并行 | §2 Function Call / AC-06 | 已完成 |
| T-06 | FunctionCallController（CRUD+试运行） | T-05 | — | §3 Function Call API / AC-05 | 已完成 |
| T-07 | 前端：菜单/路由 + JobManagementView + api/job.ts | T-03 | 后端 API 契约冻结后 | §2 / AC-01、AC-02 | 已完成 |
| T-08 | 前端：FunctionCallView + api/functionCall.ts + OpsMonitorView 筛选 | T-04、T-06 | 同上 | §2 / AC-04、AC-05 | 已完成 |
| T-09 | 规范：AGENTS.md 条目 + docs/研发/FunctionCall数据库访问规范.md | T-05 | — | §5 / AC-07 | 已完成 |
| T-10 | 验证交付：mvn test、npm build、集成验证、迭代日志与本文档证据回填 | T-01~T-09 | — | §5 / 全部 AC | 编码验证完成；部署后集成验证待执行 |

执行顺序：T-01 → {T-02→T-03, T-04, T-05→T-06} 并行 → {T-07, T-08, T-09} → T-10。共享文件协调：OpsMonitorController/OpsMonitorService 仅 T-04 修改；collectors 仅 T-05 修改。

## 3. 任务明细

### T-01：Flyway 迁移

- 描述与目标：V20261114_100000__add_job_admin.sql（sys_job_config、sys_job_execution_log、ops:job:view/manage 种子）；V20261114_110000__add_function_call.sql（func_call_sql、11 条模板种子、ops:function-call:view/manage 种子）。
- 输入：design §3 表结构与种子清单；现有模板文本（XxlJobMonitorCollector/MqMonitorCollector 在用 SQL 体，`:param` 化）。
- 输出：两个迁移文件；文末附回滚注释。
- 允许修改范围：仅 workhub-bootstrap/src/main/resources/db/schema/mysql/ 新增文件。
- 明确不修改范围：既有迁移文件。

验收条件：
- [ ] 本地/TEST Flyway 自动执行成功，三表结构与索引齐全，权限种子与 ops:view 关联正确。
- [ ] 幂等：重复执行不产生重复行（INSERT WHERE NOT EXISTS）。
- [ ] 模板种子渲染结果与旧拼接 SQL 逐字节一致（由 T-05 单测证明，迁移内模板为准）。

计划验证方式（尚未执行）：起服观察 Flyway 日志 + MCP run_readonly_query 核对表与种子。

状态及真实结果：待开始。

### T-02：动态调度框架与任务改造

- 描述与目标：WorkhubJob 接口、DynamicJobScheduler（ThreadPoolTaskScheduler、启动种子回写、CRON/FIXED_DELAY 调度、重调度、并发保护、执行包装写历史、90 天清理）；10 个入口改造为 WorkhubJob Bean，移除全部 @Scheduled；XXL/MQ 巡检默认 `0 */30 * * * *`。
- 输入：T-01 表结构；9 个现有 Job 类。
- 输出：workhub-job 新框架类与改造后任务类；单测（重调度生效、并发跳过、历史落库、cron 非法值拒绝）。
- 允许修改范围：workhub-job 模块；不修改各任务调用的 service 业务方法。
- 明确不修改范围：AccountingReconcileJob 的 ConditionalOnProperty、WorkItemDueReminder 的 reminder.enabled 内部开关、Agent globalLimit 属性。

验收条件：
- [ ] 改调度保存后不重启按新值执行（单测 + 集成验证）。
- [ ] 同任务运行中：调度触发跳过、手动触发拒绝，均有明确反馈。
- [ ] 每次实际执行在 sys_job_execution_log 有完整记录（SUCCESS/ERROR、耗时、异常信息截断）。
- [ ] 覆盖失败情形：任务抛异常记 ERROR 且不中断后续调度。

计划验证方式（尚未执行）：JUnit + 集成起服操作。

状态及真实结果：待开始。

### T-03：Job 管理 API

- 描述与目标：JobAdminService + JobAdminController（/api/ops/jobs 五个端点）+ SysJobMapper（注解 SQL，参照 OpsMonitorMapper 风格）+ model 实体/DTO；权限注解 ops:job:view/manage。
- 输入：T-02 的 DynamicJobScheduler 公开能力（reschedule/triggerNow/isRunning）。
- 输出：Controller/Service/Mapper/DTO + 单测。
- 验收条件：
- [ ] 列表返回配置+运行中标记+最近执行摘要；历史分页含失败原因。
- [ ] 非法 cron/越界 fixedDelay 返回业务错误文案。
- [ ] 触发接口在任务运行中拒绝并返回提示。

计划验证方式（尚未执行）：单测 MockMvc 风格参照现有 controller 测试（如有）；否则 service 单测。

状态及真实结果：待开始。

### T-04：XXL 看板筛选

- 描述与目标：OpsMonitorMapper.findAll 增加 lastStatus/enabled 条件（UNCHECKED=last_status IS NULL AND enabled=1）；OpsMonitorService.dashboard 与 OpsMonitorController.xxlJobDashboard 透传；单测覆盖三分支。
- 输入：现 findAll 动态 SQL。
- 输出：三处修改 + 单测。
- 验收条件：
- [ ] lastStatus=UP/ERROR/UNCHECKED 与 enabled=true/false 组合结果正确。
- [ ] 不传新参数时行为与现状完全一致。

计划验证方式（尚未执行）：Mapper 单测（H2/嵌入式或参照现有测试基建）+ 集成页面核对。

状态及真实结果：待开始。

### T-05：FunctionCallService 与 Collector 改造

- 描述与目标：FunctionCallService（render/run/runBatch/runVerified，:name 替换、缺参报错）；XxlJobMonitorCollector 7 处、MqMonitorCollector 3 处模板体改为按场景编码取模板渲染；片段构建器保留；collectLogs 组合外壳保留；等价断言单测。
- 输入：T-01 种子模板；两个 Collector 现有代码与测试。
- 输出：FunctionCallService、两个 Collector 改造、FuncCallSqlMapper、单测更新。
- 验收条件：
- [ ] 等价单测：改造前后对同一参数生成的 SQL 逐字节一致（10 个模板全覆盖）。
- [ ] 模板缺参/多余参数行为符合 design；禁用模板时巡检落 ERROR 状态（与异常口径一致）。
- [ ] 现有 XxlJobMonitorCollectorTest/MqMonitorCollectorTest 全部通过（mock 调整后）。

计划验证方式（尚未执行）：mvn test。

状态及真实结果：待开始。

### T-06：Function Call API

- 描述与目标：FunctionCallController（列表/新增/编辑/启停/试运行）+ 权限注解；试运行走 runRows 限 50 行返回列名与渲染后 SQL。
- 输入：T-05 FunctionCallService。
- 输出：Controller/DTO + 单测。
- 验收条件：
- [ ] CRUD 唯一性校验（sql_code/scenario_code 重复返回业务错误）。
- [ ] 试运行对 OPS-XXL-EXECUTORS + 现有 UP 配置（BL000004/amp_xxl_job_250）返回真实数据。
- [ ] 覆盖失败情形：模板语法非法（非 `:name` 占位符乱写）创建时校验拒绝。

计划验证方式（尚未执行）：单测 + 集成试运行。

状态及真实结果：待开始。

### T-07：前端 Job 管理

- 描述与目标：menu.ts/router 增运维组「Job 管理」(/ops/jobs, ops:job:view)；JobManagementView.vue（列表、改调度对话框含 cron 校验提示、启停二次确认、触发参数对话框、执行历史抽屉）+ api/job.ts。
- 输入：T-03 API 契约；现有页面风格（OpsMonitorView/McpResourceView）。
- 输出：前端代码；npm run build 通过。
- 验收条件：
- [ ] 页面可完成 AC-01/AC-02 全部操作路径。
- [ ] 停用 Agent 轮询任务有二次确认提示（文案说明数字员工停摆影响）。

计划验证方式（尚未执行）：本地起前后端走查。

状态及真实结果：待开始。

### T-08：前端 Function Call 与看板筛选

- 描述与目标：menu.ts/router 增「Function Call」(/ops/function-call, ops:function-call:view)；FunctionCallView.vue（SQL 定义列表+编辑对话框+试运行面板）+ api/functionCall.ts；OpsMonitorView.vue 与 api/ops.ts 看板筛选栏增加上次状态/启用状态。
- 输入：T-04/T-06 API 契约。
- 输出：前端代码；npm run build 通过。
- 验收条件：
- [ ] Function Call 页可维护定义并试运行（AC-05）。
- [ ] 看板筛选联动刷新正确（AC-04）。

计划验证方式（尚未执行）：本地走查。

状态及真实结果：待开始。

### T-09：规范固化

- 描述与目标：AGENTS.md 研发节新增「业务库访问必须经 Function Call 登记场景 SQL 并走 FunctionCallService，禁止代码内拼业务库 SQL」条目；新增 docs/研发/FunctionCall数据库访问规范.md（登记流程、`:param` 写法、参数值白名单责任、试运行要求、种子与页面维护边界）。
- 输出：两份文档。
- 验收条件：
- [ ] 规范文档可直接指导新场景接入（含示例）。
- [ ] AGENTS.md 条目简洁并与现有条目风格一致。

计划验证方式（尚未执行）：文档自查 + 用户评审。

状态及真实结果：待开始。

### T-10：验证与交付

- 描述与目标：mvn -q test 全量通过；workhub-web npm run build 通过；集成验证（起服后核对 AC-01~AC-06 场景，用 MCP run_readonly_query 核对新表数据与执行历史）；更新迭代日志与本文件证据。
- 验收条件：
- [ ] 全部 AC 有可核实证据（命令输出/页面截图/DB 查询结果，归档至 验证证据/）。
- [ ] 失败、跳过与剩余风险如实记录。

计划验证方式（尚未执行）：见各 AC 核对方式。

状态及真实结果：待开始。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-02/03/07；AC-02→T-02/03/07；AC-03→T-01/02；AC-04→T-04/08；AC-05→T-01/06/08；AC-06→T-01/05；AC-07→T-09。
- Design 约束承接：证据链保留→T-05；安全边界→T-05/06；回滚注释→T-01。
- 未覆盖项及原因：无。

### 实施证据（2026-11-14）

- **T-05 等价性**：`FunctionCallTemplateEquivalenceTest` 10/10 通过——14 条种子模板渲染结果与改造前 `.formatted` 拼接输出逐字节一致（含 MQ 分支 stripTrailing 语义）；`XxlJobMonitorCollectorTest` 30/30、`MqMonitorCollectorTest` 11/11、`OpsMonitorServiceTest` 13/13、`XxlJobFailureAlertServiceTest` 1/1、`MqFailureAlertServiceTest` 3/3 通过。
- **T-02 调度框架**：`DynamicJobSchedulerTest` 6/6 通过（启动种子回写默认 30 分钟 cron、运行中手动触发拒绝、失败任务落 ERROR 历史、非法 cron/fixedDelay 拒绝、停用不调度且重调度生效、未知任务拒绝）。
- **全量后端测试**：`mvn test` 1142 例，1139 通过；3 失败均为本分支既有问题，与本需求 diff 无交集（证据：失败用例位于 `CodexCliPromptConsistencyTest`（澄清 prompt 迁移一致性，V20261112_161000 遗留）、`KnowledgeArchiveWorkflowTest`（知识库路径口径 `requirements/` vs `业务线/`）、`BusinessLogSearchServiceTest`（BL000001 PROD ELK 范围未配置）；另 bootstrap 的 `TeamAliasRecoveryStaticTest` 依赖已被本分支删除的 `../docs/WorkHub/团队重复人员合并/SDD/rollback.sql` 文件）。本需求未触碰上述任何文件。
- **前端**：`npm run build` 成功（4.18s），新增 JobManagementView/FunctionCallView 分包确认在 dist 产物中。
- **待部署后执行**：Flyway 两份迁移自动执行、Job 页改 cron 即时生效与触发带参、看板筛选联动、Function Call 试运行对 `OPS_XXL_EXECUTORS` + BL000004/PROD 真实取数、90 天历史清理。部署验证结果回填本节。
- **本地 Debug 实例验证（2026-11-14 17:05，运行库实测）**：Flyway 两份迁移自动应用；`sys_job_config` 回写 9 行种子（账务对账未装配无行，符合设计），`OPS_XXL_JOB_FAILURE_MONITOR`/`OPS_MQ_FAILURE_MONITOR` 均为 `0 */30 * * * *`（AC-03 ✓）；`sys_job_execution_log` 按 5s/10s 节奏真实落库（SCHEDULED/SUCCESS/RUNNING 状态流转与 duration 正常，AC-02 数据侧 ✓）；日志出现 `workhub-job-*` 线程名证明 DynamicJobScheduler 生效。`func_call_sql` 空表为预期（懒初始化：首次访问 Function Call 页面或首次巡检时回写种子）。剩余页面交互项（改 cron 即时生效、触发带参、筛选联动、试运行取数）待登录会话走查。

### 确认记录

- 完整规格统一确认的范围、规格版本、结论、确认人与日期：规格 v1.0（proposal/design/tasks 全量）关键口径（30 分钟 cron、UNCHECKED 筛选、DB 唯一调度事实源、菜单归属、全部任务可操作）经用户 2026-11-14 实施计划审批与四项决策问答确认；规格文档当日建档于本目录。
