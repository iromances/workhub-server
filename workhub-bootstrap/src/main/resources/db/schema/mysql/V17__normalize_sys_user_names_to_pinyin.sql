SET @workhub_user_name_pinyin_mapping = '[
  {"old":"蔡锐","new":"cairui"},
  {"old":"尹军","new":"yinjun"},
  {"old":"张华君","new":"zhanghuajun"},
  {"old":"石浩","new":"shihao"},
  {"old":"韩猛","new":"hanmeng"},
  {"old":"吕刘浩","new":"lvliuhao"},
  {"old":"薛文韬","new":"xuewentao"},
  {"old":"智云涛","new":"zhiyuntao"},
  {"old":"徐杰呢","new":"xujieni"}
]';

SELECT COALESCE(
         JSON_ARRAYAGG(JSON_OBJECT('old', mapping.old_user_name, 'new', mapping.new_user_name)),
         JSON_ARRAY()
       )
INTO @workhub_user_name_pinyin_effective
FROM JSON_TABLE(
       @workhub_user_name_pinyin_mapping,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping
JOIN sys_user source_user ON source_user.user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
LEFT JOIN sys_user target_user
  ON target_user.user_name = mapping.new_user_name COLLATE utf8mb4_0900_ai_ci
  AND target_user.id <> source_user.id
WHERE target_user.id IS NULL;

UPDATE sys_login_log target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.user_name = mapping.new_user_name;

UPDATE sys_operation_log target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.operator_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.operator_user_name = mapping.new_user_name;

UPDATE pm_developer_resource target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
LEFT JOIN pm_developer_resource existing
  ON existing.user_name = mapping.new_user_name COLLATE utf8mb4_0900_ai_ci
  AND existing.id <> target.id
SET target.user_name = mapping.new_user_name
WHERE existing.id IS NULL;

UPDATE pm_project target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.owner_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.owner_user_name = mapping.new_user_name;

UPDATE pm_business_line_member target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.member_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
LEFT JOIN pm_business_line_member existing
  ON existing.business_line = target.business_line
  AND existing.member_user_name = mapping.new_user_name COLLATE utf8mb4_0900_ai_ci
  AND existing.id <> target.id
SET target.member_user_name = mapping.new_user_name
WHERE existing.id IS NULL;

UPDATE sys_notification target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.recipient_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.recipient_user_name = mapping.new_user_name;

UPDATE pay_operation_log target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.operator_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.operator_user_name = mapping.new_user_name;

UPDATE pm_work_item target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.creator_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.creator_user_name = mapping.new_user_name;

UPDATE pm_work_item target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.owner_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.owner_user_name = mapping.new_user_name;

UPDATE pm_work_item target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.follower_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.follower_user_name = mapping.new_user_name;

UPDATE pm_work_item_follow_up target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.operator_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.operator_user_name = mapping.new_user_name;

UPDATE pm_work_item_transition_log target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.operator_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.operator_user_name = mapping.new_user_name;

UPDATE pm_intake_record target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.development_owner_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.development_owner_user_name = mapping.new_user_name;

UPDATE pm_intake_history target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.operator_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.operator_user_name = mapping.new_user_name;

UPDATE pm_intake_todo target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.assignee_user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.assignee_user_name = mapping.new_user_name;

UPDATE pm_intake_development_analysis target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.created_by = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.created_by = mapping.new_user_name;

UPDATE pm_intake_development_analysis target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.updated_by = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.updated_by = mapping.new_user_name;

UPDATE pm_intake_clarification_analysis target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.created_by = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.created_by = mapping.new_user_name;

UPDATE pm_intake_clarification_analysis target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.updated_by = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.updated_by = mapping.new_user_name;

UPDATE sys_user target
JOIN JSON_TABLE(
       @workhub_user_name_pinyin_effective,
       '$[*]' COLUMNS (
         old_user_name VARCHAR(64) PATH '$.old',
         new_user_name VARCHAR(64) PATH '$.new'
       )
     ) mapping ON target.user_name = mapping.old_user_name COLLATE utf8mb4_0900_ai_ci
SET target.user_name = mapping.new_user_name,
    target.updated_at = CURRENT_TIMESTAMP;

SET @workhub_user_name_pinyin_effective = NULL;
SET @workhub_user_name_pinyin_mapping = NULL;
