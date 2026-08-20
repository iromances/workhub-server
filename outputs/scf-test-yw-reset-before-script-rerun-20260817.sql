-- 保费分期测试环境：江苏易吾脚本重跑前基线重置
-- 业务线：BL000001 保费分期（GitLab group: scf）
-- 环境/分支：test / master（来源：2026-08-13 同组脚本记录的用户指定基线）
--
-- 当前目标编码：
--   项目：XXT-PRJ-YW-A（amp_project.id = 161）
--   产品：PRD-YW-A001（amp_product.id = 278）
--   渠道：HZF000133（crm_channel.id = 29）
--
-- 重置后的脚本执行前编码：
--   项目：XM000161
--   产品：P000278
--   渠道：CH025110512043826
--
-- 重要说明：
-- 1. 为了重新验证 2026-08-13 的“编码关系调整 + 配置复制 + 合同复制”脚本，
--    本脚本不会物理删除 id=161/278/29 三条基础记录，而是清理目标复制配置并恢复旧编码。
--    这样 XXT-PRJ-YW-A / PRD-YW-A001 / HZF000133 会从主数据中消失，同时保留重跑所需底稿。
-- 2. 不删除 scf_self_payment.router、channel_pay_config、JDPAY AGREEMENT_PAYMENT 费率；
--    这些是原配置复制脚本明确要求保留的目标专属支付基线。
-- 3. 不删除任何申请单、放款单、融资单、账单或支付流水。
--    只要目标项目/产品/渠道仍被业务单据引用，强制守卫就会报错并停止。
-- 4. 当前（2026-08-17）只读核查发现 5 个有效申请单、2 个有效融资单引用目标数据，
--    因此在这些测试业务数据通过正常流程清理前，本脚本会按设计拒绝执行。
-- 5. 必须使用“遇错停止”模式执行；任何 guard 报错后不得跳过并继续 COMMIT。

SET @execute_reset = 0; -- 人工复核完成后改成 1

SET @target_project = 'XXT-PRJ-YW-A';
SET @target_product = 'PRD-YW-A001';
SET @target_channel = 'HZF000133';

SET @project_id = 161;
SET @product_id = 278;
SET @channel_id = 29;

SET @old_project = 'XM000161';
SET @old_product = 'P000278';
SET @old_channel = 'CH025110512043826';
SET @operator = 'codex_test_baseline_reset';

/* ============================================================
 * A. 执行前核对：先看清楚，再把 @execute_reset 改为 1
 * ============================================================ */

SELECT id, no, name, channel_codes, status, is_delete, is_cancel
FROM scf_self_order.amp_project
WHERE id = @project_id OR no IN (@target_project, @old_project)
ORDER BY id;

SELECT id, code, name, project_no, status, is_delete, is_cancel
FROM scf_self_order.amp_product
WHERE id = @product_id OR code IN (@target_product, @old_product)
ORDER BY id;

SELECT id, channel_code, type, status, user_code, auth_code, is_delete, is_cancel
FROM scf_self_order.crm_channel
WHERE id = @channel_id OR channel_code IN (@target_channel, @old_channel)
ORDER BY id;

-- 当前阻塞业务单据。任何一行都表示不能重置主数据。
SELECT application_no, status, project_no, product_code, partner_code, create_time
FROM scf_self_order.amp_application
WHERE project_no = @target_project
   OR product_code = @target_product
   OR partner_code = @target_channel
ORDER BY id;

SELECT financing_code, application_code, status,
       project_code, product_code, channel_code, create_time
FROM scf_self_order.finance_application
WHERE project_code = @target_project
   OR product_code = @target_product
   OR channel_code = @target_channel
ORDER BY id;

-- 即将清理的配置数量。
SELECT
    (SELECT COUNT(*) FROM scf_self_order.project_config
      WHERE project_id = @target_project OR project_no = @target_project) AS project_config_rows,
    (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
      WHERE project_no = @target_project) AS project_attachment_rows,
    (SELECT COUNT(*) FROM scf_self_order.product_detail_group
      WHERE product_id = @product_id) AS product_group_rows,
    (SELECT COUNT(*) FROM scf_self_order.product_detail
      WHERE product_id = @product_id) AS product_detail_rows,
    (SELECT COUNT(*) FROM scf_self_order.org_config
      WHERE org_code = @target_channel) AS org_config_rows,
    (SELECT COUNT(*) FROM scf_self_order.contract_config
      WHERE project_code = @target_project) AS contract_rows,
    (SELECT COUNT(*)
       FROM scf_self_order.contract_config c
       JOIN scf_self_order.contract_config_signers s
         ON s.contract_config_id = c.id
      WHERE c.project_code = @target_project) AS contract_signer_rows,
    (SELECT COUNT(*) FROM scf_self_payment.business_config
      WHERE query_key = @target_channel
        AND query_type = 'COOPERATION'
        AND group_key = 'PaymentConfig'
        AND config_key = 'pay_channel') AS payment_business_rows,
    (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
      WHERE merchant_no = @target_channel
        AND channel_code = 'BCM'
        AND transaction_mode = 'SINGLE_REMIT') AS bcm_fee_rows,
    (SELECT COUNT(*) FROM scf_self_saps.business_config
      WHERE query_key IN (@target_channel, @target_project)) AS saps_business_rows;

/* ============================================================
 * B. 强制执行守卫
 * ============================================================ */

USE scf_self_order;

DROP TEMPORARY TABLE IF EXISTS `_yw_reset_guard`;
CREATE TEMPORARY TABLE `_yw_reset_guard` (
    check_name VARCHAR(120) NOT NULL PRIMARY KEY,
    passed TINYINT NOT NULL,
    CONSTRAINT `chk_yw_reset_guard` CHECK (passed = 1)
);

-- 必须人工把开关改为 1。
INSERT INTO `_yw_reset_guard` VALUES
('explicit_execute_switch', IF(@execute_reset = 1, 1, 0));

-- 三条主数据必须仍然是已确认的 id + 目标编码组合。
INSERT INTO `_yw_reset_guard` VALUES
('exact_target_master_rows',
 IF((SELECT COUNT(*) FROM amp_project
      WHERE id=@project_id AND no=@target_project)=1
    AND (SELECT COUNT(*) FROM amp_product
         WHERE id=@product_id AND code=@target_product)=1
    AND (SELECT COUNT(*) FROM crm_channel
         WHERE id=@channel_id AND channel_code=@target_channel)=1, 1, 0));

-- 旧编码不得被其他记录占用，否则恢复会制造重复编码。
INSERT INTO `_yw_reset_guard` VALUES
('old_codes_not_occupied',
 IF((SELECT COUNT(*) FROM amp_project
      WHERE no=@old_project AND id<>@project_id)=0
    AND (SELECT COUNT(*) FROM amp_product
         WHERE code=@old_product AND id<>@product_id)=0
    AND (SELECT COUNT(*) FROM crm_channel
         WHERE channel_code=@old_channel AND id<>@channel_id)=0, 1, 0));

-- 不允许把仍有业务单据引用的主数据重置。
-- 这里不只查有效数据，历史软删除记录也必须先由正常测试数据清理流程处理。
INSERT INTO `_yw_reset_guard` VALUES
('no_order_side_references',
 IF((SELECT COUNT(*) FROM amp_application
      WHERE project_no=@target_project
         OR product_code=@target_product
         OR partner_code=@target_channel)=0
    AND (SELECT COUNT(*) FROM finance_application
         WHERE project_code=@target_project
            OR product_code=@target_product
            OR channel_code=@target_channel)=0
    AND (SELECT COUNT(*) FROM application_premium_supplement
         WHERE channel_code=@target_channel)=0, 1, 0));

/* ============================================================
 * C. 事务重置
 * ============================================================ */

START TRANSACTION;

-- C01 合同签署方必须先于合同配置删除。
DELETE s
FROM scf_self_order.contract_config_signers s
JOIN scf_self_order.contract_config c
  ON c.id = s.contract_config_id
WHERE c.project_code = @target_project;

DELETE FROM scf_self_order.contract_config
WHERE project_code = @target_project;

-- C02 产品复制明细：先明细、后分组。
DELETE FROM scf_self_order.product_detail
WHERE product_id = @product_id;

DELETE FROM scf_self_order.product_detail_group
WHERE product_id = @product_id;

-- C03 项目、渠道运行配置。
DELETE FROM scf_self_order.project_attachment_config
WHERE project_no = @target_project;

DELETE FROM scf_self_order.org_config
WHERE org_code = @target_channel;

DELETE FROM scf_self_order.project_config
WHERE project_id = @target_project
   OR project_no = @target_project;

-- C04 仅删除配置复制脚本新增/收敛的支付项；保留 router、channel_pay_config、JDPAY 协议支付费率。
DELETE FROM scf_self_payment.business_config
WHERE query_key = @target_channel
  AND query_type = 'COOPERATION'
  AND group_key = 'PaymentConfig'
  AND config_key = 'pay_channel';

DELETE FROM scf_self_payment.channel_fee_config
WHERE merchant_no = @target_channel
  AND channel_code = 'BCM'
  AND transaction_mode = 'SINGLE_REMIT';

-- C05 SAPS 配置复制项；statement_scene 原脚本要求为 0，这里一并清掉异常残留。
DELETE FROM scf_self_saps.business_config
WHERE query_key IN (@target_channel, @target_project);

DELETE FROM scf_self_saps.statement_scene_rule
WHERE capital_project_code = @target_project;

DELETE FROM scf_self_saps.statement_scene
WHERE capital_project_code = @target_project;

-- C06 渠道子表引用恢复旧编码。
UPDATE scf_self_order.crm_channel_emp
SET channel_code = @old_channel
WHERE channel_code = @target_channel;

UPDATE scf_self_order.crm_channel_enterprise
SET channel_code = @old_channel
WHERE channel_code = @target_channel;

UPDATE scf_self_order.crm_channel_individual
SET channel_code = @old_channel
WHERE channel_code = @target_channel;

-- C07 恢复主数据到编码关系调整脚本执行前状态。
UPDATE scf_self_order.crm_channel
SET channel_code = @old_channel,
    modify_by = @operator,
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = @channel_id
  AND channel_code = @target_channel;

UPDATE scf_self_order.amp_product
SET code = @old_product,
    project_no = NULL,
    modify_by = @operator,
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = @product_id
  AND code = @target_product;

UPDATE scf_self_order.amp_project
SET no = @old_project,
    channel_codes = @old_channel,
    modify_by = @operator,
    modify_time = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = @project_id
  AND no = @target_project;

-- C08 COMMIT 前强制验收：目标编码与目标复制配置必须全部消失。
INSERT INTO `_yw_reset_guard` VALUES
('post_target_master_codes_absent',
 IF((SELECT COUNT(*) FROM amp_project WHERE no=@target_project)=0
    AND (SELECT COUNT(*) FROM amp_product WHERE code=@target_product)=0
    AND (SELECT COUNT(*) FROM crm_channel WHERE channel_code=@target_channel)=0, 1, 0));

INSERT INTO `_yw_reset_guard` VALUES
('post_old_master_codes_restored',
 IF((SELECT COUNT(*) FROM amp_project
      WHERE id=@project_id AND no=@old_project AND channel_codes=@old_channel)=1
    AND (SELECT COUNT(*) FROM amp_product
         WHERE id=@product_id AND code=@old_product AND project_no IS NULL)=1
    AND (SELECT COUNT(*) FROM crm_channel
         WHERE id=@channel_id AND channel_code=@old_channel)=1, 1, 0));

INSERT INTO `_yw_reset_guard` VALUES
('post_order_configs_empty',
 IF((SELECT COUNT(*) FROM contract_config WHERE project_code=@target_project)=0
    AND (SELECT COUNT(*) FROM product_detail WHERE product_id=@product_id)=0
    AND (SELECT COUNT(*) FROM product_detail_group WHERE product_id=@product_id)=0
    AND (SELECT COUNT(*) FROM project_attachment_config WHERE project_no=@target_project)=0
    AND (SELECT COUNT(*) FROM org_config WHERE org_code=@target_channel)=0
    AND (SELECT COUNT(*) FROM project_config
         WHERE project_id=@target_project OR project_no=@target_project)=0, 1, 0));

INSERT INTO `_yw_reset_guard` VALUES
('post_payment_and_saps_copy_configs_empty',
 IF((SELECT COUNT(*) FROM scf_self_payment.business_config
      WHERE query_key=@target_channel AND query_type='COOPERATION'
        AND group_key='PaymentConfig' AND config_key='pay_channel')=0
    AND (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
         WHERE merchant_no=@target_channel AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT')=0
    AND (SELECT COUNT(*) FROM scf_self_saps.business_config
         WHERE query_key IN (@target_channel,@target_project))=0
    AND (SELECT COUNT(*) FROM scf_self_saps.statement_scene_rule
         WHERE capital_project_code=@target_project)=0
    AND (SELECT COUNT(*) FROM scf_self_saps.statement_scene
         WHERE capital_project_code=@target_project)=0, 1, 0));

COMMIT;

DROP TEMPORARY TABLE IF EXISTS `_yw_reset_guard`;

/* ============================================================
 * D. 执行后核验
 * ============================================================ */

SELECT id, no, name, channel_codes, modify_by, modify_time
FROM scf_self_order.amp_project
WHERE id = @project_id;

SELECT id, code, name, project_no, modify_by, modify_time
FROM scf_self_order.amp_product
WHERE id = @product_id;

SELECT id, channel_code, type, status, user_code, auth_code, modify_by, modify_time
FROM scf_self_order.crm_channel
WHERE id = @channel_id;

SELECT
    (SELECT COUNT(*) FROM scf_self_order.amp_project WHERE no=@target_project) AS target_project_should_be_0,
    (SELECT COUNT(*) FROM scf_self_order.amp_product WHERE code=@target_product) AS target_product_should_be_0,
    (SELECT COUNT(*) FROM scf_self_order.crm_channel WHERE channel_code=@target_channel) AS target_channel_should_be_0,
    (SELECT COUNT(*) FROM scf_self_order.contract_config WHERE project_code=@target_project) AS target_contract_should_be_0,
    (SELECT COUNT(*) FROM scf_self_order.product_detail WHERE product_id=@product_id) AS target_product_detail_should_be_0,
    (SELECT COUNT(*) FROM scf_self_order.product_detail_group WHERE product_id=@product_id) AS target_product_group_should_be_0,
    (SELECT COUNT(*) FROM scf_self_order.project_config
      WHERE project_id=@target_project OR project_no=@target_project) AS target_project_config_should_be_0;

