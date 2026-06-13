-- 支付商户用途继续优化：
-- 1. 退款主户合并为退款：REFUND_MAIN_ACCOUNT -> REFUND
-- 2. 签约和绑卡合并为银行卡签约：SIGN_AGREEMENT -> BIND_CARD
--
-- 执行前建议先备份 pay_merchant_purpose、pay_project_merchant_binding、
-- pay_project_merchant_binding_purpose、pay_project_merchant_binding_relation。

-- 执行前核查：旧用途在三张用途相关表中的分布。
SELECT purpose_code, COUNT(*) AS row_count
FROM pay_merchant_purpose
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT')
GROUP BY purpose_code;

SELECT purpose_code, COUNT(*) AS row_count
FROM pay_project_merchant_binding_purpose
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT')
GROUP BY purpose_code;

SELECT purpose_code, binding_status, COUNT(*) AS row_count
FROM pay_project_merchant_binding
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT')
GROUP BY purpose_code, binding_status;

-- 商户支持用途：先补目标用途，避免唯一键冲突，再删除旧用途。
INSERT INTO pay_merchant_purpose (merchant_id, purpose_code)
SELECT DISTINCT source.merchant_id, 'REFUND'
FROM pay_merchant_purpose source
LEFT JOIN pay_merchant_purpose target
  ON target.merchant_id = source.merchant_id
 AND target.purpose_code = 'REFUND'
WHERE source.purpose_code = 'REFUND_MAIN_ACCOUNT'
  AND target.id IS NULL;

INSERT INTO pay_merchant_purpose (merchant_id, purpose_code)
SELECT DISTINCT source.merchant_id, 'BIND_CARD'
FROM pay_merchant_purpose source
LEFT JOIN pay_merchant_purpose target
  ON target.merchant_id = source.merchant_id
 AND target.purpose_code = 'BIND_CARD'
WHERE source.purpose_code = 'SIGN_AGREEMENT'
  AND target.id IS NULL;

DELETE FROM pay_merchant_purpose
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT');

-- 绑定用途子表：先补目标用途，避免唯一键冲突，再删除旧用途。
INSERT INTO pay_project_merchant_binding_purpose (binding_id, purpose_code)
SELECT DISTINCT source.binding_id, 'REFUND'
FROM pay_project_merchant_binding_purpose source
LEFT JOIN pay_project_merchant_binding_purpose target
  ON target.binding_id = source.binding_id
 AND target.purpose_code = 'REFUND'
WHERE source.purpose_code = 'REFUND_MAIN_ACCOUNT'
  AND target.id IS NULL;

INSERT INTO pay_project_merchant_binding_purpose (binding_id, purpose_code)
SELECT DISTINCT source.binding_id, 'BIND_CARD'
FROM pay_project_merchant_binding_purpose source
LEFT JOIN pay_project_merchant_binding_purpose target
  ON target.binding_id = source.binding_id
 AND target.purpose_code = 'BIND_CARD'
WHERE source.purpose_code = 'SIGN_AGREEMENT'
  AND target.id IS NULL;

DELETE FROM pay_project_merchant_binding_purpose
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT');

-- 绑定主表：无唯一键冲突的主用途直接改名。
UPDATE pay_project_merchant_binding source
LEFT JOIN pay_project_merchant_binding target
  ON target.project_id = source.project_id
 AND target.merchant_id = source.merchant_id
 AND target.purpose_code = 'REFUND'
 AND target.id <> source.id
SET source.purpose_code = 'REFUND'
WHERE source.purpose_code = 'REFUND_MAIN_ACCOUNT'
  AND target.id IS NULL;

UPDATE pay_project_merchant_binding source
LEFT JOIN pay_project_merchant_binding target
  ON target.project_id = source.project_id
 AND target.merchant_id = source.merchant_id
 AND target.purpose_code = 'BIND_CARD'
 AND target.id <> source.id
SET source.purpose_code = 'BIND_CARD'
WHERE source.purpose_code = 'SIGN_AGREEMENT'
  AND target.id IS NULL;

-- 主表仍保留旧用途的记录，说明存在合并唯一键冲突。
-- 不做硬删除，避免丢失关联商户关系；先停用并标备注，后续人工复核是否迁移或删除。
UPDATE pay_project_merchant_binding
SET binding_status = 'INACTIVE',
    remark = LEFT(
      CASE
        WHEN remark IS NULL OR remark = '' THEN '商户用途优化：退款主户/签约旧用途已合并，绑定已停用待复核'
        WHEN remark LIKE '%商户用途优化：退款主户/签约旧用途已合并%' THEN remark
        ELSE CONCAT(remark, '；商户用途优化：退款主户/签约旧用途已合并，绑定已停用待复核')
      END,
      255
    )
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT');

-- 执行后核查：以下查询应不再返回商户支持用途或绑定用途子表记录；
-- 主绑定表如仍返回记录，应全部为 INACTIVE，表示需要人工复核的历史绑定。
SELECT purpose_code, COUNT(*) AS row_count
FROM pay_merchant_purpose
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT')
GROUP BY purpose_code;

SELECT purpose_code, COUNT(*) AS row_count
FROM pay_project_merchant_binding_purpose
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT')
GROUP BY purpose_code;

SELECT purpose_code, binding_status, COUNT(*) AS row_count
FROM pay_project_merchant_binding
WHERE purpose_code IN ('REFUND_MAIN_ACCOUNT', 'SIGN_AGREEMENT')
GROUP BY purpose_code, binding_status;
