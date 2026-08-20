-- 保费分期生产：申请单 JBR026080916080859 车架号修正
-- 注意：本脚本只修改数据库字段，不处理商业险投保单/PDF 文件。

START TRANSACTION;

-- ============================================================================
-- 1. WDU070001023974
--    LWLPWSU5SL004821 -> LWLPWHSU5SL004821
-- ============================================================================

UPDATE scf_self_order.application_vehicle
SET vin = 'LWLPWHSU5SL004821'
WHERE id = 8358
  AND application_no = 'JBR026080916080859'
  AND plate_number = 'WDU070001023974'
  AND vin = 'LWLPWSU5SL004821';

UPDATE scf_self_order.application_contract
SET vin = 'LWLPWHSU5SL004821'
WHERE id = 64979
  AND application_no = 'JBR026080916080859'
  AND file_type = '500'
  AND vin = 'LWLPWSU5SL004821';

UPDATE scf_self_order.loan_trial_repayschedules
SET vin = 'LWLPWHSU5SL004821',
    partner_schedule_no = CONCAT('LWLPWHSU5SL004821', '_', period)
WHERE application_no = 'JBR026080916080859'
  AND category = 2
  AND vin = 'LWLPWSU5SL004821';

UPDATE scf_self_order.repayment_plan
SET vin = 'LWLPWHSU5SL004821',
    pid = CONCAT('LWLPWHSU5SL004821', '_', period)
WHERE application_no = 'JBR026080916080859'
  AND category = 2
  AND vin = 'LWLPWSU5SL004821';

UPDATE scf_self_saps.payment_slip_sub
SET vin = 'LWLPWHSU5SL004821'
WHERE payment_slip_sn = '02608090001'
  AND vin = 'LWLPWSU5SL004821';

UPDATE scf_self_saps.repayment_plan_sub
SET vin = 'LWLPWHSU5SL004821'
WHERE application_no = 'JBR026080916080859'
  AND vin = 'LWLPWSU5SL004821';

-- ============================================================================
-- 2. WDU020001023966
--    LWLPWSU6SL004813 -> LWLPWHSU6SL004813
-- ============================================================================

UPDATE scf_self_order.application_vehicle
SET vin = 'LWLPWHSU6SL004813'
WHERE id = 8361
  AND application_no = 'JBR026080916080859'
  AND plate_number = 'WDU020001023966'
  AND vin = 'LWLPWSU6SL004813';

UPDATE scf_self_order.application_contract
SET vin = 'LWLPWHSU6SL004813'
WHERE id = 64982
  AND application_no = 'JBR026080916080859'
  AND file_type = '500'
  AND vin = 'LWLPWSU6SL004813';

UPDATE scf_self_order.loan_trial_repayschedules
SET vin = 'LWLPWHSU6SL004813',
    partner_schedule_no = CONCAT('LWLPWHSU6SL004813', '_', period)
WHERE application_no = 'JBR026080916080859'
  AND category = 2
  AND vin = 'LWLPWSU6SL004813';

UPDATE scf_self_order.repayment_plan
SET vin = 'LWLPWHSU6SL004813',
    pid = CONCAT('LWLPWHSU6SL004813', '_', period)
WHERE application_no = 'JBR026080916080859'
  AND category = 2
  AND vin = 'LWLPWSU6SL004813';

UPDATE scf_self_saps.payment_slip_sub
SET vin = 'LWLPWHSU6SL004813'
WHERE payment_slip_sn = '02608090001'
  AND vin = 'LWLPWSU6SL004813';

UPDATE scf_self_saps.repayment_plan_sub
SET vin = 'LWLPWHSU6SL004813'
WHERE application_no = 'JBR026080916080859'
  AND vin = 'LWLPWSU6SL004813';

-- ============================================================================
-- 3. WDU010001023969
--    LWLPWSU1SL004816 -> LWLPWHSU1SL004816
-- ============================================================================

UPDATE scf_self_order.application_vehicle
SET vin = 'LWLPWHSU1SL004816'
WHERE id = 8364
  AND application_no = 'JBR026080916080859'
  AND plate_number = 'WDU010001023969'
  AND vin = 'LWLPWSU1SL004816';

UPDATE scf_self_order.application_contract
SET vin = 'LWLPWHSU1SL004816'
WHERE id = 64985
  AND application_no = 'JBR026080916080859'
  AND file_type = '500'
  AND vin = 'LWLPWSU1SL004816';

UPDATE scf_self_order.loan_trial_repayschedules
SET vin = 'LWLPWHSU1SL004816',
    partner_schedule_no = CONCAT('LWLPWHSU1SL004816', '_', period)
WHERE application_no = 'JBR026080916080859'
  AND category = 2
  AND vin = 'LWLPWSU1SL004816';

UPDATE scf_self_order.repayment_plan
SET vin = 'LWLPWHSU1SL004816',
    pid = CONCAT('LWLPWHSU1SL004816', '_', period)
WHERE application_no = 'JBR026080916080859'
  AND category = 2
  AND vin = 'LWLPWSU1SL004816';

UPDATE scf_self_saps.payment_slip_sub
SET vin = 'LWLPWHSU1SL004816'
WHERE payment_slip_sn = '02608090001'
  AND vin = 'LWLPWSU1SL004816';

UPDATE scf_self_saps.repayment_plan_sub
SET vin = 'LWLPWHSU1SL004816'
WHERE application_no = 'JBR026080916080859'
  AND vin = 'LWLPWSU1SL004816';

-- 确认每条 UPDATE 的影响行数符合预期后执行提交。
COMMIT;

