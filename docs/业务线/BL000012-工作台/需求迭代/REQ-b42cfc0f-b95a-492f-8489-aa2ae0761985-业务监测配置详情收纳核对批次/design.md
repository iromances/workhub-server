# Design：业务监测配置详情收纳核对批次

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-b42cfc0f-b95a-492f-8489-aa2ae0761985 |
| 需求名称与唯一需求目录 | 业务监测配置详情收纳核对批次；`docs/业务线/BL000012-工作台/需求迭代/REQ-b42cfc0f-b95a-492f-8489-aa2ae0761985-业务监测配置详情收纳核对批次/` |
| 涉及仓库与功能域 | workhub-server（账务监测 runs 查询）；workhub-web（运维-业务监测页面 `src/views/ops/AccountingMonitorView.vue`、`src/api/accounting.ts`） |
| 所属项目或业务线 | WorkHub 平台自身功能，业务线 BL000012-工作台 |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 已实施（2026-11-22；页面交互待用户登录后走查） |
| 当前事实核对环境、分支、时间 | workhub-server `codex/development-20261102`、workhub-web 当前开发分支；2026-11-22 读取工作区源码核对 |

## 1. 现状与证据

- 页面：`AccountingMonitorView.vue`（路由 `ops/accounting`，菜单「业务监测」）。「场景监测配置」表格来自 dashboard `monitors`（`AccountingMonitorStatusResponse`：config + latestRun）；其下为独立「核对批次」区块，数据源 `GET /api/ops/accounting/runs`，仅按业务线/状态过滤。
- 配置行「详情」按钮 `v-if="row.latestRun"` 直接 `openDetail(row.latestRun)` 打开核对批次详情弹窗；无配置详情视图。
- 后端：`AccountingMonitorController.runs` → `AccountingMonitorService.listRuns/countRuns` → `AccountingRunMapper.findRuns/countRuns`，参数仅 `businessLineCode/status`；`findLatestByConfigId` 已有按 `monitor_config_id` 过滤并附带执行凭据业务线/环境守卫（`e.run_id IS NULL OR (e.business_line_code=c.business_line_code AND e.environment_code=c.environment_code)`）。
- 批次表 `ops_account_reconcile_run.monitor_config_id` 即配置外键，无需数据库变更。
- 尚未核实的假设：无；实施以文件现状为准。

## 2. 方案（接口加参 + 前端信息架构收纳）

- 总体方案：后端 runs 查询链路增加可选 `configId` 参数；前端把核对批次从页面级区块收纳进配置详情弹窗（两个 tab），主页只保留配置表。
- 取数口径：`configId` 过滤复用 `findLatestByConfigId` 的凭据守卫条件，保证 tab 内批次集合与仪表盘「最近状态」判定同源（配置被改业务线/环境后，历史批次按守卫口径排除，两侧一致）。

| 模块 | 负责 | 不负责 |
| --- | --- | --- |
| `AccountingRunMapper` | `findRuns/countRuns` 新增 `configId` 条件（含凭据守卫） | 写路径、`findLatestByConfigId` |
| `AccountingMonitorService` / `AccountingMonitorController` | 参数透传与入参声明 | 权限模型（沿用 `ops:accounting:view|manage`） |
| `api/accounting.ts` | `fetchAccountingRuns` 增加 `configId` | 其他接口封装 |
| `AccountingMonitorView.vue` | 配置详情弹窗（配置信息/核对批次两 tab）、移除独立批次区块 | 批次执行/重试后端逻辑、批次详情弹窗内容 |

## 3. 详细设计

### 3.1 后端接口契约

- `GET /api/ops/accounting/runs` 新增可选查询参数 `configId`（Long）；与 `businessLineCode/status` 可组合；分页参数不变。
- 生效 SQL 条件（`findRuns` 与 `countRuns` 同步）：

  ```sql
  AND r.monitor_config_id = #{configId}
  AND (e.run_id IS NULL OR (e.business_line_code=c.business_line_code AND e.environment_code=c.environment_code))
  ```

- 不传 `configId` 时查询与改造前完全一致（`<if>` 条件不拼接）。

### 3.2 前端配置详情弹窗

- 入口：配置表操作列「详情」按钮（去掉 `latestRun` 存在性条件），打开 `el-dialog`（宽 1080px，标题含 displayName）。
- Tab 1「配置信息」：`el-descriptions` 只读两列展示业务线（名+编码）、环境、系统、显示名称、场景类型（`scenarioName`）、预期/核对时间（完整性场景显示“仅手动核验”）、笔数来源（`expectedSourceText` + 实际/成功 SQL 编码）、容差比例、监测/告警开关、备注、创建/更新时间；`ACCOUNTING_INTEGRITY` 场景下复用 `AccountingRuleCatalog`。
- Tab 2「核对批次」：内含状态筛选（原页面级「批次状态」下拉迁入）+「查询」；表格列批次号/来源/核对日/状态/差异数/发起人/创建时间/操作（详情、重试，权限与状态判断沿用）；`el-pagination` 分页（默认每页 10）。
- 生命周期：打开弹窗即按当前配置懒加载批次（`configId` 过滤）；重试或手动核对成功后，若弹窗打开且配置匹配则刷新 tab 列表，并始终刷新 dashboard（`refreshAfterRunChange`）。
- 移除项：主页独立「核对批次」区块、页面级 `runs/runTotal/runLoading/filters.status/filters.page/pageSize`、`changePage/changePageSize/loadRuns`。

### 3.3 兼容性

- `route.query.runId` 深链仍直达批次详情弹窗（watch 逻辑未动）。
- 手动核对抽屉、配置新增/编辑抽屉、批次详情弹窗（含 SQL 证据）不变。

## 4. 验证与回滚

- 验证：H2 Mapper 测试（`AccountingExecutionEvidenceServiceTest` 补 `configId` 过滤与守卫用例）；后端 `mvn -pl workhub-service -am test`；前端 `npm run build`（vue-tsc + vite）；接口自测与页面走查（见 Tasks）。
- 回滚：改动均为加法（可选参数、新弹窗、移除区块），回滚还原本需求涉及文件即可，无数据结构与数据迁移。
