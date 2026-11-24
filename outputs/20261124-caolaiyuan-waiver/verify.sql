-- 只读验收；修正事务内和正式提交后各核验一次。未执行任何数据修正。
-- 正式修正期望：账单 total/paid/unpaid=1342.48/1342.48/0，结清1、核销1、version17。
SELECT id,bill_no,total_amount,paid_amount,unpaid_amount,settlement_status,settlement_time,
       verification_status,verification_time,verification_person,version
FROM lease_saps.customer_bill WHERE id=1993 AND tenant_code='XXT';

-- 101：1339.80/1339.80/0，version3；103：2.68/2.68/0，version4。
SELECT id,subject_no,total_amount,paid_amount,unpaid_amount,version
FROM lease_saps.bill_fee_detail
WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0
ORDER BY id;

-- 原OR订单下103=0、101=1339.80；新增BD减免订单下103=2.68；共3行。
SELECT id,order_no,trans_no,pay_detail_serial_no,subject_no,paid_amount,version,summary,remark
FROM lease_saps.customer_bill_pay_subject_detail
WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0
ORDER BY id;

-- 41=现金1339.80，40=减免2.68；各1行。paid_amount/pay_amount包含非现金减免。
SELECT sub_mode,COUNT(*) AS n,SUM(pay_amount) AS amount
FROM lease_saps.customer_bill_pay_detail
WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0
GROUP BY sub_mode;

-- 原现金成功1339.80 + 新减免成功2.68；不得出现第二笔现金成功或进行中扣款。
SELECT t.id,t.order_no,t.serial_no,t.transaction_amount,t.settle_amount,t.status,
       o.transaction_mode,o.sub_mode,o.source_order_no,t.remark
FROM lease_saps.customer_transaction t
JOIN lease_saps.customer_order o ON o.order_no=t.order_no AND o.tenant_code=t.tenant_code
WHERE t.tenant_code='XXT' AND t.goods_no='C2511211024450013'
  AND t.create_time>='2026-11-23 10:40:00'
ORDER BY t.id;

-- 原审批保持状态1、实收1339.80、已申请减免2.68、version1；不再次审批。
SELECT id,serial_no,status,actual_total_fee,paid_overdue_amount,waiver_late_amount,
       apply_time,auth_time,version
FROM lease_saps.customer_bill_write_off WHERE id=226 AND tenant_code='XXT';

-- 期望48个本金明细：total64310.40 / paid17417.40 / unpaid46893.00。
SELECT COUNT(*) AS n,SUM(d.total_amount) AS total_amount,
       SUM(d.paid_amount) AS paid_amount,SUM(d.unpaid_amount) AS unpaid_amount
FROM lease_saps.customer_bill b JOIN lease_saps.bill_fee_detail d ON b.bill_no=d.bill_no
WHERE b.is_delete=0 AND d.is_delete=0 AND b.tenant_code='XXT'
  AND b.application_no='AP251111000001' AND d.subject_no='101';
-- 期望未还租金和扣贴息后未还租金均46893.00，version13。
SELECT id,asset_order_no,unpaid_rent,unpaid_rent_discount_interest,subsidy_amount,version
FROM lease_finance.asset_order WHERE id=62 AND tenant_code='XXT';

-- 期望0行：各费用科目的结算累计与明细累计相等。
SELECT d.subject_no,d.paid_amount AS fee_paid,COALESCE(p.subject_paid,0) AS detail_paid
FROM lease_saps.bill_fee_detail d
LEFT JOIN (
  SELECT subject_no,SUM(paid_amount) AS subject_paid
  FROM lease_saps.customer_bill_pay_subject_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0
  GROUP BY subject_no
) p ON p.subject_no=d.subject_no
WHERE d.tenant_code='XXT' AND d.bill_no='C2511211024450013'
  AND d.is_delete=0 AND d.is_cancel=0 AND d.paid_amount<>COALESCE(p.subject_paid,0);
