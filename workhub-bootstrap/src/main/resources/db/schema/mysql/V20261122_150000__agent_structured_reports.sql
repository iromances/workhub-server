-- REQ-72ecd9bc: immutable per-run structured reports; existing issue workflow remains independent.
CREATE TABLE pm_agent_report (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY,
    `run_id` VARCHAR(36) NOT NULL,
    `task_id` VARCHAR(36) NOT NULL,
    `agent_id` VARCHAR(36) NOT NULL,
    `schema_version` INT NOT NULL,
    `report_type` VARCHAR(64) NOT NULL,
    `title` LONGTEXT NOT NULL,
    `conclusion` LONGTEXT NOT NULL,
    `assessment` VARCHAR(64) NOT NULL,
    `scope_json` LONGTEXT NOT NULL,
    `limitations_json` LONGTEXT NOT NULL,
    `original_event_id` VARCHAR(36) NULL,
    `payload_hash` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    UNIQUE KEY uk_report_run (run_id),
    KEY idx_report_task (task_id,created_at),
    KEY idx_report_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_report_check (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY,
    `report_id` VARCHAR(36) NOT NULL,
    `item_key` VARCHAR(128) NOT NULL,
    `ordinal` INT NOT NULL,
    `category` LONGTEXT NULL,
    `title` LONGTEXT NOT NULL,
    `outcome` VARCHAR(64) NULL,
    `result_text` LONGTEXT NULL,
    UNIQUE KEY uk_report_check (report_id,item_key),
    KEY idx_report_check_order (report_id,ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_report_finding (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY,
    `report_id` VARCHAR(36) NOT NULL,
    `item_key` VARCHAR(128) NOT NULL,
    `ordinal` INT NOT NULL,
    `title` LONGTEXT NOT NULL,
    `severity` VARCHAR(64) NULL,
    `verification_status` VARCHAR(64) NULL,
    `disposition` VARCHAR(64) NULL,
    `category` LONGTEXT NULL,
    `trigger_text` LONGTEXT NULL,
    `analysis_text` LONGTEXT NULL,
    `impact_text` LONGTEXT NULL,
    `recommendation_text` LONGTEXT NULL,
    `signature` VARCHAR(200) NULL,
    `domain` VARCHAR(64) NULL,
    `issue_id` VARCHAR(36) NULL,
    UNIQUE KEY uk_report_finding (report_id,item_key),
    KEY idx_report_finding_order (report_id,ordinal),
    KEY idx_report_finding_issue (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_report_evidence (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY,
    `report_id` VARCHAR(36) NOT NULL,
    `item_key` VARCHAR(128) NOT NULL,
    `ordinal` INT NOT NULL,
    `target_type` VARCHAR(64) NULL,
    `target_key` VARCHAR(128) NULL,
    `evidence_type` VARCHAR(64) NULL,
    `source` LONGTEXT NULL,
    `locator` LONGTEXT NULL,
    `observed_at` LONGTEXT NULL,
    `environment_code` LONGTEXT NULL,
    `verification_method` LONGTEXT NULL,
    `result_text` LONGTEXT NULL,
    `limitations_text` LONGTEXT NULL,
    UNIQUE KEY uk_report_evidence (report_id,item_key),
    KEY idx_report_evidence_order (report_id,ordinal),
    KEY idx_report_evidence_target (report_id,target_type,target_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_report_action (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY,
    `report_id` VARCHAR(36) NOT NULL,
    `item_key` VARCHAR(128) NOT NULL,
    `ordinal` INT NOT NULL,
    `finding_key` VARCHAR(128) NULL,
    `priority` VARCHAR(64) NULL,
    `description` LONGTEXT NULL,
    `acceptance_criteria` LONGTEXT NULL,
    `prerequisite_text` LONGTEXT NULL,
    UNIQUE KEY uk_report_action (report_id,item_key),
    KEY idx_report_action_order (report_id,ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

