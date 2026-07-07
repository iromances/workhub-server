ALTER TABLE `sys_user`
  ADD COLUMN `email` VARCHAR(128) NULL AFTER `display_name`,
  ADD COLUMN `mobile` VARCHAR(32) NULL AFTER `email`,
  ADD COLUMN `wecom_userid` VARCHAR(128) NULL AFTER `mobile`,
  ADD COLUMN `avatar_url` VARCHAR(512) NULL AFTER `wecom_userid`,
  ADD COLUMN `must_change_password` TINYINT(1) NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `login_fail_count` INT NOT NULL DEFAULT 0 AFTER `must_change_password`,
  ADD COLUMN `locked_until` DATETIME NULL AFTER `login_fail_count`,
  ADD COLUMN `last_login_at` DATETIME NULL AFTER `locked_until`,
  ADD COLUMN `last_login_ip` VARCHAR(64) NULL AFTER `last_login_at`,
  ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 AFTER `last_login_ip`,
  ADD KEY `idx_sys_user_status_deleted` (`status`, `deleted`),
  ADD UNIQUE KEY `uk_sys_user_wecom_userid` (`wecom_userid`);

CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_code` VARCHAR(64) NOT NULL,
  `role_name` VARCHAR(128) NOT NULL,
  `role_type` VARCHAR(32) NOT NULL DEFAULT 'CUSTOM',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `built_in` TINYINT(1) NOT NULL DEFAULT 0,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `permission_code` VARCHAR(128) NOT NULL,
  `permission_name` VARCHAR(128) NOT NULL,
  `permission_type` VARCHAR(32) NOT NULL,
  `parent_code` VARCHAR(128) NULL,
  `route_path` VARCHAR(255) NULL,
  `api_method` VARCHAR(16) NULL,
  `api_pattern` VARCHAR(255) NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`permission_code`),
  KEY `idx_sys_permission_parent` (`parent_code`, `sort_order`),
  KEY `idx_sys_permission_type` (`permission_type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`, `role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `permission_code` VARCHAR(128) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`, `permission_code`),
  KEY `idx_sys_role_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_name` VARCHAR(64) NOT NULL,
  `login_result` VARCHAR(32) NOT NULL,
  `fail_reason` VARCHAR(255) NULL,
  `ip` VARCHAR(64) NULL,
  `user_agent` VARCHAR(512) NULL,
  `occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_login_log_user_time` (`user_name`, `occurred_at`),
  KEY `idx_sys_login_log_result_time` (`login_result`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `operator_user_name` VARCHAR(64) NOT NULL,
  `permission_code` VARCHAR(128) NULL,
  `action_type` VARCHAR(64) NOT NULL,
  `target_type` VARCHAR(64) NOT NULL,
  `target_id` VARCHAR(128) NULL,
  `before_snapshot` TEXT NULL,
  `after_snapshot` TEXT NULL,
  `result` VARCHAR(32) NOT NULL,
  `error_message` TEXT NULL,
  `ip` VARCHAR(64) NULL,
  `occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_operation_log_operator_time` (`operator_user_name`, `occurred_at`),
  KEY `idx_sys_operation_log_permission_time` (`permission_code`, `occurred_at`),
  KEY `idx_sys_operation_log_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`)
SELECT * FROM (
  SELECT 'dashboard:view' AS `permission_code`, '工作台查看' AS `permission_name`, 'MENU' AS `permission_type`, NULL AS `parent_code`, '/dashboard' AS `route_path`, 10 AS `sort_order`, 1 AS `enabled`, NULL AS `remark` UNION ALL
  SELECT 'intake:record:view', '需求查看', 'MENU', NULL, '/demands', 20, 1, NULL UNION ALL
  SELECT 'intake:record:create', '需求新增', 'BUTTON', 'intake:record:view', NULL, 21, 1, NULL UNION ALL
  SELECT 'intake:record:update', '需求编辑', 'BUTTON', 'intake:record:view', NULL, 22, 1, NULL UNION ALL
  SELECT 'intake:record:delete', '需求删除', 'BUTTON', 'intake:record:view', NULL, 23, 1, NULL UNION ALL
  SELECT 'intake:stage:operate', '需求阶段动作', 'BUTTON', 'intake:record:view', NULL, 24, 1, NULL UNION ALL
  SELECT 'intake:ai:operate', '需求 AI 操作', 'BUTTON', 'intake:record:view', NULL, 25, 1, NULL UNION ALL
  SELECT 'intake:todo:manage', '需求待办管理', 'BUTTON', 'intake:record:view', NULL, 26, 1, NULL UNION ALL
  SELECT 'project:project:view', '项目查看', 'MENU', NULL, '/projects/list', 30, 1, NULL UNION ALL
  SELECT 'project:project:manage', '项目管理', 'BUTTON', 'project:project:view', NULL, 31, 1, NULL UNION ALL
  SELECT 'project:business-line:view', '业务线查看', 'MENU', NULL, '/projects/business-lines', 32, 1, NULL UNION ALL
  SELECT 'project:business-line:manage', '业务线管理', 'BUTTON', 'project:business-line:view', NULL, 33, 1, NULL UNION ALL
  SELECT 'project:system:view', '涉及系统查看', 'MENU', NULL, '/projects/systems', 34, 1, NULL UNION ALL
  SELECT 'project:system:manage', '涉及系统管理', 'BUTTON', 'project:system:view', NULL, 35, 1, NULL UNION ALL
  SELECT 'project:developer:view', '团队查看', 'MENU', NULL, '/projects/developers', 36, 1, NULL UNION ALL
  SELECT 'project:developer:manage', '团队管理', 'BUTTON', 'project:developer:view', NULL, 37, 1, NULL UNION ALL
  SELECT 'work-item:view', '工作项查看', 'MENU', NULL, '/work-items', 40, 1, NULL UNION ALL
  SELECT 'work-item:manage', '工作项管理', 'BUTTON', 'work-item:view', NULL, 41, 1, NULL UNION ALL
  SELECT 'work-item:transition', '工作项状态流转', 'BUTTON', 'work-item:view', NULL, 42, 1, NULL UNION ALL
  SELECT 'ops:monitor:view', '业务监测查看', 'MENU', NULL, '/ops/monitors', 50, 1, NULL UNION ALL
  SELECT 'ops:monitor:manage', '业务监测管理', 'BUTTON', 'ops:monitor:view', NULL, 51, 1, NULL UNION ALL
  SELECT 'ops:monitor:check', '业务监测采集', 'BUTTON', 'ops:monitor:view', NULL, 52, 1, NULL UNION ALL
  SELECT 'ops:system-alert:view', '系统预警查看', 'MENU', NULL, '/ops/system-alerts', 53, 1, NULL UNION ALL
  SELECT 'ops:system-alert:manage', '系统预警管理', 'BUTTON', 'ops:system-alert:view', NULL, 54, 1, NULL UNION ALL
  SELECT 'ops:mcp-resource:view', 'MCP 资源查看', 'MENU', NULL, '/ops/mcp-resources', 55, 1, NULL UNION ALL
  SELECT 'ops:mcp-resource:manage', 'MCP 资源管理', 'BUTTON', 'ops:mcp-resource:view', NULL, 56, 1, NULL UNION ALL
  SELECT 'ops:mcp-audit:view', 'MCP 审计查看', 'BUTTON', 'ops:mcp-resource:view', NULL, 57, 1, NULL UNION ALL
  SELECT 'payment:config:view', '支付配置查看', 'MENU', NULL, '/payment-config', 60, 1, NULL UNION ALL
  SELECT 'payment:config:manage', '支付配置管理', 'BUTTON', 'payment:config:view', NULL, 61, 1, NULL UNION ALL
  SELECT 'payment:secret:manage', '支付秘钥管理', 'BUTTON', 'payment:config:view', NULL, 62, 1, NULL UNION ALL
  SELECT 'system:config:view', '系统配置查看', 'MENU', NULL, '/system-config', 70, 1, NULL UNION ALL
  SELECT 'system:config:manage', '系统配置管理', 'BUTTON', 'system:config:view', NULL, 71, 1, NULL UNION ALL
  SELECT 'system:user:view', '账号查看', 'MENU', NULL, '/system/users', 72, 1, NULL UNION ALL
  SELECT 'system:user:manage', '账号管理', 'BUTTON', 'system:user:view', NULL, 73, 1, NULL UNION ALL
  SELECT 'system:role:view', '角色查看', 'MENU', NULL, '/system/roles', 74, 1, NULL UNION ALL
  SELECT 'system:role:manage', '角色管理', 'BUTTON', 'system:role:view', NULL, 75, 1, NULL UNION ALL
  SELECT 'system:permission:view', '权限查看', 'BUTTON', 'system:role:view', NULL, 76, 1, NULL UNION ALL
  SELECT 'system:permission:manage', '权限管理', 'BUTTON', 'system:role:view', NULL, 77, 1, NULL UNION ALL
  SELECT 'system:audit:view', '权限审计查看', 'MENU', NULL, '/system/audits', 78, 1, NULL
) AS p
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` existing WHERE existing.`permission_code` = p.`permission_code`
);

INSERT INTO `sys_role` (`role_code`, `role_name`, `role_type`, `enabled`, `built_in`, `remark`)
SELECT * FROM (
  SELECT 'SUPER_ADMIN' AS `role_code`, '超级管理员' AS `role_name`, 'BUILT_IN' AS `role_type`, 1 AS `enabled`, 1 AS `built_in`, '系统最高权限，默认绑定 admin' AS `remark` UNION ALL
  SELECT 'SYSTEM_ADMIN', '系统管理员', 'BUILT_IN', 1, 1, '系统管理类预置角色' UNION ALL
  SELECT 'PM_ADMIN', '项目管理员', 'BUILT_IN', 1, 1, '项目管理类预置角色' UNION ALL
  SELECT 'DEMAND_HANDLER', '需求处理人', 'BUILT_IN', 1, 1, '需求处理类预置角色' UNION ALL
  SELECT 'OPS_VIEWER', '运维查看员', 'BUILT_IN', 1, 1, '运维只读类预置角色' UNION ALL
  SELECT 'OPS_ADMIN', '运维管理员', 'BUILT_IN', 1, 1, '运维配置类预置角色' UNION ALL
  SELECT 'PAYMENT_ADMIN', '支付配置管理员', 'BUILT_IN', 1, 1, '支付配置类预置角色' UNION ALL
  SELECT 'NORMAL_USER', '普通用户', 'BUILT_IN', 1, 1, '普通用户预置角色'
) AS r
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role` existing WHERE existing.`role_code` = r.`role_code`
);

INSERT INTO `sys_user` (`user_name`, `display_name`, `password_hash`, `status`, `must_change_password`)
SELECT 'admin', 'WorkHub 管理员', '$2a$10$bbvD6NTlTfZt2eJ/n1XfnOoO1eE8lOreUFXCzyFmHQ0PiU39orKym', 'ACTIVE', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_user` WHERE `user_name` = 'admin'
);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `sys_user` u
JOIN `sys_role` r ON r.`role_code` = 'SUPER_ADMIN'
WHERE u.`user_name` = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user_role` ur WHERE ur.`user_id` = u.`id` AND ur.`role_id` = r.`id`
  );

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = p.`permission_code`
  );
