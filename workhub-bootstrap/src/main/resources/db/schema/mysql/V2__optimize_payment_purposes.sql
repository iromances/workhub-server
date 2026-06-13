-- 支付商户用途优化：
-- 1. 代付合并为转账：PAY_OUT -> TRANSFER
-- 2. 分账、先行通被分账商户、里易被分账商户合并为代收分账：
--    SPLIT_SETTLEMENT / SPLIT_RECEIVER_XXT / SPLIT_RECEIVER_LIYI -> WITHHOLD_SPLIT_SETTLEMENT
-- 3. 删除不再维护的配置用途：
--    QUERY_ORDER / VERIFY_ACCOUNT / UNBIND_CARD / RECHARGE / DOWNLOAD_RECON / CALLBACK_VERIFY / BALANCE_QUERY
--
-- 执行前建议先备份 pay_merchant_purpose、pay_project_merchant_binding、
-- pay_project_merchant_binding_purpose、pay_project_merchant_binding_relation。

-- 执行前核查：旧用途在三张用途相关表中的分布。
SELECT purpose_code, COUNT(*) AS row_count
FROM pay_merchant_purpose
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
)
GROUP BY purpose_code;

SELECT purpose_code, COUNT(*) AS row_count
FROM pay_project_merchant_binding_purpose
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
)
GROUP BY purpose_code;

SELECT purpose_code, binding_status, COUNT(*) AS row_count
FROM pay_project_merchant_binding
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
)
GROUP BY purpose_code, binding_status;

-- 商户支持用途：先补目标用途，避免唯一键冲突，再删除旧用途。
INSERT INTO pay_merchant_purpose (merchant_id, purpose_code)
SELECT DISTINCT source.merchant_id, 'TRANSFER'
FROM pay_merchant_purpose source
LEFT JOIN pay_merchant_purpose target
  ON target.merchant_id = source.merchant_id
 AND target.purpose_code = 'TRANSFER'
WHERE source.purpose_code = 'PAY_OUT'
  AND target.id IS NULL;

INSERT INTO pay_merchant_purpose (merchant_id, purpose_code)
SELECT DISTINCT source.merchant_id, 'WITHHOLD_SPLIT_SETTLEMENT'
FROM pay_merchant_purpose source
LEFT JOIN pay_merchant_purpose target
  ON target.merchant_id = source.merchant_id
 AND target.purpose_code = 'WITHHOLD_SPLIT_SETTLEMENT'
WHERE source.purpose_code IN ('SPLIT_SETTLEMENT', 'SPLIT_RECEIVER_XXT', 'SPLIT_RECEIVER_LIYI')
  AND target.id IS NULL;

DELETE FROM pay_merchant_purpose
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
);

-- 绑定用途子表：先补目标用途，避免唯一键冲突，再删除旧用途。
INSERT INTO pay_project_merchant_binding_purpose (binding_id, purpose_code)
SELECT DISTINCT source.binding_id, 'TRANSFER'
FROM pay_project_merchant_binding_purpose source
LEFT JOIN pay_project_merchant_binding_purpose target
  ON target.binding_id = source.binding_id
 AND target.purpose_code = 'TRANSFER'
WHERE source.purpose_code = 'PAY_OUT'
  AND target.id IS NULL;

INSERT INTO pay_project_merchant_binding_purpose (binding_id, purpose_code)
SELECT DISTINCT source.binding_id, 'WITHHOLD_SPLIT_SETTLEMENT'
FROM pay_project_merchant_binding_purpose source
LEFT JOIN pay_project_merchant_binding_purpose target
  ON target.binding_id = source.binding_id
 AND target.purpose_code = 'WITHHOLD_SPLIT_SETTLEMENT'
WHERE source.purpose_code IN ('SPLIT_SETTLEMENT', 'SPLIT_RECEIVER_XXT', 'SPLIT_RECEIVER_LIYI')
  AND target.id IS NULL;

DELETE FROM pay_project_merchant_binding_purpose
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
);

-- 绑定主表：无唯一键冲突的主用途直接改名。
UPDATE pay_project_merchant_binding source
LEFT JOIN pay_project_merchant_binding target
  ON target.project_id = source.project_id
 AND target.merchant_id = source.merchant_id
 AND target.purpose_code = 'TRANSFER'
 AND target.id <> source.id
SET source.purpose_code = 'TRANSFER'
WHERE source.purpose_code = 'PAY_OUT'
  AND target.id IS NULL;

UPDATE pay_project_merchant_binding source
LEFT JOIN pay_project_merchant_binding target
  ON target.project_id = source.project_id
 AND target.merchant_id = source.merchant_id
 AND target.purpose_code = 'WITHHOLD_SPLIT_SETTLEMENT'
 AND target.id <> source.id
SET source.purpose_code = 'WITHHOLD_SPLIT_SETTLEMENT'
WHERE source.purpose_code IN ('SPLIT_SETTLEMENT', 'SPLIT_RECEIVER_XXT', 'SPLIT_RECEIVER_LIYI')
  AND target.id IS NULL;

-- 主表仍保留旧用途的记录，说明存在合并唯一键冲突或该用途已废弃。
-- 不做硬删除，避免丢失关联商户关系；先停用并标备注，后续人工复核是否迁移或删除。
UPDATE pay_project_merchant_binding
SET binding_status = 'INACTIVE',
    remark = LEFT(
      CASE
        WHEN remark IS NULL OR remark = '' THEN '商户用途优化：旧用途已合并或删除，绑定已停用待复核'
        WHEN remark LIKE '%商户用途优化：旧用途已合并或删除%' THEN remark
        ELSE CONCAT(remark, '；商户用途优化：旧用途已合并或删除，绑定已停用待复核')
      END,
      255
    )
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
);

-- 执行后核查：以下查询应不再返回商户支持用途或绑定用途子表记录；
-- 主绑定表如仍返回记录，应全部为 INACTIVE，表示需要人工复核的历史绑定。
SELECT purpose_code, COUNT(*) AS row_count
FROM pay_merchant_purpose
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
)
GROUP BY purpose_code;

SELECT purpose_code, COUNT(*) AS row_count
FROM pay_project_merchant_binding_purpose
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
)
GROUP BY purpose_code;

SELECT purpose_code, binding_status, COUNT(*) AS row_count
FROM pay_project_merchant_binding
WHERE purpose_code IN (
  'PAY_OUT',
  'SPLIT_SETTLEMENT',
  'SPLIT_RECEIVER_XXT',
  'SPLIT_RECEIVER_LIYI',
  'QUERY_ORDER',
  'VERIFY_ACCOUNT',
  'UNBIND_CARD',
  'RECHARGE',
  'DOWNLOAD_RECON',
  'CALLBACK_VERIFY',
  'BALANCE_QUERY'
)
GROUP BY purpose_code, binding_status;
