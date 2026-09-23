-- 人员归并：仅包含DML，由Flyway executeInTransaction=true管理整个事务。
-- 快照表和断言表必须在前一个独立DDL版本创建。禁止添加DDL、显式COMMIT或存储过程。
SET @wh_team_aliases = '[
 {"old":"蔡锐","new":"cairui","display":"蔡锐","source":386,"target":1},
 {"old":"尹军","new":"yinjun","display":"尹军","source":382,"target":2},
 {"old":"石浩","new":"shihao","display":"石浩","source":469,"target":5},
 {"old":"韩猛","new":"hanmeng","display":"韩猛","source":460,"target":6},
 {"old":"薛文韬","new":"xuewentao","display":"薛文韬","source":421,"target":8},
 {"old":"徐杰呢","new":"xujieni","display":"徐杰呢","source":407,"target":10},
 {"old":"智云韬","new":"zhiyuntao","display":"智云涛","source":420,"target":9},
 {"old":"智云涛","new":"zhiyuntao","display":"智云涛","source":null,"target":9}
]';

SET @wh_team_invalid_json =
    EXISTS (SELECT 1 FROM pm_intake_record WHERE structured_data_json IS NOT NULL AND NOT JSON_VALID(structured_data_json))
    OR EXISTS (SELECT 1 FROM pm_intake_development_analysis WHERE draft_json IS NOT NULL AND NOT JSON_VALID(draft_json));
SET @wh_team_sources = (
    SELECT COUNT(*) FROM pm_developer_resource d
    JOIN JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
    ON BINARY d.user_name = BINARY m.old_name
);
SET @wh_team_needed = @wh_team_sources > 0 OR @wh_team_invalid_json
    OR EXISTS (SELECT 1 FROM pm_intake_record r
               JOIN JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
               ON BINARY r.development_owner_user_name = BINARY m.old_name
               OR BINARY JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(r.structured_data_json), r.structured_data_json, NULL), '$.developmentOwnerUserName')) = BINARY m.old_name)
    OR EXISTS (SELECT 1 FROM pm_work_item w
               JOIN JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
               ON BINARY w.owner_user_name = BINARY m.old_name OR BINARY w.follower_user_name = BINARY m.old_name)
    OR EXISTS (SELECT 1 FROM pm_intake_development_analysis a
               JOIN JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
               ON JSON_SEARCH(IF(JSON_VALID(a.draft_json), a.draft_json, NULL), 'one', m.old_name, NULL,
                              '$.developerPool[*]', '$.workItems[*].ownerUserName') IS NOT NULL)
    OR EXISTS (SELECT 1 FROM pm_intake_todo t
               JOIN JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
               ON BINARY t.assignee_user_name = BINARY m.old_name)
    OR EXISTS (SELECT 1 FROM pm_project p
               JOIN JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
               ON BINARY p.owner_user_name = BINARY m.old_name)
    OR EXISTS (SELECT 1 FROM pm_business_line_member b
               JOIN JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
               ON BINARY b.member_user_name = BINARY m.old_name);

-- CHECK(passed=1)任一断言失败将抛出错误，由Flyway回滚本版本全部DML。
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'transaction_enabled', @@SESSION.autocommit = 0 WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'snapshots_empty',
       NOT EXISTS (SELECT 1 FROM wh_bak_20260920_team_before)
       AND NOT EXISTS (SELECT 1 FROM wh_bak_20260920_team_after)
       AND NOT EXISTS (SELECT 1 FROM wh_bak_20260920_intake_before)
       AND NOT EXISTS (SELECT 1 FROM wh_bak_20260920_intake_after)
       AND NOT EXISTS (SELECT 1 FROM wh_bak_20260920_work_before)
       AND NOT EXISTS (SELECT 1 FROM wh_bak_20260920_work_after)
       AND NOT EXISTS (SELECT 1 FROM wh_bak_20260920_analysis_before)
       AND NOT EXISTS (SELECT 1 FROM wh_bak_20260920_analysis_after)
WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'valid_json', NOT @wh_team_invalid_json WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'transactional_tables', NOT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
    AND table_name IN ('pm_developer_resource','pm_intake_record','pm_work_item','pm_intake_development_analysis',
                       'wh_team_merge_20260920_checks',
                       'wh_bak_20260920_team_before','wh_bak_20260920_team_after',
                       'wh_bak_20260920_intake_before','wh_bak_20260920_intake_after',
                       'wh_bak_20260920_work_before','wh_bak_20260920_work_after',
                       'wh_bak_20260920_analysis_before','wh_bak_20260920_analysis_after')
    AND engine <> 'InnoDB') WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'canonical_identities', NOT EXISTS (
    SELECT 1 FROM JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (
        new_name VARCHAR(64) PATH '$.new', display_name VARCHAR(64) PATH '$.display', target_id BIGINT PATH '$.target')) m
    LEFT JOIN pm_developer_resource d ON BINARY d.user_name = BINARY m.new_name
    WHERE d.id IS NULL OR d.id <> m.target_id OR d.enabled <> 1
       OR NOT (BINARY d.display_name <=> BINARY m.display_name)) WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'source_identities', NOT EXISTS (
    SELECT 1 FROM JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (
        old_name VARCHAR(64) PATH '$.old', source_id BIGINT PATH '$.source')) m
    JOIN pm_developer_resource d ON BINARY d.user_name = BINARY m.old_name
    WHERE m.source_id IS NULL OR d.id <> m.source_id OR d.enabled <> 1
       OR NOT (BINARY d.display_name <=> BINARY m.old_name) OR COALESCE(d.remark,'') <> '') WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
WITH aliases AS (SELECT old_name FROM JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m)
SELECT 'no_additional_references',
    NOT EXISTS (SELECT 1 FROM pm_intake_todo t JOIN aliases m ON BINARY t.assignee_user_name = BINARY m.old_name)
    AND NOT EXISTS (SELECT 1 FROM pm_project p JOIN aliases m ON BINARY p.owner_user_name = BINARY m.old_name)
    AND NOT EXISTS (SELECT 1 FROM pm_business_line_member b JOIN aliases m ON BINARY b.member_user_name = BINARY m.old_name)
WHERE @wh_team_needed;

SET @wh_team_total = (SELECT COUNT(*) FROM pm_developer_resource);
INSERT INTO wh_bak_20260920_team_before
SELECT d.* FROM pm_developer_resource d WHERE @wh_team_needed AND EXISTS (
    SELECT 1 FROM JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old', new_name VARCHAR(64) PATH '$.new')) m
    WHERE BINARY d.user_name = BINARY m.old_name OR BINARY d.user_name = BINARY m.new_name);
INSERT INTO wh_bak_20260920_intake_before
SELECT r.* FROM pm_intake_record r WHERE @wh_team_needed AND EXISTS (
    SELECT 1 FROM JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
    WHERE BINARY r.development_owner_user_name = BINARY m.old_name
       OR BINARY JSON_UNQUOTE(JSON_EXTRACT(r.structured_data_json, '$.developmentOwnerUserName')) = BINARY m.old_name);
INSERT INTO wh_bak_20260920_work_before
SELECT w.* FROM pm_work_item w WHERE @wh_team_needed AND EXISTS (
    SELECT 1 FROM JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
    WHERE BINARY w.owner_user_name = BINARY m.old_name OR BINARY w.follower_user_name = BINARY m.old_name);
INSERT INTO wh_bak_20260920_analysis_before
SELECT a.* FROM pm_intake_development_analysis a WHERE @wh_team_needed AND EXISTS (
    SELECT 1 FROM JSON_TABLE(@wh_team_aliases, '$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
    WHERE JSON_SEARCH(a.draft_json, 'one', m.old_name, NULL, '$.developerPool[*]', '$.workItems[*].ownerUserName') IS NOT NULL);

INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'draft_array_shapes', NOT EXISTS (
    SELECT 1 FROM wh_bak_20260920_analysis_before
    WHERE COALESCE(JSON_TYPE(JSON_EXTRACT(draft_json,'$.developerPool')),'NULL') NOT IN ('ARRAY','NULL')
       OR COALESCE(JSON_TYPE(JSON_EXTRACT(draft_json,'$.workItems')),'NULL') NOT IN ('ARRAY','NULL')) WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name, passed)
SELECT 'pool_member_types', NOT EXISTS (
    SELECT 1 FROM wh_bak_20260920_analysis_before b
    JOIN JSON_TABLE(b.draft_json,'$.developerPool[*]' COLUMNS (member_value JSON PATH '$')) j
    WHERE COALESCE(JSON_TYPE(j.member_value),'NULL') NOT IN ('STRING','NULL')) WHERE @wh_team_needed;

UPDATE pm_intake_record r JOIN wh_bak_20260920_intake_before b ON b.id = r.id
JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old',new_name VARCHAR(64) PATH '$.new')) m
ON BINARY r.development_owner_user_name = BINARY m.old_name
SET r.development_owner_user_name=m.new_name, r.updated_at=b.updated_at WHERE @wh_team_needed;
UPDATE pm_intake_record r JOIN wh_bak_20260920_intake_before b ON b.id = r.id
JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old',new_name VARCHAR(64) PATH '$.new')) m
ON BINARY JSON_UNQUOTE(JSON_EXTRACT(r.structured_data_json,'$.developmentOwnerUserName')) = BINARY m.old_name
SET r.structured_data_json=JSON_SET(r.structured_data_json,'$.developmentOwnerUserName',m.new_name), r.updated_at=b.updated_at WHERE @wh_team_needed;
UPDATE pm_work_item w JOIN wh_bak_20260920_work_before b ON b.id = w.id
JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old',new_name VARCHAR(64) PATH '$.new')) m
ON BINARY w.owner_user_name=BINARY m.old_name
SET w.owner_user_name=m.new_name, w.updated_at=b.updated_at WHERE @wh_team_needed;
UPDATE pm_work_item w JOIN wh_bak_20260920_work_before b ON b.id = w.id
JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old',new_name VARCHAR(64) PATH '$.new')) m
ON BINARY w.follower_user_name=BINARY m.old_name
SET w.follower_user_name=m.new_name, w.updated_at=b.updated_at WHERE @wh_team_needed;

-- 窗口聚合显式指定序号与完整窗口，避免JSON_ARRAYAGG无序重排任务。
WITH task_values AS (
    SELECT b.id, j.seq,
           CASE WHEN m.new_name IS NULL THEN j.item ELSE JSON_SET(j.item,'$.ownerUserName',m.new_name) END AS item
    FROM wh_bak_20260920_analysis_before b
    JOIN JSON_TABLE(b.draft_json,'$.workItems[*]' COLUMNS (seq FOR ORDINALITY,item JSON PATH '$')) j
    LEFT JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old',new_name VARCHAR(64) PATH '$.new')) m
    ON BINARY JSON_UNQUOTE(JSON_EXTRACT(j.item,'$.ownerUserName'))=BINARY m.old_name
    WHERE @wh_team_needed AND JSON_TYPE(JSON_EXTRACT(b.draft_json,'$.workItems'))='ARRAY'
), rebuilt AS (
    SELECT id, JSON_ARRAYAGG(CAST(item AS JSON)) OVER (PARTITION BY id ORDER BY seq ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS items,
           ROW_NUMBER() OVER (PARTITION BY id ORDER BY seq DESC) AS pick FROM task_values
)
UPDATE pm_intake_development_analysis a JOIN rebuilt r ON r.id=a.id AND r.pick=1
JOIN wh_bak_20260920_analysis_before b ON b.id=a.id
SET a.draft_json=JSON_SET(a.draft_json,'$.workItems',r.items), a.updated_at=b.updated_at WHERE @wh_team_needed;

WITH member_values AS (
    SELECT b.id,j.seq,
           CASE WHEN m.new_name IS NULL THEN COALESCE(j.member_value,CAST('null' AS JSON))
                ELSE JSON_EXTRACT(JSON_OBJECT('value',m.new_name),'$.value') END AS member_value
    FROM wh_bak_20260920_analysis_before b
    JOIN JSON_TABLE(b.draft_json,'$.developerPool[*]' COLUMNS (seq FOR ORDINALITY,member_value JSON PATH '$')) j
    LEFT JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old',new_name VARCHAR(64) PATH '$.new')) m
    ON BINARY JSON_UNQUOTE(j.member_value)=BINARY m.old_name
    WHERE @wh_team_needed AND JSON_TYPE(JSON_EXTRACT(b.draft_json,'$.developerPool'))='ARRAY'
), first_members AS (
    SELECT id,seq,member_value,ROW_NUMBER() OVER (PARTITION BY id,BINARY CAST(member_value AS CHAR CHARACTER SET utf8mb4) ORDER BY seq) AS occurrence FROM member_values
), rebuilt AS (
    SELECT id,JSON_ARRAYAGG(CAST(member_value AS JSON)) OVER (PARTITION BY id ORDER BY seq ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS members,
           ROW_NUMBER() OVER (PARTITION BY id ORDER BY seq DESC) AS pick FROM first_members WHERE occurrence=1
)
UPDATE pm_intake_development_analysis a JOIN rebuilt r ON r.id=a.id AND r.pick=1
JOIN wh_bak_20260920_analysis_before b ON b.id=a.id
SET a.draft_json=JSON_SET(a.draft_json,'$.developerPool',r.members), a.updated_at=b.updated_at WHERE @wh_team_needed;

INSERT INTO wh_team_merge_20260920_checks (check_name,passed)
WITH aliases AS (SELECT old_name FROM JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m)
SELECT 'no_old_references',
    NOT EXISTS (SELECT 1 FROM pm_intake_record r JOIN aliases m
                ON BINARY r.development_owner_user_name=BINARY m.old_name
                OR BINARY JSON_UNQUOTE(JSON_EXTRACT(r.structured_data_json,'$.developmentOwnerUserName'))=BINARY m.old_name)
    AND NOT EXISTS (SELECT 1 FROM pm_work_item w JOIN aliases m ON BINARY w.owner_user_name=BINARY m.old_name OR BINARY w.follower_user_name=BINARY m.old_name)
    AND NOT EXISTS (SELECT 1 FROM pm_intake_development_analysis a JOIN aliases m
                    ON JSON_SEARCH(a.draft_json,'one',m.old_name,NULL,'$.developerPool[*]','$.workItems[*].ownerUserName') IS NOT NULL)
WHERE @wh_team_needed;

DELETE d FROM pm_developer_resource d JOIN wh_bak_20260920_team_before b ON b.id=d.id
JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m ON BINARY d.user_name=BINARY m.old_name
WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name,passed)
-- Flyway读取警告会执行SHOW WARNINGS，不能依赖上一条语句的ROW_COUNT()。
SELECT 'deleted_source_count', (
    SELECT COUNT(*) FROM wh_bak_20260920_team_before b
    JOIN JSON_TABLE(@wh_team_aliases,'$[*]' COLUMNS (old_name VARCHAR(64) PATH '$.old')) m
        ON BINARY b.user_name=BINARY m.old_name
    LEFT JOIN pm_developer_resource d ON d.id=b.id
    WHERE d.id IS NULL
)=@wh_team_sources WHERE @wh_team_needed;

INSERT INTO wh_bak_20260920_team_after SELECT d.* FROM pm_developer_resource d JOIN wh_bak_20260920_team_before b ON b.id=d.id WHERE @wh_team_needed;
INSERT INTO wh_bak_20260920_intake_after SELECT r.* FROM pm_intake_record r JOIN wh_bak_20260920_intake_before b ON b.id=r.id WHERE @wh_team_needed;
INSERT INTO wh_bak_20260920_work_after SELECT w.* FROM pm_work_item w JOIN wh_bak_20260920_work_before b ON b.id=w.id WHERE @wh_team_needed;
INSERT INTO wh_bak_20260920_analysis_after SELECT a.* FROM pm_intake_development_analysis a JOIN wh_bak_20260920_analysis_before b ON b.id=a.id WHERE @wh_team_needed;
INSERT INTO wh_team_merge_20260920_checks (check_name,passed)
SELECT 'snapshot_counts',
    (SELECT COUNT(*) FROM pm_developer_resource)=@wh_team_total-@wh_team_sources
    AND (SELECT COUNT(*) FROM wh_bak_20260920_team_after)=7
    AND (SELECT COUNT(*) FROM wh_bak_20260920_team_before)=7+@wh_team_sources
    AND (SELECT COUNT(*) FROM wh_bak_20260920_intake_after)=(SELECT COUNT(*) FROM wh_bak_20260920_intake_before)
    AND (SELECT COUNT(*) FROM wh_bak_20260920_work_after)=(SELECT COUNT(*) FROM wh_bak_20260920_work_before)
    AND (SELECT COUNT(*) FROM wh_bak_20260920_analysis_after)=(SELECT COUNT(*) FROM wh_bak_20260920_analysis_before)
WHERE @wh_team_needed;

SET @wh_team_aliases=NULL, @wh_team_sources=NULL, @wh_team_total=NULL, @wh_team_needed=NULL, @wh_team_invalid_json=NULL;
