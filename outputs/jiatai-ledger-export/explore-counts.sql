SELECT 'capital.loan_order' AS table_name, COUNT(*) AS rows_count FROM jiatai_amp_capital.loan_order
UNION ALL
SELECT 'capital.loan_recaccount', COUNT(*) FROM jiatai_amp_capital.loan_recaccount
UNION ALL
SELECT 'saps.repayment_plan', COUNT(*) FROM jiatai_amp_saps.repayment_plan
UNION ALL
SELECT 'saps.bill_fee_detail', COUNT(*) FROM jiatai_amp_saps.bill_fee_detail
UNION ALL
SELECT 'saps.repayment_plan_report', COUNT(*) FROM jiatai_amp_saps.repayment_plan_report
UNION ALL
SELECT 'saps.loan_order', COUNT(*) FROM jiatai_amp_saps.loan_order
