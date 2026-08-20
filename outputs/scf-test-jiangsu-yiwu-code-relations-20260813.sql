-- 保费分期（BL000001）测试环境：江苏易吾编码及关联关系调整
-- 数据库：scf_self_order
-- 目标：
--   项目 XM000161 -> XXT-PRJ-YW-A（amp_project.id = 161）
--   产品 P000278  -> PRD-YW-A001（amp_product.id = 278）
--   渠道 CH025110512043826 -> HZF000133（crm_channel.id = 29）
--   项目、产品、渠道重新建立关系
--
-- 说明：
-- 1. 项目、产品编码在 2026-08-13 只读核查时已经是目标值，脚本兼容已改/未改两种状态。
-- 2. 系统当前主要通过 project_config.project_id + product_ids 维护项目产品关系。
-- 3. product_ids 默认按现有标准新项目配置 A/B/C 三个等级。
-- 4. amp_product.project_no 同步维护，作为旧逻辑兼容字段。
-- 5. 请先执行“执行前检查”，确认结果符合注释中的预期，再执行事务 DML。

USE scf_self_order;

SET @old_project = 'XM000161';
SET @new_project = 'XXT-PRJ-YW-A';
SET @old_product = 'P000278';
SET @new_product = 'PRD-YW-A001';
SET @old_channel = 'CH025110512043826';
SET @new_channel = 'HZF000133';
SET @operator = 'manual_code_migration';
SET @product_relations = JSON_ARRAY(
    JSON_OBJECT('productId', @new_product, 'level', 'C'),
    JSON_OBJECT('productId', @new_product, 'level', 'B'),
    JSON_OBJECT('productId', @new_product, 'level', 'A')
);

-- ============================================================
-- 一、执行前检查
-- 预期：项目 id=161、产品 id=278、渠道 id=29 各一条；新编码不存在重复占用。
-- 当前已知：项目和产品已是新编码；渠道仍是旧编码；项目产品配置尚不存在。
-- ============================================================

SELECT id, no, name, channel_codes, version, modify_time
FROM amp_project
WHERE id = 161 OR no IN (@old_project, @new_project)
ORDER BY id;

SELECT id, code, name, project_no, version, modify_time
FROM amp_product
WHERE id = 278 OR code IN (@old_product, @new_product)
ORDER BY id;

SELECT id, channel_code, user_code, auth_code, status, version, modify_time
FROM crm_channel
WHERE id = 29 OR channel_code IN (@old_channel, @new_channel)
ORDER BY id;

SELECT id, project_id, product_ids, version, is_delete
FROM project_config
WHERE is_delete = 0
  AND (
      project_id IN (@old_project, @new_project)
      OR product_ids LIKE CONCAT('%', @old_product, '%')
      OR product_ids LIKE CONCAT('%', @new_product, '%')
  )
ORDER BY id;

-- 三个新编码不能被其他主数据占用；以下三项预期均为 0。
SELECT
    (SELECT COUNT(*) FROM amp_project WHERE no = @new_project AND id <> 161) AS project_code_conflicts,
    (SELECT COUNT(*) FROM amp_product WHERE code = @new_product AND id <> 278) AS product_code_conflicts,
    (SELECT COUNT(*) FROM crm_channel WHERE channel_code = @new_channel AND id <> 29) AS channel_code_conflicts;

-- 渠道旧编码当前关联行数预期：crm_channel=1、emp=2、enterprise=1、individual=1。
SELECT
    (SELECT COUNT(*) FROM crm_channel WHERE channel_code = @old_channel) AS channel_rows,
    (SELECT COUNT(*) FROM crm_channel_emp WHERE channel_code = @old_channel) AS channel_emp_rows,
    (SELECT COUNT(*) FROM crm_channel_enterprise WHERE channel_code = @old_channel) AS channel_enterprise_rows,
    (SELECT COUNT(*) FROM crm_channel_individual WHERE channel_code = @old_channel) AS channel_individual_rows;

-- ============================================================
-- 二、事务 DML
-- 只有在上面的三个 conflict 均为 0 时才执行。
-- ============================================================

START TRANSACTION;

-- 1. 项目主数据及项目-渠道关系。
UPDATE amp_project
SET no = @new_project,
    channel_codes = @new_channel,
    modify_by = @operator,
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = 161
  AND no IN (@old_project, @new_project)
  AND NOT EXISTS (
      SELECT 1
      FROM (SELECT id FROM amp_project WHERE no = @new_project AND id <> 161) AS project_conflict
  )
  AND (
      no <> @new_project
      OR channel_codes IS NULL
      OR channel_codes <> @new_channel
  );

-- 2. 产品主数据；project_no 仅作旧逻辑兼容，主关联见 project_config。
UPDATE amp_product
SET code = @new_product,
    project_no = @new_project,
    modify_by = @operator,
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = 278
  AND code IN (@old_product, @new_product)
  AND NOT EXISTS (
      SELECT 1
      FROM (SELECT id FROM amp_product WHERE code = @new_product AND id <> 278) AS product_conflict
  )
  AND (
      code <> @new_product
      OR project_no IS NULL
      OR project_no <> @new_project
  );

-- 3. 项目产品主关联：先迁移可能存在的旧项目编码/旧产品编码配置。
UPDATE project_config
SET project_id = @new_project,
    product_ids = @product_relations,
    modify_by = @operator,
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE is_delete = 0
  AND project_id IN (@old_project, @new_project)
  AND (
      project_id <> @new_project
      OR product_ids IS NULL
      OR JSON_VALID(product_ids) = 0
      OR CAST(product_ids AS CHAR) <> CAST(@product_relations AS CHAR)
  );

-- 如果项目还没有配置，则建立 A/B/C 三等级的项目产品关系。
INSERT INTO project_config (
    project_id,
    product_ids,
    create_time,
    modify_time,
    create_by,
    modify_by,
    is_delete,
    is_cancel,
    version,
    remark
)
SELECT
    @new_project,
    @product_relations,
    NOW(),
    NOW(),
    @operator,
    @operator,
    0,
    0,
    1,
    '江苏易吾项目产品关系初始化'
WHERE NOT EXISTS (
    SELECT 1
    FROM project_config
    WHERE project_id = @new_project
      AND is_delete = 0
);

-- 4. 渠道主数据和渠道明细引用。
UPDATE crm_channel
SET channel_code = @new_channel,
    modify_by = @operator,
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = 29
  AND channel_code = @old_channel
  AND NOT EXISTS (
      SELECT 1
      FROM (SELECT id FROM crm_channel WHERE channel_code = @new_channel AND id <> 29) AS channel_conflict
  );

UPDATE crm_channel_emp
SET channel_code = @new_channel
WHERE channel_code = @old_channel;

UPDATE crm_channel_enterprise
SET channel_code = @new_channel
WHERE channel_code = @old_channel;

UPDATE crm_channel_individual
SET channel_code = @new_channel
WHERE channel_code = @old_channel;

-- 当前这两张业务表旧/新渠道均无数据；保留迁移语句防止执行前新增引用。
UPDATE application_premium_supplement
SET channel_code = @new_channel
WHERE channel_code = @old_channel;

UPDATE finance_application
SET channel_code = @new_channel
WHERE channel_code = @old_channel;

COMMIT;

-- ============================================================
-- 三、执行后核验
-- ============================================================

SELECT id, no, name, channel_codes, version, modify_by, modify_time
FROM amp_project
WHERE id = 161;

SELECT id, code, name, project_no, version, modify_by, modify_time
FROM amp_product
WHERE id = 278;

SELECT id, project_id, product_ids, version, is_delete
FROM project_config
WHERE project_id = @new_project
  AND is_delete = 0;

SELECT id, channel_code, user_code, auth_code, status, version, modify_by, modify_time
FROM crm_channel
WHERE id = 29;

SELECT
    (SELECT COUNT(*) FROM crm_channel_emp WHERE channel_code = @new_channel) AS channel_emp_rows,
    (SELECT COUNT(*) FROM crm_channel_enterprise WHERE channel_code = @new_channel) AS channel_enterprise_rows,
    (SELECT COUNT(*) FROM crm_channel_individual WHERE channel_code = @new_channel) AS channel_individual_rows,
    (SELECT COUNT(*) FROM crm_channel_emp WHERE channel_code = @old_channel) AS old_channel_emp_rows,
    (SELECT COUNT(*) FROM crm_channel_enterprise WHERE channel_code = @old_channel) AS old_channel_enterprise_rows,
    (SELECT COUNT(*) FROM crm_channel_individual WHERE channel_code = @old_channel) AS old_channel_individual_rows;

-- 全部为 0 才表示旧编码已清理完成。
SELECT
    (SELECT COUNT(*) FROM amp_project WHERE no = @old_project OR channel_codes = @old_channel) AS old_project_or_channel_rows,
    (SELECT COUNT(*) FROM amp_product WHERE code = @old_product OR project_no = @old_project) AS old_product_or_project_rows,
    (SELECT COUNT(*) FROM project_config WHERE project_id = @old_project OR product_ids LIKE CONCAT('%', @old_product, '%')) AS old_project_config_rows,
    (SELECT COUNT(*) FROM crm_channel WHERE channel_code = @old_channel) AS old_channel_rows;

-- ============================================================
-- 四、整体回滚 SQL（仅在确需恢复旧编码时单独执行，不与上面 DML 同批执行）
-- ============================================================
/*
START TRANSACTION;

DELETE FROM project_config
WHERE project_id = 'XXT-PRJ-YW-A'
  AND create_by = 'manual_code_migration'
  AND remark = '江苏易吾项目产品关系初始化';

UPDATE crm_channel_emp SET channel_code = 'CH025110512043826' WHERE channel_code = 'HZF000133';
UPDATE crm_channel_enterprise SET channel_code = 'CH025110512043826' WHERE channel_code = 'HZF000133';
UPDATE crm_channel_individual SET channel_code = 'CH025110512043826' WHERE channel_code = 'HZF000133';
UPDATE application_premium_supplement SET channel_code = 'CH025110512043826' WHERE channel_code = 'HZF000133';
UPDATE finance_application SET channel_code = 'CH025110512043826' WHERE channel_code = 'HZF000133';

UPDATE crm_channel
SET channel_code = 'CH025110512043826',
    modify_by = 'manual_code_migration_rollback',
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = 29 AND channel_code = 'HZF000133';

UPDATE amp_product
SET code = 'P000278',
    project_no = NULL,
    modify_by = 'manual_code_migration_rollback',
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = 278 AND code = 'PRD-YW-A001';

UPDATE amp_project
SET no = 'XM000161',
    channel_codes = 'CH025110512043826',
    modify_by = 'manual_code_migration_rollback',
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = 161 AND no = 'XXT-PRJ-YW-A';

COMMIT;
*/
