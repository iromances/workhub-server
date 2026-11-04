-- 整组审核与出站关联；不创建配置、审批或实际发布任务。
CREATE TABLE pm_agent_release_plan (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    change_set_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_release_plan_request (run_id, request_key),
    KEY idx_agent_release_plan_change (change_set_id, created_at),
    KEY idx_agent_release_plan_status (status, updated_at)
);
CREATE TABLE pm_agent_release_plan_step (
    request_id VARCHAR(36) PRIMARY KEY,
    approval_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36) NOT NULL,
    step_order INT NOT NULL,
    UNIQUE KEY uk_agent_release_plan_approval (approval_id),
    UNIQUE KEY uk_agent_release_plan_order (plan_id, step_order)
);
CREATE TABLE pm_agent_release_review (
    id VARCHAR(36) PRIMARY KEY,
    plan_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reviewed_by VARCHAR(100) NOT NULL,
    comment TEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_release_review_request (plan_id, request_key)
);
CREATE TABLE pm_agent_release_step_dependency (
    request_id VARCHAR(36) NOT NULL,
    depends_on_request_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (request_id, depends_on_request_id)
);
