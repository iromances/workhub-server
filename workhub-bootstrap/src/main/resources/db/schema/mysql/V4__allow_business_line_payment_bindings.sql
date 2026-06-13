-- 业务支付绑定支持业务线通用配置：project_id 允许为空，business_line 承载绑定归属。
-- 执行前建议备份 pay_project_merchant_binding。

ALTER TABLE pay_project_merchant_binding
  ADD COLUMN business_line VARCHAR(128) NULL AFTER project_id;

UPDATE pay_project_merchant_binding b
JOIN pm_project p ON p.id = b.project_id
SET b.business_line = p.business_line
WHERE b.business_line IS NULL
   OR b.business_line = '';

ALTER TABLE pay_project_merchant_binding
  MODIFY COLUMN project_id BIGINT NULL;

CREATE INDEX idx_pay_binding_business_purpose
  ON pay_project_merchant_binding (business_line, purpose_code, binding_status, is_default, priority);

-- 校验业务线回填情况。
SELECT COUNT(*) AS missing_business_line_count
FROM pay_project_merchant_binding
WHERE business_line IS NULL
   OR business_line = '';
