SET @add_param_source_type = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pay_merchant_param'
          AND COLUMN_NAME = 'source_type'
    ),
    'SELECT 1',
    'ALTER TABLE pay_merchant_param ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT ''TEXT'' AFTER value_type'
);
PREPARE add_param_source_type_stmt FROM @add_param_source_type;
EXECUTE add_param_source_type_stmt;
DEALLOCATE PREPARE add_param_source_type_stmt;

SET @add_param_file_name = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pay_merchant_param'
          AND COLUMN_NAME = 'file_name'
    ),
    'SELECT 1',
    'ALTER TABLE pay_merchant_param ADD COLUMN file_name VARCHAR(255) NULL AFTER source_type'
);
PREPARE add_param_file_name_stmt FROM @add_param_file_name;
EXECUTE add_param_file_name_stmt;
DEALLOCATE PREPARE add_param_file_name_stmt;

SET @add_param_file_content_type = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pay_merchant_param'
          AND COLUMN_NAME = 'file_content_type'
    ),
    'SELECT 1',
    'ALTER TABLE pay_merchant_param ADD COLUMN file_content_type VARCHAR(128) NULL AFTER file_name'
);
PREPARE add_param_file_content_type_stmt FROM @add_param_file_content_type;
EXECUTE add_param_file_content_type_stmt;
DEALLOCATE PREPARE add_param_file_content_type_stmt;

SET @add_param_file_value_type = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pay_merchant_param'
          AND COLUMN_NAME = 'file_value_type'
    ),
    'SELECT 1',
    'ALTER TABLE pay_merchant_param ADD COLUMN file_value_type VARCHAR(16) NULL AFTER file_content_type'
);
PREPARE add_param_file_value_type_stmt FROM @add_param_file_value_type;
EXECUTE add_param_file_value_type_stmt;
DEALLOCATE PREPARE add_param_file_value_type_stmt;

UPDATE pay_merchant_param
SET source_type = 'TEXT'
WHERE source_type IS NULL OR source_type = '';

INSERT INTO pay_merchant_param (
    merchant_id,
    param_key,
    value_type,
    source_type,
    file_name,
    file_content_type,
    file_value_type,
    sensitive_flag,
    plain_value,
    encrypted_value,
    masked_value,
    remark
)
SELECT s.merchant_id,
       CASE
           WHEN EXISTS (
               SELECT 1
               FROM pay_merchant_param p
               WHERE p.merchant_id = s.merchant_id
                 AND p.param_key = s.secret_name
           ) THEN CONCAT(LEFT(s.secret_name, 100), '_secret_', s.id)
           ELSE s.secret_name
       END AS param_key,
       CASE WHEN COALESCE(s.source_type, 'TEXT') = 'FILE' THEN 'FILE' ELSE 'TEXT' END AS value_type,
       COALESCE(s.source_type, 'TEXT') AS source_type,
       s.file_name,
       s.file_content_type,
       s.file_value_type,
       1 AS sensitive_flag,
       NULL AS plain_value,
       s.encrypted_value,
       CASE WHEN COALESCE(s.source_type, 'TEXT') = 'FILE'
            THEN COALESCE(s.file_name, '历史文件')
            ELSE s.masked_value
       END AS masked_value,
       CONCAT_WS('；', s.remark, CONCAT('原秘钥类型=', s.secret_type), CONCAT('原秘钥状态=', s.status)) AS remark
FROM pay_merchant_secret s
WHERE NOT EXISTS (
    SELECT 1
    FROM pay_merchant_param p
    WHERE p.merchant_id = s.merchant_id
      AND p.param_key = CONCAT(LEFT(s.secret_name, 100), '_secret_', s.id)
);
