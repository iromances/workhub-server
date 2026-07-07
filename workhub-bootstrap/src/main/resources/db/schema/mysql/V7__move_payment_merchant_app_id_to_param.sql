INSERT INTO pay_merchant_param (
    merchant_id,
    param_key,
    value_type,
    sensitive_flag,
    plain_value,
    encrypted_value,
    masked_value,
    remark
)
SELECT
    m.id,
    'appId',
    'TEXT',
    0,
    m.app_id,
    NULL,
    m.app_id,
    '由支付商户 AppId 字段迁移'
FROM pay_merchant_account m
WHERE m.app_id IS NOT NULL
  AND TRIM(m.app_id) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM pay_merchant_param p
      WHERE p.merchant_id = m.id
        AND p.param_key = 'appId'
  );

ALTER TABLE pay_merchant_account
    DROP COLUMN app_id;
