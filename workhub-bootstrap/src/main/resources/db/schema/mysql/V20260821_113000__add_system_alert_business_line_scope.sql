CREATE TABLE IF NOT EXISTS `ops_system_alert_scope` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line_code` VARCHAR(128) NOT NULL,
  `environment_code` VARCHAR(64) NOT NULL,
  `watch_mode` VARCHAR(16) NOT NULL DEFAULT 'SELECTED',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_system_alert_scope` (`business_line_code`, `environment_code`),
  KEY `idx_ops_system_alert_scope_list` (`enabled`, `business_line_code`, `environment_code`),
  CONSTRAINT `chk_ops_system_alert_scope_mode` CHECK (`watch_mode` IN ('ALL', 'SELECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ops_system_alert_scope_service` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scope_id` BIGINT NOT NULL,
  `subsystem_name` VARCHAR(128) NOT NULL,
  `service_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_alert_scope_service` (`scope_id`, `service_name`),
  KEY `idx_ops_alert_scope_service_order` (`scope_id`, `enabled`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ops_system_alert_scope_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scope_id` BIGINT NOT NULL,
  `index_pattern` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_alert_scope_index` (`scope_id`, `index_pattern`),
  KEY `idx_ops_alert_scope_index_order` (`scope_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ops_system_alert_scope_sync_state` (
  `scope_id` BIGINT NOT NULL,
  `last_occurred_at` DATETIME NULL,
  `last_status` VARCHAR(32) NULL,
  `last_message` VARCHAR(1000) NULL,
  `last_synced_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`scope_id`),
  KEY `idx_ops_alert_scope_sync_status` (`last_status`, `last_synced_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `ops_system_alert_scope` (
  `business_line_code`, `environment_code`, `watch_mode`, `enabled`, `remark`, `created_at`, `updated_at`
)
SELECT s.`business_line_code`, s.`environment_code`, 'SELECTED', MAX(s.`enabled`),
       NULL, MIN(s.`created_at`), MAX(s.`updated_at`)
FROM `ops_system_alert_subsystem` s
GROUP BY s.`business_line_code`, s.`environment_code`;

INSERT IGNORE INTO `ops_system_alert_scope_service` (
  `scope_id`, `subsystem_name`, `service_name`, `enabled`, `sort_order`, `created_at`, `updated_at`
)
SELECT target_scope.`id`, subsystem.`subsystem_name`, subsystem.`service_name`, subsystem.`enabled`,
       ROW_NUMBER() OVER (
         PARTITION BY target_scope.`id`
         ORDER BY subsystem.`subsystem_name`, subsystem.`service_name`, subsystem.`id`
       ) - 1,
       subsystem.`created_at`, subsystem.`updated_at`
FROM `ops_system_alert_subsystem` subsystem
JOIN `ops_system_alert_scope` target_scope
  ON target_scope.`business_line_code` = subsystem.`business_line_code`
 AND target_scope.`environment_code` = subsystem.`environment_code`;

INSERT IGNORE INTO `ops_system_alert_scope_index` (`scope_id`, `index_pattern`, `sort_order`)
SELECT target_scope.`id`, source_index.`index_pattern`, MIN(source_index.`sort_order`)
FROM `ops_system_alert_subsystem_index` source_index
JOIN `ops_system_alert_subsystem` subsystem ON subsystem.`id` = source_index.`subsystem_id`
JOIN `ops_system_alert_scope` target_scope
 ON target_scope.`business_line_code` = subsystem.`business_line_code`
 AND target_scope.`environment_code` = subsystem.`environment_code`
GROUP BY target_scope.`id`, source_index.`index_pattern`;

INSERT IGNORE INTO `ops_system_alert_scope_sync_state` (
  `scope_id`, `last_occurred_at`, `last_status`, `last_message`, `last_synced_at`
)
SELECT target_scope.`id`,
       MIN(CASE WHEN subsystem.`enabled` = 1 THEN sync_state.`last_occurred_at` END),
       CASE WHEN SUM(sync_state.`last_status` = 'ERROR') > 0 THEN 'ERROR' ELSE 'SUCCESS' END,
       '迁移自关注子系统采集状态',
       MAX(sync_state.`last_synced_at`)
FROM `ops_system_alert_scope` target_scope
JOIN `ops_system_alert_subsystem` subsystem
  ON subsystem.`business_line_code` = target_scope.`business_line_code`
 AND subsystem.`environment_code` = target_scope.`environment_code`
JOIN `ops_system_alert_sync_state` sync_state ON sync_state.`subsystem_id` = subsystem.`id`
GROUP BY target_scope.`id`;

UPDATE `sys_permission`
SET `permission_name` = CASE `permission_code`
  WHEN 'ops:system-alert:create' THEN '新增关注业务线'
  WHEN 'ops:system-alert:update' THEN '编辑关注业务线'
  WHEN 'ops:system-alert:delete' THEN '删除关注业务线'
  ELSE `permission_name`
END
WHERE `permission_code` IN (
  'ops:system-alert:create', 'ops:system-alert:update', 'ops:system-alert:delete'
);
