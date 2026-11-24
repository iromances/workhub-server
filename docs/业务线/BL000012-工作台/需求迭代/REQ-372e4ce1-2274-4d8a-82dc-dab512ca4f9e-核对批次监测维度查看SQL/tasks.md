# Tasks：核对批次监测维度查看SQL

## 1. 元信息

- 需求名称与编码：核对批次监测维度查看SQL（REQ-372e4ce1-2274-4d8a-82dc-dab512ca4f9e）
- 涉及仓库与功能域：workhub-server（ops 审计查询）、workhub-web（业务监测批次详情）
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0
- Design/契约版本：v1.0
- 研发分支：server/web 均为 `codex/development-20261122`
- 任务计划状态：已确认（用户 2026-11-22 直接指令，随完整规格）
- 负责人：AI（ZCode）
- 评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | server：`sql-evidence` 可选 `family` 过滤（mapper/service/controller） | 无 | 与 T-02 并行 | §3 / AC-03 | 已完成 |
| T-02 | web：维度点击查看 SQL 弹框 + family 映射 | T-01 接口可用 | — | §2 §3 / AC-01、02、04 | 已完成 |
| T-03 | 验证：server 单测、web 构建、页面走查 | T-01、T-02 | — | AC-03、05 | 部分完成（页面走查待部署） |
| T-04 | 交付：迭代日志、现状、tasks 证据 | T-03 | — | §5 | 已完成 |

执行顺序：T-01 → T-02 → T-03 → T-04；无共享文件写冲突（分仓库提交）。

## 3. 任务明细

### T-01：sql-evidence 接口 family 过滤

- 描述与目标：`FuncCallAuditMapper#forMonitor/#countMonitor` 增加可选规则组集合过滤（`<foreach>` IN，空集合不过滤）；`AccountingMonitorService#sqlEvidence` 解析逗号分隔 `family`（去空白、去重、上限 8）；controller 透传可选参数。
- 输入：design §3 契约；既有 mapper/service/controller。
- 输出：三个 Java 文件修改；`FunctionCallAuditTest` 桩同步签名。
- 允许修改范围：上述文件；明确不修改范围：审计写入逻辑、其他接口。
- 验收条件：
  - [x] 条件 1：不传 `family` 时 mapper 收到空集合，行为与现状一致（单测 verify）。
  - [x] 条件 2：传 `family=A,B` 时仅返回 `rule_family` 为 A 或 B 的审计，计数一致（单测）。
- 计划验证方式（尚未执行）：`mvn -pl workhub-service -am test`（相关测试类）。
- 状态及真实结果：已实施。`FuncCallAuditMapper#forMonitor/#countMonitor` 增加集合过滤（动态 `<script>` + `<foreach>`，空集合不过滤）；`AccountingMonitorService#sqlEvidence` 解析逗号分隔 family（去空白、去重、`limit 8`）；controller 透传可选参数。新增 `AccountingMonitorServiceTest#sqlEvidenceFiltersByFamilyAndKeepsUnfilteredBehavior`（不传不过滤、截断口径、批次不存在报错）与 `FunctionCallAuditTest#monitorFamilyFilterScriptParsesAndBinds`（MyBatis 脚本解析、`rule_family IN` 绑定 5 个参数、空集合无 IN 子句）。随 T-03 全部通过。
- 2026-11-22 22:19 线上报错修复：用户重启实测后 `GET /runs/4/sql-evidence` 报 `BadSqlGrammarException`——Java 文本块会剥除每行行尾空白，`SELECT """ + COLUMNS` 拼接成 `SELECTmonitor_run_id...`。`forMonitor`（本次新增）与既有 `findPage`（同文件同类缺陷，21:54 已在 `/api/ops/function-call/audits` 触发）均改为 `SELECT` 独立成行（对齐 `FuncCallSqlMapper` 既有写法）。单测补强：`forMonitor` 与 `findPage` 的 BoundSql 归一化后必须以 `SELECT monitor_run_id AS monitorRunId` 开头，防回归。20 项测试通过。

### T-02：前端维度点击查看 SQL

- 描述与目标：`api/accounting.ts#fetchAccountingSqlEvidence` 增加可选 `family`；`AccountingMonitorView.vue` 监测维度名称改为链接，点击打开弹框按映射请求并展示审计（列与展开内容对齐 `AccountingSqlEvidence`，含空态与错误提示、分页）。
- 输入：design §2 §3；`AccountingSqlEvidence.vue` 交互范式。
- 输出：两个前端文件修改。
- 允许修改范围：上述文件；明确不修改范围：`AccountingSqlEvidence.vue` 平铺列表、其他页面。
- 验收条件：
  - [ ] 条件 1：三类流程维度与一致性维度点击后仅展示关联 SQL（页面走查）。
  - [ ] 条件 2：无关联审计或请求失败呈现空态/错误提示，不崩溃（走查含旧批次）。
- 计划验证方式（尚未执行）：`npm run build` + 本地页面走查。
- 状态及真实结果：已实施。`fetchAccountingSqlEvidence` 增加可选 `family` 参数；`AccountingMonitorView.vue` 监测维度名称改为链接，点击打开 `append-to-body` 弹框：头部显示规则编码与过滤口径，表格列含 SQL 编码/规则组/状态/时间/行数/耗时，展开显示证据号、模板 SHA-256、参数、起止时间、错误与渲染后 SQL，分页 20 条。映射实现：一致性维度经 `fetchAccountingRules(configId)`（组件内按 configId 缓存）精确匹配 `规则组编码_检查项`（规避 `lease_saps_bill` 与 `lease_saps_bill_settlement` 前缀包含）；流程维度按 `ruleCode` 后缀映射用途；无法映射时不放宽过滤、直接空态。页面走查待部署环境（T-03 记录）。

### T-03：验证

- 描述与目标：后端相关模块测试、前端类型检查与构建。
- 验收条件：
  - [x] 条件 1：server 测试通过（命令与输出记入本文件）。
  - [x] 条件 2：web `npm run build` 通过。
- 计划验证方式（尚未执行）：见任务 T-01、T-02。
- 状态及真实结果：已通过（除页面走查）。2026-11-22 `mvn -pl workhub-service -am test -Dtest='AccountingMonitorServiceTest,FunctionCallAuditTest'`：Tests run 20, Failures 0, Errors 0（含新增 2 项）；`mvn -pl workhub-controller -am compile` 通过。workhub-web `npm run build`（vue-tsc + vite）通过，`✓ built in 12.77s`。页面走查（AC-01/02/04 实页）待部署后进行。

### T-04：交付记录

- 验收条件：
  - [x] 条件 1：`迭代日志.md` 记录本变更；`现状.md` 业务监测段落核对并在需要时更新。
  - [x] 条件 2：本文件回填真实结果与剩余风险。
- 状态及真实结果：已完成。`迭代日志.md` 已追加条目；`现状.md` 业务监测段落描述为配置/批次/证据能力清单，本次为批次详情交互增强，未新增页面或数据结构，现状描述仍准确，仅补充 SQL 证据可按维度过滤查看。剩余风险：映射口径中流程维度用途名为执行器常量（`实际发起笔数` 等），若未来重命名需同步前端映射；页面走查未做。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01/02→T-02，AC-03→T-01/T-03，AC-04→T-02，AC-05→T-03。
- Design 接口/数据/非功能约束承接：§3 契约→T-01/T-02；§4 权限与空态→T-01/T-02；§5→T-04。
- 未覆盖项及原因：无。
- 完整规格统一确认：范围=本期交付范围，规格 v1.0，用户 2026-11-22 直接指令确认，AI 2026-11-22 建档。
