UPDATE `sys_permission` p
JOIN (
  SELECT 'intake:record:detail' AS `permission_code`, '详情' AS `permission_name` UNION ALL
  SELECT 'intake:requirement-folder:open', '需求文件' UNION ALL
  SELECT 'intake:development-plan-folder:open', '开发方案'
) names ON names.`permission_code` = p.`permission_code`
SET p.`permission_name` = names.`permission_name`;
