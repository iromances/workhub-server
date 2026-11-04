-- REQ-bf94e06e：持久化候选分支/MR 的写入意图与核验结果；不配置或访问实际 GitLab。
-- 回退应用时保留此表，以便对账尚未确认的外部写入。
CREATE TABLE pm_agent_delivery (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    change_set_id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    repository_id VARCHAR(64) NOT NULL,
    binding_json LONGTEXT NOT NULL,
    binding_hash VARCHAR(64) NOT NULL,
    source_branch VARCHAR(255) NOT NULL,
    target_branch VARCHAR(255) NOT NULL,
    result_sha VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    merge_request_iid BIGINT NULL,
    merge_request_url VARCHAR(1000) NULL,
    error_message VARCHAR(2000) NULL,
    verified_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_delivery_repository (change_set_id, repository_id),
    KEY idx_agent_delivery_run (run_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_code,route_path,sort_order,enabled,remark)
VALUES('agent:code:deliver','交付 Agent 候选代码','BUTTON','agent:view',NULL,22,1,'按固定项目绑定创建独立候选分支及草稿MR，不包含合并或发布权限');
INSERT INTO sys_role_permission(role_id,permission_code)
SELECT r.id,'agent:code:deliver' FROM sys_role r WHERE r.role_code='SUPER_ADMIN'
 AND NOT EXISTS(SELECT 1 FROM sys_role_permission p WHERE p.role_id=r.id AND p.permission_code='agent:code:deliver');
