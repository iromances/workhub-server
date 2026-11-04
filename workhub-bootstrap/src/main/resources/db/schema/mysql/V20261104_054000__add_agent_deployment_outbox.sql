-- REQ-bf94e06e：不可变人工发布授权及 Jenkins 出站记录。
-- 本迁移不创建审批、不启用发布调度、不写接入配置。回退应用时保留在途记录和资源锁。
CREATE TABLE pm_agent_release_approval (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    change_set_id VARCHAR(36) NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    binding_json LONGTEXT NOT NULL,
    binding_hash VARCHAR(64) NOT NULL,
    parameters_json LONGTEXT NOT NULL,
    parameters_hash VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reviewed_by VARCHAR(100) NOT NULL,
    comment VARCHAR(4000) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_approval_request (request_id),
    KEY idx_agent_approval_change (change_set_id,reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_deployment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    approval_id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    change_set_id VARCHAR(36) NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    binding_json LONGTEXT NOT NULL,
    binding_hash VARCHAR(64) NOT NULL,
    parameters_json LONGTEXT NOT NULL,
    parameters_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    queue_id BIGINT NULL,
    build_number BIGINT NULL,
    build_result VARCHAR(32) NULL,
    receipt_json LONGTEXT NULL,
    error_message VARCHAR(2000) NULL,
    owner_token VARCHAR(64) NULL,
    lease_until DATETIME(6) NULL,
    next_check_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_deployment_approval (approval_id),
    KEY idx_agent_deployment_poll (status,next_check_at,lease_until),
    KEY idx_agent_deployment_change (change_set_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_release_resource (
    resource_key VARCHAR(64) NOT NULL PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_agent_release_resource_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO pm_agent_dispatch_lock(id) VALUES ('RELEASE');
