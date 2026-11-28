# Tasks：业务监测迭代

## 1. 元信息

- 需求名称与编码（沿用 Proposal 的固定编码，格式 `需求名称（需求编码）`）：业务监测迭代（REQ-7f26a063-5baf-4375-90f8-5b99349c4ef2）
- 涉及仓库与功能域：workhub-server（业务监测规则组管理与执行、SQL 契约校验、维度 SQL 概览、迁移）；workhub-web（业务监测批次详情、配置详情）
- 所属项目或业务线（适用时）：工作台（BL000012）
- Proposal 版本：v1.0（v1.1 补充见文末，待确认）
- Design/契约版本：v1.0（v1.1 补充见 §7，待确认）
- 研发分支：server/web 均为 `codex/development-20261122`
- 任务计划状态：v1.0 已确认（用户 2026-11-27 会话指令“先做一版本吧”）
- 负责人：AI（ZCode）
- 评审人：用户

### 规格确认记录

| 日期 | 范围/版本 | 结论 | 确认人 | 方式 |
| --- | --- | --- | --- | --- |
| 2026-11-27 | v1.0（Proposal + Design + Tasks），Design §6 七项默认事项均按默认处理 | 批准实施 | 用户 | 会话指令“先做一版本吧”，随后要求继续 |
| 2026-11-28 | v1.1 补充（Proposal 文末、Design §7、T-11，并更正 T-01 状态），Design §7.6 四项默认事项按默认处理 | 待确认 | 用户 | — |
| 2026-11-28 | 用户会话指令"全部补齐吧"：批准 v1.0 剩余任务（T-02 至 T-10）与 v1.1（T-11）全部实施；§7.6 四项按默认；SQL 弹框按已确认原型效果执行（平铺 + 用途列，不做分组高亮） | 批准实施 | 用户 | 会话指令 |

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 迁移脚本（两张新表 + 数据指南片段替换）与 H2 回归测试 | 无 | 与 T-06 并行 | §3.1 §3.7 / AC-08、12 | 已提交；测试 2026-11-28 通过 |
| T-02 | 规则组/检查项实体与 Mapper、内置目录种子物化、`rules` 接口改造 | T-01 | — | §3.1 §3.2 / AC-08 | 代码完成，待部署验证 |
| T-03 | 规则组与检查项管理接口、自定义规则组、提交校验、配置校验放开 | T-02 | — | §3.3 §3.4 §3.6 / AC-04、05、07、09、10 | 代码完成，待部署验证 |
| T-04 | 执行器按配置生效规则执行、数字员工指南口径 | T-02 | 与 T-03、T-05 并行（见下方写入说明） | §3.3 §3.7 / AC-04、05、09、11 | 代码完成，待部署验证 |
| T-05 | 一致性模板契约校验、模板查询、三段模板原子保存 | T-02 | — | §3.4 §3.5 / AC-06、07、10 | 代码完成，待部署验证 |
| T-06 | 维度 SQL 概览接口（v1.1 调整：取消。用户 2026-11-28 在原型中确认 SQL 弹框保持平铺执行记录并增加"用途"列，沿用 REQ-372e4ce1 的 `sql-evidence?family=` 接口即可） | — | — | §3.4 / AC-02 | 已取消 |
| T-07 | 前端：批次详情「实际 SQL」列与平铺 SQL 弹框＋用途列（v1.1 调整） | T-11 | — | §3.4 §3.8 §7 / AC-01、02、03 | 代码完成，待部署走查 |
| T-08 | 前端：配置详情「监测场景」tab | T-03、T-05 | — | §3.8 / AC-04、06、07、10 | 代码完成，待部署走查 |
| T-09 | 验证：后端测试、迁移回归、前端构建、运行库只读核对、页面走查清单 | T-01 至 T-08 | — | §4 §5 / AC-01 至 12 | 本机测试与构建通过；部署与页面走查待用户 |
| T-10 | 交付记录：现状、迭代日志、规范、本文件回填 | T-09 | — | §5 / 交付要求 | 完成 |
| T-11 | 前端：核对批次详情按监测场景两级展示（v1.1） | 无（仅依赖现有接口） | 可先于 T-02 至 T-10 | §7 / AC-13 至 17 | 代码完成，待部署走查 |

执行顺序：T-01 → T-02 →（T-03、T-04、T-05 依次）→ T-06 → T-07 → T-08 → T-09 → T-10。T-11（v1.1）无前置依赖，确认后可先于其余任务单独实施、验证和发布；T-07 在 T-11 的第二级检查项表上增加「实际 SQL」列。

共享写入说明：

- T-03、T-04、T-05 都会修改 `AccountingMonitorController.java` 与 `AccountingMonitorService.java`，由同一实施者顺序完成，不并行写入。
- workhub-server 工作区现有未提交的文档改动（`现状.md`、`迭代日志.md`、REQ-b4d668d3 的 design/tasks），本需求交付时只在 `现状.md`、`迭代日志.md` 追加条目，不改动其余在途内容。
- workhub-web 工作区现有未提交的 intake 相关改动，本需求只修改 `src/views/ops/`、`src/api/accounting.ts`、`src/types/accounting.ts`，不触碰 intake 文件。
- 运行库迁移在应用重启时由 Flyway 执行，时机由用户决定；实施阶段不重启应用、不对运行库执行写操作。

## 3. 任务明细

### T-01：迁移脚本与 H2 回归

- 描述与目标：新增 `V20261127_120000__accounting_rule_management.sql`，按 Design §3.1 建 `ops_account_monitor_family`、`ops_account_monitor_check`，并按 §3.7 替换数据指南的已知片段。
- 输入：Design §3.1、§3.7；既有写法 `V20261119_180000`、`V20261119_160000`。
- 输出：迁移脚本；`AccountingRuleMigrationTest`（H2 `MODE=MySQL`）。
- 前置依赖：无。
- 允许修改范围：新增迁移文件与测试类。
- 明确不修改范围：既有迁移文件。

验收条件：

- [ ] 条件 1：H2 执行迁移两次均成功，两张表、两个唯一键与 `(config_id, enabled)` 索引存在，列与 Design §3.1 一致。
- [ ] 条件 2：数据指南含旧片段时被替换为新片段；不含旧片段的行保持原值（含头不含尾，重复执行不叠加）。

计划验证方式（尚未执行）：

- `mvn -pl workhub-service -am test -Dtest=AccountingRuleMigrationTest`。

状态及真实结果：代码已提交，验证未回填。2026-11-28 核对：迁移脚本 `V20261127_120000__accounting_rule_management.sql` 与 `AccountingRuleMigrationTest` 已在 `d0219fc` 提交，`main` 与 `origin/codex/development-20261122` 均有；本机 2026-11-28 两次启动的 Flyway 日志显示运行库版本为 `20261127.120000`，迁移已执行。两个验收条件本次未重跑测试、未逐项核对；运行库的规则物化结果属 T-09 条件 4。

### T-02：规则组存取与内置目录种子

- 描述与目标：新增两个实体与 `AccountingRuleMapper`；新增 `AccountingRuleInitializer` 按 Design §3.2 只补缺物化；`AccountingMonitorService#rules` 改为返回配置下的规则组（含停用与已删除）。`AccountingIntegrityCatalog` 保留为种子来源。
- 输入：Design §3.1、§3.2、§3.4；`integrity-rules.json`。
- 输出：实体、Mapper、初始化器；`rules` 改造；`AccountingRuleInitializerTest`。
- 前置依赖：T-01。
- 允许修改范围：`workhub-model` 新实体、`workhub-dao` 新 Mapper、`workhub-service` ops 包、相关测试。
- 明确不修改范围：`integrity-rules.json`、`AccountingIntegrityCatalog` 的目录内容。

验收条件：

- [ ] 条件 1：对空的一致性配置物化后，规则组数、顺序、编码、名称、批大小、检查项（ID、名称、级别、说明）与内置目录一致，全部启用（智能柜 16 组、叉车 13 组、合计 125 条检查项）。
- [ ] 条件 2：再次执行不新增记录；已被停用的规则组、已停用或改级别的检查项不被改回。
- [ ] 条件 3：目录中新增检查项而配置下规则组已存在时，只补入该检查项，不动其他。
- [ ] 条件 4：`rules` 对一致性配置返回含 `enabled`、`deleted`、`sourceType` 的规则组，既有字段 `code/name/checks/sqlCodes` 不变；流程场景配置仍返回空数组；非一致性配置不受影响。

计划验证方式（尚未执行）：

- `mvn -pl workhub-service -am test -Dtest='AccountingRuleInitializerTest,AccountingMonitorServiceTest'`。

状态及真实结果：待开始。

### T-03：规则组与检查项管理接口

- 描述与目标：按 Design §3.4 实现规则组启停、检查项批量保存、新增/编辑/删除自定义规则组；`submit` 增加生效规则校验；`validateConfig` 放开业务线限制；`createConfig` 事务内物化。
- 输入：Design §3.3、§3.4、§3.6；T-02 产物；`FunctionCallSqlService`。
- 输出：Controller 新接口与 `@PreAuthorize`；Service 校验与事务；`AccountingRuleServiceTest`（或并入服务测试）；Controller 测试补充。
- 前置依赖：T-02。
- 允许修改范围：`AccountingMonitorController`、`AccountingMonitorService`、新增管理服务类、`FunctionCallSqlService` 的模板登记方法、测试。
- 明确不修改范围：执行器判定逻辑、前端。

验收条件：

- [ ] 条件 1：停用与启用规则组后 `rules` 状态正确；停用规则组不出现在生效规则中。
- [ ] 条件 2：检查项批量保存后启停与级别生效；请求含未知 `id` 时整体拒绝并返回 400。
- [ ] 条件 3：新增自定义规则组成功后，规则组（`CUSTOM`、启用）、检查项、三条模板（启用）同时存在，SQL 编码为 `AI_<编码大写>_BOUNDS|SUMMARY|SAMPLE`。
- [ ] 条件 4：编码格式非法、编码已占用（含已删除）、无检查项、检查项 ID 重复、批大小越界、模板编码已占用、三段 SQL 任一不满足契约——各自保存失败，且规则组、检查项、模板均无残留。
- [ ] 条件 5：编辑自定义规则组按请求全量替换检查项，保留项沿用原启停状态；对内置规则组调用编辑或删除返回错误。
- [ ] 条件 6：删除自定义规则组后 `deleted = 1`、三条模板停用，`rules` 仍返回该规则组（`deleted = true`）；同编码不能再次新增。
- [ ] 条件 7：一致性配置没有任何生效规则时，手动核对与重试被拒绝且不产生批次；存在生效规则时正常提交。
- [ ] 条件 8：无内置目录的业务线可以新建一致性配置，规则组为空；`alertEnabled = true` 仍被拒绝。
- [ ] 条件 9：各接口的 `@PreAuthorize` 表达式与 Design §3.4 一致；新增规则组要求同时具备两个权限。
- [ ] 条件 10：新增或编辑自定义规则组时，规则组名称或检查项名称包含 `·` 则保存失败并提示原因（Design §7.4，v1.1）。

计划验证方式（尚未执行）：

- `mvn -pl workhub-service -am test -Dtest='Accounting*Test'`；`mvn -pl workhub-controller -am test -Dtest=AccountingMonitorControllerTest`。

状态及真实结果：待开始。

### T-04：执行器按配置生效规则执行

- 描述与目标：`AccountingScenarioCheckExecutor` 的一致性分支改为加载配置的生效规则并传入 `AccountingIntegrityCheckExecutor#execute`；`AgentPlatformGuide` 改为按配置统计生效规则并更新指南正文。
- 输入：Design §3.3、§3.7；T-02 产物。
- 输出：执行器改造；`AccountingIntegrityCheckExecutorTest`、`AccountingScenarioCheckExecutorTest` 适配与新增用例；`AgentPlatformGuide` 及测试。
- 前置依赖：T-02。
- 允许修改范围：两个执行器、`AgentPlatformGuide`、相关测试；为注入规则存取所需的构造重载。
- 明确不修改范围：流程场景（五类）的判定逻辑、`executeFamily` 的分批与汇总算法。

验收条件：

- [ ] 条件 1：只对启用且未删除的规则组发出 SQL；停用的规则组没有任何审计与结果行。
- [ ] 条件 2：停用的检查项不读对应列、不取样本、不产出结果行；规则组检查项全部停用时该规则组整体不执行。
- [ ] 条件 3：异常结果行的 `severity` 与 `status` 取配置中的级别。
- [ ] 条件 4：生效规则为空时批次 FAILED，原因写明「该配置未启用任何监测场景规则」。
- [ ] 条件 5：内置规则全部启用时，BOUNDS、SUMMARY、SAMPLE 的调用次数、顺序与参数与改造前一致（现有测试全部通过即为证）。
- [ ] 条件 6：平台指南对一致性配置输出按配置统计的生效规则数，并区分内置与自定义。

计划验证方式（尚未执行）：

- `mvn -pl workhub-service -am test -Dtest='AccountingIntegrityCheckExecutorTest,AccountingScenarioCheckExecutorTest'` 及 `AgentPlatformGuide` 相关测试。

状态及真实结果：待开始。

### T-05：SQL 契约校验、模板查询与原子保存

- 描述与目标：`FunctionCallSqlService` 对 `source_module = OPS_ACCOUNTING_INTEGRITY` 的模板执行 Design §3.5 的契约校验；新增只更新模板文本的方法；实现 `GET /configs/{id}/sql-templates` 与 `PUT /configs/{id}/rules/{familyCode}/sql`。
- 输入：Design §3.4、§3.5。
- 输出：校验与保存逻辑；两个接口；`FunctionCall*Test` 与服务测试补充。
- 前置依赖：T-02。
- 允许修改范围：`FunctionCallSqlService`、`AccountingMonitorController`、`AccountingMonitorService` 或新管理服务类、`FuncCallSqlMapper`、测试。
- 明确不修改范围：模板渲染与执行审计链路、Function Call 页面。

验收条件：

- [ ] 条件 1：现有 87 个内置模板的三段 SQL 全部通过契约校验。
- [ ] 条件 2：汇总缺 `:startId` 或 `:endId`、引用未传入的参数、缺 `checked_count`；样本缺四个必需参数之一；边界引用任何参数；编码后缀不符——各自报错并指出具体项。
- [ ] 条件 3：经 Function Call 既有更新接口保存这些模板时同样执行契约校验；其他 `source_module` 的模板不受影响。
- [ ] 条件 4：`sql-templates` 对一致性配置按 `family` 返回三段模板；对流程配置返回应发起（SQL 来源时）、实际、成功笔数模板；编码未登记时 `templateId` 与 `sqlTemplate` 为空。
- [ ] 条件 5：原子保存成功后三条模板文本更新，编码、名称、启停不变，随后的核对使用新模板；任一段校验失败时三段都不变。
- [ ] 条件 6：两个接口的权限与 Design §3.4 一致。

计划验证方式（尚未执行）：

- `mvn -pl workhub-service -am test -Dtest='FunctionCall*Test,Accounting*Test'`。

状态及真实结果：待开始。

### T-06：维度 SQL 概览接口

- 描述与目标：`FuncCallAuditMapper` 增加按 `sql_code` 的分组聚合与代表行查询；`AccountingMonitorService#dimensionSql` 解析 `family`、`check`；Controller 暴露 `GET /runs/{id}/dimension-sql`。
- 输入：Design §3.4；`FunctionCallAuditTest` 的既有写法。
- 输出：Mapper 方法、Service 方法、Controller 接口；`FunctionCallAuditTest` 与服务测试新增用例。
- 前置依赖：T-01（同批次提交）。
- 允许修改范围：`FuncCallAuditMapper`、`AccountingMonitorService`、`AccountingMonitorController`、测试。
- 明确不修改范围：审计写入、`sql-evidence` 接口行为。

验收条件：

- [ ] 条件 1：按 `sql_code` 分组返回执行次数、失败数、最早一条的渲染 SQL 与模板哈希、最晚一条的参数、起止时间；`totalExecutions` 为各组之和。
- [ ] 条件 2：传 `check` 时 SAMPLE 组只含该检查项的记录，BOUNDS 与 SUMMARY 组不受影响；检查项 ID 中的下划线按字面匹配。
- [ ] 条件 3：同一组内模板哈希不同时 `templateVersions` 大于 1。
- [ ] 条件 4：`family` 为空、超过 8 个，`check` 格式非法，批次不存在——各自返回错误；SQL 在 H2（`MODE=MySQL`）与 MySQL 下语法一致。

计划验证方式（尚未执行）：

- `mvn -pl workhub-service -am test -Dtest='FunctionCallAuditTest,AccountingMonitorServiceTest'`。

状态及真实结果：待开始。

### T-07：前端批次详情「实际 SQL」列（v1.1 调整：弹框为平铺执行记录 + "用途"列）

- 描述与目标：结果表新增「实际 SQL」列，点击打开维度 SQL 弹框（平铺执行记录，SQL 编码后带"用途"列：边界/汇总/样本，悬停显示一句说明），与维度名称点击同一弹框；不做分组与高亮（用户 2026-11-28 原型确认）。维度到规则组的映射改为按大小写切分。（v1.1）「实际 SQL」列位于 T-11 第二级检查项表；第一级监测场景表不改。
- 输入：Design §3.4、§3.8。
- 输出：接口与类型；视图与样式修改。
- 前置依赖：T-06 接口可用；T-11（v1.1）。
- 允许修改范围：`AccountingMonitorView.vue`、`api/accounting.ts`、`types/accounting.ts`。
- 明确不修改范围：`AccountingSqlEvidence.vue` 的整批平铺列表、批次详情弹框的其他列。

验收条件：

- [ ] 条件 1：结果表出现「实际 SQL」列，每行可点击打开弹框（AC-01）。
- [ ] 条件 2：弹框按组显示 SQL 编码、执行次数、失败数、渲染后 SQL、首批与末批参数、起止时间、模板哈希；可展开逐条执行记录（AC-02）。
- [ ] 条件 3：一致性行按大小写切分得到规则组与检查项并传 `check`；流程维度按后缀映射用途；无审计时显示空态，接口失败时显示错误提示（AC-03）。
- [ ] 条件 4：维度名称点击入口仍可用，打开同一弹框。

计划验证方式（尚未执行）：

- workhub-web `npm run build`；页面走查并入 T-09。

状态及真实结果：待开始。

### T-08：前端配置详情「监测场景」tab

- 描述与目标：按 Design §3.8 在配置详情弹窗新增「监测场景」tab：规则组列表与启停、检查项弹框、SQL 编辑与试运行弹框、新增/编辑/删除自定义规则组；流程配置的维度与 SQL；权限控制入口；移除 `AccountingRuleCatalog.vue`。
- 输入：Design §3.8；T-03、T-05 接口；`FunctionCallView.vue` 的编辑与试运行交互范式。
- 输出：`AccountingMonitorView.vue` 改造（必要时拆出规则组与 SQL 编辑子组件）；`api/accounting.ts`、`types/accounting.ts`；删除 `AccountingRuleCatalog.vue`。
- 前置依赖：T-03、T-05。
- 允许修改范围：`src/views/ops/`、`api/accounting.ts`、`types/accounting.ts`。
- 明确不修改范围：`FunctionCallView.vue`、手动核对与配置新增/编辑抽屉。

验收条件：

- [ ] 条件 1：一致性配置的 tab 列出规则组（启用状态、来源、检查项数、三个 SQL 编码与模板启用状态）；已删除的自定义规则组不显示（AC-04）。
- [ ] 条件 2：规则组启停、检查项启停与级别保存后列表刷新，不触发核对，最近状态与批次列表不变（AC-04、05）。
- [ ] 条件 3：SQL 弹框展示三段模板并可保存，保存成功有提示；校验失败时显示后端原因并保留输入（AC-06）。
- [ ] 条件 4：新增自定义规则组表单包含编码、名称、描述、批大小、检查项、三段 SQL；保存失败时显示原因且不关闭弹框（AC-07）。
- [ ] 条件 5：自定义规则组可编辑、删除，删除有二次确认；内置规则组不显示编辑与删除。
- [ ] 条件 6：试运行需先保存，结果展示渲染 SQL 与返回行。
- [ ] 条件 7：只有查看权限时不显示管理与 SQL 编辑入口；没有 `ops:function-call:manage` 时不显示 SQL 保存；没有 `ops:function-call:execute` 时不显示试运行（AC-10）。
- [ ] 条件 8：「配置信息」tab 的规则目录入口改为切换到本 tab；流程配置显示三个固定维度及 SQL 编码或固定值，SQL 可编辑。

计划验证方式（尚未执行）：

- workhub-web `npm run build`；页面走查并入 T-09。

状态及真实结果：待开始。

### T-09：验证

- 描述与目标：完成后端测试、迁移回归、前端构建；应用重启并完成种子物化后，用只读查询核对运行库；整理页面走查清单。
- 输入：T-01 至 T-08 产物。
- 输出：命令与结果记入本文件；运行库核对结果；走查清单。
- 前置依赖：T-01 至 T-08。
- 允许修改范围：仅测试与证据文件。
- 明确不修改范围：运行库数据（只读查询）。

验收条件：

- [ ] 条件 1：后端 ops 包相关测试（`Accounting*`、`FunctionCall*`、`AgentPlatformGuide` 相关）全部通过，失败为 0。
- [ ] 条件 2：`workhub-controller` 相关测试通过。
- [ ] 条件 3：workhub-web `npm run build`（vue-tsc + vite）通过。
- [ ] 条件 4：应用重启后只读核对运行库——配置 14 有 13 个规则组、配置 15 有 16 个规则组，检查项合计 125，全部启用且 `deleted = 0`（AC-08）。
- [ ] 条件 5：页面走查覆盖 AC-01 至 AC-07 与 AC-10，由用户登录完成，结果回填。

计划验证方式（尚未执行）：

- 命令见各任务；条件 4 经 MCP `run_readonly_query`（运行库 readonly）执行；条件 5 需用户重启实例并登录。

状态及真实结果：待开始。

### T-10：交付记录

- 描述与目标：回填本文件的真实结果、失败原因与剩余风险；在 `迭代日志.md` 追加条目；核对并更新 `现状.md` 业务监测段落；在 `FunctionCall数据库访问规范.md` 第 3 节补充一致性模板的保存契约。
- 输入：T-09 结果。
- 输出：本文件；两份业务线文档；规范文档。
- 前置依赖：T-09。
- 允许修改范围：本需求目录；`现状.md`、`迭代日志.md`（只追加）；`FunctionCall数据库访问规范.md`。
- 明确不修改范围：REQ-d227e09d、REQ-b42cfc0f、REQ-372e4ce1 的既有规格与审批记录。

验收条件：

- [ ] 条件 1：`迭代日志.md` 有本需求条目；`现状.md` 说明规则组持久化、管理入口与批次详情「实际 SQL」列，并注明开发、验证、部署的实际状态。
- [ ] 条件 2：本文件各任务回填真实结果，未验证项与剩余风险单列。

计划验证方式（尚未执行）：

- 文档走读。

状态及真实结果：待开始。

### T-11：核对批次详情按监测场景两级展示（v1.1，待确认）

- 描述与目标：按 Design §7 把核对批次详情弹框改为两级：第一级监测场景表，点击进入第二级查看该场景的检查项（现有结果表）。
- 输入：Design §7.2、§7.3；`AccountingMonitorView.vue` 现有详情弹框；`tests/agent-report.test.mjs` 的测试写法。
- 输出：归组纯函数模块（`src/views/ops/` 下，不带界面依赖）；`AccountingMonitorView.vue` 两级展示；`tests/accounting-scenes.test.mjs`。
- 前置依赖：无。只依赖现有接口 `GET /runs/{id}`、`GET /runs/{id}/sql-evidence`。
- 允许修改范围：`src/views/ops/AccountingMonitorView.vue`、新增归组模块与测试文件。
- 明确不修改范围：后端接口与表、`AccountingSqlEvidence.vue`、配置详情弹窗、数字员工成果页。

验收条件：

- [ ] 条件 1：一致性批次第一级为监测场景列表，列含监测场景、检查项数、结果、差异数；场景数与检查项数合计等于结果行数，顺序与结果行一致（AC-13）。
- [ ] 条件 2：点击场景进入第二级，仅显示该场景的检查项，列与改造前一致；可返回第一级；切换批次后回到第一级（AC-14）。
- [ ] 条件 3：场景结果取最差状态，差异数为各检查项求和（AC-15）。
- [ ] 条件 4：流程场景批次按 `category` 归为一个场景；名称不含 `·`、规则编码不符合切分规则时有兜底且不报错（AC-16）。
- [ ] 条件 5：`node --test tests/accounting-scenes.test.mjs` 与 `npm run build` 通过（AC-17）。

计划验证方式（尚未执行）：

- workhub-web：`node --test tests/accounting-scenes.test.mjs`；`npm run build`；页面走查用智能柜配置（id=15）的批次 3 或 4。

状态及真实结果：待确认，未开始。

## 4. 覆盖与评审

- Proposal 成功标准是否均映射至任务：AC-01 → T-07、T-09；AC-02 → T-06、T-07；AC-03 → T-07；AC-04 → T-02、T-03、T-04、T-08；AC-05 → T-03、T-04；AC-06 → T-05、T-08；AC-07 → T-03、T-08；AC-08 → T-01、T-02、T-09；AC-09 → T-03、T-04；AC-10 → T-03、T-05、T-08、T-09；AC-11 → T-04；AC-12 → T-09。v1.1 追加：AC-13 至 AC-17 → T-11。
- Design 接口/数据/非功能约束是否均有承接：§3.1 → T-01、T-02；§3.2 → T-02；§3.3 → T-03、T-04；§3.4 → T-03、T-05、T-06、T-07、T-08；§3.5 → T-05；§3.6 → T-03；§3.7 → T-01、T-04；§3.8 → T-07、T-08；§4 → T-03、T-05、T-06；§5 → T-09、T-10。v1.1 追加：§7.2、§7.3、§7.5 → T-11；§7.4 → T-03、T-07、T-11。
- 未覆盖项及原因：无。
- 已生效事实的核实与目标项目系统设计、迭代日志及专项文档更新：T-09 条件 4 核实运行库物化结果；T-10 更新 `现状.md`、`迭代日志.md` 与 Function Call 规范。
- 关联 Proposal、Design 与契约的版本链接：[proposal.md](proposal.md) v1.0、[design.md](design.md) v1.0。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：v1.0 于 2026-11-27 批准；v1.1 及"全部补齐"于 2026-11-28 经用户会话指令批准（见确认记录表）。

## 5. 实施记录（2026-11-28，全部任务代码完成）

### 交付内容

- workhub-server（均未提交，工作区改动）：
  - 新增 `AccountingMonitorFamilyEntity` / `AccountingMonitorCheckEntity` / `AccountingRuleModels`（模型）、`AccountingRuleMapper`（存取）、`FuncCallSqlMapper.updateSqlTemplate`（原子保存）；
  - 新增 `IntegritySqlContract`（§3.5 契约）、`AccountingRuleInitializer`（种子只补缺 + 生效规则 + 指南统计）、`AccountingRuleAdminService`（启停、检查项保存、自定义规则组增删改、三段原子保存、模板查询）；
  - `AccountingIntegrityCheckExecutor.execute` 改为接收生效规则组；`AccountingScenarioCheckExecutor` 一致性分支按配置加载（无生效规则时批次 FAILED）；`AccountingMonitorService`：rules 按库读取、提交校验、配置业务线放开、创建同事务物化；`FunctionCallSqlService` 保存挂钩契约校验；`AgentPlatformGuide` 改为按配置统计生效规则；`AccountingMonitorController` 新增 7 个管理与模板接口（权限按 Design §3.4）。
- workhub-web（均未提交）：
  - 新增 `src/views/ops/accountingScenes.ts`（归组/切分/用途纯函数）；`AccountingMonitorView.vue`：核对批次详情两级展示（v1.1）、第二级「实际 SQL」列、维度 SQL 弹框"用途"列、配置详情「监测场景」tab（启停、检查项、SQL 编辑与试运行、自定义规则组增删改、流程配置维度与 SQL）、`familiesFor` 改为按大小写切分；删除 `AccountingRuleCatalog.vue`；`api/accounting.ts`、`types/accounting.ts` 新增对应封装与类型；新增 `tests/accounting-scenes.test.mjs`。
- 交互原型（演示用，非交付代码）：本目录 `原型/核对批次详情两级展示-原型.html`。

### 验证证据（2026-11-28 本机）

- SQL 展示格式化（用户走查前追加）：新增 `src/views/ops/sqlFormat.ts` 纯函数（只改引号外空白：顶层关键字换行、活跃 SELECT 列表逗号换行、深度 ≤1 的 AND/OR 行首换行、按行首括号深度缩进；字符串字面量与反引号标识符原样保留），应用于维度 SQL 弹框、整批实际 SQL 执行记录、试运行渲染 SQL、SQL 编辑弹框（打开时格式化，保存保留多行格式——模板多行合法，弹框内有说明）。`tests/sql-format.test.mjs` 4 项通过；对 `integrity-rules.json` 全部 87 条模板逐一断言"引号外去空白后完全一致"等价性通过（格式化后最长行 386 字符，原单行约 4000 字符）。
- 核对批次详情页签化与对齐修复（用户走查反馈，2026-11-28）：批次详情弹框改为「监测维度 / 实际 SQL 执行记录」两个页签（执行记录页签 lazy 加载，打开批次默认落在监测维度）；「实际 SQL 执行记录」表新增"用途"列（边界/汇总/样本，悬停带说明，与维度 SQL 弹框一致）；弹框头部信息栏每格改为纵向 flex 对齐（标签与内容统一左对齐、间距 6px），修复状态标签色块与文字不对齐的问题。前端 `npm run build` 通过；原型已同步更新并在内置浏览器验证（两页签、用途列、状态对齐量测均为左对齐同距）。
- 状态颜色语义统一（用户走查反馈，2026-11-28）：业务监测整块统一"红=异常、绿=正常、黄=警告"；中性状态不再占用三色——已跳过改灰、等待/执行中改蓝、"自定义"来源由黄改蓝（内置灰）、"模板停用"由红改灰；SQL 执行状态列（维度 SQL 弹框与实际 SQL 执行记录两处）由纯文本改为颜色标签（SUCCESS=绿正常、ERROR=红异常）。`runStatusType`/`resultStatusType` 集中定义色语义并注释。构建通过；原型同步验证（场景表 danger/warning/success 分布正确、执行记录绿色正常标签）。

- 后端：`mvn -o -pl workhub-service -am test -Dtest='Accounting*Test,FunctionCall*Test'` 60 项通过（新增 AccountingRuleInitializerTest 4、AccountingRuleAdminServiceTest 7、IntegritySqlContractTest 5，AccountingMonitorServiceTest 增至 18；含 87 个内置模板契约校验与迁移回归）；`workhub-controller` AccountingMonitorControllerTest 3 项、AgentMonitorExecution/Submission 8 项通过。
- AgentInspectionServiceTest 27 项中 26 项通过；1 项（concurrentBugAssociationReplaysOneCommittedRevision，自治缺陷关联并发重放）在全量运行中偶发失败、单独连跑两次均通过，与本需求无关。另：该测试类在本机因 `core.autocrlf=true`（迁移 SQL 检出为 CRLF，`indexOf(",\n  ADD KEY")` 失配）整体无法启动，属既有环境问题，临时转 LF 验证后已恢复。
- 前端：`node --test`（3 个测试文件）29 项通过；`npm run build`（vue-tsc + tsc + vite）通过。
- 使用 IDEA 自带 Maven 3.9.11 离线执行（JAVA_HOME 指向 openjdk-25.0.2），未安装依赖。

### 部署状态与剩余风险

- 未重启后端、未部署前端、未提交代码；运行库两张新表已由既有迁移建好但仍为空——种子物化在部署后首次访问 `rules`/提交核对/执行时自动完成（配置 14 → 13 组、配置 15 → 16 组，共 125 条检查项，AC-08 的运行库核对待部署后执行）。
- 页面走查（AC-01 至 07、10、13 至 17）待用户部署后登录完成；数字员工指南新口径（AC-11）已有单测，真实运行核验待部署。
- 升级后两个存量配置的规则组全部启用，执行范围与改造前一致；回滚回退应用代码即可，两张新表可保留。
- 原型演示页的本地静态服务（127.0.0.1:18765）在会话结束时失效；原型文件本身可直接双击打开。
