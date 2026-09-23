-- 仅修复需求202609180002。必须由Flyway事务执行，不可逐句自动提交。
-- 断言通过时执行SELECT 1；失败分支引用派生表中不存在的列，PREPARE立即报错，Flyway回滚。
SET @wh_rb_guard = IF(@@SESSION.autocommit = 0 AND DATABASE() = 'workhub',
    'SELECT 1', 'SELECT wrong_database_or_missing_transaction FROM (SELECT 1) AS guard');
PREPARE wh_rb_assert FROM @wh_rb_guard;
EXECUTE wh_rb_assert;
DEALLOCATE PREPARE wh_rb_assert;

SET @wh_rb_guard = IF((SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND engine = 'InnoDB' AND table_name IN
    ('pm_intake_record', 'pm_intake_history', 'pm_intake_clarification_analysis',
     'pm_intake_development_analysis', 'pm_intake_work_item_relation')) = 5,
    'SELECT 1', 'SELECT non_transactional_tables FROM (SELECT 1) AS guard');
PREPARE wh_rb_assert FROM @wh_rb_guard;
EXECUTE wh_rb_assert;
DEALLOCATE PREPARE wh_rb_assert;

-- 锁定目标及关联记录。用户应在迁移期间暂停该需求的页面操作。
SELECT id FROM pm_intake_record WHERE id = 217 OR approval_code = '202609180002' FOR UPDATE;
SELECT id FROM pm_intake_clarification_analysis WHERE intake_id = 217 FOR UPDATE;
SELECT id FROM pm_intake_development_analysis WHERE intake_id = 217 FOR UPDATE;
SELECT id FROM pm_intake_work_item_relation WHERE intake_id = 217 FOR UPDATE;
SELECT id FROM pm_intake_history WHERE intake_id = 217 FOR UPDATE;

SET @wh_rb_marker = 'V20260921_230911__return_intake_202609180002_to_clarification';
SET @wh_rb_history_count = (SELECT COUNT(*) FROM pm_intake_history
    WHERE intake_id = 217 AND JSON_UNQUOTE(JSON_EXTRACT(
        IF(JSON_VALID(detail_text), detail_text, '{}'), '$.migration')) = @wh_rb_marker);
SET @wh_rb_done = @wh_rb_history_count = 1;

SET @wh_rb_guard = IF(
    (SELECT COUNT(*) FROM pm_intake_record WHERE approval_code = '202609180002' AND deleted = 0) = 1
    AND EXISTS (SELECT 1 FROM pm_intake_record
        WHERE id = 217 AND approval_code = '202609180002' AND business_line_code = 'BL000009'
          AND requirement_type = '研发需求' AND deleted = 0 AND converted_work_item_id IS NULL
          AND ((@wh_rb_done AND demand_status = '待澄清')
               OR (@wh_rb_history_count = 0 AND demand_status = '待评估'
                   AND updated_at = '2026-09-21 16:09:13')))
    AND NOT EXISTS (SELECT 1 FROM pm_intake_work_item_relation WHERE intake_id = 217)
    AND EXISTS (SELECT 1 FROM pm_intake_history WHERE id = 3527 AND intake_id = 217
        AND action_summary = '澄清完成' AND detail_text = '需求状态：待澄清 -> 待评估'),
    'SELECT 1', 'SELECT intake_baseline_changed FROM (SELECT 1) AS guard');
PREPARE wh_rb_assert FROM @wh_rb_guard;
EXECUTE wh_rb_assert;
DEALLOCATE PREPARE wh_rb_assert;

-- 首次执行必须与核验时的两条FAILED分析完全对应；重复执行不能覆盖新的分析状态。
SET @wh_rb_guard = IF(@wh_rb_done OR (
    (SELECT COUNT(*) FROM pm_intake_clarification_analysis WHERE intake_id = 217) = 1
    AND EXISTS (SELECT 1 FROM pm_intake_clarification_analysis
        WHERE id = 21 AND intake_id = 217 AND analysis_status = 'FAILED'
          AND updated_at = '2026-09-21 16:03:42' AND items_json IS NULL)
    AND (SELECT COUNT(*) FROM pm_intake_development_analysis WHERE intake_id = 217) = 1
    AND EXISTS (SELECT 1 FROM pm_intake_development_analysis
        WHERE id = 83 AND intake_id = 217 AND analysis_status = 'FAILED'
          AND updated_at = '2026-09-21 16:09:14')),
    'SELECT 1', 'SELECT analysis_baseline_changed FROM (SELECT 1) AS guard');
PREPARE wh_rb_assert FROM @wh_rb_guard;
EXECUTE wh_rb_assert;
DEALLOCATE PREPARE wh_rb_assert;

SET @wh_rb_before = (SELECT JSON_OBJECT(
    'id', id, 'approvalCode', approval_code, 'businessLineCode', business_line_code,
    'demandStatus', demand_status, 'updatedAt', updated_at)
    FROM pm_intake_record WHERE id = 217);
SET @wh_rb_clarification_before = (SELECT JSON_OBJECT(
    'id', id, 'status', analysis_status, 'updatedAt', updated_at, 'itemsJson', items_json)
    FROM pm_intake_clarification_analysis WHERE id = 21 AND intake_id = 217);
SET @wh_rb_development_before = (SELECT JSON_OBJECT(
    'id', id, 'status', analysis_status, 'updatedAt', updated_at)
    FROM pm_intake_development_analysis WHERE id = 83 AND intake_id = 217);
SET @wh_rb_at = CURRENT_TIMESTAMP;

UPDATE pm_intake_record
SET demand_status = '待澄清', updated_at = @wh_rb_at
WHERE id = 217 AND approval_code = '202609180002' AND business_line_code = 'BL000009'
  AND requirement_type = '研发需求' AND deleted = 0 AND converted_work_item_id IS NULL
  AND demand_status = '待评估' AND updated_at = '2026-09-21 16:09:13'
  AND NOT @wh_rb_done;

-- 不依赖ROW_COUNT：Flyway读取SQL警告可能改变该值。校验锁定行的实际后镜像。
SET @wh_rb_guard = IF(@wh_rb_done OR EXISTS (SELECT 1 FROM pm_intake_record
    WHERE id = 217 AND approval_code = '202609180002'
      AND demand_status = '待澄清' AND updated_at = @wh_rb_at),
    'SELECT 1', 'SELECT intake_update_failed FROM (SELECT 1) AS guard');
PREPARE wh_rb_assert FROM @wh_rb_guard;
EXECUTE wh_rb_assert;
DEALLOCATE PREPARE wh_rb_assert;

INSERT INTO pm_intake_history (intake_id, action_type, action_summary, detail_text, operator_user_name, created_at)
SELECT 217, 'UPDATE', '需求回退澄清', JSON_OBJECT(
    'migration', @wh_rb_marker,
    'reason', '用户确认回退至上一个节点，以重新触发此前因Xcode许可失败的澄清分析',
    'before', CAST(@wh_rb_before AS JSON),
    'after', JSON_OBJECT('demandStatus', '待澄清', 'updatedAt', @wh_rb_at),
    'clarificationBefore', CAST(@wh_rb_clarification_before AS JSON),
    'developmentBefore', CAST(@wh_rb_development_before AS JSON),
    'executor', 'Flyway', 'databasePrincipal', CURRENT_USER()),
    'flyway', @wh_rb_at
WHERE NOT @wh_rb_done;

SET @wh_rb_guard = IF((SELECT COUNT(*) FROM pm_intake_history
    WHERE intake_id = 217 AND JSON_UNQUOTE(JSON_EXTRACT(
        IF(JSON_VALID(detail_text), detail_text, '{}'), '$.migration')) = @wh_rb_marker) = 1,
    'SELECT 1', 'SELECT rollback_audit_missing_or_duplicated FROM (SELECT 1) AS guard');
PREPARE wh_rb_assert FROM @wh_rb_guard;
EXECUTE wh_rb_assert;
DEALLOCATE PREPARE wh_rb_assert;
