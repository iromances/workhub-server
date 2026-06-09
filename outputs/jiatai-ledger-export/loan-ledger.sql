SELECT
  a.loan_application_no AS '放款申请单号',
  a.project_name AS '项目',
  a.payment_time AS '放款时间',
  a.start_date AS '起租日',
  a.end_date AS '到期日',
  b.to_acc_no AS '放款收款账户账号',
  b.to_cnaps AS '联行号',
  b.to_acc_name AS '放款收款账户名称',
  b.to_acc_dept AS '放款收款方开户行名称',
  a.refactoring_contract_no AS '合同编号',
  a.other_info ->> '$.entrustedGuaranteeAgreementNo' AS '委托担保合同编号',
  a.name AS '客户姓名',
  CASE a.model
    WHEN '1' THEN '商户全额贴息'
    WHEN '2' THEN '客户付息'
    WHEN '3' THEN '商户部分贴息（付息）'
    WHEN '4' THEN '商户部分贴息（贴息）'
    ELSE '/'
  END AS '付息方式',
  a.id_card_no AS '客户身份证号',
  a.other_info ->> '$.livingAddress' AS '地址',
  b.trans_money AS '放款金额(元)',
  a.other_info ->> '$.monthRepay' AS '月还款额(元)',
  a.loan_period AS '期数（月）',
  COALESCE(ps.total_amount, 0) AS '应还总金额(元)',
  COALESCE(ps.unpaid_total_amount, 0) AS '未还总金额(元)',
  COALESCE(fs.principal, 0) AS '应还本金(元)',
  COALESCE(fs.unpaid_principal, 0) AS '未还本金(元)',
  COALESCE(fs.interest, 0) AS '应还利息(元)',
  COALESCE(fs.unpaid_interest, 0) AS '未还利息(元)',
  COALESCE(fs.funder_total_amount, 0) AS '应还资方总金额(元)',
  COALESCE(fs.service_fee, 0) AS '应还保理手续费(元)',
  a.service_fee AS '原始保理手续费(元)',
  COALESCE(fs.unpaid_service_fee, 0) AS '未还保理手续费(元)',
  CASE
    WHEN a.model = '1' THEN COALESCE(fs.principal, 0) - COALESCE(a.service_fee, 0)
    WHEN a.model = '2' THEN COALESCE(fs.principal, 0)
    ELSE COALESCE(fs.principal, 0)
  END AS '实际资方本金(元)',
  a.service_fee AS '原始资方服务费(元)',
  a.guarantee_fee AS '原始担保费',
  CASE COALESCE(ps.bill_status, a.loan_after_status)
    WHEN 100 THEN '结清'
    WHEN 200 THEN '正常'
    WHEN 400 THEN '退款'
    WHEN 600 THEN '回购'
    ELSE '/'
  END AS '还款状态',
  a.assets_tag AS '资产标签'
FROM jiatai_amp_capital.loan_order a
JOIN jiatai_amp_capital.loan_recaccount b
  ON a.loan_application_no = b.loan_application_no
LEFT JOIN (
  SELECT
    rp.application_no,
    SUM(COALESCE(rp.total_amount, 0)) AS total_amount,
    SUM(COALESCE(rp.unpaid_amount, 0)) AS unpaid_total_amount,
    MAX(COALESCE(rp.overdue_day, 0)) AS max_overdue_day,
    CASE
      WHEN SUM(CASE WHEN rp.bill_status IN (600, 601) THEN 1 ELSE 0 END) > 0 THEN 600
      WHEN SUM(CASE WHEN rp.bill_status IN (400, 401) THEN 1 ELSE 0 END) > 0 THEN 400
      WHEN SUM(CASE WHEN rp.customer_settlement_status <> 1 OR rp.customer_settlement_status IS NULL THEN 1 ELSE 0 END) = 0 THEN 100
      ELSE 200
    END AS bill_status
  FROM jiatai_amp_saps.repayment_plan rp
  WHERE rp.is_delete = 0
    AND rp.is_cancel = 0
  GROUP BY rp.application_no
) ps
  ON ps.application_no = a.application_no
LEFT JOIN (
  SELECT
    rp.application_no,
    SUM(CASE WHEN bfd.subject_no = 'P0001' THEN COALESCE(bfd.total_amount, 0) ELSE 0 END) AS principal,
    SUM(CASE WHEN bfd.subject_no = 'P0001' THEN COALESCE(bfd.paid_amount, 0) ELSE 0 END) AS paid_principal,
    SUM(CASE WHEN bfd.subject_no = 'P0001' THEN COALESCE(bfd.unpaid_amount, 0) ELSE 0 END) AS unpaid_principal,
    SUM(CASE WHEN bfd.subject_no = 'P0003' THEN COALESCE(bfd.total_amount, 0) ELSE 0 END) AS service_fee,
    SUM(CASE WHEN bfd.subject_no = 'P0003' THEN COALESCE(bfd.paid_amount, 0) ELSE 0 END) AS paid_service_fee,
    SUM(CASE WHEN bfd.subject_no = 'P0003' THEN COALESCE(bfd.unpaid_amount, 0) ELSE 0 END) AS unpaid_service_fee,
    SUM(CASE WHEN bfd.subject_no IN ('P0003', 'P0004', 'P0005', 'P0006', 'P0008', 'P0009') THEN COALESCE(bfd.total_amount, 0) ELSE 0 END) AS interest,
    SUM(CASE WHEN bfd.subject_no IN ('P0003', 'P0004', 'P0005', 'P0006', 'P0008', 'P0009') THEN COALESCE(bfd.unpaid_amount, 0) ELSE 0 END) AS unpaid_interest,
    SUM(CASE WHEN bfd.subject_no IN ('P0001', 'P0003') THEN COALESCE(bfd.total_amount, 0) ELSE 0 END) AS funder_total_amount,
    SUM(CASE WHEN bfd.subject_no IN ('P0001', 'P0003') THEN COALESCE(bfd.paid_amount, 0) ELSE 0 END) AS funder_paid_amount,
    SUM(CASE WHEN bfd.subject_no IN ('P0001', 'P0003') THEN COALESCE(bfd.unpaid_amount, 0) ELSE 0 END) AS funder_unpaid_amount
  FROM jiatai_amp_saps.repayment_plan rp
  JOIN jiatai_amp_saps.bill_fee_detail bfd ON rp.bill_no = bfd.bill_no
  WHERE rp.is_delete = 0
    AND rp.is_cancel = 0
    AND bfd.is_delete = 0
    AND bfd.is_cancel = 0
  GROUP BY rp.application_no
) fs
  ON fs.application_no = a.application_no
WHERE a.is_delete = 0
  AND a.is_cancel = 0
  AND a.status = '351'
  AND b.is_delete = 0
  AND b.is_cancel = 0
ORDER BY a.payment_time ASC
