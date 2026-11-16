-- 澄清与任务评估运行记录改造（REQ-e810ed05-b9bd-4def-8c23-f94c59754d03）存量数据修复：
-- 历史上后台分析任务静默丢失（线程死亡/提交失败/服务重启）导致 PENDING/RUNNING 永挂，
-- 且旧实现单行覆盖使前端重试按钮永久禁用。改造后每轮分析独立成行，
-- 此处把存量超时未完成的运行记录置为 FAILED，恢复可重试入口。
-- 仅命中超过 30 分钟（与 EXECUTION_STALE_TIMEOUT 口径一致）的 PENDING/RUNNING 行，可重复执行。

UPDATE pm_intake_clarification_analysis
SET analysis_status = 'FAILED',
    analysis_message = '后台执行中断（历史遗留），已由数据修复置为失败，可重新分析',
    updated_by = 'migration',
    updated_at = CURRENT_TIMESTAMP
WHERE analysis_status IN ('PENDING', 'RUNNING')
  AND updated_at < NOW() - INTERVAL 30 MINUTE;

UPDATE pm_intake_development_analysis
SET analysis_status = 'FAILED',
    analysis_message = '后台执行中断（历史遗留），已由数据修复置为失败，可重新分析',
    updated_by = 'migration',
    updated_at = CURRENT_TIMESTAMP
WHERE analysis_status IN ('PENDING', 'RUNNING')
  AND updated_at < NOW() - INTERVAL 30 MINUTE;
