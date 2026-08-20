ALTER TABLE `ops_system_alert_event`
  ADD COLUMN `event_category` VARCHAR(32) NOT NULL DEFAULT 'SYSTEM_ERROR' AFTER `log_level`,
  ADD KEY `idx_ops_system_alert_event_category_time` (`event_category`, `occurred_at`);

UPDATE `ops_system_alert_event`
SET `event_category` = 'SLOW_SQL'
WHERE LOWER(CONCAT_WS(' ', `title`, `message`, `error_type`, `stack_trace`))
          LIKE '%discard long time none received connection%'
   OR LOWER(CONCAT_WS(' ', `title`, `message`, `error_type`, `stack_trace`))
          LIKE '%lastpacketreceivedidlemillis%';
