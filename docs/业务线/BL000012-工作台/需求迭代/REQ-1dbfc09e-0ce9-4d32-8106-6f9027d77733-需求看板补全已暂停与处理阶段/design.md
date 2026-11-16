# Design：需求看板补全已暂停与处理阶段

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-1dbfc09e-0ce9-4d32-8106-6f9027d77733 |
| 需求名称与唯一需求目录 | 需求看板补全已暂停与处理阶段；`docs/业务线/BL000012-工作台/需求迭代/REQ-1dbfc09e-0ce9-4d32-8106-6f9027d77733-需求看板补全已暂停与处理阶段/` |
| 涉及仓库与功能域 | `workhub-server`：看板聚合与响应模型、单测；`workhub-web`：看板页类型、表格列与文案 |
| 所属项目或业务线 | `BL000012-工作台` |
| 对应 Proposal 版本 | 草案 v1 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | `workhub-server` 分支 `codex/development-20261102`、`workhub-web` 分支 `codex/development-20261103`，2026-11-12 核对；生产数据经 `bl000012-prod-db-10-10-116-32-3306`（readonly）核实 |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 阶段分组定义：`workhub-service/src/main/java/cn/aslight/workhub/service/intake/IntakeService.java:136-175`（`DASHBOARD_DEMAND_GROUPS` 4 组）。
  - 阶段卡片构建：同文件 `buildDashboardStageCards`（715 行）；业务线统计 `buildDashboardBusinessLineStats`（729-756 行，仅 4 分项计入 `totalCount`）；重点需求 `buildDashboardHighlightedDemands`（766 行）；进度报告汇总 `buildProgressReport`（778 行）派生自同一分组。
  - 状态常量：`IntakeDemandStatusRules`（`PAUSED`="已暂停"、`PENDING_PROCESSING`="待处理"、`PROCESSING`="处理中"，全 14 状态）。
  - 响应模型：`workhub-model/src/main/java/cn/aslight/workhub/model/intake/IntakeDashboardResponse.java` `BusinessLineStats`（无 pausedCount）。
  - 前端类型：`workhub-web/src/types/work-item.ts:177-187` `IntakeDashboardBusinessLineStats`；页面 `workhub-web/src/views/dashboard/DashboardView.vue`（134 行按 `card.className` 动态渲染卡片；161-174 行业务线统计表硬编码 4 分项列；120、149 行文案）。
- 当前接口、数据及配置：`GET /api/intakes/dashboard`；无数据库表结构变更。
- 事实来源（路径、提交、环境或只读证据）：生产库 `workhub.pm_intake_record` 状态分布（2026-11-12）：已完成 202、**已暂停 6**、终止关闭 5、测试中 4、待澄清 3、开发中 2、已收录 2、待评估 1；待处理/处理中 0。
- 目标系统当前实现证据及本次重新核对结果：已暂停不在任何 `DASHBOARD_DEMAND_GROUPS` 分组 → 阶段卡片不计数、业务线统计计入 `demandCount` 但不计入任何分项与 `totalCount`、重点需求时间线永不出现。
- 尚未核实的假设：无（样式类由后端下发，前端按 className 渲染已核实）。

## 2. 系统架构

- 总体方案：不新增接口、不改库表。在后端唯一分组源 `DASHBOARD_DEMAND_GROUPS` 补全状态归属，派生消费方（阶段卡片、业务线统计、重点需求、进度报告汇总）自动继承；前端仅补类型字段、表格列与文案。
- 状态归属（改后，14 状态全覆盖）：

| 分组（key） | 状态 | 变化 |
| --- | --- | --- |
| not-started 未启动的需求 | 已收录、待澄清、**待处理**、待评估、待排期、待设计 | +待处理 |
| developing 开发中的需求 | 开发中、**处理中** | +处理中（组描述"已指定研发人员或执行人，正在推进实现"语义兼容） |
| testing 测试中的需求 | 测试中 | 不变 |
| pending-release 待上线的需求 | 待验收、待上线 | 不变 |
| **paused 已暂停的需求（新增）** | 已暂停 | 新增卡片，置于待上线之后；note："因故暂停推进，恢复后回到原阶段" |
| （不进卡片） | 已完成、终止关闭 | 不变（终态） |

- 前端页面、交互与后端规则边界：后端负责分组、计数与统计口径；前端负责呈现（新卡片列宽沿用 `el-col :lg="6"`，第 5 张卡在宽屏换行展示，不做栅格重构）。
- 已暂停卡片样式（已确认）：后端下发新样式类 `metric-card--paused` / `stage-tag--paused`，前端新增对应配色 CSS，与"未启动"视觉区分。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-service `IntakeService` | 分组定义、卡片计数、统计口径、排序（`DEMAND_STATUS_SORT_ORDER` 已含全部状态，不改） | 前端呈现 | 输出 `IntakeDashboardResponse` | `IntakeDemandStatusRules` 常量 |
| workhub-model `IntakeDashboardResponse` | 契约字段 | 聚合逻辑 | `BusinessLineStats` 新增 `pausedCount` | — |
| workhub-web | 类型、表格列、文案 | 聚合与规则 | 渲染看板 | 后端契约 |

## 3. 契约与数据模型

- API：`GET /api/intakes/dashboard` 响应 `businessLineStats[].pausedCount`（新增，int，已暂停需求数；位置在 `pendingReleaseCount` 之后、`totalCount` 之前）；`demandCards[]` 多一个 `key="paused"` 元素；其余字段语义不变。权威文件：`workhub-model/.../IntakeDashboardResponse.java`。
- 请求、响应、字段语义、默认值/空值、错误码和状态：无新请求参数；`pausedCount` 无负值；旧前端忽略新字段不受影响（向后兼容）。
- 幂等、并发、重试和重复执行行为：纯只读聚合，不涉及。
- 数据归属、关键约束、迁移及历史兼容：无库表变更、无数据迁移。
- 关键业务数据的来源、口径与质量边界：`pausedCount` 与阶段卡片同源（`demand_status='已暂停'`）；`totalCount` 口径调整为五分项之和（非终态合计）；`demandRatio` 仍按 `demandCount/全量` 计算，不变。
- 数据库变更：无。

## 4. 非功能约束

- 安全与权限：沿用看板接口既有权限，无变化；不涉及测试数据处理。
- 性能、容量和时效：分组数 4→5，聚合仍为内存遍历，无可感知影响。
- 失败处理、降级与补偿：无外部依赖变化。
- 日志、监控、告警与审计：无变化。

## 5. 交付与回滚

- 影响系统和前后端发布顺序：后端先行（新字段向后兼容，旧前端可忽略），前端随后；同分支迭代内一起发布亦可。
- 灰度或开关：无。
- 数据备份与前后校验：无数据变更；发布前后可用看板接口对比 `demandCards` 数量（4→5）与已暂停计数（当前 6）。
- 回滚条件、步骤与不可逆部分：还原两端代码即回滚，无不可逆部分。
- 生效并核实后需要更新的文档：`docs/业务线/BL000012-工作台/现状.md`（如含看板口径描述则补充）、`迭代日志.md` 记录本次迭代。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → §2 状态归属表 + §3 契约；目标 2 → `totalCount` 口径调整 + AC-02。
- 明确非范围保持：`UNRELEASED_EXCLUDED_STATUSES`、列表页、终态处理均不动。
- 待确认问题及结论（2026-11-12 用户确认）：
  1. 已暂停卡片样式：新增独立 `--paused` 配色（不复用 `--idle`）；
  2. "处理中"归入"开发中的需求"分组：认可；
  3. 审批编号 202607100003 无业务线数据：用户已自行修复，T-03 取消，本需求不涉及。
- 契约、ADR 及 Tasks 的关联链接：Tasks 同目录 `tasks.md`；无 ADR。
