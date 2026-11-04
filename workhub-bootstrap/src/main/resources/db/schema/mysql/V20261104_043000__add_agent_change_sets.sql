-- REQ-bf94e06e：不可变的多仓库候选交付清单；不把清单建立视为测试或远端交付完成。
-- 回退应用时保留历史清单及其引用；此迁移不改运行配置或真实仓库。
CREATE TABLE pm_agent_change_set (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    revision BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    manifest_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_change_request (run_id, request_key),
    UNIQUE KEY uk_agent_change_revision (run_id, revision),
    KEY idx_agent_change_manifest (manifest_hash),
    KEY idx_agent_change_task (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
