CREATE TABLE `mcp_bastion_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NOT NULL,
  `host` VARCHAR(255) NOT NULL,
  `port` INT NOT NULL DEFAULT 22,
  `username` VARCHAR(128) NOT NULL,
  `password_encrypted` TEXT NULL,
  `identity_file` VARCHAR(512) NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `legacy_resource_id` BIGINT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_bastion_config_name` (`name`),
  UNIQUE KEY `uk_mcp_bastion_config_legacy_resource` (`legacy_resource_id`),
  KEY `idx_mcp_bastion_config_list` (`enabled`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `mcp_resource_config`
  ADD COLUMN `bastion_id` BIGINT NULL AFTER `ssh_bastion_enabled`,
  ADD KEY `idx_mcp_resource_config_bastion_id` (`bastion_id`);

INSERT INTO `mcp_bastion_config` (
  `name`,
  `host`,
  `port`,
  `username`,
  `password_encrypted`,
  `identity_file`,
  `enabled`,
  `remark`,
  `legacy_resource_id`
)
SELECT CONCAT(LEFT(`name`, 96), '-堡垒机-', `id`),
       `ssh_bastion_host`,
       COALESCE(`ssh_bastion_port`, 22),
       `ssh_bastion_user`,
       `ssh_bastion_password_encrypted`,
       `ssh_identity_file`,
       1,
       '由历史数据库目标堡垒机配置自动迁移',
       `id`
FROM `mcp_resource_config`
WHERE `resource_type` = 'DATABASE'
  AND `ssh_bastion_enabled` = 1
  AND `ssh_bastion_host` IS NOT NULL
  AND `ssh_bastion_host` <> ''
  AND `ssh_bastion_user` IS NOT NULL
  AND `ssh_bastion_user` <> '';

UPDATE `mcp_resource_config` resource
JOIN `mcp_bastion_config` bastion
  ON bastion.`legacy_resource_id` = resource.`id`
SET resource.`bastion_id` = bastion.`id`
WHERE resource.`resource_type` = 'DATABASE';

ALTER TABLE `mcp_bastion_config`
  DROP KEY `uk_mcp_bastion_config_legacy_resource`,
  DROP COLUMN `legacy_resource_id`;
