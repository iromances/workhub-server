-- REQ-4d361388：巡检问题结构化与自动修复闭环。问题即工单：跨轮聚合（task+signature 唯一）、
-- 深查五要素、修复分支/MR、人工审核与两段式发版状态机。

CREATE TABLE pm_agent_issue (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    business_line_code VARCHAR(32) NOT NULL,
    environment_code VARCHAR(64) NOT NULL,
    domain VARCHAR(32) NOT NULL,
    signature VARCHAR(200) NOT NULL,
    title VARCHAR(500) NOT NULL,
    severity VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    analysis_json LONGTEXT NOT NULL,
    evidence_json LONGTEXT NOT NULL,
    fix_json LONGTEXT NULL,
    review_json LONGTEXT NULL,
    deploy_json LONGTEXT NULL,
    first_run_id VARCHAR(36) NOT NULL,
    last_run_id VARCHAR(36) NOT NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    unseen_rounds INT NOT NULL DEFAULT 0,
    reopen_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_issue_signature (task_id, signature),
    KEY idx_agent_issue_status (status, updated_at),
    KEY idx_agent_issue_domain (domain, severity),
    KEY idx_agent_issue_agent (agent_id, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
