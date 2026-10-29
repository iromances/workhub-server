-- 需求202610270002；嘉泰租赁资产保理生产；中恒 PRJ-ZH-001。
-- 新建本次只读一致性快照，同一会话内导出三页。不得运行其他写入语句。
-- 本次已确认截止日为2026-10-29；最终未脱敏、未加密。原始日期辅助字段由构建器按确认口径计算。
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;
SELECT NOW(6) AS snapshot_recorded_at, CONNECTION_ID() AS connection_id,
       @@server_uuid AS server_uuid, @@session.time_zone AS session_time_zone,
       @@system_time_zone AS system_time_zone;

-- 银行卡导出：前7列为交付字段，末尾2列仅用于本地核验同一会话和实际查询时间。
SELECT ar.asset_code AS `资产编号`, cb.partner_agree_id AS `协议号`,
       cb.bank_name AS `银行名称`, cb.card_name AS `持卡人姓名`,
       cb.card_no AS `银行卡卡号`, cb.phone AS `银行预留手机号`,
       cb.create_time AS `绑定日期`, NOW(6) AS `__extracted_at`,
       CONNECTION_ID() AS `__connection_id`
FROM assets_lease.asset_record ar
JOIN assets_saps.card_on_application coa ON coa.application_no=ar.asset_code
JOIN assets_saps.card_binding cb ON cb.serial_no=coa.bind_serial_no
WHERE ar.project_code='PRJ-ZH-001'
ORDER BY ar.asset_code, coa.id;

-- 全部导出完成后，另行执行本会话的只读事务核查及 ROLLBACK。

-- 资产原始导出：转让日期暂空，交付时按本次确认日期写入；前67列对应最终模板。
SELECT
    ar.asset_code AS `资产编码`,
    fa.application_code AS `融资单号`,
    ar.external_asset_code AS `进件单号`,
    ar.customer_code AS `客户编号`,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(fa.other_info,'$.serviceRate')) AS DECIMAL(10,8))*100 AS `成本利率`,
    fa.factoring_contract_no AS `保理合同编号`,
    fa.finance_amount AS `合同金额`,
    CAST(ev.attribute_value AS DECIMAL(20,4))/100 AS `车辆评估金额`,
    fa.loan_time AS `放款日期`,
    ar.rate*100 AS `对客借款利率年化%(选填)`,
    NULL AS `转让日期`,
    bal.remaining_principal AS `转让时剩余本金`,
    bal.remaining_rent AS `转让时剩余租金`,
    ac.customer_name AS `客户姓名`,
    ac.id_card_no AS `身份证号`,
    TRIM(SUBSTRING_INDEX(ac.id_card_expire_date,'至',1)) AS `身份证起始日期`,
    TRIM(SUBSTRING_INDEX(ac.id_card_expire_date,'至',-1)) AS `身份证截止日期`,
    ac.id_card_issue_organ AS `身份证签发机关`,
    ac.nation AS `民族`,
    CASE ac.gender WHEN 0 THEN '未知' WHEN 1 THEN '男' WHEN 2 THEN '女' ELSE CAST(ac.gender AS CHAR) END AS `性别`,
    ac.customer_mobile AS `手机号`,
    CASE ac.marital_status WHEN 0 THEN '未婚' WHEN 1 THEN '已婚' WHEN 2 THEN '离异' WHEN 3 THEN '其他' ELSE CAST(ac.marital_status AS CHAR) END AS `婚姻状况`,
    NULL AS `户籍行政区划代码`,
    ac.census_address_detail AS `户籍详细地址`,
    NULL AS `现居住地行政区划代码`,
    ac.living_address_detail AS `现居住详细地址`,
    ac.email AS `联系邮箱`,
    w.workplace_name AS `单位名称`,
    NULL AS `单位行政区划代码`,
    w.workplace_address_detail AS `单位详细地址`,
    ct.contact_1_name AS `联系人1姓名`,
    ct.contact_1_mobile AS `联系人1手机号`,
    ct.contact_1_relation AS `联系人1关系`,
    ct.contact_2_name AS `联系人2姓名`,
    ct.contact_2_mobile AS `联系人2手机号`,
    ct.contact_2_relation AS `联系人2关系`,
    ct.contact_3_name AS `联系人3姓名`,
    ct.contact_3_mobile AS `联系人3手机号`,
    ct.contact_3_relation AS `联系人3关系`,
    ct.contact_4_name AS `联系人4姓名`,
    ct.contact_4_mobile AS `联系人4手机号`,
    ct.contact_4_relation AS `联系人4关系`,
    ct.contact_5_name AS `联系人5姓名`,
    ct.contact_5_mobile AS `联系人5手机号`,
    ct.contact_5_relation AS `联系人5关系`,
    ct.contact_6_name AS `联系人6姓名`,
    ct.contact_6_mobile AS `联系人6手机号`,
    ct.contact_6_relation AS `联系人6关系`,
    ct.contact_7_name AS `联系人7姓名`,
    ct.contact_7_mobile AS `联系人7手机号`,
    ct.contact_7_relation AS `联系人7关系`,
    ct.contact_8_name AS `联系人8姓名`,
    ct.contact_8_mobile AS `联系人8手机号`,
    ct.contact_8_relation AS `联系人8关系`,
    d.device_name AS `车型名称`,
    d.brand AS `车型品牌`,
    NULL AS `车辆型号(行驶证)`,
    pl.attribute_value AS `车牌号`,
    vin.attribute_value AS `车架号`,
    en.attribute_value AS `发动机号`,
    rd.attribute_value AS `车辆登记日期`,
    CAST(mi.attribute_value AS DECIMAL(20,4))*10000 AS `公里数`,
    NULL AS `登记证编号`,
    CASE uc.attribute_value WHEN '0' THEN '家庭自用汽车' WHEN '1' THEN '非营业客车' WHEN '3' THEN '营业客车' WHEN '4' THEN '非营业货车' WHEN '5' THEN '营业货车' WHEN '6' THEN '特种车' WHEN '7' THEN '摩托车' WHEN '8' THEN '拖拉机' WHEN '9' THEN '挂车' WHEN '10' THEN '企业用车' ELSE uc.attribute_value END AS `车辆使用性质`,
    NULL AS `抵押权人(选填)`,
    NULL AS `抵押登记日期(选填)`,
    ar.remark AS `备注`,
    NOW(6) AS `__extracted_at`,
    CONNECTION_ID() AS `__connection_id`
FROM assets_lease.asset_record ar
         LEFT JOIN assets_lease.finance_application fa ON fa.finance_assets_codes=ar.asset_code AND fa.project_code=ar.project_code AND fa.status='351'
         LEFT JOIN assets_lease.asset_customer ac ON ac.asset_code=ar.asset_code
         LEFT JOIN assets_lease.asset_customer_work_experience w ON w.asset_code=ac.asset_code AND w.work_experience_code=ac.work_experience_code
         LEFT JOIN assets_lease.asset_device d ON d.asset_code=ar.asset_code
         LEFT JOIN assets_lease.asset_supplement_attribute ev ON ev.asset_code=ar.asset_code AND ev.link_flag=d.device_code AND ev.attribute_group='DEVICE' AND ev.group_key='vehicleBaseInfo' AND ev.attribute_en='vehicleEvaluation' AND ev.is_delete=0
         LEFT JOIN assets_lease.asset_supplement_attribute pl ON pl.asset_code=ar.asset_code AND pl.link_flag=d.device_code AND pl.attribute_group='DEVICE' AND pl.group_key='vehicleRegisterInfo' AND pl.attribute_en='plateNo' AND pl.is_delete=0
         LEFT JOIN assets_lease.asset_supplement_attribute vin ON vin.asset_code=ar.asset_code AND vin.link_flag=d.device_code AND vin.attribute_group='DEVICE' AND vin.group_key='vehicleBaseInfo' AND vin.attribute_en='vehicleVin' AND vin.is_delete=0
         LEFT JOIN assets_lease.asset_supplement_attribute en ON en.asset_code=ar.asset_code AND en.link_flag=d.device_code AND en.attribute_group='DEVICE' AND en.group_key='vehicleBaseInfo' AND en.attribute_en='engineNo' AND en.is_delete=0
         LEFT JOIN assets_lease.asset_supplement_attribute rd ON rd.asset_code=ar.asset_code AND rd.link_flag=d.device_code AND rd.attribute_group='DEVICE' AND rd.group_key='vehicleRegisterInfo' AND rd.attribute_en='firstRegistrationDate' AND rd.is_delete=0
         LEFT JOIN assets_lease.asset_supplement_attribute mi ON mi.asset_code=ar.asset_code AND mi.link_flag=d.device_code AND mi.attribute_group='DEVICE' AND mi.group_key='vehicleRegisterInfo' AND mi.attribute_en='mileage' AND mi.is_delete=0
         LEFT JOIN assets_lease.asset_supplement_attribute uc ON uc.asset_code=ar.asset_code AND uc.link_flag=d.device_code AND uc.attribute_group='DEVICE' AND uc.group_key='vehicleRegisterInfo' AND uc.attribute_en='useCharacter' AND uc.is_delete=0
LEFT JOIN (
    SELECT ranked.asset_code,
        MAX(CASE WHEN ranked.rn = 1 THEN ranked.name END) AS contact_1_name,
        MAX(CASE WHEN ranked.rn = 1 THEN ranked.mobile END) AS contact_1_mobile,
        MAX(CASE WHEN ranked.rn = 1 THEN ranked.relation END) AS contact_1_relation,
        MAX(CASE WHEN ranked.rn = 2 THEN ranked.name END) AS contact_2_name,
        MAX(CASE WHEN ranked.rn = 2 THEN ranked.mobile END) AS contact_2_mobile,
        MAX(CASE WHEN ranked.rn = 2 THEN ranked.relation END) AS contact_2_relation,
        MAX(CASE WHEN ranked.rn = 3 THEN ranked.name END) AS contact_3_name,
        MAX(CASE WHEN ranked.rn = 3 THEN ranked.mobile END) AS contact_3_mobile,
        MAX(CASE WHEN ranked.rn = 3 THEN ranked.relation END) AS contact_3_relation,
        MAX(CASE WHEN ranked.rn = 4 THEN ranked.name END) AS contact_4_name,
        MAX(CASE WHEN ranked.rn = 4 THEN ranked.mobile END) AS contact_4_mobile,
        MAX(CASE WHEN ranked.rn = 4 THEN ranked.relation END) AS contact_4_relation,
        MAX(CASE WHEN ranked.rn = 5 THEN ranked.name END) AS contact_5_name,
        MAX(CASE WHEN ranked.rn = 5 THEN ranked.mobile END) AS contact_5_mobile,
        MAX(CASE WHEN ranked.rn = 5 THEN ranked.relation END) AS contact_5_relation,
        MAX(CASE WHEN ranked.rn = 6 THEN ranked.name END) AS contact_6_name,
        MAX(CASE WHEN ranked.rn = 6 THEN ranked.mobile END) AS contact_6_mobile,
        MAX(CASE WHEN ranked.rn = 6 THEN ranked.relation END) AS contact_6_relation,
        MAX(CASE WHEN ranked.rn = 7 THEN ranked.name END) AS contact_7_name,
        MAX(CASE WHEN ranked.rn = 7 THEN ranked.mobile END) AS contact_7_mobile,
        MAX(CASE WHEN ranked.rn = 7 THEN ranked.relation END) AS contact_7_relation,
        MAX(CASE WHEN ranked.rn = 8 THEN ranked.name END) AS contact_8_name,
        MAX(CASE WHEN ranked.rn = 8 THEN ranked.mobile END) AS contact_8_mobile,
        MAX(CASE WHEN ranked.rn = 8 THEN ranked.relation END) AS contact_8_relation
    FROM (
        SELECT c.asset_code, c.name, c.mobile,
               CASE c.relation WHEN 1 THEN '配偶' WHEN 2 THEN '父母' WHEN 3 THEN '子女'
                   WHEN 4 THEN '朋友' WHEN 5 THEN '亲属' WHEN 6 THEN '同学' WHEN 7 THEN '其他'
                   ELSE CAST(c.relation AS CHAR) END AS relation,
               ROW_NUMBER() OVER (PARTITION BY c.asset_code ORDER BY c.order_index, c.id) AS rn
        FROM assets_lease.asset_contact c
        JOIN assets_lease.asset_record ca ON ca.asset_code = c.asset_code
        WHERE ca.project_code = 'PRJ-ZH-001'
    ) ranked
    GROUP BY ranked.asset_code
) ct ON ct.asset_code = ar.asset_code
LEFT JOIN (
    SELECT b.application_no,
           CASE WHEN COUNT(*) = COUNT(bp.unpaid_amount)
                THEN SUM(bp.unpaid_amount) ELSE NULL END AS remaining_principal,
           CASE WHEN COUNT(*) = COUNT(bp.unpaid_amount) AND COUNT(*) = COUNT(bi.unpaid_amount)
                THEN SUM(bp.unpaid_amount + bi.unpaid_amount) ELSE NULL END AS remaining_rent
    FROM assets_saps.customer_bill b
    JOIN assets_lease.asset_record ba ON ba.asset_code = b.application_no
    LEFT JOIN assets_saps.bill_fee_detail bp
        ON bp.bill_no = b.bill_no AND bp.subject_no = '101' AND bp.is_delete = 0
    LEFT JOIN assets_saps.bill_fee_detail bi
        ON bi.bill_no = b.bill_no AND bi.subject_no = '1020' AND bi.is_delete = 0
    WHERE ba.project_code = 'PRJ-ZH-001' AND b.is_delete = 0
    GROUP BY b.application_no
) bal ON bal.application_no = ar.asset_code
WHERE ar.project_code='PRJ-ZH-001'
ORDER BY ar.asset_code;

-- 账单原始导出：逾期天数暂空，保留源宽限日期及实还时间，交付时按确认日期重算。
SELECT
    cb.application_no AS `资产编号`,
    cb.bill_no AS `客户账单编号`,
    cb.customer_name AS `客户名称`,
    ao.cooperation_name AS `合作方名称`,
    cb.pid AS `合作方账单编号`,
    cb.period AS `当前期数`,
    cb.total_period AS `总期数`,
    cb.start_date AS `本期开始日期`,
    cb.end_date AS `本期结束日期`,
    cb.unpaid_amount AS `未还总额(元)`,
    cb.total_amount AS `应还总额(元)`,
    cb.principal AS `应还本金(元)`,
    IFNULL(lx.total_amount,0) AS `应还利息(元)`,
    IFNULL(fwf.total_amount,0) AS `应还服务费(元)`,
    IFNULL(fx.total_amount,0) AS `应还罚息(元)`,
    IFNULL(lgj.total_amount,0) AS `应还留购价(元)`,
    IFNULL(tqjq.total_amount,0) AS `应还提前结清手续费(元)`,
    IFNULL(bj.paid_amount,0) AS `实还本金(元)`,
    IFNULL(lx.paid_amount,0) AS `实还利息(元)`,
    IFNULL(fwf.paid_amount,0) AS `实还服务费(元)`,
    IFNULL(fx.paid_amount,0) AS `实还罚息(元)`,
    IFNULL(lgj.paid_amount,0) AS `实还留购价(元)`,
    IFNULL(tqjq.paid_amount,0) AS `实还提前结清手续费(元)`,
    NULL AS `逾期天数`,
    CASE cb.settlement_status WHEN 0 THEN '未还款' WHEN 1 THEN '已还款' WHEN 2 THEN '部分还款' ELSE CAST(cb.settlement_status AS CHAR) END AS `客户还款状态`,
    cb.settlement_time AS `客户实还时间`,
    ao.assets_tag AS `资产标签`,
    cb.is_delete+0 AS `__is_delete`,
    bj.unpaid_amount AS `__unpaid_principal`,
    lx.unpaid_amount AS `__unpaid_interest`,
    NOW(6) AS `__extracted_at`,
    CONNECTION_ID() AS `__connection_id`,
    cb.last_grace_date AS `__last_grace_date`
FROM assets_saps.customer_bill cb
         JOIN assets_lease.asset_record ar ON ar.asset_code=cb.application_no
         LEFT JOIN assets_saps.application_order ao ON ao.application_no=cb.application_no AND ao.is_delete=0
         LEFT JOIN assets_saps.bill_fee_detail bj ON bj.bill_no=cb.bill_no AND bj.subject_no='101' AND bj.is_delete=0
         LEFT JOIN assets_saps.bill_fee_detail lx ON lx.bill_no=cb.bill_no AND lx.subject_no='1020' AND lx.is_delete=0
         LEFT JOIN assets_saps.bill_fee_detail fwf ON fwf.bill_no=cb.bill_no AND fwf.subject_no='1023' AND fwf.is_delete=0
         LEFT JOIN assets_saps.bill_fee_detail fx ON fx.bill_no=cb.bill_no AND fx.subject_no='103' AND fx.is_delete=0
         LEFT JOIN assets_saps.bill_fee_detail lgj ON lgj.bill_no=cb.bill_no AND lgj.subject_no='1025' AND lgj.is_delete=0
         LEFT JOIN assets_saps.bill_fee_detail tqjq ON tqjq.bill_no=cb.bill_no AND tqjq.subject_no='104' AND tqjq.is_delete=0
WHERE ar.project_code='PRJ-ZH-001'
ORDER BY cb.application_no,cb.period,cb.id;

-- 全部导出后先执行此查询，确认同一只读快照，再单独执行 ROLLBACK。
SELECT NOW(6) AS checked_at, CONNECTION_ID() AS connection_id,
       trx_started, trx_isolation_level, trx_is_read_only, trx_rows_modified,
       (SELECT COUNT(*) FROM assets_lease.asset_record a WHERE a.project_code='PRJ-ZH-001') AS assets_count,
       (SELECT COUNT(*) FROM assets_lease.asset_record a JOIN assets_saps.card_on_application ca ON ca.application_no=a.asset_code JOIN assets_saps.card_binding c ON c.serial_no=ca.bind_serial_no WHERE a.project_code='PRJ-ZH-001') AS cards_count,
       (SELECT COUNT(*) FROM assets_saps.customer_bill b JOIN assets_lease.asset_record a ON a.asset_code=b.application_no WHERE a.project_code='PRJ-ZH-001') AS bills_count,
       (SELECT COUNT(*) FROM assets_saps.customer_bill b JOIN assets_lease.asset_record a ON a.asset_code=b.application_no WHERE a.project_code='PRJ-ZH-001' AND b.is_delete=1) AS deleted_bills_count,
       (SELECT SUM(p.unpaid_amount) FROM assets_saps.customer_bill b JOIN assets_lease.asset_record a ON a.asset_code=b.application_no LEFT JOIN assets_saps.bill_fee_detail p ON p.bill_no=b.bill_no AND p.subject_no='101' AND p.is_delete=0 WHERE a.project_code='PRJ-ZH-001' AND b.is_delete=0) AS remaining_principal,
       (SELECT SUM(p.unpaid_amount+i.unpaid_amount) FROM assets_saps.customer_bill b JOIN assets_lease.asset_record a ON a.asset_code=b.application_no LEFT JOIN assets_saps.bill_fee_detail p ON p.bill_no=b.bill_no AND p.subject_no='101' AND p.is_delete=0 LEFT JOIN assets_saps.bill_fee_detail i ON i.bill_no=b.bill_no AND i.subject_no='1020' AND i.is_delete=0 WHERE a.project_code='PRJ-ZH-001' AND b.is_delete=0) AS remaining_rent
FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID();

-- 仅结束本文件对应的只读会话，不操作其他控制台的事务。
ROLLBACK;
