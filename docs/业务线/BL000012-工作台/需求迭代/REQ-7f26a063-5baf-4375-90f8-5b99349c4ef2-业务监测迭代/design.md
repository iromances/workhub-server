# Design：业务监测迭代

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 业务监测迭代（REQ-7f26a063-5baf-4375-90f8-5b99349c4ef2） |
| 涉及仓库与功能域 | workhub-server：规则组持久化与种子、管理与执行接口、执行器、SQL 契约校验、维度 SQL 概览、数字员工指南；workhub-web：批次详情、配置详情、接口封装 |
| 所属项目或业务线（适用时） | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | server `codex/development-20261122` @ `d8962db`、web `codex/development-20261122` @ `8ad353c`；2026-11-27 读取两仓库工作区源码，并以只读查询核对运行库 |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 执行链：`AccountingScenarioCheckExecutor#execute` → 一致性分支 `new AccountingIntegrityCheckExecutor(functionCallService, runMapper).execute(config, run)` → `AccountingIntegrityCatalog.families(config.businessLineCode())`（静态 JSON）→ 逐规则组 `executeFamily`：BOUNDS 1 次（无参数）→ 主键分批 SUMMARY（`startId/endId/startDateTime/endExclusiveDateTime`，可选 `legacyMaxId`）→ 命中的检查项再取 SAMPLE（另带 `ruleCode='<检查项ID>'`、`sampleLimit`）。
  - 规则目录读取：`AccountingMonitorService#rules(configId)` 返回内置目录（code/name/checks/sqlCodes）。消费方两处：`AccountingRuleCatalog.vue`（配置信息 tab 的只读弹框）与 `AccountingMonitorView.vue#familiesFor`（维度到规则组的映射，经规则目录精确匹配）。
  - 模板：`func_call_sql`。`FunctionCallService#monitorStatement` 每次执行直查模板并冻结哈希；`FuncCallSchemaInitializer` 启动后首次使用时对 `AccountingIntegrityCatalog.seeds()` 只补缺。
  - 审计：`func_call_sql_audit_log`（`monitor_run_id/rule_family/parameters_json/template_hash/rendered_sql/status`）；`FuncCallAuditMapper#forMonitor/#countMonitor` 支持按 `monitor_run_id + rule_family IN` 分页。
  - 提交入口：页面手动、重试、调度 Job、数字员工（`AgentMonitorSubmissionService` 复用 `submitManual`），全部汇入 `AccountingMonitorService#submit` → 同一执行器。
- 当前接口、数据及配置：`GET /api/ops/accounting/runs/{id}/sql-evidence`（可选 `family`）、`GET /api/ops/accounting/configs/{id}/rules`、Function Call 的 `PUT /api/ops/function-call/sql/{id}` 与 `POST /api/ops/function-call/execute`。
- 事实来源（路径、提交、环境或只读证据）：运行库 `bl000012-prod-db-10-10-116-32-3306` readonly，2026-11-27：
  - `ops_account_monitor_config` 仅 2 行，均为 `ACCOUNTING_INTEGRITY`：14 叉车 BL000008 / lease-saps，15 智能柜 BL000005 / ol-saps；均启用、告警关闭。
  - `func_call_sql`：`AI_LEA*` 39 条、`AI_OL_*` 48 条，全部启用且 `updated_at = created_at`，从未被页面修改；`OPS_SCN_*` 3 条流程占位模板停用，没有流程场景配置。
  - `ops_account_reconcile_run` 共 4 个批次：3、4 是配置 15 的一致性批次（各 67 行结果，批次 4 有 321 条审计，14:47:17 开始、14:55:06 结束）；配置 14 尚无批次；批次 1、2 的配置已被 REQ-d227e09d 迁移清空。
  - 批次 4 按规则组的审计分布：`ol_saps_order` 为 1 条边界 + 83 条汇总 + 4 条样本（88 条），`ol_saps_channel` 85 条，`ol_saps_transaction_settlement` 84 条。现状弹框按 20 条分页，看一个维度要翻 4–5 页且汇总 SQL 文本相同、只有参数不同，这是 §3.4 改为按 SQL 编码分组展示的依据。
  - 样本审计的 `parameters_json` 实际形态为 `{"startId":"1","endId":"3707","startDateTime":"NULL","endExclusiveDateTime":"NULL","ruleCode":"'BALANCE'","sampleLimit":"20"}`，§3.4 的 SAMPLE 检查项过滤依据此格式。
- 目标系统当前实现证据及本次重新核对结果：对 `integrity-rules.json` 全量脚本核对——29 个规则组编码全局唯一且全为小写（最长 33），125 个检查项 ID 全部以大写字母开头、由 `[A-Z0-9_]` 组成（最长 27），级别只有 WARN/ERROR，`rule_code` 125 个无重复；边界 SQL 均不引用参数，汇总 SQL 均含 `:startId`、`:endId` 与 `checked_count` 且参数不超出执行器传入集合，样本 SQL 均含四个必需参数；按大小写切分 `rule_code` 可唯一还原规则组与检查项（0 例不符）。因此 §3.5 的保存契约对现有 87 个模板全部成立。
- 尚未核实的假设：无。数字员工的业务监测工具（`monitor.accounting.*`）不直接读取规则目录，全仓仅 `AgentPlatformGuide` 引用 `AccountingIntegrityCatalog`。

## 2. 系统架构

- 总体方案和设计目标：把「配置下有哪些监测场景（规则组及其检查项）」从代码静态目录改为按配置持久化的数据；代码目录降级为种子，只补缺、库内优先，与 `func_call_sql` 模板种子同一模式。SQL 继续统一登记在 `func_call_sql`，渲染、执行、审计链路不变。执行器在批次开始时读取一次配置的生效规则，批次内不再读取。
- 主要流程、数据流和状态流：
  1. 种子：首次访问时按内置目录为每个一致性配置补缺物化规则组与检查项（§3.2）。
  2. 管理：配置详情「监测场景」tab → 规则组与检查项管理接口写新表；SQL 编辑写 `func_call_sql`。
  3. 执行：提交核对 → 校验存在生效规则 → 批次开始时加载「启用且未删除的规则组 × 启用的检查项」→ 逐规则组按 `AI_<编码大写>_<后缀>` 取模板渲染执行 → 结果与审计落库。
  4. 查看：批次详情「实际 SQL」列 → 维度 SQL 概览接口按 `sql_code` 聚合审计 → 分组弹框。
- 前端页面、交互与后端规则边界：编码格式、级别枚举、批大小范围、SQL 契约、模板编码占用等校验全部在后端；前端只做输入收集、必填提示、按权限显示入口与展示格式化。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-server 规则组管理与种子 | 规则组/检查项存取、启停、自定义规则组与模板登记（同事务）、内置目录补缺物化 | SQL 渲染执行、调度 | 管理请求 → 新表与 `func_call_sql` | 两张新表、`FunctionCallSqlService` |
| workhub-server 执行 | 提交前校验、按配置生效规则执行 | 规则组管理界面 | 配置 + 批次 → 结果与审计 | 规则组存取、`FunctionCallService` |
| workhub-server SQL 契约与模板编辑 | 一致性模板保存时的契约校验、三段模板原子保存 | 试运行执行 | 模板文本 → 通过或报错 | `func_call_sql` |
| workhub-server 维度 SQL 概览 | 按 SQL 编码聚合审计 | 审计写入 | 批次 + 规则组（+ 检查项）→ 分组 SQL | `func_call_sql_audit_log` |
| workhub-web 批次详情 | 「实际 SQL」列、分组 SQL 弹框、逐条记录 | 审计写入 | 维度行 → 实际 SQL | 概览接口、`sql-evidence` |
| workhub-web 配置详情 | 「监测场景」tab：规则组、检查项、SQL 编辑、试运行 | 业务校验、执行 | 用户操作 → 管理接口 | 规则组管理接口、Function Call 接口 |

## 3. 契约与数据模型

### 3.1 数据模型（迁移 `V20261127_120000__accounting_rule_management.sql`）

只建表和替换一处数据指南文本，不插入规则数据（规则由 §3.2 的种子物化，理由见该节）。

`ops_account_monitor_family`（配置下的规则组）

| 列 | 类型 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键自增 |
| config_id | BIGINT NOT NULL | 所属配置 `ops_account_monitor_config.id` |
| family_code | VARCHAR(64) NOT NULL | `^[a-z][a-z0-9_]{1,49}$`；派生 SQL 编码 `AI_<大写>_SUMMARY` 不超过 64 位 |
| family_name | VARCHAR(128) NOT NULL | — |
| description | VARCHAR(512) NULL | — |
| batch_size | INT NOT NULL | 1–20000（执行器既有边界） |
| source_type | VARCHAR(16) NOT NULL | `BUILTIN` / `CUSTOM` |
| enabled | TINYINT(1) NOT NULL DEFAULT 1 | — |
| deleted | TINYINT(1) NOT NULL DEFAULT 0 | 仅自定义规则组会置 1 |
| sort_order | INT NOT NULL DEFAULT 0 | — |
| created_by / updated_by | VARCHAR(64) NOT NULL | 种子为 `system`，其余为登录名 |
| created_at / updated_at | DATETIME NOT NULL | — |

唯一键 `(config_id, family_code)`，普通索引 `(config_id, enabled)`。

`ops_account_monitor_check`（规则组下的检查项）

| 列 | 类型 | 约束 |
| --- | --- | --- |
| id | BIGINT | 主键自增 |
| family_id | BIGINT NOT NULL | 所属规则组 |
| check_id | VARCHAR(64) NOT NULL | `^[A-Z][A-Z0-9_]{0,39}$`；对应汇总 SQL 的 `<check_id>_n`、`<check_id>_diff` 列 |
| check_name | VARCHAR(128) NOT NULL | — |
| severity | VARCHAR(16) NOT NULL | `WARN` / `ERROR` |
| note | VARCHAR(512) NULL | — |
| enabled | TINYINT(1) NOT NULL DEFAULT 1 | — |
| sort_order | INT NOT NULL DEFAULT 0 | — |
| created_by / updated_by、created_at / updated_at | 同上 | — |

唯一键 `(family_id, check_id)`。

规则组的 SQL 模板仍在 `func_call_sql`：编码 `AI_<规则组编码大写>_BOUNDS|SUMMARY|SAMPLE`，`scenario_code = ACC-<规则组编码>`，`branch_order` 1/2/3，`source_module = OPS_ACCOUNTING_INTEGRITY`，与内置种子一致。

### 3.2 内置目录种子（只补缺）

新增 `AccountingRuleInitializer`，进程内首次访问时执行一次（与 `FuncCallSchemaInitializer` 同模式）：

- 对每个 `scenario_type = ACCOUNTING_INTEGRITY` 的配置，按内置目录顺序处理该业务线的规则组：
  - 配置下没有该 `family_code` → 插入规则组（`BUILTIN`、启用、`sort_order` 为目录序号、`created_by = system`）及其全部检查项（启用）。
  - 已存在且 `source_type = BUILTIN` → 只补入目录中有而配置下没有的检查项；已有的规则组与检查项（含停用状态、级别）一律不动。
- 调用点：`AccountingMonitorService` 的 `rules`、`sqlTemplates`、`submit`（校验前），以及一致性执行器加载规则前；`createConfig` 在同一事务内直接为新建配置物化。
- 并发：方法同步；插入撞唯一键视为已存在。
- 不用 Flyway 物化的原因：代码目录是唯一事实来源，迁移脚本重复写 125 条检查项会形成双写；沿用模板种子「迁移不插数据、初始化器回写」的既有先例（`V20261114_110000` 注释）。

### 3.3 执行口径

- 生效规则：`enabled = 1 AND deleted = 0` 的规则组（按 `sort_order, id`），各取 `enabled = 1` 的检查项（按 `sort_order, id`）；检查项全部停用的规则组剔除。构造为现有 `AccountingIntegrityCatalog.Family` 记录（执行器只用 code/name/batchSize/checks 与 `sqlCode(suffix)`），`executeFamily` 逻辑不变。
- `AccountingIntegrityCheckExecutor#execute` 改为接收调用方传入的生效规则组；为空时批次 FAILED，原因写「该配置未启用任何监测场景规则」。
- 提交校验：`AccountingMonitorService#submit` 对一致性配置校验生效规则非空，否则抛「该配置未启用任何监测场景规则」。手动、重试、数字员工三条入口都经过这里；调度本来跳过一致性场景，不变。
- 检查项级别：结果行 `severity` 与异常时的 `status` 取配置中的级别，不再取代码目录。
- 规则组、检查项的启停与级别在批次开始时读取一次；SQL 模板仍是每次渲染直查。修改后下一次核对生效，执行中的批次不回读。历史批次的审计按条冻结 `template_hash` 与 `rendered_sql`，详情展示不随模板修改变化。
- 重试历史批次时按提交时刻的生效规则执行，结果行集合可能与原批次不同。

### 3.4 API

权限均沿用既有权限点，不新增。路径前缀 `/api/ops/accounting`。

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /configs/{id}/rules`（改造） | `ops:accounting:view` 或 `manage` | 返回配置下全部规则组，含停用与已删除 |
| `GET /configs/{id}/sql-templates?family=`（新） | 同上 | 返回模板全文。一致性配置传 `family` 取该规则组三段；流程配置不传，取应发起（SQL 来源时）、实际、成功笔数模板 |
| `PUT /configs/{id}/rules/{familyCode}/sql`（新） | `ops:function-call:manage` | 原子保存一致性规则组的三段模板 |
| `PUT /configs/{id}/rules/{familyCode}/enabled`（新） | `ops:accounting:manage` | 启用或停用规则组 |
| `PUT /configs/{id}/rules/{familyCode}/checks`（新） | `ops:accounting:manage` | 批量保存检查项的启停与级别 |
| `POST /configs/{id}/rules`（新） | `ops:accounting:manage` 且 `ops:function-call:manage` | 新增自定义规则组并登记三段模板 |
| `PUT /configs/{id}/rules/{familyCode}`（新） | `ops:accounting:manage` | 编辑自定义规则组（仅 `CUSTOM`） |
| `DELETE /configs/{id}/rules/{familyCode}`（新） | `ops:accounting:manage` | 逻辑删除自定义规则组（仅 `CUSTOM`） |
| `GET /runs/{id}/dimension-sql?family=&check=`（新） | `ops:accounting:view` 或 `manage` | 维度实际 SQL 概览 |

字段与校验：

- `GET /configs/{id}/rules`：每个规则组 `{code, name, description, batchSize, sourceType, enabled, deleted, sortOrder, checks[{id, name, severity, note, enabled}], sqlCodes[3]}`。既有字段 `code/name/checks/sqlCodes` 保留，`checks[]` 增 `enabled`。流程场景配置仍返回空数组。
- `GET /configs/{id}/sql-templates`：每项 `{purpose, familyCode, suffix, sqlCode, templateId, name, description, sqlTemplate, paramDesc, enabled}`。SQL 编码未登记时该项仍返回，`templateId` 与 `sqlTemplate` 为空、`enabled = false`。
- `PUT .../rules/{familyCode}/sql`：请求 `{boundsSql, summarySql, sampleSql}`，三段都必填。规则组须存在且未删除；内置与自定义均可编辑。只更新三条模板的 `sql_template`，编码、名称、启停不变。
- `PUT .../rules/{familyCode}/checks`：请求 `{checks[{id, enabled, severity}]}`，只更新已存在的检查项，请求中出现未知 `id` 整体拒绝。
- `POST /configs/{id}/rules`：请求 `{code, name, description, batchSize, checks[{id, name, severity, note}], sql{bounds, summary, sample}}`。校验：编码格式且未被该配置占用（含已删除）；名称必填；`batchSize` 1–20000；至少 1 个检查项，检查项 ID 格式且组内唯一；级别枚举；三段 SQL 满足 §3.5；三条模板编码未被占用。同一事务内写规则组（`CUSTOM`、启用）、检查项并登记模板（启用）。任一校验失败整体回滚。
- `PUT /configs/{id}/rules/{familyCode}`：请求 `{name, description, batchSize, checks[...]}`，按请求全量替换检查项集合：保留的沿用原启停状态，新增的默认启用，请求中不再出现的物理删除。
- `DELETE ...`：规则组 `deleted = 1, enabled = 0`，检查项保留，三条模板置为停用。已删除的编码不可复用，不提供恢复。
- `GET /runs/{id}/dimension-sql`：
  - `family` 必填，逗号分隔，去空白、去重、取前 8 个，口径同 `sql-evidence`。`check` 可选，须匹配 `^[A-Z][A-Z0-9_]*$`；传入时只把 `parameters_json` 含 `"ruleCode":"'<check>'"` 的记录计入 SAMPLE 组，其余 SQL 编码照常统计。
  - 响应 `{family[], check, totalExecutions, groups[{sqlCode, executions, errors, templateVersions, templateHash, renderedSql, firstParameters, lastParameters, startedAt, finishedAt}]}`。`renderedSql`、`templateHash`、`firstParameters` 取该组最早一条，`lastParameters` 取最晚一条，`startedAt` 为最早开始，`finishedAt` 为最晚结束，`templateVersions` 为该组不同模板哈希的个数。
  - 查询有界：按 `monitor_run_id + rule_family IN` 分组聚合，代表行只取每组的最早与最晚两条。SAMPLE 的检查项过滤用 `parameters_json LIKE ... ESCAPE '!'`，模式中的 `%`、`_`、`!` 以 `!` 转义。

前端由维度行推导入参：

- 一致性行（`category = 单据支付一致性`）：`ruleCode` 按 `^([a-z0-9_]+?)_([A-Z][A-Z0-9_]*)$` 切出规则组编码与检查项 ID。规则组编码限定小写、检查项 ID 限定大写开头，切分唯一，不再依赖规则目录，已删除的规则组或检查项的历史行同样能定位。
- 流程维度：沿用 REQ-372e4ce1 的后缀到用途映射（按时执行 → 实际发起笔数；应发起 vs 实际 → 应发起、实际发起笔数；实际 vs 成功 → 成功、实际发起笔数），不传 `check`。

### 3.5 SQL 契约校验

对 `source_module = OPS_ACCOUNTING_INTEGRITY` 的模板，在新建或编辑保存时执行（`FunctionCallSqlService`，因此 Function Call 页面保存这些模板时同样生效）。编码必须以 `_BOUNDS`、`_SUMMARY`、`_SAMPLE` 之一结尾，并按下表校验：

| 后缀 | 必须引用 | 允许引用的全部参数 | 必须包含的文本 |
| --- | --- | --- | --- |
| BOUNDS | 无 | 不允许任何参数 | — |
| SUMMARY | `:startId`、`:endId` | `startId`、`endId`、`startDateTime`、`endExclusiveDateTime`、`legacyMaxId` | `checked_count` |
| SAMPLE | `:startId`、`:endId`、`:ruleCode`、`:sampleLimit` | 上述加 `ruleCode`、`sampleLimit` | — |

既有的通用校验不变（非空、不超过 60000 字符、只支持 `:name` 占位、以 SELECT/WITH/SHOW/DESC 开头）。汇总 SQL 的结果列是否覆盖每个启用检查项无法静态可靠判断，由保存后的试运行和执行结果（缺列时该规则组 FAILED 并写明缺哪列）兜底，不强制。

### 3.6 配置校验的放开

`validateConfig` 不再限制一致性配置只能是叉车、智能柜：自定义规则组对任意业务线开放，内置规则组只在有目录的业务线自动物化。没有内置目录的业务线新建一致性配置后规则组为空，提交核对会被 §3.3 的校验拒绝，直到新增自定义规则组。`alertEnabled` 必须关闭的校验不变。

### 3.7 数字员工与指南

- `AgentPlatformGuide#guide`：「内置规则=N」改为按配置统计的「生效规则=N（启用规则组 a/总 b，自定义 c）」，N 为启用规则组 × 启用检查项的数量；指南正文「使用内置规则目录及 func_call_sql 的 AI_* 模板」改为「使用配置下的规则组（内置目录为种子，或自定义）及 func_call_sql 的 AI_* 模板」。
- 数据指南 `sys_config_item(agent.runtime.dataGuide)`：迁移中以 `REPLACE` 替换已知片段（做法同 `V20261119_180000`），说明规则来自 `ops_account_monitor_family / ops_account_monitor_check`，可在配置详情管理。

### 3.8 前端

批次详情：

- 结果表新增「实际 SQL」列（宽 95，位于「样例」之前），按钮「查看」打开维度 SQL 弹框。维度名称的点击入口保留。
- 弹框改造：头部显示规则编码、规则组、检查项。主体按 SQL 模板分组，每组标题为「边界 / 汇总 / 样本 · SQL 编码 · 执行 N 次 · 失败 M」，正文依次为渲染后 SQL（等宽、可复制）、首批参数、末批参数、起止时间、模板哈希；`templateVersions` 大于 1 时提示「该批次执行期间模板发生过变化」。底部折叠区「逐条执行记录」沿用现有明细表（`sql-evidence?family=`，每页 20 条）。空态与错误提示沿用。

配置详情：

- 新增「监测场景」tab，位于「配置信息」与「核对批次」之间，打开弹窗时懒加载。
- 一致性配置：工具栏「新增规则组」。表格列：启用开关、名称（下方编码）、来源标签（内置 / 自定义）、检查项（启用数 / 总数）、SQL（三个编码，模板停用时标出）、操作（检查项、SQL；自定义规则组另有编辑、删除）。已删除的自定义规则组不显示。
- 检查项弹框：每行启用开关、ID、名称、级别下拉（警告 / 异常）、说明；自定义规则组可增删检查项。保存调用批量保存接口。
- SQL 弹框：三段模板编辑器，显示编码与启用状态；「保存」调用原子保存接口；「试运行」需先保存，输入参数 JSON（边界预填 `{}`，汇总与样本预填示例参数）后调用现有 `POST /api/ops/function-call/execute`，展示渲染 SQL 与最多 50 行结果。
- 流程配置：列出三个固定维度及其 SQL 编码或固定值；SQL 编码可打开同一编辑弹框的单模板形态，保存调用现有 `PUT /api/ops/function-call/sql/{id}`。
- 「配置信息」tab 的「查看规则与 SQL」入口改为切换到「监测场景」tab，`AccountingRuleCatalog.vue` 删除。
- 入口按权限显示：管理与新增需 `ops:accounting:manage`，SQL 保存需 `ops:function-call:manage`（新增规则组需两者），试运行需 `ops:function-call:execute`。
- 保存成功后刷新本 tab，不触发核对；最近状态与批次列表不受影响。

## 4. 非功能约束

- 安全与权限、授权边界和测试数据处理：不新增权限点。SQL 模板的编辑权仍只属于 `ops:function-call:manage`，业务监测详情只是多了一个入口，不扩大授权面。自定义 SQL 只能经只读网关执行。管理操作记录操作人到新表；`func_call_sql` 没有修改人、修改历史列，本期不补。
- 性能、容量和时效：规则组读取走 `(config_id, enabled)` 索引。概览接口为一次分组聚合，加每组最早、最晚两条代表行的查询，单次请求有界。
- 失败处理、降级与补偿：契约校验失败返回 400 并给出具体原因。模板被停用的规则组在执行时按现状 FAILED。提交时无生效规则直接拒绝，不产生批次。
- 日志、监控、告警与审计：种子物化首次执行时记录补入的规则组与检查项数。SQL 执行审计沿用现有链路。

## 5. 交付与回滚

- 影响系统和前后端/数据库发布顺序：server 先行（含迁移），web 随后；web 依赖新接口，不能先上。
- 灰度或开关：无。升级后首次访问即完成种子物化，两个存量配置的规则组全部启用，执行范围与改造前一致。
- 数据备份与前后校验：迁移只新增表，不动存量数据。生效后用只读查询核对：两个配置的规则组数为 13 与 16、检查项合计 125、全部启用（AC-08）。
- 回滚条件、步骤与不可逆部分：回退应用代码即可，两张新表可保留，旧代码只读静态目录不受影响。回滚后的已知影响：已停用的规则组恢复全量执行；已修改的模板不会还原（没有修改历史）；自定义规则组不再被识别，其模板仍在 `func_call_sql`。
- 生效并核实后需要更新的内容：`现状.md` 业务监测段落、`迭代日志.md`、本需求 `tasks.md` 回填证据；`FunctionCall数据库访问规范.md` 第 3 节补充一致性模板的保存契约。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 由 §3.4 概览接口与 §3.8 批次详情承接；目标 2 由 §3.1–3.6 的数据、种子、执行口径与接口以及 §3.8 配置详情承接。
- 明确非范围是否被保持：调度与通知口径未改（§3.3）；历史批次数据未改（§3.3）；无新权限点（§3.4）；无模板修改历史（§4）。
- 与既有确认的变更：REQ-d227e09d v2「静态规则目录按业务线绑定，来源表不接受自由输入」被本期放开（用户 2026-11-27 选择新增自定义规则组）；安全边界由 §3.5 契约校验与只读网关保持。
- 待确认问题及影响（随完整规格统一确认，任一变更需改设计）：
  1. 一致性配置放开业务线限制（§3.6）。不放开则其他业务线无法使用自定义规则组。
  2. 内置规则组的检查项只能启停、改级别，不可增删；自定义规则组可增删改。放开内置增删需同步处理汇总 SQL 列缺失的提示。
  3. SQL 编辑沿用 `ops:function-call:manage`。只有 `ops:accounting:manage` 的角色看得到规则组管理，但看不到 SQL 保存入口。
  4. 代码发版新增内置规则组或检查项时，启动后自动补入所有一致性配置并默认启用（§3.2），下次核对范围随之扩大。
  5. 自定义规则组的删除是逻辑删除，编码不可复用，三段模板随之停用。
  6. 批次详情保留维度名称的点击入口，新增的「实际 SQL」列为并列入口。
  7. 试运行只针对已保存的模板，保存动作本身不访问业务库。
- 契约、ADR 及 Tasks 的关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。无 ADR。
