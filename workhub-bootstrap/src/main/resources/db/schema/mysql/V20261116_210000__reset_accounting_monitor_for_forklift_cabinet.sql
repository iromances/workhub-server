-- REQ-67108299：账务监测 FunctionCall 化——清空存量配置，收敛为叉车（lease_saps）与
-- 智能柜（ol_saps）两条 SAPS_BILL_CHAIN 配置（SQL 走 func_call_sql 登记模板，每日对账）。
-- 运行/结果/凭据/规则配置一并兜底清空（改造前均为空表或随旧配置失效）。

DELETE FROM `ops_account_rule_config`;
DELETE FROM `ops_account_reconcile_result`;
DELETE FROM `ops_account_reconcile_run`;
DELETE FROM `ops_account_execution_evidence`;
DELETE FROM `ops_account_monitor_config`;

INSERT INTO `ops_account_monitor_config` (
  `business_line_code`, `environment_code`, `system_name`, `display_name`,
  `database_target_key`, `schema_name`, `rule_profile`, `enabled`, `daily_enabled`, `remark`
)
SELECT seed.*
FROM (
  SELECT 'BL000008' AS `business_line_code`,
         'PROD' AS `environment_code`,
         'lease-saps' AS `system_name`,
         '叉车账务（账单-缴费单-支付订单-结算-对账单）' AS `display_name`,
         'db-prod' AS `database_target_key`,
         'lease_saps' AS `schema_name`,
         'SAPS_BILL_CHAIN' AS `rule_profile`,
         1 AS `enabled`,
         1 AS `daily_enabled`,
         'REQ-67108299：FunctionCall 模板规则（OPS_ACC_*），每日对账前一天' AS `remark` UNION ALL
  SELECT 'BL000005', 'PROD', 'ol-saps', '智能柜账务（账单-缴费单-支付订单-结算-对账单）',
         'db-prod', 'ol_saps', 'SAPS_BILL_CHAIN', 1, 1,
         'REQ-67108299：FunctionCall 模板规则（OPS_ACC_*），每日对账前一天'
) AS seed
WHERE NOT EXISTS (
  SELECT 1
  FROM `ops_account_monitor_config` existing
  WHERE existing.`business_line_code` = seed.`business_line_code`
    AND existing.`environment_code` = seed.`environment_code`
    AND existing.`system_name` = seed.`system_name`
);
