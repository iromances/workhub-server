ALTER TABLE `pm_attachment`
  MODIFY COLUMN `storage_path` VARCHAR(512) NULL,
  ADD COLUMN `file_content` LONGBLOB NULL AFTER `content_type`,
  ADD COLUMN `file_size` BIGINT NULL AFTER `file_content`,
  ADD COLUMN `file_sha256` CHAR(64) NULL AFTER `file_size`;
