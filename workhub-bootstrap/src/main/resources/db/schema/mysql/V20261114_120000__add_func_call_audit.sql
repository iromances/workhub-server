-- REQ-b4d7e6f2：Function Call 执行审计表。
-- 回滚：DROP TABLE func_call_sql_audit_log;

CREATE TABLE IF NOT EXISTS `func_call_sql_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sql_code` VARCHAR(160) NOT NULL COMMENT 'SQL 编码（组合语句为参与码 + 连接）',
  `scenario_code` VARCHAR(64) NULL COMMENT '场景编码（单编码可推导时填写）',
  `source` VARCHAR(16) NOT NULL COMMENT '执行来源：SYSTEM（巡检/代码）/ MANUAL（页面试运行）',
  `operator` VARCHAR(64) NOT NULL COMMENT '操作人（页面为登录用户，巡检为 SYSTEM）',
  `business_line_code` VARCHAR(128) NOT NULL COMMENT '业务线编码',
  `environment_code` VARCHAR(64) NOT NULL COMMENT '环境编码',
  `rendered_sql` MEDIUMTEXT NOT NULL COMMENT '渲染后实际执行 SQL 全文（审计证据）',
  `status` VARCHAR(16) NOT NULL COMMENT '执行状态：SUCCESS / ERROR',
  `row_count` INT NULL COMMENT '返回行数',
  `duration_ms` BIGINT NULL COMMENT '执行耗时毫秒（批次语句共享整批计时）',
  `error_message` TEXT NULL COMMENT '失败原因',
  `started_at` DATETIME(3) NOT NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_func_audit_code_time` (`sql_code`, `started_at`),
  KEY `idx_func_audit_time` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Function Call 执行审计（保留 365 天）';
