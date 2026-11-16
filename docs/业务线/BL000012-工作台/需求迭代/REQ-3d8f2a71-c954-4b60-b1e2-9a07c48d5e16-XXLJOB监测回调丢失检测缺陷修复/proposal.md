# Proposal：XXL-JOB 监测回调丢失检测缺陷修复

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-3d8f2a71-c954-4b60-b1e2-9a07c48d5e16 |
| 需求名称 | XXL-JOB 监测回调丢失检测缺陷修复 |
| 涉及仓库与功能域 | workhub-server：`workhub-service` ops 监测（XxlJobMonitorCollector 及关联模型/配置/测试）、`workhub-dao`（OpsMonitorMapper）、`workhub-model`（OpsMonitorEntity/OpsMonitorSaveRequest/XxlJobDashboardResponse） |
| 所属项目或业务线 | WorkHub 平台自身（业务线 BL000012-工作台） |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-3d8f2a71-c954-4b60-b1e2-9a07c48d5e16-XXLJOB监测回调丢失检测缺陷修复/` |
| 版本/状态 | v2 规格已确认，实施中（v1 为 2026-11-13 起草的"CALLBACK_LOST 单点口径"草案，未实施；v2 按用户 2026-11-14 会话定稿的白名单五态口径重写） |
| 目标系统 | WorkHub Server |
| 原始需求来源及版本 | 2026-11-13 用户会话："xxljob 监测似乎有缺陷，生产上 lease 和 ol 的 xxljob 从 11 号开始似乎都有问题"；2026-11-14 用户会话定稿判定口径："调度成功是 200，其他判定调度异常；执行成功是 200，0 执行中，其他执行码异常；执行中/STUCK 按时间阈值区分"。当日生产事故：11-11 18:23 切换 2.5.0 后 lease-saps/ol-saps/lease-finance 约 2,844 次调度回调丢失，监测全程 UP |
| 目标环境/分支 | workhub-server 当前开发分支（codex/development-20261102） |
| 负责人/评审人 | 用户 |

## 1. Why：背景与问题

2026-11-11 18:23 生产 XXL-JOB 切换 2.5.0 后，lease-saps / ol-saps / lease-finance 三个执行器所有任务"调度成功（trigger_code=200）但执行结果永不回写（handle_code 恒为 0）"，持续约 41.5 小时共约 2,844 次触发。WorkHub XXL-JOB 监测（BL000005、BL000008 两条）全程显示 UP"区间内无失败日志"，未产生任何告警。

代码根因（`workhub-service/src/main/java/cn/aslight/workhub/service/ops/XxlJobMonitorCollector.java`）：

1. 失败谓词按"枚举失败值"而非"白名单成功值"：`handle_code <> 200 AND NOT (trigger_code IN (0,200) AND handle_code = 0)`——`handle_code=0` 被永久视为"执行中"，无时限。本次事故卡死行（含"handle_time 已写+handle_msg 空串"的 2.5.0 半写形态）全部被该谓词过滤。
2. 调度侧只认 `trigger_code=500` 为失败：`trigger_code=0` 残留（admin 触发中途崩溃）、NULL、未来新码均不报。
3. 无"没跑"检测：job 完全不触发时无日志行，0 失败 = 绿。
4. 状态判定视角受 `executor_app_name` 白名单限制：lease-finance（事故中至今未恢复）不在两条监测的执行器名单内，即使谓词正确也不会被发现。
5. `xxl-job` 官方语义里 handle_code 只有 200/500/0，但版本升级/兼容故障会造出枚举之外的中间态（本次已出现三种形态：0+handle_time已写+msg空串、0+全NULL、500），判定必须与具体魔法值解耦。

## 2. What：目标与范围

### 本期目标

1. **判定口径改为白名单五态 + 静默维度**（详见 design 第 1 节）：唯一可信成功是 `handle_code=200`（调度侧同理 `trigger_code=200`）；一切非成功值按语义归入异常或按时间阈值区分"执行中/卡死"。回放本次事故：11-11 18:23 故障开始，最迟 20:23（默认阈值 2 小时）状态转 ERROR。
2. **静默检测**：`trigger_status=1` 且 `trigger_next_time` 落后超过宽限期（10 分钟）计为静默任务，兜住"调度停摆、连日志行都没有"的场景。
3. **全库聚合视角**：健康口径（五态计数、静默计数、job/执行器统计）按监测库全量聚合，不再受 `executor_app_name` 名单限制；名单只用于失败明细列表聚焦。
4. **卡死阈值按监测项可配置**：`ops_monitor_config` 新增 `stale_threshold_minutes`（NULL 用默认 2 小时；需要更灵敏的监测项可单独调小，长跑任务可调大）。
5. **日志状态筛选语义升级**：`FAILED` 归一为 `ABNORMAL`（显式失败 + 卡死 + 调度异常）；`ALL` 保留；行分类新增 `STUCK`（中文名"卡死（回调丢失）"）。
6. 单元测试覆盖新谓词、五态分类、静默检测、阈值边界与全库聚合。

### 本期交付范围

1. `workhub-model`：`OpsMonitorEntity`/`OpsMonitorSaveRequest`（staleThresholdMinutes）、`XxlJobDashboardResponse`（新增 scheduleFailedCount/executionFailedCount/stuckCount/silentJobCount）。
2. `workhub-dao`：`OpsMonitorMapper` 增列读写。
3. `workhub-service`：`OpsMonitorSchemaInitializer`（幂等加列）、`OpsMonitorService`（保存映射）、`XxlJobMonitorCollector`（核心口径重写）、测试。
4. 文档：迭代日志与本需求 tasks 状态与证据。

### 明确不做

1. 不改 MQ 监测、Accounting 监测与 ELK 告警链路。
2. 不改 `collectSummary`（report 报表口径，当前无生产调用方）：保持报表展示语义，新增字段填 0 并在 design 记录限制。
3. 不改前端页面（新增响应字段后端先行，前端展示另行需求）。
4. 不在本需求内为旧中心库 `lease_xxl_job` 补监测配置（运维配置动作，随 tasks 配套项跟踪）。
5. 状态维持 7 天滚动窗口语义不变（事故历史卡死记录会使状态 ERROR 持续至窗口滚出，如实反映）。

### 约束与依赖

- 卡死阈值默认 2 小时（120 分钟，2026-11-14 用户确认由定稿的 30 分钟上调）：覆盖常见分钟级任务并避免把近 2 小时的真实长任务（如 ol-order 里易设备同步）误标为卡死；代价是回调丢失最长延迟 2 小时告警，需要更灵敏的业务可对单个监测项调小。
- 阈值截止时间由应用侧时钟生成字面量拼入 SQL（与既有日期字面量方式一致），不依赖数据库时钟与 INTERVAL 方言。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 新口径 SQL 对生产 `lease_xxl_job_250` 只读回放：11-11~11-13 卡死行计为 STUCK、调度失败行计为 SCHEDULE_FAILED、`handle_code=500` 计为 EXECUTION_FAILED，且 lease-finance 当前卡死计入（全库聚合） | 只读 SQL vs 代码生成 SQL 一致性核对 | 用户 |
| AC-02 | 阈值内执行中（handle_code=0/NULL 且距今 ≤ 阈值）不判异常，状态 UP | 单测 | 用户 |
| AC-03 | 显式失败（trigger_code=500 / handle_code=500）口径不回归，EXECUTION_FAILED/SCHEDULE_FAILED 分类保留 | 既有单测更新后全绿 | 用户 |
| AC-04 | 静默任务（trigger_status=1 且 trigger_next_time 落后 >10 分钟）计入状态判定与 message | 单测 | 用户 |
| AC-05 | 配置 executor 名单的监测，健康统计 SQL 不含执行器过滤（全库聚合），明细 SQL 仍按名单聚焦 | 单测断言 SQL 形状 | 用户 |
| AC-06 | `stale_threshold_minutes` 可保存、读取、生效（SQL 截止字面量随配置变化）；非法值（<1）拒绝保存 | 单测 | 用户 |
| AC-07 | 响应新增 executionFailedCount/stuckCount/silentJobCount（调度失败并入执行失败，2026-11-14 用户确认）；logStatus 接受 ALL/ABNORMAL/FAILED（后两者同谓词） | 单测 | 用户 |
| AC-08 | 全量构建与 `workhub-service` 单测通过 | `mvn test` | 用户 |

## 4. 与 v1 的差异（2026-11-14 会话定稿）

| 维度 | v1 草案 | v2 定稿 |
| --- | --- | --- |
| 判定原则 | 旧谓词 OR 回调丢失（保留 trigger IN (0,200) 排除） | 白名单四态：trigger≠200 即执行失败（含调度失败，2026-11-14 并入）；handle 只有 200 成功、0/NULL 未决、其他执行失败 |
| 状态命名 | CALLBACK_LOST | STUCK（卡死（回调丢失）），语义同 |
| 静默检测 | 无 | 新增（trigger_next_time 落后 >10 分钟） |
| 聚合视角 | 沿用 executor 过滤（漏检记为运维项） | 健康口径全库聚合，名单仅聚焦明细（代码内落实） |
| 阈值 | 常量 60 分钟，明确不做配置化 | 默认 2 小时（定稿 30 分钟，2026-11-14 用户改为 2 小时），监测项可配置（新增列） |
| 日志筛选 | FAILED 扩展含回调丢失 | FAILED/ABNORMAL 同谓词 = 显式失败+卡死+调度异常 |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
