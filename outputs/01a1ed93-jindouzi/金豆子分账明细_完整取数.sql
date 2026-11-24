SELECT ob.id,ob.serial_no,MAX(rp.customer_name) customer_name,ob.project_code,ob.product_code,ob.channel_id,MAX(rp.loan_id) loan_id,MAX(rp.loan_time) loan_time,ob.transaction_time,ob.transaction_amount,MAX(CASE WHEN a.source_user IN ('G0001','G0002') THEN a.order_amount ELSE 0 END) guarantee_amount,MAX(CASE WHEN a.source_user='P0001' THEN a.order_amount ELSE 0 END) partner_amount,ob.channel_fee capital_fee,MAX(CASE WHEN a.source_user IN ('G0001','G0002') THEN a.channel_fee ELSE 0 END) guarantee_fee,MAX(CASE WHEN a.source_user='P0001' THEN a.channel_fee ELSE 0 END) partner_fee,MAX(oc.transaction_mode) transaction_mode,ob.complete_time
FROM amp_payment.order_business ob
JOIN amp_saps.repayment_plan_pay_detail rppd ON rppd.pay_serial_no=ob.serial_no AND rppd.is_delete=0
JOIN amp_saps.repayment_plan rp ON rp.bill_no=rppd.bill_no AND rp.is_delete=0 AND rp.is_cancel=0
JOIN amp_payment.order_business_asinfo_original a ON a.order_business_id=ob.id AND a.source_user IN ('G0001','G0002','P0001') AND a.target_user='C0001' AND a.is_delete=0 AND a.is_cancel=0
JOIN amp_payment.order_channel oc ON oc.order_business_id=ob.id AND oc.status=4 AND oc.is_delete=0 AND oc.is_cancel=0
WHERE ob.cooperation_code='HZF000137' AND ob.status=4 AND ob.is_delete=0 AND ob.is_cancel=0 AND ob.share_user_type=2 AND ob.order_date>='2026-09-29' AND ob.order_date<'2026-11-18'
GROUP BY ob.id,ob.serial_no,ob.project_code,ob.product_code,ob.channel_id,ob.transaction_time,ob.transaction_amount,ob.channel_fee,ob.complete_time
ORDER BY ob.transaction_time,ob.id
LIMIT 2000;
