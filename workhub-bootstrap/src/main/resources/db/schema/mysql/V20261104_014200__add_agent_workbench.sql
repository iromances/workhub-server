-- REQ-bf94e06e：Agent 工作台配置、任务、运行及审计。只新增表和权限，不创建 Agent 或启动任务。
-- 时间字段统一保存 UTC；部署前执行本迁移。回退应用时保留任务/审计，不自动删表或清理业务证据。
CREATE TABLE pm_agent_definition (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    owner_name VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_agent_owner (owner_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    agent_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    specification_json LONGTEXT NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    schedule_mode VARCHAR(16) NOT NULL,
    cron_expression VARCHAR(128) NULL,
    zone_id VARCHAR(64) NOT NULL,
    misfire_policy VARCHAR(16) NOT NULL,
    schedule_enabled TINYINT(1) NOT NULL DEFAULT 0,
    next_run_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_task_request (agent_id, request_key),
    KEY idx_agent_task_agent (agent_id, created_at),
    KEY idx_agent_schedule_due (schedule_enabled, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_run (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    trigger_key VARCHAR(128) NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    configuration_json LONGTEXT NOT NULL,
    specification_json LONGTEXT NOT NULL,
    provider_binding_json LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    step_count INT NOT NULL DEFAULT 0,
    elapsed_millis BIGINT NOT NULL DEFAULT 0,
    cancel_requested TINYINT(1) NOT NULL DEFAULT 0,
    owner_token VARCHAR(64) NULL,
    lease_until DATETIME(6) NULL,
    external_handle TEXT NULL,
    result_text LONGTEXT NULL,
    error_message VARCHAR(2000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_run_trigger (task_id, trigger_key),
    KEY idx_agent_run_queue (status, created_at),
    KEY idx_agent_run_lease (status, lease_until),
    KEY idx_agent_run_agent (agent_id, status),
    KEY idx_agent_run_task (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    ordinal BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_event_key (run_id, event_key),
    UNIQUE KEY uk_agent_event_order (run_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_artifact (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    artifact_key VARCHAR(128) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    provenance VARCHAR(32) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_artifact_key (run_id, artifact_key),
    KEY idx_agent_artifact_run (run_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_audit (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    object_type VARCHAR(32) NOT NULL,
    object_id VARCHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_agent_audit_object (object_type, object_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_agent_dispatch_lock (id VARCHAR(32) NOT NULL PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO pm_agent_dispatch_lock(id) VALUES ('DISPATCH');

INSERT INTO sys_permission(permission_code, permission_name, permission_type, parent_code, route_path, sort_order, enabled, remark)
VALUES
 ('agent:view','Agent 工作台','MENU',NULL,'/agents',15,1,'查看授权的 Agent 和任务'),
 ('agent:manage','管理 Agent','BUTTON','agent:view',NULL,16,1,'维护 Agent 配置、职责和工具范围'),
 ('agent:task:assign','分派 Agent 任务','BUTTON','agent:view',NULL,17,1,'分派立即或周期任务'),
 ('agent:task:view','查看 Agent 任务','BUTTON','agent:view',NULL,18,1,'查看运行记录和产物'),
 ('agent:task:operate','操作 Agent 任务','BUTTON','agent:view',NULL,19,1,'补充、取消和重试任务'),
 ('autonomy:release:review','审核 Agent 发布','BUTTON','agent:view',NULL,20,1,'审核具体版本和环境的发布申请');
INSERT INTO sys_role_permission(role_id, permission_code)
SELECT r.id, p.permission_code FROM sys_role r JOIN sys_permission p
  ON p.permission_code IN ('agent:view','agent:manage','agent:task:assign','agent:task:view','agent:task:operate','autonomy:release:review')
WHERE r.role_code='SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id=r.id AND x.permission_code=p.permission_code);
