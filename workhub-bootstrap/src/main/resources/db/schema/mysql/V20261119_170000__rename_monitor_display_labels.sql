-- 用户确认的展示名称调整；只改文案，保持权限编码、路径和行为不变。
UPDATE sys_permission
SET permission_name = CASE
      WHEN permission_code LIKE 'ops:monitor:%' THEN REPLACE(permission_name, '业务监测', '服务监测')
      WHEN permission_code LIKE 'ops:accounting:%' THEN REPLACE(permission_name, '账务监测', '业务监测')
      ELSE permission_name END
WHERE permission_code LIKE 'ops:monitor:%' OR permission_code LIKE 'ops:accounting:%';
-- pm_agent_tool 已由 V20261110_130000 删除；工具展示文案由前端映射维护。
