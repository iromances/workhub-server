-- 仅建立不可变问题关闭凭据；不修改已有问题或已登记缺陷状态。
CREATE TABLE pm_autonomy_case_resolution (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    case_version BIGINT NOT NULL,
    resolved_version BIGINT NOT NULL,
    evidence_version BIGINT NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    verification_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36) NOT NULL,
    change_set_id VARCHAR(36) NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    proposal_id VARCHAR(36) NOT NULL,
    proposal_hash VARCHAR(64) NOT NULL,
    artifact_id VARCHAR(36) NOT NULL,
    artifact_hash VARCHAR(64) NOT NULL,
    check_ids_json TEXT NOT NULL,
    window_start DATETIME(6) NOT NULL,
    window_end DATETIME(6) NOT NULL,
    reason TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_autonomy_resolution_version (case_id, case_version),
    UNIQUE KEY uk_autonomy_resolution_verification (verification_id)
);
