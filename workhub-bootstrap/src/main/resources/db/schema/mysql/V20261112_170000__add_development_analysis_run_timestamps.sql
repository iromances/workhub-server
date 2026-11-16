-- 研发任务（任务评估）运行记录补齐轮次时间（REQ-e810ed05 后续变更④，与澄清同款）：
-- started_at 首次进入 RUNNING；finished_at 首次到达 DRAFT/CONFIRMED/FAILED；
-- 由 Mapper CASE 首次赋值维护，存量行按 created_at/updated_at 近似回填，可重复执行。

ALTER TABLE pm_intake_development_analysis
    ADD COLUMN started_at DATETIME NULL COMMENT '首次进入RUNNING时间' AFTER analysis_message,
    ADD COLUMN finished_at DATETIME NULL COMMENT '首次到达DRAFT/CONFIRMED/FAILED时间' AFTER started_at;

UPDATE pm_intake_development_analysis
SET started_at = COALESCE(started_at, created_at),
    finished_at = COALESCE(finished_at, updated_at)
WHERE analysis_status IN ('DRAFT', 'CONFIRMED', 'FAILED');
