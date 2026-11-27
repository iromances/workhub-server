# Tasks：数字员工取消先分派任务再执行

## 1. 元信息

- 需求名称与编码：数字员工取消先分派任务再执行（REQ-7fecce00-c414-40f8-96c6-219674bf9400）
- 涉及仓库与功能域：workhub-server 数字员工接口与服务；workhub-web 数字员工页面、API 与类型
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0
- Design/契约版本：v1.0（契约见 design §3）
- 研发分支：server `codex/development-20261122`；web `codex/development-20261122`
- 任务计划状态：规格已确认，源码完成；本机运行实例已加载新代码并完成页面走查，未提交、未部署
- 负责人：AI（ZCode）
- 评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端：职责执行任务与员工级接口 | 规格确认 | — | design §2、§3 / AC-03、04、06、07 | 已完成 |
| T-02 | 前端：API 与类型 | 规格确认 | 契约已定，可与 T-01 并行 | design §3 / — | 已完成 |
| T-03 | 前端：页面与交互 | T-02 | — | design §2 / AC-01、02、05 | 已完成（走查通过） |
| T-04 | 验证 | T-01、T-03 | — | AC-08 | 已完成 |
| T-05 | 文档与交付记录 | T-04 | — | design §5 | 已完成 |
| T-06 | 本机运行实例重启与页面走查 | T-04；用户授权重启 | — | AC-01～AC-05 | 已完成（未触发真实运行与保存） |
| T-07 | 只读复查认领与补建结果 | T-06 | — | AC-04 | 已完成 |

执行顺序：T-01 与 T-02、T-03 并行 → T-04 → T-05 → T-06 → T-07。两个仓库的工作区都有与本需求无关的未提交改动，实施只改动了下列明确列出的文件，未提交、未部署。

## 3. 任务明细

### T-01：后端职责执行任务与员工级接口

- 描述与目标：新增两个员工级接口，提供职责执行任务的查询、认领、补建，以及按员工列运行；加保留标识与删除保护；更新两句提示文案。
- 输入：design §2、§3；`AgentWorkbenchService.java`、`AgentMapper.java`、`AgentController.java`、`AgentAccessService.java`、`AgentTaskSpecification.java`。
- 输出：上述源码修改；`AgentWorkbenchServiceTest` 新增 7 项用例。
- 前置依赖：规格确认。
- 允许修改范围：`workhub-controller/…/agent/AgentController.java`；`workhub-service/…/agent/AgentWorkbenchService.java`、`AgentAccessService.java`；`workhub-dao/…/agent/AgentMapper.java`；`workhub-model/…/agent/AgentTaskSpecification.java`；对应测试。
- 明确不修改范围：`AgentTaskController` 与全部任务级接口、执行适配器、通知、业务监测、验收、对账、`IntakeAiDevelopmentService`、`AgentFixService`、数据库结构与迁移。

验收条件：

- [x] 新建员工首次取职责执行任务时得到默认配置任务；再次调用返回同一条。
- [x] 员工名下有手工任务与需求开发任务时，认领运行次数最多的手工任务；需求开发任务与问题修复任务不被认领；写 `ADOPT_DUTY` 审计；规格与版本不变。
- [x] 按员工列运行覆盖其全部任务并按开始时间倒序；非归属人且无 `agent:manage` 被拒绝。
- [x] 创建任务使用请求标识 `duty` 被拒绝；删除职责执行任务被拒绝；删除员工仍级联清除。
- [x] 未限定范围员工的默认执行范围为全部启用业务线及其环境；已限定范围的取其授权范围。

计划验证方式：`mvn -pl workhub-service -am test -Dtest=AgentWorkbenchServiceTest -Dsurefire.failIfNoSpecifiedTests=false`，以及 `mvn -pl workhub-controller -am compile`。

状态及真实结果：已完成（2026-11-26）。改动：`AgentMapper` 新增 `findDutyCandidate`、`updateTaskRequestKey`、`listRunsByAgent`；`AgentAccessService` 新增 `allEnabledScopes`；`AgentWorkbenchService` 新增 `DUTY_TASK_REQUEST_KEY`、`dutyTask`、`agentRuns`、`provisionDutyTask`、`defaultDutySpecification`，`createTask` 拒绝保留标识，`deleteTask` 保护职责执行任务；`AgentController` 新增两个接口；补充指令上限与完成目标两句提示文案更新。新增用例 7 项全部通过：`dutyTaskIsProvisionedOnceWithDefaultSpecificationAndRunsDirectly`、`dutyTaskDefaultsToEveryEnabledScopeWhenEmployeeIsUnrestricted`、`dutyTaskAdoptsTheMostRunManualTaskOnceAndLeavesSystemTasksAlone`、`dutyTaskAdoptionIsDeterministicWhenManualTasksTie`、`dutyTaskForEmployeeWithOnlySystemTasksIsCreatedAlongsideThem`、`dutyTaskReservedKeyAndDeletionAreGuardedWhileAgentDeletionStillCascades`、`agentRunsListEveryTaskOfTheEmployeeNewestFirstAndRespectOwnership`。

整类结果：Tests run: 85, Failures: 1, Errors: 0, Skipped: 1（83 通过）。失败项 `apiPromptRequiresEli5SummaryReport` 是存量失败，与本次无关：在临时 worktree 里以“HEAD `e7b7b7b` + 工作区其他未提交改动、不含本需求 6 个文件”为基线单跑该用例，同样在同一断言失败（HEAD 版第 975 行 `contains("ELI5")`）。本次改动不涉及提示词、模板或执行适配器。该用例对应的结果整理提示词属于其他在途工作，未在本需求内处理。

编译：`mvn -pl workhub-controller -am compile` 与 `mvn -pl workhub-bootstrap -am compile`（全模块）均 BUILD SUCCESS。说明：`-am` 会把 `-Dtest` 过滤套到依赖模块，需带 `-Dsurefire.failIfNoSpecifiedTests=false`，否则模块因无匹配测试直接判失败。

### T-02：前端 API 与类型

- 描述与目标：API 层改为员工级入口，删除页面不再使用的任务级函数与类型。
- 输入：design §3。
- 输出：`src/api/agent.ts` 新增 `fetchAgentDutyTask`，`fetchAgentRuns` 改为按员工取数；删除 `fetchAgentTasks`、`createAgentTask`、`fetchAgentTask`、`saveAgentSchedule`、`deleteAgentTask`；`src/types/agent.ts` 删除 `AgentTaskCreate`。
- 前置依赖：规格确认。
- 允许修改范围：`src/api/agent.ts`、`src/types/agent.ts`。
- 明确不修改范围：其他 API 与类型文件（含工作区已有的 `src/types/work-item.ts` 改动）。

验收条件：

- [x] 两个函数的路径与参数与 design §3 一致；运行列表每页 5 条。
- [x] 被删除的函数与类型在全仓库没有残留引用。

计划验证方式：`npm run build`。

状态及真实结果：已完成（2026-11-26）。全仓库 grep 被删除符号无残留（`canAssign` 仅剩工作项页面自己的同名变量）。

### T-03：前端页面与交互

- 描述与目标：任务成果弹框改为员工级；移除任务列表、「分派任务」、任务编辑与删除、「调整周期」抽屉；新增「执行设置」。
- 输入：design §2。
- 输出：
  - `AgentWorkbenchView.vue`：「任务成果」直接打开员工级弹框；历史运行、详情加载、开始新一轮按员工与职责执行任务取数；删除任务列表和周期抽屉的状态、函数、模板与样式。
  - `AgentTaskDialog.vue`：改为只编辑的「执行设置」抽屉。
  - `AgentRunPanel.vue`：按 `dutyObjective` 显示「本轮目标」。
  - `AgentConfigDialog.vue`、`AgentRunAcceptancePanel.vue`：与分派相关的提示文案。
- 前置依赖：T-02。
- 允许修改范围：`src/views/agent/` 下列出的五个文件。
- 明确不修改范围：报告渲染、历史侧栏、验收、对账、问题、候选变更等面板的内部逻辑。

验收条件：

- [x] 页面没有任务列表、「分派任务」与「调整周期」；「任务成果」直接打开弹框。
- [x] 「历史运行」展示方式不变；运行详情按运行自带的 `taskId` 加载。
- [x] 「开始新一轮」使用职责执行任务；「执行设置」保存后弹框内的常设配置随之更新。
- [x] 员工被删除时，打开中的任务成果弹框关闭。
- [x] 构建通过。
- [x] 真实页面走查（AC-01、02、05 的页面部分）：见 T-06。

计划验证方式：`npm run build`；页面走查。

状态及真实结果：已完成（2026-11-26），页面走查见 T-06。实现与 design §2 的三处细化：职责截断的展开入口改为按实际溢出测量显示（展开状态下不重新测量，保证可收起），而不是按字数阈值；「执行设置」保存时带上原有的验收标准，避免像原表单那样在编辑时被置空；走查后把执行范围多选框改为折叠显示（`collapse-tags`）。其余与设计一致：弹框标题为「任务成果 · 员工名」，工作目标为空时显示“按员工职责执行”；常设配置读取失败时历史运行照常显示，只收起「执行设置」和「开始新一轮」并提示。`npm run build`（vue-tsc 类型检查 + vite）通过（首次 `✓ built in 4.38s`，折叠显示调整后 `✓ built in 5.62s`）；agent 视图 grep 无“分派、调整周期、任务列表、任务范围、任务目标”残留。

### T-04：验证

- 描述与目标：执行 T-01、T-03 的计划验证并记录真实输出。
- 输入：T-01、T-03 的产物。
- 输出：本文件中的验证证据。

验收条件：

- [x] 后端定向测试与编译已执行，失败项如实记录（T-01）。
- [x] 前端 `npm run build` 通过。

计划验证方式：见 T-01、T-03。

状态及真实结果：已完成（2026-11-26），证据见 T-01、T-03。

### T-05：文档与交付记录

- 描述与目标：更新系统设计与迭代记录。
- 输入：T-01–T-04 的结果。
- 输出：`现状.md` 新增小节并在两处旧描述后加指引；`迭代日志.md` 新增条目；workhub-web `docs/frontend-architecture.md` 数字员工小节三处说明。

验收条件：

- [x] 三处文档与实际实现一致，并注明未部署、页面走查状态。

计划验证方式：文档核对。

状态及真实结果：已完成（2026-11-26）。

### T-06：本机运行实例重启与页面走查

- 描述与目标：用户授权重启本机运行实例加载新代码，并在内置浏览器走查任务成果弹框。
- 输入：T-01、T-03 的产物。
- 输出：重启记录与走查结论。

验收条件：

- [x] 重启前确认没有排队或执行中的运行（生产库只读核对为 0），且工作区没有待执行的 Flyway 迁移。
- [x] 新实例启动成功并响应。
- [x] 页面走查 AC-01～AC-05 的页面部分（「开始新一轮」与「保存设置」两个写入动作未触发，见状态说明）。

状态及真实结果：已完成（2026-11-26），页面走查见本节末尾。原实例是 IDEA 启动的调试进程（PID 88013，2026-11-25 19:27 启动）。重启前核对：`pm_agent_run` 无排队、执行中或停止中的运行；工作区在实例启动后没有新增或改动的迁移文件；全模块编译后用原进程的 classpath 与环境变量（含 MCP 访问令牌，原样带过去，未打印、未落盘）在仓库根目录拉起新实例，去掉 IDEA 调试代理参数。新实例 PID 49385，监听 8080，Flyway “Successfully validated 106 migrations … Schema `workhub` is up to date”，启动耗时 2.908 秒，实际中断约 5 秒；日志在系统临时目录 `workhub-local-instance-20261126-101914.log`。

需要知悉的两点：其一，原 IDEA 调试会话已结束，8080 当时由本次拉起的进程占用；其二，重启加载的是整个工作区，顺带生效了今天 09:29–09:30 的头像改存数据库改动及 `application.yml` 移除本地存储路径（该改动只涉及头像存取，登录链路未动，其 14 项测试通过）。

后续：用户登录内置浏览器后指示“杀掉进程”，已于 2026-11-26 10:51 前后用 SIGTERM 停止 PID 49385（约 4 秒正常退出），8080 已释放、交还用户从 IDEA 启动；该进程运行期间日志无 ERROR。停止时刻的在途运行未能核对：只读查询经 MCP 发出，而 MCP 依赖这个后端本身。用户随后于 11:03 从 IDEA 调试启动新实例（PID 89214，class 指向各模块 `target/classes`，含本次改动），并在内置浏览器登录。

页面走查（2026-11-26 11:10 前后，内置浏览器，`http://127.0.0.1:9091/agents`，现有 Vite dev server 热更新提供新代码）：

- 员工列表：6 个员工，操作列为「任务成果 / 配置 / 停用 / 删除」，一行内右对齐；没有「分派任务」。
- 保费分期监测巡检助手（首次打开触发认领）：弹框全屏，标题「任务成果 · 保费分期监测巡检助手」，标题下是认领来的工作目标，「职责」一行，标签「分析报告 / 增量分析」，工具栏「刷新 / 执行设置 / 开始新一轮」；历史运行 124 轮、每页 5 条，翻到第 2 页顺序正确；切换历史运行后右侧详情随之切换。弹框内没有“分派、调整周期、任务列表、分派人”残留文案。
- 研发工程师（首次打开补建默认配置）：标题下显示“按员工职责执行”，职责一行；历史运行把 4 个需求开发任务的运行合并为 6 轮；需求开发的运行显示「本轮目标：按已确认任务拆解在研发分支 … 完成需求开发」。
- 智能柜监测巡检助手：认领 131 轮的巡检任务（目标“巡检智能柜系统，输出汇总报告”），历史运行合并为 132 轮。
- 叉车运维员：1280px 下职责两行、不截断、无展开入口；640px 下截断为两行并出现「展开」，展开后三行、入口变「收起」，收起后恢复。900px 下历史侧栏折叠，「查看历史运行」可展开为双列并收起。
- 执行设置（研发工程师，只打开不保存）：标题「执行设置 · 研发工程师」，字段为工作目标、补充资料、期望产物、数据范围、执行范围、业务监测、执行方式；值为分析报告、全量分析、读取已有核验结果、手动执行。

走查中发现并修复一处版式问题：未限定范围员工的默认执行范围有 28 项，多选框标签铺满五行，把下方字段挤出视野。`AgentTaskDialog.vue` 的执行范围选择器改为折叠显示（`collapse-tags`，前 6 项加 “+N”，悬浮可看全部），复看后业务监测与执行方式进入视野。修复后 `npm run build` 通过（`✓ built in 5.62s`）。

未执行的两项：没有点「开始新一轮」（会发起真实巡检运行并触发通知），没有点「保存设置」（会写生产配置）。两者需用户明确授权后再验证。

### T-07：只读复查认领与补建结果

- 描述与目标：走查打开各员工任务成果后，核对生产库中的认领与补建结果。
- 输入：T-06。
- 输出：只读查询结果。

验收条件：

- [x] `pm_agent_task WHERE request_key='duty'` 与 design §1 基线的“处理”列一致（已打开过任务成果的员工才会出现）；`pm_agent_audit` 中对应 `ADOPT_DUTY` 行存在。

状态及真实结果：已完成（2026-11-26）。本机运行实例连接的就是生产运行库，走查后只读核对：走查打开过的 4 个员工结果与 design §1 基线一致——保费分期、智能柜、叉车的常设配置任务就是原巡检任务（`2181445e-…`、`e2d82c44-…`、`12bd560d-…`），各带一条 `ADOPT_DUTY` 审计，规格、版本与运行数不变（124 / 131 / 91 轮）；研发工程师补建默认配置 `13af3c9f-…`（工作目标为空、全量分析、28 个范围、手动，带 `CREATE` 审计），4 个需求开发任务原样保留。未打开的消费分期、新消费分期尚无常设配置任务，首次打开时再认领。除此之外没有其他部署目标。

## 4. 覆盖与评审

- Proposal 成功标准是否均映射至任务：AC-01、02、05 → T-03、T-06；AC-03 → T-01，其页面入口在 T-06 核对；AC-04、06 → T-01、T-07；AC-07 → T-01（既有测试）；AC-08 → T-04。
- Design 接口/数据/非功能约束是否均有承接：契约 → T-01、T-02；页面交互 → T-03；认领审计与权限 → T-01；文档更新 → T-05。
- 未覆盖项及原因：AC-03 的真实执行（点「开始新一轮」）与 AC-05 的页面保存（点「保存设置」）没有触发，因为两者会分别发起真实巡检运行、写生产配置，需用户明确授权；后端对应逻辑由既有 `run`、`updateTask` 单测覆盖。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：T-05 已完成；走查后如发现页面问题，在本文件与迭代日志补记。
- 关联 Proposal、Design 与契约的版本链接：[proposal.md](proposal.md) v1.0、[design.md](design.md) v1.0（本目录）。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：范围为三份规格全部内容（proposal v1.0、design v1.0、tasks v1.0）；用户于 2026-11-26 确认实施，并对 design §6 四个问题逐项答复：存量任务按规格认领（智能柜认领 131 轮巡检任务，“业务监测 v4 一次性验收”任务只保留历史，不新增“按历史运行再跑一轮”）；「开始新一轮」不加二次确认；未限定范围员工的默认执行范围取全部启用业务线及其环境；授权实施完成后重启本机运行实例做页面走查。
