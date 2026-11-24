-- 曹来源 C2511211024450013：一键补滞纳金减免2.68元。
-- 文件已准备，未执行。2026-11-24 14:17北京时间复查金额/版本及扣款状态未变。
-- 连接生产MySQL，在独立SQL窗口按“执行脚本”运行整份文件（支持DELIMITER）。
-- 无需替换参数；成功自动COMMIT，校验不符/SQL错误/警告均ROLLBACK并返回ROLLED_BACK。
-- 需要 CREATE ROUTINE / ALTER ROUTINE / EXECUTE 及两库相关表读写权限。
-- 一次性过程执行后DROP，不安装软件、不修改应用代码或配置；过程DDL不能事务回滚。
-- 发布后，在该进件无并发代扣、核销、减免、重算的窗口运行，不要只选中中间部分。
-- 业务DML仍为4条单表INSERT + 5条单表UPDATE；不改变原现金实收1339.80。
-- 专用补偿流水号使用FIX命名空间，避免冒用系统数字序列；不猜自增主键。
-- 修正前快照及最终状态随查询结果返回，请保存执行结果。
-- 看最终status：COMMITTED=成功；ROLLED_BACK=未修正，须看error_message。
-- 执行成功后不要再重审原核销或重复减免。SQL不发送客户结清通知。

DELIMITER $$
CREATE PROCEDURE lease_saps.tmp_fix_hx226_20261124_01a20c2f()
SQL SECURITY INVOKER
BEGIN
  DECLARE v_errno INT DEFAULT 0;
  DECLARE v_state CHAR(5) DEFAULT '00000';
  DECLARE v_message TEXT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION, SQLWARNING
  BEGIN
    GET DIAGNOSTICS CONDITION 1 v_errno=MYSQL_ERRNO, v_state=RETURNED_SQLSTATE,
                               v_message=MESSAGE_TEXT;
    ROLLBACK;
    SELECT 'ROLLED_BACK' AS status, v_errno AS error_number,
           v_state AS sqlstate_code, v_message AS error_message;
  END;

  SET @fix_operator=LEFT(CONCAT('SQL_FIX:',SUBSTRING_INDEX(USER(),'@',1)),50);
  SET @fix_order_no='BD_FIX_HX226_20261124';
  SET @fix_trans_no='TX_FIX_HX226_20261124';
  SET @fix_pay_no='BP_FIX_HX226_20261124';
  SET @repair_time=UTC_TIMESTAMP() + INTERVAL 8 HOUR;
  SET @repair_tag='FIX-HX226-20261124';
  SET @ok=0;
  SET @new_order_id=NULL, @new_trans_id=NULL, @new_pay_id=NULL, @new_subject_id=NULL;

START TRANSACTION;

-- 1. 锁定并保存以下结果作为修正前快照；不创建生产备份表。
-- 进件的其他期次也参与资产汇总，整个进件须处于无并发处理窗口。
SELECT id, bill_no, total_amount, paid_amount, unpaid_amount, version,
       settlement_status, verification_status, is_delete, is_cancel
FROM lease_saps.customer_bill
WHERE tenant_code='XXT' AND application_no='AP251111000001'
ORDER BY id FOR UPDATE;
SELECT d.id, d.bill_no, d.subject_no, d.total_amount, d.paid_amount,
       d.unpaid_amount, d.version, d.is_delete, d.is_cancel
FROM lease_saps.bill_fee_detail d
JOIN lease_saps.customer_bill b ON b.bill_no=d.bill_no
WHERE b.tenant_code='XXT' AND b.application_no='AP251111000001'
  AND (d.subject_no='101' OR b.id=1993)
ORDER BY d.id FOR UPDATE;
SELECT * FROM lease_saps.customer_bill WHERE id=1993 AND tenant_code='XXT' FOR UPDATE;
SELECT * FROM lease_saps.customer_bill_write_off
WHERE tenant_code='XXT' AND bill_no='C2511211024450013' FOR UPDATE;
SELECT * FROM lease_saps.customer_order
WHERE tenant_code='XXT' AND customer_code='CO218213744'
  AND create_time>='2026-11-23 10:40:00' ORDER BY id FOR UPDATE;
SELECT * FROM lease_saps.customer_transaction
WHERE tenant_code='XXT' AND goods_no='C2511211024450013' ORDER BY id FOR UPDATE;
SELECT * FROM lease_saps.customer_bill_pay_detail
WHERE tenant_code='XXT' AND bill_no='C2511211024450013' ORDER BY id FOR UPDATE;
SELECT * FROM lease_saps.customer_bill_pay_subject_detail
WHERE tenant_code='XXT' AND bill_no='C2511211024450013' ORDER BY id FOR UPDATE;
SELECT * FROM lease_saps.bill_derate_apply
WHERE tenant_code='XXT' AND bill_no='C2511211024450013' FOR UPDATE;
SELECT * FROM lease_finance.asset_order WHERE id=62 AND tenant_code='XXT' FOR UPDATE;

-- 2. 前置检查；不符合快照或已修复时，整笔停止并回滚。
SET @ok = 1;

SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_bill
  WHERE id=1993 AND tenant_code='XXT' AND bill_no='C2511211024450013'
    AND customer_code='CO218213744' AND application_no='AP251111000001'
    AND product_type=2 AND period=13 AND total_period=48 AND overdue_day=3
    AND total_amount=1342.48 AND paid_amount=1339.80 AND unpaid_amount=2.68
    AND settlement_status=1 AND settlement_time IS NULL
    AND verification_status=2 AND verification_time='2026-11-23 10:43:04'
    AND payment_mode=20 AND version=16 AND is_delete=0 AND is_cancel=0);

SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_bill_write_off
  WHERE id=226 AND tenant_code='XXT' AND serial_no='HX261123104055000001'
    AND bill_no='C2511211024450013' AND status=1 AND version=1
    AND actual_total_fee=1339.80 AND paid_overdue_amount=0 AND waiver_late_amount=2.68
    AND is_delete=0 AND is_cancel=0);
SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_bill_write_off
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0);
SET @ok = @ok AND (
  SELECT COUNT(*)=0 FROM lease_saps.bill_derate_apply
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013');

SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_order
  WHERE id=65271 AND tenant_code='XXT' AND order_no='OR261123104304000001'
    AND source_order_no='HX261123104055000001' AND customer_code='CO218213744'
    AND transaction_mode=3 AND sub_mode=41 AND status=4 AND version=2
    AND pay_amount=1339.80 AND transaction_amount=1339.80 AND settle_amount=1339.80
    AND is_delete=0 AND is_cancel=0);
SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_transaction
  WHERE id=65326 AND tenant_code='XXT' AND order_no='OR261123104304000001'
    AND serial_no='261123104304000001' AND goods_no='C2511211024450013'
    AND transaction_amount=1339.80 AND settle_amount=1339.80 AND status=4 AND version=2
    AND is_delete=0 AND is_cancel=0);
-- 后续扣款必须全部失败且结算额为 0；任何新增、进行中或成功记录均停止复核。
SET @ok = @ok AND (
  SELECT COUNT(*)=4 AND SUM(IF(status=3 AND settle_amount=0,1,0))=4
  FROM lease_saps.customer_transaction
  WHERE tenant_code='XXT' AND goods_no='C2511211024450013' AND id>65326);

SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_bill_pay_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0);
SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_bill_pay_detail
  WHERE id=1710 AND tenant_code='XXT' AND serial_no='BP261123104304000001'
    AND order_no='OR261123104304000001' AND trans_no='261123104304000001'
    AND pay_amount=1339.80 AND sub_mode=41 AND is_reverse=0 AND version=1
    AND is_delete=0 AND is_cancel=0);
SET @ok = @ok AND (
  SELECT COUNT(*)=2 AND SUM(paid_amount)=1339.80
  FROM lease_saps.customer_bill_pay_subject_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0);
SET @ok = @ok AND (
  SELECT COUNT(*)=2 FROM lease_saps.customer_bill_pay_subject_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013'
    AND order_no='OR261123104304000001' AND trans_no='261123104304000001'
    AND pay_detail_serial_no='BP261123104304000001' AND version=1
    AND is_delete=0 AND is_cancel=0
    AND ((id=4486 AND subject_no='103' AND paid_amount=2.68)
      OR (id=4487 AND subject_no='101' AND paid_amount=1337.12)));
SET @ok = @ok AND (
  SELECT COUNT(*)=2 FROM lease_saps.bill_fee_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0
    AND ((id=5821 AND subject_no='101' AND total_amount=1339.80
          AND paid_amount=1337.12 AND unpaid_amount=2.68 AND version=2)
      OR (id=12318 AND subject_no='103' AND total_amount=2.68
          AND paid_amount=2.68 AND unpaid_amount=0 AND version=4)));
SET @ok = @ok AND (
  SELECT COUNT(*)=5 AND SUM(total_amount)=1342.48
    AND SUM(paid_amount)=1339.80 AND SUM(unpaid_amount)=2.68
  FROM lease_saps.bill_fee_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0);

-- 与 queryApplicationPayinfo 的现有汇总口径一致，不按结清状态过滤。
SET @ok = @ok AND (
  SELECT COUNT(*)=48 AND SUM(d.total_amount)=64310.40
    AND SUM(d.paid_amount)=17414.72 AND SUM(d.unpaid_amount)=46895.68
  FROM lease_saps.customer_bill b JOIN lease_saps.bill_fee_detail d ON b.bill_no=d.bill_no
  WHERE b.is_delete=0 AND d.is_delete=0 AND b.tenant_code='XXT'
    AND b.application_no='AP251111000001' AND d.subject_no='101');
SET @ok = @ok AND (
  SELECT COUNT(*)=1 FROM lease_finance.asset_order
  WHERE id=62 AND tenant_code='XXT' AND application_code='AP251111000001'
    AND customer_code='CO218213744' AND asset_order_no='AO2511210002'
    AND total_rent=64310.40 AND unpaid_rent=48232.80
    AND unpaid_rent_discount_interest=48232.80 AND subsidy_amount=0
    AND version=12 AND is_delete=0 AND is_cancel=0);
-- 包括软删除记录的流水号占用也禁止，避免复用历史流水号。
SET @ok = @ok AND (SELECT COUNT(*)=0 FROM lease_saps.customer_order WHERE order_no=@fix_order_no);
SET @ok = @ok AND (SELECT COUNT(*)=0 FROM lease_saps.customer_transaction
  WHERE serial_no=@fix_trans_no OR order_no=@fix_order_no);
SET @ok = @ok AND (SELECT COUNT(*)=0 FROM lease_saps.customer_bill_pay_detail
  WHERE serial_no=@fix_pay_no OR order_no=@fix_order_no OR trans_no=@fix_trans_no);
SET @ok = @ok AND (SELECT COUNT(*)=0 FROM lease_saps.customer_bill_pay_subject_detail
  WHERE pay_detail_serial_no=@fix_pay_no OR order_no=@fix_order_no OR trans_no=@fix_trans_no);
IF COALESCE(@ok,0) <> 1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Precheck failed: data changed, already repaired, or serial occupied. No repair applied.';
END IF;

-- 3. 补齐减免链路，4 张表各 INSERT 1 行。主键均由数据库生成。
INSERT INTO lease_saps.customer_order
  (tenant_code,order_no,source_order_no,customer_code,customer_name,transaction_type,
   order_desc,sub_type,transaction_mode,sub_mode,channel,pay_amount,transaction_amount,
   recharge_amount,settle_amount,platform_fee,channel_fee,advance_fee,earn_fee,status,
   order_time,priority,product_code,product_type,terminal_type,create_by,create_time,
   version,is_delete,is_cancel,remark)
SELECT tenant_code,@fix_order_no,'HX261123104055000001',customer_code,customer_name,2,
       '滞纳金减免',2000,4,40,8,2.68,2.68,0,2.68,0,0,0,0,4,
       @repair_time,2,product_code,product_type,10,@fix_operator,@repair_time,1,0,0,
       CONCAT(@repair_tag,' 原核销减免漏执行补偿；无现金收款')
FROM lease_saps.customer_bill
WHERE @ok=1 AND id=1993 AND tenant_code='XXT' AND version=16;
SET @r1=ROW_COUNT();
SET @new_order_id=IF(@r1=1,LAST_INSERT_ID(),NULL);
SET @ok=@ok AND @r1=1;

INSERT INTO lease_saps.customer_transaction
  (tenant_code,order_no,serial_no,source_serial_no,transaction_name,pay_amount,
   transaction_amount,recharge_amount,settle_amount,status,goods_no,send_date,
   create_by,create_time,version,is_delete,is_cancel,remark)
SELECT 'XXT',@fix_order_no,@fix_trans_no,'HX261123104055000001','滞纳金减免',
       2.68,2.68,0,2.68,4,'C2511211024450013',DATE(@repair_time),
       @fix_operator,@repair_time,1,0,0,@repair_tag
WHERE @ok=1;
SET @r2=ROW_COUNT();
SET @new_trans_id=IF(@r2=1,LAST_INSERT_ID(),NULL);
SET @ok=@ok AND @r2=1;

INSERT INTO lease_saps.customer_bill_pay_detail
  (tenant_code,bill_no,serial_no,order_no,trans_no,channel,sub_mode,pay_amount,fee_amount,
   overdue_day,settlement_time,operate_time,account_time,is_reverse,
   create_by,create_time,version,is_delete,is_cancel,remark)
SELECT tenant_code,bill_no,@fix_pay_no,@fix_order_no,@fix_trans_no,8,40,2.68,0,
       overdue_day,@repair_time,@repair_time,@repair_time,0,
       @fix_operator,@repair_time,1,0,0,@repair_tag
FROM lease_saps.customer_bill
WHERE @ok=1 AND id=1993 AND tenant_code='XXT' AND version=16;
SET @r3=ROW_COUNT();
SET @new_pay_id=IF(@r3=1,LAST_INSERT_ID(),NULL);
SET @ok=@ok AND @r3=1;

INSERT INTO lease_saps.customer_bill_pay_subject_detail
  (tenant_code,bill_no,order_no,trans_no,pay_detail_serial_no,subject_no,subject_name,
   paid_amount,priority,create_by,create_time,version,is_delete,is_cancel,remark)
SELECT tenant_code,bill_no,@fix_order_no,@fix_trans_no,@fix_pay_no,subject_no,subject_name,
       2.68,priority,@fix_operator,@repair_time,1,0,0,@repair_tag
FROM lease_saps.bill_fee_detail
WHERE @ok=1 AND id=12318 AND tenant_code='XXT' AND subject_no='103'
  AND total_amount=2.68 AND paid_amount=2.68 AND unpaid_amount=0 AND version=4;
SET @r4=ROW_COUNT();
SET @new_subject_id=IF(@r4=1,LAST_INSERT_ID(),NULL);
SET @ok=@ok AND @r4=1;

-- 4. 调整原现金收款科目分配，两条合计仍为 1339.80；不删除旧明细。
UPDATE lease_saps.customer_bill_pay_subject_detail
SET paid_amount=0,version=version+1,modify_by=@fix_operator,modify_time=@repair_time,
    summary=CONCAT('原2.68元实收改分配本金；减免流水 ',@fix_order_no)
WHERE @ok=1 AND id=4486 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND order_no='OR261123104304000001' AND subject_no='103' AND paid_amount=2.68
  AND version=1 AND is_delete=0 AND is_cancel=0;
SET @r5=ROW_COUNT();
SET @ok=@ok AND @r5=1;

UPDATE lease_saps.customer_bill_pay_subject_detail
SET paid_amount=1339.80,version=version+1,modify_by=@fix_operator,modify_time=@repair_time,
    summary='FIX-HX226-20261124：补回原误分配滞纳金的实收2.68元'
WHERE @ok=1 AND id=4487 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND order_no='OR261123104304000001' AND subject_no='101' AND paid_amount=1337.12
  AND version=1 AND is_delete=0 AND is_cancel=0;
SET @r6=ROW_COUNT();
SET @ok=@ok AND @r6=1;

UPDATE lease_saps.bill_fee_detail
SET paid_amount=1339.80,unpaid_amount=0,version=version+1,
    modify_by=@fix_operator,modify_time=@repair_time
WHERE @ok=1 AND id=5821 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND subject_no='101' AND total_amount=1339.80 AND paid_amount=1337.12 AND unpaid_amount=2.68
  AND version=2 AND is_delete=0 AND is_cancel=0;
SET @r7=ROW_COUNT();
SET @ok=@ok AND @r7=1;
-- 103科目的已结算额仍为2.68：原现金2.68转出、减免2.68转入，净变化0。
-- 因此 id=12318 的金额和版本不变；不能再 paid_amount += 2.68。

UPDATE lease_saps.customer_bill
SET paid_amount=1342.48,unpaid_amount=0,settlement_status=1,settlement_time=@repair_time,
    verification_status=1,verification_time=@repair_time,verification_person=@fix_operator,
    payment_mode=20,version=version+1,modify_by=@fix_operator,modify_time=@repair_time
WHERE @ok=1 AND id=1993 AND tenant_code='XXT' AND bill_no='C2511211024450013'
  AND total_amount=1342.48 AND paid_amount=1339.80 AND unpaid_amount=2.68
  AND settlement_status=1 AND settlement_time IS NULL AND verification_status=2
  AND version=16 AND is_delete=0 AND is_cancel=0;
SET @r8=ROW_COUNT();
SET @ok=@ok AND @r8=1;

-- 5. 补偿正常减免 afterCommit 中 updateUnpaidRent 应做的汇总同步。
-- 原资产汇总已陈旧，不能用 unpaid_rent-2.68；先核验修正后的全进件本金合计。
SET @ok=@ok AND (
  SELECT COUNT(*)=48 AND SUM(d.unpaid_amount)=46893.00
  FROM lease_saps.customer_bill b JOIN lease_saps.bill_fee_detail d ON b.bill_no=d.bill_no
  WHERE b.is_delete=0 AND d.is_delete=0 AND b.tenant_code='XXT'
    AND b.application_no='AP251111000001' AND d.subject_no='101');
UPDATE lease_finance.asset_order
SET unpaid_rent=46893.00,unpaid_rent_discount_interest=46893.00,
    version=version+1,modify_by=@fix_operator,modify_time=@repair_time
WHERE @ok=1 AND id=62 AND tenant_code='XXT' AND application_code='AP251111000001'
  AND customer_code='CO218213744' AND unpaid_rent=48232.80
  AND unpaid_rent_discount_interest=48232.80 AND subsidy_amount=0
  AND version=12 AND is_delete=0 AND is_cancel=0;
SET @r9=ROW_COUNT();
SET @ok=@ok AND @r9=1;

-- 6. 最终一致性门槛；以下各项必须为1。
SET @ok=@ok AND (
  SELECT COUNT(*)=5 AND SUM(total_amount)=1342.48 AND SUM(paid_amount)=1342.48
    AND SUM(unpaid_amount)=0 AND MIN(unpaid_amount)>=0
  FROM lease_saps.bill_fee_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0);
SET @ok=@ok AND (
  SELECT COUNT(*)=2 AND SUM(pay_amount)=1342.48
    AND SUM(IF(sub_mode=41,pay_amount,0))=1339.80
    AND SUM(IF(sub_mode=40,pay_amount,0))=2.68
  FROM lease_saps.customer_bill_pay_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0);
SET @ok=@ok AND (
  SELECT COUNT(*)=3 AND SUM(paid_amount)=1342.48
    AND SUM(IF(subject_no='101',paid_amount,0))=1339.80
    AND SUM(IF(subject_no='103',paid_amount,0))=2.68
  FROM lease_saps.customer_bill_pay_subject_detail
  WHERE tenant_code='XXT' AND bill_no='C2511211024450013' AND is_delete=0 AND is_cancel=0);
SET @ok=@ok AND (
  SELECT COUNT(*)=1 FROM lease_saps.customer_bill WHERE id=1993 AND tenant_code='XXT'
    AND paid_amount=1342.48 AND unpaid_amount=0 AND settlement_status=1
    AND verification_status=1 AND version=17);


IF COALESCE(@ok,0) <> 1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Repair row count or final balance mismatch; all business changes rolled back.';
END IF;
COMMIT;
SELECT 'COMMITTED' AS status, 'C2511211024450013' AS bill_no,
       1339.80 AS cash_amount, 2.68 AS waived_amount, 0.00 AS unpaid_amount,
       46893.00 AS application_unpaid_principal,
       @repair_time AS repair_time, @fix_operator AS database_operator,
       @fix_order_no AS order_no, @fix_trans_no AS trans_no, @fix_pay_no AS pay_no,
       @new_order_id AS order_id, @new_trans_id AS transaction_id,
       @new_pay_id AS pay_detail_id, @new_subject_id AS pay_subject_id;
END$$
DELIMITER ;

CALL lease_saps.tmp_fix_hx226_20261124_01a20c2f();
DROP PROCEDURE lease_saps.tmp_fix_hx226_20261124_01a20c2f;
