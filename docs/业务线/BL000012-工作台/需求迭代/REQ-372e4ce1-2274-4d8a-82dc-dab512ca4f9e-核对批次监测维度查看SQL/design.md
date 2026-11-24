# Design：核对批次监测维度查看SQL

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 核对批次监测维度查看SQL（REQ-372e4ce1-2274-4d8a-82dc-dab512ca4f9e） |
| 涉及仓库与功能域 | workhub-server：controller/service/dao（审计查询加过滤）；workhub-web：批次详情弹框交互 |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随 Proposal，用户 2026-11-22 直接指令） |
| 当前事实核对环境、分支、时间 | server/web 均在 `codex/development-20261122`；2026-11-22 本地核对源码 |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - `AccountingMonitorController#sqlEvidence`（`workhub-controller/.../ops/AccountingMonitorController.java:37`）→ `AccountingMonitorService#sqlEvidence`（`workhub-service/.../ops/AccountingMonitorService.java:62`）→ `FuncCallAuditMapper#forMonitor/#countMonitor` 按 `monitor_run_id` 全量分页。
  - 前端 `AccountingSqlEvidence.vue` 平铺展示整批审计；详情弹框维度表在 `AccountingMonitorView.vue:804`（监测维度列）。
- 审计写入口径（已核实）：
  - 流程场景 `AccountingScenarioCheckExecutor#count`：`rule_family` = 用途字符串（`实际发起笔数`/`应发起笔数`/`成功笔数`），每批次至多 3 条。
  - 一致性场景 `AccountingIntegrityCheckExecutor#query`：`rule_family` = 规则组编码（如 `lease_saps_bill`），每规则组 1 条 BOUNDS + N 批 SUMMARY + 异常时 SAMPLE，可达数十上百条；结果行 `rule_code = 规则组编码_检查项`，检查项编码均不含下划线（`integrity-rules.json` 核对）。
- 尚未核实的假设：无（映射口径均有源码依据）。

## 2. 系统架构

- 总体方案：审计已按 `monitor_run_id + rule_family` 落库，本次只增加按 `rule_family` 集合过滤的读取通道；前端由维度行推导关联 `rule_family` 集合并请求过滤结果。
- 前端页面、交互与后端规则边界：前端负责"维度 → 规则组/用途"映射（纯目录知识），后端只做通用 `family` 过滤，不感知场景目录。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-server | `sql-evidence` 接口增加 `family` 过滤、去重与数量上限 | 维度映射、UI | `runId, family[], page, pageSize` → 审计分页 | `func_call_sql_audit_log` |
| workhub-web | 维度点击、映射、SQL 弹框展示 | 审计写入、执行 SQL | 维度行 → 过滤后的审计列表 | 上述接口 |

## 3. 契约与数据模型

- API：`GET /api/ops/accounting/runs/{id}/sql-evidence`（既有），新增可选查询参数 `family`：逗号分隔的 `rule_family` 精确匹配值（流程场景为用途名，一致性场景为规则组编码）；服务端去空、去重，最多取前 8 个，超出忽略；不传或全空白时不过滤，响应结构与排序（`ORDER BY id`）不变。权威文件：`AccountingMonitorController.java`。
- 前端映射（`AccountingMonitorView.vue`）：
  - `category === '单据支付一致性'` → `[ruleCode 去掉最后一个下划线段]`；
  - 否则按 `ruleCode` 末段：`STARTED_ON_TIME→[实际发起笔数]`；`EXPECTED_VS_ACTUAL→[应发起笔数, 实际发起笔数]`；`SUCCESS_RATE→[成功笔数, 实际发起笔数]`。
- 幂等/并发：只读查询，无影响。数据库无变更、无迁移。

## 4. 非功能约束

- 安全与权限：沿用 `ops:accounting:view` / `ops:accounting:manage`；`family` 只进 MyBatis `#{}` 预编译参数，无注入面。
- 性能：`monitor_run_id + rule_family` 均有界过滤，单次上限 50 条（沿用服务端 pageSize 上限）；一致性大批次由弹框内分页承载。
- 失败处理：维度无匹配审计时空态提示，沿用"旧批次不按时间推测关联"口径；接口报错时弹框内提示重试。

## 5. 交付与回滚

- 发布顺序：server 先行（参数可选，向后兼容），web 随后；单独回滚 web 即恢复原交互，回滚 server 不影响既有调用（不传 `family` 行为不变）。
- 数据备份与回滚：无数据库变更。
- 生效后更新：`现状.md` 业务监测段落、`迭代日志.md`、tasks 证据。

## 6. 一致性与待确认

- Proposal 目标承接：AC-01/02 由前端弹框 + §3 映射承接；AC-03 由服务端"不传不过滤"与单测承接；AC-04 由空态承接；AC-05 由验证任务承接。
- 明确非范围保持：不改审计写入与平铺列表，无新权限点。
- 待确认问题：无。
- 关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
