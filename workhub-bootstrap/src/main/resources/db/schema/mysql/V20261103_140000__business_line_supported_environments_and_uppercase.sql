-- 业务线支持环境配置与环境编码统一大写（REQ-95321398）
-- 1) pm_business_line 增加支持环境列；2) 存量 environment_code 全部迁移为大写；3) 按存量配置回填各业务线支持环境。

ALTER TABLE `pm_business_line`
    ADD COLUMN `supported_environments` VARCHAR(512) NULL AFTER `description`;

-- 迁移前备份原始环境编码（表名 + 行主键 + 原值），用于人工核对与回滚。
CREATE TABLE `bak_20261103_environment_code` AS
SELECT 'pm_business_line_access_config' AS `table_name`, `id` AS `row_id`, `environment_code` AS `original_value`
FROM `pm_business_line_access_config`
UNION ALL
SELECT 'mcp_resource_config', `id`, `environment_code` FROM `mcp_resource_config`
UNION ALL
SELECT 'ops_monitor_config', `id`, `environment_code` FROM `ops_monitor_config`
UNION ALL
SELECT 'ops_system_alert_subsystem', `id`, `environment_code` FROM `ops_system_alert_subsystem`
UNION ALL
SELECT 'ops_system_alert_scope', `id`, `environment_code` FROM `ops_system_alert_scope`
UNION ALL
SELECT 'ops_system_alert_event', `id`, `environment_code` FROM `ops_system_alert_event`
UNION ALL
SELECT 'sys_notification', `id`, `environment_code` FROM `sys_notification`
UNION ALL
SELECT 'ops_account_monitor_config', `id`, `environment_code` FROM `ops_account_monitor_config`;

-- 存量环境编码统一大写。若同表存在仅大小写不同的重复键，更新会因唯一键冲突失败，需人工裁决后重试。
UPDATE `pm_business_line_access_config` SET `environment_code` = UPPER(TRIM(`environment_code`));
UPDATE `mcp_resource_config` SET `environment_code` = UPPER(TRIM(`environment_code`));
UPDATE `ops_monitor_config` SET `environment_code` = UPPER(TRIM(`environment_code`));
UPDATE `ops_system_alert_subsystem` SET `environment_code` = UPPER(TRIM(`environment_code`));
UPDATE `ops_system_alert_scope` SET `environment_code` = UPPER(TRIM(`environment_code`));
UPDATE `ops_system_alert_event` SET `environment_code` = UPPER(TRIM(`environment_code`));
UPDATE `sys_notification` SET `environment_code` = UPPER(TRIM(`environment_code`)) WHERE `environment_code` IS NOT NULL;
UPDATE `ops_account_monitor_config` SET `environment_code` = UPPER(TRIM(`environment_code`));

-- 回填业务线支持环境：访问地址配置与 MCP 资源中出现过的环境编码并集，大写去重后按编码排序。
UPDATE `pm_business_line` `bl`
LEFT JOIN (
    SELECT `business_line_code`, GROUP_CONCAT(DISTINCT `env` ORDER BY `env` SEPARATOR ',') AS `envs`
    FROM (
        SELECT `business_line_code`, UPPER(TRIM(`environment_code`)) AS `env` FROM `pm_business_line_access_config`
        UNION
        SELECT `business_line_code`, UPPER(TRIM(`environment_code`)) FROM `mcp_resource_config`
    ) `merged`
    GROUP BY `business_line_code`
) `env_map` ON `env_map`.`business_line_code` = `bl`.`business_line_code`
SET `bl`.`supported_environments` = `env_map`.`envs`;
