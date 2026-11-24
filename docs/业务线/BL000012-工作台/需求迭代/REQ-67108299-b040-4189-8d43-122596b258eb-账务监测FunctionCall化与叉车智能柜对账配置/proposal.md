# Proposal｜账务监测 FunctionCall 化与叉车智能柜对账配置

- 需求编码：REQ-67108299-b040-4189-8d43-122596b258eb
- 需求名称：账务监测 FunctionCall 化与叉车智能柜对账配置
- 目录：`docs/业务线/BL000012-工作台/需求迭代/REQ-67108299-b040-4189-8d43-122596b258eb-账务监测FunctionCall化与叉车智能柜对账配置/`
- 涉及仓库：workhub-server（本仓库）
- 日期：2026-11-16

## 背景

账务监测现有 10 条业务线配置（`ops_account_monitor_config`，全部 PROD、每日任务关闭，运行记录为空），规则 SQL 硬编码在 `AccountingRuleCatalog`，执行走 `McpAccountingDatabaseQueryClient` 直连 MCP 网关，未纳入 FunctionCall 统一 SQL 访问与执行审计。叉车（BL000008/lease_saps）与智能柜（BL000005/ol_saps）账务链路表结构同构（生产同库 `db-prod`，按 schema 区分），具备用同一套可配置 SQL 规则监测的条件。

## 目标

1. 清空账务监测现有配置数据（保留旧硬编码规则代码不动，供后续其他业务线复用）。
2. 参照 XXL-JOB 监测模式：新规则 SQL 以 `func_call_sql` 模板登记（种子幂等回写、页面可改），代码经 `FunctionCallService` 渲染执行并落执行审计。
3. 新增叉车、智能柜两条 PROD 监测配置，规则集覆盖账单、缴费单、支付订单、账单结算数据、缴费单结算数据、对账单、对账单结算数据七类对象的数据质量校验及相互交叉校验（14 条规则）。
4. 启用每日自动对账（前一天窗口），保留手动区间对账。

## 范围

- `func_call_sql` 新增 `OPS_ACC_*` 模板 29 条（14 规则 × SUMMARY/SAMPLE + 1 表存在性检查）。
- `AccountingRuleCatalog` 新增 `SAPS_BILL_CHAIN` 规则包（叉车/智能柜共用），SQL 来源改为 FunctionCall 模板渲染。
- `AccountingReconcileExecutor` 执行通道统一改走 `FunctionCallService`（审计 + targetHash 证据）。
- `AccountingReconcileJob` 去掉属性门控，调度交由 `sys_job_config`（默认每日 06:30）。
- Flyway 迁移：清空旧配置并写入叉车、智能柜 2 条新配置（enabled=1、daily_enabled=1）。

不在范围：旧规则代码与其他业务线行为的修改或删除；workhub-web 前端改造（现有页面无需变更即可使用）。

## 验收标准

1. 迁移执行后 `ops_account_monitor_config` 仅剩叉车、智能柜 2 条 `SAPS_BILL_CHAIN` 配置。
2. `/api/ops/accounting/rules?ruleProfile=SAPS_BILL_CHAIN` 返回 14 条规则；手动对账（前一日窗口）产出 14 条规则结果，SUMMARY/SAMPLE SQL 经 `func_call_sql_audit_log` 审计。
3. 29 条模板在 lease_saps、ol_saps 两个 schema 上渲染执行无 SQL 错误（readonly 网关验证）。
4. `sys_job_config` 种子出 `OPS_ACCOUNTING_RECONCILE`，按库内调度值生效。
5. 相关单元测试通过（模板等价性、规则包、执行器、服务层）。

## 已确认决策

| 决策 | 结论 | 确认人/日期 |
| --- | --- | --- |
| 旧规则与配置处理 | 仅清数据，保留旧硬编码规则代码 | 用户／2026-11-16 |
| 运行方式 | 每日自动对账 + 手动触发 | 用户／2026-11-16 |
| 清数据落地方式 | Flyway 迁移脚本 | 用户／2026-11-16 |
