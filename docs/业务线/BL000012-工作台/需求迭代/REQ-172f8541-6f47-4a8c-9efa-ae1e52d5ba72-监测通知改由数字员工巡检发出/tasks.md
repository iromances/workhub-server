# Tasks：监测通知改由数字员工巡检发出

## 1. 元信息

- 需求编码：REQ-172f8541-6f47-4a8c-9efa-ae1e52d5ba72；目录同名唯一。
- 涉及仓库与功能域：workhub-server（ops 三处站内信删除、AgentRunNotifier 新增、协调器挂钩、测试）。
- Proposal/Design 版本：v1；状态：已确认（2026-11-12 用户追加；关键取舍问题未答，按推荐默认执行：三处全删 + 仅 P0/P1 时通知，后续可加任务级开关）。
- 负责人/评审人：用户。

## 2. 任务与验证（2026-11-12 实施完成）

| 任务 | 结果 |
| --- | --- |
| 删除 XXL 失败告警站内信 | `XxlJobFailureAlertService` 仅保留扫描与状态落库（巡检读取 last_status） |
| 删除 MQ 失败告警站内信 | `MqFailureAlertService` 同上 |
| 删除 ELK 采集故障站内信 | `SystemAlertCollectionStore` 移除通知链路（批次累计/收件人/NotificationBatch），采集与回执不变；财务监测本无通知 |
| 新增巡检通知 | `AgentRunNotifier`：COMPLETED 且报告含 P0/P1 → 按任务范围业务线成员发 `AGENT_PATROL` 站内信（dedupe=AGENT_RUN:{runId}，标题含 Agent 名与目标，正文报告前 1000 字；通知失败仅记日志）；挂钩 `AgentExecutionCoordinator.execute` |
| 测试 | 新增 `AgentRunNotifierTest` 3 项（命中通知/无标记与非完成不发/通知异常不影响运行）；改写 XxlJob/MqFailureAlertServiceTest、SystemAlertCollectionStoreTest（通知行数断言改恒 0）；相关 37 项全绿 |
| 部署 | 16:36 重启生效；巡检任务恢复每 30 分钟周期（17:00 起首轮） |

全量 `mvn test` 1049 项：本需求范围 0 失败；既有无关失败仍为 KnowledgeArchiveWorkflowTest 与 BusinessLogSearchServiceTest（用户在途改动），另见 CodexCliPromptConsistencyTest（澄清域并行在途，非本需求）。
