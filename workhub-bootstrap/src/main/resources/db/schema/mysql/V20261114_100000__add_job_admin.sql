-- REQ-9f3c2a84：Job 管理动态调度（表结构 + 权限种子）。
-- 调度配置由 DynamicJobScheduler 启动时按 WorkhubJob Bean 幂等回写默认值，本迁移不插入任务种子。
-- 回滚：DROP TABLE sys_job_execution_log; DROP TABLE sys_job_config;
--       DELETE FROM sys_role_permission WHERE permission_code LIKE 'ops:job:%';
--       DELETE FROM sys_permission WHERE permission_code LIKE 'ops:job:%';

CREATE TABLE IF NOT EXISTS `sys_job_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_code` VARCHAR(64) NOT NULL COMMENT '任务编码（WorkhubJob#code）',
  `job_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
  `schedule_type` VARCHAR(16) NOT NULL COMMENT '调度类型：CRON / FIXED_DELAY',
  `schedule_value` VARCHAR(64) NOT NULL COMMENT 'cron 表达式或 fixedDelay 毫秒数',
  `default_schedule_value` VARCHAR(64) NOT NULL COMMENT '代码内默认值（误操作恢复参照）',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否调度',
  `allow_manual_trigger` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许手动触发',
  `param_desc` VARCHAR(512) NULL COMMENT '参数说明（手动触发入参格式）',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_job_config_code` (`job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统定时任务调度配置（DB 为唯一调度事实源）';

CREATE TABLE IF NOT EXISTS `sys_job_execution_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_code` VARCHAR(64) NOT NULL COMMENT '任务编码',
  `trigger_type` VARCHAR(16) NOT NULL COMMENT '触发方式：SCHEDULED / MANUAL',
  `params` TEXT NULL COMMENT '手动触发入参（原样记录）',
  `status` VARCHAR(16) NOT NULL COMMENT '执行状态：RUNNING / SUCCESS / ERROR',
  `started_at` DATETIME(3) NOT NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间',
  `duration_ms` BIGINT NULL COMMENT '耗时毫秒',
  `error_message` TEXT NULL COMMENT '失败原因（截断 4000 字符）',
  `hostname` VARCHAR(128) NULL COMMENT '执行节点主机名',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_job_exec_job_time` (`job_code`, `started_at`),
  KEY `idx_sys_job_exec_status` (`status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统定时任务执行历史（保留 90 天）';

INSERT INTO `sys_permission` (
  `permission_code`, `permission_name`, `permission_type`,
  `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`
)
SELECT permission.*
FROM (
  SELECT 'ops:job:view' AS `permission_code`,
         'Job管理查看' AS `permission_name`,
         'MENU' AS `permission_type`,
         'ops:view' AS `parent_code`,
         '/ops/jobs' AS `route_path`,
         36 AS `sort_order`,
         1 AS `enabled`,
         '系统定时任务调度与执行历史查看' AS `remark` UNION ALL
  SELECT 'ops:job:manage', 'Job管理维护', 'BUTTON', 'ops:job:view', NULL, 37, 1, '允许修改调度时间、启停任务并手动触发（支持参数）' UNION ALL
  SELECT 'ops:job:trigger', 'Job手动触发', 'BUTTON', 'ops:job:view', NULL, 38, 1, '允许带参数手动触发任务'
) AS permission
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_permission` existing
  WHERE existing.`permission_code` = permission.`permission_code`
);

-- 超级管理员直授；已有业务监测管理/采集权限的角色按能力映射授予。
INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.`permission_code` IN ('ops:job:view', 'ops:job:manage', 'ops:job:trigger')
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
  SELECT 'ops:monitor:view' AS `source_permission`, 'ops:job:view' AS `target_permission` UNION ALL
  SELECT 'ops:monitor:manage', 'ops:job:view' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:job:manage' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:job:trigger' UNION ALL
  SELECT 'ops:monitor:check', 'ops:job:trigger'
) mapping ON mapping.`source_permission` = source.`permission_code`
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_role_permission` existing
  WHERE existing.`role_id` = source.`role_id`
    AND existing.`permission_code` = mapping.`target_permission`
);
