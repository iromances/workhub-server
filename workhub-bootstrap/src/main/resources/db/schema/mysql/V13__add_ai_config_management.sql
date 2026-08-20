CREATE TABLE IF NOT EXISTS `ai_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `provider_code` varchar(64) NOT NULL COMMENT '接入配置编码',
  `provider_name` varchar(128) NOT NULL COMMENT '接入配置名称',
  `channel_type` varchar(16) NOT NULL COMMENT '调用通道：API/CLI',
  `vendor` varchar(32) DEFAULT NULL COMMENT '接入供应商：OPENROUTER/XIANXINGTONG，可为空',
  `model_provider` varchar(32) NOT NULL COMMENT '模型厂商：OPENAI/ANTHROPIC/ZHIPU/DEEPSEEK/QWEN',
  `api_protocol` varchar(32) DEFAULT NULL COMMENT 'API协议：OPENROUTER/OPENAI_COMPATIBLE/CUSTOM',
  `api_base_url` varchar(512) DEFAULT NULL COMMENT 'API Base URL',
  `api_key` varchar(1024) DEFAULT NULL COMMENT 'API Key，按本次需求明文存储',
  `cli_command` varchar(512) DEFAULT NULL COMMENT 'CLI命令路径或命令行',
  `cli_working_directory` varchar(512) DEFAULT NULL COMMENT 'CLI工作目录',
  `connect_timeout_seconds` int DEFAULT NULL COMMENT '连接超时秒数',
  `read_timeout_seconds` int DEFAULT NULL COMMENT '读取超时秒数',
  `call_timeout_seconds` int DEFAULT NULL COMMENT '调用总超时秒数',
  `site_url` varchar(512) DEFAULT NULL COMMENT 'OpenRouter Referer',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'OpenRouter应用名',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_provider_code` (`provider_code`),
  KEY `idx_ai_provider_enabled` (`channel_type`, `vendor`, `model_provider`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI接入配置';

CREATE TABLE IF NOT EXISTS `ai_use_case_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `use_case_code` varchar(128) NOT NULL COMMENT 'AI业务场景编码',
  `use_case_name` varchar(128) NOT NULL COMMENT 'AI业务场景名称',
  `domain` varchar(32) NOT NULL COMMENT '领域：SYSTEM/INTAKE/MCP/OPS',
  `description` varchar(1024) DEFAULT NULL COMMENT '说明',
  `provider_config_id` bigint NOT NULL COMMENT 'AI接入配置ID',
  `model` varchar(128) DEFAULT NULL COMMENT '实际模型名',
  `reasoning_level` varchar(32) DEFAULT NULL COMMENT '推理强度：LOW/MEDIUM/HIGH/ULTRA',
  `speed_mode` varchar(32) DEFAULT NULL COMMENT '速度模式：STANDARD/FAST',
  `timeout_seconds` int DEFAULT NULL COMMENT 'useCase调用超时秒数',
  `json_schema_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用结构化schema输出',
  `schema_classpath` varchar(512) DEFAULT NULL COMMENT 'JSON Schema classpath',
  `prompt_template` longtext COMMENT '提示词模板',
  `prompt_variables_desc` longtext COMMENT '提示词变量说明',
  `prompt_version` int NOT NULL DEFAULT '1' COMMENT '提示词版本',
  `prompt_checksum` varchar(128) DEFAULT NULL COMMENT '提示词校验摘要',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_use_case_code` (`use_case_code`),
  KEY `idx_ai_use_case_domain` (`domain`, `enabled`),
  KEY `idx_ai_use_case_provider` (`provider_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI业务场景配置与提示词模板';

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `parent_code`, `route_path`, `sort_order`, `enabled`, `remark`)
SELECT * FROM (
  SELECT 'system:ai-config:view' AS `permission_code`, 'AI配置' AS `permission_name`, 'MENU' AS `permission_type`, NULL AS `parent_code`, '/system/ai-config' AS `route_path`, 74 AS `sort_order`, 1 AS `enabled`, NULL AS `remark` UNION ALL
  SELECT 'system:ai-config:create', '新增AI配置', 'BUTTON', 'system:ai-config:view', NULL, 75, 1, NULL UNION ALL
  SELECT 'system:ai-config:update', '编辑AI配置', 'BUTTON', 'system:ai-config:view', NULL, 76, 1, NULL UNION ALL
  SELECT 'system:ai-config:manage', 'AI配置全部操作（兼容）', 'BUTTON', 'system:ai-config:view', NULL, 77, 1, NULL
) AS p
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission` existing WHERE existing.`permission_code` = p.`permission_code`
);

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`permission_code`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND p.`permission_code` IN ('system:ai-config:view', 'system:ai-config:create', 'system:ai-config:update', 'system:ai-config:manage')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp WHERE rp.`role_id` = r.`id` AND rp.`permission_code` = p.`permission_code`
  );

INSERT INTO `sys_role_permission` (`role_id`, `permission_code`)
SELECT rp.`role_id`, mapping.`new_permission_code`
FROM `sys_role_permission` rp
JOIN (
  SELECT 'system:ai-config:manage' AS `old_permission_code`, 'system:ai-config:create' AS `new_permission_code` UNION ALL
  SELECT 'system:ai-config:manage', 'system:ai-config:update'
) AS mapping ON mapping.`old_permission_code` = rp.`permission_code`
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_permission` existing
  WHERE existing.`role_id` = rp.`role_id`
    AND existing.`permission_code` = mapping.`new_permission_code`
);
