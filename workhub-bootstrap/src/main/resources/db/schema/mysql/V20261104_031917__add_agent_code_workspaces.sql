-- REQ-bf94e06e：受控代码工作区与修订回执；不创建执行策略，不拉取仓库或启动测试。
-- 本迁移在 Agent 工作台迁移之后执行。回退应用时保留代码对象与证据，不自动删表。
CREATE TABLE pm_agent_code_workspace (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    repository_id VARCHAR(64) NOT NULL,
    policy_hash VARCHAR(64) NOT NULL,
    host_id VARCHAR(255) NOT NULL,
    base_branch VARCHAR(255) NOT NULL,
    base_sha VARCHAR(64) NOT NULL,
    candidate_sha VARCHAR(64) NOT NULL,
    source_branch VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_workspace_repo (run_id, repository_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_code_operation (
    workspace_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (workspace_id, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission(permission_code, permission_name, permission_type, parent_code, route_path, sort_order, enabled, remark)
VALUES ('agent:code:use','使用 Agent 代码工具','BUTTON','agent:view',NULL,21,1,'在已配置的仓库和路径范围读写隔离代码工作区');
INSERT INTO sys_role_permission(role_id, permission_code)
SELECT r.id,'agent:code:use' FROM sys_role r WHERE r.role_code='SUPER_ADMIN'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission p WHERE p.role_id=r.id AND p.permission_code='agent:code:use');
