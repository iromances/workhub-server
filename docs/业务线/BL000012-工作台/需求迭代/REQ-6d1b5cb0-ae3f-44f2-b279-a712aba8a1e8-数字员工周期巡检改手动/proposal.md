# Proposal：数字员工周期巡检改手动

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-6d1b5cb0-ae3f-44f2-b279-a712aba8a1e8 |
| 需求名称 | 数字员工周期巡检改手动 |
| 涉及仓库与功能域 | workhub-server（workhub-bootstrap 数据订正迁移；无前后端代码改动） |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-6d1b5cb0-ae3f-44f2-b279-a712aba8a1e8-数字员工周期巡检改手动/ |
| 版本/状态 | v1.0 已确认（用户 2026-11-22 会话指示「目前的数字员工周期巡检的，统一改为手动」；实施计划经用户批准） |
| 目标环境/分支 | codex/development-20261102；生效目标为生产运行库 workhub |

## 1. Why

生产库现有 5 个数字员工 CRON 周期巡检任务每小时自动调度，消耗 AI 通道与执行资源；用户要求巡检改为按需手动触发。产品已支持任务级「手动」模式编辑（PUT /api/agent-tasks/{id}/schedule）与手动运行（POST /api/agent-tasks/{id}/run，页面运行按钮），故本需求为存量数据订正，不涉及代码功能变更。

## 2. What 与 Done

存量 `schedule_mode='CRON'` 的数字员工任务统一订正为手动（MANUAL、cron 清空、停用调度、清 next_run_at），与页面「编辑任务→手动」写入口径一致；此后巡检仅经页面手动触发。

| 编号 | 验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | 迁移为单条受限 UPDATE（仅 pm_agent_task，WHERE schedule_mode='CRON'），幂等，无 DELETE/DROP | 迁移解析测试 |
| AC-02 | 订正后生产库 `SELECT ... WHERE schedule_mode='CRON'` 返回 0 行（基线 5 行）；任务不再被 findDueTaskIds 自动派发 | 只读 SQL 复查 |
| AC-03 | 任务在页面显示「手动」，运行按钮可触发（POST /agent-tasks/{id}/run 不受影响） | 页面走查（待部署） |

### 基线（2026-11-22 生产只读实查，workhub.pm_agent_task）

| 任务 ID | 数字员工 | cron | 状态 |
| --- | --- | --- | --- |
| 2181445e-dd1c-11f1-8721-005056949076 | 保费分期监测巡检助手 | 0 0 0/1 * * * | CRON/启用 |
| 12bd560d-6711-4ae8-90e3-4df104e7c8f8 | 叉车运维员 | 0 26 * * * * | CRON/启用 |
| 9a24157d-d490-4536-b835-6c6d76d3a20f | 新消费分期监测巡检助手（ZCode） | 0 38 * * * * | CRON/启用 |
| e2d82c44-e64a-4835-a0a5-0354a145874b | 智能柜监测巡检助手（ZCode） | 0 14 * * * * | CRON/启用 |
| ad59acd8-e377-4382-9038-29469860aa1a | 消费分期监测巡检助手（ZCode） | 0 10 * * * * | CRON/启用 |

另有 4 个 MANUAL 任务（研发工程师×3、智能柜旧任务）不受影响。

# Design（同文件承载）

- 交付物为 Flyway 数据订正迁移 `V20261122_110000__agent_patrol_cron_tasks_to_manual.sql`（先例 V20261110_130000）：`UPDATE pm_agent_task SET schedule_mode='MANUAL', cron_expression=NULL, schedule_enabled=0, next_run_at=NULL, version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE schedule_mode='CRON'`。调度闸为 `schedule_enabled=1 AND next_run_at<=now AND agent enabled`（AgentMapper.findDueTaskIds），订正后不再自动派发；zone_id/misfire_policy 保留；不写 pm_agent_event 审计（先例一致），轨迹记录于迁移注释与本归档。
- 立即生效途径：AI 侧生产库通道只读，无法直接写库；已向用户提供与迁移等价的手工 SQL（先手工执行则部署时迁移命中 0 行，两者兼容）。范围仅存量任务，不限制此后新建 CRON 任务；新建任务默认模式不变。
- 回滚：`UPDATE pm_agent_task SET schedule_mode='MANUAL'...` 反向需按基线表逐任务恢复 cron 与启用位（版本号顺延），无表结构变更。

# Tasks

| 任务 | 状态 |
| --- | --- |
| T-01 数据订正迁移 + 解析测试 | 已完成 |
| T-02 验证与归档 | 已完成 |

## 实施证据（2026-11-22）

- 迁移：`workhub-bootstrap/src/main/resources/db/schema/mysql/V20261122_110000__agent_patrol_cron_tasks_to_manual.sql`（单条受限 UPDATE，幂等，文件头注明 REQ 与口径）。
- 测试：新增 `AgentPatrolManualMigrationTest`（Flyway MySQLParser 解析断言：恰好 1 条语句、六字段赋值、WHERE 限定 CRON、无 DELETE/DROP；解析器前置行注释并入语句已按先例口径断言语句体）；`mvn -pl workhub-bootstrap -am test` 与 `AgentMonitorEvidenceMigrationTest` 回归 2/2 通过。
- 生产核对：执行前基线见上表（5 个 CRON）；执行后待查 `SELECT id FROM workhub.pm_agent_task WHERE schedule_mode='CRON'` 应为 0 行。
- 待部署/待执行：生产库订正（随部署迁移或用户手工 SQL）；页面走查任务显示手动与运行按钮。

确认记录：用户 2026-11-22 提出统一改手动指示；实施计划（规格）经用户批准后实施。
