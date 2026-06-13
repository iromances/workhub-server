CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_name` VARCHAR(64) NOT NULL,
  `display_name` VARCHAR(128) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_user_name` (`user_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_developer_resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_name` VARCHAR(64) NOT NULL,
  `display_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_developer_resource_user_name` (`user_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_config_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_group` VARCHAR(128) NOT NULL,
  `config_key` VARCHAR(128) NOT NULL,
  `config_name` VARCHAR(128) NOT NULL,
  `value_type` VARCHAR(32) NOT NULL,
  `plain_value` TEXT NULL,
  `encrypted_value` TEXT NULL,
  `masked_value` VARCHAR(255) NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mcp_resource_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `resource_type` VARCHAR(32) NOT NULL,
  `target_key` VARCHAR(128) NOT NULL,
  `business_line_code` VARCHAR(128) NOT NULL,
  `environment_code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `host` VARCHAR(255) NOT NULL,
  `port` INT NOT NULL,
  `database_schema` VARCHAR(128) NULL,
  `username` VARCHAR(128) NULL,
  `secret_ref` VARCHAR(255) NULL,
  `password_encrypted` TEXT NULL,
  `ssh_password_encrypted` TEXT NULL,
  `ssh_bastion_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `ssh_bastion_host` VARCHAR(255) NULL,
  `ssh_bastion_port` INT NULL,
  `ssh_bastion_user` VARCHAR(128) NULL,
  `ssh_bastion_password_encrypted` TEXT NULL,
  `ssh_identity_file` VARCHAR(512) NULL,
  `allowed_services_json` TEXT NULL,
  `allowed_log_paths_json` TEXT NULL,
  `profiles_json` TEXT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_resource_config_target_key` (`target_key`),
  KEY `idx_mcp_resource_config_list` (`business_line_code`, `environment_code`, `resource_type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mcp_resource_business_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `resource_id` BIGINT NOT NULL,
  `business_line_code` VARCHAR(128) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mcp_resource_business_line` (`resource_id`, `business_line_code`),
  KEY `idx_mcp_resource_business_line_code` (`business_line_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sys_config_item` (
  `config_group`, `config_key`, `config_name`, `value_type`, `plain_value`, `enabled`, `remark`
) VALUES (
  'knowledge.project',
  'vaultPath',
  '项目知识库地址',
  'TEXT',
  '/Users/aslight/Obsidian Vault/Company Obsidian Vault',
  1,
  'AI 任务评估遇到不确定业务口径时可参考的本地 Obsidian Vault 路径'
), (
  'ai.codexCli',
  'model',
  'Codex CLI 模型',
  'TEXT',
  'gpt-5.5',
  1,
  'AI 任务评估调用 Codex CLI 时使用的模型，优先级高于 application.yml'
), (
  'ai.codexCli',
  'reasoningEffort',
  'Codex CLI 推理强度',
  'TEXT',
  'xhigh',
  1,
  'AI 任务评估调用 Codex CLI 时使用的推理强度，xhigh 表示最高'
), (
  'intake.requirementFolder',
  'basePath',
  '需求文件夹基础目录',
  'TEXT',
  '/Users/aslight/Desktop/进行中的需求',
  1,
  '需求管理打开需求文件夹时使用的本地基础目录'
);

CREATE TABLE `pm_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_code` VARCHAR(64) NOT NULL,
  `project_name` VARCHAR(128) NOT NULL,
  `project_type` VARCHAR(32) NOT NULL,
  `business_line` VARCHAR(128) NOT NULL DEFAULT '',
  `project_status` VARCHAR(32) NOT NULL,
  `owner_user_name` VARCHAR(64) NOT NULL,
  `description` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_project_code` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_business_line_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line` VARCHAR(128) NOT NULL,
  `member_user_name` VARCHAR(64) NOT NULL,
  `member_display_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_business_line_member` (`business_line`, `member_user_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_business_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line_name` VARCHAR(128) NOT NULL,
  `gitlab_group_name` VARCHAR(255) NULL,
  `description` TEXT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_business_line_name` (`business_line_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ops_monitor_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `monitor_type` VARCHAR(32) NOT NULL,
  `monitor_key` VARCHAR(128) NOT NULL,
  `business_line_code` VARCHAR(128) NOT NULL,
  `environment_code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `admin_base_url` VARCHAR(512) NULL,
  `username` VARCHAR(128) NULL,
  `password_encrypted` TEXT NULL,
  `xxl_job_database_name` VARCHAR(128) NULL,
  `executor_app_name` VARCHAR(128) NULL,
  `job_handler` VARCHAR(255) NULL,
  `job_desc` VARCHAR(255) NULL,
  `mq_topic` VARCHAR(255) NULL,
  `mq_consumer_group` VARCHAR(255) NULL,
  `mq_lag_threshold` INT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `last_status` VARCHAR(32) NULL,
  `last_message` VARCHAR(1000) NULL,
  `last_checked_at` DATETIME NULL,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_monitor_config_key` (`monitor_key`),
  KEY `idx_ops_monitor_config_list` (`business_line_code`, `environment_code`, `monitor_type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ops_system_alert_subsystem` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line_code` VARCHAR(128) NOT NULL,
  `environment_code` VARCHAR(64) NOT NULL,
  `subsystem_name` VARCHAR(128) NOT NULL,
  `service_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_system_alert_subsystem` (`business_line_code`, `environment_code`, `service_name`),
  KEY `idx_ops_system_alert_subsystem_list` (`business_line_code`, `environment_code`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ops_system_alert_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line_code` VARCHAR(128) NOT NULL,
  `environment_code` VARCHAR(64) NOT NULL,
  `subsystem_name` VARCHAR(128) NULL,
  `service_name` VARCHAR(128) NOT NULL,
  `log_level` VARCHAR(32) NOT NULL,
  `title` VARCHAR(255) NULL,
  `message` TEXT NULL,
  `error_type` VARCHAR(255) NULL,
  `stack_trace` MEDIUMTEXT NULL,
  `trace_id` VARCHAR(128) NULL,
  `request_id` VARCHAR(128) NULL,
  `occurred_at` DATETIME NOT NULL,
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ops_system_alert_event_query` (`business_line_code`, `environment_code`, `service_name`, `log_level`, `occurred_at`),
  KEY `idx_ops_system_alert_event_time` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `recipient_user_name` VARCHAR(64) NOT NULL,
  `notification_type` VARCHAR(64) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `content` TEXT NULL,
  `business_line_code` VARCHAR(128) NULL,
  `environment_code` VARCHAR(64) NULL,
  `dedupe_key` VARCHAR(255) NOT NULL,
  `read_flag` TINYINT(1) NOT NULL DEFAULT 0,
  `read_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_notification_dedupe` (`recipient_user_name`, `dedupe_key`),
  KEY `idx_sys_notification_recipient` (`recipient_user_name`, `read_flag`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_project_involved_system` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_scope` VARCHAR(32) NOT NULL,
  `business_line` VARCHAR(128) NOT NULL DEFAULT '',
  `system_name` VARCHAR(128) NOT NULL,
  `description` TEXT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_project_involved_system_name` (`system_scope`, `business_line`, `system_name`),
  KEY `idx_pm_project_involved_system_select` (`system_scope`, `business_line`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_channel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `channel_code` VARCHAR(64) NOT NULL,
  `channel_name` VARCHAR(128) NOT NULL,
  `vendor_name` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `description` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_merchant_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `channel_id` BIGINT NOT NULL,
  `merchant_code` VARCHAR(128) NOT NULL,
  `merchant_name` VARCHAR(128) NOT NULL,
  `environment` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `app_id` VARCHAR(128) NULL,
  `settlement_subject` VARCHAR(128) NULL,
  `remark` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_merchant_channel_code_env` (`channel_id`, `merchant_code`, `environment`),
  KEY `idx_pay_merchant_channel_id` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_merchant_purpose` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT NOT NULL,
  `purpose_code` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_merchant_purpose` (`merchant_id`, `purpose_code`),
  KEY `idx_pay_merchant_purpose_code` (`purpose_code`, `merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_merchant_param` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT NOT NULL,
  `param_key` VARCHAR(128) NOT NULL,
  `value_type` VARCHAR(32) NOT NULL,
  `sensitive_flag` TINYINT(1) NOT NULL DEFAULT 0,
  `plain_value` TEXT NULL,
  `encrypted_value` TEXT NULL,
  `masked_value` VARCHAR(255) NULL,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_merchant_param_merchant_key` (`merchant_id`, `param_key`),
  KEY `idx_pay_merchant_param_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_merchant_secret` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT NOT NULL,
  `secret_name` VARCHAR(128) NOT NULL,
  `secret_type` VARCHAR(32) NOT NULL,
  `encrypted_value` TEXT NOT NULL,
  `masked_value` VARCHAR(255) NOT NULL,
  `fingerprint` VARCHAR(128) NOT NULL,
  `algorithm` VARCHAR(64) NOT NULL,
  `version_no` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `valid_from` DATETIME NULL,
  `valid_to` DATETIME NULL,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_merchant_secret_name_version` (`merchant_id`, `secret_name`, `version_no`),
  KEY `idx_pay_merchant_secret_lookup` (`merchant_id`, `secret_name`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_project_merchant_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NULL,
  `business_line` VARCHAR(128) NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `purpose_code` VARCHAR(32) NOT NULL,
  `priority` INT NOT NULL DEFAULT 1,
  `is_default` TINYINT(1) NOT NULL DEFAULT 0,
  `binding_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_binding_project_merchant_purpose` (`project_id`, `merchant_id`, `purpose_code`),
  KEY `idx_pay_binding_project_purpose` (`project_id`, `purpose_code`, `binding_status`, `is_default`, `priority`),
  KEY `idx_pay_binding_business_purpose` (`business_line`, `purpose_code`, `binding_status`, `is_default`, `priority`),
  KEY `idx_pay_binding_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_project_merchant_binding_purpose` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `binding_id` BIGINT NOT NULL,
  `purpose_code` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_binding_purpose` (`binding_id`, `purpose_code`),
  KEY `idx_pay_binding_purpose_code` (`purpose_code`, `binding_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_project_merchant_binding_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `binding_id` BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `relation_role` VARCHAR(32) NOT NULL,
  `relation_name` VARCHAR(128) NULL,
  `priority` INT NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pay_binding_relation_binding` (`binding_id`, `relation_role`, `priority`),
  KEY `idx_pay_binding_relation_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_merchant_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT NOT NULL,
  `credential_key` VARCHAR(128) NOT NULL,
  `credential_name` VARCHAR(128) NOT NULL,
  `credential_type` VARCHAR(32) NOT NULL,
  `encrypted_value` TEXT NOT NULL,
  `masked_value` VARCHAR(255) NOT NULL,
  `fingerprint` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_merchant_credential_key` (`merchant_id`, `credential_key`),
  KEY `idx_pay_merchant_credential_merchant` (`merchant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pay_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `biz_type` VARCHAR(32) NOT NULL,
  `biz_id` BIGINT NOT NULL,
  `action_type` VARCHAR(32) NOT NULL,
  `action_summary` VARCHAR(128) NOT NULL,
  `detail_text` TEXT NULL,
  `operator_user_name` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pay_operation_log_biz` (`biz_type`, `biz_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_sprint` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `sprint_name` VARCHAR(128) NOT NULL,
  `sprint_status` VARCHAR(32) NOT NULL,
  `start_date` DATE NULL,
  `end_date` DATE NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_release` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `release_name` VARCHAR(128) NOT NULL,
  `release_version` VARCHAR(64) NOT NULL,
  `release_status` VARCHAR(32) NOT NULL,
  `planned_at` DATETIME NULL,
  `released_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_work_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_item_no` VARCHAR(64) NOT NULL,
  `project_id` BIGINT NOT NULL,
  `sprint_id` BIGINT NULL,
  `release_id` BIGINT NULL,
  `work_item_type` VARCHAR(32) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `source_channel` VARCHAR(64) NOT NULL,
  `priority` VARCHAR(16) NOT NULL,
  `urgency` VARCHAR(16) NULL,
  `status` VARCHAR(32) NOT NULL,
  `creator_user_name` VARCHAR(64) NOT NULL,
  `owner_user_name` VARCHAR(64) NOT NULL,
  `follower_user_name` VARCHAR(64) NOT NULL,
  `proposer_name` VARCHAR(128) NULL,
  `acceptance_criteria` TEXT NULL,
  `planned_start_at` DATETIME NULL,
  `planned_end_at` DATETIME NULL,
  `finished_at` DATETIME NULL,
  `pause_previous_status` VARCHAR(32) NULL,
  `pause_reason` VARCHAR(255) NULL,
  `pause_date` DATE NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_work_item_no` (`work_item_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_work_item_follow_up` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_item_id` BIGINT NOT NULL,
  `content` TEXT NOT NULL,
  `operator_user_name` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_work_item_transition_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_item_id` BIGINT NOT NULL,
  `from_status` VARCHAR(32) NOT NULL,
  `to_status` VARCHAR(32) NOT NULL,
  `reason` VARCHAR(255) NULL,
  `operator_user_name` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_intake_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `source_type` VARCHAR(32) NOT NULL,
  `source_channel` VARCHAR(64) NOT NULL,
  `external_message_id` VARCHAR(128) NULL,
  `sender_name` VARCHAR(128) NULL,
  `received_at` DATETIME NOT NULL,
  `raw_content` TEXT NOT NULL,
  `development_owner_user_name` VARCHAR(64) NULL,
  `structured_data_json` MEDIUMTEXT NULL,
  `ai_draft_json` MEDIUMTEXT NULL,
  `intake_status` VARCHAR(32) NOT NULL,
  `demand_status` VARCHAR(32) NULL,
  `pause_previous_demand_status` VARCHAR(32) NULL,
  `pause_reason` VARCHAR(255) NULL,
  `pause_date` DATE NULL,
  `enrichment_status` VARCHAR(16) NULL,
  `enrichment_error_summary` VARCHAR(255) NULL,
  `enrichment_updated_at` DATETIME NULL,
  `converted_work_item_id` BIGINT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME NULL,
  `deleted_by` VARCHAR(64) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_intake_record_external_message_id` (`external_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_intake_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `intake_id` BIGINT NOT NULL,
  `action_type` VARCHAR(32) NOT NULL,
  `action_summary` VARCHAR(128) NOT NULL,
  `detail_text` TEXT NULL,
  `operator_user_name` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pm_intake_history_intake_id` (`intake_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_intake_todo` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `intake_id` BIGINT NOT NULL,
  `todo_title` VARCHAR(255) NOT NULL,
  `todo_content` TEXT NULL,
  `todo_status` VARCHAR(32) NOT NULL,
  `assignee_user_name` VARCHAR(64) NULL,
  `planned_at` DATETIME NULL,
  `completed_at` DATETIME NULL,
  `process_result` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pm_intake_todo_intake_id` (`intake_id`),
  KEY `idx_pm_intake_todo_status` (`todo_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_intake_development_analysis` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `intake_id` BIGINT NOT NULL,
  `project_id` BIGINT NULL,
  `business_line` VARCHAR(128) NULL,
  `repository_url` VARCHAR(512) NULL,
  `analysis_status` VARCHAR(32) NOT NULL,
  `analysis_message` VARCHAR(255) NULL,
  `draft_json` TEXT NULL,
  `zentao_sync_status` VARCHAR(32) NULL,
  `zentao_sync_message` VARCHAR(255) NULL,
  `created_by` VARCHAR(64) NOT NULL,
  `updated_by` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pm_intake_development_analysis_intake_id` (`intake_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_intake_clarification_analysis` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `intake_id` BIGINT NOT NULL,
  `business_line` VARCHAR(128) NULL,
  `analysis_status` VARCHAR(32) NOT NULL,
  `analysis_message` VARCHAR(255) NULL,
  `items_json` TEXT NULL,
  `created_by` VARCHAR(64) NOT NULL,
  `updated_by` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pm_intake_clarification_analysis_intake_id` (`intake_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_intake_work_item_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `intake_id` BIGINT NOT NULL,
  `work_item_id` BIGINT NOT NULL,
  `draft_index` INT NULL,
  `relation_type` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_intake_work_item_relation` (`intake_id`, `work_item_id`),
  KEY `idx_pm_intake_work_item_relation_intake` (`intake_id`, `draft_index`),
  KEY `idx_pm_intake_work_item_relation_work_item` (`work_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `biz_type` VARCHAR(32) NOT NULL,
  `biz_id` BIGINT NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `storage_path` VARCHAR(512) NOT NULL,
  `content_type` VARCHAR(128) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
