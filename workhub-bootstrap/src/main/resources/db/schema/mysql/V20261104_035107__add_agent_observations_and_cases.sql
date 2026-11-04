-- REQ-bf94e06e：主动巡检证据、问题信号与分析历史，不修改原监测数据或生成正式工作项。
-- 在 Agent 工作台迁移后执行；回退应用时保留问题和证据表，不自动清空已产生的历史。
CREATE TABLE pm_agent_observation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    tool_key VARCHAR(128) NOT NULL,
    tool_code VARCHAR(100) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    business_line_code VARCHAR(32) NOT NULL,
    environment_code VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_agent_observation_call (run_id, tool_key),
    KEY idx_agent_observation_scope (business_line_code, environment_code, observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_autonomy_case (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    business_line_code VARCHAR(32) NOT NULL,
    environment_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    subject_key VARCHAR(256) NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_run_id VARCHAR(36) NULL,
    latest_proposal_id VARCHAR(36) NULL,
    occurrence_count BIGINT NOT NULL DEFAULT 0,
    evidence_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    first_seen_at DATETIME(6) NULL,
    last_seen_at DATETIME(6) NULL,
    last_observed_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_autonomy_case_fingerprint (fingerprint),
    KEY idx_autonomy_case_scope (business_line_code, environment_code, status, updated_at),
    KEY idx_autonomy_case_active (active_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_autonomy_signal (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    signal_key VARCHAR(64) NOT NULL,
    case_id VARCHAR(36) NOT NULL,
    observation_id VARCHAR(36) NOT NULL,
    evidence_pointer VARCHAR(256) NOT NULL,
    source_event_id VARCHAR(256) NOT NULL,
    source_version VARCHAR(64) NOT NULL,
    evidence_json LONGTEXT NOT NULL,
    occurred_at DATETIME(6) NULL,
    observed_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_autonomy_signal_key (signal_key),
    KEY idx_autonomy_signal_case (case_id, observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_autonomy_proposal (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    case_version BIGINT NOT NULL,
    proposal_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_autonomy_proposal_request (case_id, run_id, request_key),
    KEY idx_autonomy_proposal_case (case_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pm_autonomy_case_run (
    case_id VARCHAR(36) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    relation VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (case_id, run_id),
    KEY idx_autonomy_case_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 全局短事务互斥只用于同指纹问题登记；不持锁调用 AI、Git、Jenkins 或监测服务。
INSERT INTO pm_agent_dispatch_lock(id) VALUES ('AUTONOMY_SIGNAL');
INSERT INTO sys_permission(permission_code, permission_name, permission_type, parent_code, route_path, sort_order, enabled, remark)
VALUES ('autonomy:case:view','查看自主巡检问题','BUTTON','agent:view',NULL,22,1,'查看已授权范围的问题与来源证据'),
       ('autonomy:case:operate','跟进自主巡检问题','BUTTON','agent:view',NULL,23,1,'登记证据、领取问题和维护分析方案；不授予发布审核');
INSERT INTO sys_role_permission(role_id, permission_code)
SELECT r.id,p.permission_code FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('autonomy:case:view','autonomy:case:operate')
WHERE r.role_code='SUPER_ADMIN' AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id=r.id AND x.permission_code=p.permission_code);
