-- 保费分期测试环境：为江苏易吾项目复制老自营合同配置
-- 源项目：XXT-PRJ-ZY-A
-- 目标项目：XXT-PRJ-YW-A
-- 规则：复制源项目全部有效合同配置及有效签署人，但排除居间服务协议。
-- 排除模板：insurance.intermediary.service.agreement
--
-- 说明：
-- 1. 脚本虽使用 INSERT ... SELECT ... NOT EXISTS，但强守卫要求目标配置为空；本脚本只能完整执行一次，禁止重跑。
-- 2. 不复制源记录主键；目标合同与签署人使用新自增主键。
-- 3. 不修改、不删除已有目标配置。
-- 4. 执行前后均提供核对 SQL；预期新增合同 32 条、签署人 39 条
--    （以执行时源项目有效数据为准）。
-- 5. 必须在“遇错停止”模式下执行；任一 guard 报错立即 ROLLBACK，禁止跳过错误继续 COMMIT。

USE `scf_self_order`;

SET @source_project_code = 'XXT-PRJ-ZY-A';
SET @target_project_code = 'XXT-PRJ-YW-A';
SET @excluded_template_code = 'insurance.intermediary.service.agreement';

-- 执行前源数据硬校验：当前测试库应为 33 个合同、42 个签署人；排除居间后为 32/39。
SELECT
    (SELECT COUNT(*) FROM contract_config
     WHERE project_code = @source_project_code AND is_delete = 0 AND is_cancel = 0) AS source_contract_should_be_33,
    (SELECT COUNT(*) FROM contract_config c
     JOIN contract_config_signers s ON s.contract_config_id = c.id
     WHERE c.project_code = @source_project_code
       AND c.is_delete = 0 AND c.is_cancel = 0
       AND s.is_delete = 0 AND s.is_cancel = 0) AS source_signer_should_be_42;

-- 执行前核对：目标项目不应存在居间协议。
SELECT
    c.project_code,
    c.scene_code,
    c.template_code,
    c.template_name,
    c.is_delete,
    c.is_cancel
FROM contract_config c
WHERE c.project_code IN (@source_project_code, @target_project_code)
ORDER BY c.project_code, c.scene_code, c.template_code;

SELECT COUNT(*) AS expected_contract_count
FROM contract_config c
WHERE c.project_code = @source_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
  AND c.template_code <> @excluded_template_code;

SELECT COUNT(*) AS expected_signer_count
FROM contract_config c
JOIN contract_config_signers s ON s.contract_config_id = c.id
WHERE c.project_code = @source_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
  AND s.is_delete = 0
  AND s.is_cancel = 0
  AND c.template_code <> @excluded_template_code;

-- 强制执行守卫：防止源配置已变更、目标被并发写入或源自然键重复时继续复制。
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_contract_guard`;
CREATE TEMPORARY TABLE `scf_self_order`.`_hzf000133_contract_guard` (
    check_name VARCHAR(100) NOT NULL PRIMARY KEY,
    passed TINYINT NOT NULL,
    CONSTRAINT `chk_hzf000133_contract_guard` CHECK (passed = 1)
);

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('source_contract_counts_33_42_excluded_32_39',
 IF((SELECT COUNT(*) FROM contract_config
     WHERE project_code=@source_project_code AND is_delete=0 AND is_cancel=0)=33
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@source_project_code
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=42
    AND (SELECT COUNT(*) FROM contract_config
         WHERE project_code=@source_project_code AND template_code<>@excluded_template_code
           AND is_delete=0 AND is_cancel=0)=32
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@source_project_code AND c.template_code<>@excluded_template_code
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=39,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('source_contract_natural_keys_unique',
 IF((SELECT COUNT(*) FROM (
       SELECT scene_code,template_code
       FROM contract_config
       WHERE project_code=@source_project_code AND template_code<>@excluded_template_code
         AND is_delete=0 AND is_cancel=0
       GROUP BY scene_code,template_code HAVING COUNT(*)>1
     ) duplicated_contract)=0
    AND (SELECT COUNT(*) FROM (
       SELECT c.scene_code,c.template_code,s.identity,s.identity_name,s.identity_type,s.sign_type,s.sort
       FROM contract_config c
       JOIN contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code=@source_project_code AND c.template_code<>@excluded_template_code
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0
       GROUP BY c.scene_code,c.template_code,s.identity,s.identity_name,s.identity_type,s.sign_type,s.sort
       HAVING COUNT(*)>1
     ) duplicated_signer)=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('target_contracts_still_empty',
 IF((SELECT COUNT(*) FROM contract_config WHERE project_code=@target_project_code)=0,1,0));

-- 强制执行顺序：基础配置脚本必须已完整提交，且目标仍保持 9 期/JDPAY 运行值。
INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('base_config_ready_before_contract_copy',
 IF((SELECT COUNT(*) FROM product_detail_group
     WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=15
    AND (SELECT COUNT(*) FROM product_detail
         WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=89
    AND (SELECT COUNT(*) FROM project_attachment_config
         WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=44
    AND (SELECT COUNT(*) FROM org_config
         WHERE org_code='HZF000133' AND org_type='CHANNEL' AND org_group_key='DEFAULT'
           AND is_delete=0 AND is_cancel=0)=8
    AND (SELECT COUNT(*)
         FROM product_detail d JOIN product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
           AND d.code='period' AND d.default_value='9'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_payment`.business_config
         WHERE query_key='HZF000133' AND query_type='COOPERATION'
           AND group_key='PaymentConfig' AND config_key='pay_channel'
           AND config_value='JDPAY' AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no='HZF000133' AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT'
           AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
           AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A') AND config_status=1
           AND is_delete=0 AND is_cancel=0)=8,1,0));

START TRANSACTION;

-- 复制合同配置。业务字段与老自营保持一致，仅替换项目编号。
INSERT INTO contract_config (
    main_id,
    tenant_code,
    project_code,
    template_code,
    template_name,
    template_name_custom,
    template_version,
    template_url,
    user_type,
    scene_code,
    scene_title,
    `generateType`,
    use_condition,
    silent_sign,
    contract_no_rule,
    ref_other_contract,
    sign_mode,
    biz_type,
    contract_vars,
    sort,
    callback_url,
    jump_back_url,
    hide,
    create_time,
    create_by,
    is_delete,
    is_cancel,
    version,
    remark
)
SELECT
    src.main_id,
    src.tenant_code,
    @target_project_code,
    src.template_code,
    src.template_name,
    src.template_name_custom,
    src.template_version,
    src.template_url,
    src.user_type,
    src.scene_code,
    src.scene_title,
    src.`generateType`,
    src.use_condition,
    src.silent_sign,
    src.contract_no_rule,
    src.ref_other_contract,
    src.sign_mode,
    src.biz_type,
    src.contract_vars,
    src.sort,
    src.callback_url,
    src.jump_back_url,
    src.hide,
    NOW(),
    'contract_config_copy',
    0,
    0,
    src.version,
    src.remark
FROM contract_config src
WHERE src.project_code = @source_project_code
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND src.template_code <> @excluded_template_code
  AND NOT EXISTS (
      SELECT 1
      FROM contract_config tgt
      WHERE tgt.project_code = @target_project_code
        AND tgt.template_code <=> src.template_code
        AND tgt.scene_code <=> src.scene_code
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

-- 复制签署人配置，通过“场景 + 模板编码”映射目标合同主键。
INSERT INTO contract_config_signers (
    contract_config_id,
    identity,
    identity_name,
    identity_type,
    sign_type,
    sort,
    create_time,
    create_by,
    is_delete,
    is_cancel,
    version,
    remark
)
SELECT
    tgt.id,
    src_signer.identity,
    src_signer.identity_name,
    src_signer.identity_type,
    src_signer.sign_type,
    src_signer.sort,
    NOW(),
    'contract_config_copy',
    0,
    0,
    src_signer.version,
    src_signer.remark
FROM contract_config src
JOIN contract_config_signers src_signer
  ON src_signer.contract_config_id = src.id
JOIN contract_config tgt
  ON tgt.project_code = @target_project_code
 AND tgt.template_code <=> src.template_code
 AND tgt.scene_code <=> src.scene_code
 AND tgt.is_delete = 0
 AND tgt.is_cancel = 0
WHERE src.project_code = @source_project_code
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND src_signer.is_delete = 0
  AND src_signer.is_cancel = 0
  AND src.template_code <> @excluded_template_code
  AND NOT EXISTS (
      SELECT 1
      FROM contract_config_signers exists_signer
      WHERE exists_signer.contract_config_id = tgt.id
        AND exists_signer.identity <=> src_signer.identity
        AND exists_signer.identity_name <=> src_signer.identity_name
        AND exists_signer.identity_type <=> src_signer.identity_type
        AND exists_signer.sign_type <=> src_signer.sign_type
        AND exists_signer.sort <=> src_signer.sort
        AND exists_signer.is_delete = 0
        AND exists_signer.is_cancel = 0
  );

-- COMMIT 前强制语义验收。
INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_contract_counts_32_39_no_intermediary',
 IF((SELECT COUNT(*) FROM contract_config
     WHERE project_code=@target_project_code AND is_delete=0 AND is_cancel=0)=32
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@target_project_code
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=39
    AND (SELECT COUNT(*) FROM contract_config
         WHERE project_code=@target_project_code AND template_code=@excluded_template_code
           AND is_delete=0 AND is_cancel=0)=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_binding_card_ci_configs_4_signers_5',
 IF((SELECT COUNT(*) FROM contract_config c
     WHERE c.project_code=@target_project_code AND c.scene_code='BINDING_CARD'
       AND c.template_code<>@excluded_template_code
       AND (c.use_condition IS NULL OR c.use_condition=''
            OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
       AND c.is_delete=0 AND c.is_cancel=0)=4
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@target_project_code AND c.scene_code='BINDING_CARD'
           AND c.template_code<>@excluded_template_code
           AND (c.use_condition IS NULL OR c.use_condition=''
                OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=5,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_binding_success_configs_3_signers_3',
 IF((SELECT COUNT(*) FROM contract_config
     WHERE project_code=@target_project_code AND scene_code='BINDING_SUCCESS'
       AND `generateType`='ATTACHMENT' AND is_delete=0 AND is_cancel=0)=3
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@target_project_code AND c.scene_code='BINDING_SUCCESS'
           AND c.`generateType`='ATTACHMENT'
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=3,1,0));

-- 不只核对数量：32 个合同和 39 个签署人的复制业务字段必须与山西源配置一致。
INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_contract_business_values_equal_source',
 IF((SELECT COUNT(*)
     FROM contract_config src
     JOIN contract_config tgt
       ON tgt.project_code=@target_project_code
      AND tgt.scene_code <=> src.scene_code
      AND tgt.template_code <=> src.template_code
      AND tgt.is_delete=0 AND tgt.is_cancel=0
     WHERE src.project_code=@source_project_code
       AND src.template_code<>@excluded_template_code
       AND src.is_delete=0 AND src.is_cancel=0
       AND tgt.main_id <=> src.main_id
       AND tgt.tenant_code <=> src.tenant_code
       AND tgt.template_name <=> src.template_name
       AND tgt.template_name_custom <=> src.template_name_custom
       AND tgt.template_version <=> src.template_version
       AND tgt.template_url <=> src.template_url
       AND tgt.user_type <=> src.user_type
       AND tgt.scene_title <=> src.scene_title
       AND tgt.`generateType` <=> src.`generateType`
       AND tgt.use_condition <=> src.use_condition
       AND tgt.silent_sign <=> src.silent_sign
       AND tgt.contract_no_rule <=> src.contract_no_rule
       AND tgt.ref_other_contract <=> src.ref_other_contract
       AND tgt.sign_mode <=> src.sign_mode
       AND tgt.biz_type <=> src.biz_type
       AND tgt.contract_vars <=> src.contract_vars
       AND tgt.sort <=> src.sort
       AND tgt.callback_url <=> src.callback_url
       AND tgt.jump_back_url <=> src.jump_back_url
       AND tgt.hide <=> src.hide
       AND tgt.version <=> src.version
       AND tgt.remark <=> src.remark)=32
    AND (SELECT COUNT(*)
         FROM contract_config src
         JOIN contract_config_signers ss ON ss.contract_config_id=src.id
         JOIN contract_config tgt
           ON tgt.project_code=@target_project_code
          AND tgt.scene_code <=> src.scene_code
          AND tgt.template_code <=> src.template_code
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         JOIN contract_config_signers ts
           ON ts.contract_config_id=tgt.id
          AND ts.identity <=> ss.identity
          AND ts.identity_name <=> ss.identity_name
          AND ts.identity_type <=> ss.identity_type
          AND ts.sign_type <=> ss.sign_type
          AND ts.sort <=> ss.sort
          AND ts.is_delete=0 AND ts.is_cancel=0
         WHERE src.project_code=@source_project_code
           AND src.template_code<>@excluded_template_code
           AND src.is_delete=0 AND src.is_cancel=0
           AND ss.is_delete=0 AND ss.is_cancel=0
           AND ts.version <=> ss.version
           AND ts.remark <=> ss.remark)=39,1,0));

COMMIT;
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_contract_guard`;

-- 执行后核对。
SELECT
    c.scene_code,
    COUNT(*) AS contract_count
FROM contract_config c
WHERE c.project_code = @target_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
GROUP BY c.scene_code
ORDER BY c.scene_code;

SELECT COUNT(*) AS actual_contract_count
FROM contract_config c
WHERE c.project_code = @target_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0;

SELECT COUNT(*) AS actual_signer_count
FROM contract_config c
JOIN contract_config_signers s ON s.contract_config_id = c.id
WHERE c.project_code = @target_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
  AND s.is_delete = 0
  AND s.is_cancel = 0;

SELECT COUNT(*) AS intermediary_agreement_count_should_be_zero
FROM contract_config c
WHERE c.project_code = @target_project_code
  AND c.template_code = @excluded_template_code
  AND c.is_delete = 0
  AND c.is_cancel = 0;

-- 如需回滚本脚本本次新增的目标项目合同配置，请人工确认后依次执行：
-- DELETE s
-- FROM contract_config_signers s
-- JOIN contract_config c ON c.id = s.contract_config_id
-- WHERE c.project_code = @target_project_code
--   AND s.create_by = 'contract_config_copy';
--
-- DELETE FROM contract_config
-- WHERE project_code = @target_project_code
--   AND create_by = 'contract_config_copy';
