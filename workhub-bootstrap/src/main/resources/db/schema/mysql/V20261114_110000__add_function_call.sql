-- REQ-9f3c2a84：Function Call 统一业务库 SQL 访问（表结构 + 权限种子）。
-- SQL 模板种子由 FuncCallSchemaInitializer 启动时从 FuncCallSqlSeeds 幂等回写
-- （sql_code 已存在时不覆盖，页面维护的模板优先），本迁移不插入模板数据。
-- 回滚：DROP TABLE func_call_sql;
--       DELETE FROM sys_role_permission WHERE permission_code LIKE 'ops:function-call:%';
--       DELETE FROM sys_permission WHERE permission_code LIKE 'ops:function-call:%';

CREATE TABLE IF NOT EXISTS `func_call_sql` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sql_code` VARCHAR(64) NOT NULL COMMENT 'SQL 编码（唯一，调用方可直接传此编码）',
  `scenario_code` VARCHAR(64) NULL COMMENT '业务场景编码（一个场景可映射多条分支模板，按 branch_order 组合）',
  `branch_order` INT NOT NULL DEFAULT 1 COMMENT '同场景多分支模板的组合顺序',
  `name` VARCHAR(128) NOT NULL COMMENT '模板名称',
  `description` VARCHAR(512) NULL COMMENT '用途说明',
  `sql_template` MEDIUMTEXT NOT NULL COMMENT 'SQL 模板（:name 命名参数，值由调用方白名单校验后传入）',
  `param_desc` VARCHAR(1024) NULL COMMENT '参数说明（名称、含义、是否可空）',
  `source_module` VARCHAR(64) NULL COMMENT '来源模块（如 OPS_XXL / OPS_MQ）',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用（禁用后调用报错）',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_func_call_sql_code` (`sql_code`),
  KEY `idx_func_call_scenario` (`scenario_code`, `branch_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Function Call 场景 SQL 登记表（业务库只读查询统一入口）';

INSERT INTO `sys_permission` (
  `permission_code`, `permission_name`, `permission_type`,
  `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`
)
SELECT permission.*
FROM (
  SELECT 'ops:function-call:view' AS `permission_code`,
         'Function Call查看' AS `permission_name`,
         'MENU' AS `permission_type`,
         'ops:view' AS `parent_code`,
         '/ops/function-call' AS `route_path`,
         39 AS `sort_order`,
         1 AS `enabled`,
         '业务库场景 SQL 登记与调用查看' AS `remark` UNION ALL
  SELECT 'ops:function-call:manage', 'Function Call维护', 'BUTTON', 'ops:function-call:view', NULL, 40, 1, '允许维护场景 SQL 模板并试运行' UNION ALL
  SELECT 'ops:function-call:execute', 'Function Call试运行', 'BUTTON', 'ops:function-call:view', NULL, 41, 1, '允许带参数试运行只读 SQL'
) AS permission
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_permission` existing
  WHERE existing.`permission_code` = permission.`permission_code`
);

-- 超级管理员直授；已有业务监测管理权限的角色按能力映射授予。
INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.`permission_code` IN ('ops:function-call:view', 'ops:function-call:manage', 'ops:function-call:execute')
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
  SELECT 'ops:monitor:view' AS `source_permission`, 'ops:function-call:view' AS `target_permission` UNION ALL
  SELECT 'ops:monitor:manage', 'ops:function-call:view' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:function-call:manage' UNION ALL
  SELECT 'ops:monitor:manage', 'ops:function-call:execute'
) mapping ON mapping.`source_permission` = source.`permission_code`
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_role_permission` existing
  WHERE existing.`role_id` = source.`role_id`
    AND existing.`permission_code` = mapping.`target_permission`
);
