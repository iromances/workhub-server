-- Job 管理菜单从「运维」移至「系统管理」（用户 2026-11-14 要求，来源 REQ-9f3c2a84 后续调整）。
-- 权限编码保持 ops:job:*（后端 @PreAuthorize 与已授权角色不变），仅调整菜单挂载与路由。
-- 回滚：UPDATE sys_permission SET parent_code='ops:view', route_path='/ops/jobs', sort_order=36 WHERE permission_code='ops:job:view';

UPDATE `sys_permission`
SET `parent_code` = 'system:view',
    `route_path` = '/system/jobs',
    `sort_order` = 57
WHERE `permission_code` = 'ops:job:view';

-- 持有 Job 管理的角色补授 system:view 父菜单权限（沿用 V16 子权限映射父权限模式）。
INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT rp.`role_id`, 'system:view'
FROM `sys_role_permission` rp
WHERE rp.`permission_code` = 'ops:job:view'
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_permission` existing
    WHERE existing.`role_id` = rp.`role_id`
      AND existing.`permission_code` = 'system:view'
  );
