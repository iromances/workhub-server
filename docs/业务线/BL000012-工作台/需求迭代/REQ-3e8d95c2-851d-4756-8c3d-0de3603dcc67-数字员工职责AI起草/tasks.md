# Tasks：数字员工职责 AI 起草

## 1. 元信息

- 需求编码：REQ-3e8d95c2-851d-4756-8c3d-0de3603dcc67
- 需求名称与唯一需求目录：数字员工职责 AI 起草；docs/业务线/BL000012-工作台/需求迭代/REQ-3e8d95c2-851d-4756-8c3d-0de3603dcc67-数字员工职责AI起草/
- 涉及仓库与功能域：workhub-server（model/service/controller/迁移）；workhub-web（AgentConfigDialog、api/types）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：草案 v1
- Design/契约版本：草案 v1
- 目标环境与分支：workhub-server codex/development-20261102；workhub-web codex/development-20261103
- 任务计划状态：规格已确认并实施完成
- 负责人/评审人：用户（2026-11-16 确认前后端一起实施）

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端场景+请求/响应模型+服务+端点+迁移 | 规格确认 | 无 | §2/§3、AC-01～04 | 已完成 |
| T-02 | 后端单测（服务校验/调用/裁剪、模板一致性） | T-01 | 无 | AC-01～04 | 已完成 |
| T-03 | 前端对话面板+API 封装 | 规格确认（可与 T-01 并行，契约已定） | 契约冻结 | §2、AC-05 | 已完成 |
| T-04 | 验证与交付记录 | T-01～T-03 | 无 | §5 | 已完成 |

执行顺序：T-01 → T-02 → T-03（构建验证）→ T-04；后端先于前端发布。

## 3. 任务明细

### T-01：后端起草链路

- 描述与目标：注册 `agent.instructions.draft` 场景（MODEL_ONLY，API+CLI，变量 agentName/scopeSummary/conversation）；新增 `AgentInstructionsDraftRequest/Response` 与 `DraftMessage`；`AgentInstructionsDraftService`（校验、conversation/scopeSummary 组装、`AiGatewayRequest` 携带 provider/model 覆盖、execute 纯文本、trim/12000 截断）；`AgentController` 新增 `POST /api/agents/instructions-draft`（agent:manage）；迁移种子模板。
- 输入：design §2/§3；`AiUseCaseDefinitions`、`DefaultAiGatewayClient`、`IntakeSubmitTestReviewService` 模式。
- 输出：上述代码与 `V20261116_xxxxxx__agent_instructions_draft.sql`；`TestPromptTemplates.MIGRATION_FILES` 登记新迁移。
- 明确不修改范围：网关 `DefaultAiGatewayClient`、既有场景与 Agent 保存逻辑。

验收条件：

- [ ] 端点按契约返回草稿文本与实际模型；覆盖接入/模型生效（AC-01/02/03）。
- [ ] 参数边界（空消息、>20 条、>8000 字、role 非法、缺接入）被校验拦截（AC-04）。

计划验证方式（尚未执行）：`mvn -pl workhub-service -am test -Dtest='AgentInstructionsDraftServiceTest,PromptTemplateMigrationConsistencyTest'`。

状态及真实结果：已完成（2026-11-16）。改动：`AiUseCaseDefinitions` 注册 `agent.instructions.draft`（MODEL_ONLY，API+CLI，变量 agentName/scopeSummary/conversation）；新增 `AgentInstructionsDraftRequest/Response`（含 DraftMessage，消息 ≤20 条、单条 ≤8000、role ∈ user|assistant）、`AgentInstructionsDraftService`（网关 `AiGatewayRequest` 覆盖接入/模型，输出 trim 并截断 12000）、`AgentController` 新增 `POST /api/agents/instructions-draft`（agent:manage）；迁移 `V20261116_160000__agent_instructions_draft.sql` 种子模板（provider 绑定取首个接入仅满足非空约束），`TestPromptTemplates.MIGRATION_FILES` 已登记。验证见 T-02/T-04。剩余风险：真实模型联调待部署。

### T-02：后端单测

- 描述与目标：`AgentInstructionsDraftServiceTest`（mock AiGatewayClient：初稿/修订两轮、失败文案、输出截断、校验边界）；`PromptTemplateMigrationConsistencyTest` 随新场景通过。
- 输出：测试代码与执行证据。
- 验收条件：
  - [ ] 全部断言通过；既有测试无回归。

计划验证方式（尚未执行）：同 T-01 命令，另跑 `AgentWorkbenchServiceTest` 抽查回归。

状态及真实结果：已完成（2026-11-16）。`AgentInstructionsDraftServiceTest` 6/6（初稿/修订对话组装、覆盖传递、失败文案、空输出、12000 截断、Bean 校验边界）；`PromptTemplateMigrationConsistencyTest` 2/2（新模板占位与白名单一致）；`AgentWorkbenchServiceTest` 68 项回归通过且新迁移在 H2 MySQL 模式执行成功；`mvn compile` 全模块通过。

### T-03：前端对话面板

- 描述与目标：`AgentConfigDialog.vue` 职责旁"AI 起草"按钮 → 内嵌面板：接入下拉（默认表单已选）+ 模型输入（默认表单/接入默认）+ 对话区（首条提示语）+ 发送（loading）+ "采用此稿"（回填，已有内容先确认）；`api/agent.ts` 增 `draftAgentInstructions`；`types/agent.ts` 增类型。
- 输出：组件与类型代码、`npm run build` 通过。
- 验收条件：
  - [ ] 面板可完成"描述→初稿→调整→采用"闭环（AC-01/05）；构建通过。

计划验证方式（尚未执行）：`npm run build`；部署后页面走查。

状态及真实结果：已完成（2026-11-16，分支 codex/development-20261103）。改动：`AgentConfigDialog.vue` 职责区新增“AI 起草”工具行与内嵌对话框（对话气泡区、发送调整/生成初稿、采用此稿回填且已有内容先 ElMessageBox 确认覆盖、会话随抽屉重置、前端侧消息截取最近 20 条）；`api/agent.ts` 增 `draftAgentInstructions`；`types/agent.ts` 增三个类型。`npm run build`（vue-tsc + vite）通过。页面走查待部署。

后续变更（2026-11-16，用户要求）：起草对话框移除首轮示例提示文案；接入与模型选择保留。

后续变更②（2026-11-16，用户澄清后修正）：仅删除冗余显示文案——AI 接入选项去掉” · 通道类型”后缀、模型输入占位不再回显默认模型串（改为”留空使用默认模型”）、职责旁提示文字与底部 MCP 说明 alert 删除；”AI 接入””模型”两个字段在主表单与起草对话框均保留（起草默认带入表单已选）。曾误删两字段后已恢复。`npm run build` 通过。

后续变更③（2026-11-16，用户要求）：起草入口文案改”帮我起草”；起草对话框去掉接入/模型选择区，模型与推理强度仅以”GLM-5.3-Flash · 高”文案标注在”生成初稿”按钮旁；后端起草服务固定 reasoningLevelOverride=HIGH，`AgentInstructionsDraftRequest.messages` 放宽为可空（空对话=按岗位与业务范围直接起草初稿，变量占位”（尚无对话…）”）。`AgentInstructionsDraftServiceTest` 7/7（新增空对话起草用例）；`npm run build` 通过。另：空对话区不再渲染空白框（有消息才显示）。

缺陷修复（2026-11-16，真实链路报错”ZCode 模型必须填写 Provider/Model 标识”）：前端原写死 model='GLM-5.3-Flash' 缺 Provider 前缀，ZCode CLI 校验（AiCliType.selection）拦截。改为 model=null 留空走接入默认模型（先行通接入默认即完整格式 GLM-5.3-Flash），格式由接入配置保证。`npm run build` 通过。

缺陷修复②（2026-11-16，真实链路报错 Reasoning effort “HIGH” is not supported by GLM-5.3-Flash）：写死的 HIGH 档该模型不支持。去掉 reasoningLevelOverride，模型与档位均回落接入默认（先行通-GLM 默认档位 max，即最高档，语义仍为高档）；按钮旁文案保持”GLM-5.3-Flash · 高”。`AgentInstructionsDraftServiceTest` 7/7。

缺陷修复③（2026-11-16，真实链路报错”请求未完成，请刷新状态后重试”）：前端 axios 全局 15 秒超时，起草（ZCode CLI + 最高档）单轮分钟级必超时且无响应体、落到 errorText 兜底文案。经用户确认：全局默认超时调整为 60 秒（`http.ts`），起草请求单独 300 秒。`npm run build` 通过；真实生成初稿待重试验证。

档位口径说明：此前报错是请求覆盖了大写 HIGH；修复后起草不再覆盖档位，直接用 AI 接入配置的默认推理档（页面所显示的”高”，值 high），与接入配置一致。

后续变更④（2026-11-16，用户决定整体撤销）：删除”帮我起草”功能全部代码——后端 `AgentInstructionsDraftService`/`AgentInstructionsDraftRequest/Response`、`AgentController` 端点、`AiUseCaseDefinitions` 场景注册与测试；前端对话框起草按钮/面板/状态、`draftAgentInstructions` 与三个类型。新迁移 `V20261116_220000` 软删除场景行（V20261116_160000/170000 已在本地库应用不可改，210000 为并行账务需求已占用故取 220000；170000 中的岗位字典 agentPosition 属 REQ-4ff2e6e6 保留不动）。全局 60 秒超时（用户确认）保留在 http.ts。回归：Workbench 69、模板一致性 2、Autonomy 16、Inspection 27 全绿；全模块编译与前端构建通过。需求整体下线，历史记录保留。

### T-04：验证与交付记录

- 描述与目标：证据归档；迭代日志与现状.md 更新。
- 验收条件：
  - [ ] 证据写入本文件；迭代日志/现状更新或说明原因。

状态及真实结果：已完成（2026-11-16）。证据写入本文件；迭代日志已加条目；现状.md 新增“职责 AI 起草”小节。生产生效待部署（后端含迁移先发、前端随后；真实模型联调部署后进行）。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01～04 → T-01/T-02；AC-05 → T-03。
- Design 约束承接：无状态与网关复用（§2）→ T-01；发布顺序与回滚（§5）→ T-04 记录。
- 未覆盖项及原因：无。
- 完整规格统一确认：范围=proposal v1 + design v1 + tasks v1；用户于 2026-11-16 确认“前后端一起实施”；实施完成后证据见各任务明细。
