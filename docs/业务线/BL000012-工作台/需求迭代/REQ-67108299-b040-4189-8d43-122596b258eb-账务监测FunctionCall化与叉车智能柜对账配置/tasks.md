# Tasks｜账务监测 FunctionCall 化与叉车智能柜对账配置

需求：REQ-67108299-b040-4189-8d43-122596b258eb。规格：proposal v1 / design v1（2026-11-16）。

## 确认记录

- 范围与验收标准已于 2026-11-16 经用户确认（三项决策见 proposal），批准实施。

## 任务

- [x] T1 模板：编写 29 条 `OPS_ACC_*` SQL 模板，更新 `outputs/gen_func_call_seeds.py` 并重新生成 `FuncCallSqlSeeds`。
- [x] T2 规则包：`AccountingRuleCatalog` 注入 `FunctionCallService`，新增 `templateRule` 工厂与 `SAPS_BILL_CHAIN` 14 条规则。
- [x] T3 执行器：`AccountingReconcileExecutor` 改走 `FunctionCallService`（表存在性模板、SUMMARY/SAMPLE 分步执行、审计编码、targetHash）；删除 `AccountingDatabaseQueryClient`/`McpAccountingDatabaseQueryClient`。
- [x] T4 调度：`AccountingReconcileJob` 去门控；清理 application.yml `workhub.ops.accounting` 配置段。
- [x] T5 迁移：`V20261116_210000__reset_accounting_monitor_for_forklift_cabinet.sql` 清旧配置并写入叉车/智能柜配置。
- [x] T6 测试：更新 `FunctionCallTemplateEquivalenceTest`（沿用）、`AccountingRuleCatalogTest`（新增 3 用例）、`AccountingReconcileExecutorTest`（改 FunctionCallService 桩 + 新增模板端到端与审计编码用例）、`AccountingExecutionEvidenceServiceTest`（改 FunctionCallService 桩）；全部通过。
- [x] T7 实库验证：29 条渲染 SQL 在 db-prod（lease_saps、ol_saps 各 29 条，共 58 条）readonly 执行通过。
- [ ] T8 端到端（待部署）：部署后手动触发叉车、智能柜各一次前一日对账，核对 14 条规则结果/`func_call_sql_audit_log` 审计/`ops_account_execution_evidence` 凭据；确认 `sys_job_config` 种子 `OPS_ACCOUNTING_RECONCILE` 且次日 06:30 每日对账生效。
- [x] T9 交付：更新 BL000012 迭代日志、docs/notes.md、本文件证据与剩余风险。

## 证据

- T1：`outputs/gen_func_call_seeds.py` 改为追加式生成（既有种子以当前 Java 文件为准原样保留，sql_code 冲突即报错）；`FuncCallSqlSeeds` 现共 44 条种子（15 旧 + 29 新），参数清单按模板实际引用的 `:name` 占位符自动推导。
- T6：`mvn -pl workhub-service -am test` 账务/FunctionCall 相关 5 个测试类 31 个用例全部通过（AccountingReconcileExecutorTest 7、AccountingRuleCatalogTest 6、AccountingExecutionEvidenceServiceTest 5、AccountingMonitorServiceTest 3、FunctionCallTemplateEquivalenceTest 10）。
- T7（2026-11-16，db-prod readonly）：
  - lease_saps：29/29 条执行成功，当日窗口全部 0 异常；表存在性检查返回全部 8 张白名单表。
  - ol_saps：29/29 条执行成功；`CROSS_SLIP_DETAIL_VS_BILL_DETAIL` 发现 3 条真实差异（订单 `CO261115100431000001`、`CO261115101458000001`、`CO261115103514000001`，各 1000.00 元：缴费单结算明细有核销额、账单结算明细为 0，差异合计 3000.00 元）。
  - 首轮验证发现 `statement_pay_detail` 无 `is_reverse` 列（lease/ol 两库一致，已由 information_schema 核实），`OPS_ACC_SETTLE_DETAIL_INVALID_AMOUNT` 模板对账单结算分支已去除冲正条件并复验通过。
- 存量失败（与本需求无关，属工作区在途改动）：`KnowledgeArchiveWorkflowTest.readingLegacyAnalysisReturnsRepositoryPathsWithoutRewritingStoredHistory`、`BusinessLogSearchServiceTest.shouldPreserveActualExecutionAndExposeContentTruncationWithoutSensitiveFilters`；全模块 1179 个用例中仅此 2 个失败。

## 剩余风险

1. `CROSS_SLIP_DETAIL_VS_BILL_DETAIL` 的 3 条差异可能是异步核销时差（规则已按 WARN 级）或账单侧漏核销，待首个每日对账批次与业务侧确认口径。
2. 页面修改 `OPS_ACC_*` 模板时若引入新参数名，渲染会显式报错（FunctionCallService 校验未传参数），需同步调整代码传参；模板停用会使依赖它的规则立即 FAILED 并体现在批次结果中。
3. 旧规则包（COMMON_SAPS 等）代码保留但已无配置指向；若日后为其他业务线重建配置，其执行同样经 FunctionCallService（审计编码 `OPS_ACC_LEGACY_<规则码>_*`），`database_target_key` 仅作存档字段，实际目标按业务线+环境唯一解析。
4. T8 端到端验证依赖部署（Flyway 迁移 + 种子回写 + `sys_job_config` 种子 `OPS_ACCOUNTING_RECONCILE`），部署前每日对账不会生效。
