-- V6 added file metadata for merchant secrets, but earlier file uploads only kept
-- the encrypted value. Mark legacy records that are strongly file-like so the UI
-- can show a download action instead of only a masked value.
UPDATE pay_merchant_secret
SET source_type = 'FILE',
    file_name = CASE
        WHEN LOWER(secret_name) REGEXP '\\.(pem|key|crt|cer|der|p12|pfx)$' THEN secret_name
        ELSE NULL
    END,
    file_content_type = CASE
        WHEN LOWER(secret_name) REGEXP '\\.(p12|pfx)$'
             OR LOWER(secret_name) LIKE '%p12%'
             OR LOWER(secret_name) LIKE '%pfx%' THEN 'application/octet-stream'
        ELSE NULL
    END,
    file_value_type = CASE
        WHEN LOWER(secret_name) REGEXP '\\.(p12|pfx)$'
             OR LOWER(secret_name) LIKE '%p12%'
             OR LOWER(secret_name) LIKE '%pfx%' THEN 'BINARY'
        ELSE 'TEXT'
    END
WHERE source_type = 'TEXT'
  AND (file_name IS NULL OR file_name = '')
  AND (
      secret_type IN ('PRIVATE_KEY', 'PUBLIC_KEY', 'CERTIFICATE')
      OR LOWER(secret_name) REGEXP '\\.(pem|key|crt|cer|der|p12|pfx)$'
      OR LOWER(secret_name) LIKE '%cert%'
      OR LOWER(secret_name) LIKE '%pem%'
      OR LOWER(secret_name) LIKE '%key%'
      OR LOWER(secret_name) LIKE '%p12%'
      OR LOWER(secret_name) LIKE '%pfx%'
      OR secret_name LIKE '%证书%'
      OR secret_name LIKE '%公钥%'
      OR secret_name LIKE '%私钥%'
  );
