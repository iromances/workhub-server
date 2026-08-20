SET @system_name_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'mcp_resource_config'
    AND column_name = 'system_name'
);

SET @system_name_ddl = IF(
  @system_name_exists = 0,
  'ALTER TABLE `mcp_resource_config` ADD COLUMN `system_name` TEXT NULL AFTER `name`',
  'ALTER TABLE `mcp_resource_config` MODIFY COLUMN `system_name` TEXT NULL'
);

PREPARE system_name_statement FROM @system_name_ddl;
EXECUTE system_name_statement;
DEALLOCATE PREPARE system_name_statement;
