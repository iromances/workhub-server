# Proposal：业务监测配置详情收纳核对批次

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-b42cfc0f-b95a-492f-8489-aa2ae0761985 |
| 需求名称 | 业务监测配置详情收纳核对批次 |
| 涉及仓库与功能域 | workhub-web：运维-业务监测页面（`AccountingMonitorView.vue`、`api/accounting.ts`）；workhub-server：账务监测 runs 查询接口（`AccountingMonitorController` / `AccountingMonitorService` / `AccountingRunMapper`） |
| 所属项目或业务线 | WorkHub 平台自身功能，业务线 BL000012-工作台 |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-b42cfc0f-b95a-492f-8489-aa2ae0761985-业务监测配置详情收纳核对批次/` |
| 版本/状态 | v1 已确认（2026-11-22 用户批准实施计划即规格确认） |
| 目标系统 | WorkHub Web + WorkHub Server |
| 原始需求来源及版本 | 2026-11-22 用户会话：“业务检测的核对批次放到的场景监测配置详情里，用两个tab” |
| 目标环境/分支 | workhub-server `codex/development-20261102`；workhub-web 当前开发分支 |
| 负责人/评审人 | 用户 |

## 1. Why：背景与问题

业务监测页面（`workhub-web/src/views/ops/AccountingMonitorView.vue`）目前「场景监测配置」与「核对批次」是两个平级区块：

1. 核对批次与配置的关联只能靠业务线筛选间接表达，无法按配置维度查看某个场景的历史批次。
2. 配置行「详情」按钮直接打开最近一次批次的详情弹窗，没有配置维度的详情视图。
3. 后端 `GET /api/ops/accounting/runs` 只支持业务线/状态过滤，不支持按配置（`configId`）过滤。

## 2. What：目标与范围

### 本期目标

1. 新增「场景监测配置详情」弹窗，内含两个 tab（用户已确认）：
   - **配置信息**：只读展示该配置（业务线/环境/系统/显示名/场景类型/预期与核对时间/笔数来源/容差/开关/备注/创建与更新时间）；单据及支付一致性场景复用 `AccountingRuleCatalog` 展示规则目录。
   - **核对批次**：按该配置过滤的批次列表，含状态筛选、分页、批次详情、重试。
2. 配置表操作列「详情」改为打开配置详情弹窗（不再要求存在最近批次）。
3. 主页面独立的「核对批次」区块移除（用户已确认），批次状态筛选移入核对批次 tab；业务线筛选保留，继续作用于配置表。

### 本期交付范围

1. workhub-server：`GET /api/ops/accounting/runs` 新增可选 `configId` 参数（Mapper/Service/Controller 透传），权限不变。
2. workhub-web：`fetchAccountingRuns` 增加 `configId` 参数；`AccountingMonitorView.vue` 配置详情双 tab 重构。

### 明确不做

1. 不改核对批次的执行、调度、幂等与重试逻辑；不改 dashboard 汇总口径。
2. 不新增权限点、数据库表与迁移；`configId` 过滤复用既有 `ops_account_reconcile_run.monitor_config_id` 列。
3. 不改手动核对抽屉、配置新增/编辑抽屉、批次详情弹窗内容与 `route.query.runId` 深链行为。
4. 不保留主页面全局核对批次入口（用户已确认移除；跨配置按状态全局筛选能力随之下线）。

### 约束与依赖

- `configId` 过滤的批次集合口径与仪表盘「最近状态」一致（沿用 `findLatestByConfigId` 的执行凭据业务线/环境守卫条件）。
- 前端权限沿用 `ops:accounting:view|manage|run`，与后端 `@PreAuthorize` 一致。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| D1 | `GET /api/ops/accounting/runs?configId=X` 仅返回配置 X 的批次，total 正确；不传 `configId` 行为与改造前一致 | 接口自测 + H2 Mapper 测试 | 用户 |
| D2 | 配置行「详情」打开配置详情弹窗，两个 tab 可切换；配置信息展示完整、准确 | 页面走查 | 用户 |
| D3 | 核对批次 tab 按配置过滤，状态筛选/分页/批次详情/重试可用；重试或手动核对后列表与最近状态刷新 | 页面走查 | 用户 |
| D4 | 主页面不再有独立核对批次区块；批次状态筛选仅在 tab 内；业务线筛选仍作用于配置表 | 页面走查 | 用户 |
| D5 | 后端模块测试通过；前端 `npm run build`（含 vue-tsc）通过 | 构建命令 | 用户 |
