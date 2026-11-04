-- REQ-bf94e06e：跨运行巡检进度；只保存受控来源快照和 AI 分析，不改写监测源数据。
-- 回退应用保留四表及历史，真实环境由现有 Flyway 在获准部署时执行。
CREATE TABLE pm_agent_inspection_stream (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    business_line_code VARCHAR(32) NOT NULL,
    environment_code VARCHAR(64) NOT NULL,
    after_id BIGINT NOT NULL DEFAULT 0,
    upper_id BIGINT NOT NULL DEFAULT 0,
    sweep_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_inspection_scope (task_id, source_type, business_line_code, environment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_inspection_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stream_id VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assigned_run_id VARCHAR(36) NULL,
    observation_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_inspection_source_version (stream_id, source_id, content_hash),
    KEY idx_inspection_pending (stream_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_inspection_analysis (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    observation_id VARCHAR(36) NULL,
    outcome VARCHAR(32) NOT NULL,
    evidence_pointer VARCHAR(256) NULL,
    reason VARCHAR(2000) NOT NULL,
    case_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_inspection_analysis (item_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_inspection_call (
    run_id VARCHAR(36) NOT NULL,
    tool_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id, tool_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
