INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`)
SELECT * FROM (
  SELECT 'intake:attachment:manage' AS `permission_code`, '需求附件维护' AS `permission_name`, 'BUTTON' AS `permission_type`, 'intake:record:view' AS `parent_code`, NULL AS `route_path`, 27 AS `sort_order`, 1 AS `enabled`, NULL AS `remark` UNION ALL
  SELECT 'intake:metadata:update', '需求基础信息维护', 'BUTTON', 'intake:record:view', NULL, 28, 1, NULL UNION ALL
  SELECT 'intake:zentao:sync', '需求同步禅道', 'BUTTON', 'intake:record:view', NULL, 29, 1, NULL UNION ALL

  SELECT 'project:project:create', '项目新增', 'BUTTON', 'project:project:view', NULL, 31, 1, NULL UNION ALL
  SELECT 'project:project:update', '项目编辑', 'BUTTON', 'project:project:view', NULL, 32, 1, NULL UNION ALL
  SELECT 'project:project:delete', '项目删除', 'BUTTON', 'project:project:view', NULL, 33, 1, NULL UNION ALL
  SELECT 'project:business-line:create', '业务线新增', 'BUTTON', 'project:business-line:view', NULL, 34, 1, NULL UNION ALL
  SELECT 'project:business-line:update', '业务线编辑', 'BUTTON', 'project:business-line:view', NULL, 35, 1, NULL UNION ALL
  SELECT 'project:business-line:delete', '业务线删除', 'BUTTON', 'project:business-line:view', NULL, 36, 1, NULL UNION ALL
  SELECT 'project:business-line:sync-system', '业务线系统同步', 'BUTTON', 'project:business-line:view', NULL, 37, 1, NULL UNION ALL
  SELECT 'project:system:create', '涉及系统新增', 'BUTTON', 'project:system:view', NULL, 38, 1, NULL UNION ALL
  SELECT 'project:system:update', '涉及系统编辑', 'BUTTON', 'project:system:view', NULL, 39, 1, NULL UNION ALL
  SELECT 'project:system:delete', '涉及系统删除', 'BUTTON', 'project:system:view', NULL, 40, 1, NULL UNION ALL
  SELECT 'project:developer:create', '团队成员新增', 'BUTTON', 'project:developer:view', NULL, 41, 1, NULL UNION ALL
  SELECT 'project:developer:update', '团队成员编辑', 'BUTTON', 'project:developer:view', NULL, 42, 1, NULL UNION ALL
  SELECT 'project:developer:delete', '团队成员删除', 'BUTTON', 'project:developer:view', NULL, 43, 1, NULL UNION ALL

  SELECT 'work-item:create', '工作项新增', 'BUTTON', 'work-item:view', NULL, 43, 1, NULL UNION ALL
  SELECT 'work-item:update', '工作项编辑', 'BUTTON', 'work-item:view', NULL, 44, 1, NULL UNION ALL
  SELECT 'work-item:assign', '工作项指派', 'BUTTON', 'work-item:view', NULL, 45, 1, NULL UNION ALL
  SELECT 'work-item:follow-up', '工作项跟进记录', 'BUTTON', 'work-item:view', NULL, 46, 1, NULL UNION ALL

  SELECT 'ops:monitor:create', '业务监测新增', 'BUTTON', 'ops:monitor:view', NULL, 51, 1, NULL UNION ALL
  SELECT 'ops:monitor:update', '业务监测编辑', 'BUTTON', 'ops:monitor:view', NULL, 52, 1, NULL UNION ALL
  SELECT 'ops:monitor:delete', '业务监测删除', 'BUTTON', 'ops:monitor:view', NULL, 53, 1, NULL UNION ALL
  SELECT 'ops:system-alert:create', '系统预警子系统新增', 'BUTTON', 'ops:system-alert:view', NULL, 55, 1, NULL UNION ALL
  SELECT 'ops:system-alert:update', '系统预警子系统编辑', 'BUTTON', 'ops:system-alert:view', NULL, 56, 1, NULL UNION ALL
  SELECT 'ops:system-alert:delete', '系统预警子系统删除', 'BUTTON', 'ops:system-alert:view', NULL, 57, 1, NULL UNION ALL
  SELECT 'ops:mcp-resource:create', 'MCP 资源新增', 'BUTTON', 'ops:mcp-resource:view', NULL, 58, 1, NULL UNION ALL
  SELECT 'ops:mcp-resource:update', 'MCP 资源编辑', 'BUTTON', 'ops:mcp-resource:view', NULL, 59, 1, NULL UNION ALL
  SELECT 'ops:mcp-resource:delete', 'MCP 资源删除', 'BUTTON', 'ops:mcp-resource:view', NULL, 60, 1, NULL UNION ALL

  SELECT 'payment:channel:create', '支付渠道新增', 'BUTTON', 'payment:config:view', NULL, 63, 1, NULL UNION ALL
  SELECT 'payment:channel:update', '支付渠道编辑', 'BUTTON', 'payment:config:view', NULL, 64, 1, NULL UNION ALL
  SELECT 'payment:merchant:create', '支付商户新增', 'BUTTON', 'payment:config:view', NULL, 65, 1, NULL UNION ALL
  SELECT 'payment:merchant:update', '支付商户编辑', 'BUTTON', 'payment:config:view', NULL, 66, 1, NULL UNION ALL
  SELECT 'payment:param:manage', '支付参数维护', 'BUTTON', 'payment:config:view', NULL, 67, 1, NULL UNION ALL
  SELECT 'payment:credential:manage', '支付凭据维护', 'BUTTON', 'payment:config:view', NULL, 68, 1, NULL UNION ALL
  SELECT 'payment:binding:manage', '支付绑定维护', 'BUTTON', 'payment:config:view', NULL, 69, 1, NULL UNION ALL

  SELECT 'system:config:create', '系统配置新增', 'BUTTON', 'system:config:view', NULL, 72, 1, NULL UNION ALL
  SELECT 'system:config:update', '系统配置编辑', 'BUTTON', 'system:config:view', NULL, 73, 1, NULL UNION ALL
  SELECT 'system:user:create', '账号新增', 'BUTTON', 'system:user:view', NULL, 74, 1, NULL UNION ALL
  SELECT 'system:user:update', '账号编辑', 'BUTTON', 'system:user:view', NULL, 75, 1, NULL UNION ALL
  SELECT 'system:user:status', '账号启停', 'BUTTON', 'system:user:view', NULL, 76, 1, NULL UNION ALL
  SELECT 'system:user:reset-password', '账号重置密码', 'BUTTON', 'system:user:view', NULL, 77, 1, NULL UNION ALL
  SELECT 'system:user:assign-role', '账号分配角色', 'BUTTON', 'system:user:view', NULL, 78, 1, NULL UNION ALL
  SELECT 'system:role:create', '角色新增', 'BUTTON', 'system:role:view', NULL, 79, 1, NULL UNION ALL
  SELECT 'system:role:update', '角色编辑', 'BUTTON', 'system:role:view', NULL, 80, 1, NULL UNION ALL
  SELECT 'system:role:status', '角色启停', 'BUTTON', 'system:role:view', NULL, 81, 1, NULL UNION ALL
  SELECT 'system:role:delete', '角色删除', 'BUTTON', 'system:role:view', NULL, 82, 1, NULL UNION ALL
  SELECT 'system:role:assign-permission', '角色配置权限', 'BUTTON', 'system:role:view', NULL, 83, 1, NULL
) AS p
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` existing WHERE existing.`permission_code` = p.`permission_code`
);

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = p.`permission_code`
  );

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT rp.`role_id`, mapping.`new_permission_code`
FROM `sys_role_permission` rp
JOIN (
  SELECT 'intake:record:update' AS `old_permission_code`, 'intake:attachment:manage' AS `new_permission_code` UNION ALL
  SELECT 'intake:record:update', 'intake:metadata:update' UNION ALL
  SELECT 'intake:ai:operate', 'intake:zentao:sync' UNION ALL
  SELECT 'project:project:manage', 'project:project:create' UNION ALL
  SELECT 'project:project:manage', 'project:project:update' UNION ALL
  SELECT 'project:project:manage', 'project:project:delete' UNION ALL
  SELECT 'project:business-line:manage', 'project:business-line:create' UNION ALL
  SELECT 'project:business-line:manage', 'project:business-line:update' UNION ALL
  SELECT 'project:business-line:manage', 'project:business-line:delete' UNION ALL
  SELECT 'project:business-line:manage', 'project:business-line:sync-system' UNION ALL
  SELECT 'project:system:manage', 'project:system:create' UNION ALL
  SELECT 'project:system:manage', 'project:system:update' UNION ALL
  SELECT 'project:system:manage', 'project:system:delete' UNION ALL
  SELECT 'project:developer:manage', 'project:developer:create' UNION ALL
  SELECT 'project:developer:manage', 'project:developer:update' UNION ALL
  SELECT 'project:developer:manage', 'project:developer:delete' UNION ALL
  SELECT 'work-item:manage', 'work-item:create' UNION ALL
  SELECT 'work-item:manage', 'work-item:update' UNION ALL
  SELECT 'work-item:manage', 'work-item:assign' UNION ALL
  SELECT 'work-item:manage', 'work-item:follow-up' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:monitor:create' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:monitor:update' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:monitor:delete' UNION ALL
  SELECT 'ops:system-alert:manage', 'ops:system-alert:create' UNION ALL
  SELECT 'ops:system-alert:manage', 'ops:system-alert:update' UNION ALL
  SELECT 'ops:system-alert:manage', 'ops:system-alert:delete' UNION ALL
  SELECT 'ops:mcp-resource:manage', 'ops:mcp-resource:create' UNION ALL
  SELECT 'ops:mcp-resource:manage', 'ops:mcp-resource:update' UNION ALL
  SELECT 'ops:mcp-resource:manage', 'ops:mcp-resource:delete' UNION ALL
  SELECT 'payment:config:manage', 'payment:channel:create' UNION ALL
  SELECT 'payment:config:manage', 'payment:channel:update' UNION ALL
  SELECT 'payment:config:manage', 'payment:merchant:create' UNION ALL
  SELECT 'payment:config:manage', 'payment:merchant:update' UNION ALL
  SELECT 'payment:config:manage', 'payment:param:manage' UNION ALL
  SELECT 'payment:config:manage', 'payment:credential:manage' UNION ALL
  SELECT 'payment:config:manage', 'payment:binding:manage' UNION ALL
  SELECT 'system:config:manage', 'system:config:create' UNION ALL
  SELECT 'system:config:manage', 'system:config:update' UNION ALL
  SELECT 'system:user:manage', 'system:user:create' UNION ALL
  SELECT 'system:user:manage', 'system:user:update' UNION ALL
  SELECT 'system:user:manage', 'system:user:status' UNION ALL
  SELECT 'system:user:manage', 'system:user:reset-password' UNION ALL
  SELECT 'system:user:manage', 'system:user:assign-role' UNION ALL
  SELECT 'system:role:manage', 'system:role:create' UNION ALL
  SELECT 'system:role:manage', 'system:role:update' UNION ALL
  SELECT 'system:role:manage', 'system:role:status' UNION ALL
  SELECT 'system:role:manage', 'system:role:delete' UNION ALL
  SELECT 'system:role:manage', 'system:role:assign-permission'
) AS mapping ON mapping.`old_permission_code` = rp.`permission_code`
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_permission` existing
  WHERE existing.`role_id` = rp.`role_id`
    AND existing.`permission_code` = mapping.`new_permission_code`
);
