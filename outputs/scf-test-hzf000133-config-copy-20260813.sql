-- 保费分期测试环境：江苏易吾新自营渠道配置补齐
-- 业务线：BL000001 保费分期（GitLab group: scf）
-- 环境/分支：测试环境 / master（用户指定）
-- 源：HZF000144 / XXT-PRJ-ZY-A / PRD-ZY-A001
-- 目标：HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001
--
-- 重要边界：
-- 1. 复制山西自营的配置结构和运行规则，但不覆盖目标唯一编码、项目额度、地域、支付路由和费率。
-- 2. 江苏易吾产品已确认只支持 9 期；复制产品明细后强制把期数相关配置收敛为 9 期。
-- 3. 本脚本不复制支付密钥、证书等渠道专属敏感数据；仅复制运行时强制查询的 BCM 单笔代付手续费规则，并把合作方编码替换为目标渠道。
-- 4. 合同配置见同目录 scf-test-hzf000133-contract-config-20260813.sql。
-- 5. 明确不复制 SAPS statement_scene/statement_scene_rule：master 代码尚未注册江苏易吾项目扩展点；误配会中断缴费单通知或客户账单事务。
-- 6. DML 本身使用 INSERT ... SELECT ... NOT EXISTS；但执行守卫会要求目标仍为已核对的空白基线，防止并发/误重跑。
-- 7. 项目附件配置属于运行配置：页面附件回显和放款后保单补传都会按项目号读取，随项目复制山西现有 44 条。
-- 8. org_cooperation 不复制：新自营链路使用 crm_channel + amp_project.channel_codes；目标项目 cooperation_id 为空是当前模型设计，
--    旧合作方表还包含主体、证件、银行卡等渠道专属资料，不能从山西渠道冒名复制。
-- 9. 必须在“遇错停止”模式下执行；任一 guard 报错立即 ROLLBACK，禁止跳过错误继续 COMMIT。

SET @source_channel = 'HZF000144';
SET @target_channel = 'HZF000133';
SET @source_project = 'XXT-PRJ-ZY-A';
SET @target_project = 'XXT-PRJ-YW-A';
SET @source_product = 'PRD-ZY-A001';
SET @target_product = 'PRD-YW-A001';

-- 为临时守卫表显式选定 schema，避免客户端未预选数据库时报 No database selected。
USE `scf_self_order`;

-- 执行前可视化核对。
SELECT code, id, status, repayment_type, grace_period_days
FROM `scf_self_order`.amp_product
WHERE code IN (@source_product, @target_product)
  AND is_delete = 0
  AND is_cancel = 0
ORDER BY code, id;

-- 强制执行守卫（MySQL 8.4 CHECK 约束）。
-- 下列任意一条不满足都会直接报错，不得手工忽略错误继续执行。
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_config_guard`;
CREATE TEMPORARY TABLE `scf_self_order`.`_hzf000133_config_guard` (
    check_name VARCHAR(100) NOT NULL PRIMARY KEY,
    passed TINYINT NOT NULL,
    CONSTRAINT `chk_hzf000133_config_guard` CHECK (passed = 1)
);

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('source_and_target_product_unique',
 IF((SELECT COUNT(*) FROM `scf_self_order`.amp_product
     WHERE code IN (@source_product,@target_product) AND is_delete=0 AND is_cancel=0)=2
    AND (SELECT COUNT(*) FROM `scf_self_order`.amp_product
         WHERE code=@source_product AND id=265 AND status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.amp_product
         WHERE code=@target_product AND id=278 AND status=1 AND is_delete=0 AND is_cancel=0)=1,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('source_config_counts_15_89_44_8_1_1_8',
 IF((SELECT COUNT(*) FROM `scf_self_order`.product_detail_group
     WHERE product_id=265 AND is_delete=0 AND is_cancel=0)=15
    AND (SELECT COUNT(*) FROM `scf_self_order`.product_detail
         WHERE product_id=265 AND is_delete=0 AND is_cancel=0)=89
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@source_project AND is_delete=0 AND is_cancel=0)=44
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_config
         WHERE org_code=@source_channel AND org_type='CHANNEL' AND org_group_key='DEFAULT'
           AND is_delete=0 AND is_cancel=0)=8
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key=@source_channel AND query_type='COOPERATION'
           AND group_key='PaymentConfig' AND config_key='pay_channel'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@source_channel AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT'
           AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
           AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN (@source_channel,@source_project) AND is_delete=0 AND is_cancel=0)=8,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('target_config_still_empty',
 IF((SELECT COUNT(*) FROM `scf_self_order`.product_detail_group WHERE product_id=278)=0
    AND (SELECT COUNT(*) FROM `scf_self_order`.product_detail WHERE product_id=278)=0
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@target_project)=0
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_config WHERE org_code=@target_channel)=0
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config WHERE query_key=@target_channel)=0
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@target_channel AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT')=0
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN (@target_channel,@target_project))=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('source_config_natural_keys_unique',
 IF((SELECT COUNT(*) FROM (
       SELECT label_code
       FROM `scf_self_order`.product_detail_group
       WHERE product_id=265 AND is_delete=0 AND is_cancel=0
       GROUP BY label_code HAVING COUNT(*)>1
     ) duplicated_group)=0
    AND (SELECT COUNT(*) FROM (
       SELECT g.label_code,d.code
       FROM `scf_self_order`.product_detail d
       JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=265 AND g.product_id=265
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0
       GROUP BY g.label_code,d.code HAVING COUNT(*)>1
     ) duplicated_detail)=0
    AND (SELECT COUNT(*) FROM (
       SELECT org_type,org_group_key,org_detail_key
       FROM `scf_self_order`.org_config
       WHERE org_code=@source_channel AND org_type='CHANNEL' AND org_group_key='DEFAULT'
         AND is_delete=0 AND is_cancel=0
       GROUP BY org_type,org_group_key,org_detail_key HAVING COUNT(*)>1
     ) duplicated_org)=0
    AND (SELECT COUNT(*) FROM (
       SELECT query_type,group_key,config_key
       FROM `scf_self_payment`.business_config
       WHERE query_key=@source_channel AND query_type='COOPERATION'
         AND group_key='PaymentConfig' AND config_key='pay_channel'
         AND is_delete=0 AND is_cancel=0
       GROUP BY query_type,group_key,config_key HAVING COUNT(*)>1
     ) duplicated_payment_business)=0
    AND (SELECT COUNT(*) FROM (
       SELECT query_key,query_type,group_key,config_key
       FROM `scf_self_saps`.business_config
       WHERE query_key IN (@source_channel,@source_project) AND is_delete=0 AND is_cancel=0
       GROUP BY query_key,query_type,group_key,config_key HAVING COUNT(*)>1
     ) duplicated_saps_business)=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('target_master_data_ready',
 IF((SELECT COUNT(*) FROM `scf_self_order`.amp_project
     WHERE no=@target_project AND channel_codes=@target_channel AND status=1
       AND effect_date<=NOW()
       AND margin_ratio=10 AND current_margin_amount>0 AND total_limit>0
       AND supplier_id=6 AND technical_service_provider_id=2
       AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.crm_channel
         WHERE channel_code=@target_channel AND type=2 AND status=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_config
         WHERE project_id=@target_project AND insurance_type='1'
           AND JSON_LENGTH(product_ids)=3
           AND JSON_CONTAINS(product_ids,JSON_OBJECT('productId',@target_product,'level','A'))
           AND JSON_CONTAINS(product_ids,JSON_OBJECT('productId',@target_product,'level','B'))
           AND JSON_CONTAINS(product_ids,JSON_OBJECT('productId',@target_product,'level','C'))
           AND single_limit_min=1 AND single_limit_max=500000
           AND pre_risk_model='PRD-JBR-001' AND apply_risk_model='PRD-JBR-001'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_config
         WHERE project_id=@target_project AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_funding_party
         WHERE org_code='ZJF000015' AND status=1
           AND JSON_CONTAINS(periods,'"9"')
           AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
           AND supported_insurance_types='1'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_funding_party
         WHERE org_code='ZJF000011' AND status=1
           AND JSON_CONTAINS(periods,'"9"')
           AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
           AND is_delete=0 AND is_cancel=0)=1,1,0));

-- 支付和出账公共运行项必须在第一条 DML 前已就绪；本脚本不会复制目标专属密钥或路由。
INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('target_payment_and_saps_runtime_ready',
 IF((SELECT COUNT(*) FROM `scf_self_payment`.router
     WHERE cooperation_code=@target_channel AND project_no=@target_project
       AND product_code=@target_product AND payment_product='DS' AND channel='JDPAY'
       AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_pay_config
         WHERE cooperation_code=@target_channel AND channel_code='JDPAY' AND status=1
           AND channel_url IS NOT NULL AND channel_url<>''
           AND member_id IS NOT NULL AND member_id<>''
           AND terminal_id IS NOT NULL AND terminal_id<>''
           AND resv_one IS NOT NULL AND resv_one<>''
           AND notify_url IS NOT NULL AND notify_url<>''
           AND pay_config_code IS NOT NULL AND pay_config_code<>''
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_pay_config tgt
         JOIN `scf_self_payment`.channel_pay_config src
           ON src.cooperation_code=@source_channel AND src.channel_code='JDPAY' AND src.status=1
          AND src.is_delete=0 AND src.is_cancel=0
         WHERE tgt.cooperation_code=@target_channel AND tgt.channel_code='JDPAY' AND tgt.status=1
           AND tgt.channel_url=src.channel_url AND tgt.member_id=src.member_id
           AND tgt.terminal_id=src.terminal_id AND tgt.resv_one=src.resv_one
           AND tgt.notify_url=src.notify_url
           AND tgt.is_delete=0 AND tgt.is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@target_channel AND channel_code='JDPAY'
           AND transaction_mode='AGREEMENT_PAYMENT' AND channel_fee_rate=0.002
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
           AND config_key='bcmMockFlag' AND config_value='true' AND config_status=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
           AND config_key='bcmMockUrl' AND config_value IS NOT NULL AND config_value<>''
           AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.payment_slip_subject
         WHERE product_code='DEF' AND subject_no IN ('201','10201','10203','103')
           AND is_delete=0 AND is_cancel=0)=4
    AND (SELECT COUNT(*) FROM `scf_self_saps`.bill_subject
         WHERE product_code='DEF' AND subject_no IN ('101','10102','10201','10202','103')
           AND is_delete=0 AND is_cancel=0)=5
    AND (SELECT COUNT(DISTINCT subject_no) FROM `scf_self_saps`.customer_bill_subject
         WHERE product_code='DEF' AND subject_no IN ('101','10102')
           AND is_delete=0 AND is_cancel=0)=2,1,0));

START TRANSACTION;

/* ============================================================
 * S01 项目控制补齐：只补标识完整性和保证金配置，不覆盖已确认的商业险/额度/地域/产品关系。
 * ============================================================ */
USE `scf_self_order`;

UPDATE project_config
SET project_no = @target_project,
    modify_time = NOW(),
    modify_by = 'codex_config_copy'
WHERE project_id = @target_project
  AND is_delete = 0
  AND is_cancel = 0
  AND project_no IS NULL;

UPDATE project_config
SET project_detail_key = 'MarginRatio',
    value = '0.1',
    modify_time = NOW(),
    modify_by = 'codex_config_copy'
WHERE project_id = @target_project
  AND is_delete = 0
  AND is_cancel = 0
  AND project_detail_key IS NULL;

/* ============================================================
 * S01-1 项目附件配置：复制山西项目 44 条运行配置。
 * 源表存在语义相同的历史重复记录；这里按源记录完整复制，保证页面回显、合同附件和保单补传行为一致。
 * ============================================================ */
INSERT INTO project_attachment_config (
    project_no, must_exist, type, cn_name, en_name, stage, class_name, default_url,
    create_by, create_time, suffix, modify_by, modify_time,
    is_delete, delete_by, delete_time, is_cancel, version, remark,
    can_supplement, value_type
)
SELECT
    @target_project, src.must_exist, src.type, src.cn_name, src.en_name, src.stage,
    src.class_name, src.default_url,
    'codex_config_copy', NOW(), src.suffix, NULL, NULL,
    src.is_delete, NULL, NULL, src.is_cancel, COALESCE(src.version, 0), src.remark,
    src.can_supplement, src.value_type
FROM project_attachment_config src
WHERE src.project_no=@source_project
  AND src.is_delete=0
  AND src.is_cancel=0
  AND NOT EXISTS (
      SELECT 1 FROM project_attachment_config tgt
      WHERE tgt.project_no=@target_project
  );

/* ============================================================
 * S02 产品主表：复制非费率运行属性。
 * 保留目标 commercial_insurance_rate/non_commercial_insurance_rate、首付比例和罚息率。
 * ============================================================ */
UPDATE amp_product tgt
JOIN amp_product src
  ON src.code = @source_product
 AND src.is_delete = 0
 AND src.is_cancel = 0
SET tgt.repayment_type = src.repayment_type,
    tgt.grace_period_days = src.grace_period_days,
    tgt.project_no = @target_project,
    tgt.modify_time = NOW(),
    tgt.modify_by = 'codex_config_copy'
WHERE tgt.code = @target_product
  AND tgt.is_delete = 0
  AND tgt.is_cancel = 0;

SELECT id INTO @source_product_id
FROM amp_product
WHERE code = @source_product AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC LIMIT 1;

SELECT id INTO @target_product_id
FROM amp_product
WHERE code = @target_product AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC LIMIT 1;

/* ============================================================
 * S03 产品配置组：复制山西有效的 15 个组。
 * ============================================================ */
INSERT INTO product_detail_group (
    product_id, label_code, label_name, order_index,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_product_id, src.label_code, src.label_name, src.order_index,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM product_detail_group src
WHERE src.product_id = @source_product_id
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM product_detail_group tgt
      WHERE tgt.product_id = @target_product_id
        AND tgt.label_code = src.label_code
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

/* ============================================================
 * S04 产品配置明细：复制山西有效的 89 条明细。
 * ============================================================ */
INSERT INTO product_detail (
    product_id, detail_group_id, code, name, default_value, data_type, order_index,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_product_id,
    tgt_group.id,
    src_detail.code,
    src_detail.name,
    src_detail.default_value,
    src_detail.data_type,
    src_detail.order_index,
    'codex_config_copy',
    NOW(),
    0,
    0,
    COALESCE(src_detail.version, 0),
    src_detail.remark
FROM product_detail src_detail
JOIN product_detail_group src_group
  ON src_group.id = src_detail.detail_group_id
 AND src_group.product_id = @source_product_id
 AND src_group.is_delete = 0
 AND src_group.is_cancel = 0
JOIN product_detail_group tgt_group
 ON tgt_group.product_id = @target_product_id
 AND tgt_group.label_code = src_group.label_code
 AND tgt_group.is_delete = 0
 AND tgt_group.is_cancel = 0
WHERE src_detail.product_id = @source_product_id
  AND src_detail.is_delete = 0
  AND src_detail.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM product_detail exists_detail
      WHERE exists_detail.product_id = @target_product_id
        AND exists_detail.detail_group_id = tgt_group.id
        AND exists_detail.code = src_detail.code
        AND exists_detail.is_delete = 0
        AND exists_detail.is_cancel = 0
  );

/* ============================================================
 * S05 目标产品只支持 9 期：覆盖复制进来的山西 3/6/9 期明细。
 * 费率仍使用 amp_product 中江苏易吾现有 9 期费率：渠道 3.8%、运营 3.2%。
 * ============================================================ */
UPDATE product_detail d
JOIN product_detail_group g ON g.id = d.detail_group_id
SET d.default_value = CASE d.code
        WHEN 'period' THEN '9'
        WHEN 'InitialPaymentRatio' THEN '{"9":0.1}'
        WHEN 'customerFeeRateMax' THEN '{"9":0.36}'
        WHEN 'customerFeeRateMin' THEN '{"9":0.001}'
        WHEN 'fpFeeRate' THEN '{"9":0.04}'
        ELSE d.default_value
    END,
    d.modify_time = NOW(),
    d.modify_by = 'codex_config_copy'
WHERE d.product_id = @target_product_id
  AND g.product_id = @target_product_id
  AND g.label_code = 'BASE_CONFIG'
  AND g.is_delete = 0
  AND g.is_cancel = 0
  AND d.code IN ('period','InitialPaymentRatio','customerFeeRateMax','customerFeeRateMin','fpFeeRate')
  AND d.is_delete = 0
  AND d.is_cancel = 0;

-- 山西产品历史值为 BAOFUPAY，但江苏易吾支付路由已配置为 JDPAY；保持同一支付渠道，避免绑卡与收银台路由不一致。
UPDATE product_detail d
JOIN product_detail_group g ON g.id = d.detail_group_id
SET d.default_value = 'JDPAY',
    d.modify_time = NOW(),
    d.modify_by = 'codex_config_copy'
WHERE d.product_id = @target_product_id
  AND g.product_id = @target_product_id
  AND g.label_code = 'PAY_CONFIG'
  AND g.is_delete = 0
  AND g.is_cancel = 0
  AND d.code = 'payment_support_channel'
  AND d.is_delete = 0
  AND d.is_cancel = 0;

/* ============================================================
 * S06 渠道组织配置：复制山西 DEFAULT 组全部 8 项。
 * ============================================================ */
INSERT INTO org_config (
    org_code, org_type, org_group_key, org_detail_key, value, default_value,
    udf_1, udf_2, udf_3, udf_4, udf_5,
    udf_11, udf_12, udf_13, udf_14, udf_15,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_channel, src.org_type, src.org_group_key, src.org_detail_key,
    src.value, src.default_value,
    src.udf_1, src.udf_2, src.udf_3, src.udf_4, src.udf_5,
    src.udf_11, src.udf_12, src.udf_13, src.udf_14, src.udf_15,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM org_config src
WHERE src.org_code = @source_channel
  AND src.org_type = 'CHANNEL'
  AND src.org_group_key = 'DEFAULT'
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM org_config tgt
      WHERE tgt.org_code = @target_channel
        AND tgt.org_type = src.org_type
        AND tgt.org_group_key = src.org_group_key
        AND tgt.org_detail_key = src.org_detail_key
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

/* ============================================================
 * S07 支付业务配置：收银台支付渠道使用 JDPAY；放款固定走 BCM，补齐 SINGLE_REMIT 手续费。
 * 不覆盖已存在的 router、channel_pay_config 和 JDPAY 协议支付费率。
 * ============================================================ */
USE `scf_self_payment`;

INSERT INTO business_config (
    query_key, query_type, group_key, group_desc,
    config_key, config_value, config_value_desc, config_status,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_channel, 'COOPERATION', src.group_key, src.group_desc,
    src.config_key, 'JDPAY', src.config_value_desc, src.config_status,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM business_config src
WHERE src.query_key = @source_channel
  AND src.query_type = 'COOPERATION'
  AND src.group_key = 'PaymentConfig'
  AND src.config_key = 'pay_channel'
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM business_config tgt
      WHERE tgt.query_key = @target_channel
        AND tgt.query_type = 'COOPERATION'
        AND tgt.group_key = src.group_key
        AND tgt.config_key = src.config_key
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

-- 即使数据库中存在人工预置的同名有效项，也统一收敛到目标实际支付通道。
UPDATE business_config
SET config_value = 'JDPAY',
    config_status = 1,
    modify_time = NOW(),
    modify_by = 'codex_config_copy'
WHERE query_key = @target_channel
  AND query_type = 'COOPERATION'
  AND group_key = 'PaymentConfig'
  AND config_key = 'pay_channel'
  AND is_delete = 0
  AND is_cancel = 0;

-- scf-payment 的代付手续费计算会强制按 merchant_no + BCM + SINGLE_REMIT 查询；缺失会直接抛 CONFIG_NOT_EXIST。
-- master 的 SAPS PaymentTask 已显式指定 BCM，因此无需复制山西的 DF router 或 mer_channel_config。
INSERT INTO channel_fee_config (
    merchant_no, channle_id, channel_code, transaction_mode,
    biz_type, card_type, bank_rule, channel_fee_rule,
    start_range, end_range, channel_fee, channel_fee_rate,
    platform_fee, platform_fee_rate, platform_fee_enable,
    min_fee_amount, max_fee_amount, calculate_rule, main_account_belong, fee_desc,
    create_by, create_time, modify_by, modify_time, delete_by, delete_time,
    is_delete, is_cancel, version, remark
)
SELECT
    @target_channel, src.channle_id, src.channel_code, src.transaction_mode,
    src.biz_type, src.card_type, src.bank_rule, src.channel_fee_rule,
    src.start_range, src.end_range, src.channel_fee, src.channel_fee_rate,
    src.platform_fee, src.platform_fee_rate, src.platform_fee_enable,
    src.min_fee_amount, src.max_fee_amount, src.calculate_rule, src.main_account_belong, src.fee_desc,
    'codex_config_copy', NOW(), NULL, NULL, NULL, NULL,
    0, 0, COALESCE(src.version, 0), src.remark
FROM channel_fee_config src
WHERE src.merchant_no = @source_channel
  AND src.channel_code = 'BCM'
  AND src.transaction_mode = 'SINGLE_REMIT'
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM channel_fee_config tgt
      WHERE tgt.merchant_no = @target_channel
        AND tgt.channel_code = 'BCM'
        AND tgt.transaction_mode = 'SINGLE_REMIT'
  );

/* ============================================================
 * S08 SAPS 业务配置：渠道维度 6 项 + 项目维度 2 项。
 * 测试环境山西银行账户四项本身均为 test，复制后仍为 test。
 * ============================================================ */
USE `scf_self_saps`;

INSERT INTO business_config (
    query_key, query_type, group_key, group_desc,
    config_key, config_value, config_value_desc, config_status,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    CASE
        WHEN src.query_key = @source_channel THEN @target_channel
        WHEN src.query_key = @source_project THEN @target_project
    END,
    src.query_type, src.group_key, src.group_desc,
    src.config_key, src.config_value, src.config_value_desc, src.config_status,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM business_config src
WHERE src.query_key IN (@source_channel, @source_project)
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM business_config tgt
      WHERE tgt.query_key = CASE
              WHEN src.query_key = @source_channel THEN @target_channel
              WHEN src.query_key = @source_project THEN @target_project
            END
        AND tgt.group_key = src.group_key
        AND tgt.config_key = src.config_key
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

-- 本脚本到此只完成进件、缴费和出客户账单所需的基础配置。
-- statement_scene/statement_scene_rule 必须保持 0 条，待 scf-saps 为 XXT-PRJ-YW-A 增加扩展点并部署后另行配置。

-- COMMIT 前强制语义验收。任一项报错时立即手工 ROLLBACK。
INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_counts_15_89_44_8_1_1_8',
 IF((SELECT COUNT(*) FROM `scf_self_order`.product_detail_group
     WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=15
    AND (SELECT COUNT(*) FROM `scf_self_order`.product_detail
         WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=89
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@target_project AND is_delete=0 AND is_cancel=0)=44
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_config
         WHERE org_code=@target_channel AND org_type='CHANNEL' AND org_group_key='DEFAULT'
           AND is_delete=0 AND is_cancel=0)=8
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key=@target_channel AND query_type='COOPERATION'
           AND group_key='PaymentConfig' AND config_key='pay_channel'
           AND config_value='JDPAY' AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@target_channel AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN (@target_channel,@target_project) AND config_status=1
           AND is_delete=0 AND is_cancel=0)=8,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_product_and_project_values',
 IF((SELECT COUNT(*) FROM `scf_self_order`.amp_product
     WHERE id=278 AND code=@target_product AND project_no=@target_project
       AND repayment_type='4' AND grace_period_days=0 AND status=1
       AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_config
         WHERE project_id=@target_project AND project_no=@target_project
           AND project_detail_key='MarginRatio' AND CAST(value AS DECIMAL(10,4))=0.1
           AND insurance_type='1' AND is_delete=0 AND is_cancel=0)=1,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_runtime_product_values',
 IF((SELECT COUNT(*)
     FROM `scf_self_order`.product_detail d
     JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
     WHERE d.product_id=278 AND g.product_id=278
       AND g.label_code='BASE_CONFIG' AND d.code='period' AND d.default_value='9'
       AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='BASE_CONFIG' AND d.code='InitialPaymentRatio'
           AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.1'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='BASE_CONFIG' AND d.code='plateformFeeRate' AND d.default_value='0.005'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='BASE_CONFIG' AND d.code='repaymentPlanCalculation' AND d.default_value='ZY_Customer'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='PAY_CONFIG' AND d.code='payment_support_channel' AND d.default_value='JDPAY'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1,1,0));

-- 不只核对数量：除 6 个目标专属覆盖项外，83 条产品明细必须与山西相同；组织和 SAPS 的 8 项业务值必须逐项相同。
INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_copied_business_values_equal_source',
 IF((SELECT COUNT(*)
     FROM `scf_self_order`.product_detail sd
     JOIN `scf_self_order`.product_detail_group sg
       ON sg.id=sd.detail_group_id AND sg.product_id=265
      AND sg.is_delete=0 AND sg.is_cancel=0
     JOIN `scf_self_order`.product_detail_group tg
       ON tg.product_id=278 AND tg.label_code=sg.label_code
      AND tg.is_delete=0 AND tg.is_cancel=0
     JOIN `scf_self_order`.product_detail td
       ON td.product_id=278 AND td.detail_group_id=tg.id AND td.code=sd.code
      AND td.is_delete=0 AND td.is_cancel=0
     WHERE sd.product_id=265 AND sd.is_delete=0 AND sd.is_cancel=0
       AND NOT ((sg.label_code='BASE_CONFIG'
                 AND sd.code IN ('period','InitialPaymentRatio','customerFeeRateMax','customerFeeRateMin','fpFeeRate'))
                OR (sg.label_code='PAY_CONFIG' AND sd.code='payment_support_channel'))
       AND tg.label_name <=> sg.label_name
       AND tg.order_index <=> sg.order_index
       AND td.name <=> sd.name
       AND td.default_value <=> sd.default_value
       AND td.data_type <=> sd.data_type
       AND td.order_index <=> sd.order_index)=83
    AND (SELECT COALESCE(SUM(CRC32(CONCAT_WS('#',
              COALESCE(must_exist,'<NULL>'),COALESCE(type,'<NULL>'),COALESCE(cn_name,'<NULL>'),
              COALESCE(en_name,'<NULL>'),COALESCE(stage,'<NULL>'),COALESCE(class_name,'<NULL>'),
              COALESCE(default_url,'<NULL>'),COALESCE(suffix,'<NULL>'),COALESCE(is_delete,'<NULL>'),
              COALESCE(is_cancel,'<NULL>'),COALESCE(version,'<NULL>'),COALESCE(remark,'<NULL>'),
              COALESCE(can_supplement,'<NULL>'),COALESCE(value_type,'<NULL>')))),0)
         FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@source_project AND is_delete=0 AND is_cancel=0)
        =
        (SELECT COALESCE(SUM(CRC32(CONCAT_WS('#',
              COALESCE(must_exist,'<NULL>'),COALESCE(type,'<NULL>'),COALESCE(cn_name,'<NULL>'),
              COALESCE(en_name,'<NULL>'),COALESCE(stage,'<NULL>'),COALESCE(class_name,'<NULL>'),
              COALESCE(default_url,'<NULL>'),COALESCE(suffix,'<NULL>'),COALESCE(is_delete,'<NULL>'),
              COALESCE(is_cancel,'<NULL>'),COALESCE(version,'<NULL>'),COALESCE(remark,'<NULL>'),
              COALESCE(can_supplement,'<NULL>'),COALESCE(value_type,'<NULL>')))),0)
         FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@target_project AND is_delete=0 AND is_cancel=0)
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.org_config src
         JOIN `scf_self_order`.org_config tgt
           ON tgt.org_code=@target_channel
          AND tgt.org_type=src.org_type
          AND tgt.org_group_key=src.org_group_key
          AND tgt.org_detail_key=src.org_detail_key
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         WHERE src.org_code=@source_channel
           AND src.org_type='CHANNEL' AND src.org_group_key='DEFAULT'
           AND src.is_delete=0 AND src.is_cancel=0
           AND tgt.value <=> src.value
           AND tgt.default_value <=> src.default_value
           AND tgt.udf_1 <=> src.udf_1 AND tgt.udf_2 <=> src.udf_2
           AND tgt.udf_3 <=> src.udf_3 AND tgt.udf_4 <=> src.udf_4
           AND tgt.udf_5 <=> src.udf_5 AND tgt.udf_11 <=> src.udf_11
           AND tgt.udf_12 <=> src.udf_12 AND tgt.udf_13 <=> src.udf_13
           AND tgt.udf_14 <=> src.udf_14 AND tgt.udf_15 <=> src.udf_15)=8
    AND (SELECT COUNT(*)
         FROM `scf_self_saps`.business_config src
         JOIN `scf_self_saps`.business_config tgt
           ON tgt.query_key=CASE WHEN src.query_key=@source_channel THEN @target_channel
                                 WHEN src.query_key=@source_project THEN @target_project END
          AND tgt.group_key=src.group_key
          AND tgt.config_key=src.config_key
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         WHERE src.query_key IN (@source_channel,@source_project)
           AND src.is_delete=0 AND src.is_cancel=0
           AND tgt.query_type <=> src.query_type
           AND tgt.group_desc <=> src.group_desc
           AND tgt.config_value <=> src.config_value
           AND tgt.config_value_desc <=> src.config_value_desc
           AND tgt.config_status <=> src.config_status)=8
    AND (SELECT COUNT(*)
         FROM `scf_self_payment`.channel_fee_config src
         JOIN `scf_self_payment`.channel_fee_config tgt
           ON tgt.merchant_no=@target_channel
          AND tgt.channel_code=src.channel_code
          AND tgt.transaction_mode=src.transaction_mode
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         WHERE src.merchant_no=@source_channel
           AND src.channel_code='BCM' AND src.transaction_mode='SINGLE_REMIT'
           AND src.is_delete=0 AND src.is_cancel=0
           AND tgt.channle_id <=> src.channle_id
           AND tgt.biz_type <=> src.biz_type AND tgt.card_type <=> src.card_type
           AND tgt.bank_rule <=> src.bank_rule AND tgt.channel_fee_rule <=> src.channel_fee_rule
           AND tgt.start_range <=> src.start_range AND tgt.end_range <=> src.end_range
           AND tgt.channel_fee <=> src.channel_fee AND tgt.channel_fee_rate <=> src.channel_fee_rate
           AND tgt.platform_fee <=> src.platform_fee AND tgt.platform_fee_rate <=> src.platform_fee_rate
           AND tgt.platform_fee_enable <=> src.platform_fee_enable
           AND tgt.min_fee_amount <=> src.min_fee_amount AND tgt.max_fee_amount <=> src.max_fee_amount
           AND tgt.calculate_rule <=> src.calculate_rule
           AND tgt.main_account_belong <=> src.main_account_belong)=1,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_statement_scene_still_zero',
 IF((SELECT COUNT(*) FROM `scf_self_saps`.statement_scene
     WHERE capital_project_code=@target_project AND is_delete=0 AND is_cancel=0)=0,1,0));

COMMIT;
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_config_guard`;

/* ============================================================
 * 执行后核对
 * ============================================================ */
USE `scf_self_order`;
SELECT 'product_group' AS config_type, COUNT(*) AS actual_count
FROM product_detail_group WHERE product_id = @target_product_id AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'product_detail', COUNT(*)
FROM product_detail WHERE product_id = @target_product_id AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'project_attachment_config', COUNT(*)
FROM project_attachment_config WHERE project_no = @target_project AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'org_config', COUNT(*)
FROM org_config WHERE org_code = @target_channel AND org_group_key = 'DEFAULT' AND is_delete = 0 AND is_cancel = 0;

SELECT d.code,d.default_value
FROM product_detail d
JOIN product_detail_group g ON g.id=d.detail_group_id
WHERE d.product_id=@target_product_id
  AND g.label_code='BASE_CONFIG'
  AND d.code IN ('period','InitialPaymentRatio','fpFeeRate','repaymentPlanCalculation','plateformFeeRate','repayDateRule')
  AND d.is_delete=0 AND d.is_cancel=0
ORDER BY d.code;

USE `scf_self_payment`;
SELECT query_key,group_key,config_key,config_value,config_status
FROM business_config
WHERE query_key=@target_channel AND is_delete=0 AND is_cancel=0;

SELECT merchant_no,channel_code,transaction_mode,card_type,bank_rule,
       channel_fee_rule,channel_fee,channel_fee_rate,platform_fee_enable
FROM channel_fee_config
WHERE merchant_no=@target_channel
  AND channel_code='BCM'
  AND transaction_mode='SINGLE_REMIT'
  AND is_delete=0 AND is_cancel=0;

USE `scf_self_saps`;
SELECT query_key,query_type,group_key,config_key,config_value,config_status
FROM business_config
WHERE query_key IN (@target_channel,@target_project) AND is_delete=0 AND is_cancel=0
ORDER BY query_key,group_key,config_key;

SELECT capital_project_code,scene_code,strategy_code,payment_mode,scene_type
FROM statement_scene
WHERE capital_project_code=@target_project AND is_delete=0 AND is_cancel=0
ORDER BY scene_code;
