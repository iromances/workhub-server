# Proposal：慢 SQL 汇总下线

> SDD 流程的 Proposal 模板。随完整规格在实施前统一确认。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-be099a24-3914-4f2f-a8ba-be91b795f28b |
| 需求名称 | 慢 SQL 汇总下线 |
| 涉及仓库与功能域 | workhub-server（workhub-dao、workhub-service、workhub-model、workhub-controller 测试）；workhub-web（系统预警页） |
| 所属项目或业务线（适用时） | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-be099a24-3914-4f2f-a8ba-be91b795f28b-慢SQL汇总下线/ |
| 版本/状态 | 草案 v1 |
| 目标系统 | WorkHub 前后端（运维 → 系统预警） |
| 原始需求来源及版本 | 用户："删除慢 SQL 汇总"（2026-11-18，范围已确认为前后端整体移除） |
| 目标环境/分支 | 本地开发环境；workhub-server codex/development-20261102；workhub-web codex/development-20261103 |
| 负责人/评审人 | 用户；AI 协作（实施） |

## 1. Why：背景与问题

- 系统预警页"慢 SQL"页签顶部的"慢 SQL 汇总"表格提供按业务线/环境/服务的慢 SQL 计数聚合，用户确认不再需要该展示。
- 该聚合由 `/ops/system-alerts` dashboard 接口每次调用都执行 `summarizeEvents` 聚合查询（含系统异常页签调用），属于无用开销。
- 前端"最近慢 SQL 事件"明细列表保留，仍可按服务列直观察看慢 SQL 明细。

## 2. What：目标与范围

### 本期目标

移除"慢 SQL 汇总"展示与数据链路（前后端整体，用户已确认）：前端删除汇总区块与相关类型；后端删除 dashboard 响应中的 `summaries` 字段、`summarizeEvents` 聚合查询与 `SystemAlertSubsystemSummaryResponse` 模型。

### 本期交付范围

1. workhub-web：`SystemAlertView.vue` 删除"慢 SQL 汇总"区块；`serviceOptions` 不再合并 summaries；`types/ops.ts` 删除 `SystemAlertSubsystemSummary` 接口与 `SystemAlertDashboard.summaries` 字段。
2. workhub-server：`SystemAlertDashboardResponse` 删除 `summaries`；`SystemAlertService.dashboard` 不再调用 `summarizeEvents`；`SystemAlertMapper.summarizeEvents` 删除；`SystemAlertSubsystemSummaryResponse` 模型删除。
3. 两侧测试同步调整；前后端构建验证。

### 明确不做

1. 不删"慢 SQL"页签本身及"最近慢 SQL 事件"明细、删除/过滤任务等功能。
2. 不动 `ops_system_alert_event` 表结构与既有数据。
3. 不调整 dashboard 接口其余字段（subsystems、events、executionEvidence 等）。

### 约束与依赖

- 依赖代码：`SystemAlertView.vue`、`types/ops.ts`、`SystemAlertController/Service/Mapper`、`SystemAlertDashboardResponse`。
- 兼容性：接口为 WorkHub 前端专用，前后端同批发布，无外部消费方（`summaries` 仅前端该页使用）。
- 服务下拉选项来源收敛：`serviceOptions` 改为仅由 subsystems 与关注业务线 scopes 组装，事件表未建子系统/关注记录的服务将不再出现在下拉（明细列表不受影响）。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 系统预警页"慢 SQL"页签不再展示"慢 SQL 汇总"区块，"最近慢 SQL 事件"及筛选、删除、删除&过滤功能不受影响 | 构建 + 代码核对 | 用户 |
| AC-02 | `/ops/system-alerts` 响应不再含 `summaries` 字段；后端不再执行 `summarizeEvents` 聚合查询 | 单测/Controller 测试调整 + 代码核对 | 用户 |
| AC-03 | 前后端全量编译构建通过，相关既有测试调整后通过 | mvn/npm 构建 | AI 协作 |
