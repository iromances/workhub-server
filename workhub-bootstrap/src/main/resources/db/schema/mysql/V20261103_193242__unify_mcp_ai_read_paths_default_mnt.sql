-- REQ-da3c89c7 v2：复用 allowed_log_paths_json，统一允许 AI 读取的目录。
-- 用户明确要求全部服务器当前默认 /mnt；迁移前仅备份目录配置，不复制任何凭据。
CREATE TABLE `bak_mcp_ai_read_paths_20261103_193242` AS
SELECT `id`, `target_key`, `allowed_log_paths_json`
FROM `mcp_resource_config`
WHERE `resource_type` = 'SERVER';

UPDATE `mcp_resource_config`
SET `allowed_log_paths_json` = '["/mnt"]', `updated_at` = CURRENT_TIMESTAMP
WHERE `resource_type` = 'SERVER';

-- 回滚应用可保留 /mnt。恢复备份中的其他目录属于扩权，须逐项目标取得用户明确授权。
