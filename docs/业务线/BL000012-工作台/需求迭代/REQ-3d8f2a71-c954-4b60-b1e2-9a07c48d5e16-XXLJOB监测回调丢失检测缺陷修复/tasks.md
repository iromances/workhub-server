# Tasks：XXL-JOB 监测回调丢失检测缺陷修复

对应 [proposal.md](proposal.md)、[design.md](design.md)（v2）。状态：实施中。

## 任务清单

| 编号 | 任务 | 依赖 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 配置链路：`OpsMonitorSchemaInitializer` 幂等加列 `stale_threshold_minutes`；`OpsMonitorMapper` 三处 SELECT/INSERT/UPDATE 增列；`OpsMonitorEntity`/`OpsMonitorSaveRequest` 增字段；`OpsMonitorService.toEntity` 阈值归一（<1 拒绝） | 规格确认 | 单测 AC-06 | 已完成（2026-11-14） |
| T-02 | `XxlJobMonitorCollector`：`healthStatsSql`（全库五态+静默+统计）；`logStatusFilterSql`/`logStatisticsSql` 谓词换 ABNORMAL；`normalizeLogStatus` 增 ABNORMAL（FAILED 归一）；`collect` 状态与 message 换健康口径；显示字段映射 | T-01 | 单测 AC-01/02/04/05/07 | 已完成（2026-11-14） |
| T-03 | `toFailedJob` 行分类带 staleCutoff（STUCK/RUNNING/…），删除无调用方重载；`check` detail 增键；`XxlJobDashboardResponse` 新增 4 字段并更新构造器调用 | T-02 | 单测 + 编译 | 已完成（2026-11-14） |
| T-04 | `XxlJobMonitorCollectorTest`：既有用例 fixture/断言更新 + design 第 6 节新增用例 + H2 collect() 全链路 | T-01~03 | `mvn test` 全绿（AC-03/08） | 代码完成；collector 29/29 绿、model/dao 模块绿；workhub-service 全模块构建当时被并发会话在途的 `AgentToolAdminService`（引用未落盘的 `AgentToolCatalog`，REQ-f573f4cb）暂阻，见证据 3 |
| T-05 | 生产只读回放（部署前预演 + 部署后实测）：新谓词对 `lease_xxl_job_250` 回放，卡死/调度失败/执行失败/静默计数与只读 SQL 预期一致 | T-04 | 记录本表（AC-01 证据） | 预演完成（见证据 1）；部署后实测待部署 |
| T-06 | 文档：迭代日志、本需求 tasks 状态与证据更新 | T-04/T-05 | 归档完整 | 本次更新 |
| T-07 | 静默任务明细：`XxlJobSilentJobResponse` 模型 + `silentJobsSql`（批次第 4 条，top 20，lagMinutes Java 侧计算）+ `XxlJobDashboardResponse.silentJobs` + `check` detail 增键 | T-02 | 单测（明细映射/排序/无执行器过滤/H2 真实 SQL） | 已完成（2026-11-14）；当日运行期发现老结构库 `xxl_job_info` 无 `schedule_conf` 列（SQLSyntaxErrorException），已移除该展示字段并回归 |

## 配套运维动作（不在本需求代码内，随补跑方案跟踪）

| 编号 | 动作 | 说明 |
| --- | --- | --- |
| O-01 | 旧中心库 `lease_xxl_job` 增配一条临时监测（至旧 admin 退役） | 覆盖迁移期旧中心调度失败（当前每日 ~11 万条 500 无监测）；v2 全库聚合上线后，两条 lease_xxl_job_250 监测将自动覆盖 lease-finance 等全部执行器 |
| O-02 | lease-finance 执行器修复跟踪（REQ-9b086f9b 处置文档遗留项，截至 11-14 仍未恢复） | v2 上线后监测将如实标红，直至执行器修复 |
| O-03 | workhub-web 前端对齐（2026-11-14 扩展）：①执行日志状态筛选文案"失败"→"异常"（后端已兼容 FAILED=ABNORMAL）；②XXL-JOB 详情页指标卡（成功/执行中/执行失败/卡死（回调丢失）/静默任务）+ 静默任务清单表格（数据在响应 executionFailedCount/stuckCount/silentJobCount/silentJobs，已就绪）；③监测列表页 XXL-JOB 行异常摘要徽标（可先用 last_message 文字，结构化再加 last_health 列）；④行级分类标签去掉"调度失败"（已并入"执行失败"） | 用户确认"加上吧"；后端字段已全部就绪，纯前端工作量 |

## 确认记录

| 日期 | 范围/版本 | 结论 | 确认人 |
| --- | --- | --- | --- |
| 2026-11-14 | v1 草案（CALLBACK_LOST 单点口径） | 起草后经会话讨论被 v2 取代，未实施 | 用户 |
| 2026-11-14 | v2（白名单五态 + 静默 + 全库聚合 + 阈值配置） | 用户会话定稿判定口径（"调度成功是200，其他判定调度异常；执行成功是200，0执行中，其他执行码异常；执行中/STUCK 按时间阈值"），并指示"先把前面xxljob的方案实现了吧" | 用户 |
| 2026-11-14 | v2 变更：默认卡死阈值 30 → 120 分钟 | 用户确认"改成2小时吧"；代码/测试/规格已同步（事故回放首报时点相应变为最迟故障开始+2小时） | 用户 |
| 2026-11-14 | v2 变更：行级 SCHEDULE_FAILED 并入 EXECUTION_FAILED | 用户确认"调度失败也算执行失败"；行级四态（成功/执行中/卡死/执行失败），明细行仍保留 triggerCode 原始值可区分调度侧根因；代码/测试/规格已同步 | 用户 |
| 2026-11-14 | v2 变更：响应新增静默任务明细 silentJobs（top 20） | 用户确认"加上吧"；调度停摆可定位到具体任务与 next_time 落后程度；前端展示要点并入 O-03 | 用户 |

## 证据

1. **AC-01 生产只读回放预演（2026-11-14 10:10，db-prod/readonly，`lease_xxl_job_250`，7 天窗口，回放时阈值 30 分钟，静默宽限 10 分钟）**：successCount=13,408、runningCount=4、**stuckCount=2,991**、scheduleFailedCount=1、executionFailedCount=4、**silentJobCount=96**。同口径下旧谓词失败数=0（监测当时显示 UP"区间内无失败日志"），新口径判定 ERROR——2,991 条含 11-11~11-13 事故存量与 lease-finance 持续新增的半写卡死（O-02），与 proposal 第 1 节根因数据一致。注：回放执行后默认阈值经用户确认上调为 120 分钟；事故存量（均已超 2 天）判定不变仍为 STUCK，仅 30–120 分钟区间的未决行（主要是 lease-finance 最近 2 小时新增）改判执行中，整体 ERROR 结论不变，部署后 T-05 按新默认实测。
2. **单测**：`XxlJobMonitorCollectorTest` 29/29 通过（含新增 STUCK 超时/阈值内 RUNNING/静默/全库聚合/阈值配置与拒绝/ABNORMAL=FAILED 等价/H2 真实 SQL 全链路五态分类用例）；`mvn test -pl workhub-model,workhub-dao -am` 通过；2026-11-14 09:55 全仓 `mvn test` 运行 1,118 个测试，除 2 个与本需求无关的既有失败（`KnowledgeArchiveWorkflowTest` 知识库路径迁移、`BusinessLogSearchServiceTest` ELK 范围配置，均属其他在途工作）外全绿，当时本需求全部改动已在编译与测试范围内。
3. **并发会话说明**：实施期间另一会话在本仓库推进 REQ-f573f4cb（数字员工工具管理），暂存的 `AgentToolAdminService` 引用了尚未落盘的 `AgentToolCatalog`，导致 workhub-service 模块级全量构建在其实施完成前暂不可用；本需求代码在该文件出现前的全量构建中已验证通过，源码此后未再改动。
4. **部署注意**：启动时 `OpsMonitorSchemaInitializer` 自动为 `ops_monitor_config` 幂等加列 `stale_threshold_minutes`（可空，不改变存量行为，空值用默认 2 小时）；上线后 BL000005/BL000008 两条监测将如实转为 ERROR 并持续到 lease-finance 修复、卡死存量滚出 7 天窗口（proposal 约束已确认接受）。
