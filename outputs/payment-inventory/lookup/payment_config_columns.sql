SELECT table_schema, table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema IN ('amp_payment', 'jiatai_amp_payment')
  AND table_name IN ('channel_pay_config', 'mer_channel_config')
ORDER BY table_schema, table_name, ordinal_position;
