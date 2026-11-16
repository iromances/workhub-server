-- 澄清与任务评估运行记录改造（REQ-e810ed05-b9bd-4def-8c23-f94c59754d03）后续变更：
-- 澄清运行记录增加开始/结束时间，前端按轮次展示状态、开始时间、结束时间与失败原因。
-- started_at：首次进入 RUNNING 的时间；finished_at：首次到达 DRAFT/CONFIRMED/FAILED 的时间；
-- 两者由 Mapper 的 CASE 首次赋值维护，存量行按 created_at/updated_at 近似回填，可重复执行。

ALTER TABLE pm_intake_clarification_analysis
    ADD COLUMN started_at DATETIME NULL COMMENT '首次进入RUNNING时间' AFTER analysis_message,
    ADD COLUMN finished_at DATETIME NULL COMMENT '首次到达DRAFT/CONFIRMED/FAILED时间' AFTER started_at;

UPDATE pm_intake_clarification_analysis
SET started_at = COALESCE(started_at, created_at),
    finished_at = COALESCE(finished_at, updated_at)
WHERE analysis_status IN ('DRAFT', 'CONFIRMED', 'FAILED');
