# Design｜账务监测 FunctionCall 化与叉车智能柜对账配置

需求：REQ-67108299-b040-4189-8d43-122596b258eb。规格版本：v1（2026-11-16）。

## 1. 总体方案

沿用 XXL-JOB 监测的 FunctionCall 模式（REQ-9f3c2a84 / REQ-b4d7e6f2）：

- SQL 模板登记 `func_call_sql`（`sql_code` 唯一），占位符统一 `:name` 命名参数，保存与渲染均走 `FunctionCallService` 校验；页面维护优先，种子幂等回写不覆盖页面修改。
- 执行统一经 `FunctionCallService.executeStatements / executeVerified`（MCP 只读网关 + SqlPolicyGuard），每条语句落 `func_call_sql_audit_log`。

账务对账执行链路保持既有批次模型（`ops_account_reconcile_run/result`、幂等键、手动/每日/重试触发、执行凭据快照），仅把「SQL 构造」与「数据库执行」两段替换为 FunctionCall：

```
AccountingReconcileJob(每日) / Controller(手动)
  → AccountingMonitorService.submit(…)
  → AccountingReconcileExecutor.execute(runId)
      1. OPS_ACC_TABLE_EXISTENCE 模板（information_schema）→ 存在表白名单 + VerifiedBatch.targetHash
      2. evidence.capture(run, config, rules, targetHash)（渲染 SQL 一次并固化哈希）
      3. 逐规则：executeStatements(SUMMARY) → 异常>0 再 executeStatements(SAMPLE)
```

## 2. 规则包 SAPS_BILL_CHAIN（叉车/智能柜共用）

两业务线生产同库不同 schema（lease_saps / ol_saps），SQL 以 `:schemaName`（反引号白名单标识符）参数区分。状态枚举口径（取自业务实体，生产列已核实）：

- `customer_bill.settlement_status`、`statement.status`：1=已结清。
- `payment_slip.status`：4=支付成功（无独立结算状态列）。
- `customer_order.status`、`customer_transaction.status`：4=交易成功。

### 2.1 单对象数据质量规则

| 规则编码 | 名称 | 类别 | 粒度/窗口 | 口径 |
| --- | --- | --- | --- | --- |
| BILL_INVALID_AMOUNT | 账单金额字段异常 | 账单 | DAILY／bill_date | total/paid/unpaid 为空或负、red<0 |
| BILL_SETTLED_WITH_UNPAID | 已结清账单仍有未付 | 账单 | DAILY／bill_date | settlement_status=1 且 (\|unpaid\|>0.01 或 settlement_time 空) |
| SLIP_INVALID_AMOUNT | 缴费单金额字段异常 | 缴费单 | DAILY／create_time | total/paid/unpaid 为空或负 |
| SLIP_SETTLED_WITH_UNPAID | 已支付缴费单仍有未付 | 缴费单 | DAILY／create_time | status=4 且 \|unpaid\|>0.01 |
| ORDER_INVALID_AMOUNT | 支付订单金额异常 | 支付订单 | HOURLY／create_time | pay/transaction/settle/recharge/discount 为空或负 |
| STATEMENT_INVALID_AMOUNT | 对账单金额字段异常 | 对账单 | DAILY／bill_date | total/paid/unpaid 为空或负、red<0 |
| STATEMENT_SETTLED_WITH_UNPAID | 已结清对账单仍有未付 | 对账单 | DAILY／bill_date | status=1 且 (\|unpaid\|>0.01 或 settlement_date 空) |
| SETTLE_DETAIL_INVALID_AMOUNT | 结算明细金额异常 | 结算明细 | HOURLY／create_time | 三张 *_pay_detail 金额为空或负（is_reverse=0） |

### 2.2 交叉校验规则（差异>0.01 记异常）

| 规则编码 | 名称 | 粒度/窗口 | 口径（主单侧窗口 + 明细全量聚合） |
| --- | --- | --- | --- |
| CROSS_BILL_PAID_VS_DETAIL | 账单已付与账单结算不一致 | DAILY／bill_date | customer_bill.paid_amount = Σ customer_bill_pay_detail.pay_amount（含 is_reverse 冲正）by bill_no |
| CROSS_SLIP_PAID_VS_DETAIL | 缴费单已付与缴费单结算不一致 | DAILY／create_time | payment_slip.paid_amount = Σ payment_slip_pay_detail.pay_amount（含冲正）by payment_slip_no |
| CROSS_STATEMENT_PAID_VS_DETAIL | 对账单已付与对账单结算不一致 | DAILY／bill_date | statement.paid_amount = Σ statement_pay_detail.trans_amount by statement_no |
| CROSS_ORDER_VS_TRANSACTION | 支付订单与交易流水不一致 | HOURLY／create_time | 成功订单(customer_order.status=4).pay_amount = Σ 成功交易(customer_transaction.status=4).pay_amount by order_no |
| CROSS_ORDER_SETTLE_VS_SLIP_DETAIL | 支付订单核销与缴费单结算不一致 | HOURLY／明细 create_time | customer_order.settle_amount = Σ payment_slip_pay_detail.pay_amount（含冲正）by order_no |
| CROSS_SLIP_DETAIL_VS_BILL_DETAIL | 缴费单结算与账单结算不一致 | HOURLY／缴费单结算明细 create_time | Σ payment_slip_pay_detail.pay_amount = Σ customer_bill_pay_detail.pay_amount（含冲正）by order_no+trans_no |

交叉规则以窗口侧驱动、对侧全量聚合（与既有 LOGISTICS_BILL_PAY_DETAIL 规则同构），避免两侧写入时差造成假差异。

## 3. func_call_sql 模板

- 命名：`OPS_ACC_<RULE>_SUMMARY` / `OPS_ACC_<RULE>_SAMPLE`，场景 `OPS-ACC-<RULE>`，sourceModule `OPS_ACCOUNTING`；另有 `OPS_ACC_TABLE_EXISTENCE`（场景 OPS-ACC-TABLE-EXISTENCE）。共 29 条。
- 统一参数集（模板按需引用，未引用参数仅告警忽略）：`:schemaName`（反引号库标识符）、`:startDateTime`/`:endExclusiveDateTime`（yyyy-MM-dd HH:mm:ss）、`:startDate`/`:endDateExclusive`（日期）、`:sampleLimit`（样本上限，固定 20）、`:tableNames`（仅表存在性模板，白名单单引号列表）。
- SUMMARY 必须返回恰好一行 `anomaly_count, difference_amount`；SAMPLE 返回 `business_key` 及差异明细列，`LIMIT :sampleLimit`。
- 模板由 `outputs/gen_func_call_seeds.py` 生成进 `FuncCallSqlSeeds`（勿手改），等价性测试 `FunctionCallTemplateEquivalenceTest` 同步覆盖。

## 4. 代码改动

- `AccountingRuleCatalog`：构造注入 `FunctionCallService`；新增 `templateRule(...)` 工厂（`SqlBuilder` 内调用 `functionCallService.render(模板码, 统一参数集)`），注册 `SAPS_BILL_CHAIN` = 14 条模板规则；旧规则包原样保留。
- `AccountingReconcileExecutor`：
  - 依赖由 `AccountingDatabaseQueryClient` 改为 `FunctionCallService`；
  - 表存在性检查改走 `OPS_ACC_TABLE_EXISTENCE` 渲染执行，`targetHash` 取自 `executeVerified` 回执（哈希算法与原实现一致）；
  - 规则执行：SUMMARY 先行、异常>0 再 SAMPLE，均经 `executeStatements`（审计编码=模板 sql_code）；旧包规则审计编码用 `OPS_ACC_LEGACY_<RULE_CODE>` 标记；
  - 渲染仍只发生一次（evidence.capture 内 `buildSql`），执行使用同一渲染结果。
- 删除 `AccountingDatabaseQueryClient`、`McpAccountingDatabaseQueryClient`（唯一调用方为执行器）。
- `AccountingReconcileJob`：移除 `@ConditionalOnProperty(workhub.ops.accounting.enabled)` 门控，bean 常驻注册，由 `DynamicJobScheduler` 按 `sys_job_config` 种子化（默认 CRON `0 30 6 * * *`，允许页面停用）；清理 application.yml 中失效的 `workhub.ops.accounting` 配置段。
- `AccountingMonitorService`/Controller/前端契约不变：新 profile 自动进入 `supportedProfiles`，配置校验（启用需数据库目标、每日需启用）沿用。

## 5. 数据迁移

`V20261116_210000__reset_accounting_monitor_for_forklift_cabinet.sql`：

1. 兜底清空 `ops_account_rule_config`、`ops_account_reconcile_result`、`ops_account_reconcile_run`、`ops_account_execution_evidence`、`ops_account_monitor_config`；
2. 写入 2 条配置：BL000008/PROD/lease-saps/lease_saps、BL000005/PROD/ol-saps/ol_saps，`rule_profile=SAPS_BILL_CHAIN`、`database_target_key=db-prod`、`enabled=1`、`daily_enabled=1`。

回滚：迁移前 10 条旧配置可通过旧种子迁移 `V20260723_210000` 的 INSERT 值恢复（本迁移保留该文件不动）；新运行数据为增量，无需回滚。

## 6. 验证

1. 单测：`FunctionCallTemplateEquivalenceTest`（29 条种子）、`AccountingRuleCatalogTest`（SAPS_BILL_CHAIN 渲染）、`AccountingReconcileExecutorTest`（FunctionCallService 桩：表存在性、SUMMARY/SAMPLE、审计编码、targetHash）、`AccountingMonitorServiceTest`。
2. 实库验证：渲染后 29 条 SQL 在 db-prod readonly 上对 lease_saps、ol_saps 各执行一遍（语法/字段/枚举口径）。
3. 端到端：服务启动后手动触发叉车、智能柜各一次前一日窗口对账，核对 14 条规则结果、`func_call_sql_audit_log` 审计、`ops_account_execution_evidence` 快照；核对 `sys_job_config` 种子出 `OPS_ACCOUNTING_RECONCILE`。
