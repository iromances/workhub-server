SELECT COUNT(*) AS rows_count
FROM jiatai_amp_capital.loan_order a
JOIN jiatai_amp_capital.loan_recaccount b
  ON a.loan_application_no = b.loan_application_no
WHERE a.is_delete = 0
  AND a.is_cancel = 0
  AND a.status = '351'
  AND b.is_delete = 0
  AND b.is_cancel = 0
