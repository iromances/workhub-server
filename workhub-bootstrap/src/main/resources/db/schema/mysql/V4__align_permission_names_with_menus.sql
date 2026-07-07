UPDATE `sys_permission` p
JOIN (
  SELECT 'dashboard:view' AS `permission_code`, '工作台' AS `permission_name` UNION ALL
  SELECT 'intake:record:view', '需求管理' UNION ALL
  SELECT 'intake:record:create', '需求录入' UNION ALL
  SELECT 'intake:record:update', '需求编辑（兼容）' UNION ALL
  SELECT 'intake:record:delete', '删除需求' UNION ALL
  SELECT 'intake:stage:operate', '阶段流转' UNION ALL
  SELECT 'intake:ai:operate', 'AI 分析' UNION ALL
  SELECT 'intake:todo:manage', '待办管理' UNION ALL
  SELECT 'intake:attachment:manage', '附件管理' UNION ALL
  SELECT 'intake:metadata:update', '基础信息维护' UNION ALL
  SELECT 'intake:zentao:sync', '同步禅道' UNION ALL

  SELECT 'project:project:view', '项目列表' UNION ALL
  SELECT 'project:project:create', '新增项目' UNION ALL
  SELECT 'project:project:update', '编辑项目' UNION ALL
  SELECT 'project:project:delete', '删除项目' UNION ALL
  SELECT 'project:project:manage', '项目全部操作（兼容）' UNION ALL
  SELECT 'project:business-line:view', '业务线' UNION ALL
  SELECT 'project:business-line:create', '新增业务线' UNION ALL
  SELECT 'project:business-line:update', '编辑业务线' UNION ALL
  SELECT 'project:business-line:delete', '删除业务线' UNION ALL
  SELECT 'project:business-line:sync-system', '从 Git 同步系统' UNION ALL
  SELECT 'project:business-line:manage', '业务线全部操作（兼容）' UNION ALL
  SELECT 'project:system:view', '中台系统管理' UNION ALL
  SELECT 'project:system:create', '新增涉及系统' UNION ALL
  SELECT 'project:system:update', '编辑涉及系统' UNION ALL
  SELECT 'project:system:delete', '删除涉及系统' UNION ALL
  SELECT 'project:system:manage', '涉及系统全部操作（兼容）' UNION ALL
  SELECT 'project:developer:view', '团队' UNION ALL
  SELECT 'project:developer:create', '新增研发人员' UNION ALL
  SELECT 'project:developer:update', '编辑研发人员' UNION ALL
  SELECT 'project:developer:delete', '删除研发人员' UNION ALL
  SELECT 'project:developer:manage', '团队全部操作（兼容）' UNION ALL

  SELECT 'work-item:view', '工作项' UNION ALL
  SELECT 'work-item:create', '新建工作项' UNION ALL
  SELECT 'work-item:update', '编辑工作项' UNION ALL
  SELECT 'work-item:assign', '指派工作项' UNION ALL
  SELECT 'work-item:follow-up', '添加跟进记录' UNION ALL
  SELECT 'work-item:transition', '状态流转' UNION ALL
  SELECT 'work-item:manage', '工作项全部操作（兼容）' UNION ALL

  SELECT 'ops:monitor:view', '业务监测' UNION ALL
  SELECT 'ops:monitor:create', '新增业务监测' UNION ALL
  SELECT 'ops:monitor:update', '编辑业务监测' UNION ALL
  SELECT 'ops:monitor:delete', '删除业务监测' UNION ALL
  SELECT 'ops:monitor:check', '手动采集' UNION ALL
  SELECT 'ops:monitor:manage', '业务监测全部操作（兼容）' UNION ALL
  SELECT 'ops:system-alert:view', '系统预警' UNION ALL
  SELECT 'ops:system-alert:create', '新增关注子系统' UNION ALL
  SELECT 'ops:system-alert:update', '编辑关注子系统' UNION ALL
  SELECT 'ops:system-alert:delete', '删除关注子系统' UNION ALL
  SELECT 'ops:system-alert:manage', '系统预警全部操作（兼容）' UNION ALL
  SELECT 'ops:mcp-resource:view', 'MCP' UNION ALL
  SELECT 'ops:mcp-resource:create', '新增 MCP 目标' UNION ALL
  SELECT 'ops:mcp-resource:update', '编辑 MCP 目标' UNION ALL
  SELECT 'ops:mcp-resource:delete', '删除 MCP 目标' UNION ALL
  SELECT 'ops:mcp-resource:manage', 'MCP 全部操作（兼容）' UNION ALL
  SELECT 'ops:mcp-audit:view', 'MCP 审计记录' UNION ALL

  SELECT 'payment:config:view', '支付配置' UNION ALL
  SELECT 'payment:config:manage', '支付配置全部操作（兼容）' UNION ALL
  SELECT 'payment:channel:create', '新增渠道' UNION ALL
  SELECT 'payment:channel:update', '编辑渠道' UNION ALL
  SELECT 'payment:merchant:create', '新增商户' UNION ALL
  SELECT 'payment:merchant:update', '编辑商户' UNION ALL
  SELECT 'payment:param:manage', '参数维护' UNION ALL
  SELECT 'payment:credential:manage', '凭据维护' UNION ALL
  SELECT 'payment:binding:manage', '绑定维护' UNION ALL
  SELECT 'payment:secret:manage', '秘钥全部操作（兼容）' UNION ALL

  SELECT 'system:config:view', '系统配置' UNION ALL
  SELECT 'system:config:create', '新增配置' UNION ALL
  SELECT 'system:config:update', '编辑配置' UNION ALL
  SELECT 'system:config:manage', '系统配置全部操作（兼容）' UNION ALL
  SELECT 'system:user:view', '账号管理' UNION ALL
  SELECT 'system:user:create', '新增账号' UNION ALL
  SELECT 'system:user:update', '编辑账号' UNION ALL
  SELECT 'system:user:status', '启停账号' UNION ALL
  SELECT 'system:user:reset-password', '重置密码' UNION ALL
  SELECT 'system:user:assign-role', '分配角色' UNION ALL
  SELECT 'system:user:manage', '账号全部操作（兼容）' UNION ALL
  SELECT 'system:role:view', '角色管理' UNION ALL
  SELECT 'system:role:create', '新增角色' UNION ALL
  SELECT 'system:role:update', '编辑角色' UNION ALL
  SELECT 'system:role:status', '启停角色' UNION ALL
  SELECT 'system:role:delete', '删除角色' UNION ALL
  SELECT 'system:role:assign-permission', '配置权限' UNION ALL
  SELECT 'system:role:manage', '角色全部操作（兼容）' UNION ALL
  SELECT 'system:permission:view', '查看权限树' UNION ALL
  SELECT 'system:permission:manage', '权限点维护（预留）' UNION ALL
  SELECT 'system:audit:view', '权限审计'
) AS names ON names.`permission_code` = p.`permission_code`
SET p.`permission_name` = names.`permission_name`;
