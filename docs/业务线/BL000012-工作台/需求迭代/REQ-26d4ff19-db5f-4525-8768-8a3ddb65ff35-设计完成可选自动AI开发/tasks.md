# Tasks：设计完成可选自动AI开发

## 1. 元信息

- 需求名称与编码：设计完成可选自动AI开发（REQ-26d4ff19-db5f-4525-8768-8a3ddb65ff35）
- 涉及仓库与功能域：workhub-server（需求阶段推进、AI 分支开发、模型与控制器）；workhub-web（需求清单阶段弹窗、AI 分支开发区块、请求类型）
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0
- Design/契约版本：v1.0
- 研发分支：server/web 均为 `codex/development-20261122`
- 任务计划状态：已确认（2026-11-24 经 ZCode 计划审批统一确认）
- 负责人：AI（ZCode）实施
- 评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端请求字段与触发跳过（IntakeStageActionRequest、IntakeService、历史留痕） | 无 | 与 T-02/T-03 无共享文件冲突 | Design §2/§3；AC-02/AC-03 | 实现完成（见实施结果） |
| T-02 | retry 无记录手动启动（IntakeAiDevelopmentService、IntakeController javadoc） | 无 | 与 T-01 并行 | Design §3；AC-04 | 实现完成（见实施结果） |
| T-03 | 后端测试（IntakeAiDevelopmentServiceTest、IntakeServiceTest） | T-01、T-02 | 无 | AC-02/AC-03/AC-04 | 自动验证完成 |
| T-04 | 前端类型与弹窗开关、手动启动入口（types/work-item.ts、IntakeListView.vue） | 无（契约先于联调即可） | 与 T-01~T-03 并行 | Design §2；AC-01/AC-04 | 实现完成，页面走查待部署 |
| T-05 | 平台文档更新（现状.md、迭代日志.md）与全量验证 | T-01~T-04 | 无 | AC-05 | 自动验证完成 |

执行顺序：T-01 → T-02 → T-03 →（T-04 可与后端并行）→ T-05。共享文件说明：IntakeService 仅 T-01 修改；IntakeAiDevelopmentService 仅 T-02 修改；无数据库对象写入。

## 3. 任务明细

### T-01：后端请求字段与触发跳过

- 描述与目标：`IntakeStageActionRequest` 新增 `Boolean aiDevelopmentEnabled`；`IntakeService.advanceStage` 在 `CONFIRM_DESIGN` 且研发需求且 `Boolean.FALSE.equals(...)` 时跳过 `validateBeforeDesignConfirmed`；`schedulePostStageAutomation` 同条件下跳过 `triggerAfterDesignConfirmed`；历史明细追加「AI 自动开发：启用/未启用（人工开发）」。
- 输入：Design §3 契约；现有 `aiClarificationEnabled` 跳过模式（IntakeService.java:2973-2976）。
- 输出：上述三文件代码改动。
- 前置依赖：无。
- 允许修改范围：IntakeStageActionRequest.java、IntakeService.java。
- 明确不修改范围：状态机、其他动作分支、IntakeAiDevelopmentService。

验收条件：

- [ ] 条件 1：传 `aiDevelopmentEnabled=false` 确认设计完成，需求推进开发中，不调预检、不产生执行记录，历史含「AI 自动开发：未启用（人工开发）」。
- [ ] 条件 2：未传或传 true 时行为与现状一致（预检 + 自动触发）；非研发需求/其他动作不受影响。

计划验证方式（尚未执行）：IntakeServiceTest 新增用例 + 既有用例回归。

状态及真实结果：待开始。

### T-02：retry 无记录手动启动

- 描述与目标：`IntakeAiDevelopmentService.retry` 在 `lockByIntakeId` 返回 null 且需求处于开发中时，复用 `triggerAfterDesignConfirmed` 的 upsert+submit 逻辑创建并启动；既有 FAILED/PENDING 校验不变；更新 `IntakeController` retry 端点 javadoc。
- 输入：Design §3 retry 语义扩展。
- 输出：IntakeAiDevelopmentService.java、IntakeController.java。
- 前置依赖：无。
- 允许修改范围：仅上述两文件。
- 明确不修改范围：`triggerAfterDesignConfirmed`、watch 巡检、Agent 任务创建。

验收条件：

- [ ] 条件 1：开发中无记录调 retry → 创建 PENDING 并启动 Agent 任务，返回执行状态。
- [ ] 条件 2：非开发中（如测试中）无记录调 retry → 报"仅开发中的需求可以启动 AI 开发"；并发双击不产生双记录（沿用锁与唯一键）。

计划验证方式（尚未执行）：IntakeAiDevelopmentServiceTest 新增用例。

状态及真实结果：待开始。

### T-03：后端测试

- 描述与目标：`IntakeAiDevelopmentServiceTest` 补「retry 无记录创建并启动」「非开发中拒绝」；`IntakeServiceTest`（domain/intake/service）补「设计完成 false 跳过预检与触发、历史留痕」「未传字段保持现状」；检查 `IntakeControllerTest` 受影响用例。
- 输入：T-01/T-02 产物。
- 输出：测试代码与运行证据。
- 允许修改范围：上述测试文件。
- 明确不修改范围：生产代码（发现缺陷回到 T-01/T-02 修）。

验收条件：

- [ ] 条件 1：新用例全绿且覆盖 AC-02/AC-03/AC-04 后端部分。
- [ ] 条件 2：`mvn -pl workhub-service -am test` 全量通过（含既有用例无回归）。

计划验证方式（尚未执行）：Maven 测试输出。

状态及真实结果：待开始。

### T-04：前端类型与交互

- 描述与目标：`types/work-item.ts` 的 `IntakeStageActionRequest` 加 `aiDevelopmentEnabled?: boolean`；`IntakeListView.vue`：`stageActionForm` 增加字段并在 `resetStageActionForm` 重置为 true；`CONFIRM_DESIGN` 且研发需求时弹窗显示「自动 AI 开发」el-switch（仿「AI 辅助澄清」块）与提示文案；payload 仅 `CONFIRM_DESIGN` 时传值；AI 分支开发区块显示条件扩展（开发中研发需求无记录时显示「未启动」+「启动 AI 开发」按钮，`retryAiDevelopmentAction` 支持无记录场景取 `selectedDetail.id`）。
- 输入：Design §2 前端交互；后端契约。
- 输出：两文件代码改动。
- 允许修改范围：types/work-item.ts、IntakeListView.vue。
- 明确不修改范围：其他页面、API 层函数签名（复用 retryIntakeAiDevelopment）。

验收条件：

- [ ] 条件 1：设计完成弹窗仅研发需求显示开关、默认启用、文案区分两种选择；提交后按选择生效。
- [ ] 条件 2：开发中无记录的详情显示「未启动」区块与启动按钮；点击后区块切换为执行状态展示；非研发需求/非开发中不显示。

计划验证方式（尚未执行）：`npm run build` + 页面走查（联调待部署后）。

状态及真实结果：待开始。

### T-05：文档更新与全量验证

- 描述与目标：更新 `现状.md` 11a 节（设计完成后自动开发改为可选 + 手动启动口径）与 `迭代日志.md`；跑后端 Maven 测试与前端构建。
- 输入：T-01~T-04 产物。
- 输出：文档更新与验证证据。
- 允许修改范围：docs/业务线/BL000012-工作台/ 下两文件、本 tasks.md 状态。

验收条件：

- [ ] 条件 1：AC-01~AC-05 全部可核对。
- [ ] 条件 2：验证输出留档于本节。

计划验证方式（尚未执行）：`mvn -pl workhub-service -am test`、controller 模块测试、`npm run build`。

状态及真实结果：待开始。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-04；AC-02→T-01/T-03；AC-03→T-01/T-03；AC-04→T-02/T-03/T-04；AC-05→T-03/T-05。
- Design 接口/数据/非功能约束承接：契约字段→T-01/T-04；retry 语义→T-02；历史→T-01；权限不变→无需改动。
- 未覆盖项及原因：真实环境页面联调（应用未重启，沿用本仓库"实施未发布"惯例，留待部署后走查）。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：T-05。
- 关联版本：proposal v1.0、design v1.0。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：全量规格（本三件套），v1.0，2026-11-24 经 ZCode 计划审批由用户确认，AI 不得自填"已批准"。

## 6. 实施结果（2026-11-24）

- T-01 已实现：`IntakeStageActionRequest` 新增 `aiDevelopmentEnabled`；`IntakeService.advanceStage` 在设计完成且研发需求且 `Boolean.FALSE` 时跳过 `validateBeforeDesignConfirmed`，`schedulePostStageAutomation` 同条件跳过 `triggerAfterDesignConfirmed`，历史明细追加「AI 自动开发：启用/未启用（人工开发）」（非研发需求不追加）。
- T-02 已实现：`IntakeAiDevelopmentService.retry` 无记录且开发中时经 `triggerAfterDesignConfirmed` 首次启动，`DuplicateKeyException`（intake_id 唯一键）按并发双击容忍并返回当前状态；既有 FAILED/PENDING-无任务重试语义不变；`IntakeController` retry 端点 javadoc 已更新。
- T-04 已实现：前端类型与弹窗开关（仅研发需求显示，默认启用）、payload 仅 CONFIRM_DESIGN 传值；AI 分支开发区块在开发中研发需求无记录时显示「未启动」+「启动 AI 开发」，`retryAiDevelopmentAction` 支持无记录首次启动并区分提示文案。
- 实际验证：`mvn -pl workhub-service -am test -Dtest='IntakeAiDevelopmentServiceTest,IntakeServiceTest'` 48 项全部通过（含新增 4 项：IntakeServiceTest 38、IntakeAiDevelopmentServiceTest 10）；`IntakeControllerTest` 19 项通过；前端 `npm run build`（vue-tsc + vite）通过。workhub-service 全量 1317 项中 2 项失败（AgentWorkbenchServiceTest ELI5、BusinessLogSearchServiceTest ELK），经暂存改动后基线复跑确认为分支既有失败，与本需求无关。
- 未验证：应用未重启，真实环境页面走查（弹窗开关显示、手动启动按钮、历史展示）待部署后执行；未提交 Git。
- 剩余风险：旧前端 + 新后端组合下字段不传，行为等同默认启用（与现状一致，无风险）；新前端 + 旧后端组合下开关不生效（字段被忽略），无报错。
