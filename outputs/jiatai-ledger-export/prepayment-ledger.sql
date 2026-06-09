SELECT
  rp.project_name AS '项目',
  rp.product_code AS '产品编号',
  rp.cooperation_code AS '合作方编号',
  rp.loan_id AS '放款申请单号',
  rp.customer_code AS '用户编号',
  rp.customer_name AS '用户名称',
  CASE rp.interest_subsidy_mode
    WHEN 1 THEN '商户全额贴息'
    WHEN 2 THEN '客户付息'
    WHEN 3 THEN '商户部分贴息（付息）'
    WHEN 4 THEN '商户部分贴息（贴息）'
    ELSE NULL
  END AS '付息方式',
  rp.bill_no AS '用户账单编号',
  rp.application_no AS '进件申请单号',
  rp.pay_channel_serial_no AS '代扣渠道流水',
  rp.period AS '当前期数',
  rp.total_period AS '总期数',
  rp.total_amount AS '应还总金额',
  rp.paid_amount AS '已还总金额',
  rp.unpaid_amount AS '未还总金额',
  rp.principal AS '应还本金',
  rp.unpaid_principal AS '未还本金',
  rp.interest AS '应还利息',
  rp.unpaid_interest AS '未还利息',
  rp.service_fee AS '应还保理手续费',
  rp.unpaid_service_fee AS '未还保理手续费',
  rp.guarantor_fee AS '应还担保费',
  rp.unpaid_guarantor_fee AS '未还担保费',
  rp.overdue_fee AS '应还罚息',
  rp.unpaid_overdue_fee AS '未还罚息',
  rp.advance_fee AS '应付提前结清手续费',
  rp.unpaid_advance_fee AS '未付提前结清手续费',
  CASE
    WHEN rp.interest_subsidy_mode = 1 AND rp.original_service_fee IS NOT NULL
      THEN COALESCE(rp.principal, 0) - COALESCE(rp.original_service_fee, 0)
    ELSE COALESCE(rp.principal, 0)
  END AS '实际资方本金',
  GREATEST(
    CASE
      WHEN rp.interest_subsidy_mode = 1 AND rp.original_service_fee IS NOT NULL
        THEN COALESCE(rp.original_service_fee, 0)
      WHEN rp.bill_status IN (400, 401) AND COALESCE(rp.service_fee, 0) <> 0
        THEN COALESCE(rp.service_fee, 0)
      WHEN rp.interest_subsidy_mode = 2
        THEN COALESCE(rp.service_fee, 0)
      ELSE 0
    END,
    0
  ) AS '实际资方服务费',
  CASE
    WHEN rp.original_service_fee IS NULL THEN 0
    ELSE COALESCE(rp.original_service_fee, 0) - GREATEST(
      CASE
        WHEN rp.interest_subsidy_mode = 1 AND rp.original_service_fee IS NOT NULL
          THEN COALESCE(rp.original_service_fee, 0)
        WHEN rp.bill_status IN (400, 401) AND COALESCE(rp.service_fee, 0) <> 0
          THEN COALESCE(rp.service_fee, 0)
        WHEN rp.interest_subsidy_mode = 2
          THEN COALESCE(rp.service_fee, 0)
        ELSE 0
      END,
      0
    )
  END AS '应冲资方服务费',
  rp.start_date AS '起息日',
  rp.last_pay_date AS '最晚还款日',
  rp.last_grace_date AS '宽限期到期日',
  CASE rp.bill_status
    WHEN 50 THEN '未还款'
    WHEN 100 THEN '正常还款'
    WHEN 201 THEN '提前结清'
    WHEN 400 THEN '退费'
    WHEN 401 THEN '七天无理由退费'
    WHEN 500 THEN '待代偿'
    WHEN 501 THEN '代偿'
    WHEN 600 THEN '待回购'
    WHEN 601 THEN '回购'
    ELSE NULL
  END AS '账单状态',
  CASE rp.settlement_status
    WHEN 0 THEN '未结算'
    WHEN 1 THEN '已结清'
    WHEN 2 THEN '未结清'
    ELSE NULL
  END AS '结算状态',
  rp.settlement_time AS '结算时间',
  CASE rp.customer_settlement_status
    WHEN 0 THEN '未结算'
    WHEN 1 THEN '已结清'
    WHEN 2 THEN '未结清'
    ELSE NULL
  END AS '用户实际还款状态',
  rp.customer_settlement_time AS '用户实际还款时间',
  rp.overdue_day AS '逾期天数(系统)',
  rp.customer_overdue_day AS '逾期天数（合作方）',
  rp.red_amount AS '对账单红冲金额',
  rp.original_service_fee AS '原始保理手续费',
  ol.assets_tag AS '资产标签'
FROM jiatai_amp_saps.repayment_plan_report rp
LEFT JOIN jiatai_amp_saps.loan_order ol
  ON rp.application_no = ol.application_no
WHERE rp.is_delete = 0
  AND rp.is_cancel = 0
  AND ol.is_delete = 0
  AND rp.settlement_time >= '2026-04-27 00:00:00'
  AND rp.settlement_time <= '2026-05-29 23:59:59'
ORDER BY rp.loan_id DESC, rp.period ASC
