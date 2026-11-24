ALTER TABLE pm_intake_clarification_analysis ADD COLUMN raw_output LONGTEXT NULL COMMENT '脱敏原始AI答复，用于仅恢复结果整理';
ALTER TABLE pm_intake_clarification_analysis ADD COLUMN input_fingerprint CHAR(64) NULL COMMENT '输入、模型、schema及代码版本指纹';
ALTER TABLE pm_intake_clarification_analysis ADD COLUMN recovery_pending TINYINT NOT NULL DEFAULT 0 COMMENT '仅格式未完成的原文可恢复，业务校验失败须重新分析';
ALTER TABLE pm_intake_development_analysis ADD COLUMN raw_output LONGTEXT NULL COMMENT '脱敏原始AI答复，用于仅恢复结果整理';
ALTER TABLE pm_intake_development_analysis ADD COLUMN input_fingerprint CHAR(64) NULL COMMENT '输入、模型、schema及代码版本指纹';
ALTER TABLE pm_intake_development_analysis ADD COLUMN recovery_pending TINYINT NOT NULL DEFAULT 0 COMMENT '仅格式未完成的原文可恢复，业务校验失败须重新分析';
