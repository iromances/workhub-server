INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`)
SELECT * FROM (
  SELECT 'intake:record:detail' AS `permission_code`, '查看详情' AS `permission_name`, 'BUTTON' AS `permission_type`, 'intake:record:view' AS `parent_code`, NULL AS `route_path`, 20 AS `sort_order`, 1 AS `enabled`, NULL AS `remark` UNION ALL
  SELECT 'intake:collaboration:copy', '复制协同信息', 'BUTTON', 'intake:record:view', NULL, 21, 1, NULL UNION ALL
  SELECT 'intake:zentao:open', '打开禅道', 'BUTTON', 'intake:record:view', NULL, 22, 1, NULL UNION ALL
  SELECT 'intake:requirement-folder:open', '打开需求文件夹', 'BUTTON', 'intake:record:view', NULL, 23, 1, NULL UNION ALL
  SELECT 'intake:development-plan-folder:open', '打开开发方案文件夹', 'BUTTON', 'intake:record:view', NULL, 24, 1, NULL
) AS p
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` existing WHERE existing.`permission_code` = p.`permission_code`
);

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT rp.`role_id`, p.`permission_code`
FROM `sys_role_permission` rp
JOIN `sys_permission` p ON p.`permission_code` IN (
  'intake:record:detail',
  'intake:collaboration:copy',
  'intake:zentao:open',
  'intake:requirement-folder:open',
  'intake:development-plan-folder:open'
)
WHERE rp.`permission_code` = 'intake:record:view'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` existing
    WHERE existing.`role_id` = rp.`role_id`
      AND existing.`permission_code` = p.`permission_code`
  );

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = p.`permission_code`
  );
