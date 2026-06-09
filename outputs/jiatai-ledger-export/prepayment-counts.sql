SELECT
  CASE rp.bill_status
    WHEN 100 THEN '正常还款'
    WHEN 201 THEN '提前结清'
    WHEN 400 THEN '退费'
    WHEN 401 THEN '七天无理由退费'
    WHEN 501 THEN '已代偿'
    WHEN 601 THEN '已回购'
    ELSE CONCAT('未知-', rp.bill_status)
  END AS bill_status,
  COUNT(*) AS rows_count,
  MIN(rp.settlement_time) AS min_settlement_time,
  MAX(rp.settlement_time) AS max_settlement_time
FROM jiatai_amp_saps.repayment_plan_report rp
LEFT JOIN jiatai_amp_saps.loan_order ol ON rp.application_no = ol.application_no
WHERE rp.is_delete = 0
  AND rp.is_cancel = 0
  AND ol.is_delete = 0
  AND rp.settlement_time >= '2026-04-27 00:00:00'
  AND rp.settlement_time <= '2026-05-29 23:59:59'
GROUP BY bill_status
ORDER BY rows_count DESC
