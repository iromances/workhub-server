# Design｜账务监测场景化优化

需求：REQ-d227e09d-e81b-4601-b5e9-940c06d5ae6e。规格版本：v1（2026-11-18）。

## 1. 总体方案

删除 REQ-67108299 的扫表对账执行链，账务监测从「按系统配置 + 规则包扫表一致性校验」重构为「按业务场景配置 + 预期/实际计数核对」：

```
AccountingScenarioCheckJob(默认关闭) / Controller(手动)
  → AccountingMonitorService.submit(…)
  → AccountingScenarioCheckExecutor.execute(runId)
      1. 按配置的 func_call_sql 编码渲染计数 SQL（:checkDate/:startDateTime/:endExclusiveDateTime）
      2. FunctionCallService.executeStatements 执行（逐条落 func_call_sql_audit_log）
      3. 三段式判定（按时执行 / 应发起 vs 实际 / 实际 vs 成功）→ 写 ops_account_reconcile_result
      4. alert_enabled 且 WARN/ERROR 时经 WeCom 机器人推送
```

监测对象从「账单/对账单/交易数据质量」变为「业务场景流程预期达成」：每个场景配置声明预期发生时间、核对时间、预期笔数来源（SQL 或固定值）、实际与成功笔数 SQL 编码、容差；执行时只做轻量计数比对，不再扫表。

## 2. 场景模型

### 2.1 场景类型（AccountingScenarioCatalog）

| 类型编码 | 名称 | 说明 |
| --- | --- | --- |
| BATCH_WITHHOLD | 批量代扣 | 每日定时批量发起账单扣款 |
| COMPENSATION | 代偿 | 代偿扣款/入账流程 |
| BUYBACK | 回购 | 回购流程 |
| PAYMENT | 支付 | 支付流程 |
| CLEARING_SETTLE | 清分结算 | 清分结算流程 |

### 2.2 监测维度（五类场景统一三段式）

| 维度后缀 | 名称 | 输入 | 判定 |
| --- | --- | --- | --- |
| STARTED_ON_TIME | 按时执行 | 实际笔数 | 核对时间已过且实际=0 → ERROR（流程未发起）；>0 → PASS |
| EXPECTED_VS_ACTUAL | 应发起 vs 实际 | 预期（SQL/固定值）+ 实际 | \|预期-实际\| > 容差×预期 → ERROR；否则 PASS |
| SUCCESS_RATE | 实际 vs 成功 | 实际 + 成功 | 未配置成功 SQL → SKIPPED；实际=0 → SKIPPED；差笔数 > 容差×实际 → WARN；成功=0 且实际>0 → ERROR；否则 PASS |

结果行 `rule_code = 场景类型_维度后缀`（如 `BATCH_WITHHOLD_EXPECTED_VS_ACTUAL`）、`category = 场景类型`、`granularity = SCENARIO`、`anomaly_count = 差异笔数`、`difference_amount = 0`、`message` 含「应发起 N、实际 M、成功 K、容差与实际差异率」。代扣场景即用户示例：每天什么时候扣（expected_time/check_time）、应发起多少笔（expected）、实际扣多少（actual/success）。

## 3. 数据模型（复用 ops_account_monitor_config）

**不新建配置表**：`ops_account_monitor_config` 经 ALTER 转为场景配置——旧列 `database_target_key/schema_name/rule_profile/daily_enabled` 删除，新增：

- `scenario_type VARCHAR(32) NOT NULL`
- `expected_time TIME NULL`（场景预期发生时间，展示与核对参考）
- `check_time TIME NOT NULL`（每日核对时间）
- `expected_count_sql_code VARCHAR(64) NULL` / `expected_fixed_count INT NULL`（二选一）
- `actual_count_sql_code VARCHAR(64) NOT NULL`
- `success_count_sql_code VARCHAR(64) NULL`
- `tolerance_rate DECIMAL(6,4) NOT NULL DEFAULT 0`（0–1 小数）
- `alert_enabled TINYINT(1) NOT NULL DEFAULT 0`

唯一键扩展为（business_line_code, environment_code, system_name, scenario_type）。

复用理由：`ops_account_reconcile_run.monitor_config_id` 及 `AccountingRunMapper`/`AgentMonitorReadMapper` 的既有 JOIN 语义不变；若新表自增 ID 与历史 run 的悬空 config_id 撞号，会把旧运行误挂到新配置上。运行/结果表 `ops_account_reconcile_run/result` 结构不动，历史数据保留；`ops_account_execution_evidence` 保留只读（新运行不再写凭据，执行审计由 `func_call_sql_audit_log` 承担，`describe` 对新运行返回 UNRECORDED）。

## 4. 代码改动

### 删除
- `AccountingReconcileExecutor`、`AccountingRuleCatalog`、`AccountingRuleDefinition`、`AccountingRuleSql`、`AccountingReconcileJob`。
- `AccountingExecutionEvidenceService.capture()/PreparedRule`（read/describe/hash/RECORD_ZONE 保留，Agent 巡检与系统预警在用）。
- `FuncCallSqlSeeds` 中 29 条 `OPS_ACC_*` 种子（生成脚本 `outputs/gen_func_call_seeds.py` 已不在仓库，直接编辑并同步等价性测试）。
- 模型 `AccountingRuleResponse`；`AccountingManualRunRequest` 的窗口/规则字段。

### 新增/修改
- `WorkhubJob.defaultEnabled()`（默认 true）：`DynamicJobScheduler.ensureSeeded` 种子时按其落 `enabled`。
- `AccountingScenarioCatalog`（静态）：场景类型/维度定义、ruleCode 拼装。
- `AccountingScenarioCheckExecutor`：渲染（`FunctionCallService.render`，参数 `:checkDate`/`:startDateTime`/`:endExclusiveDateTime`）→ `executeStatements`（单行 `cnt` 计数）→ 三段判定 → 结果落库 → 汇总 finishRun → WeCom 告警（`WecomRobotNotifier` 新增公开 markdown 方法，webhook 未配置时自动跳过）。
- `AccountingMonitorService` 重写：场景配置 CRUD（启用前校验 SQL 编码已登记且启用，经 `FunctionCallService.isCodeEnabled` 新方法）、dashboard、`submitManual(configId, checkDate)`、`retry`、`submitDueScheduled(now)`（enabled=1 且当日 check_time 已到、幂等键 `SCENARIO:{configId}:{date}` 防重）。
- `AccountingMonitorController`：`/scenarios` 场景清单替代 `/rules`；`/runs/manual` 请求体改为 `configId + checkDate + requestKey`。
- `AccountingScenarioCheckJob`：code `OPS_ACCOUNTING_SCENARIO_CHECK`，CRON `0 */10 * * * *`（扫描到期配置），`defaultEnabled()` 返回 false。
- 前端 `api/accounting.ts` + `AccountingMonitorView.vue`：配置表单按场景字段重构，运行详情按监测维度展示。

## 5. 数据迁移

`V20261118_210000__accounting_scenario_monitor_refactor.sql`：

1. `DELETE FROM ops_account_rule_config`、`DELETE FROM ops_account_monitor_config`（运行/结果/凭据历史保留）。
2. ALTER `ops_account_monitor_config`：删 4 旧列、加场景列、换唯一键。
3. `UPDATE func_call_sql SET enabled=0 WHERE LEFT(sql_code,8)='OPS_ACC_'`（模板保留可查审计）。
4. `DELETE FROM sys_job_config WHERE job_code='OPS_ACCOUNTING_RECONCILE'`（执行历史保留）。
5. 登记代扣场景占位模板（enabled=0，口径待业务确认后页面启用）：
   - `OPS_SCN_WITHHOLD_EXPECTED` / `OPS_SCN_WITHHOLD_ACTUAL` / `OPS_SCN_WITHHOLD_SUCCESS`，场景编码 `OPS-SCN-WITHHOLD`，sourceModule `OPS_ACCOUNTING`，INSERT 采用 NOT EXISTS 幂等。

## 6. 验证与回滚

- 单测：场景核对执行器（计数/容差/未发起/成功率为零/SQL 失败）、服务层配置校验与幂等、调度种子默认关闭、Controller 契约；迁移在 H2（MODE=MySQL）建表回归。
- 实库：迁移后核对配置表结构、OPS_ACC_* 全部停用、旧 Job 行删除；登记真实口径 SQL 后手动触发代扣核对走通 run/result/审计/告警。
- 回滚：代码回退 + 迁移前备份 `ops_account_monitor_config`（迁移会清空并改结构，旧规则执行链已删除，回滚需连代码一起回退到本需求前版本）。

## 7. 补充设计 v2：单据及支付交叉核验（2026-11-19，已确认）

### 7.1 本轮核实的事实

- 工作区 `/Users/aslight/IDEAWorkspace/workhub-server`，当前分支 `codex/development-20261102`，HEAD `5ff1954b4b71592183418bc2afa0e4a0ce93d89e`；存在其他在途修改，本轮读取的是工作区实际文件，不宣称等同已部署版本。
- `AccountingScenarioCatalog` 当前仅提供五种流程场景；`AccountingScenarioCheckExecutor.count` 仅接受单行非负整数，且 `startedOnTimeResult` 将实际数为零判为 ERROR。因此异常数 SQL 不能直接填入实际发起计数槽位。
- `AccountingResultEntity` 已有 `ruleCode/anomalyCount/differenceAmount/sampleJson`，可复用结果表达能力。
- `AccountingMonitorMapper` 配置唯一性包含 scenario_type；v2 应使用独立一致性场景，不覆盖原有流程配置。
- MCP 当前配置将两业务线生产资源指向 `db-prod / readonly`。尚未确认执行环境，故未查询业务数据。旧需求中的 lease_saps / ol_saps、状态枚举和表映射仅作核实线索。

### 7.2 执行流程与平台适配

1. 环境确认后，从业务线配置定位仓库、远程分支与提交；核对生成入口、实体枚举、Mapper、金额单位、成功/失败/关闭/冲正/退款条件。
2. 经 MCP 最小化查询 information_schema，核实所需表、列、索引；再对明确时间范围的小样本验证关联与状态映射。付款单不推定为 payment_slip；客户支付订单到渠道的链路须有业务键证据。
3. 编制规则清单及 SQL 参数契约，按照 Proposal AC-v2-01 至 AC-v2-05 分组。两业务线分别出具覆盖清单，缺表或无可靠规则依据时标明未覆盖。
4. FunctionCall 登记每条汇总/样本 SQL；增加独立一致性核验场景及配置，执行器新增独立判定分支。复用已有运行/结果记录，不恢复已删除的旧扫表执行器。
5. 汇总结果先执行；异常时再按同一批业务键获取有限样本。人工选定窗口的首次核验通过 MCP readonly 执行；平台部署后按同一窗口手动运行并核对运行详情及 FunctionCall 审计。两种验证状态分别记录，不将 MCP 执行等同页面执行成功。

平台配置拟增加一致性汇总及样本 SQL 编码，流程计数配置契约保持兼容；一致性模式只校验自身所需编码，不要求伪造实际/成功计数。细化接口及迁移前，须完成表结构、业务口径和 v2 规格确认；若核实结果影响上述方案或验收，补充规格后再确认。

### 7.3 SQL 与金额契约

- 参数统一使用受控业务线/库映射、`:startDateTime`、`:endExclusiveDateTime` 及有限样本上限；时间区间左闭右开，按 Asia/Shanghai 明确转换。
- 汇总以规则为粒度，提供 rule_code、checked_count、anomaly_count、difference_amount；样本含规则码、业务键、异常原因、两侧金额和有符号差额，避免输出客户敏感字段。
- 对金额按业务币种/字段精度进行 DECIMAL 计算，默认精确到最小货币单位比较；若业务允许容差，必须有明确规则及记录，不能任意忽略 0.01 元。
- 单据应付、实付、结算、充值、优惠、手续费、退款/冲正分别建等式；不能无依据要求这些金额全部相等。分摊关系按单据与交易关联键聚合，禁止将整个订单额重复记入每张单据。
- 各明细侧先按业务键独立汇总后关联，避免多个一对多 JOIN 导致金额倍增；覆盖缺明细、无主单、重复关联、NULL 与错误状态。
- 每条记录先比较，再统计异常数与绝对差额，禁止只比较总和掩盖差异抵消。不同规则之间的金额不可汇总为实际资金损失。
- 窗口用于选择驱动业务对象，对侧只查询这批业务键的相关历史明细，不用相同创建时间截断对侧。核对新生成及窗口内发生支付/结算变更的旧单，明确样本集合，避免仅查新单遗漏旧单变更。
- 异步处理中状态按代码可证实的时序/超时规则分类；不能凭空设置等待阈值，也不能把所有未完成订单都当作漏单。
- 多次读取可能出现时点差异；记录查询时间，对变化中的对象做有界复核；无法保证一致快照时保留该限制。

### 7.4 状态、安全、验证与回滚

- 有样本且无异常为 PASS；异常为 ERROR/WARN（按规则）；无样本为 SKIPPED 并注明原因；缺表/缺映射或权限导致不可核验为明确未覆盖；SQL 执行失败为 FAILED。不得将后三者显示为正常。
- SQL 先检查访问路径与索引，再按日或主键批次执行；无界明细聚合、全库扫描不执行。所有数据库查询经 MCP/FunctionCall 受控通道，不直连数据库。
- 平台改动只增加必要场景能力及 SQL 模板，迁移幂等且不得清空现有配置、运行或审计；登记不覆盖用户改过的模板。自动调度及外部通知保持关闭。
- 测试覆盖缺单、重复单、明细倍增、跨窗口结算、部分支付、分摊、优惠/充值、冲正退款、差额抵消、无样本与查询失败；适配现有执行器与服务层测试，核对旧流程场景未受影响。
- 回滚停用本次新增场景/模板并回退新增代码；保留本次运行记录与审计。业务数据库无修改，无业务数据回滚。
- 实施交付后更新现状、迭代日志及 Tasks，明确本地测试、真实 SQL 验证、部署与页面端到端各自状态。

### v2 实施定稿与批准记录（2026-11-19）

用户已明确批准接入。以下为实施定稿，替代前文待确认的设计措辞：

- 独立 `ACCOUNTING_INTEGRITY` 场景，静态规则目录按业务线绑定 29 个查询族、125 条规则，来源表不接受自由输入。87 个模板通过 FunctionCall 初始化器只补缺登记，页面维护不覆盖。
- 每族冻结 MIN/MAX(id)，按最多 10,000 个主键顺序分批；旧渠道及缴费结算辅助表同时冻结上限。汇总与单规则样本均经 FunctionCallService 只读执行并审计。
- 手动 checkDate 可空，全量运行起止时间为空；选择日期按驱动记录 create_time 筛选，对侧按关联键查询全历史。单日模式不保证覆盖当日修改的所有旧单，默认全量覆盖冻结边界内旧单。
- BigDecimal 累加绝对差额；缺列、非法计数、查询失败不能判为通过；无来源样本 SKIPPED。结果记录基数、高水位、完成位置及最多 20 条样本。
- 复用配置及结果表；一致性无需流程计数 SQL，旧流程仍必填日期与计数 SQL。新增迁移不清空既有记录。自动调度显式跳过一致性场景，通知关闭。
- 计划编码不一致独立告警；新退款流程不强求 customer_transaction，另核退款单、成功流水及订单；对账单主表与实付精度差按分比较。
- 代码及 MCP 真实只读核验完成，运行环境迁移、SQL 登记、页面触发落库和审计联调待发布后验收。外部支付账本、对账单完整应生成集合及无生产样本的拆单流程不算验证通过。


### 文案调整 v3

使用一次性映射替换前端及后端显示文案，避免连续替换使原账务监测被改为服务监测。新增迁移按既有权限/工具编码更新展示名，保留历史迁移及编码。
## 2026-11-19 v4 设计补充（已确认，与 Proposal v4 对应）

### 现状证据

工作台 BL000012，运行库 PROD；后端本地分支 codex/development-20261102，前端 codex/development-20261103。智能柜 BL000005/PROD，配置 15 为 ACCOUNTING_INTEGRITY。运行 d0af3480-3132-476a-b8c8-6ff15dabdf60（13:33:48）错误沿用旧规则表及调度判断。运行库 87 个一致性模板全部启用，SHA-256 与本地规则目录逐一一致，其中智能柜 48 个模板、67 条规则。补跑前配置 15 无核对批次，智能柜 AI_* 模板无执行审计。原独立核验经 MCP 执行，未生成平台核对批次。此次已从页面提交全量只读批次 AS20261119141449AED5C5（id=3），实际结果另记 Tasks。

### 流程与契约

1. 平台生成当前运维语义和场景能力说明，补充动态配置、规则目录、执行策略和最新结果信息。数据库地址等管理员数据指南保留；旧规则表说明按新模型修订，避免整段覆盖管理员自定义内容。旧业务监测名称显式映射为服务监测；旧账务监测映射为业务监测，稳定路由/权限/内部类别不改名。
2. 巡检区分 OBSERVE（读取现有批次）与 EXECUTE_CONFIGURED（执行明确选定配置）；存量任务默认 OBSERVE。智能柜本次验收可显式使用执行模式。每轮依据实时权限和业务线/环境校验选定 configId，复用 AccountingMonitorService/AccountingScenarioCheckExecutor，不让模型自行生成另一套核验 SQL。日期为空沿用全量分批，指定日期沿用来源创建时间；二者与系统预警增量窗口分别披露。
3. 场景执行为平台受控应用动作，公开只读 SQL MCP 保持只读。对数字员工开放的调用必须绑定可信当前运行身份，校验操作者 ops:accounting:run/manage 与任务 scope，不能相信模型传入的 actor/runId。返回 monitorRunId、status、coverage、poll/read 信息；暂停/失租约后只重读已有批次，不重复提交。
4. 以 agentRunId+configId+核验窗口建立唯一关联和幂等键；同配置同窗口有执行中批次时复用或明确等待，不能并发重跑全量。平台执行器完成后写出规则结果；数字员工只引用已落库结果和稳定证据 ID。未完成/失败保留状态与原因，不把报告文字当作 SQL 已执行凭据。
5. 增量迁移增加运行关联及 SQL 审计关联字段（monitorRunId、ruleFamily、批次边界；兼容历史空值）。FunctionCall 每次执行留存实际 SQL、模板编码/内容摘要、参数、开始结束时间、查询状态和失败原因。查询结果按规则关联审计，不用时间邻近猜测归属；现有历史审计标识“未建立运行关联”。业务监测详情展示规则清单、异常样本及分页审计入口，数字员工成果链接同一批次。
6. 报告保留“异常数/规则数/覆盖基数/差额”的独立口径，各规则金额和数量可能重叠，不聚合为资金损失。告警关闭、手动场景、缺少历史批次均按事实描述。旧错误报告保留，补充新核验纠正说明。

### 验证与交付

覆盖新旧名称映射、内置规则识别、手动模式不误判调度、权限与跨业务线拒绝、重复提交/失租约重读、批次失败与空样本、SQL 审计精确关联、模板修改后历史快照。前端进行类型检查/构建与真实页面验收；智能柜以 3 类历史异常进行当次结果对照。先完成代码与隔离测试，再在运行环境部署验证，重启前核实在途数字员工和场景任务，避免再次中断租约。

回滚：关闭新执行模式并回退应用代码，保留批次、关联及审计数据；不删除历史，不改业务数据。保持全局场景调度及通知关闭。交付后更新系统现状、迭代日志、Tasks 的代码/部署/真实执行状态。

