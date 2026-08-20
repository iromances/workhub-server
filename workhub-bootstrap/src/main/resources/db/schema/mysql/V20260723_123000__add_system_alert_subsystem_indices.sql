CREATE TABLE IF NOT EXISTS `ops_system_alert_subsystem_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `subsystem_id` BIGINT NOT NULL,
  `index_pattern` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_alert_subsystem_index` (`subsystem_id`, `index_pattern`),
  KEY `idx_ops_alert_subsystem_index_order` (`subsystem_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
