ALTER TABLE `mcp_resource_config`
  ADD COLUMN `public_resource` TINYINT(1) NOT NULL DEFAULT 0 AFTER `target_key`,
  ADD COLUMN `feature_tags_json` TEXT NULL AFTER `public_resource`;

CREATE INDEX `idx_mcp_resource_public`
  ON `mcp_resource_config` (`public_resource`, `environment_code`, `resource_type`, `enabled`);
