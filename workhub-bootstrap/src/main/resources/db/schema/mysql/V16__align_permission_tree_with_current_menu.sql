INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`)
SELECT * FROM (
  SELECT 'ops:view' AS `permission_code`, '运维' AS `permission_name`, 'MENU' AS `permission_type`, NULL AS `parent_code`, '/ops' AS `route_path`, 30 AS `sort_order`, 1 AS `enabled`, '权限配置目录节点，对齐前端侧边栏' AS `remark` UNION ALL
  SELECT 'project:view', '项目管理', 'MENU', NULL, '/projects', 40, 1, '权限配置目录节点，对齐前端侧边栏' UNION ALL
  SELECT 'system:view', '系统管理', 'MENU', NULL, '/system', 50, 1, '权限配置目录节点，对齐前端侧边栏'
) AS p
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` existing WHERE existing.`permission_code` = p.`permission_code`
);

UPDATE `sys_permission` p
JOIN (
  SELECT 'dashboard:view' AS `permission_code`, '工作台' AS `permission_name`, NULL AS `parent_code`, '/dashboard' AS `route_path`, 10 AS `sort_order` UNION ALL
  SELECT 'intake:record:view', '需求管理', NULL, '/demands', 20 UNION ALL
  SELECT 'ops:view', '运维', NULL, '/ops', 30 UNION ALL
  SELECT 'ops:monitor:view', '业务监测', 'ops:view', '/ops/monitors', 31 UNION ALL
  SELECT 'ops:system-alert:view', '系统预警', 'ops:view', '/ops/system-alerts', 32 UNION ALL
  SELECT 'ops:mcp-resource:view', 'MCP', 'ops:view', '/ops/mcp-resources', 33 UNION ALL
  SELECT 'project:view', '项目管理', NULL, '/projects', 40 UNION ALL
  SELECT 'project:project:view', '项目列表', 'project:view', '/projects/list', 41 UNION ALL
  SELECT 'project:business-line:view', '业务线', 'project:view', '/projects/business-lines', 42 UNION ALL
  SELECT 'project:system:view', '中台系统管理', 'project:view', '/projects/systems', 43 UNION ALL
  SELECT 'project:developer:view', '团队', 'project:view', '/projects/developers', 44 UNION ALL
  SELECT 'payment:config:view', '支付配置', 'project:view', '/payment-config', 45 UNION ALL
  SELECT 'system:view', '系统管理', NULL, '/system', 50 UNION ALL
  SELECT 'system:config:view', '系统配置', 'system:view', '/system-config', 51 UNION ALL
  SELECT 'system:ai-config:view', 'AI配置', 'system:view', '/system/ai-config', 52 UNION ALL
  SELECT 'system:user:view', '账号管理', 'system:view', '/system/users', 53 UNION ALL
  SELECT 'system:role:view', '角色管理', 'system:view', '/system/roles', 54 UNION ALL
  SELECT 'system:audit:view', '权限审计', 'system:view', '/system/audits', 55
) AS menu ON menu.`permission_code` = p.`permission_code`
SET p.`permission_name` = menu.`permission_name`,
    p.`permission_type` = 'MENU',
    p.`parent_code` = menu.`parent_code`,
    p.`route_path` = menu.`route_path`,
    p.`sort_order` = menu.`sort_order`,
    p.`enabled` = 1;

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND p.`permission_code` IN ('ops:view', 'project:view', 'system:view')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = p.`permission_code`
  );

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT DISTINCT rp.`role_id`, mapping.`parent_permission_code`
FROM `sys_role_permission` rp
JOIN (
  SELECT 'ops:monitor:view' AS `child_permission_code`, 'ops:view' AS `parent_permission_code` UNION ALL
  SELECT 'ops:system-alert:view', 'ops:view' UNION ALL
  SELECT 'ops:mcp-resource:view', 'ops:view' UNION ALL
  SELECT 'project:project:view', 'project:view' UNION ALL
  SELECT 'project:business-line:view', 'project:view' UNION ALL
  SELECT 'project:system:view', 'project:view' UNION ALL
  SELECT 'project:developer:view', 'project:view' UNION ALL
  SELECT 'payment:config:view', 'project:view' UNION ALL
  SELECT 'system:config:view', 'system:view' UNION ALL
  SELECT 'system:ai-config:view', 'system:view' UNION ALL
  SELECT 'system:user:view', 'system:view' UNION ALL
  SELECT 'system:role:view', 'system:view' UNION ALL
  SELECT 'system:audit:view', 'system:view'
) AS mapping ON mapping.`child_permission_code` = rp.`permission_code`
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_permission` existing
  WHERE existing.`role_id` = rp.`role_id`
    AND existing.`permission_code` = mapping.`parent_permission_code`
);
