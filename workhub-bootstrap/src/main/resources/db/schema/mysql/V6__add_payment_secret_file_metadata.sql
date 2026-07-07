ALTER TABLE pay_merchant_secret
  ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'TEXT' AFTER algorithm,
  ADD COLUMN file_name VARCHAR(255) NULL AFTER source_type,
  ADD COLUMN file_content_type VARCHAR(128) NULL AFTER file_name,
  ADD COLUMN file_value_type VARCHAR(16) NULL AFTER file_content_type;
