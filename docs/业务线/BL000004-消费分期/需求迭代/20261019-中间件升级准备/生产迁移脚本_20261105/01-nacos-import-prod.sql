-- 消费分期生产初始导入，2026-11-05；仅供执行人确认后运行，助手未执行。
-- 来源：桌面 nacos-1.3.2-to-2.5.4-insert-only-20261023.sql，保留既有 INSERT SELECT 字段映射。
-- 同一 MySQL 8 实例；新中心未启动，源配置/任务定义冻结；先完成数据库备份。
-- 目标必须已经按官方版本建表且无任何初始化数据；非空目标直接报错。
-- 使用支持 DELIMITER 的客户端，遇错停止；禁止 --force。需要目标库 CREATE ROUTINE 权限。
-- 新库已有迁移数据时不要运行此脚本。XXL 参数替换及任务启用见同目录 README.md。
DELIMITER $$
CREATE PROCEDURE `amp_nacos_254`.`migration_import_20261105`()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = 'amp_nacos_254' AND TABLE_NAME IN ('tenant_info','group_capacity','tenant_capacity','config_info','config_tags_relation','his_config_info') AND ENGINE = 'InnoDB') <> 6 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target tables missing or not InnoDB';
    END IF;
    SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
    START TRANSACTION WITH CONSISTENT SNAPSHOT;
    IF (SELECT COUNT(*) FROM `amp_nacos_254`.`tenant_info`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_nacos_254`.`group_capacity`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_nacos_254`.`tenant_capacity`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_nacos_254`.`config_info`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_nacos_254`.`config_tags_relation`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_nacos_254`.`his_config_info`) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target is not empty; do not rerun initial migration';
    END IF;

    INSERT INTO `amp_nacos_254`.`tenant_info` (`id`, `kp`, `tenant_id`, `tenant_name`, `tenant_desc`, `create_source`, `gmt_create`, `gmt_modified`)
    SELECT s.`id`, s.`kp`, s.`tenant_id`, s.`tenant_name`, s.`tenant_desc`, s.`create_source`, s.`gmt_create`, s.`gmt_modified`
    FROM `amp_nacos`.`tenant_info` s;

    INSERT INTO `amp_nacos_254`.`group_capacity` (`id`, `group_id`, `quota`, `usage`, `max_size`, `max_aggr_count`, `max_aggr_size`, `max_history_count`, `gmt_create`, `gmt_modified`)
    SELECT s.`id`, s.`group_id`, s.`quota`, s.`usage`, s.`max_size`, s.`max_aggr_count`, s.`max_aggr_size`, s.`max_history_count`, s.`gmt_create`, s.`gmt_modified`
    FROM `amp_nacos`.`group_capacity` s;

    INSERT INTO `amp_nacos_254`.`tenant_capacity` (`id`, `tenant_id`, `quota`, `usage`, `max_size`, `max_aggr_count`, `max_aggr_size`, `max_history_count`, `gmt_create`, `gmt_modified`)
    SELECT s.`id`, s.`tenant_id`, s.`quota`, s.`usage`, s.`max_size`, s.`max_aggr_count`, s.`max_aggr_size`, s.`max_history_count`, s.`gmt_create`, s.`gmt_modified`
    FROM `amp_nacos`.`tenant_capacity` s;

    INSERT INTO `amp_nacos_254`.`config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`, `encrypted_data_key`)
    SELECT s.`id`, s.`data_id`, s.`group_id`, s.`content`, s.`md5`, s.`gmt_create`, s.`gmt_modified`, s.`src_user`, s.`src_ip`, s.`app_name`, s.`tenant_id`, s.`c_desc`, s.`c_use`, s.`effect`, s.`type`, s.`c_schema`, ''
    FROM `amp_nacos`.`config_info` s;

    INSERT INTO `amp_nacos_254`.`config_tags_relation` (`id`, `tag_name`, `tag_type`, `data_id`, `group_id`, `tenant_id`, `nid`)
    SELECT s.`id`, s.`tag_name`, s.`tag_type`, s.`data_id`, s.`group_id`, s.`tenant_id`, s.`nid`
    FROM `amp_nacos`.`config_tags_relation` s;

    INSERT INTO `amp_nacos_254`.`his_config_info` (`id`, `nid`, `data_id`, `group_id`, `app_name`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `op_type`, `tenant_id`, `encrypted_data_key`, `publish_type`, `gray_name`, `ext_info`)
    SELECT s.`id`, s.`nid`, s.`data_id`, s.`group_id`, s.`app_name`, s.`content`, s.`md5`, s.`gmt_create`, s.`gmt_modified`, s.`src_user`, s.`src_ip`, s.`op_type`, s.`tenant_id`, '', 'formal', NULL, NULL
    FROM `amp_nacos`.`his_config_info` s;

    COMMIT;
    SELECT 'Initial import committed; verify counts before starting the new center' AS migration_result;
END$$
DELIMITER ;
CALL `amp_nacos_254`.`migration_import_20261105`();
DROP PROCEDURE `amp_nacos_254`.`migration_import_20261105`;
