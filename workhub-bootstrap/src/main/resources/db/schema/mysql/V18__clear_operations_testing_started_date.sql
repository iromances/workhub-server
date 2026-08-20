UPDATE `pm_intake_record`
SET `testing_started_date` = NULL
WHERE `deleted` = 0
  AND `requirement_type` = '数据提取/运维'
  AND `actual_testing_effort` IS NULL
  AND `actual_testing_completed_date` IS NULL;
