# Tasks｜账务监测场景化优化

需求：REQ-d227e09d-e81b-4601-b5e9-940c06d5ae6e。规格：proposal v1 / design v1（2026-11-18）。

## 确认记录

- 实施计划（含数值来源走 func_call_sql 计数、旧链路彻底删除、调度双重默认关闭、框架+代扣场景先行四项决策）已于 2026-11-18 经用户批准，批准实施。

## 任务

- [x] T1 调度默认关闭：`WorkhubJob.defaultEnabled()`（默认 true）+ `DynamicJobScheduler.ensureSeeded` 按其种子；新增 `AccountingScenarioCheckJob`（OPS_ACCOUNTING_SCENARIO_CHECK，CRON `0 */10 * * * *`，defaultEnabled=false），删除 `AccountingReconcileJob`。
- [x] T2 删除旧链路：`AccountingReconcileExecutor`/`AccountingRuleCatalog`/`AccountingRuleDefinition`/`AccountingRuleSql`/`AccountingReconcileJob` 及对应测试删除；`AccountingExecutionEvidenceService` 摘除 capture/PreparedRule（read/describe/hash/RECORD_ZONE 保留供 Agent 巡检与系统预警使用）；`FuncCallSqlSeeds` 移除 29 条 `OPS_ACC_*` 种子（生成脚本已不在仓库，直接编辑并更新类注释）。
- [x] T3 迁移 `V20261118_210000__accounting_scenario_monitor_refactor.sql`：清空 `ops_account_rule_config`/`ops_account_monitor_config`；配置表 ALTER 为场景结构（复用原表避免新旧配置 ID 与历史 run 悬空 monitor_config_id 撞号）；停用 `OPS_ACC_*` 模板；删除 `sys_job_config` 中 OPS_ACCOUNTING_RECONCILE 行；登记代扣占位模板 3 条（enabled=0，场景码 OPS-SCN-WITHHOLD）；权限菜单描述对齐。
- [x] T4 场景监测后端：`AccountingScenarioCatalog`（5 场景 × 3 维度）、`AccountingScenarioCheckExecutor`（render→executeStatements 计数→三段判定→run/result 落库→WeCom 告警）、`AccountingMonitorService`/`AccountingMonitorMapper`/模型重写、Controller（/scenarios 替代 /rules，manual 改 configId+checkDate）、`FunctionCallService.isCodeEnabled`、`WecomRobotNotifier.sendMarkdownMessage`。
- [x] T5 测试：`AccountingScenarioCheckExecutorTest`（H2 真实迁移建表 6 用例：容差内全过/未发起/成功为零/预期 SQL 失败/实际取数失败/告警推送）、`AccountingMonitorServiceTest` 重写 9 用例、`DynamicJobSchedulerTest` 新增 defaultEnabled=false 种子用例、`AccountingMonitorControllerTest` 更新（scenarios 清单+缺 checkDate 拒绝）、`AccountingExecutionEvidenceServiceTest` 重写为凭据读取校验+Agent 来源链路。
- [x] T6 前端 workhub-web：`types/accounting.ts`/`api/accounting.ts`/`AccountingMonitorView.vue` 按场景维度重构（场景类型/预期与核对时间/SQL 编码来源二选一/容差/告警开关；运行详情按监测维度展示；手动核对改核对日）。
- [ ] T7 实库验证（待部署）：迁移后核对配置表结构、OPS_ACC_* 全部停用、旧 Job 行删除、新 Job 种子 enabled=0；业务口径 SQL 登记启用后手动触发代扣核对走通 run/result/func_call_sql_audit_log/告警链路。
- [x] T8 交付：BL000012 迭代日志、docs/notes.md、本文件证据与剩余风险。

## 证据

- T5（2026-11-18，本机）：`mvn -pl workhub-service -am test` 账务/调度相关 7 个测试类 64 用例全绿（AccountingScenarioCheckExecutorTest 6、AccountingMonitorServiceTest 9、AccountingExecutionEvidenceServiceTest 2、AgentInspectionServiceTest 27、AgentMonitorReadMapperTest 3、DynamicJobSchedulerTest 7、FunctionCallTemplateEquivalenceTest 10）；`mvn -pl workhub-job,workhub-bootstrap -am test` controller/job/mcp-server 全绿。
- 存量失败（与本需求无关，属工作区在途改动）：`BusinessLogSearchServiceTest.shouldPreserve...`（ELK 范围未配置，REQ-67108299 已记录）、`TeamAliasRecoveryStaticTest.restoreUsesNoRoutineOrImplicitCommit`（缺 `../docs/WorkHub/团队重复人员合并/SDD/rollback.sql` 文件）。
- T6（2026-11-18）：`npm run build`（vue-tsc --noEmit + vite build）通过。
- 执行器三段判定口径见 design §2.2；`INSERT` 占位模板参数集 `:checkDate`/`:startDateTime`/`:endExclusiveDateTime` 与执行器传参一致（未引用参数仅告警忽略）。

## 剩余风险

1. 代扣场景三段 SQL 的真实业务口径（表/状态/统计规则）待业务方提供；占位模板（OPS_SCN_WITHHOLD_EXPECTED/ACTUAL/SUCCESS，enabled=0）在页面修改并启用前，引用它们的配置无法通过启用校验（isCodeEnabled 拦截）。
2. 代偿/回购/支付/清分结算四场景共用三段式框架，口径到位后登记 SQL 编码即可配置，无需改代码。
3. 新运行不再写 `ops_account_execution_evidence`（执行审计由 `func_call_sql_audit_log` 承担），Agent 巡检对新运行的凭据校验显示 UNRECORDED，属预期。
4. 迁移含 MySQL 专用多操作 ALTER（DROP KEY/ADD UNIQUE KEY），H2 测试不直接执行该迁移（相关测试按迁移后结构手工建表）；部署时需在真实 MySQL 上执行并核对。

## 2026-11-19 补充任务 v2（已确认）

- 用户确认 PROD、日期可选、默认全量，随后确认“确认，接入账务监测”。统一确认范围为 Proposal v2 / Design §7。
- 已刷新并核实业务仓库 origin/master，字段、索引、状态和金额来源的证据及提交 SHA 归档。
- 完成 29 组 SQL、125 条规则、87 个 FunctionCall 模板及前后端接入。生产 db-prod/readonly 完成 175 次有效汇总查询；修正前探索保留并排除。

| 任务 | 实际结果 | 状态 |
| --- | --- | --- |
| V2-01 | 环境、目标分支、表/索引/状态/关联键和金额核实 | 完成 |
| V2-02 | 用户统一确认规格，按真实业务口径细化规则 | 完成 |
| V2-03 | SQL 目录及只补缺登记代码交付；运行环境登记待发布 | 代码完成，部署待验 |
| V2-04 | 独立场景、可选日期、分批、异常数差额样本，旧流程兼容 | 完成 |
| V2-05 | 后端编译、34 项服务/接口/模板测试、前端构建 | 通过 |
| V2-06 | 生产只读全量及重点样本；页面触发和运行落库未做 | SQL 完成，平台待验 |
| V2-07 | 报告、逐规则结果、SQL、异常样本及状态更新 | 完成 |

实际验证：

- `mvn -o -pl workhub-service -am -DskipTests compile`：通过。
- `mvn -o -pl workhub-controller -am -Dtest=AccountingIntegrityCheckExecutorTest,AccountingMonitorServiceTest,AccountingScenarioCheckExecutorTest,AccountingMonitorControllerTest,FunctionCallTemplateEquivalenceTest -Dsurefire.failIfNoSpecifiedTests=false test`：34 项通过。首次沙箱执行因 Mockito JVM attach 限制失败，使用现有依赖在沙箱外重跑通过，未安装软件。
- workhub-web `npm run build`：类型检查和构建通过。
- [核验报告](验证/20261119-生产全量核验/核验报告.md)包含实际 SQL、异常样本及覆盖边界；两条拆单规则零样本 SKIPPED。
- 未发布或重启 WorkHub，未在运行库应用迁移；没有把 MCP 查询当作页面执行成功。未验证外部支付机构账本、对账单完整应生成集合及全部主体/币种/定价规则。
- 未修改业务数据或业务系统代码，发现的金额异常及历史明细缺口留待修复。


## 文案调整 v3（2026-11-19）

确认人：用户；来源：当前会话明确指定两处新名称。范围：菜单、标题及相关显示文案；验收：原业务监测显示服务监测，原账务监测显示业务监测，路由和权限编码保持原值。已完成菜单、路由标题、提示及权限/工具名称迁移；前端构建和后端离线编译通过。未新增测试；属于文案调整，无业务逻辑变更。迁移尚未在运行环境应用。


### v3 启动修复（2026-11-19，已恢复）

新增文案迁移误引用已删除的 pm_agent_tool，导致 1146 和 Flyway 失败历史。已移除该语句，补充现行表结构执行与 Flyway 解析两项测试并通过。用户明确授权后备份并仅清理 20261119.170000 的失败行；正常启动成功重跑迁移，101 条校验通过，其余 100 条历史不变。/api/system/ping 返回 HTTP 200 / UP。详见 [修复记录](验证/20261119-启动修复/修复记录.md)。
## 2026-11-19 v4：巡检口径与执行证据贯通（已确认实施）

规格：Proposal v4 / Design v4，沿用本需求目录与编码。用户已在当前会话统一确认“确认，按 v4 实施”。

| 任务 | 验收映射 | 状态与证据 |
| --- | --- | --- |
| V4-01 最新智能柜报告与运行库核对 | AC-v4-01 | 已完成：13:33 运行使用旧规则表及调度推断；配置 15 为手动一致性，87 个模板全启用且内容一致，智能柜 48 模板/67 规则；补跑前无场景运行和 SQL 审计 |
| V4-02 经正式页面补跑智能柜 | AC-v4-04 | 已完成 AS20261119141449AED5C5，runId=3，PARTIAL，三类重点异常复现，详见下方 |
| V4-03 指南及巡检语义对齐 | AC-v4-01 | 已部署：指南动态列出场景及内置规则数，旧目录纠正迁移应用 |
| V4-04 受控场景执行、幂等及结果读取 | AC-v4-02 | 已部署：任务显式选择配置，持租约校验权限/范围；同轮复用、同窗口防重、事务提交后执行 |
| V4-05 运行/规则/SQL 审计关联及页面呈现 | AC-v4-03 | 已部署：批次及规则目录入口、SQL快照/参数/模板哈希/执行状态分页、数字员工关联跳转 |
| V4-06 回归、实际执行对照、纠正说明 | AC-v4-04/05 | 代码测试及正式批次核验通过，报告端到端验收完成；旧失败和两条无样本保留 |

未安装依赖、未修改业务数据、未启用自动调度和通知；诊断阶段尚未证明数字员工自动执行链路；当前部署后的端到端核验见后续记录。旧核验报告“尚未部署”描述的是当时状态，本轮已核实模板在运行库登记生效，应以当前证据更新部署状态。


### v4 统一确认与补跑结果

2026-11-19 用户明确回复“确认，按 v4 实施”，确认 Proposal v4 / Design v4 范围。实施采用平台执行器先执行选定场景、再将真实结果注入 AI 的方式，运行身份来自已持有的租约，不向公共只读 MCP 扩大写入权限；与已确认受控调用契约一致。

正式补跑 id=3，14:14:49—14:22:39，67 规则：PASS 50、WARN 9、ERROR 4、FAILED 2、SKIPPED 2，批次 PARTIAL。3 项历史重点异常均复现：结算 4 笔/150000 元、付款总额 4 笔/4500000 元、付款与渠道状态 29 笔/132644.08 元。交易结算组 2 规则查询中途失败，未认定正常；其余差异和空样本保留。前端展示差异数 4925 为多规则累计命中，不能解释为去重业务笔数或资金损失。


### v4 部署与回归记录

- 迁移 `20261119.180000` 已成功应用；既有 MCP 凭据随启动环境恢复，页面及 MCP 只读查询均正常。
- 后端 115 项回归：0 失败、0 错误、1 项既有跳过；新增等待/中断/事务边界测试后，16 项针对性测试全部通过（包含 5 项新增）。迁移解析测试通过；前端类型检查及构建通过。
- 一次性验收数字员工运行 `c606fa08-fadc-423f-a0e5-3516042d408d` 已从真实页面提交：仅 BL000005/PROD 配置 15、全量、手动一次，不改变原周期任务模式。
- 已建立真实批次 `AS2026111914471798EB84`（id=4），页面关联跳转已验证；SQL 审计能够展开渲染 SQL、参数、模板 SHA-256，并反向显示数字员工运行 ID。等待本轮终态及报告核验后更新最终验收。
- 旧批次 id=3 不具备新增关联字段，不按时间推测或伪造 SQL 关联；旧报告保留以便追溯。


### v4 正式场景终态

批次 4 已完成：67 规则（52 PASS / 9 WARN / 4 ERROR / 2 SKIPPED / 0 FAILED），321 次 SQL 审计全部成功，三类重点异常再次复现；交易结算组两条规则完整跑通。数字员工正在生成报告。新增队列拒绝落库边界使用 REQUIRES_NEW，防止 afterCommit 回调留下 PENDING，16 项针对性回归通过；待当前活跃运行结束后部署该边界补丁。


### v4 最终交付与运行交接

- 数字员工验收运行 `c606fa08-fadc-423f-a0e5-3516042d408d` 最终 COMPLETED，1030 秒、1 项报告产物；报告引用真实批次、67 条规则和 321 次审计，三类重点异常及两条无样本均保留。
- 最后页面回读发现 AI 工单旧分类会让业务核验被展示在服务监测中。已修正：显式场景任务的「业务监测」页签直接呈现正式 BUSINESS_MONITOR_RESULT 规则结果；AI 分析工单单独展示并保留原记录，不将模型分类作为场景执行事实。正式批次指标同时用于页面和下载 HTML，报告原文不改写。通用任务展示保持原方式。
- 前端最终构建通过；报告渲染与正式规则指标测试 14 项全部通过，包括 FAILED/SKIPPED 区分、事件去重、缺失证据不判正常、HTML 导出一致性。
- 队列拒绝使用独立事务落库的边界补丁已编译、测试并启动验证。
- 用户反馈 8080 占用后，确认占用者为本轮临时验证实例 PID 84280；在活跃 Agent/监测批次均为 0 后正常 SIGTERM，验证端口释放。随后 8080 已由用户新进程接管；不再启动临时后端。构建、迁移与验证产物保留。
- 本轮未修改业务数据、未默认开启周期全量扫描或通知；原周期巡检仍为 OBSERVE。历史报告、历史 PARTIAL 批次和旧 SQL 证据不伪造回填。

最终页面回读通过：业务监测显示 67 条正式规则，结论及六项指标为 67/52/9/4/0/2；AI 分析工单独立显示 15 项。展开业务监测页签可查看正式规则及 SQL 入口，截图保存在本轮验证目录。
