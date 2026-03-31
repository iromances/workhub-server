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

CREATE TABLE `pm_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_code` VARCHAR(64) NOT NULL,
  `project_name` VARCHAR(128) NOT NULL,
  `project_type` VARCHAR(32) NOT NULL,
  `project_status` VARCHAR(32) NOT NULL,
  `owner_user_name` VARCHAR(64) NOT NULL,
  `description` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_project_code` (`project_code`)
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
  `structured_data_json` TEXT NULL,
  `ai_draft_json` TEXT NULL,
  `intake_status` VARCHAR(32) NOT NULL,
  `enrichment_status` VARCHAR(16) NULL,
  `enrichment_error_summary` VARCHAR(255) NULL,
  `enrichment_updated_at` DATETIME NULL,
  `converted_work_item_id` BIGINT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pm_intake_record_external_message_id` (`external_message_id`)
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
