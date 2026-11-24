# Proposal：XXLJOB执行日志状态多选筛选

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-4a3634e6-034b-4068-a2d5-75308c9ed1a4 |
| 需求名称 | XXLJOB执行日志状态多选筛选 |
| 涉及仓库与功能域 | workhub-server（XxlJobMonitorCollector）；workhub-web（OpsMonitorView 执行日志筛选、api/ops） |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-4a3634e6-034b-4068-a2d5-75308c9ed1a4-XXLJOB执行日志状态多选筛选/ |
| 版本/状态 | v1.0 已确认（用户 2026-11-22 会话指示「XXL-JOB 执行日志里的状态支持多选，且默认选中卡死和失败的」；实施计划经用户批准） |
| 目标环境/分支 | codex/development-20261102（后端）/ 对应前端分支 |
| 任务来源 | REQ-a58c7e21（五态筛选对齐）的后续变更 |

## 1. Why

REQ-a58c7e21 把执行日志状态筛选对齐为 4 种行类型单选，打开弹框默认清空（=查全部），需再人工选择才能聚焦异常；且单选无法同时查看「卡死」与「执行失败」两类异常。

## 2. What 与 Done

状态下拉支持**多选**（4 种行类型），打开弹框**默认选中「卡死（回调丢失）」+「执行失败（含调度失败）」**；清空选择即查全部。后端 `logStatus` 支持逗号分隔多选（OR 谓词），单值调用完全兼容。

| 编号 | 验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | 后端 `logStatus` 接受逗号分隔多选：逐项白名单校验（ALL/ABNORMAL/SUCCESS/RUNNING/STUCK/EXECUTION_FAILED，FAILED 兼容归一 ABNORMAL），按白名单次序去重排序；含 ALL 归一为 [ALL]；空值沿用服务层默认 ABNORMAL；非法成员报错文案支持多选提示 | 单测 |
| AC-02 | 多选 SQL 谓词为各状态谓词 OR 连接（matching CASE 与 statusFilter 一致）；单值路径产物与改造前逐字节一致 | 单测 SQL 断言 |
| AC-03 | evidence `Definition.logStatus`（String 字段不变）存规范序逗号串（如 STUCK,EXECUTION_FAILED），hash 随查询值计算 | 单测 |
| AC-04 | 返回状态：所选状态与异常集合 {ABNORMAL,STUCK,EXECUTION_FAILED} 有交集且 total>0 → ERROR；文案状态名按「、」连接（如「区间卡死（回调丢失）、执行失败日志数」） | 单测 |
| AC-05 | 前端状态下拉多选（collapse-tags）、默认选中 STUCK+EXECUTION_FAILED、清空即查全部（不传 logStatus，后端默认 ALL） | 构建通过 + 待部署走查 |

约束：不改 SQL 登记模板；不动 MQ 筛选；不动 Controller/Service 方法签名（逗号串经既有 String 参数传入）；不动 `collect` 仪表盘默认口径。

# Design（同文件承载）

- 后端 `XxlJobMonitorCollector`：`normalizeLogStatuses(String)→List<String>` 承载逗号拆分/校验/白名单序去重；`statusPredicate(List,…)` OR 拼接单态谓词（原 switch 改名 `singleStatusPredicate`）；`logStatusFilterSql` 增 List 重载（String 版委托归一）；`collectLogs` 的 matching、evidence（`String.join(",", statuses)`）、返回状态与文案按多选适配。回滚：还原该文件。
- 前端 `OpsMonitorView`：`logFilters.logStatus` 改为四态数组，`resetLogFilters` 默认 `['STUCK','EXECUTION_FAILED']`；el-select 加 `multiple collapse-tags collapse-tags-tooltip`（宽 240px）；`loadLogs` 传 `join(',')`（空数组不传）。`api/ops.ts` 两处 `logStatus` 类型放宽为 `string`（逗号分隔多选）。回滚：还原两个文件。
- 无 DDL、无数据变更。

# Tasks

| 任务 | 状态 |
| --- | --- |
| T-01 后端多选归一/OR 谓词/matching/evidence/组合文案 | 已完成 |
| T-02 前端多选下拉 + 默认 STUCK+EXECUTION_FAILED + 类型放宽 | 已完成 |
| T-03 验证（后端定向单测 + npm build）+ 归档 | 已完成 |

## 实施证据（2026-11-22）

- 后端：`XxlJobMonitorCollector` 新增 `normalizeLogStatuses`（逗号拆分、trim、大写、逐项白名单校验、FAILED→ABNORMAL、含 ALL 归一 [ALL]、空值默认 ABNORMAL、白名单序去重），`statusPredicate` 改多状态 OR 拼接（单态 switch 移入 `singleStatusPredicate`），`logStatusFilterSql` 增 List 重载，`logStatisticsSql` matching 与 `collectLogs` 的 evidence（logStatus 存规范序逗号串）/返回状态（异常交集且 total>0→ERROR）/文案（名称「、」连接）按多选适配；单值 SQL 产物与改造前一致。
- 前端：`OpsMonitorView` 状态下拉改多选（collapse-tags），`resetLogFilters` 默认 `['STUCK','EXECUTION_FAILED']`，`loadLogs` 空数组不传 logStatus（后端默认 ALL）；`api/ops.ts` `fetchXxlJobDetail`/`fetchXxlJobLogs` 的 `logStatus` 放宽为 `string`（注释说明）。
- 验证：`mvn -pl workhub-service -am test -Dtest='XxlJobMonitorCollectorTest,OpsMonitorServiceTest'` 45/45 通过（`XxlJobMonitorCollectorTest` 32/32，含新增 `logStatusFilterSupportsCommaSeparatedMultiSelect`：多选 OR 谓词、规范序证据 `STUCK,EXECUTION_FAILED`、去重与大小写归一、`RUNNING,SUCCESS`→UP 组合文案、`ALL,STUCK`→ALL 归一、`STUCK,BOGUS` 拒绝）；workhub-web `npm run build` 通过。
- 待部署走查：打开执行日志弹框默认选中卡死+执行失败、多选组合查询、清空后查全部。

确认记录：用户 2026-11-22 提出多选与默认选中指示；实施计划（规格）经用户批准后实施。多选口径沿用 REQ-a58c7e21 五态定义。
