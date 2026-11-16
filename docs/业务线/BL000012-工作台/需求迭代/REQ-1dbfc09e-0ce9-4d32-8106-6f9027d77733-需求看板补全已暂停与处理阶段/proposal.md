# Proposal：需求看板补全已暂停与处理阶段

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-1dbfc09e-0ce9-4d32-8106-6f9027d77733 |
| 需求名称 | 需求看板补全已暂停与处理阶段 |
| 涉及仓库与功能域 | `workhub-server`（`workhub-service` 需求看板聚合 `IntakeService`、`workhub-model` 响应模型 `IntakeDashboardResponse`、对应单测）；`workhub-web`（看板类型定义 `src/types/work-item.ts`、页面 `src/views/dashboard/DashboardView.vue`） |
| 所属项目或业务线 | `BL000012-工作台`，WorkHub 平台自身需求 |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-1dbfc09e-0ce9-4d32-8106-6f9027d77733-需求看板补全已暂停与处理阶段/` |
| 版本/状态 | 草案 v1，待完整规格统一确认 |
| 目标系统 | WorkHub 后端需求看板接口 `/api/intakes/dashboard` 及前端工作台看板页 |
| 原始需求来源及版本 | 用户 2026-11-12 提出"工作台是不是漏了已暂停的需求"；经代码与生产库核实确认（见 Design 现状与证据） |
| 目标环境/分支 | `workhub-server` 分支 `codex/development-20261102`；`workhub-web` 分支 `codex/development-20261103` |
| 负责人/评审人 | 待确认 |

## 1. Why：背景与问题

- 当前问题及证据：需求看板阶段分组 `DASHBOARD_DEMAND_GROUPS` 仅覆盖 已收录/待澄清/待评估/待排期/待设计、开发中、测试中、待验收/待上线；**已暂停**（及数据运维需求的 待处理/处理中）不在任何分组。生产库当前有 6 条已暂停需求，在看板阶段卡片、业务线统计分项、重点需求时间线中全部不可见；业务线统计出现"需求数 6、阶段合计 0"类对不上账的展示。
- 受影响用户/角色：通过工作台看板页（`/api/intakes/dashboard`）查看需求全局进度的研发与管理人员。
- 不解决的后果：已暂停需求从看板"消失"，全局进度与业务线统计失真；暂停决策的可见性缺失。

## 2. What：目标与范围

### 本期目标

1. 需求看板阶段分组覆盖全部非终态需求状态，已暂停需求在看板可见、可统计。
2. 业务线统计口径与阶段卡片一致，消除"需求数与阶段合计对不上"的失真。

### 本期交付范围

1. 后端：`DASHBOARD_DEMAND_GROUPS` 补全——待处理归入"未启动的需求"、处理中归入"开发中的需求"、新增"已暂停的需求"分组卡片（置于"待上线的需求"之后）。
2. 后端：`BusinessLineStats` 新增 `pausedCount` 字段；`totalCount`（阶段合计）纳入已暂停。
3. 前端：`IntakeDashboardBusinessLineStats` 类型新增 `pausedCount`；业务线统计表新增"已暂停"列；页面文案同步（页头描述、统计范围说明）。
4. 后端单测覆盖新增分组与统计口径。

### 明确不做

1. 进度报告"未上线需求"排除已暂停的既有设计（`UNRELEASED_EXCLUDED_STATUSES`）保持不变。
2. 需求列表页（`/api/intakes` 分页筛选）与暂停/恢复流程不变。
3. 终态（已完成、终止关闭）仍不进阶段卡片与阶段合计。
4. 无业务线需求（审批编号 202607100003）的数据修复不属于本需求代码范围，如需修复另行确认（见 Design 待确认）。

### 约束与依赖

- 依赖的代码和文档：`workhub-service/src/main/java/cn/aslight/workhub/service/intake/IntakeService.java`、`workhub-model/.../IntakeDashboardResponse.java`；前端 `src/types/work-item.ts`、`src/views/dashboard/DashboardView.vue`。
- 业务/合规约束：无。
- 既有系统/接口/数据约束：`demandCards`、进度报告阶段汇总均由 `DASHBOARD_DEMAND_GROUPS` 派生，新增分组自动进入，不新增接口；无数据库变更。
- 外部依赖与前置条件：无。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 看板接口 `demandCards` 含"已暂停的需求"卡片，`value` 等于已暂停需求数（当前生产 6） | 调用 `/api/intakes/dashboard` 核对 + 单测 | 待确认 |
| AC-02 | 业务线统计含 `pausedCount`；每行 `totalCount` = 未启动+开发中+测试中+待上线+已暂停；与 `demandCount` 的差值恰为该业务线已完成+终止关闭数 | 单测 + 页面核对 | 待确认 |
| AC-03 | 待处理计入"未启动"、处理中计入"开发中"，全部 14 个状态均有归属 | 单测断言分组覆盖 | 待确认 |
| AC-04 | 已暂停需求可出现在重点需求时间线（跟随分组遍历与既有 limit 12 规则） | 单测 | 待确认 |
| AC-05 | 进度报告阶段汇总自动含"已暂停"分组（派生自阶段分组，不单独维护） | 单测 + 报告截图核对 | 待确认 |
| AC-06 | `workhub-server` 相关单测通过；`workhub-web` 构建通过 | 本地构建/测试输出 | 待确认 |
