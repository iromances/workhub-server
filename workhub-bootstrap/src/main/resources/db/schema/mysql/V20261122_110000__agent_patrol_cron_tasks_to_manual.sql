-- REQ-6d1b5cb0：数字员工周期巡检任务统一改为手动触发（用户 2026-11-22 指示）。
-- 存量 CRON 任务（基线 5 个：保费分期/叉车/新消费分期/智能柜/消费分期的监测巡检助手）按页面
-- 「编辑任务→手动」同口径订正：MANUAL、cron 清空、停用调度、清 next_run_at；zone_id/misfire_policy 保留。
-- 调度闸为 schedule_enabled=1 AND next_run_at<=now（AgentMapper.findDueTaskIds），订正后不再自动派发；
-- 手动运行入口 POST /agent-tasks/{id}/run 不受影响。幂等：重复执行命中 0 行。
-- 不写 pm_agent_event 审计（与 V20261110_130000 数据订正先例一致），变更轨迹见本注释与需求归档。
UPDATE pm_agent_task
SET schedule_mode = 'MANUAL',
    cron_expression = NULL,
    schedule_enabled = 0,
    next_run_at = NULL,
    version = version + 1,
    updated_at = UTC_TIMESTAMP(6)
WHERE schedule_mode = 'CRON';
