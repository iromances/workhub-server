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
);

CREATE TABLE `pm_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `business_line_code` VARCHAR(64) NOT NULL DEFAULT '',
  `business_line_name` VARCHAR(128) NOT NULL DEFAULT '',
  `project_code` VARCHAR(64) NOT NULL,
  `project_name` VARCHAR(128) NOT NULL,
  `project_type` VARCHAR(32) NOT NULL,
  `project_group` VARCHAR(128) NOT NULL DEFAULT '',
  `project_status` VARCHAR(32) NOT NULL,
  `owner_user_name` VARCHAR(64) NOT NULL,
  `description` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_project_code` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_project_group_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_group` VARCHAR(128) NOT NULL,
  `member_user_name` VARCHAR(64) NOT NULL,
  `member_display_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_project_group_member` (`project_group`, `member_user_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `pm_project_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `group_name` VARCHAR(128) NOT NULL,
  `gitlab_group_name` VARCHAR(255) NULL,
  `description` TEXT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_project_group_name` (`group_name`)
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
  `project_id` BIGINT NOT NULL,
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

CREATE TABLE `pm_intake_development_analysis` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `intake_id` BIGINT NOT NULL,
  `project_id` BIGINT NULL,
  `project_group` VARCHAR(128) NULL,
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
