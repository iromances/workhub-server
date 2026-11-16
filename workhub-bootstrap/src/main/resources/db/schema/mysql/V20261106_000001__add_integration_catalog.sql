CREATE TABLE IF NOT EXISTS `sys_integration_system` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `system_code` VARCHAR(64) NOT NULL COMMENT '系统编码：ZENTAO/JENKINS/GITLAB 等，内置探针按编码分派',
  `system_name` VARCHAR(128) NOT NULL COMMENT '系统名称',
  `integration_method` VARCHAR(32) NOT NULL COMMENT '集成方式：REST_API/REST_AND_LOCAL_GIT',
  `auth_type` VARCHAR(32) NOT NULL COMMENT '认证方式：ACCOUNT_PASSWORD/BASIC_API_TOKEN/PAT',
  `config_groups` VARCHAR(512) DEFAULT NULL COMMENT '关联 sys_config 配置组 CSV，支持尾部点前缀组',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_system_code` (`system_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统集成台账';

CREATE TABLE IF NOT EXISTS `sys_integration_capability` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `system_id` BIGINT NOT NULL COMMENT '所属集成系统ID',
  `capability_code` VARCHAR(64) NOT NULL COMMENT '能力编码，系统内唯一',
  `capability_name` VARCHAR(128) NOT NULL COMMENT '能力名称',
  `capability_type` VARCHAR(16) NOT NULL COMMENT '能力类型：READ/WRITE',
  `impl_status` VARCHAR(16) NOT NULL COMMENT '实现状态：ACTIVE/PLANNED',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '能力说明',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_capability_code` (`system_id`, `capability_code`),
  CONSTRAINT `fk_integration_capability_system` FOREIGN KEY (`system_id`) REFERENCES `sys_integration_system` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统集成接入能力目录';

INSERT INTO `sys_integration_system` (`system_code`, `system_name`, `integration_method`, `auth_type`, `config_groups`, `enabled`, `sort_order`, `remark`)
SELECT * FROM (
  SELECT 'ZENTAO' AS `system_code`, '禅道' AS `system_name`, 'REST_API' AS `integration_method`, 'ACCOUNT_PASSWORD' AS `auth_type`,
         'zentao.base,zentao.mapping' AS `config_groups`, 1 AS `enabled`, 10 AS `sort_order`,
         '提测链路创建测试单；凭据在系统配置 zentao.base 组维护' AS `remark` UNION ALL
  SELECT 'JENKINS', 'Jenkins', 'REST_API', 'BASIC_API_TOKEN', 'agent.jenkins,intake.submitTest', 1, 20,
         '数字员工构建测试与发布、提测触发构建；实例与 Job 绑定在 agent.jenkins/policy 维护' UNION ALL
  SELECT 'GITLAB', 'GitLab', 'REST_AND_LOCAL_GIT', 'PAT', 'gitlab.global,gitlab.,agent.gitlab', 1, 30,
         '仓库同步、候选分支与 Draft MR 交付；凭据在系统配置 gitlab 组维护'
) AS s
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_integration_system` existing WHERE existing.`system_code` = s.`system_code`
);

INSERT INTO `sys_integration_capability` (`system_id`, `capability_code`, `capability_name`, `capability_type`, `impl_status`, `description`, `enabled`, `sort_order`)
SELECT sys.`id`, c.`capability_code`, c.`capability_name`, c.`capability_type`, c.`impl_status`, c.`description`, 1, c.`sort_order`
FROM `sys_integration_system` sys
JOIN (
  SELECT 'ZENTAO' AS `system_code`, 'exchange-token' AS `capability_code`, '换Token' AS `capability_name`, 'READ' AS `capability_type`, 'ACTIVE' AS `impl_status`,
         '账密换取禅道访问 Token（POST /api.php/v1/tokens）' AS `description`, 10 AS `sort_order` UNION ALL
  SELECT 'ZENTAO', 'match-build', '匹配版本', 'READ', 'ACTIVE', '按研发分支名匹配禅道项目下已有版本（build）', 20 UNION ALL
  SELECT 'ZENTAO', 'create-test-task', '创建测试单', 'WRITE', 'ACTIVE', '确认提测时创建禅道测试单（v2 testtasks）', 30 UNION ALL
  SELECT 'ZENTAO', 'create-task', '创建任务', 'WRITE', 'PLANNED', '规划中：在禅道创建任务/工单', 40 UNION ALL
  SELECT 'JENKINS', 'probe', '只读探针', 'READ', 'ACTIVE', '核对 Job 可构建状态与参数清单，不触发构建', 10 UNION ALL
  SELECT 'JENKINS', 'submit-build', '触发构建', 'WRITE', 'ACTIVE', '提交参数化构建并跟踪队列与构建状态', 20 UNION ALL
  SELECT 'JENKINS', 'query-state', '队列/构建状态查询', 'READ', 'ACTIVE', '查询队列与构建进度、结果', 30 UNION ALL
  SELECT 'JENKINS', 'query-receipt', '构建回执查询', 'READ', 'ACTIVE', '读取归档的结构化构建/部署回执产物', 40 UNION ALL
  SELECT 'JENKINS', 'find-lost-response', '丢失响应查找', 'READ', 'ACTIVE', '按关联参数与请求标识找回丢失的构建回执，不改键重发', 50 UNION ALL
  SELECT 'GITLAB', 'list-group-projects', '列组仓库', 'READ', 'ACTIVE', '按业务线 GitLab 组列出可访问仓库', 10 UNION ALL
  SELECT 'GITLAB', 'sync-repositories', '仓库同步', 'READ', 'ACTIVE', 'fetch/clone 组内仓库到本地工作区', 20 UNION ALL
  SELECT 'GITLAB', 'create-candidate-branch', '创建候选分支', 'WRITE', 'ACTIVE', '为候选修改在目标仓库创建分支', 30 UNION ALL
  SELECT 'GITLAB', 'create-draft-mr', '创建Draft MR', 'WRITE', 'ACTIVE', '以 Draft MR 形式交付候选修改并记录回执', 40 UNION ALL
  SELECT 'GITLAB', 'merge-request', '查/合并MR', 'READ', 'ACTIVE', '查询合并请求状态与合并结果', 50
) AS c ON c.`system_code` = sys.`system_code`
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_integration_capability` existing
  WHERE existing.`system_id` = sys.`id` AND existing.`capability_code` = c.`capability_code`
);

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`)
SELECT * FROM (
  SELECT 'system:integration:view' AS `permission_code`, '系统集成' AS `permission_name`, 'MENU' AS `permission_type`, 'system:view' AS `parent_code`, '/system/integrations' AS `route_path`, 56 AS `sort_order`, 1 AS `enabled`, NULL AS `remark` UNION ALL
  SELECT 'system:integration:create', '新增集成系统', 'BUTTON', 'system:integration:view', NULL, 57, 1, NULL UNION ALL
  SELECT 'system:integration:update', '编辑集成系统', 'BUTTON', 'system:integration:view', NULL, 58, 1, NULL
) AS p
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` existing WHERE existing.`permission_code` = p.`permission_code`
);

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND p.`permission_code` IN ('system:integration:view', 'system:integration:create', 'system:integration:update')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = p.`permission_code`
  );
