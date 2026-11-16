# Proposal：XXL-JOB执行日志状态筛选五态对齐

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-a58c7e21-9d4b-4f6e-8b2c-7f3d1a9e5c04 |
| 需求名称 | XXL-JOB执行日志状态筛选五态对齐 |
| 涉及仓库与功能域 | workhub-server（XxlJobMonitorCollector）；workhub-web（OpsMonitorView 执行日志筛选） |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-a58c7e21-9d4b-4f6e-8b2c-7f3d1a9e5c04-XXLJOB执行日志状态筛选五态对齐/ |
| 版本/状态 | v1.0（用户 2026-11-14 会话指示"状态查询条件少了，和列表中的状态对齐"） |
| 目标环境/分支 | codex/development-20261102（后端）/ 对应前端分支 |

## 1. Why

REQ-3d8f2a71 已把 XXL-JOB 监测重构为白名单五态口径（执行成功/执行中/卡死（回调丢失）/执行失败（含调度失败）），执行日志表格行状态列即按 failureType 四态展示；但日志查询的状态筛选条件只有「全部/失败(=异常)」两档，无法按行状态定位（如只看卡死或只看执行成功）。

## 2. What 与 Done

筛选选项与日志表格行状态（failureTypeName）对齐：**全部 / 异常（默认，执行失败+卡死+调度失败）/ 执行成功 / 执行中 / 卡死（回调丢失）/ 执行失败（含调度失败）**。

| 编号 | 验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | logStatus 支持 ALL/ABNORMAL（FAILED 兼容归一）/SUCCESS/RUNNING/STUCK/EXECUTION_FAILED；非法值报错文案更新 | 单测 |
| AC-02 | 各状态筛选的 SQL 谓词与五态分类口径一致（SUCCESS=trigger200+handle200；RUNNING=未决且新于卡死阈值线；STUCK=未决且早于阈值线；EXECUTION_FAILED=trigger≠200/NULL 或 handle∉(0,200)；ABNORMAL=STUCK∪EXECUTION_FAILED） | 单测 SQL 断言 |
| AC-03 | 筛选异常态（ABNORMAL/STUCK/EXECUTION_FAILED）有结果时返回状态 ERROR，文案按状态名输出；SUCCESS/RUNNING/ALL 维持 UP 口径 | 单测 |
| AC-04 | 前端筛选下拉六项与 failureTypeName 文案对齐；默认值仍为异常 | 页面走查 |

约束：不改 SQL 登记模板（statusFilter/matching 本就是代码内白名单片段参数）；不动 MQ 筛选；不动详情页失败明细默认口径。

# Design（同文件承载）

- `XxlJobMonitorCollector`：新增 `statusPredicate(status, staleCutoff)` 统一产出各状态谓词片段；`logStatusFilterSql` 与 `collectLogs` 的 `matching` 改走该方法；`normalizeLogStatus` 扩展五态；返回状态与文案按筛选状态生成（异常集合 {ABNORMAL, STUCK, EXECUTION_FAILED} 有行→ERROR）。
- 前端：`OpsMonitorView` 执行日志筛选下拉扩为六项（`logStatus` 类型扩展），`api/ops.ts` 两处类型同步；默认 ABNORMAL（原 FAILED 值改传 ABNORMAL，后端对 FAILED 保持兼容）。
- 回滚：还原两个文件；无 DDL、无数据变更。

# Tasks

| 任务 | 状态 |
| --- | --- |
| T-01 后端五态筛选（normalizeLogStatus/statusPredicate/logStatusFilterSql/matching/状态文案）+ 单测 | 已完成 |
| T-02 前端筛选下拉六项 + 类型扩展 | 已完成 |
| T-03 验证（ops 域单测 + npm build）+ 迭代日志 | 已完成 |

## 实施证据（2026-11-14）

- 后端：`XxlJobMonitorCollector` 新增 `statusPredicate`（SUCCESS=trigger200+handle200；RUNNING=未决且新于阈值线或时间 NULL；STUCK=未决且早于阈值线；EXECUTION_FAILED=调度失败∪handle∉(0,200)；ABNORMAL=STUCK∪EXECUTION_FAILED，与行级 `failureType` 口径一致）；`normalizeLogStatus` 接受五态值（FAILED 兼容归一 ABNORMAL，非法值文案更新）；`collectLogs` 返回状态按异常集合 {ABNORMAL,STUCK,EXECUTION_FAILED} 有行→ERROR，文案统一"区间{状态名}日志数"；matching 片段走 statusPredicate。SQL 登记模板无需变更（statusFilter/matching 本就是代码内片段参数）。
- 前端（用户复核后定稿）：`OpsMonitorView` 执行日志筛选下拉**仅保留 4 种行类型**（执行成功/执行中/卡死（回调丢失）/执行失败（含调度失败）），「全部/异常」不作为选项；下拉可清空，清空即查全部（不传 logStatus，后端默认 ALL），打开日志默认清空；`api/ops.ts` 两处 `logStatus` 联合类型保留全集（API 兼容，前端仅传 4 态或空）。
- 同弹框复核调整：执行日志弹框业务线改显中文名（`businessLineNameOf`，回落编码，编码以小字并列）；弹框加宽至 92% 视口、表格 max-height 提至 640，任务描述/错误摘要列加宽，完整展示内容。
- 验证：`XxlJobMonitorCollectorTest` 31/31（新增 `logStatusFilterAlignsWithFiveStateRowClassification`：STUCK/SUCCESS/RUNNING/EXECUTION_FAILED 谓词断言、异常态 ERROR 与状态名文案、非法值拒绝）；`npm run build` 通过（三次迭代均验证）。
- 待部署走查：页面 4 态筛选与清空取数、弹框展示效果。

确认记录：用户 2026-11-14 提出并与行状态对齐的指示；五态口径沿用 REQ-3d8f2a71 已确认口径。
