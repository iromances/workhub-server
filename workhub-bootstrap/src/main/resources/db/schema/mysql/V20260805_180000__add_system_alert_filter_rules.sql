CREATE TABLE `ops_system_alert_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rule_name` VARCHAR(128) NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `match_scope` VARCHAR(32) NOT NULL,
  `match_mode` VARCHAR(16) NOT NULL,
  `priority` INT NOT NULL DEFAULT 100,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `remark` VARCHAR(500) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_system_alert_rule_name` (`rule_name`),
  KEY `idx_ops_system_alert_rule_enabled_priority` (`enabled`, `priority`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ops_system_alert_rule_keyword` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rule_id` BIGINT NOT NULL,
  `keyword` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_system_alert_rule_keyword` (`rule_id`, `keyword`),
  KEY `idx_ops_system_alert_rule_keyword_order` (`rule_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `ops_system_alert_rule`
  (`rule_name`, `action`, `match_scope`, `match_mode`, `priority`, `enabled`, `remark`)
VALUES
  ('JWT无效噪音', 'IGNORE', 'MESSAGE', 'ANY', 10, 1, '迁移自系统预警原硬编码规则'),
  ('余额不足噪音', 'IGNORE', 'MESSAGE', 'ANY', 20, 1, '迁移自系统预警原硬编码规则'),
  ('商品明细总额不等噪音', 'IGNORE', 'MESSAGE', 'ANY', 30, 1, '迁移自系统预警原硬编码规则'),
  ('重复提交噪音', 'IGNORE', 'MESSAGE', 'ANY', 40, 1, '迁移自系统预警原硬编码规则'),
  ('支付流水号重复噪音', 'IGNORE', 'MESSAGE', 'ANY', 50, 1, '迁移自系统预警原硬编码规则'),
  ('账单顺序结算噪音', 'IGNORE', 'MESSAGE', 'ANY', 60, 1, '迁移自系统预警原硬编码规则'),
  ('交易金额与试算不等噪音', 'IGNORE', 'MESSAGE', 'ANY', 70, 1, '迁移自系统预警原硬编码规则'),
  ('七天无理由退费超期噪音', 'IGNORE', 'MESSAGE', 'ANY', 80, 1, '迁移自系统预警原硬编码规则'),
  ('账单已结算噪音', 'IGNORE', 'MESSAGE', 'ALL', 90, 1, '错误码与账单已结算同时命中'),
  ('Druid连接长时间未收包', 'SLOW_SQL', 'ALL_TEXT', 'ANY', 100, 1, '归入慢 SQL 且不发送站内信');

INSERT INTO `ops_system_alert_rule_keyword` (`rule_id`, `keyword`, `sort_order`)
SELECT r.id, seed.keyword, seed.sort_order
FROM `ops_system_alert_rule` r
JOIN (
  SELECT 'JWT无效噪音' AS rule_name, '拦截请求解析token异常：jwt无效' AS keyword, 0 AS sort_order
  UNION ALL SELECT '余额不足噪音', '余额不足', 0
  UNION ALL SELECT '商品明细总额不等噪音', '[200007]交易金额和商品明细总额不等', 0
  UNION ALL SELECT '重复提交噪音', '[900000]请勿重复提交', 0
  UNION ALL SELECT '支付流水号重复噪音', '[900000]业务方支付流水号重复', 0
  UNION ALL SELECT '账单顺序结算噪音', '[900000]请按顺序结算账单', 0
  UNION ALL SELECT '交易金额与试算不等噪音', '[900000]交易金额与试算不等', 0
  UNION ALL SELECT '七天无理由退费超期噪音', '[999999]实际天数已超七天，无法执行七天无理由退费', 0
  UNION ALL SELECT '账单已结算噪音', '[900000]', 0
  UNION ALL SELECT '账单已结算噪音', '账单已结算', 1
  UNION ALL SELECT 'Druid连接长时间未收包', 'discard long time none received connection', 0
  UNION ALL SELECT 'Druid连接长时间未收包', 'lastPacketReceivedIdleMillis', 1
) seed ON seed.rule_name = r.rule_name;
