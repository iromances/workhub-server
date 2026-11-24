-- REQ-d227e09d v4：仅新增关联，不改旧结果，不开启调度或通知。
CREATE TABLE pm_agent_monitor_run (
    agent_run_id VARCHAR(36) NOT NULL,
    config_id BIGINT NOT NULL,
    monitor_run_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (agent_run_id, config_id),
    KEY idx_agent_monitor_batch (monitor_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS func_call_sql_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  sql_code VARCHAR(160) NOT NULL, scenario_code VARCHAR(64), source VARCHAR(16) NOT NULL,
  operator VARCHAR(64) NOT NULL, business_line_code VARCHAR(128) NOT NULL, environment_code VARCHAR(64) NOT NULL,
  rendered_sql MEDIUMTEXT NOT NULL, status VARCHAR(16) NOT NULL, row_count INT, duration_ms BIGINT,
  error_message TEXT, started_at DATETIME(3) NOT NULL, finished_at DATETIME(3),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_func_audit_code_time(sql_code,started_at), KEY idx_func_audit_time(started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE func_call_sql_audit_log
    ADD COLUMN monitor_run_id BIGINT NULL,
    ADD COLUMN rule_family VARCHAR(128) NULL,
    ADD COLUMN parameters_json TEXT NULL,
    ADD COLUMN template_hash VARCHAR(64) NULL,
    ADD KEY idx_func_call_monitor_run (monitor_run_id, id);

-- 只替换已知旧目录片段，保留管理员维护的目标地址和其他指南。
UPDATE sys_config_item
SET plain_value = REPLACE(plain_value,
  '业务监测配置 ops_monitor_config；账务核对运行与差异 ops_account_reconcile_run / ops_account_reconcile_result；账务配置与规则 ops_account_monitor_config / ops_account_rule_config',
  '服务监测配置 ops_monitor_config；业务监测批次与规则结果 ops_account_reconcile_run / ops_account_reconcile_result；业务监测场景 ops_account_monitor_config。ACCOUNTING_INTEGRITY 的规则来自内置目录及 func_call_sql 的 AI_* 模板，不使用旧 ops_account_rule_config；该类型仅手动或明确任务执行，不能按 check_time 推断漏调度'),
  updated_at = CURRENT_TIMESTAMP
WHERE config_group='agent.runtime' AND config_key='dataGuide'
  AND plain_value LIKE '%账务配置与规则 ops_account_monitor_config / ops_account_rule_config%';
