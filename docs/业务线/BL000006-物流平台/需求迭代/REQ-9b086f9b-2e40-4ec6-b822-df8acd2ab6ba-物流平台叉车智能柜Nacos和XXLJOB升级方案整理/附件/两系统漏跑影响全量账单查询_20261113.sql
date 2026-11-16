-- 两系统 2026-11-12 / 2026-11-13 漏跑影响账单：全量候选回溯
-- 更正：不按 bill_fee_record / 补跑成功记录选人，不排除后来已结清的账单。
-- 前两条：当前有效账单的历史候选回溯，按系统一笔账单一行。
-- 叉车父任务16名义触发时间03:00:10；智能柜父任务72名义触发时间00:01:00（北京时间）。
-- 子任务由父任务完成后触发，因此上述时点是回溯基准，不是缺失运行的实际启动时间。
-- 每天分别判断：账单当时已创建、最后宽限日早于业务日期、当时尚未结清。
-- “当时尚未结清”按当前非已结清，或结清时间晚于回溯基准判断。
-- affected_20261112 / affected_20261113：1=该日候选；affected_days：两天候选次数。
-- 不以当下 unpaid_amount、overdue_day、是否补算、是否产生正费用作为入选条件。
-- 当前金额和状态仅作展示，不能直接当作漏跑当天的应收、未还本金或应补逾期费。
-- 此处是任务候选范围；历史锁定、展期、项目免计费等规则仍决定当日是否实际计费。
--
-- 已只读验证主清单：
-- 叉车：12日60、13日60，去重60，当前已结清0。
-- 智能柜：12日894、13日890，去重900，当前已结清13。
-- 两系统：12日954、13日950，去重960。
--
-- 历史完整性边界：
-- 当前主表无法完整重建历史宽限日调整、曾结清后冲正/重开等变化。
-- 另发现叉车31、智能柜53笔当前已删除的潜在候选，删除时间均为空，
-- 无法确认其在漏跑当天是否仍有效；不能将其直接算作确认受影响，也不能静默丢弃。
-- 后两条独立返回这些历史删除待核对项，应凭审计记录/历史快照确认，不与主清单混算。
-- 960是“当前有效账单按可用时间字段回溯”的确认查询结果，不是已证明无缺口的历史快照总数。
-- 两日父任务触发后的秒级结清边界、结清回填等也需结合原业务流水复核。
-- 查询只读，无LIMIT；客户端如限制结果行数，请调整显示/导出上限。

-- 1. 叉车：当前有效账单回溯（包含后来已结清）
SELECT q.*,
       q.affected_20261112 + q.affected_20261113 AS affected_days
FROM (
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
        b.create_time AS bill_created_at,
        b.last_pay_date,
        b.last_grace_date,
        b.settlement_status AS current_settlement_status,
        b.settlement_time AS current_settlement_time,
        b.overdue_day AS current_overdue_days,
        b.total_amount AS current_total_amount,
        b.paid_amount AS current_paid_amount,
        b.unpaid_amount AS current_unpaid_amount,
        CASE WHEN b.last_grace_date < '2026-11-12'
             AND b.create_time <= '2026-11-12 03:00:10'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-12 03:00:10')
        THEN 1 ELSE 0 END AS affected_20261112,
        CASE WHEN b.last_grace_date < '2026-11-13'
             AND b.create_time <= '2026-11-13 03:00:10'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-13 03:00:10')
        THEN 1 ELSE 0 END AS affected_20261113
    FROM lease_saps.customer_bill b
    WHERE b.is_delete = 0
      AND b.last_grace_date < '2026-11-13'
      AND b.create_time <= '2026-11-13 03:00:10'
) q
WHERE q.affected_20261112 = 1 OR q.affected_20261113 = 1
ORDER BY q.current_settlement_status DESC, q.bill_no;

-- 2. 智能柜：当前有效账单回溯（包含后来已结清）
SELECT q.*,
       q.affected_20261112 + q.affected_20261113 AS affected_days
FROM (
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
        b.create_time AS bill_created_at,
        b.last_pay_date,
        b.last_grace_date,
        b.settlement_status AS current_settlement_status,
        b.settlement_time AS current_settlement_time,
        b.overdue_day AS current_overdue_days,
        b.total_amount AS current_total_amount,
        b.paid_amount AS current_paid_amount,
        b.unpaid_amount AS current_unpaid_amount,
        CASE WHEN b.last_grace_date < '2026-11-12'
             AND b.create_time <= '2026-11-12 00:01:00'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-12 00:01:00')
        THEN 1 ELSE 0 END AS affected_20261112,
        CASE WHEN b.last_grace_date < '2026-11-13'
             AND b.create_time <= '2026-11-13 00:01:00'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-13 00:01:00')
        THEN 1 ELSE 0 END AS affected_20261113
    FROM ol_saps.customer_bill b
    WHERE b.is_delete = 0
      AND b.last_grace_date < '2026-11-13'
      AND b.create_time <= '2026-11-13 00:01:00'
) q
WHERE q.affected_20261112 = 1 OR q.affected_20261113 = 1
ORDER BY q.current_settlement_status DESC, q.bill_no;

-- 3. 叉车：历史删除时间缺失/较晚的待核对项，非确认受影响总数
SELECT q.*,
       q.affected_20261112 + q.affected_20261113 AS affected_days
FROM (
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
        b.create_time AS bill_created_at,
        b.delete_time,
        b.modify_time,
        b.is_delete,
        b.last_pay_date,
        b.last_grace_date,
        b.settlement_status AS current_settlement_status,
        b.settlement_time AS current_settlement_time,
        b.overdue_day AS current_overdue_days,
        b.total_amount AS current_total_amount,
        b.paid_amount AS current_paid_amount,
        b.unpaid_amount AS current_unpaid_amount,
        CASE WHEN b.last_grace_date < '2026-11-12'
             AND b.create_time <= '2026-11-12 03:00:10'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-12 03:00:10')
        THEN 1 ELSE 0 END AS affected_20261112,
        CASE WHEN b.last_grace_date < '2026-11-13'
             AND b.create_time <= '2026-11-13 03:00:10'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-13 03:00:10')
        THEN 1 ELSE 0 END AS affected_20261113
    FROM lease_saps.customer_bill b
    WHERE b.is_delete = 1
      AND (b.delete_time IS NULL OR b.delete_time >= '2026-11-12 03:00:10')
      AND b.last_grace_date < '2026-11-13'
      AND b.create_time <= '2026-11-13 03:00:10'
) q
WHERE q.affected_20261112 = 1 OR q.affected_20261113 = 1
ORDER BY q.bill_no;

-- 4. 智能柜：历史删除时间缺失/较晚的待核对项，非确认受影响总数
SELECT q.*,
       q.affected_20261112 + q.affected_20261113 AS affected_days
FROM (
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
        b.create_time AS bill_created_at,
        b.delete_time,
        b.modify_time,
        b.is_delete,
        b.last_pay_date,
        b.last_grace_date,
        b.settlement_status AS current_settlement_status,
        b.settlement_time AS current_settlement_time,
        b.overdue_day AS current_overdue_days,
        b.total_amount AS current_total_amount,
        b.paid_amount AS current_paid_amount,
        b.unpaid_amount AS current_unpaid_amount,
        CASE WHEN b.last_grace_date < '2026-11-12'
             AND b.create_time <= '2026-11-12 00:01:00'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-12 00:01:00')
        THEN 1 ELSE 0 END AS affected_20261112,
        CASE WHEN b.last_grace_date < '2026-11-13'
             AND b.create_time <= '2026-11-13 00:01:00'
             AND (b.settlement_status <> 1 OR b.settlement_time > '2026-11-13 00:01:00')
        THEN 1 ELSE 0 END AS affected_20261113
    FROM ol_saps.customer_bill b
    WHERE b.is_delete = 1
      AND (b.delete_time IS NULL OR b.delete_time >= '2026-11-12 00:01:00')
      AND b.last_grace_date < '2026-11-13'
      AND b.create_time <= '2026-11-13 00:01:00'
) q
WHERE q.affected_20261112 = 1 OR q.affected_20261113 = 1
ORDER BY q.bill_no;

