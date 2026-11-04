-- 仅建立复查记录，不自动执行迁移外的巡检、构建或发布。
CREATE TABLE pm_agent_verification (
    id VARCHAR(36) PRIMARY KEY,
    plan_id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    change_set_id VARCHAR(36) NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    approved_snapshot_hash VARCHAR(64) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    artifact_id VARCHAR(36),
    artifact_hash VARCHAR(64),
    conclusion_json LONGTEXT,
    next_check_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_verification_plan (plan_id),
    UNIQUE KEY uk_agent_verification_artifact (artifact_id),
    KEY idx_agent_verification_wait (status, next_check_at)
);
CREATE TABLE pm_agent_verification_sample (
    id VARCHAR(36) PRIMARY KEY,
    verification_id VARCHAR(36) NOT NULL,
    check_id VARCHAR(64) NOT NULL,
    sample_order BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    assessment VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    historical_data_pending TINYINT NOT NULL,
    evidence_json LONGTEXT NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_verification_sample_request (verification_id, request_key),
    UNIQUE KEY uk_agent_verification_sample_order (verification_id, sample_order),
    KEY idx_agent_verification_check (verification_id, check_id, sample_order)
);
CREATE TABLE pm_agent_verification_call (
    run_id VARCHAR(36) NOT NULL,
    tool_key VARCHAR(180) NOT NULL,
    tool_code VARCHAR(80) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id, tool_key)
);
