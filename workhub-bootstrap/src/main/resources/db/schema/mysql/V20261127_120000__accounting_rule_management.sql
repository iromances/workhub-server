-- REQ-7f26a063：业务监测规则组与检查项按配置持久化。只建表并替换数据指南已知片段，不插入规则数据：
-- 内置目录由 AccountingRuleInitializer 首次访问时只补缺物化，页面维护后的启停与级别不会被覆盖。
-- 与账务监测既有表一致不建外键，避免迁移账号额外依赖 REFERENCES 权限。
CREATE TABLE IF NOT EXISTS `ops_account_monitor_family` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_id` BIGINT NOT NULL,
  `family_code` VARCHAR(64) NOT NULL,
  `family_name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(512) NULL,
  `batch_size` INT NOT NULL,
  `source_type` VARCHAR(16) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_by` VARCHAR(64) NOT NULL,
  `updated_by` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_account_monitor_family` (`config_id`, `family_code`),
  KEY `idx_ops_account_monitor_family_config` (`config_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ops_account_monitor_check` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `check_id` VARCHAR(64) NOT NULL,
  `check_name` VARCHAR(128) NOT NULL,
  `severity` VARCHAR(16) NOT NULL,
  `note` VARCHAR(512) NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_by` VARCHAR(64) NOT NULL,
  `updated_by` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_account_monitor_check` (`family_id`, `check_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 数据指南：只替换 REQ-d227e09d v4 写入的已知片段，保留管理员维护的其他内容；片段不存在时不改动。
UPDATE `sys_config_item`
SET `plain_value` = REPLACE(`plain_value`,
  'ACCOUNTING_INTEGRITY 的规则来自内置目录及 func_call_sql 的 AI_* 模板，不使用旧 ops_account_rule_config；该类型仅手动或明确任务执行，不能按 check_time 推断漏调度',
  'ACCOUNTING_INTEGRITY 的规则来自配置下的规则组与检查项 ops_account_monitor_family / ops_account_monitor_check（内置目录为种子，可在业务监测配置详情启停、改级别或新增自定义规则组），SQL 为 func_call_sql 的 AI_* 模板，不使用旧 ops_account_rule_config；该类型仅手动或明确任务执行，不能按 check_time 推断漏调度')
WHERE `config_group` = 'agent.runtime' AND `config_key` = 'dataGuide'
  AND `plain_value` LIKE '%ACCOUNTING_INTEGRITY 的规则来自内置目录及 func_call_sql 的 AI_* 模板%';
