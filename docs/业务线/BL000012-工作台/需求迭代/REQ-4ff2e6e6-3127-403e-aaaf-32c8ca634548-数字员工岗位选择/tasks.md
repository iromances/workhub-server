# Tasks：数字员工岗位选择

## 1. 元信息

- 需求编码：REQ-4ff2e6e6-3127-403e-aaaf-32c8ca634548
- 需求名称与唯一需求目录：数字员工岗位选择；docs/业务线/BL000012-工作台/需求迭代/REQ-4ff2e6e6-3127-403e-aaaf-32c8ca634548-数字员工岗位选择/
- 涉及仓库与功能域：workhub-server（model/service/迁移）；workhub-web（AgentConfigDialog、types）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：草案 v1
- Design/契约版本：草案 v1
- 目标环境与分支：workhub-server codex/development-20261102；workhub-web codex/development-20261103
- 任务计划状态：规格已确认并实施完成
- 负责人/评审人：用户（2026-11-16 确认标准 IT 岗位+全栈开发、必填、联动起草；追加公司常见岗位与模糊匹配）

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端字段+校验+options+起草联动+迁移 | 规格确认 | 无 | §2/§3、AC-01～04 | 已完成 |
| T-02 | 后端单测与构造点适配 | T-01 | 无 | AC-01/03/04 | 已完成 |
| T-03 | 前端岗位下拉与起草联动 | 规格确认 | 契约冻结 | AC-01/02 | 已完成 |
| T-04 | 验证与交付记录 | T-01～T-03 | 无 | §5 | 已完成 |

执行顺序：T-01 → T-02 → T-03 → T-04；后端先于前端发布。

## 3. 任务明细

### T-01：后端岗位链路

- 描述与目标：`AgentConfiguration` 增 `position`（name 之后，@NotBlank @Size(64)）；`AgentWorkbenchService` 保存路径校验（空→"请选择岗位"；不在启用字典→"岗位不存在或已停用"）；`AgentOptionsResponse.positions` + `AgentOptionsService` 注入 DictService；`AgentInstructionsDraftRequest.positionName` 与服务变量、`AiUseCaseDefinitions` 白名单；迁移：字典 agentPosition 种子（IT+全栈+公司常见）+ `agent.instructions.draft` 模板 v2（岗位段）并登记 `TestPromptTemplates`。
- 输入：design §2/§3。
- 输出：代码与 `V20261116_170000__agent_position.sql`。
- 明确不修改范围：agent.*.step 执行模板、Agent 列表展示、字典管理页。

验收条件：

- [ ] 保存校验与 options 下发按契约生效（AC-01）。
- [ ] 起草提示词含岗位段（AC-03）。

计划验证方式（尚未执行）：`mvn -pl workhub-service -am test -Dtest='AgentWorkbenchServiceTest,AgentInstructionsDraftServiceTest,PromptTemplateMigrationConsistencyTest'`。

状态及真实结果：已完成（2026-11-16）。`AgentConfiguration` 增 position（name 后，@NotBlank @Size(64)）；`AgentWorkbenchService` 注入 DictService，validateConfiguration 增岗位校验（空→请选择岗位；不在启用字典→岗位不存在或已停用），运行快照构造同步携带岗位；`AgentOptionsResponse.positions` + `AgentOptionsService` 下发；`AgentInstructionsDraftRequest.positionName` 与服务变量；`AiUseCaseDefinitions` 白名单增 positionName；迁移 `V20261116_170000`（字典 agentPosition 22 项 ON DUPLICATE KEY 种子 + 起草模板 v2 岗位段）登记 `TestPromptTemplates`。

### T-02：后端单测与构造点适配

- 描述与目标：14 处 `AgentConfiguration` 构造调用点补 position；新增用例：无岗位创建拦截、岗位不在字典拦截、带岗位(Ops)创建成功、存量无岗位 detail 读取正常、起草 positionName 变量断言。
- 验收条件：
  - [ ] 相关套件全绿（AC-01/03/04）。

计划验证方式（尚未执行）：同 T-01 命令 + 定向运行涉及的 13 个测试文件中受影响者。

状态及真实结果：已完成（2026-11-16）。14 处构造调用点补 position（统一 "OPS"）；AgentWorkbenchServiceTest/AutonomyCaseServiceTest/AgentInspectionServiceTest 建 dict 表+OPS 种子并注入 DictService；新增用例 `positionIsRequiredAndMustExistInDictionary`（缺岗位、未知岗位、存量无岗位读取/编辑行为）。定向结果：Workbench 69（+1）、Draft 6（positionName 断言）、模板一致性 2、Autonomy 16、Inspection 27、DeploymentDispatcher 30、Verification 65、ReleasePlan 35、Change 42、BuildTestDispatcher 40、CodeWorkspace 12、SystemAlertVerificationQuery 16、RunNotifier 3、JenkinsPolicy 6——全部 0 失败；`mvn compile` 全模块通过。

### T-03：前端岗位下拉

- 描述与目标：`AgentConfiguration` 类型加 position；`AgentOptions` 加 positions；对话框"岗位"必填下拉（options.positions，filterable 模糊匹配，选项 label=中文名）、submit 校验"请选择岗位"、起草 payload 带 positionName（按 code 取中文名）。
- 验收条件：
  - [ ] 必填拦截与模糊过滤生效（AC-01/02）；`npm run build` 通过。

计划验证方式（尚未执行）：`npm run build`；部署后走查。

状态及真实结果：已完成（2026-11-16，分支 codex/development-20261103）。`AgentConfiguration` 类型加 position、`AgentOptions` 加 positions；对话框名称后新增"岗位"必填下拉（filterable 输入过滤）、submit 校验"请选择岗位"、存量配置 position 兜底空串、起草 payload 按岗位 code 映射中文名 positionName；`AgentWorkbenchView` 默认 options 补 positions。`npm run build`（vue-tsc + vite）通过。页面走查待部署。

### T-04：验证与交付记录

- 验收条件：
  - [ ] 证据写入本文件；迭代日志/现状更新。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01～04 → T-01/T-02/T-03。
- 未覆盖项及原因：无。
- 完整规格统一确认：范围=proposal v1 + design v1 + tasks v1；用户于 2026-11-16 确认实施（含三项前置决定与两项追加：全栈开发、公司常见岗位、模糊匹配）；实施完成后证据见各任务明细。生产生效待部署（后端含迁移先发、前端随后）。
