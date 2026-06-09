SELECT
  'asset.channel_pay_config' AS source_table,
  cooperation_code,
  project_no,
  channel_code,
  pay_config_code,
  query_key,
  transaction_mode,
  CONCAT_WS('\n',
    CONCAT('pay_config_code=', COALESCE(pay_config_code, '')),
    CONCAT('query_key=', COALESCE(query_key, '')),
    CONCAT('member_id=', COALESCE(member_id, '')),
    CONCAT('terminal_id=', COALESCE(terminal_id, '')),
    CONCAT('pub_key=', CASE WHEN pub_key IS NULL OR pub_key = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('pub_path=', COALESCE(pub_path, '')),
    CONCAT('pri_key=', CASE WHEN pri_key IS NULL OR pri_key = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('pri_path=', COALESCE(pri_path, '')),
    CONCAT('pri_key_pwd=', CASE WHEN pri_key_pwd IS NULL OR pri_key_pwd = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('trans_key=', CASE WHEN trans_key IS NULL OR trans_key = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('channel_url=', COALESCE(channel_url, '')),
    CONCAT('notify_url=', COALESCE(notify_url, '')),
    CONCAT('redirect_url=', COALESCE(redirect_url, '')),
    CONCAT('share_notify_url=', COALESCE(share_notify_url, '')),
    CONCAT('check_file_url=', COALESCE(check_file_url, '')),
    CONCAT('check_file_ip=', COALESCE(check_file_ip, '')),
    CONCAT('check_remit_member_id=', COALESCE(check_remit_member_id, ''))
  ) AS config_values
FROM amp_payment.channel_pay_config
WHERE status = 1 AND COALESCE(is_delete, 0) = 0 AND COALESCE(is_cancel, 0) = 0
  AND cooperation_code IN (
    'HZF000036','HZF000038','HZF000039','HZF000118','HZF000125',
    'HZF000126','HZF000127','HZF000128','HZF000130','HZF000133',
    'HZF000135','HZF000137','HZF000138','HZF000141','HZF000142'
  )
UNION ALL
SELECT
  'asset.mer_channel_config' AS source_table,
  mer_id AS cooperation_code,
  'REMIT' AS project_no,
  channel_code,
  '' AS pay_config_code,
  CONCAT(mer_id, '|', channel_code, '|', product_type) AS query_key,
  product_type AS transaction_mode,
  CONCAT_WS('\n',
    CONCAT('mer_id=', COALESCE(mer_id, '')),
    CONCAT('channel_code=', COALESCE(channel_code, '')),
    CONCAT('channel_name=', COALESCE(channel_name, '')),
    CONCAT('product_type=', COALESCE(product_type, '')),
    CONCAT('channel_mer_id=', COALESCE(channel_mer_id, '')),
    CONCAT('channel_acq_id=', COALESCE(channel_acq_id, '')),
    CONCAT('pub_key=', CASE WHEN pub_key IS NULL OR pub_key = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('pub_path=', COALESCE(pub_path, '')),
    CONCAT('pri_key=', CASE WHEN pri_key IS NULL OR pri_key = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('pri_path=', COALESCE(pri_path, '')),
    CONCAT('cert_pwd=', CASE WHEN cert_pwd IS NULL OR cert_pwd = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('trans_key=', CASE WHEN trans_key IS NULL OR trans_key = '' THEN '' ELSE '已配置，脱敏' END),
    CONCAT('channel_url=', COALESCE(channel_url, '')),
    CONCAT('notify_url=', COALESCE(notify_url, ''))
  ) AS config_values
FROM amp_payment.mer_channel_config
WHERE status = 1 AND COALESCE(is_delete, 0) = 0 AND COALESCE(is_cancel, 0) = 0
  AND product_type = 'REMIT'
  AND mer_id IN (
    'HZF000036','HZF000038','HZF000039','HZF000118','HZF000125',
    'HZF000126','HZF000127','HZF000128','HZF000130','HZF000133',
    'HZF000135','HZF000137','HZF000138','HZF000141','HZF000142'
  )
ORDER BY cooperation_code, channel_code, project_no, pay_config_code;
