-- 提测 AI 审核与 Jenkins 构建改为可选项（REQ-a71851db-1d1c-4f20-b99e-330950af6ad9 后续变更）：
-- 1) review_status 注释补充 SKIPPED（人工跳过 AI 审核直接确认提测的记录）；
-- 2) intake_id 由普通索引升级为唯一键：跳过审核时确认接口也会插入记录，
--    与触发审核并发可能产生重复行，唯一键使冲突可见交由人工重试。

ALTER TABLE pm_intake_submit_test_review
    DROP INDEX idx_pm_intake_submit_test_review_intake,
    ADD UNIQUE KEY uk_pm_intake_submit_test_review_intake (intake_id),
    MODIFY COLUMN review_status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/PASSED/FAILED/SKIPPED';
