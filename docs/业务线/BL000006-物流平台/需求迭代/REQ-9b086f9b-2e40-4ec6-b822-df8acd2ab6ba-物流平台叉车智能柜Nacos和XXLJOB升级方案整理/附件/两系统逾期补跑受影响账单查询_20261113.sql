-- 注意：本文件仅查询实际补跑成功账单，不是漏跑影响全量清单。
-- 全量需求请改用同目录 两系统漏跑影响全量账单查询_20261113.sql，包含后来已结清及独立历史删除核对项。
-- 2026-11-12、2026-11-13 Job 漏跑受影响账单：实际补算范围
-- 只读 SELECT；两条语句分别执行，各返回一张账单明细表。
-- 生产库：叉车 lease_saps；智能柜 ol_saps。
-- 以已验收的补跑日期、创建时间窗口及科目定位，不扫描所有历史逾期账单。
-- 每个系统一笔账单一行；先合并费用分录，再关联账单，避免资方/平台科目导致重复。
-- 保留叉车7笔零新增费用账单；不包含用户决定不再处理的13笔已结清账单。
-- 不再按账单当前结清状态过滤，以保留补跑后发生还款的原受影响账单。
-- current_* 和账单展示字段反映查询时状态；fee_* / total_added_fee 仅为本次两天补算金额。
-- 金额单位：元；各时间为生产库北京时间。
-- 此次只读校验：叉车60笔 / 415.78元，智能柜887笔 / 993.36元，合计947笔 / 1409.14元。
-- fee_record_count 为科目分录数；recalculated_days 为补算日期数，均不是账单数。
-- 本文件不含 LIMIT，以便查询完整明细；若客户端限制结果行数，请调整客户端显示/导出上限。

-- 叉车（60笔，415.78元）
SELECT
    b.id AS bill_id,
    b.bill_no,
    b.customer_code,
    b.customer_name,
    b.application_no,
    b.financial_contract_no,
    b.product_code,
    b.product_type,
    b.period,
    b.total_period,
    b.last_pay_date,
    b.last_grace_date,
    b.overdue_day AS current_overdue_days,
    b.settlement_status AS current_settlement_status,
    b.settlement_time AS current_settlement_time,
    b.total_amount AS current_total_amount,
    b.paid_amount AS current_paid_amount,
    b.unpaid_amount AS current_unpaid_amount,
    f.fee_20261112,
    f.fee_20261113,
    f.total_added_fee,
    f.recalculated_days,
    f.fee_record_count,
    f.first_record_time,
    f.last_record_time
FROM lease_saps.customer_bill b
JOIN (
    SELECT
        bill_no,
        SUM(CASE WHEN end_date = '2026-11-12' THEN fee_amount ELSE 0 END) AS fee_20261112,
        SUM(CASE WHEN end_date = '2026-11-13' THEN fee_amount ELSE 0 END) AS fee_20261113,
        SUM(fee_amount) AS total_added_fee,
        COUNT(DISTINCT end_date) AS recalculated_days,
        COUNT(*) AS fee_record_count,
        MIN(create_time) AS first_record_time,
        MAX(create_time) AS last_record_time
    FROM lease_saps.bill_fee_record
    WHERE is_delete = 0
      AND is_cancel = 0
      AND subject_no IN ('103', '10301', '10302')
      AND (
          (start_date = '2026-11-12' AND end_date = '2026-11-12'
           AND create_time >= '2026-11-13 14:43:54'
           AND create_time < '2026-11-13 14:43:56')
          OR
          (start_date = '2026-11-13' AND end_date = '2026-11-13'
           AND create_time >= '2026-11-13 14:45:40'
           AND create_time < '2026-11-13 14:45:42')
      )
    GROUP BY bill_no
) f ON f.bill_no = b.bill_no
WHERE b.is_delete = 0
ORDER BY f.total_added_fee DESC, b.bill_no;

-- 智能柜（887笔，993.36元）
SELECT
    b.id AS bill_id,
    b.bill_no,
    b.customer_code,
    b.customer_name,
    b.application_no,
    b.financial_contract_no,
    b.product_code,
    b.product_type,
    b.period,
    b.total_period,
    b.last_pay_date,
    b.last_grace_date,
    b.overdue_day AS current_overdue_days,
    b.settlement_status AS current_settlement_status,
    b.settlement_time AS current_settlement_time,
    b.total_amount AS current_total_amount,
    b.paid_amount AS current_paid_amount,
    b.unpaid_amount AS current_unpaid_amount,
    f.fee_20261112,
    f.fee_20261113,
    f.total_added_fee,
    f.recalculated_days,
    f.fee_record_count,
    f.first_record_time,
    f.last_record_time
FROM ol_saps.customer_bill b
JOIN (
    SELECT
        bill_no,
        SUM(CASE WHEN end_date = '2026-11-12' THEN fee_amount ELSE 0 END) AS fee_20261112,
        SUM(CASE WHEN end_date = '2026-11-13' THEN fee_amount ELSE 0 END) AS fee_20261113,
        SUM(fee_amount) AS total_added_fee,
        COUNT(DISTINCT end_date) AS recalculated_days,
        COUNT(*) AS fee_record_count,
        MIN(create_time) AS first_record_time,
        MAX(create_time) AS last_record_time
    FROM ol_saps.bill_fee_record
    WHERE is_delete = 0
      AND is_cancel = 0
      AND subject_no IN ('10301')
      AND (
          (start_date = '2026-11-12' AND end_date = '2026-11-12'
           AND create_time >= '2026-11-13 14:37:38'
           AND create_time < '2026-11-13 14:37:41')
          OR
          (start_date = '2026-11-13' AND end_date = '2026-11-13'
           AND create_time >= '2026-11-13 14:41:11'
           AND create_time < '2026-11-13 14:41:14')
      )
    GROUP BY bill_no
) f ON f.bill_no = b.bill_no
WHERE b.is_delete = 0
ORDER BY f.total_added_fee DESC, b.bill_no;

