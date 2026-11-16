# Tasks：业务监测 MQ 消息监测接入

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-17f620d9-b7b7-43e4-b37b-29cee0711f6b |
| 规格版本 | v1（proposal/design 同版本） |
| 确认结论 | 2026-11-12 用户批准实施计划，视为规格确认；口径三项（自动发现全部库、失败+发送积压、本期含告警）当日会话确认 |
| 确认人/日期 | 用户 / 2026-11-12 |
| 涉及仓库 | workhub-server（`codex/development-20261102`）、workhub-web（当前开发分支） |

## 任务编排

| # | 任务 | 仓库 | 依赖 | 计划结果 | 实际结果 |
| --- | --- | --- | --- | --- | --- |
| 1 | workhub-model 新增 `MqDatabaseStatsResponse`/`MqMessageResponse`/`MqDashboardResponse`/`MqMessagePageResponse`/`MqQueryEvidence` | workhub-server | - | 5 个 record 编译通过 | ✅ 完成；`MqDashboardResponse` 实施中补 `mqTopic`/`mqConsumerGroup`/`mqLagThreshold`/`remark` 4 个配置回显字段 |
| 2 | `MqMonitorCollector`：发现库、统计 UNION ALL、明细分页 + 证据、`check` | workhub-server | 1 | collector 单测覆盖口径与 SQL 形态 | ✅ 完成；`MqMonitorCollectorTest` 8 项通过（发现库校验/逐库聚合/状态分级/复合唯一键/过滤器/参数校验） |
| 3 | `OpsMonitorService` MQ 分发/CRUD 校验，移除“下一步接入”；`OpsMonitorController` 三个 MQ 只读接口 | workhub-server | 2 | Service/Controller 测试通过 | ✅ 完成；`OpsMonitorServiceTest` 13 项通过（新增 MQ 创建校验/检测分发/详情分发 4 项） |
| 4 | `MqFailureAlertService` + workhub-job `MqFailureMonitorJob` | workhub-server | 2 | 告警单测通过 | ✅ 完成；`MqFailureAlertServiceTest` 3 项通过 |
| 5 | workhub-web：types/api/MQ 标签页 | workhub-web | 3 | 构建通过，页面可用 | ✅ 完成；`npm run build`（vue-tsc + tsc + vite）通过 |
| 6 | 全量验证：`mvn test`、`npm run build`、真实链路抽查 | 双仓库 | 3,4,5 | 全部通过 | ✅ 见下方验证证据；workhub-service 全量 4 项失败均为工作区既有并行改动（详见图证据） |
| 7 | 交付：更新 `现状.md`/`迭代日志.md` | workhub-server | 6 | 文档反映实际进展 | ✅ 完成 |
| 8 | **v1.1**：读取端硬约束核实（maxRows=100/timeout=10s/整批中止）与大表实测 | workhub-server | 6 | 约束确认 | ✅ th_saps 单库统计实测 >60s 超时；th_payment 无二级索引 |
| 9 | **v1.1**：Collector 重构为逐库隔离执行 + 降级标注 + 归并分页 + `MAX_PAGE_SIZE=50` | workhub-server | 8 | 大库超时不阻断其余库 | ✅ 完成；`MqMonitorCollectorTest` 重写为 11 项全部通过 |
| 10 | **v1.1**：前端逐库错误展示、页大小选项收敛、详情预填关注过滤 | workhub-web | 9 | 构建通过 | ✅ 完成 |

## 验证证据

- 计划：`mvn test` 全量；workhub-web 构建；db-prod 只读抽查 collector SQL 口径。
- 实际（2026-11-12）：
  - 新增 24 项单测全部通过：`MqMonitorCollectorTest` 8、`MqFailureAlertServiceTest` 3、`OpsMonitorServiceTest` 13（含新增 4 项 MQ 用例）。
  - workhub-service 全量 1030 项：1026 通过，4 项失败均为工作区既有并行改动所致、与本需求无关——`KnowledgeArchiveWorkflowTest`（知识路径重构）、`PromptTemplateMigrationConsistencyTest`×2（提示词迁移进行中文件）、`BusinessLogSearchServiceTest`（ELK 范围，对应文件均为本次会话未触碰的既有 staged/modified 状态）。
  - workhub-controller 存在既有 `AgentToolControllerTest` 测试编译失败（agent-tool 文件处于 staged-Added 但工作区已删除状态），导致该模块测试未执行；主代码 `mvn compile` 通过，`OpsMonitorController.class`、`MqFailureMonitorJob.class` 生成。
  - workhub-web `npm run build` 通过。
  - 真实链路抽查（MCP readonly 对 db-prod）：发现库、逐库统计 UNION ALL、统计+分页合并语句三种 SQL 形态均执行成功且口径正确；assets_saps 7 天窗口发送 2610/成功 2602/积压 6、接收 2601/失败 0，合并语句统计与明细一致（matchingCount=6 贯穿每行），实际抓到 6 条每日对账通知消息卡在”发送中”状态——正是积压口径的目标信号。
  - **v1.1 大表实测**：`th_saps.message_sender_log` 单库 7 天窗口统计 >60 秒超时（索引核实：仅 status 前缀复合索引、status 范围 556 万行、create_time 无索引）；`th_payment`（3500 万行）无二级索引；同款单库 SQL 在 assets_saps 秒级返回——证明逐库隔离 + 降级为唯一可行路径，降级后 th_saps/th_payment 标注采集失败、其余库正常统计。
- 剩余待人工：应用部署后页面联调（检测按钮、详情弹窗、定时告警通知落地）；真实 `ops_monitor_config` MQ 配置创建走查。

## 剩余风险

- th_saps/th_payment 等大库因超时持续降级（面板 WARN 点名），对应库的失败/积压消息不参与明细与告警——需要业务侧评估为 `message_sender_log.create_time`（或 `(status, create_time)`）建索引后自动恢复；属跨仓库 DDL，不在本需求范围。
- 自动发现为实例级（db-prod 跨 8 个业务线共享），一个业务线的 MQ 监测会覆盖共享实例上全部含 `message_sender_log` 的库；同一环境建多条不同业务线的 MQ 监测会重复扫描同一批库，建议每环境一条。
- 自动发现依赖 MCP 目标账号具备 `information_schema` 读权限，账号权限变化会导致发现结果变化（证据中记录当日发现列表可追溯）。
- workhub-controller 测试因既有 agent-tool 文件状态无法执行，MQ 接口在 controller 层无自动化覆盖（逻辑已被 service 层用例覆盖）。
