-- REQ-d227e09d：账务监测场景化优化——删除扫表对账配置链，配置表转为场景维度。
-- 运行/结果/凭据历史数据保留只读；旧每日对账 Job 配置行删除（执行历史保留）。

-- 1. 清空旧规则包配置与按规则覆盖配置（run/result/evidence 不动）
DELETE FROM `ops_account_rule_config`;
DELETE FROM `ops_account_monitor_config`;

-- 2. 配置表结构改造：系统维度 → 场景维度（表已清空，唯一键随场景类型扩展）
ALTER TABLE `ops_account_monitor_config`
  DROP KEY `uk_ops_account_monitor_identity`,
  DROP KEY `idx_ops_account_monitor_enabled`,
  DROP COLUMN `database_target_key`,
  DROP COLUMN `schema_name`,
  DROP COLUMN `rule_profile`,
  DROP COLUMN `daily_enabled`,
  ADD COLUMN `scenario_type` VARCHAR(32) NOT NULL COMMENT '场景类型：BATCH_WITHHOLD/COMPENSATION/BUYBACK/PAYMENT/CLEARING_SETTLE' AFTER `display_name`,
  ADD COLUMN `expected_time` TIME NULL COMMENT '场景预期发生时间（如代扣每日 08:00）' AFTER `scenario_type`,
  ADD COLUMN `check_time` TIME NOT NULL COMMENT '每日核对时间（核对时间过后才允许自动核对）' AFTER `expected_time`,
  ADD COLUMN `expected_count_sql_code` VARCHAR(64) NULL COMMENT '应发起笔数 func_call_sql 编码（与 expected_fixed_count 二选一）' AFTER `check_time`,
  ADD COLUMN `expected_fixed_count` INT NULL COMMENT '固定应发起笔数（与 expected_count_sql_code 二选一）' AFTER `expected_count_sql_code`,
  ADD COLUMN `actual_count_sql_code` VARCHAR(64) NOT NULL COMMENT '实际发起笔数 func_call_sql 编码' AFTER `expected_fixed_count`,
  ADD COLUMN `success_count_sql_code` VARCHAR(64) NULL COMMENT '成功笔数 func_call_sql 编码（未配置则成功率维度跳过）' AFTER `actual_count_sql_code`,
  ADD COLUMN `tolerance_rate` DECIMAL(6,4) NOT NULL DEFAULT 0.0000 COMMENT '容差比例（0-1，差异超出容差记异常）' AFTER `success_count_sql_code`,
  ADD COLUMN `alert_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '差异告警开关（WeCom 机器人）' AFTER `tolerance_rate`,
  ADD UNIQUE KEY `uk_ops_account_scenario_identity` (`business_line_code`, `environment_code`, `system_name`, `scenario_type`),
  ADD KEY `idx_ops_account_scenario_enabled` (`enabled`, `check_time`);

-- 3. 旧扫表对账模板全部停用（保留模板与审计历史，不再被渲染执行）
UPDATE `func_call_sql`
SET `enabled` = 0,
    `remark` = 'REQ-d227e09d：扫表对账下线，模板停用留档'
WHERE LEFT(`sql_code`, 8) = 'OPS_ACC_'
  AND `enabled` = 1;

-- 4. 旧每日对账 Job 配置删除（sys_job_execution_log 历史保留；新场景核对 Job 由 DynamicJobScheduler 以默认停用种子）
DELETE FROM `sys_job_config` WHERE `job_code` = 'OPS_ACCOUNTING_RECONCILE';

-- 5. 代扣场景占位模板（enabled=0，业务口径确认后页面修改并启用）
INSERT INTO `func_call_sql` (
  `sql_code`, `scenario_code`, `branch_order`, `name`, `description`,
  `sql_template`, `param_desc`, `source_module`, `enabled`, `remark`
)
SELECT seed.*
FROM (
  SELECT 'OPS_SCN_WITHHOLD_EXPECTED' AS `sql_code`,
         'OPS-SCN-WITHHOLD' AS `scenario_code`,
         1 AS `branch_order`,
         '批量代扣·应发起笔数（占位）' AS `name`,
         '当日应发起扣款的账单笔数；口径待业务确认后修改启用' AS `description`,
         '-- 口径占位：待业务确认应扣账单的表与筛选条件（示例：当日到期且未取消的应扣账单数）\nSELECT COUNT(*) AS cnt\nFROM `your_schema`.`due_bill_placeholder`\nWHERE bill_date >= :checkDate\n  AND bill_date < DATE_ADD(:checkDate, INTERVAL 1 DAY)\nLIMIT 1' AS `sql_template`,
         '参数：checkDate=核对日期（yyyy-MM-dd）；startDateTime/endExclusiveDateTime 由执行器统一传入，模板未引用时忽略' AS `param_desc`,
         'OPS_ACCOUNTING' AS `source_module`,
         0 AS `enabled`,
         'REQ-d227e09d：代扣场景占位模板，口径确认后启用' AS `remark` UNION ALL
  SELECT 'OPS_SCN_WITHHOLD_ACTUAL', 'OPS-SCN-WITHHOLD', 2, '批量代扣·实际发起笔数（占位）',
         '当日实际发起扣款的笔数；口径待业务确认后修改启用',
         '-- 口径占位：待业务确认扣款发起记录的表与筛选条件（示例：当日发起的扣款指令数）\nSELECT COUNT(*) AS cnt\nFROM `your_schema`.`withhold_request_placeholder`\nWHERE create_time >= :startDateTime\n  AND create_time < :endExclusiveDateTime\nLIMIT 1',
         '参数：startDateTime/endExclusiveDateTime=核对日窗口（含头不含尾，yyyy-MM-dd HH:mm:ss）',
         'OPS_ACCOUNTING', 0, 'REQ-d227e09d：代扣场景占位模板，口径确认后启用' UNION ALL
  SELECT 'OPS_SCN_WITHHOLD_SUCCESS', 'OPS-SCN-WITHHOLD', 3, '批量代扣·扣款成功笔数（占位）',
         '当日实际扣款成功的笔数；口径待业务确认后修改启用',
         '-- 口径占位：待业务确认扣款成功记录的表与筛选条件（示例：当日扣款成功数）\nSELECT COUNT(*) AS cnt\nFROM `your_schema`.`withhold_success_placeholder`\nWHERE finish_time >= :startDateTime\n  AND finish_time < :endExclusiveDateTime\n  AND status = 1\nLIMIT 1',
         '参数：startDateTime/endExclusiveDateTime=核对日窗口（含头不含尾，yyyy-MM-dd HH:mm:ss）',
         'OPS_ACCOUNTING', 0, 'REQ-d227e09d：代扣场景占位模板，口径确认后启用'
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM `func_call_sql` existing WHERE existing.`sql_code` = seed.`sql_code`
);

-- 6. 权限菜单描述与场景化口径对齐
UPDATE `sys_permission`
SET `permission_name` = '账务监测',
    `remark` = '业务场景预期核对监测'
WHERE `permission_code` = 'ops:accounting:view';
