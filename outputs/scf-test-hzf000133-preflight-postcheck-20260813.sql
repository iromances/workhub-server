-- 保费分期测试环境：江苏易吾 HZF000133 执行前/执行后核验
-- 环境/分支：test / master（用户指定）
-- 本文件只包含 SELECT/SET 只读语句，不修改数据库。
-- 目标：HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001（产品 id=278）

/* ======================== A. 执行前基线 ======================== */

-- A01 主数据必须各 1 条，且全部启用。
SELECT id, no, name, channel_codes, status, effect_date,
       margin_ratio, current_margin_amount, total_limit,
       supplier_id, technical_service_provider_id
FROM scf_self_order.amp_project
WHERE no = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

SELECT id, code, name, project_no, status, repayment_type,
       active_time, invalid_time, customer_down_payment_ratio,
       commercial_insurance_rate, penalty_interest_rate, grace_period_days
FROM scf_self_order.amp_product
WHERE code = 'PRD-YW-A001' AND is_delete = 0 AND is_cancel = 0;

SELECT id, channel_code, type, status, tenant_code, user_code, auth_code
FROM scf_self_order.crm_channel
WHERE channel_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

-- 9 期资金方主数据：中化必须启用、支持 9 期商业险；通汇必须启用并支持 9 期。
SELECT org_code, org_name, status, periods, supported_periods, supported_insurance_types
FROM scf_self_order.org_funding_party
WHERE org_code IN ('ZJF000015','ZJF000011')
  AND is_delete = 0 AND is_cancel = 0
ORDER BY org_code;

-- A02 项目关系必须只有目标产品；商业险-only 是目标现状。
SELECT id, project_id, project_no, product_ids, insurance_type,
       period_type, period, project_detail_key, value
FROM scf_self_order.project_config
WHERE project_id = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

-- A03 支付专属配置已存在，不允许从山西覆盖。
SELECT cooperation_code, project_no, product_code, payment_product, channel
FROM scf_self_payment.router
WHERE cooperation_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

SELECT id, cooperation_code, channel_code, query_key, status
FROM scf_self_payment.channel_pay_config
WHERE cooperation_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

-- JDPAY 运行必填字段：必须均非空；敏感值只核验存在性，不在结果中展示。
SELECT id, cooperation_code, channel_code, query_key, status,
       CASE WHEN channel_url IS NOT NULL AND channel_url <> '' THEN 1 ELSE 0 END AS channel_url_present,
       CASE WHEN member_id IS NOT NULL AND member_id <> '' THEN 1 ELSE 0 END AS member_id_present,
       CASE WHEN terminal_id IS NOT NULL AND terminal_id <> '' THEN 1 ELSE 0 END AS terminal_id_present,
       CASE WHEN resv_one IS NOT NULL AND resv_one <> '' THEN 1 ELSE 0 END AS resv_one_present,
       CASE WHEN notify_url IS NOT NULL AND notify_url <> '' THEN 1 ELSE 0 END AS notify_url_present,
       CASE WHEN pay_config_code IS NOT NULL AND pay_config_code <> '' THEN 1 ELSE 0 END AS pay_config_code_present
FROM scf_self_payment.channel_pay_config
WHERE cooperation_code = 'HZF000133'
  AND channel_code = 'JDPAY'
  AND status = 1
  AND is_delete = 0 AND is_cancel = 0;

-- 目标 JDPAY 端点/商户/应用/租户/通知地址应与当前已成功运行的山西 JDPAY 配置一致；仅编码可不同。
-- 只返回 0/1，不展示敏感值。
SELECT CASE WHEN t.channel_url=s.channel_url THEN 1 ELSE 0 END AS channel_url_same_as_running_source,
       CASE WHEN t.member_id=s.member_id THEN 1 ELSE 0 END AS member_id_same_as_running_source,
       CASE WHEN t.terminal_id=s.terminal_id THEN 1 ELSE 0 END AS terminal_id_same_as_running_source,
       CASE WHEN t.resv_one=s.resv_one THEN 1 ELSE 0 END AS tenant_same_as_running_source,
       CASE WHEN t.notify_url=s.notify_url THEN 1 ELSE 0 END AS notify_url_same_as_running_source
FROM scf_self_payment.channel_pay_config t
JOIN scf_self_payment.channel_pay_config s
  ON s.cooperation_code='HZF000144' AND s.channel_code='JDPAY' AND s.status=1
 AND s.is_delete=0 AND s.is_cancel=0
WHERE t.cooperation_code='HZF000133' AND t.channel_code='JDPAY' AND t.status=1
  AND t.is_delete=0 AND t.is_cancel=0;

SELECT id, merchant_no, channel_code, transaction_mode, card_type, bank_rule,
       channel_fee_rule, channel_fee, channel_fee_rate, platform_fee_enable
FROM scf_self_payment.channel_fee_config
WHERE merchant_no = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

-- 放款固定走 BCM；目标执行前应没有 SINGLE_REMIT，主脚本执行后必须精确为 1 条。
-- BCM Mock 使用 DEF 回退，以下两项必须启用；URL 只校验非空，不复制为目标项目专属配置。
SELECT query_key, query_type, group_key, config_key,
       CASE WHEN config_key='bcmMockUrl' THEN IF(config_value IS NULL OR config_value='',0,1)
            ELSE (config_value='true') END AS runtime_value_ready,
       config_status
FROM scf_self_payment.business_config
WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
  AND config_key IN ('bcmMockFlag','bcmMockUrl')
  AND is_delete=0 AND is_cancel=0
ORDER BY config_key;

-- A04 当前缺口基线：执行前通常为 0/0/0/0/0/0/0/0。
SELECT
  (SELECT COUNT(*) FROM scf_self_order.product_detail_group
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_group_count,
  (SELECT COUNT(*) FROM scf_self_order.product_detail
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_detail_count,
  (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
   WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0) AS project_attachment_count,
  (SELECT COUNT(*) FROM scf_self_order.org_config
   WHERE org_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0) AS org_config_count,
  (SELECT COUNT(*) FROM scf_self_order.contract_config
   WHERE project_code = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0) AS contract_count,
  (SELECT COUNT(*) FROM scf_self_payment.business_config
   WHERE query_key = 'HZF000133' AND is_delete = 0 AND is_cancel = 0) AS payment_business_count,
  (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
   WHERE merchant_no='HZF000133' AND channel_code='BCM'
     AND transaction_mode='SINGLE_REMIT') AS bcm_single_remit_fee_count,
  (SELECT COUNT(*) FROM scf_self_saps.business_config
   WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A') AND is_delete = 0 AND is_cancel = 0) AS saps_business_count;

-- A05 山西源数据必须仍为：15 / 89 / 44 / 8 / 32 / 39 / 1 / 1 / 8。
-- 任一数量变化都停止执行，先重新比对源配置，不要按旧预期继续复制。
SELECT
  (SELECT COUNT(*)
   FROM scf_self_order.product_detail_group g
   JOIN scf_self_order.amp_product p ON p.id = g.product_id
   WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
     AND g.is_delete = 0 AND g.is_cancel = 0) AS source_group_should_be_15,
  (SELECT COUNT(*)
   FROM scf_self_order.product_detail d
   JOIN scf_self_order.amp_product p ON p.id = d.product_id
   WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
     AND d.is_delete = 0 AND d.is_cancel = 0) AS source_detail_should_be_89,
  (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
   WHERE project_no='XXT-PRJ-ZY-A' AND is_delete=0 AND is_cancel=0) AS source_attachment_should_be_44,
  (SELECT COUNT(*) FROM scf_self_order.org_config
   WHERE org_code = 'HZF000144' AND org_type = 'CHANNEL' AND org_group_key = 'DEFAULT'
     AND is_delete = 0 AND is_cancel = 0) AS source_org_should_be_8,
  (SELECT COUNT(*) FROM scf_self_order.contract_config
   WHERE project_code = 'XXT-PRJ-ZY-A'
     AND template_code <> 'insurance.intermediary.service.agreement'
     AND is_delete = 0 AND is_cancel = 0) AS source_contract_should_be_32,
  (SELECT COUNT(*)
   FROM scf_self_order.contract_config c
   JOIN scf_self_order.contract_config_signers s ON s.contract_config_id = c.id
   WHERE c.project_code = 'XXT-PRJ-ZY-A'
     AND c.template_code <> 'insurance.intermediary.service.agreement'
     AND c.is_delete = 0 AND c.is_cancel = 0
     AND s.is_delete = 0 AND s.is_cancel = 0) AS source_signer_should_be_39,
  (SELECT COUNT(*) FROM scf_self_payment.business_config
   WHERE query_key = 'HZF000144' AND query_type='COOPERATION'
     AND group_key = 'PaymentConfig' AND config_key = 'pay_channel'
     AND is_delete = 0 AND is_cancel = 0) AS source_payment_should_be_1,
  (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
   WHERE merchant_no='HZF000144' AND channel_code='BCM'
     AND transaction_mode='SINGLE_REMIT'
     AND is_delete=0 AND is_cancel=0) AS source_bcm_single_remit_should_be_1,
  (SELECT COUNT(*) FROM scf_self_saps.business_config
   WHERE query_key IN ('HZF000144','XXT-PRJ-ZY-A')
     AND is_delete = 0 AND is_cancel = 0) AS source_saps_should_be_8;

-- A06 源自然键必须都无重复；四项必须全部为 0。
SELECT 'product_group_duplicate' AS check_item, COUNT(*) AS duplicate_key_count
FROM (
  SELECT g.label_code
  FROM scf_self_order.product_detail_group g
  JOIN scf_self_order.amp_product p ON p.id = g.product_id
  WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
    AND g.is_delete = 0 AND g.is_cancel = 0
  GROUP BY g.label_code HAVING COUNT(*) > 1
) x
UNION ALL
SELECT 'product_detail_duplicate', COUNT(*)
FROM (
  SELECT g.label_code, d.code
  FROM scf_self_order.product_detail d
  JOIN scf_self_order.product_detail_group g ON g.id = d.detail_group_id
  JOIN scf_self_order.amp_product p ON p.id = d.product_id
  WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
    AND g.is_delete = 0 AND g.is_cancel = 0
    AND d.is_delete = 0 AND d.is_cancel = 0
  GROUP BY g.label_code, d.code HAVING COUNT(*) > 1
) x
UNION ALL
SELECT 'org_config_duplicate', COUNT(*)
FROM (
  SELECT org_type, org_group_key, org_detail_key
  FROM scf_self_order.org_config
  WHERE org_code = 'HZF000144' AND is_delete = 0 AND is_cancel = 0
  GROUP BY org_type, org_group_key, org_detail_key HAVING COUNT(*) > 1
) x
UNION ALL
SELECT 'contract_config_duplicate', COUNT(*)
FROM (
  SELECT scene_code, template_code
  FROM scf_self_order.contract_config
  WHERE project_code = 'XXT-PRJ-ZY-A'
    AND template_code <> 'insurance.intermediary.service.agreement'
    AND is_delete = 0 AND is_cancel = 0
  GROUP BY scene_code, template_code HAVING COUNT(*) > 1
) x;

/* ======================== B. 执行后验收 ======================== */

-- B01 硬性数量：15 / 89 / 44 / 8 / 32 / 39 / 1 / 1 / 8（含项目附件 44 条、BCM 单笔代付手续费 1 条）。
SELECT
  (SELECT COUNT(*) FROM scf_self_order.product_detail_group
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_group_should_be_15,
  (SELECT COUNT(*) FROM scf_self_order.product_detail
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_detail_should_be_89,
  (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
   WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0) AS project_attachment_should_be_44,
  (SELECT COUNT(*) FROM scf_self_order.org_config
   WHERE org_code = 'HZF000133' AND org_type = 'CHANNEL' AND org_group_key = 'DEFAULT'
     AND is_delete = 0 AND is_cancel = 0) AS org_config_should_be_8,
  (SELECT COUNT(*) FROM scf_self_order.contract_config
   WHERE project_code = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0) AS contract_should_be_32,
  (SELECT COUNT(*) FROM scf_self_order.contract_config c
   JOIN scf_self_order.contract_config_signers s ON s.contract_config_id = c.id
   WHERE c.project_code = 'XXT-PRJ-YW-A'
     AND c.is_delete = 0 AND c.is_cancel = 0
     AND s.is_delete = 0 AND s.is_cancel = 0) AS signer_should_be_39,
  (SELECT COUNT(*) FROM scf_self_payment.business_config
   WHERE query_key = 'HZF000133' AND query_type='COOPERATION'
     AND group_key = 'PaymentConfig' AND config_key = 'pay_channel'
     AND config_value = 'JDPAY' AND config_status = 1
     AND is_delete = 0 AND is_cancel = 0) AS payment_config_should_be_1,
  (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
   WHERE merchant_no='HZF000133' AND channel_code='BCM' AND transaction_mode='SINGLE_REMIT'
     AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
     AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
     AND is_delete=0 AND is_cancel=0) AS bcm_single_remit_fee_should_be_1,
  (SELECT COUNT(*) FROM scf_self_saps.business_config
   WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A')
     AND config_status = 1 AND is_delete = 0 AND is_cancel = 0) AS saps_business_should_be_8;

-- B02 9 期及核心运行配置必须完全一致。
SELECT g.label_code, d.code, d.default_value
FROM scf_self_order.product_detail_group g
JOIN scf_self_order.product_detail d
  ON d.detail_group_id = g.id AND d.product_id = g.product_id
WHERE g.product_id = 278
  AND g.is_delete = 0 AND g.is_cancel = 0
  AND d.is_delete = 0 AND d.is_cancel = 0
  AND d.code IN (
      'period','InitialPaymentRatio','customerFeeRateMax','customerFeeRateMin','fpFeeRate',
      'repaymentPlanCalculation','plateformFeeRate','repayDateRule',
      'firstFunder','secondFunder','payment_support_channel'
  )
ORDER BY g.label_code, d.code;

-- 预期关键值：
-- period=9
-- InitialPaymentRatio={"9":0.1}
-- customerFeeRateMax={"9":0.36}
-- customerFeeRateMin={"9":0.001}
-- fpFeeRate={"9":0.04}
-- repaymentPlanCalculation=ZY_Customer
-- plateformFeeRate=0.005
-- payment_support_channel=JDPAY

-- B03 项目、产品回填；目标商业险费率仍为 9 期 3.8% + 3.2%，不得被山西覆盖。
SELECT id, code, project_no, repayment_type, grace_period_days,
       commercial_insurance_rate, customer_down_payment_ratio, penalty_interest_rate
FROM scf_self_order.amp_product
WHERE id = 278;

SELECT id, project_id, project_no, project_detail_key, value,
       insurance_type, period_type, period, product_ids
FROM scf_self_order.project_config
WHERE project_id = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

-- B04 结算场景必须仍为 0；非 0 立即停止进件并回查误执行脚本。
SELECT COUNT(*) AS statement_scene_must_be_0
FROM scf_self_saps.statement_scene
WHERE capital_project_code = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

-- B05 居间协议必须为 0。
SELECT COUNT(*) AS intermediary_contract_must_be_0
FROM scf_self_order.contract_config
WHERE project_code = 'XXT-PRJ-YW-A'
  AND template_code = 'insurance.intermediary.service.agreement'
  AND is_delete = 0 AND is_cancel = 0;

-- 目标仅商业险（insuranceType=1）：BINDING_CARD 运行时必须匹配 4 份合同、5 个签署人。
-- 非商业险模板的 use_condition={"insuranceType":"2"} 不应进入本次签约流程。
SELECT COUNT(*) AS binding_card_ci_contract_should_be_4,
       SUM(signer_count) AS binding_card_ci_signer_should_be_5
FROM (
  SELECT c.id, COUNT(s.id) AS signer_count
  FROM scf_self_order.contract_config c
  LEFT JOIN scf_self_order.contract_config_signers s
    ON s.contract_config_id = c.id
   AND s.is_delete = 0 AND s.is_cancel = 0
  WHERE c.project_code = 'XXT-PRJ-YW-A'
    AND c.scene_code = 'BINDING_CARD'
    AND c.template_code <> 'insurance.intermediary.service.agreement'
    AND c.is_delete = 0 AND c.is_cancel = 0
    AND (
      c.use_condition IS NULL OR c.use_condition = ''
      OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition, '$.insuranceType')) LIKE '%1%'
    )
  GROUP BY c.id
) binding_card_ci;

-- 投保签章配置必须为 3 份附件模板、3 个签署人。
-- 实际车辆缺少某一种原始保单时，对应填充器返回空并跳过该模板。
SELECT COUNT(*) AS binding_success_contract_should_be_3,
       SUM(signer_count) AS binding_success_signer_should_be_3
FROM (
  SELECT c.id, COUNT(s.id) AS signer_count
  FROM scf_self_order.contract_config c
  LEFT JOIN scf_self_order.contract_config_signers s
    ON s.contract_config_id = c.id
   AND s.is_delete = 0 AND s.is_cancel = 0
  WHERE c.project_code = 'XXT-PRJ-YW-A'
    AND c.scene_code = 'BINDING_SUCCESS'
    AND c.generateType = 'ATTACHMENT'
    AND c.is_delete = 0 AND c.is_cancel = 0
  GROUP BY c.id
) binding_success;

-- B06 SAPS 使用产品专属配置，不存在时回退 DEF；以下默认科目是生成缴费单和客户账单的硬前置。
-- 必须依次为 4 / 5 / 2。客户账单科目按编码去重，忽略现库历史重复行；无需再复制产品专属科目。
SELECT 'payment_slip_subject_DEF' AS check_item, COUNT(*) AS actual_count, 4 AS expected_count
FROM scf_self_saps.payment_slip_subject
WHERE product_code = 'DEF'
  AND subject_no IN ('201','10201','10203','103')
  AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'bill_subject_DEF', COUNT(*), 5
FROM scf_self_saps.bill_subject
WHERE product_code = 'DEF'
  AND subject_no IN ('101','10102','10201','10202','103')
  AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'customer_bill_subject_101_10102_DEF', COUNT(DISTINCT subject_no), 2
FROM scf_self_saps.customer_bill_subject
WHERE product_code = 'DEF'
  AND subject_no IN ('101','10102')
  AND is_delete = 0 AND is_cancel = 0;

-- B06-1 放款支付运行项：依次必须为 1 / 1 / 1。
SELECT 'target_BCM_SINGLE_REMIT_fee' AS check_item, COUNT(*) AS actual_count, 1 AS expected_count
FROM scf_self_payment.channel_fee_config
WHERE merchant_no='HZF000133' AND channel_code='BCM' AND transaction_mode='SINGLE_REMIT'
  AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
  AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
  AND is_delete=0 AND is_cancel=0
UNION ALL
SELECT 'DEF_bcmMockFlag_true', COUNT(*), 1
FROM scf_self_payment.business_config
WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
  AND config_key='bcmMockFlag' AND config_value='true' AND config_status=1
  AND is_delete=0 AND is_cancel=0
UNION ALL
SELECT 'DEF_bcmMockUrl_present', COUNT(*), 1
FROM scf_self_payment.business_config
WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
  AND config_key='bcmMockUrl' AND config_value IS NOT NULL AND config_value<>'' AND config_status=1
  AND is_delete=0 AND is_cancel=0;

-- B07 资金方硬校验：两项都必须为 1。
SELECT 'first_funder_ZJF000015_9_ci' AS check_item, COUNT(*) AS actual_count, 1 AS expected_count
FROM scf_self_order.org_funding_party
WHERE org_code = 'ZJF000015' AND status = 1
  AND JSON_CONTAINS(periods,'"9"')
  AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
  AND supported_insurance_types = '1'
  AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'second_funder_ZJF000011_9', COUNT(*), 1
FROM scf_self_order.org_funding_party
WHERE org_code = 'ZJF000011' AND status = 1
  AND JSON_CONTAINS(periods,'"9"')
  AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
  AND is_delete = 0 AND is_cancel = 0;

-- B08 最终单行放行闸门：只有 gate_result=GO 才允许新建进件。
-- 任一项不满足都返回 STOP，再根据 B01~B07 定位具体项。
SELECT CASE WHEN
  (SELECT COUNT(*) FROM scf_self_order.product_detail_group
   WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=15
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail
       WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=89
  AND (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
       WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=44
  AND (SELECT COUNT(*) FROM scf_self_order.org_config
       WHERE org_code='HZF000133' AND org_type='CHANNEL' AND org_group_key='DEFAULT'
         AND is_delete=0 AND is_cancel=0)=8
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config
       WHERE project_code='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=32
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       JOIN scf_self_order.contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code='XXT-PRJ-YW-A'
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=39
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config
       WHERE project_code='XXT-PRJ-YW-A'
         AND template_code='insurance.intermediary.service.agreement'
         AND is_delete=0 AND is_cancel=0)=0
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       WHERE c.project_code='XXT-PRJ-YW-A' AND c.scene_code='BINDING_CARD'
         AND (c.use_condition IS NULL OR c.use_condition=''
              OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
         AND c.is_delete=0 AND c.is_cancel=0)=4
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       JOIN scf_self_order.contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code='XXT-PRJ-YW-A' AND c.scene_code='BINDING_CARD'
         AND (c.use_condition IS NULL OR c.use_condition=''
              OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=5
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config
       WHERE project_code='XXT-PRJ-YW-A' AND scene_code='BINDING_SUCCESS'
         AND `generateType`='ATTACHMENT' AND is_delete=0 AND is_cancel=0)=3
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       JOIN scf_self_order.contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code='XXT-PRJ-YW-A' AND c.scene_code='BINDING_SUCCESS'
         AND c.`generateType`='ATTACHMENT'
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=3
  AND (SELECT COUNT(*) FROM scf_self_payment.business_config
       WHERE query_key='HZF000133' AND query_type='COOPERATION'
         AND group_key='PaymentConfig' AND config_key='pay_channel'
         AND config_value='JDPAY' AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.router
       WHERE cooperation_code='HZF000133' AND project_no='XXT-PRJ-YW-A'
         AND product_code='PRD-YW-A001' AND payment_product='DS' AND channel='JDPAY'
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_pay_config
       WHERE cooperation_code='HZF000133' AND channel_code='JDPAY' AND status=1
         AND channel_url<>'' AND member_id<>'' AND terminal_id<>'' AND resv_one<>''
         AND notify_url<>'' AND pay_config_code<>''
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_pay_config t
       JOIN scf_self_payment.channel_pay_config s
         ON s.cooperation_code='HZF000144' AND s.channel_code='JDPAY' AND s.status=1
        AND s.is_delete=0 AND s.is_cancel=0
       WHERE t.cooperation_code='HZF000133' AND t.channel_code='JDPAY' AND t.status=1
         AND t.channel_url=s.channel_url AND t.member_id=s.member_id
         AND t.terminal_id=s.terminal_id AND t.resv_one=s.resv_one
         AND t.notify_url=s.notify_url
         AND t.is_delete=0 AND t.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
       WHERE merchant_no='HZF000133' AND channel_code='JDPAY'
         AND transaction_mode='AGREEMENT_PAYMENT' AND channel_fee_rate=0.002
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
       WHERE merchant_no='HZF000133' AND channel_code='BCM'
         AND transaction_mode='SINGLE_REMIT'
         AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
         AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.business_config
       WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
         AND config_key='bcmMockFlag' AND config_value='true' AND config_status=1
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.business_config
       WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
         AND config_key='bcmMockUrl' AND config_value IS NOT NULL AND config_value<>''
         AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_saps.business_config
       WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A') AND config_status=1
         AND is_delete=0 AND is_cancel=0)=8
  AND (SELECT COUNT(*) FROM scf_self_saps.statement_scene
       WHERE capital_project_code='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=0
  AND (SELECT COUNT(*) FROM scf_self_order.amp_product
       WHERE id=278 AND code='PRD-YW-A001' AND project_no='XXT-PRJ-YW-A'
         AND repayment_type='4' AND grace_period_days=0 AND status=1
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.project_config
       WHERE project_id='XXT-PRJ-YW-A' AND project_no='XXT-PRJ-YW-A'
         AND project_detail_key='MarginRatio' AND CAST(value AS DECIMAL(10,4))=0.1
         AND insurance_type='1' AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='period' AND d.default_value='9'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='InitialPaymentRatio'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.1'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='fpFeeRate'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.04'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='customerFeeRateMax'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.36'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='customerFeeRateMin'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.001'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='plateformFeeRate' AND d.default_value='0.005'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='repaymentPlanCalculation' AND d.default_value='ZY_Customer'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='PAY_CONFIG'
         AND d.code='payment_support_channel' AND d.default_value='JDPAY'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_saps.payment_slip_subject
       WHERE product_code='DEF' AND subject_no IN ('201','10201','10203','103')
         AND is_delete=0 AND is_cancel=0)=4
  AND (SELECT COUNT(*) FROM scf_self_saps.bill_subject
       WHERE product_code='DEF' AND subject_no IN ('101','10102','10201','10202','103')
         AND is_delete=0 AND is_cancel=0)=5
  AND (SELECT COUNT(DISTINCT subject_no) FROM scf_self_saps.customer_bill_subject
       WHERE product_code='DEF' AND subject_no IN ('101','10102')
         AND is_delete=0 AND is_cancel=0)=2
  AND (SELECT COUNT(*) FROM scf_self_order.org_funding_party
       WHERE org_code='ZJF000015' AND status=1 AND JSON_CONTAINS(periods,'"9"')
         AND supported_insurance_types='1' AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.org_funding_party
       WHERE org_code='ZJF000011' AND status=1 AND JSON_CONTAINS(periods,'"9"')
         AND is_delete=0 AND is_cancel=0)=1
THEN 'GO' ELSE 'STOP' END AS gate_result;

-- B09 环境待补偿队列门禁（不属于 HZF000133 配置）。
-- 正确补偿入口是 SAPS 的 transferTransactionSchedule；它按 transfer_no 定位，并用 payment_req_id 去支付系统查单。
-- scf-order 的 loanPaymentQueryStatusJob 存在查询键不一致：SAPS business_no 存的是申请单号，代码却用 LP 支付号查询，不能用于本次补偿。
-- READY 只表示当前没有可补偿的超时处理中转账，不等于已经证明 XXL 定时调度恢复；新单仍须按 C03-2 观察。
SELECT CASE WHEN
  (SELECT COUNT(*) FROM scf_self_saps.transfer_transaction
   WHERE transfer_type=10 AND status=2
     AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
     AND real_retry_count<5 AND is_delete=0 AND is_cancel=0)=0
  THEN 'READY' ELSE 'BLOCKED_BY_STALE_SAPS_LOAN_TRANSFER' END AS environment_result,
  (SELECT COUNT(*) FROM scf_self_order.loan_order
   WHERE status='340' AND create_time<DATE_SUB(NOW(),INTERVAL 10 MINUTE)
     AND is_delete=0 AND is_cancel=0) AS stale_loan_340_count,
  (SELECT COUNT(*) FROM scf_self_saps.transfer_transaction
   WHERE transfer_type=10 AND status=2
     AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
     AND real_retry_count<5 AND is_delete=0 AND is_cancel=0) AS saps_transfer_waiting_retry_count,
  (SELECT COUNT(*) FROM scf_self_saps.transfer_transaction
   WHERE transfer_type=10 AND status=2
     AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
     AND real_retry_count>=5 AND is_delete=0 AND is_cancel=0) AS saps_transfer_retry_exhausted_count,
  (SELECT MAX(payment_time) FROM scf_self_order.loan_order
   WHERE status='351' AND is_delete=0 AND is_cancel=0) AS latest_success_payment_time;

-- B09-1 需要补偿的 SAPS 放款转账明细。每个 transfer_no 单独触发一次 transferTransactionSchedule，随后重跑 B09。
-- 任务参数：{"transferNos":["查询结果中的 transfer_no"]}。Mock 310204 已实测返回 stat=1（成功）。
SELECT transfer_no, business_no AS application_no, order_id AS pay_apply_no,
       payment_req_id, cooperation_code, status, real_retry_count,
       running_status, instance_status, order_time
FROM scf_self_saps.transfer_transaction
WHERE transfer_type=10 AND status=2
  AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
  AND real_retry_count<5 AND is_delete=0 AND is_cancel=0
ORDER BY order_time, id;

/* ======================== C. 新进件链路跟踪 ======================== */

-- 必须是配置脚本执行后创建并完成预审的新申请单。
-- 不要使用旧单 APA260813205018000001：其 tech_service_fee_ratio 已在明细为空时固化为 0。
SET @application_no = '请替换为新申请单号';

-- C01 进件主状态、签约及首付缴费单引用。
SELECT a.application_no, a.status, a.project_no, a.product_code, a.partner_code,
       a.funder_code, a.sign_status, a.start_sign_flow_date,
       al.loan_period, al.first_payment_status, al.first_payment_slip_no,
       al.loan_application_no, al.loan_time
FROM scf_self_order.amp_application a
LEFT JOIN scf_self_order.application_loan al
  ON al.application_no = a.application_no AND al.is_delete = 0 AND al.is_cancel = 0
WHERE a.application_no = @application_no AND a.is_delete = 0 AND a.is_cancel = 0;

-- C01-1 费用汇总守恒：差额必须为 0；首笔款必须大于 0。
-- 仅商业险产品的 other_fee 为未融资交强险/附加险/车船税，会一并进入首笔款，属于正常逻辑。
SELECT al.application_no, al.loan_period, al.initial_payment_ratio,
       al.tech_service_fee_ratio, al.repayment_type,
       al.initial_payment, al.operation_service_fee, al.channel_service_fee, al.other_fee,
       al.first_payment,
       al.first_payment - (
         COALESCE(al.initial_payment,0)
         + COALESCE(al.operation_service_fee,0)
         + COALESCE(al.channel_service_fee,0)
         + COALESCE(al.other_fee,0)
       ) AS first_payment_diff_must_be_0
FROM scf_self_order.application_loan al
WHERE al.application_no = @application_no AND al.is_delete = 0 AND al.is_cancel = 0;

-- 必须值：loan_period=9、initial_payment_ratio=0.1、tech_service_fee_ratio=0.005、repayment_type=4。

-- C01-1A 保司与车辆材料完整性：提交进件前检查。
-- insurance_company_required_fields_present 必须为 1；选择线上盖章时，vci_file_count 必须等于 vehicle_count。
SELECT aic.application_no,
       CASE WHEN aic.company_name IS NOT NULL AND aic.company_name<>''
                  AND aic.receiving_account IS NOT NULL AND aic.receiving_account<>''
                  AND aic.receiving_bank IS NOT NULL AND aic.receiving_bank<>''
                  AND aic.cnaps IS NOT NULL AND aic.cnaps<>''
                  AND aic.payment_summary IS NOT NULL AND aic.payment_summary<>''
                  AND aic.sign_mode IS NOT NULL
            THEN 1 ELSE 0 END AS insurance_company_required_fields_present,
       aic.sign_mode,
       COUNT(av.id) AS vehicle_count,
       SUM(CASE WHEN av.original_vci_file IS NOT NULL AND av.original_vci_file<>'' THEN 1 ELSE 0 END) AS vci_file_count,
       SUM(CASE WHEN av.vci_amount IS NOT NULL AND av.vci_amount>0 THEN 1 ELSE 0 END) AS positive_vci_amount_count
FROM scf_self_order.application_insurance_company aic
LEFT JOIN scf_self_order.application_vehicle av
  ON av.application_no=aic.application_no AND av.is_delete=0 AND av.is_cancel=0
WHERE aic.application_no=@application_no AND aic.is_delete=0 AND aic.is_cancel=0
GROUP BY aic.application_no, aic.company_name, aic.receiving_account,
         aic.receiving_bank, aic.cnaps, aic.payment_summary, aic.sign_mode;

-- C01-2 签约流程：BINDING_CARD 和每辆车的 BINDING_SUCCESS 最终都应为 SIGN_SUCCESS。
SELECT cp.biz_source_code, cp.scene_code, cp.contract_process_code,
       cp.sign_status, cp.create_time, cp.modify_time
FROM scf_self_order.contract_process cp
WHERE (cp.biz_source_code = @application_no
       OR cp.biz_source_code LIKE CONCAT(@application_no, '#%'))
  AND cp.scene_code IN ('BINDING_CARD','BINDING_SUCCESS')
  AND cp.is_delete = 0 AND cp.is_cancel = 0
ORDER BY cp.id;

-- C01-3 绑卡：申请/确认成功后，目标渠道必须出现 JDPAY 有效协议（status=1）。
SELECT application_no, mer_id, cid, status, card_type,
       CASE WHEN agree_id IS NOT NULL AND agree_id <> '' THEN 1 ELSE 0 END AS agree_id_present,
       CASE WHEN platform_agree_id IS NOT NULL AND platform_agree_id <> '' THEN 1 ELSE 0 END AS platform_agree_id_present,
       create_time
FROM scf_self_payment.card_binding
WHERE application_no = @application_no
  AND mer_id = 'HZF000133'
  AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C01-4 产品实例仅作观察，不是本链路的强制放行条件。
-- master 在无 product_instance 时会回退当前产品配置，并用 application_loan 的期数/对客费率覆盖。
SELECT pi.id, pi.application_no, pi.prod_code, pi.product_id,
       pi.status, pi.type, pi.repayment_type,
       COUNT(pid.id) AS instance_override_count
FROM scf_self_order.product_instance pi
LEFT JOIN scf_self_order.product_instance_detail pid
  ON pid.product_instance_id = pi.id
 AND pid.is_delete = 0 AND pid.is_cancel = 0
WHERE pi.application_no = @application_no
  AND pi.is_delete = 0 AND pi.is_cancel = 0
GROUP BY pi.id, pi.application_no, pi.prod_code, pi.product_id,
         pi.status, pi.type, pi.repayment_type;

-- C02 缴费单：生成后应有 1 条；支付成功时 status=4。
-- status=4 且 notify=0 表示支付已成功、但还没成功通知订单系统；定时任务最多重试 5 次。
SELECT serial_no, application_no, status, project_no, product_code,
       total_amount, paid_amount, unpaid_amount, payment_type,
       notify, retry_count, fail_msg, create_time, finish_time
FROM scf_self_saps.payment_slip
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C02-1 缴费单与费用科目必须守恒：三个差额都必须为 0。
-- 未支付时：total=unpaid、paid=0；支付完成时：total=paid、unpaid=0。
SELECT ps.serial_no,
       ps.total_amount,
       COALESCE(SUM(d.total_amount),0) AS detail_total_amount,
       ps.total_amount - COALESCE(SUM(d.total_amount),0) AS total_diff_must_be_0,
       ps.paid_amount - COALESCE(SUM(d.paid_amount),0) AS paid_diff_must_be_0,
       ps.unpaid_amount - COALESCE(SUM(d.unpaid_amount),0) AS unpaid_diff_must_be_0
FROM scf_self_saps.payment_slip ps
LEFT JOIN scf_self_saps.payment_slip_fee_detail d
  ON d.serial_no = ps.serial_no AND d.is_delete = 0 AND d.is_cancel = 0
WHERE ps.application_no = @application_no
  AND ps.is_delete = 0 AND ps.is_cancel = 0
GROUP BY ps.serial_no, ps.total_amount, ps.paid_amount, ps.unpaid_amount;

-- C03 放款单：成功基准 status=351、payment_time 非空。
SELECT application_no, loan_application_no, status, loan_period,
       cooperation_code, funder_code, payment_time, sign_status,
       financing_status, create_time
FROM scf_self_order.loan_order
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C03-1 放款支付明细：payment_result=2 表示处理中；超过10分钟时继续执行 C03-2 定位 SAPS 转账。
SELECT lo.application_no, lo.loan_application_no, lo.status AS loan_status,
       lp.payment_channel, lp.payment_result, lp.payment_amt,
       lp.payment_time, lp.reason, lp.create_time, lp.modify_time
FROM scf_self_order.loan_order lo
LEFT JOIN scf_self_order.loan_payment lp
  ON lp.loan_application_no=lo.loan_application_no AND lp.is_delete=0 AND lp.is_cancel=0
WHERE lo.application_no=@application_no AND lo.is_delete=0 AND lo.is_cancel=0
ORDER BY lp.id;

-- C03-2 SAPS/支付系统代付状态：处理中超过5分钟时，按 transfer_no 单笔触发 SAPS transferTransactionSchedule。
-- XXL-Job 原始任务参数：{"transferNos":["这里填 transfer_no"]}。禁止再次申请放款。
SELECT lo.application_no, lo.loan_application_no, lo.status AS loan_status,
       lp.pay_apply_no, lp.payment_result AS order_payment_result,
       tt.transfer_no, tt.payment_req_id, tt.status AS saps_transfer_status,
       tt.real_retry_count, tt.running_status, tt.instance_status,
       ob.serial_no AS payment_serial_no, ob.status AS payment_order_status,
       ob.channel_id AS payment_channel, ob.create_time AS payment_create_time,
       ob.modify_time AS payment_modify_time
FROM scf_self_order.loan_order lo
LEFT JOIN scf_self_order.loan_payment lp
  ON lp.loan_application_no=lo.loan_application_no AND lp.is_delete=0 AND lp.is_cancel=0
LEFT JOIN scf_self_saps.transfer_transaction tt
  ON tt.business_no=lo.application_no AND tt.transfer_type=10
 AND tt.order_id=lp.pay_apply_no AND tt.is_delete=0 AND tt.is_cancel=0
LEFT JOIN scf_self_payment.order_business ob
  ON ob.biz_serial_no=tt.payment_req_id AND ob.cooperation_code=tt.cooperation_code
 AND ob.is_delete=0 AND ob.is_cancel=0
WHERE lo.application_no=@application_no AND lo.is_delete=0 AND lo.is_cancel=0
ORDER BY tt.id, ob.id;

-- C04 客户账单：9 期产品应生成 9 条。
SELECT application_no, project_code, product_code, total_period,
       COUNT(*) AS bill_count, MIN(create_time) AS first_bill_time
FROM scf_self_saps.customer_bill
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
GROUP BY application_no, project_code, product_code, total_period;

-- C04-1 每一期客户账单与客户科目明细必须守恒；应返回 9 行，detail_diff_must_be_0 全为 0。
-- 目标为商业险-only，但正式计划若包含非商业险本金，DEF/10102 也必须能生成客户明细。
SELECT cb.period, cb.bill_no, cb.total_amount,
       COALESCE(SUM(d.total_amount),0) AS customer_detail_total,
       cb.total_amount-COALESCE(SUM(d.total_amount),0) AS detail_diff_must_be_0,
       GROUP_CONCAT(CONCAT(d.subject_no,':',d.total_amount) ORDER BY d.subject_no) AS customer_details
FROM scf_self_saps.customer_bill cb
LEFT JOIN scf_self_saps.customer_bill_fee_detail d
  ON d.bill_no=cb.bill_no AND d.is_delete=0 AND d.is_cancel=0
WHERE cb.application_no=@application_no AND cb.is_delete=0 AND cb.is_cancel=0
GROUP BY cb.id,cb.period,cb.bill_no,cb.total_amount
ORDER BY cb.period;

-- C05 正式还款计划/推送状态诊断。
-- 正常应有 9 条主计划；推送成功后 status=1、is_lock=0。
-- 若放款成功后存在 status=0、is_lock=1，表示待推送或上次推送失败。
SELECT loan_id, application_no, total_period, category, status, is_lock,
       COUNT(*) AS plan_count, MIN(create_time) AS first_plan_time
FROM scf_self_order.repayment_plan
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
GROUP BY loan_id, application_no, total_period, category, status, is_lock
ORDER BY category, status, is_lock;

-- C06 还款计划汇总主表应有 1 条，total_period=9。
SELECT loan_id, application_no, project_no, product_code, total_period,
       create_time
FROM scf_self_order.loan_repayment_plan
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C07 全链路单行摘要：便于快速判断卡在哪一层。
SELECT a.application_no,
       a.status AS application_status,
       al.loan_period,
       al.first_payment_status,
       al.first_payment_slip_no,
       ps.status AS payment_slip_status,
       ps.notify AS payment_slip_notify,
       ps.retry_count AS payment_slip_retry_count,
       lo.loan_application_no,
       lo.status AS loan_status,
       lo.payment_time,
       (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
        WHERE rp.application_no=a.application_no AND rp.category=1
          AND rp.is_delete=0 AND rp.is_cancel=0) AS main_plan_count,
       (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no
          AND cb.is_delete=0 AND cb.is_cancel=0) AS customer_bill_count
FROM scf_self_order.amp_application a
LEFT JOIN scf_self_order.application_loan al
  ON al.application_no=a.application_no AND al.is_delete=0 AND al.is_cancel=0
LEFT JOIN scf_self_saps.payment_slip ps
  ON ps.application_no=a.application_no AND ps.is_delete=0 AND ps.is_cancel=0
LEFT JOIN scf_self_order.loan_order lo
  ON lo.application_no=a.application_no AND lo.is_delete=0 AND lo.is_cancel=0
WHERE a.application_no=@application_no AND a.is_delete=0 AND a.is_cancel=0
ORDER BY ps.id DESC, lo.id DESC
LIMIT 1;

-- C08 最终验收门禁：PASS 才表示“新渠道进件→缴费成功通知→放款成功→9期客户账单”完整通过。
-- WAIT 表示链路尚未走完；FAIL 表示已出现终态冲突、非目标编码或期数/账单结构异常。
SELECT CASE
  WHEN a.application_no IS NULL THEN 'FAIL'
  WHEN a.project_no<>'XXT-PRJ-YW-A' OR a.product_code<>'PRD-YW-A001' OR a.partner_code<>'HZF000133' THEN 'FAIL'
  WHEN al.loan_period IS NOT NULL AND al.loan_period<>9 THEN 'FAIL'
  WHEN ps.status IS NOT NULL AND ps.status IN (3,5) THEN 'FAIL'
  WHEN lo.status IS NOT NULL AND lo.status IN ('320','350','352','355','360','361') THEN 'FAIL'
  WHEN (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
        WHERE rp.application_no=a.application_no AND rp.category=1
          AND rp.is_delete=0 AND rp.is_cancel=0)>0
       AND NOT ((SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
                 WHERE rp.application_no=a.application_no AND rp.category=1
                   AND rp.total_period=9 AND rp.period BETWEEN 1 AND 9
                   AND rp.is_delete=0 AND rp.is_cancel=0)=9
                AND (SELECT COUNT(DISTINCT rp.period) FROM scf_self_order.repayment_plan rp
                     WHERE rp.application_no=a.application_no AND rp.category=1
                       AND rp.is_delete=0 AND rp.is_cancel=0)=9) THEN 'FAIL'
  WHEN (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no AND cb.is_delete=0 AND cb.is_cancel=0)>0
       AND NOT ((SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
                 WHERE cb.application_no=a.application_no
                   AND cb.project_code='XXT-PRJ-YW-A' AND cb.product_code='PRD-YW-A001'
                   AND cb.total_period=9 AND cb.period BETWEEN 1 AND 9
                   AND cb.is_delete=0 AND cb.is_cancel=0)=9
                AND (SELECT COUNT(DISTINCT cb.period) FROM scf_self_saps.customer_bill cb
                     WHERE cb.application_no=a.application_no
                       AND cb.is_delete=0 AND cb.is_cancel=0)=9) THEN 'FAIL'
  WHEN (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no AND cb.is_delete=0 AND cb.is_cancel=0)>0
       AND EXISTS (SELECT 1
                   FROM scf_self_saps.customer_bill cb
                   WHERE cb.application_no=a.application_no
                     AND cb.is_delete=0 AND cb.is_cancel=0
                     AND cb.total_amount<>(SELECT COALESCE(SUM(d.total_amount),0)
                                          FROM scf_self_saps.customer_bill_fee_detail d
                                          WHERE d.bill_no=cb.bill_no
                                            AND d.is_delete=0 AND d.is_cancel=0)) THEN 'FAIL'
  WHEN a.status='351'
       AND al.loan_period=9 AND al.initial_payment_ratio=0.1
       AND al.tech_service_fee_ratio=0.005 AND al.repayment_type=4
       AND ps.status=4 AND ps.notify=1
       AND ps.total_amount=ps.paid_amount AND ps.unpaid_amount=0
       AND lo.status='351' AND lo.loan_period=9 AND lo.payment_time IS NOT NULL
       AND (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
            WHERE rp.application_no=a.application_no AND rp.category=1
              AND rp.total_period=9 AND rp.period BETWEEN 1 AND 9
              AND rp.status='1' AND rp.is_lock='0'
              AND rp.is_delete=0 AND rp.is_cancel=0)=9
       AND (SELECT COUNT(DISTINCT rp.period) FROM scf_self_order.repayment_plan rp
            WHERE rp.application_no=a.application_no AND rp.category=1
              AND rp.is_delete=0 AND rp.is_cancel=0)=9
       AND (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
            WHERE cb.application_no=a.application_no
              AND cb.project_code='XXT-PRJ-YW-A' AND cb.product_code='PRD-YW-A001'
              AND cb.total_period=9 AND cb.period BETWEEN 1 AND 9
              AND cb.is_delete=0 AND cb.is_cancel=0)=9
       AND (SELECT COUNT(DISTINCT cb.period) FROM scf_self_saps.customer_bill cb
            WHERE cb.application_no=a.application_no
              AND cb.is_delete=0 AND cb.is_cancel=0)=9
       AND (SELECT COUNT(*)
            FROM scf_self_saps.customer_bill cb
            WHERE cb.application_no=a.application_no
              AND cb.is_delete=0 AND cb.is_cancel=0
              AND cb.total_amount=(SELECT COALESCE(SUM(d.total_amount),0)
                                   FROM scf_self_saps.customer_bill_fee_detail d
                                   WHERE d.bill_no=cb.bill_no
                                     AND d.is_delete=0 AND d.is_cancel=0))=9
    THEN 'PASS'
  ELSE 'WAIT'
END AS end_to_end_result,
       input.application_no, a.status AS application_status,
       al.loan_period, al.first_payment_slip_no,
       ps.status AS payment_slip_status, ps.notify AS payment_slip_notify,
       lo.status AS loan_status,
       (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
        WHERE rp.application_no=a.application_no AND rp.category=1
          AND rp.is_delete=0 AND rp.is_cancel=0) AS main_plan_count,
       (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no
          AND cb.is_delete=0 AND cb.is_cancel=0) AS customer_bill_count
FROM (SELECT @application_no AS application_no) input
LEFT JOIN scf_self_order.amp_application a
  ON a.application_no=input.application_no AND a.is_delete=0 AND a.is_cancel=0
LEFT JOIN scf_self_order.application_loan al
  ON al.application_no=a.application_no AND al.is_delete=0 AND al.is_cancel=0
LEFT JOIN scf_self_saps.payment_slip ps
  ON ps.id=(SELECT MAX(ps2.id) FROM scf_self_saps.payment_slip ps2
            WHERE ps2.application_no=a.application_no AND ps2.is_delete=0 AND ps2.is_cancel=0)
LEFT JOIN scf_self_order.loan_order lo
  ON lo.id=(SELECT MAX(lo2.id) FROM scf_self_order.loan_order lo2
            WHERE lo2.application_no=a.application_no AND lo2.is_delete=0 AND lo2.is_cancel=0)
;
