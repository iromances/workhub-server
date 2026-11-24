# Proposal：核对批次监测维度查看SQL

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 核对批次监测维度查看SQL（REQ-372e4ce1-2274-4d8a-82dc-dab512ca4f9e） |
| 涉及仓库与功能域 | workhub-server：`AccountingMonitorController` / `AccountingMonitorService` / `FuncCallAuditMapper`；workhub-web：`AccountingMonitorView.vue`、`api/accounting.ts` |
| 所属项目或业务线 | 工作台（BL000012） |
| 版本 | v1.0 |
| 状态 | 已确认（用户 2026-11-22 直接指令"业务监测-核对批次详情，监测维度，点击可查看sql"） |
| 目标系统 | WorkHub Server、WorkHub Web |
| 原始需求来源及版本 | 2026-11-22 用户："业务监测-核对批次详情，监测维度，点击可查看sql" |
| 研发分支 | server/web 均为 `codex/development-20261122` |
| 负责人 | AI（ZCode）实施 |
| 评审人 | 用户（直接指令即规格确认） |

## 1. Why：背景与问题

- 当前问题及证据：核对批次详情弹框（`AccountingMonitorView.vue` "核对批次详情"）中，监测维度表格下方仅有整批"实际 SQL 执行记录"平铺列表（`AccountingSqlEvidence`，按 20 条分页），维度行与它执行过的 SQL 之间没有关联入口；单据及支付一致性批次每个规则组会产生几十至上百条审计，用户无法从某一维度直接定位其 SQL。
- 受影响用户/角色：业务监测使用与运维排查人员（`ops:accounting:view` 及以上）。
- 不解决的后果：核对差异排查时需人工在平铺 SQL 列表中按编码猜规则组，效率低且易错。

## 2. What：目标与范围

### 本期目标

1. 核对批次详情弹框中，点击任一监测维度即可查看该维度对应已执行 SQL（渲染后 SQL、参数、执行状态等审计证据）。

### 本期交付范围

1. workhub-server：`GET /api/ops/accounting/runs/{id}/sql-evidence` 增加可选 `family` 查询参数（逗号分隔的规则组/用途过滤，按 `func_call_sql_audit_log.rule_family` 精确匹配），不传时行为不变。
2. workhub-web：详情弹框"监测维度"列名称改为可点击链接，点击打开弹框展示该维度关联 SQL 审计（含展开的渲染 SQL、参数、模板哈希、状态）。
3. 维度到 SQL 的关联口径：
   - 单据及支付一致性（结果行 `category = 单据支付一致性`）：`rule_code = 规则组编码_检查项`，关联 `rule_family = 规则组编码`（全部检查项共享该规则组的 BOUNDS/SUMMARY/SAMPLE 审计）。
   - 流程场景（三段式维度）：按维度关联执行用途——按时执行→实际发起笔数；应发起 vs 实际→应发起笔数、实际发起笔数；实际 vs 成功→成功笔数、实际发起笔数。

### 明确不做

1. 不改 SQL 审计落库内容与既有平铺"实际 SQL 执行记录"展示。
2. 不新增权限点，沿用 `ops:accounting:view` / `ops:accounting:manage`。
3. 不做 SQL 模板编辑入口跳转（模板维护仍在 Function Call 页面）。

### 约束与依赖

- 依赖的代码和文档：`func_call_sql_audit_log.rule_family`（REQ-b4d7e6f2 审计表，`AccountingScenarioCheckExecutor` 以用途、`AccountingIntegrityCheckExecutor` 以规则组编码写入）。
- 业务/合规约束：SQL 证据只读展示，不提供执行入口。
- 既有系统/接口/数据约束：旧批次可能无关联审计（提示空态，不按时间推测），沿用现状口径。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 批次详情弹框中每个监测维度名称可点击，弹框内仅列出该维度关联的 SQL 审计记录（SQL 编码、状态、时间、行数、耗时），展开可见渲染后 SQL、参数与模板哈希 | 页面走查 | 用户 |
| AC-02 | 单据及支付一致性批次点击某规则组维度，仅显示该规则组的审计；流程场景按 2.3 的用途口径显示对应 1-2 类 SQL | 页面走查 | 用户 |
| AC-03 | `sql-evidence` 不带 `family` 参数时返回与改造前一致（总数、分页、排序） | 接口对比 + 单测 | AI |
| AC-04 | 维度无关联审计（如旧批次、固定应发起值、未配置成功 SQL 跳过）时弹框呈现空态提示，不报错 | 页面走查 | 用户 |
| AC-05 | workhub-server 相关模块测试通过；workhub-web `npm run build` 通过 | 构建输出 | AI |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
