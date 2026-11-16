-- 消费分期生产初始导入，2026-11-05；仅供执行人确认后运行，助手未执行。
-- 来源：桌面 02-amp-xxljob-to-2.5.0-insert-only-20261026.sql，保留既有 INSERT SELECT 字段映射。
-- 同一 MySQL 8 实例；新中心未启动，源配置/任务定义冻结；先完成数据库备份。
-- 目标必须已经按官方版本建表且无任何初始化数据；非空目标直接报错。
-- 使用支持 DELIMITER 的客户端，遇错停止；禁止 --force。需要目标库 CREATE ROUTINE 权限。
-- 新库已有迁移数据时不要运行此脚本。XXL 参数替换及任务启用见同目录 README.md。
DELIMITER $$
CREATE PROCEDURE `amp_xxl_job_250`.`migration_import_20261105`()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = 'amp_xxl_job_250' AND TABLE_NAME IN ('xxl_job_group','xxl_job_info','xxl_job_user','xxl_job_logglue','xxl_job_lock','xxl_job_registry','xxl_job_log','xxl_job_log_report') AND ENGINE = 'InnoDB') <> 8 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target tables missing or not InnoDB';
    END IF;
    SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
    START TRANSACTION WITH CONSISTENT SNAPSHOT;
    IF (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_group`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_info`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_user`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_logglue`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_lock`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_registry`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_log`) <> 0 OR
       (SELECT COUNT(*) FROM `amp_xxl_job_250`.`xxl_job_log_report`) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target is not empty; do not rerun initial migration';
    END IF;
    IF EXISTS (SELECT 1 FROM `amp_xxl_job`.`xxl_job_group` WHERE address_type <> 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Manual executor addresses require a separate migration decision';
    END IF;

    INSERT INTO `amp_xxl_job_250`.`xxl_job_group`
        (`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
    SELECT
        `id`, `app_name`, `title`, `address_type`, NULL, NOW()
    FROM `amp_xxl_job`.`xxl_job_group`;

    INSERT INTO `amp_xxl_job_250`.`xxl_job_info`
        (`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
         `schedule_type`, `schedule_conf`, `misfire_strategy`,
         `executor_route_strategy`, `executor_handler`, `executor_param`, `executor_block_strategy`,
         `executor_timeout`, `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
         `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`)
    SELECT
        `id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
        'CRON', `job_cron`, 'DO_NOTHING',
        `executor_route_strategy`, `executor_handler`, `executor_param`, `executor_block_strategy`,
        `executor_timeout`, `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
        `glue_updatetime`, `child_jobid`, 0, 0, 0
    FROM `amp_xxl_job`.`xxl_job_info`;

    INSERT INTO `amp_xxl_job_250`.`xxl_job_user`
        (`id`, `username`, `password`, `role`, `permission`)
    SELECT
        `id`, `username`, `password`, `role`, `permission`
    FROM `amp_xxl_job`.`xxl_job_user`;

    INSERT INTO `amp_xxl_job_250`.`xxl_job_logglue`
        (`id`, `job_id`, `glue_type`, `glue_source`, `glue_remark`, `add_time`, `update_time`)
    SELECT
        `id`, `job_id`, `glue_type`, `glue_source`, `glue_remark`, `add_time`, `update_time`
    FROM `amp_xxl_job`.`xxl_job_logglue`;

    INSERT INTO `amp_xxl_job_250`.`xxl_job_lock` (`lock_name`) VALUES ('schedule_lock');

    COMMIT;
    SELECT 'Initial import committed; verify counts before starting the new center' AS migration_result;
END$$
DELIMITER ;
CALL `amp_xxl_job_250`.`migration_import_20261105`();
DROP PROCEDURE `amp_xxl_job_250`.`migration_import_20261105`;
