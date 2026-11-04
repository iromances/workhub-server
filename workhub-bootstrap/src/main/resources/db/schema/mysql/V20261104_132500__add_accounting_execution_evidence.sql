-- 只为新执行记录实际上下文；历史运行不回填或推测环境。回退应用时保留本表。
CREATE TABLE ops_account_execution_evidence (
    run_id BIGINT NOT NULL,
    business_line_code VARCHAR(32) NOT NULL,
    environment_code VARCHAR(32) NOT NULL,
    system_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id),
    KEY idx_account_evidence_scope (business_line_code, environment_code, run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
