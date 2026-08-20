UPDATE `pm_intake_record` r
JOIN (
  SELECT h.`intake_id`,
         NULLIF(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(h.`detail_text`, '实际测试工时：', -1), '\n', 1), '->', -1)), '-') AS `actual_testing_effort`,
         STR_TO_DATE(
           NULLIF(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(h.`detail_text`, '实际测试完成日期：', -1), '\n', 1), '->', -1)), '-'),
           '%Y/%m/%d'
         ) AS `actual_testing_completed_date`
  FROM `pm_intake_history` h
  JOIN (
    SELECT `intake_id`, MAX(`id`) AS `history_id`
    FROM `pm_intake_history`
    WHERE `action_type` = 'UPDATE'
      AND `action_summary` = '测试通过'
      AND `detail_text` LIKE '%实际测试工时：%'
      AND `detail_text` LIKE '%实际测试完成日期：%'
    GROUP BY `intake_id`
  ) latest ON latest.`history_id` = h.`id`
) repaired ON repaired.`intake_id` = r.`id`
SET r.`actual_testing_effort` = COALESCE(NULLIF(r.`actual_testing_effort`, ''), repaired.`actual_testing_effort`),
    r.`actual_testing_completed_date` = COALESCE(r.`actual_testing_completed_date`, repaired.`actual_testing_completed_date`)
WHERE r.`deleted` = 0
  AND (r.`actual_testing_effort` IS NULL
       OR r.`actual_testing_effort` = ''
       OR r.`actual_testing_completed_date` IS NULL);
