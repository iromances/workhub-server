INSERT INTO lease_saps.customer_order
  (tenant_code,order_no,source_order_no,customer_code,customer_name,transaction_type,
   order_desc,sub_type,transaction_mode,sub_mode,channel,pay_amount,transaction_amount,
   recharge_amount,settle_amount,platform_fee,channel_fee,advance_fee,earn_fee,status,
   order_time,priority,product_code,product_type,terminal_type,create_by,create_time,
   version,is_delete,is_cancel,remark)
VALUES
  ('XXT','BD261124144012900226','HX261123104055000001','CO218213744','曹来源',2,
       '滞纳金减免',2000,4,40,8,2.68,2.68,0,2.68,0,0,0,0,4,
       NOW(),2,'PRJ25011001000224',2,10,'shihao',NOW(),1,0,0,
       'FIX-HX226-20261124 原核销减免漏执行补偿；无现金收款');

INSERT INTO lease_saps.customer_transaction
  (tenant_code,order_no,serial_no,source_serial_no,transaction_name,pay_amount,
   transaction_amount,recharge_amount,settle_amount,status,goods_no,send_date,
   create_by,create_time,version,is_delete,is_cancel,remark)
VALUES
  ('XXT','BD261124144012900226','261124144012900227','HX261123104055000001','滞纳金减免',
       2.68,2.68,0,2.68,4,'C2511211024450013',DATE(NOW()),
       'shihao',NOW(),1,0,0,'FIX-HX226-20261124');

INSERT INTO lease_saps.customer_bill_pay_detail
  (tenant_code,bill_no,serial_no,order_no,trans_no,channel,sub_mode,pay_amount,fee_amount,
   overdue_day,settlement_time,operate_time,account_time,is_reverse,
   create_by,create_time,version,is_delete,is_cancel,remark)
VALUES
  ('XXT','C2511211024450013','BP261124144012900228','BD261124144012900226','261124144012900227',8,40,2.68,0,
       3,NOW(),NOW(),NOW(),0,
       'shihao',NOW(),1,0,0,'FIX-HX226-20261124');

INSERT INTO lease_saps.customer_bill_pay_subject_detail
  (tenant_code,bill_no,order_no,trans_no,pay_detail_serial_no,subject_no,subject_name,
   paid_amount,priority,create_by,create_time,version,is_delete,is_cancel,remark)
VALUES
  ('XXT','C2511211024450013','BD261124144012900226','261124144012900227','BP261124144012900228','103','滞纳金',
       2.68,10,'shihao',NOW(),1,0,0,'FIX-HX226-20261124');

UPDATE lease_saps.customer_bill_pay_subject_detail
SET paid_amount=0,version=version+1,modify_by='shihao',modify_time=NOW(),
    summary='原2.68元实收改分配本金；减免流水 BD261124144012900226'
WHERE id=4486 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND order_no='OR261123104304000001' AND subject_no='103' AND paid_amount=2.68
  AND version=1 AND is_delete=0 AND is_cancel=0;

UPDATE lease_saps.customer_bill_pay_subject_detail
SET paid_amount=1339.80,version=version+1,modify_by='shihao',modify_time=NOW(),
    summary='FIX-HX226-20261124：补回原误分配滞纳金的实收2.68元'
WHERE id=4487 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND order_no='OR261123104304000001' AND subject_no='101' AND paid_amount=1337.12
  AND version=1 AND is_delete=0 AND is_cancel=0;

UPDATE lease_saps.bill_fee_detail
SET paid_amount=1339.80,unpaid_amount=0,version=version+1,
    modify_by='shihao',modify_time=NOW()
WHERE id=5821 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND subject_no='101' AND total_amount=1339.80 AND paid_amount=1337.12 AND unpaid_amount=2.68
  AND version=2 AND is_delete=0 AND is_cancel=0;

UPDATE lease_saps.customer_bill
SET paid_amount=1342.48,unpaid_amount=0,settlement_status=1,settlement_time=NOW(),
    verification_status=1,verification_time=NOW(),verification_person='shihao',
    payment_mode=20,version=version+1,modify_by='shihao',modify_time=NOW()
WHERE id=1993 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND total_amount=1342.48 AND paid_amount=1339.80 AND unpaid_amount=2.68
  AND settlement_status=1 AND settlement_time IS NULL AND verification_status=2
  AND version=16 AND is_delete=0 AND is_cancel=0;

UPDATE lease_finance.asset_order
SET unpaid_rent=46893.00,unpaid_rent_discount_interest=46893.00,
    version=version+1,modify_by='shihao',modify_time=NOW()
WHERE id=62 AND tenant_code='XXT' AND application_code='AP251111000001'
  AND customer_code='CO218213744' AND unpaid_rent=48232.80
  AND unpaid_rent_discount_interest=48232.80 AND subsidy_amount=0
  AND version=12 AND is_delete=0 AND is_cancel=0;
