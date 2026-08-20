-- 业务线名称只由 pm_business_line 维护，业务表仅保留稳定编码。

-- 先按当前名称、历史改名和已确认别名回填项目编码。
UPDATE pm_project p
JOIN pm_business_line bl ON BINARY bl.business_line_name = BINARY p.business_line
SET p.business_line_code = bl.business_line_code
WHERE p.business_line_code = '';

UPDATE pm_project
SET business_line_code = CASE business_line
    WHEN '嘉泰资产平台' THEN 'BL000003'
    WHEN '资产平台' THEN 'BL000004'
    WHEN '汇浦' THEN 'BL000007'
    WHEN '创新保理' THEN 'BL000009'
    WHEN '设备' THEN 'BL000007'
    WHEN '嘉泰汇浦设备' THEN 'BL000007'
    ELSE business_line_code
END
WHERE business_line_code = '';

UPDATE pm_business_line_member m
JOIN pm_business_line bl ON BINARY bl.business_line_name = BINARY m.business_line
SET m.business_line_code = bl.business_line_code
WHERE m.business_line_code = '';

UPDATE pm_business_line_member
SET business_line_code = CASE business_line
    WHEN '嘉泰资产平台' THEN 'BL000003'
    WHEN '资产平台' THEN 'BL000004'
    WHEN '汇浦' THEN 'BL000007'
    WHEN '创新保理' THEN 'BL000009'
    WHEN '设备' THEN 'BL000007'
    WHEN '嘉泰汇浦设备' THEN 'BL000007'
    ELSE business_line_code
END
WHERE business_line_code = '';

UPDATE pm_project_involved_system s
JOIN pm_business_line bl ON BINARY bl.business_line_name = BINARY s.business_line
SET s.business_line_code = bl.business_line_code
WHERE s.system_scope = 'BUSINESS_LINE'
  AND s.business_line_code = '';

UPDATE pm_project_involved_system
SET business_line_code = CASE business_line
    WHEN '嘉泰资产平台' THEN 'BL000003'
    WHEN '资产平台' THEN 'BL000004'
    WHEN '汇浦' THEN 'BL000007'
    WHEN '创新保理' THEN 'BL000009'
    WHEN '设备' THEN 'BL000007'
    WHEN '嘉泰汇浦设备' THEN 'BL000007'
    ELSE business_line_code
END
WHERE system_scope = 'BUSINESS_LINE'
  AND business_line_code = '';

-- 支付绑定优先继承项目编码，无项目时再按名称回填。
UPDATE pay_project_merchant_binding b
JOIN pm_project p ON p.id = b.project_id
SET b.business_line_code = p.business_line_code
WHERE b.business_line_code = ''
  AND p.business_line_code <> '';

UPDATE pay_project_merchant_binding b
JOIN pm_business_line bl ON BINARY bl.business_line_name = BINARY b.business_line
SET b.business_line_code = bl.business_line_code
WHERE b.business_line_code = '';

UPDATE pay_project_merchant_binding
SET business_line_code = CASE business_line
    WHEN '嘉泰资产平台' THEN 'BL000003'
    WHEN '资产平台' THEN 'BL000004'
    WHEN '汇浦' THEN 'BL000007'
    WHEN '创新保理' THEN 'BL000009'
    WHEN '设备' THEN 'BL000007'
    WHEN '嘉泰汇浦设备' THEN 'BL000007'
    ELSE business_line_code
END
WHERE business_line_code = '';

-- 需求及分析记录允许业务线未归类，能确认的先回填，其余保持 NULL。
UPDATE pm_intake_record r
JOIN pm_business_line bl
  ON BINARY bl.business_line_code = BINARY r.business_line
  OR BINARY bl.business_line_name = BINARY r.business_line
SET r.business_line_code = bl.business_line_code
WHERE r.business_line_code IS NULL OR r.business_line_code = '';

UPDATE pm_intake_record
SET business_line_code = CASE business_line
    WHEN '嘉泰资产平台' THEN 'BL000003'
    WHEN '资产平台' THEN 'BL000004'
    WHEN '资产平台（BL000004）' THEN 'BL000004'
    WHEN '汇浦' THEN 'BL000007'
    WHEN '创新保理' THEN 'BL000009'
    ELSE business_line_code
END
WHERE business_line_code IS NULL OR business_line_code = '';

UPDATE pm_intake_development_analysis a
JOIN pm_project p ON p.id = a.project_id
SET a.business_line_code = p.business_line_code
WHERE (a.business_line_code IS NULL OR a.business_line_code = '')
  AND p.business_line_code <> '';

UPDATE pm_intake_development_analysis a
JOIN pm_intake_record r ON r.id = a.intake_id
SET a.business_line_code = r.business_line_code
WHERE (a.business_line_code IS NULL OR a.business_line_code = '')
  AND r.business_line_code IS NOT NULL
  AND r.business_line_code <> '';

UPDATE pm_intake_development_analysis a
JOIN pm_business_line bl
  ON BINARY bl.business_line_code = BINARY a.business_line
  OR BINARY bl.business_line_name = BINARY a.business_line
SET a.business_line_code = bl.business_line_code
WHERE a.business_line_code IS NULL OR a.business_line_code = '';

UPDATE pm_intake_development_analysis
SET business_line_code = CASE business_line
    WHEN '嘉泰资产平台' THEN 'BL000003'
    WHEN '资产平台' THEN 'BL000004'
    WHEN '汇浦' THEN 'BL000007'
    WHEN '创新保理' THEN 'BL000009'
    ELSE business_line_code
END
WHERE business_line_code IS NULL OR business_line_code = '';

UPDATE pm_intake_clarification_analysis a
JOIN pm_intake_record r ON r.id = a.intake_id
SET a.business_line_code = r.business_line_code
WHERE (a.business_line_code IS NULL OR a.business_line_code = '')
  AND r.business_line_code IS NOT NULL
  AND r.business_line_code <> '';

UPDATE pm_intake_clarification_analysis a
JOIN pm_business_line bl
  ON BINARY bl.business_line_code = BINARY a.business_line
  OR BINARY bl.business_line_name = BINARY a.business_line
SET a.business_line_code = bl.business_line_code
WHERE a.business_line_code IS NULL OR a.business_line_code = '';

UPDATE pm_intake_clarification_analysis
SET business_line_code = CASE business_line
    WHEN '嘉泰资产平台' THEN 'BL000003'
    WHEN '资产平台' THEN 'BL000004'
    WHEN '汇浦' THEN 'BL000007'
    WHEN '创新保理' THEN 'BL000009'
    ELSE business_line_code
END
WHERE business_line_code IS NULL OR business_line_code = '';

-- JSON 仅保留稳定编码；查询时由 DAO 动态补充当前名称。
UPDATE pm_intake_record
SET structured_data_json = JSON_REMOVE(structured_data_json, '$.businessLine')
WHERE JSON_VALID(structured_data_json)
  AND JSON_CONTAINS_PATH(structured_data_json, 'one', '$.businessLine');

UPDATE pm_intake_record
SET ai_draft_json = JSON_REMOVE(ai_draft_json, '$.businessLine')
WHERE JSON_VALID(ai_draft_json)
  AND JSON_CONTAINS_PATH(ai_draft_json, 'one', '$.businessLine');

UPDATE pm_intake_development_analysis
SET draft_json = JSON_REMOVE(draft_json, '$.businessLine')
WHERE JSON_VALID(draft_json)
  AND JSON_CONTAINS_PATH(draft_json, 'one', '$.businessLine');

UPDATE pm_intake_clarification_analysis
SET items_json = JSON_REMOVE(items_json, '$.businessLine')
WHERE JSON_VALID(items_json)
  AND JSON_CONTAINS_PATH(items_json, 'one', '$.businessLine');

-- 移除名称字段和依赖名称的索引。
ALTER TABLE pm_business_line_member
  DROP INDEX uk_pm_business_line_member,
  ADD UNIQUE KEY uk_pm_business_line_member (business_line_code, member_user_name),
  DROP COLUMN business_line,
  ADD CONSTRAINT chk_pm_business_line_member_code CHECK (business_line_code <> '');

ALTER TABLE pm_project_involved_system
  DROP INDEX uk_pm_project_involved_system_name,
  DROP INDEX idx_pm_project_involved_system_select,
  DROP COLUMN business_line,
  ADD CONSTRAINT chk_pm_project_involved_system_code CHECK (system_scope <> 'BUSINESS_LINE' OR business_line_code <> '');

ALTER TABLE pay_project_merchant_binding
  DROP INDEX idx_pay_binding_business_purpose,
  DROP COLUMN business_line,
  ADD CONSTRAINT chk_pay_binding_business_line_code CHECK (business_line_code <> '');

ALTER TABLE pm_project
  DROP COLUMN business_line,
  ADD CONSTRAINT chk_pm_project_business_line_code CHECK (business_line_code <> '');
ALTER TABLE pm_intake_record DROP COLUMN business_line;
ALTER TABLE pm_intake_development_analysis DROP COLUMN business_line;
ALTER TABLE pm_intake_clarification_analysis DROP COLUMN business_line;
