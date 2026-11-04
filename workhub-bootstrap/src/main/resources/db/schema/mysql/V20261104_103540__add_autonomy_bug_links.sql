-- REQ-bf94e06e：AI 关联缺陷版本与真实运行证据；不修改原工作项，回退应用保留关联历史。
CREATE TABLE pm_autonomy_bug_link (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    work_item_id BIGINT NOT NULL,
    work_item_version VARCHAR(64) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    observation_id VARCHAR(36) NOT NULL,
    evidence_pointer VARCHAR(256) NOT NULL,
    signal_id VARCHAR(36) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    case_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_bug_link_case_version (case_id, case_version),
    KEY idx_bug_link_item_version (case_id, work_item_id, case_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_autonomy_bug_link_request (
    case_id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    link_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (case_id, run_id, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
