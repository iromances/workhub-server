ALTER TABLE pm_intake_record
    ADD COLUMN process_estimated_effort JSON NULL COMMENT '需求预估工时人工修正；SQL NULL 表示沿用研发评估';
