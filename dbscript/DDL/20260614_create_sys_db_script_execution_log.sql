CREATE TABLE IF NOT EXISTS `sys_db_script_execution_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `script_name` VARCHAR(255) NOT NULL,
  `script_path` VARCHAR(512) NOT NULL,
  `script_type` VARCHAR(16) NOT NULL,
  `script_version` VARCHAR(64) NULL,
  `checksum` VARCHAR(128) NULL,
  `execution_status` VARCHAR(32) NOT NULL,
  `executed_by` VARCHAR(64) NOT NULL,
  `executed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `error_message` TEXT NULL,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_db_script_execution_log_name` (`script_name`),
  KEY `idx_sys_db_script_execution_log_status` (`execution_status`, `executed_at`),
  KEY `idx_sys_db_script_execution_log_type` (`script_type`, `executed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sys_db_script_execution_log` (
  `script_name`,
  `script_path`,
  `script_type`,
  `script_version`,
  `execution_status`,
  `executed_by`,
  `remark`
) VALUES (
  '20260614_create_sys_db_script_execution_log.sql',
  'dbscript/DDL/20260614_create_sys_db_script_execution_log.sql',
  'DDL',
  '20260614',
  'SUCCESS',
  'manual',
  '创建数据库脚本执行登记表'
) ON DUPLICATE KEY UPDATE
  `execution_status` = VALUES(`execution_status`),
  `executed_at` = CURRENT_TIMESTAMP,
  `remark` = VALUES(`remark`);
