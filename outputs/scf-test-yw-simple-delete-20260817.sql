-- 保费分期测试环境简版硬删除
-- 目标：XXT-PRJ-YW-A / PRD-YW-A001 / HZF000133
-- 注意：不删除申请单、融资单、账单、支付流水和外部统一认证账号。

START TRANSACTION;

-- 合同配置。
DELETE s
FROM scf_self_order.contract_config_signers s
JOIN scf_self_order.contract_config c ON c.id = s.contract_config_id
WHERE c.project_code = 'XXT-PRJ-YW-A';

DELETE FROM scf_self_order.contract_config
WHERE project_code = 'XXT-PRJ-YW-A';

-- 项目配置。
DELETE FROM scf_self_order.project_attachment_config
WHERE project_no = 'XXT-PRJ-YW-A';

DELETE FROM scf_self_order.project_config
WHERE project_id = 'XXT-PRJ-YW-A'
   OR project_no = 'XXT-PRJ-YW-A';

-- 产品配置及产品。
DELETE FROM scf_self_order.product_detail
WHERE product_id IN (
    SELECT id FROM scf_self_order.amp_product WHERE code = 'PRD-YW-A001'
);

DELETE FROM scf_self_order.product_detail_group
WHERE product_id IN (
    SELECT id FROM scf_self_order.amp_product WHERE code = 'PRD-YW-A001'
);

DELETE FROM scf_self_order.amp_product
WHERE code = 'PRD-YW-A001';

-- 渠道直接关系及渠道。
DELETE FROM scf_self_order.org_config
WHERE org_code = 'HZF000133';

-- 配置复制脚本新增的支付/SAPS 项；保留 JDPAY 路由、商户参数和协议支付费率。
DELETE FROM scf_self_payment.business_config
WHERE query_key = 'HZF000133'
  AND query_type = 'COOPERATION'
  AND group_key = 'PaymentConfig'
  AND config_key = 'pay_channel';

DELETE FROM scf_self_payment.channel_fee_config
WHERE merchant_no = 'HZF000133'
  AND channel_code = 'BCM'
  AND transaction_mode = 'SINGLE_REMIT';

DELETE FROM scf_self_saps.business_config
WHERE query_key IN ('HZF000133', 'XXT-PRJ-YW-A');

DELETE FROM scf_self_order.crm_channel_emp
WHERE channel_code = 'HZF000133';

DELETE FROM scf_self_order.crm_channel_enterprise
WHERE channel_code = 'HZF000133';

DELETE FROM scf_self_order.crm_channel_individual
WHERE channel_code = 'HZF000133';

DELETE FROM scf_self_order.crm_channel
WHERE channel_code = 'HZF000133';

-- 项目主数据。
DELETE FROM scf_self_order.amp_project
WHERE no = 'XXT-PRJ-YW-A';

COMMIT;
