SELECT
  id, cooperation_code, project_no, channel_code, pay_config_code, query_key,
  transaction_mode, status, is_delete, is_cancel, member_id, terminal_id,
  channel_url, notify_url, remark
FROM amp_payment.channel_pay_config
WHERE cooperation_code = 'HZF000141'
ORDER BY id;
