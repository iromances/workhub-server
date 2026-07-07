UPDATE pay_merchant_param
SET file_name = NULL,
    masked_value = '历史文件'
WHERE source_type = 'FILE'
  AND remark LIKE '%原秘钥类型=%'
  AND file_name IN (
      CONCAT(param_key, '.pem'),
      CONCAT(param_key, '.key'),
      CONCAT(param_key, '.p12'),
      CONCAT(param_key, '.txt')
  );
