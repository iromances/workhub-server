-- REQ-d227e09d v2：用户确认接入单据支付一致性，日期可选，默认全量分批，仅手动。
ALTER TABLE ops_account_monitor_config
  MODIFY actual_count_sql_code VARCHAR(64) NULL COMMENT '流程场景实际笔数SQL；一致性场景使用内置规则目录';
ALTER TABLE ops_account_reconcile_run
  MODIFY start_time DATETIME NULL COMMENT '核验日期起点；NULL表示全量',
  MODIFY end_time DATETIME NULL COMMENT '核验日期终点，不含；NULL表示全量';

INSERT INTO ops_account_monitor_config
  (business_line_code,environment_code,system_name,display_name,scenario_type,
   check_time,tolerance_rate,alert_enabled,enabled,remark)
SELECT s.code,'PROD',s.system_name,s.name,'ACCOUNTING_INTEGRITY','08:30:00',0,0,1,
       '日期可选，默认全量分批；仅手动核验，自动调度和通知关闭'
FROM (SELECT 'BL000008' AS code,'lease-saps' AS system_name,'叉车单据及支付一致性' AS name
      UNION ALL SELECT 'BL000005','ol-saps','智能柜单据及支付一致性') s
WHERE NOT EXISTS (SELECT 1 FROM ops_account_monitor_config c
  WHERE c.business_line_code=s.code AND c.environment_code='PROD'
    AND c.system_name=s.system_name AND c.scenario_type='ACCOUNTING_INTEGRITY');
