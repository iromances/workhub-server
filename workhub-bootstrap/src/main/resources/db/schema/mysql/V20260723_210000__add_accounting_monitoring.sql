-- 账务监测表通过关联字段和索引维护逻辑关系，不创建数据库外键，
-- 避免 Flyway 运行账号额外依赖 REFERENCES 权限。
CREATE TABLE IF NOT EXISTS `ops_account_monitor_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line_code` VARCHAR(32) NOT NULL,
  `environment_code` VARCHAR(32) NOT NULL DEFAULT 'prod',
  `system_name` VARCHAR(128) NOT NULL,
  `display_name` VARCHAR(128) NOT NULL,
  `database_target_key` VARCHAR(128) NULL,
  `schema_name` VARCHAR(128) NOT NULL,
  `rule_profile` VARCHAR(64) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `daily_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `remark` VARCHAR(500) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_account_monitor_identity` (`business_line_code`, `environment_code`, `system_name`),
  KEY `idx_ops_account_monitor_enabled` (`environment_code`, `enabled`, `daily_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ops_account_rule_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `monitor_config_id` BIGINT NOT NULL,
  `rule_code` VARCHAR(128) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `severity_override` VARCHAR(16) NULL,
  `threshold_json` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_account_rule_config` (`monitor_config_id`, `rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ops_account_reconcile_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_no` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `monitor_config_id` BIGINT NOT NULL,
  `trigger_type` VARCHAR(16) NOT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `rule_codes` VARCHAR(2000) NULL,
  `status` VARCHAR(16) NOT NULL,
  `rule_total` INT NOT NULL DEFAULT 0,
  `passed_count` INT NOT NULL DEFAULT 0,
  `warning_count` INT NOT NULL DEFAULT 0,
  `failed_count` INT NOT NULL DEFAULT 0,
  `skipped_count` INT NOT NULL DEFAULT 0,
  `anomaly_count` INT NOT NULL DEFAULT 0,
  `requested_by` VARCHAR(64) NOT NULL,
  `started_at` DATETIME NULL,
  `finished_at` DATETIME NULL,
  `error_message` VARCHAR(1000) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_account_run_no` (`run_no`),
  UNIQUE KEY `uk_ops_account_run_idempotency` (`idempotency_key`),
  KEY `idx_ops_account_run_config_time` (`monitor_config_id`, `created_at`),
  KEY `idx_ops_account_run_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ops_account_reconcile_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_id` BIGINT NOT NULL,
  `rule_code` VARCHAR(128) NOT NULL,
  `rule_name` VARCHAR(128) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `granularity` VARCHAR(16) NOT NULL,
  `severity` VARCHAR(16) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `anomaly_count` INT NOT NULL DEFAULT 0,
  `difference_amount` DECIMAL(20,2) NOT NULL DEFAULT 0,
  `sample_json` LONGTEXT NULL,
  `message` VARCHAR(1000) NULL,
  `duration_ms` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_account_result_rule` (`run_id`, `rule_code`),
  KEY `idx_ops_account_result_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `ops_account_monitor_config` (
  `business_line_code`, `environment_code`, `system_name`, `display_name`,
  `database_target_key`, `schema_name`, `rule_profile`, `enabled`, `daily_enabled`, `remark`
)
SELECT seed.*
FROM (
  SELECT 'BL000001' AS `business_line_code`,
         'prod' AS `environment_code`,
         'scf-saps' AS `system_name`,
         '保费分期账务' AS `display_name`,
         'db-prod' AS `database_target_key`,
         'scf_saps' AS `schema_name`,
         'SCF_SAPS' AS `rule_profile`,
         1 AS `enabled`,
         0 AS `daily_enabled`,
         '每日任务默认关闭，待生产只读连接恢复后启用' AS `remark` UNION ALL
  SELECT 'BL000008', 'prod', 'lease-saps', '叉车账务', 'db-prod', 'lease_saps', 'LEASE_SAPS', 1, 0, '每日任务默认关闭，待生产只读连接恢复后启用' UNION ALL
  SELECT 'BL000003', 'prod', 'jiatai-amp-saps', '嘉泰消费分期账务', 'jiatai-amp-db-prod', 'jiatai_amp_saps', 'JIATAI_AMP_SAPS', 1, 0, '生产 schema 已完成只读核验' UNION ALL
  SELECT 'BL000009', 'prod', 'assets-saps', '嘉泰租赁资产保理账务', NULL, 'assets_saps', 'ASSETS_SAPS', 0, 0, '需先补齐生产 DB 资源绑定及项目映射' UNION ALL
  SELECT 'BL000007', 'prod', 'equip-lease-saps', '嘉泰设备租赁账务', 'jiatai-hp-db-prod', 'equip_lease_saps', 'EQUIP_LEASE_SAPS', 1, 0, '显式选择 jiatai-hp-db-prod，避免多 DB 目标歧义' UNION ALL
  SELECT 'BL000005', 'prod', 'ol-saps', '智能柜账务', 'db-prod', 'ol_saps', 'OL_SAPS', 1, 0, '每日任务默认关闭，待生产只读连接恢复后启用' UNION ALL
  SELECT 'BL000010', 'prod', 'assets-saps', '服务费分期保理账务', NULL, 'assets_saps', 'ASSETS_SAPS', 0, 0, '需先补齐生产 DB 资源绑定及项目映射' UNION ALL
  SELECT 'BL000004', 'prod', 'amp-saps', '消费分期账务', 'amp-db-prod', 'amp_saps', 'AMP_SAPS', 1, 0, '包含再保理资方字段完整性规则' UNION ALL
  SELECT 'BL000006', 'prod', 'thctay-saps', '物流平台账务', 'db-prod', 'th_saps', 'LOGISTICS_SAPS', 1, 0, '规则口径复用 saps-account-checker' UNION ALL
  SELECT 'BL000002', 'prod', 'assets-saps', '账单管理账务', 'db-prod', 'assets_saps', 'ASSETS_SAPS', 0, 0, '共享 schema 项目映射完成前保持关闭，避免跨业务线扫描'
) AS seed
WHERE NOT EXISTS (
  SELECT 1
  FROM `ops_account_monitor_config` existing
  WHERE existing.`business_line_code` = seed.`business_line_code`
    AND existing.`environment_code` = seed.`environment_code`
    AND existing.`system_name` = seed.`system_name`
);

INSERT INTO `sys_permission` (
  `permission_code`, `permission_name`, `permission_type`,
  `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`
)
SELECT permission.*
FROM (
  SELECT 'ops:accounting:view' AS `permission_code`,
         '账务监测' AS `permission_name`,
         'MENU' AS `permission_type`,
         'ops:view' AS `parent_code`,
         '/ops/accounting' AS `route_path`,
         33 AS `sort_order`,
         1 AS `enabled`,
         '生产账务只读监测' AS `remark` UNION ALL
  SELECT 'ops:accounting:run', '账务监测手动对账', 'BUTTON', 'ops:accounting:view', NULL, 34, 1, '允许发起只读手动对账' UNION ALL
  SELECT 'ops:accounting:manage', '账务监测配置管理', 'BUTTON', 'ops:accounting:view', NULL, 35, 1, '允许维护 DB 目标、schema 和调度开关'
) AS permission
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_permission` existing
  WHERE existing.`permission_code` = permission.`permission_code`
);

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.`permission_code` IN ('ops:accounting:view', 'ops:accounting:run', 'ops:accounting:manage')
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_permission` existing
    WHERE existing.`role_id` = r.`id`
      AND existing.`permission_code` = p.`permission_code`
  );

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT DISTINCT source.`role_id`, mapping.`target_permission`
FROM `sys_role_permission` source
JOIN (
  SELECT 'ops:monitor:view' AS `source_permission`, 'ops:accounting:view' AS `target_permission` UNION ALL
  SELECT 'ops:monitor:check', 'ops:accounting:run' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:accounting:run' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:accounting:manage'
) mapping ON mapping.`source_permission` = source.`permission_code`
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_role_permission` existing
  WHERE existing.`role_id` = source.`role_id`
    AND existing.`permission_code` = mapping.`target_permission`
);
