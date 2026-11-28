-- 手动恢复 V20260920_155225。由维护人员作为新的事务型 Flyway 迁移发布，配套 .sql.conf 一起使用。
-- 在停止相关业务写入的维护窗口执行。断言由原迁移的 CHECK(passed=1) 执行，出错时回滚整笔事务。
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'restore_transaction_enabled', @@SESSION.autocommit = 0;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'restore_snapshots_present',
    EXISTS (SELECT 1 FROM wh_team_merge_20260920_checks WHERE check_name = 'snapshot_counts' AND passed = 1)
    AND (SELECT COUNT(*) FROM wh_bak_20260920_team_after) = 7
    AND (SELECT COUNT(*) FROM wh_bak_20260920_team_before) >= 7;
SET @wh_saved_limit = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;

-- 核对三组表的列名、顺序、类型与排序规则。现有结构与快照不同时交由维护人员处理。
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
WITH target_tables (target, snapshot) AS (
    SELECT 'pm_developer_resource', 'wh_bak_20260920_team'
    UNION ALL SELECT 'pm_intake_record', 'wh_bak_20260920_intake'
    UNION ALL SELECT 'pm_work_item', 'wh_bak_20260920_work'
    UNION ALL SELECT 'pm_intake_development_analysis', 'wh_bak_20260920_analysis'
), layouts AS (
    SELECT t.target, c.table_name, GROUP_CONCAT(
        JSON_ARRAY(c.column_name, c.column_type, c.collation_name, c.is_nullable, c.extra)
        ORDER BY c.ordinal_position SEPARATOR '|') AS layout
    FROM target_tables t JOIN information_schema.columns c
      ON c.table_schema = DATABASE()
     AND c.table_name IN (t.target, CONCAT(t.snapshot, '_before'), CONCAT(t.snapshot, '_after'))
    JOIN information_schema.tables e ON e.table_schema = c.table_schema AND e.table_name = c.table_name
      AND e.engine = 'InnoDB'
    GROUP BY t.target, c.table_name
)
SELECT 'restore_table_layouts', (SELECT COUNT(*) FROM layouts) = 12
    AND NOT EXISTS (SELECT 1 FROM layouts GROUP BY target HAVING COUNT(DISTINCT BINARY layout) <> 1);

-- pm_developer_resource。锁定业务行，逐列对照合并后快照，任何后续修改均使本次恢复失败。
SELECT t.id FROM pm_developer_resource t JOIN wh_bak_20260920_team_before b ON b.id = t.id FOR UPDATE;
SELECT GROUP_CONCAT(CONCAT('(BINARY t.`', column_name, '` <=> BINARY a.`', column_name, '`)')
                    ORDER BY ordinal_position SEPARATOR ' AND ')
INTO @wh_equal FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pm_developer_resource';
SET @wh_sql = CONCAT(
    'INSERT INTO wh_team_merge_20260920_checks (check_name, passed) SELECT ''restore_team_after'', NOT EXISTS (',
    'SELECT 1 FROM wh_bak_20260920_team_after a LEFT JOIN pm_developer_resource t ON t.id = a.id ',
    'LEFT JOIN wh_bak_20260920_team_before b ON b.id = a.id WHERE t.id IS NULL OR b.id IS NULL OR NOT (', @wh_equal, '))');
PREPARE wh_restore FROM @wh_sql;
EXECUTE wh_restore;
DEALLOCATE PREPARE wh_restore;
-- 合并仅删除来源账号，保留的账号已通过后镜像校验；主键或唯一键被复用时 INSERT 会失败。
INSERT INTO pm_developer_resource
SELECT b.* FROM wh_bak_20260920_team_before b LEFT JOIN wh_bak_20260920_team_after a ON a.id = b.id WHERE a.id IS NULL;

-- pm_intake_record。锁定业务行，逐列对照合并后快照，任何后续修改均使本次恢复失败。
SELECT t.id FROM pm_intake_record t JOIN wh_bak_20260920_intake_before b ON b.id = t.id FOR UPDATE;
SELECT GROUP_CONCAT(CONCAT('(BINARY t.`', column_name, '` <=> BINARY a.`', column_name, '`)')
                    ORDER BY ordinal_position SEPARATOR ' AND ')
INTO @wh_equal FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pm_intake_record';
SET @wh_sql = CONCAT(
    'INSERT INTO wh_team_merge_20260920_checks (check_name, passed) SELECT ''restore_intake_after'', NOT EXISTS (',
    'SELECT 1 FROM wh_bak_20260920_intake_after a LEFT JOIN pm_intake_record t ON t.id = a.id ',
    'LEFT JOIN wh_bak_20260920_intake_before b ON b.id = a.id WHERE t.id IS NULL OR b.id IS NULL OR NOT (', @wh_equal, '))');
PREPARE wh_restore FROM @wh_sql;
EXECUTE wh_restore;
DEALLOCATE PREPARE wh_restore;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'restore_intake_ids', NOT EXISTS (
    SELECT 1 FROM wh_bak_20260920_intake_before b LEFT JOIN wh_bak_20260920_intake_after a ON a.id = b.id WHERE a.id IS NULL);
UPDATE pm_intake_record t JOIN wh_bak_20260920_intake_before b ON b.id = t.id
SET t.development_owner_user_name = b.development_owner_user_name,
    t.structured_data_json = b.structured_data_json,
    t.updated_at = b.updated_at;

-- pm_work_item。锁定业务行，逐列对照合并后快照，任何后续修改均使本次恢复失败。
SELECT t.id FROM pm_work_item t JOIN wh_bak_20260920_work_before b ON b.id = t.id FOR UPDATE;
SELECT GROUP_CONCAT(CONCAT('(BINARY t.`', column_name, '` <=> BINARY a.`', column_name, '`)')
                    ORDER BY ordinal_position SEPARATOR ' AND ')
INTO @wh_equal FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pm_work_item';
SET @wh_sql = CONCAT(
    'INSERT INTO wh_team_merge_20260920_checks (check_name, passed) SELECT ''restore_work_after'', NOT EXISTS (',
    'SELECT 1 FROM wh_bak_20260920_work_after a LEFT JOIN pm_work_item t ON t.id = a.id ',
    'LEFT JOIN wh_bak_20260920_work_before b ON b.id = a.id WHERE t.id IS NULL OR b.id IS NULL OR NOT (', @wh_equal, '))');
PREPARE wh_restore FROM @wh_sql;
EXECUTE wh_restore;
DEALLOCATE PREPARE wh_restore;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'restore_work_ids', NOT EXISTS (
    SELECT 1 FROM wh_bak_20260920_work_before b LEFT JOIN wh_bak_20260920_work_after a ON a.id = b.id WHERE a.id IS NULL);
UPDATE pm_work_item t JOIN wh_bak_20260920_work_before b ON b.id = t.id
SET t.owner_user_name = b.owner_user_name,
    t.follower_user_name = b.follower_user_name,
    t.updated_at = b.updated_at;

-- pm_intake_development_analysis。锁定业务行，逐列对照合并后快照，任何后续修改均使本次恢复失败。
SELECT t.id FROM pm_intake_development_analysis t JOIN wh_bak_20260920_analysis_before b ON b.id = t.id FOR UPDATE;
SELECT GROUP_CONCAT(CONCAT('(BINARY t.`', column_name, '` <=> BINARY a.`', column_name, '`)')
                    ORDER BY ordinal_position SEPARATOR ' AND ')
INTO @wh_equal FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pm_intake_development_analysis';
SET @wh_sql = CONCAT(
    'INSERT INTO wh_team_merge_20260920_checks (check_name, passed) SELECT ''restore_analysis_after'', NOT EXISTS (',
    'SELECT 1 FROM wh_bak_20260920_analysis_after a LEFT JOIN pm_intake_development_analysis t ON t.id = a.id ',
    'LEFT JOIN wh_bak_20260920_analysis_before b ON b.id = a.id WHERE t.id IS NULL OR b.id IS NULL OR NOT (', @wh_equal, '))');
PREPARE wh_restore FROM @wh_sql;
EXECUTE wh_restore;
DEALLOCATE PREPARE wh_restore;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'restore_analysis_ids', NOT EXISTS (
    SELECT 1 FROM wh_bak_20260920_analysis_before b LEFT JOIN wh_bak_20260920_analysis_after a ON a.id = b.id WHERE a.id IS NULL);
UPDATE pm_intake_development_analysis t JOIN wh_bak_20260920_analysis_before b ON b.id = t.id
SET t.draft_json = b.draft_json,
    t.updated_at = b.updated_at;

SET SESSION group_concat_max_len = @wh_saved_limit;
