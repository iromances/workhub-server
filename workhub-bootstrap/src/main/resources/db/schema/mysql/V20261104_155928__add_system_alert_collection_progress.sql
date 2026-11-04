-- 只建立系统预警采集进度与不可变分页回执，不触发采集或回填真实数据。
CREATE TABLE ops_system_alert_collection_state (
    scope_id BIGINT PRIMARY KEY,
    window_id VARCHAR(36) NOT NULL,
    definition_json LONGTEXT NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    from_time DATETIME(6) NOT NULL,
    to_time DATETIME(6) NOT NULL,
    position_json LONGTEXT,
    latest_occurred_at DATETIME(6),
    covered_until DATETIME(6),
    status VARCHAR(24) NOT NULL,
    owner_token VARCHAR(36),
    lease_until DATETIME(6),
    issue_count BIGINT NOT NULL DEFAULT 0,
    page_number BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_alert_collection_pending (status, updated_at)
);
CREATE TABLE ops_system_alert_collection_page (
    id VARCHAR(36) PRIMARY KEY,
    scope_id BIGINT NOT NULL,
    window_id VARCHAR(36) NOT NULL,
    page_number BIGINT NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    from_time DATETIME(6) NOT NULL,
    to_time DATETIME(6) NOT NULL,
    complete TINYINT NOT NULL,
    receipt_json LONGTEXT NOT NULL,
    receipt_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_alert_collection_page (window_id, page_number),
    KEY idx_alert_collection_scope_time (scope_id, to_time)
);
