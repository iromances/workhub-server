# Tasks：业务监测配置详情收纳核对批次

## 1. 元信息

- 需求编码：REQ-b42cfc0f-b95a-492f-8489-aa2ae0761985
- 需求名称与唯一需求目录：业务监测配置详情收纳核对批次；`docs/业务线/BL000012-工作台/需求迭代/REQ-b42cfc0f-b95a-492f-8489-aa2ae0761985-业务监测配置详情收纳核对批次/`
- 涉及仓库与功能域：workhub-server（账务监测 runs 查询）；workhub-web（业务监测页面 `AccountingMonitorView.vue`、`api/accounting.ts`）
- 所属项目或业务线：WorkHub 平台自身功能，业务线 BL000012-工作台
- Proposal 版本：v1
- Design/契约版本：v1
- 目标环境与分支：workhub-server `codex/development-20261102`；workhub-web 当前开发分支
- 任务计划状态：v1 已实施（2026-11-22）
- 负责人/评审人：用户

### 规格确认记录

| 日期 | 范围/版本 | 结论 | 确认人 | 方式 |
| --- | --- | --- | --- | --- |
| 2026-11-22 | v1（Proposal + Design + Tasks） | 批准实施 | 用户 | 会话中批准实施计划（含两个交互决策：双 tab 为「配置信息+核对批次」；主页独立核对批次区块移除） |

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端 runs 查询支持 `configId`（Mapper/Service/Controller） | 无 | 与 T-02 并行 | §3.1 / D1 | 已完成 |
| T-02 | 前端 API 封装与配置详情双 tab 重构 | T-01（联调依赖） | — | §3.2 / D2、D3、D4 | 已完成 |
| T-03 | 测试与构建验证（H2 用例 + mvn test + npm build） | T-01、T-02 | — | §4 / D1、D5 | 已完成 |
| T-04 | 接口自测与页面走查 | T-03 | 需用户登录环境 | D1~D4 | 待走查 |
| T-05 | 文档更新（迭代日志、现状） | T-03 | — | 交付要求 | 已完成 |

执行顺序：T-01 → T-02 → T-03 →（T-04 用户走查）→ 收口。

## 3. 任务明细

### T-01：后端 runs 查询支持 configId

- 描述与目标：`AccountingRunMapper.findRuns/countRuns` 新增 `configId` 条件（含凭据守卫）；`AccountingMonitorService.listRuns/countRuns` 与 `AccountingMonitorController.runs` 透传可选参数。
- 允许修改范围：上述三个文件；不涉及写路径与其他查询。
- 验收条件：
  - [x] `configId` 过滤生效且与 `findLatestByConfigId` 守卫口径一致（D1）。
  - [x] 不传 `configId` 行为不变（`<if>` 不拼接）。
- 计划验证方式：H2 Mapper 测试 + 接口自测。
- 状态及真实结果：已完成；`AccountingExecutionEvidenceServiceTest.runsFilterByConfigFollowLatestRunEvidenceGuard` 覆盖按配置过滤、多配置隔离与凭据环境不一致排除三种情形，全部通过。

### T-02：前端配置详情双 tab 重构

- 描述与目标：`fetchAccountingRuns` 增加 `configId`；`AccountingMonitorView.vue` 新增配置详情弹窗（配置信息/核对批次两 tab，懒加载、状态筛选、分页、批次详情与重试），移除主页独立核对批次区块与页面级批次状态筛选。
- 允许修改范围：`api/accounting.ts`、`AccountingMonitorView.vue`。
- 验收条件：
  - [x] 配置行「详情」不再依赖最近批次存在，打开双 tab 弹窗（D2）。
  - [x] 核对批次 tab 按配置过滤、可筛选/分页/详情/重试；重试或手动核对后刷新（D3）。
  - [x] 主页面无独立核对批次区块；业务线筛选仍作用于配置表（D4）。
  - [x] `route.query.runId` 深链与手动核对/配置编辑抽屉不受影响。
- 计划验证方式：vue-tsc 构建核对 + 用户页面走查。
- 状态及真实结果：已完成（代码与构建核对）；页面交互待用户登录后走查（T-04）。

### T-03：测试与构建验证

- 验收条件：
  - [x] 后端 `mvn -pl workhub-service -am test -Dtest=AccountingExecutionEvidenceServiceTest` 通过：Tests run 3, Failures 0, Errors 0（2026-11-22）。
  - [x] 前端 `npm run build`（vue-tsc + vite）通过（2026-11-22）。
- 状态及真实结果：已完成；证据见本节记录，构建输出未另存档。

### T-04：接口自测与页面走查

- 验收条件：
  - [ ] `GET /api/ops/accounting/runs?configId=X` 返回与 total 正确（D1）。
  - [ ] 页面走查 D2~D4。
- 计划验证方式：用户登录环境自测。
- 状态及真实结果：待用户走查；未验证项即上述两条。

### T-05：文档更新

- 验收条件：
  - [x] 迭代日志记录本次变更；`现状.md` 业务监测章节更新页面结构描述。
- 状态及真实结果：已完成（2026-11-22）。

## 4. 剩余风险与未验证项

- 页面交互（双 tab 展示、筛选/分页/重试联动）未经真人走查，见 T-04。
- 主页面全局核对批次入口移除后，跨配置按状态全局筛选能力下线（Proposal 已确认接受）。

## 5. 后续变更记录

### 2026-11-22 v1.1：删除页面说明文案

- 来源：用户会话指示，删除页头「按业务场景核对预期与实际（批量代扣、代偿、回购、支付、清分结算）；计数 SQL 经 Function Call 登记，自动调度默认关闭。」与「场景监测配置」小节「业务库仅保存核对结果；启用前对应 SQL 编码须在 Function Call 登记并启用。」两处说明（原文“业务库只读计数，WorkHub 仅保存核对结果；……”）。
- 改动：仅 `AccountingMonitorView.vue` 模板删除两个 `<p>` 说明行，标题保留。
- 验证：`npm run build`（vue-tsc + vite）通过（2026-11-22）；页面走查并入 T-04。
- 状态：已完成。

### 2026-11-22 v1.2：删除页头栏，操作按钮迁至筛选区

- 来源：用户会话指示「删除业务检测那一栏，并把刷新、新增配置、手动核对三个按钮放到场景监测配置下，和查询按钮放一起」。
- 改动：`AccountingMonitorView.vue` 删除页头整栏（`业务监测` 标题与 header-actions 按钮）；「刷新」（:loading 联动 loadAll）、「新增配置」（canManage）、「手动核对」（canRun，primary）三个按钮迁入「场景监测配置」筛选区「查询」右侧同一 `el-form-item`；清理 `.page-header`/`.header-actions` 失效样式；按钮权限、loading 与点击行为不变。
- 验证：`npm run build`（vue-tsc + vite）通过（2026-11-22）；页面走查并入 T-04。
- 状态：已完成。

### 2026-11-22 v1.3：删除规则目录弹框说明文案

- 来源：用户会话指示「业务监测规则目录弹框：删除规则目录说明覆盖范围；实际执行以核对批次及 SQL 审计为准。」
- 改动：`AccountingRuleCatalog.vue` 弹框删除说明行 `<p>规则目录说明覆盖范围；实际执行以核对批次及 SQL 审计为准。</p>`；规则分组、SQL 编码与规则列表展示不变。
- 验证：`npm run build`（vue-tsc + vite）通过（2026-11-22）；页面走查并入 T-04。
- 状态：已完成。

### 2026-11-22 v1.4：场景监测配置表笔数来源列移除规则目录入口

- 来源：用户会话指示「场景监测配置，笔数来源列，删除查看规则与SQL」。
- 改动：`AccountingMonitorView.vue` 场景监测配置表「笔数来源」列删除完整性场景下的 `AccountingRuleCatalog` 入口（「查看规则与 SQL」按钮）；列内预期/实际/成功 SQL 编码展示不变。配置详情弹窗「配置信息」tab 的规则目录入口保留。
- 验证：`npm run build`（vue-tsc + vite）通过（2026-11-22）；页面走查并入 T-04。
- 状态：已完成。

### 2026-11-22 v1.5：删除场景监测配置上方统计框

- 来源：用户会话指示「场景监测配置上方的几个统计框也删掉」。
- 改动：`AccountingMonitorView.vue` 删除页首 `summary-grid` 统计区（场景配置/执行中/最近正常/最近有异常/执行失败/差异笔数六卡）及失效的 `summary` 计算属性与全部 `.summary-*` 样式（含 1200px 响应式规则）；`dashboard` 数据与配置表渲染不受影响（后端 summary 字段仍返回，仅不再展示）。
- 验证：`npm run build`（vue-tsc + vite）通过（2026-11-22）；页面走查并入 T-04。
- 状态：已完成。

### 2026-11-22 v1.6：删除「场景监测配置」标题

- 来源：用户会话指示「删除场景监测配置文案」。
- 改动：`AccountingMonitorView.vue` 删除配置卡片顶部「场景监测配置」`section-title` 标题块及失效的 `.section-title` 样式；新增 `.page-card > .filter-form { margin-top: 0 }` 使主筛选表单贴顶（配置详情弹窗核对批次 tab 的 `.filter-form` 上边距保持不变）。新增/编辑抽屉与详情弹窗标题中的「场景监测配置」字样为组件自身标题，保留。
- 验证：`npm run build`（vue-tsc + vite）通过（2026-11-22）；页面走查并入 T-04。
- 状态：已完成。
