# Tasks：需求提测环境必选与 Jenkins 环境放开

## 1. 元信息

- 需求名称与编码：需求提测环境必选与 Jenkins 环境放开（REQ-db1f9f08-242d-4ba6-8fce-962f4f64aafa）
- 涉及仓库与功能域：workhub-server 需求管理提测确认与测试发布校验；workhub-web 需求管理提测弹窗与测试发布面板
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0
- Design/契约版本：v1.0（无接口字段变更，语义收紧）
- 研发分支：server/web 均为 `codex/development-20261122`
- 任务计划状态：已实施（T-09 页面走查待部署）
- 负责人：AI（ZCode）
- 评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端 validateBinding 环境泛化 | 无 | 与 T-02/T-03 并行 | design §2 规则 2 / AC-03、AC-04 | 已实施 |
| T-02 | 后端 confirmAndSubmit 环境必选+一致性+回执 | 无 | 与 T-01/T-03 并行 | design §2 规则 1/3/4 / AC-02、AC-03、AC-04 | 已实施 |
| T-03 | 后端 DTO 注释更新 | 无 | — | design §3 / AC-02 | 已实施 |
| T-04 | 后端单测更新与新增 | T-01、T-02 | — | AC-02、AC-03、AC-04 | 已通过 |
| T-05 | 前端提测弹窗环境必选下拉与联动 | 无 | 与后端任务并行 | design §2 / AC-01、AC-02 | 已实施 |
| T-06 | 前端发布面板环境过滤与 label | 无 | 与 T-05 并行 | design §2 / AC-01、AC-03 | 已实施 |
| T-07 | 后端全量单测 | T-04 | — | AC-05 | 已通过（存量无关失败除外） |
| T-08 | 前端构建验证 | T-05、T-06 | 与 T-07 并行 | AC-05 | 已通过 |
| T-09 | 页面走查（部署后） | 部署 | — | AC-01、AC-02 | 待部署 |

执行顺序：T-01/T-02/T-03 → T-04 → T-07；T-05/T-06 → T-08；部署 → T-09。

## 3. 任务明细

### T-01：validateBinding 环境泛化

- 描述与目标：`IntakeTestDeploymentService.validateBinding` 从 TEST 硬编码改为按绑定环境推导。
- 输入：design §2 关键规则 2。
- 输出：`workhub-service/src/main/java/cn/aslight/workhub/service/intake/IntakeTestDeploymentService.java` 修改。
- 允许修改范围：仅 validateBinding 方法及其报错文案。
- 明确不修改范围：bindings/validateRequest/submit/watch 流程、GitLab 分支命名、Job 参数核验。

验收条件：

- [x] 条件 1：`environmentCode` 为空或 PROD 时绑定被拒绝。
- [x] 条件 2：SIT 绑定配 `sit-` 前缀 jobName 与 deployTarget 可通过校验；SIT 绑定配 `test-` 前缀被拒绝。
- [x] 条件 3：既有 TEST 绑定（`test-scs-saps`/`test-scs-server`）行为不变。

计划验证方式（尚未执行）：IntakeTestDeploymentServiceTest 新增/扩展用例。

状态及真实结果：已通过；rejectsProductionEnvironmentAndJob 扩展 4 类坏例（PROD、prod Job、SIT 配 test- Job、SIT 配 test- deployTarget），新增 sitBindingWithMatchingPrefixSubmits 正例，IntakeTestDeploymentServiceTest 10/10 通过。

### T-02：confirmAndSubmit 环境必选+一致性+回执

- 描述与目标：环境必填移出 triggerJenkins 条件；非 PROD；属于业务线支持环境（未配置回退 TEST）；Jenkins 时与绑定环境一致；回执始终写入选环境。
- 输入：design §2 关键规则 1/3/4。
- 输出：`IntakeSubmitTestReviewService.java` 修改（新增 BusinessLineMapper 构造依赖）。
- 允许修改范围：confirmAndSubmit 方法、构造器、相关 import。
- 明确不修改范围：AI 审核流程、禅道测试单创建、阶段推进。

验收条件：

- [x] 条件 1：不开 Jenkins 缺环境也报"请选择测试环境后再确认提测"。
- [x] 条件 2：PROD 环境被拒绝；非业务线支持环境被拒绝。
- [x] 条件 3：环境与绑定不符报"测试环境与所选测试发布目标不符"。
- [x] 条件 4：回执 jenkinsEnvironmentCode = 所选环境（含不开 Jenkins 场景）。

计划验证方式（尚未执行）：IntakeSddFlowTest 用例。

状态及真实结果：已通过；新增 submitTestConfirm_shouldRequireEnvironmentEvenWithoutJenkins、shouldRejectProductionAndUnsupportedEnvironment、shouldRequireEnvironmentMatchingDeploymentBinding、shouldRecordEnvironmentWithoutJenkins（覆盖小写归一化），既有用例补 BusinessLineMapper mock 与环境入参，IntakeSddFlowTest 30/30 通过。

### T-03：DTO 注释更新

- 描述与目标：`IntakeSubmitTestConfirmRequest.environmentCode` 注释改为"提测确认选择的测试环境编码；必填，不允许 PROD"。
- 输出：`workhub-model/src/main/java/cn/aslight/workhub/model/intake/IntakeSubmitTestConfirmRequest.java`。

验收条件：

- [x] 条件 1：注释与实际校验一致，类头注释同步。

状态及真实结果：已实施；类头与字段注释同步更新，另为 IntakeSubmitTestReviewEntity.jenkinsEnvironmentCode 补充语义注释。

### T-04：后端单测更新与新增

- 描述与目标：IntakeSddFlowTest 各 confirmAndSubmit 用例补 BusinessLineMapper mock 与环境入参；新增缺环境/PROD/绑定不符用例；IntakeTestDeploymentServiceTest 扩展 rejectsProductionEnvironmentAndJob 并新增 SIT 正例。
- 输出：两份测试文件修改。

验收条件：

- [x] 条件 1：全部既有用例（除有意收紧的缺环境场景）通过。
- [x] 条件 2：新增用例覆盖 AC-02、AC-03、AC-04。

状态及真实结果：已通过；`mvn -pl workhub-service -am test -Dtest='IntakeSddFlowTest,IntakeTestDeploymentServiceTest'` 40/40 通过（2026-11-23）。

### T-05：前端提测弹窗环境必选下拉与联动

- 描述与目标：新增"测试环境"必选下拉（选项=业务线非 PROD 环境，回退 TEST，默认 TEST）；提交校验与请求体始终带环境；环境切换重置发布选择；无匹配绑定时禁用 Jenkins 开关；详情回执新增"提测环境"指标卡。
- 输出：`workhub-web/src/views/intake/IntakeListView.vue` 修改。

验收条件：

- [x] 条件 1：弹窗可见环境下拉且带必填标记，不选无法提交（warning"请选择测试环境"）。
- [x] 条件 2：环境选项随需求业务线变化；未配置环境回退 TEST。
- [x] 条件 3：请求体 environmentCode 始终传值。
- [x] 条件 4：详情回执显示提测环境。

状态及真实结果：已实施；另加"当前环境未登记测试发布目标"提交拦截文案与开关禁用提示。代码级验证通过（T-08 构建），页面走查待部署（T-09）。

### T-06：前端发布面板环境过滤与 label

- 描述与目标：`IntakeTestDeploymentPanel.vue` 新增可选 prop environmentCode，按绑定环境过滤并联动重选；选项 label 显示绑定真实环境；change 事件附带绑定环境供父组件同步。
- 输出：`workhub-web/src/views/intake/IntakeTestDeploymentPanel.vue` 修改。

验收条件：

- [x] 条件 1：详情页无 prop 用法行为不变。
- [x] 条件 2：弹窗中面板仅显示所选环境的绑定。

状态及真实结果：已实施；绑定列表改为 allBindings + computed 过滤，环境变化或重新加载后自动重选首个匹配绑定并清空合测需求。代码级验证通过（T-08 构建），页面走查待部署。

### T-07：后端全量单测

- 输入：T-01～T-04 产物。
- 输出：`mvn -pl workhub-service -am test` 结果。

验收条件：

- [x] 条件 1：workhub-service 全量测试通过（存量无关失败除外，见下）。

状态及真实结果：已执行；workhub-model/support/dao/mcp-server/controller/job 全部通过，workhub-service 1313 个测试仅 2 个失败，均为与本次改动无关的存量失败：AgentWorkbenchServiceTest.apiPromptRequiresEli5SummaryReport（属前序会话未提交的 AgentWorkbenchService 改动，该文件有 365 行未提交修改）、BusinessLogSearchServiceTest（ELK 范围配置 BL000001.PROD 未启用的环境依赖问题）。排除这两个类后下游模块全部通过。workhub-bootstrap 存量失败 TeamAliasRecoveryStaticTest（仓库中不存在 ../docs/WorkHub/团队重复人员合并/SDD/rollback.sql），同样与本次无关。

### T-08：前端构建验证

- 输入：T-05、T-06 产物。
- 输出：`npm run build` 结果。

验收条件：

- [x] 条件 1：vue-tsc 类型检查与 vite 构建通过。

状态及真实结果：已通过；2026-11-23 `npm run build` 输出 `✓ built in 4.22s`（vue-tsc 与 tsc 类型检查均含在内）。

### T-09：页面走查（部署后）

- 输入：两侧部署产物。
- 输出：AC-01、AC-02 页面核对结论。

验收条件：

- [ ] 条件 1：提测弹窗环境必选生效，选项正确。
- [ ] 条件 2：提测完成后回执显示所选环境。

## 4. 覆盖与评审

- Proposal 成功标准是否均映射至任务：AC-01 → T-05/T-09；AC-02 → T-02/T-04/T-05；AC-03、AC-04 → T-01/T-02/T-04；AC-05 → T-07/T-08。
- Design 接口/数据/非功能约束是否均有承接：契约语义收紧 → T-02/T-03/T-05；安全护栏 → T-01/T-02。
- 未覆盖项及原因：T-09 页面走查待部署；非 TEST 环境绑定登记数据本期不新增（运维按 `{env小写}-{system}` 约定在 sys_config 登记，见 design §3）。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：本目录三份规格已随实施更新；需求迭代日志待随分支交付时记录。
- 关联 Proposal、Design 与契约的版本链接：Proposal v1.0 / Design v1.0（本目录）。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：范围为本目录三份规格全部内容；v1.0；用户于 2026-11-23 提出需求并确认两项规格决策（环境始终必选；Jenkins 环境本次一并放开），实施计划经用户批准；实施于 server/web 分支 `codex/development-20261122`。
