-- REQ-bf94e06e：独立测试请求与工具回执；不创建或触发真实 Job。
-- 应用回退保留在途请求、证据与共享资源锁，不将未知请求视作未提交。
CREATE TABLE pm_agent_build_test (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    change_set_id VARCHAR(36) NOT NULL,
    binding_id VARCHAR(64) NOT NULL,
    attempt BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
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
    wake_pending BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_test_request (run_id,request_key),
    UNIQUE KEY uk_agent_test_attempt (change_set_id,binding_id,attempt),
    KEY idx_agent_test_poll (status,next_check_at,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_build_test_call (
    run_id VARCHAR(36) NOT NULL,
    tool_key VARCHAR(160) NOT NULL,
    tool_code VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id,tool_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
