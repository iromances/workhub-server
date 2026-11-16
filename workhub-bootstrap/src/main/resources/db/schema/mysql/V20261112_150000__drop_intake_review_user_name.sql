-- 删除"审核员"设计（REQ-a71851db-1d1c-4f20-b99e-330950af6ad9 后续变更，2026-11-12 用户确认撤销）：
-- review_user_name 原为澄清阶段可选的默认任务负责人（SDD 任务兜底），现按用户要求整体下线：
-- SDD 模式任务负责人只认任务项自身指派，未指派直接拦截；常规模式兜底走开发人员池。
-- 该列数据仅作默认负责人提示用途，随列删除。

ALTER TABLE pm_intake_record
    DROP COLUMN review_user_name;
