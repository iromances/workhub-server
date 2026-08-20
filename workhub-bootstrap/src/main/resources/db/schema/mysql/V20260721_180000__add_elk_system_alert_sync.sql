SET @add_system_alert_source_event_id = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ops_system_alert_event'
          AND COLUMN_NAME = 'source_event_id'
    ),
    'SELECT 1',
    'ALTER TABLE ops_system_alert_event ADD COLUMN source_event_id VARCHAR(64) NULL AFTER source_type'
);
PREPARE add_system_alert_source_event_id_stmt FROM @add_system_alert_source_event_id;
EXECUTE add_system_alert_source_event_id_stmt;
DEALLOCATE PREPARE add_system_alert_source_event_id_stmt;

SET @add_system_alert_source_event_index = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ops_system_alert_event'
          AND INDEX_NAME = 'uk_ops_system_alert_source_event'
    ),
    'SELECT 1',
    'ALTER TABLE ops_system_alert_event ADD UNIQUE KEY uk_ops_system_alert_source_event (source_event_id, business_line_code, environment_code, service_name)'
);
PREPARE add_system_alert_source_event_index_stmt FROM @add_system_alert_source_event_index;
EXECUTE add_system_alert_source_event_index_stmt;
DEALLOCATE PREPARE add_system_alert_source_event_index_stmt;

CREATE TABLE IF NOT EXISTS `ops_system_alert_sync_state` (
  `subsystem_id` BIGINT NOT NULL,
  `last_occurred_at` DATETIME NULL,
  `last_status` VARCHAR(32) NULL,
  `last_message` VARCHAR(1000) NULL,
  `last_synced_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`subsystem_id`),
  KEY `idx_ops_system_alert_sync_status` (`last_status`, `last_synced_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
