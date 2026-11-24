INSERT INTO lease_saps.customer_order
  (tenant_code,order_no,source_order_no,customer_code,customer_name,transaction_type,
   order_desc,sub_type,transaction_mode,sub_mode,channel,pay_amount,transaction_amount,
   recharge_amount,settle_amount,platform_fee,channel_fee,advance_fee,earn_fee,status,
   order_time,priority,product_code,product_type,terminal_type,create_by,create_time,
   version,is_delete,is_cancel,remark)
SELECT tenant_code,'BD261124144012900226','HX261123104055000001',customer_code,customer_name,2,
       '滞纳金减免',2000,4,40,8,2.68,2.68,0,2.68,0,0,0,0,4,
       NOW(),2,product_code,product_type,10,'shihao',NOW(),1,0,0,
       CONCAT('FIX-HX226-20261124',' 原核销减免漏执行补偿；无现金收款')
FROM lease_saps.customer_bill
WHERE id=1993 AND tenant_code='XXT' AND version=16
  AND bill_no='C2511211024450013' AND customer_code='CO218213744'
  AND total_amount=1342.48 AND paid_amount=1339.80 AND unpaid_amount=2.68
  AND settlement_status=1 AND settlement_time IS NULL AND verification_status=2
  AND is_delete=0 AND is_cancel=0
  AND (SELECT COUNT(*) FROM lease_saps.customer_bill_pay_subject_detail
       WHERE tenant_code='XXT' AND bill_no='C2511211024450013'
         AND order_no='OR261123104304000001' AND version=1
         AND is_delete=0 AND is_cancel=0
         AND ((id=4486 AND subject_no='103' AND paid_amount=2.68)
           OR (id=4487 AND subject_no='101' AND paid_amount=1337.12)))=2
  AND (SELECT COUNT(*) FROM lease_saps.customer_transaction
       WHERE tenant_code='XXT' AND goods_no='C2511211024450013' AND id>65326)=4
  AND NOT EXISTS (SELECT 1 FROM lease_saps.customer_transaction
       WHERE tenant_code='XXT' AND goods_no='C2511211024450013'
         AND id>65326 AND (status IS NULL OR status<>3 OR settle_amount IS NULL OR settle_amount<>0))
  AND NOT EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
       WHERE tenant_code='XXT' AND bill_no='C2511211024450013'
         AND is_delete=0 AND is_cancel=0 AND id<>1710)
  AND NOT EXISTS (SELECT 1 FROM lease_saps.customer_order WHERE order_no='BD261124144012900226');

INSERT INTO lease_saps.customer_transaction
  (tenant_code,order_no,serial_no,source_serial_no,transaction_name,pay_amount,
   transaction_amount,recharge_amount,settle_amount,status,goods_no,send_date,
   create_by,create_time,version,is_delete,is_cancel,remark)
SELECT 'XXT','BD261124144012900226','261124144012900227','HX261123104055000001','滞纳金减免',
       2.68,2.68,0,2.68,4,'C2511211024450013',DATE(NOW()),
       'shihao',NOW(),1,0,0,'FIX-HX226-20261124'
WHERE EXISTS (SELECT 1 FROM lease_saps.customer_order
    WHERE tenant_code='XXT' AND order_no='BD261124144012900226'
      AND source_order_no='HX261123104055000001' AND customer_code='CO218213744'
      AND transaction_mode=4 AND sub_mode=40 AND status=4 AND settle_amount=2.68)
  AND NOT EXISTS (SELECT 1 FROM lease_saps.customer_transaction
    WHERE serial_no='261124144012900227' OR order_no='BD261124144012900226');

INSERT INTO lease_saps.customer_bill_pay_detail
  (tenant_code,bill_no,serial_no,order_no,trans_no,channel,sub_mode,pay_amount,fee_amount,
   overdue_day,settlement_time,operate_time,account_time,is_reverse,
   create_by,create_time,version,is_delete,is_cancel,remark)
SELECT tenant_code,bill_no,'BP261124144012900228','BD261124144012900226','261124144012900227',8,40,2.68,0,
       overdue_day,NOW(),NOW(),NOW(),0,
       'shihao',NOW(),1,0,0,'FIX-HX226-20261124'
FROM lease_saps.customer_bill
WHERE id=1993 AND tenant_code='XXT' AND version=16
  AND EXISTS (SELECT 1 FROM lease_saps.customer_transaction
    WHERE tenant_code='XXT' AND order_no='BD261124144012900226' AND serial_no='261124144012900227'
      AND goods_no='C2511211024450013' AND status=4 AND settle_amount=2.68)
  AND NOT EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
    WHERE serial_no='BP261124144012900228' OR order_no='BD261124144012900226' OR trans_no='261124144012900227');

INSERT INTO lease_saps.customer_bill_pay_subject_detail
  (tenant_code,bill_no,order_no,trans_no,pay_detail_serial_no,subject_no,subject_name,
   paid_amount,priority,create_by,create_time,version,is_delete,is_cancel,remark)
SELECT tenant_code,bill_no,'BD261124144012900226','261124144012900227','BP261124144012900228',subject_no,subject_name,
       2.68,priority,'shihao',NOW(),1,0,0,'FIX-HX226-20261124'
FROM lease_saps.bill_fee_detail
WHERE id=12318 AND tenant_code='XXT' AND subject_no='103'
  AND total_amount=2.68 AND paid_amount=2.68 AND unpaid_amount=0 AND version=4
  AND EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
    WHERE tenant_code='XXT' AND serial_no='BP261124144012900228' AND order_no='BD261124144012900226'
      AND trans_no='261124144012900227' AND bill_no='C2511211024450013' AND sub_mode=40 AND pay_amount=2.68)
  AND NOT EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_subject_detail
    WHERE pay_detail_serial_no='BP261124144012900228' OR order_no='BD261124144012900226' OR trans_no='261124144012900227');

UPDATE lease_saps.customer_bill_pay_subject_detail
SET paid_amount=0,version=version+1,modify_by='shihao',modify_time=NOW(),
    summary=CONCAT('原2.68元实收改分配本金；减免流水 ','BD261124144012900226')
WHERE id=4486 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND order_no='OR261123104304000001' AND subject_no='103' AND paid_amount=2.68
  AND version=1 AND is_delete=0 AND is_cancel=0
  AND EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
    WHERE tenant_code='XXT' AND serial_no='BP261124144012900228' AND order_no='BD261124144012900226'
      AND trans_no='261124144012900227' AND bill_no='C2511211024450013' AND sub_mode=40 AND pay_amount=2.68);

UPDATE lease_saps.customer_bill_pay_subject_detail
SET paid_amount=1339.80,version=version+1,modify_by='shihao',modify_time=NOW(),
    summary='FIX-HX226-20261124：补回原误分配滞纳金的实收2.68元'
WHERE id=4487 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND order_no='OR261123104304000001' AND subject_no='101' AND paid_amount=1337.12
  AND version=1 AND is_delete=0 AND is_cancel=0
  AND EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
    WHERE tenant_code='XXT' AND serial_no='BP261124144012900228' AND order_no='BD261124144012900226'
      AND trans_no='261124144012900227' AND bill_no='C2511211024450013' AND sub_mode=40 AND pay_amount=2.68);

UPDATE lease_saps.bill_fee_detail
SET paid_amount=1339.80,unpaid_amount=0,version=version+1,
    modify_by='shihao',modify_time=NOW()
WHERE id=5821 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND subject_no='101' AND total_amount=1339.80 AND paid_amount=1337.12 AND unpaid_amount=2.68
  AND version=2 AND is_delete=0 AND is_cancel=0
  AND EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
    WHERE tenant_code='XXT' AND serial_no='BP261124144012900228' AND order_no='BD261124144012900226'
      AND trans_no='261124144012900227' AND bill_no='C2511211024450013' AND sub_mode=40 AND pay_amount=2.68);

UPDATE lease_saps.customer_bill
SET paid_amount=1342.48,unpaid_amount=0,settlement_status=1,settlement_time=NOW(),
    verification_status=1,verification_time=NOW(),verification_person='shihao',
    payment_mode=20,version=version+1,modify_by='shihao',modify_time=NOW()
WHERE id=1993 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND total_amount=1342.48 AND paid_amount=1339.80 AND unpaid_amount=2.68
  AND settlement_status=1 AND settlement_time IS NULL AND verification_status=2
  AND version=16 AND is_delete=0 AND is_cancel=0
  AND EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
    WHERE tenant_code='XXT' AND serial_no='BP261124144012900228' AND order_no='BD261124144012900226'
      AND trans_no='261124144012900227' AND bill_no='C2511211024450013' AND sub_mode=40 AND pay_amount=2.68);

UPDATE lease_finance.asset_order
SET unpaid_rent=46893.00,unpaid_rent_discount_interest=46893.00,
    version=version+1,modify_by='shihao',modify_time=NOW()
WHERE id=62 AND tenant_code='XXT' AND application_code='AP251111000001'
  AND customer_code='CO218213744' AND unpaid_rent=48232.80
  AND unpaid_rent_discount_interest=48232.80 AND subsidy_amount=0
  AND version=12 AND is_delete=0 AND is_cancel=0
  AND EXISTS (SELECT 1 FROM lease_saps.customer_bill_pay_detail
    WHERE tenant_code='XXT' AND serial_no='BP261124144012900228' AND order_no='BD261124144012900226'
      AND trans_no='261124144012900227' AND bill_no='C2511211024450013' AND sub_mode=40 AND pay_amount=2.68)
  AND (SELECT COUNT(*)=48 AND SUM(d.unpaid_amount)=46893.00
    FROM lease_saps.customer_bill b JOIN lease_saps.bill_fee_detail d ON b.bill_no=d.bill_no
    WHERE b.is_delete=0 AND d.is_delete=0 AND b.tenant_code='XXT'
      AND b.application_no='AP251111000001' AND d.subject_no='101');
