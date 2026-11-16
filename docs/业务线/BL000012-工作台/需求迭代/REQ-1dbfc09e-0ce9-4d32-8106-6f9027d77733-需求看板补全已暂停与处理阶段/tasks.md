# Tasks：需求看板补全已暂停与处理阶段

## 1. 元信息

- 需求编码：REQ-1dbfc09e-0ce9-4d32-8106-6f9027d77733
- 需求名称与唯一需求目录：需求看板补全已暂停与处理阶段；`docs/业务线/BL000012-工作台/需求迭代/REQ-1dbfc09e-0ce9-4d32-8106-6f9027d77733-需求看板补全已暂停与处理阶段/`
- 涉及仓库与功能域：`workhub-server`（看板聚合、契约、单测）；`workhub-web`（看板页类型、表格列、文案）
- 所属项目或业务线：`BL000012-工作台`
- Proposal 版本：草案 v1
- Design/契约版本：草案 v1
- 目标环境与分支：`workhub-server@codex/development-20261102`；`workhub-web@codex/development-20261103`
- 任务计划状态：已确认（2026-11-12 用户统一确认草案 v1 及三项决策，见 §4），实施中
- 负责人/评审人：用户（sh）确认

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端分组补全 + `pausedCount` 契约字段 + 单测 | 无 | 确认后即可 | §2 归属表、§3 契约；AC-01~05 | 已完成（验证通过） |
| T-02 | 前端类型、表格列、文案及 `--paused` 配色样式 | 无（与 T-01 并行） | 契约字段确定后 | §2 前端边界；AC-02 页面呈现 | 已完成（构建通过） |
| T-03 | ~~202607100003 业务线数据修复~~ | — | — | Design §6 结论 3 | 已取消（用户已自行处理） |
| T-04 | 验证与交付：测试/构建证据、现状.md 与迭代日志.md 更新 | T-01、T-02 | — | AC-06；Design §5 | 已完成 |

执行顺序：T-01、T-02 并行 → T-04；T-03 待确认后独立执行。无共享文件冲突（两仓库各自修改）；`docs/` 更新仅 T-04。

## 3. 任务明细

### T-01：后端看板分组补全与统计口径

- 描述与目标：`DASHBOARD_DEMAND_GROUPS` 按设计归属表补全（+待处理入未启动、+处理中入开发中、新增 paused 分组）；`MutableBusinessLineStats`/`BusinessLineStats` 增加 `pausedCount` 并纳入 `totalCount`；进度报告汇总随分组自动继承，不单独维护。
- 输入：Design §1 现状证据、§2 归属表、§3 契约；`IntakeDemandStatusRules` 状态常量。
- 输出：`IntakeService.java`、`IntakeDashboardResponse.java` 修改；`IntakeServiceTest` 新增/调整用例。
- 前置依赖：规格确认。
- 允许修改范围：上述两主文件与对应测试文件。
- 明确不修改范围：`UNRELEASED_EXCLUDED_STATUSES`、`DEMAND_STATUS_SORT_ORDER`、暂停/恢复流程、列表页查询。

验收条件：

- [ ] `demandCards` 含 5 张卡片，`paused` 卡片 value 等于已暂停样例数。
- [ ] 每行统计 `totalCount` = 五分项之和；`demandRatio` 口径不变。
- [ ] 单测覆盖：已暂停计入 pausedCount、待处理计入未启动、处理中计入开发中、重点需求可含已暂停、进度报告汇总含已暂停。

计划验证方式（尚未执行）：`mvn -pl workhub-service test`（含相关单测类）。

状态及真实结果（2026-11-12）：已完成。`IntakeService.java`（分组表 + 统计分支 + MutableBusinessLineStats）、`IntakeDashboardResponse.java`（`pausedCount` 字段）修改；新增用例 `dashboard_shouldIncludePausedAndOperationsStatusesInStageGroups` 覆盖 5 卡顺序、paused 计数、待处理入未启动、处理中入开发中、重点需求含已暂停、进度报告汇总含已暂停、终态不入阶段合计。`mvn -pl workhub-service -am test -Dtest=IntakeServiceListFilterTest` 13/13 通过，BUILD SUCCESS；BusinessLineStats 全仓唯一构造点已核实。剩余风险：workhub-controller 测试编译失败为工作区既有并行改动（AgentToolControllerTest 引用不存在类、IntakeControllerTest 的 DevelopmentAnalysisResponse 构造器落后于模型新增 retryable 字段），与本任务无关，未修。

### T-02：前端看板类型与呈现

- 描述与目标：`IntakeDashboardBusinessLineStats` 增加 `pausedCount`；统计表新增"已暂停"列（置于"待上线"与"阶段合计"之间）；页头描述与统计范围文案补"已暂停"；如确认新增样式则补 `metric-card--paused`/`stage-tag--paused`。
- 输入：T-01 契约字段；`src/types/work-item.ts`、`src/views/dashboard/DashboardView.vue`。
- 输出：上述两文件修改（遵循 `workhub-web/AGENTS.md`）。
- 前置依赖：无硬依赖（契约已定），可与 T-01 并行。
- 允许修改范围：上述两文件及（如确认）其样式段。
- 明确不修改范围：栅格布局重构、进度报告渲染逻辑、其他页面。

验收条件：

- [ ] 统计表展示已暂停列且数值与后端一致。
- [ ] `npm run build`（以 `workhub-web` 实际构建命令为准）通过。

计划验证方式（尚未执行）：前端构建 + 本地联调看板页目视核对。

状态及真实结果（2026-11-12）：已完成。`src/types/work-item.ts`（`pausedCount`）、`src/views/dashboard/DashboardView.vue`（"已暂停"列、页头与统计说明文案、`metric-card--paused`/`stage-tag--paused` 紫色配色）修改；实施中发现并修正 `dashboardReport.ts` 的 `drawSummaryCards` 按 4 卡硬编码宽度（`(width - gap*3)/4`），改为按 `summary.length` 自适应，否则第 5 张汇总卡溢出报告截图画布。`npm run build` 通过。页面目视联调待部署。

### T-03：无业务线历史需求数据修复（已取消）

- 2026-11-12 确认：用户已自行修复该数据，本任务取消，本需求不涉及数据变更。

### T-04：验证与交付

- 描述与目标：汇总 AC 证据；按 Design §5 更新 `现状.md`（看板口径描述，如适用）与 `迭代日志.md`。
- 输入：T-01/T-02 结果。
- 输出：测试与构建输出、文档更新。
- 前置依赖：T-01、T-02 完成。
- 允许修改范围：`docs/业务线/BL000012-工作台/现状.md`、`迭代日志.md`、`docs/notes.md` 过程记录。
- 明确不修改范围：其他业务线文档。

验收条件：

- [ ] AC-01~06 逐条有证据或说明。
- [ ] 迭代日志含本次实际进展。

计划验证方式（尚未执行）：证据核对清单。

状态及真实结果（2026-11-12）：已完成。AC-01~05 由 IntakeServiceListFilterTest 新增用例覆盖；AC-06 后端 13/13、前端 build 通过。文档：`现状.md` 追加"需求看板阶段分组与统计口径（2026-11-12，实施完成、未发布）"章节；`迭代日志.md` 新增本次记录。未部署，看板页面呈现与进度报告截图待部署后走查。

## 4. 覆盖与评审

- Proposal 成功标准是否均映射至任务：AC-01~05→T-01；AC-02 页面呈现→T-02；AC-06→T-04。
- Design 接口/数据/非功能约束是否均有承接：契约字段→T-01/T-02；无库表/权限/性能变更→T-01 说明。
- 未覆盖项及原因：T-03 为待确认数据项，不在代码验收内。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：T-04。
- 关联 Proposal、Design 与契约的版本链接：同目录 `proposal.md`、`design.md`（草案 v1）。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：确认范围 = Proposal/Design/Tasks 草案 v1 全部范围 + 三项决策（已暂停卡片新增 `--paused` 配色、处理中归入开发中分组、202607100003 数据用户自行处理）；规格版本 = 草案 v1；结论 = 确认实施；确认人 = 用户（sh）；日期 = 2026-11-12。
