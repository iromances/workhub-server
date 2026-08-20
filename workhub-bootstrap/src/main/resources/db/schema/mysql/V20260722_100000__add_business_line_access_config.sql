CREATE TABLE `pm_business_line_access_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line_code` VARCHAR(32) NOT NULL,
  `environment_code` VARCHAR(64) NOT NULL,
  `endpoint_type` VARCHAR(32) NOT NULL,
  `endpoint_name` VARCHAR(128) NOT NULL,
  `endpoint_url` VARCHAR(1024) NOT NULL,
  `path_prefix` VARCHAR(255) NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_bl_access_identity` (`business_line_code`, `environment_code`, `endpoint_type`, `endpoint_name`),
  KEY `idx_pm_bl_access_line_env` (`business_line_code`, `environment_code`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
