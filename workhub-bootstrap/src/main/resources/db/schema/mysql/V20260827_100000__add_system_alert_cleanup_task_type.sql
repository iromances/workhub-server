ALTER TABLE `ops_system_alert_cleanup_task`
  ADD COLUMN `task_type` VARCHAR(32) NOT NULL DEFAULT 'DELETE_AND_FILTER' AFTER `task_no`;
