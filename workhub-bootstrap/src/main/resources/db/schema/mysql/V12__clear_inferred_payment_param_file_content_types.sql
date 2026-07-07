UPDATE pay_merchant_param
SET file_content_type = NULL
WHERE source_type = 'FILE'
  AND remark LIKE '%原秘钥类型=%'
  AND file_content_type = 'text/plain; charset=utf-8';
