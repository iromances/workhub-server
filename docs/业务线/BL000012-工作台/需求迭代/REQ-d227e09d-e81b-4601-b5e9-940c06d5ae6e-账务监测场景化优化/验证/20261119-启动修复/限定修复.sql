-- 仅用于恢复本次启动失败；执行前备份并确认下面的 SELECT 恰好返回一条失败记录。
-- 用户已于 2026-11-19 在当前会话明确授权本次限定维护；后续执行不得扩大范围。
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM flyway_schema_history
WHERE version = '20261119.170000'
  AND script = 'V20261119_170000__rename_monitor_display_labels.sql'
  AND type = 'SQL' AND success = 0
LIMIT 2;

-- 只在数量为 1 且其他迁移校验没有异常时，在事务内执行；删除行数必须为 1。
DELETE FROM flyway_schema_history
WHERE version = '20261119.170000'
  AND script = 'V20261119_170000__rename_monitor_display_labels.sql'
  AND description = 'rename monitor display labels'
  AND type = 'SQL' AND success = 0;

-- 然后由正常启动流程重跑修正后的迁移；不要执行全库 flyway repair 或清理其他历史记录。
