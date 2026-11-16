-- MCP 与 Function Call 菜单从「运维」移至「系统管理」（用户 2026-11-14 要求，同 V20261114_220000 Job 管理菜单归位）。
-- 权限编码保持 ops:mcp-resource:* / ops:function-call:*（后端 @PreAuthorize 与已授权角色不变），仅调整菜单挂载与路由。
-- 回滚：UPDATE sys_permission SET parent_code='ops:view', route_path='/ops/mcp-resources', sort_order=33 WHERE permission_code='ops:mcp-resource:view';
--       UPDATE sys_permission SET parent_code='ops:view', route_path='/ops/function-call', sort_order=39 WHERE permission_code='ops:function-call:view';

UPDATE `sys_permission`
SET `parent_code` = 'system:view',
    `route_path` = '/system/mcp-resources',
    `sort_order` = 58
WHERE `permission_code` = 'ops:mcp-resource:view';

UPDATE `sys_permission`
SET `parent_code` = 'system:view',
    `route_path` = '/system/function-call',
    `sort_order` = 59
WHERE `permission_code` = 'ops:function-call:view';

-- 持有对应查看权限的角色补授 system:view 父菜单权限（沿用 V16 子权限映射父权限模式）。
INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT DISTINCT rp.`role_id`, 'system:view'
FROM `sys_role_permission` rp
WHERE rp.`permission_code` IN ('ops:mcp-resource:view', 'ops:function-call:view')
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_permission` existing
    WHERE existing.`role_id` = rp.`role_id`
      AND existing.`permission_code` = 'system:view'
  );
