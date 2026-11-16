-- 禅道任务同步汇总可能包含逐项失败明细，varchar(255) 不足（实测 Data too long）；
-- 扩为 varchar(1000)，与代码侧 900 字符截断防御配合。
ALTER TABLE pm_intake_development_analysis
    MODIFY COLUMN zentao_sync_message VARCHAR(1000) NULL COMMENT '禅道同步结果摘要（新建/更新/取消/失败与明细）';
