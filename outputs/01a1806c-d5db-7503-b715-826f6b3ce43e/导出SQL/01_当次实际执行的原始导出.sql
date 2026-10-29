-- 需求202610270002：中恒项目全量数据导出
-- 业务线：嘉泰租赁资产保理；环境：生产；项目：PRJ-ZH-001；MySQL 8.0。
-- 在已核实的 DataGrip 生产连接中执行。全部为只读 SELECT，不包含脱敏表达式。
-- 转让截止日期固定为 2026-10-28。金额、状态等来自重新执行时的实时数据；本文件不能重建历史快照。
-- 已交付文件的账单查询时间：2026-10-28 11:57:54；资产查询时间：2026-10-28 12:09:22。
-- 银行卡原始文件生成完成时间：2026-10-28 11:39:51。上述时间均为北京时间。
-- 当次交付行数：资产8062（含105条失效）、银行卡8717（含141条解绑且关联已删除）、账单286488（含36条已删除）。
-- 三条无资产主记录的试单账单通过资产主表关联被排除。已删除账单仅保留明细，不计转让余额。
-- 重新导出后须重新核对行数和金额；不要为了凑历史行数添加 LIMIT 或删行。
-- 建议在同一只读一致性快照中执行三段查询，避免其间还款变动；确认客户端仍使用同一会话/事务。
-- Excel导出时把身份证、卡号、手机号、协议号、业务编号保留为文本，避免长数字被截断或前导零丢失。

-- 【实际执行版】
-- 以下三个 SELECT 原文取自当次 DataGrip 控制台/执行历史，未改查询逻辑。
-- 资产结果为52个原模板字段+4个内部辅助字段；银行卡为7列；账单为27个模板字段+4个内部辅助字段。
-- 最终工作簿另做了以下本地处理，因此本文件的资产结果不能直接当成67列表格交付：
-- 1. 资产 __contacts_json 按 order_index、id 升序排列，展开联系人1至8，共24列，重复联系人不去重。
--    关系映射：1配偶、2父母、3子女、4朋友、5亲属、6同学、7其他。
-- 2. 使用同一份账单原始导出文件；按资产编号，仅对 __is_delete=0 汇总：
--    转让时剩余本金 = SUM(__unpaid_principal)；剩余租金 = SUM(__unpaid_principal + __unpaid_interest)。
--    包含逾期和未到期，不包含罚息/服务费；没有客户账单的105条失效资产余额保持空。
-- 3. 移除所有 __ 开头辅助列；日期按源值转换为Excel日期，“长期”保留文本。
-- 4. 成本利率、保理合同和放款等取唯一 status=351 成功申请；115条无成功申请的资产融资字段保留空值。
-- 5. 三个行政区划代码列留空；车辆型号(行驶证)、登记证编号、抵押权人、抵押登记日期按确认留空。
-- 6. 将数据写入原模板三页，记录导出时间与口径批注；所有个人和银行卡字段均未脱敏。

-- 1. 资产原始导出（56列）
SELECT
    ar.asset_code AS `资产编码`, fa.application_code AS `融资单号`, ar.external_asset_code AS `进件单号`, ar.customer_code AS `客户编号`,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(fa.other_info,'$.serviceRate')) AS DECIMAL(10,8))*100 AS `成本利率`,
    fa.factoring_contract_no AS `保理合同编号`, fa.finance_amount AS `合同金额`, CAST(ev.attribute_value AS DECIMAL(20,4))/100 AS `车辆评估金额`, fa.loan_time AS `放款日期`, ar.rate*100 AS `对客借款利率年化%(选填)`, DATE('2026-10-28') AS `转让日期`, NULL AS `转让时剩余本金`, NULL AS `转让时剩余租金`,
    ac.customer_name AS `客户姓名`, ac.id_card_no AS `身份证号`, TRIM(SUBSTRING_INDEX(ac.id_card_expire_date,'至',1)) AS `身份证起始日期`, TRIM(SUBSTRING_INDEX(ac.id_card_expire_date,'至',-1)) AS `身份证截止日期`, ac.id_card_issue_organ AS `身份证签发机关`, ac.nation AS `民族`, CASE ac.gender WHEN 0 THEN '未知' WHEN 1 THEN '男' WHEN 2 THEN '女' ELSE CAST(ac.gender AS CHAR) END AS `性别`, ac.customer_mobile AS `手机号`,
    CASE ac.marital_status WHEN 0 THEN '未婚' WHEN 1 THEN '已婚' WHEN 2 THEN '离异' WHEN 3 THEN '其他' ELSE CAST(ac.marital_status AS CHAR) END AS `婚姻状况`,
    NULL AS `户籍行政区划代码`, ac.census_address_detail AS `户籍详细地址`, NULL AS `现居住地行政区划代码`, ac.living_address_detail AS `现居住详细地址`, ac.email AS `联系邮箱`, w.workplace_name AS `单位名称`, NULL AS `单位行政区划代码`, w.workplace_address_detail AS `单位详细地址`,
    NULL AS `联系人1姓名`, NULL AS `联系人1手机号`, NULL AS `联系人1关系`, NULL AS `联系人2姓名`, NULL AS `联系人2手机号`, NULL AS `联系人2关系`, NULL AS `联系人3姓名`, NULL AS `联系人3手机号`, NULL AS `联系人3关系`,
    d.device_name AS `车型名称`, d.brand AS `车型品牌`, NULL AS `车辆型号(行驶证)`, pl.attribute_value AS `车牌号`, vin.attribute_value AS `车架号`, en.attribute_value AS `发动机号`, rd.attribute_value AS `车辆登记日期`, CAST(mi.attribute_value AS DECIMAL(20,4))*10000 AS `公里数`, NULL AS `登记证编号`,
    CASE uc.attribute_value WHEN '0' THEN '家庭自用汽车' WHEN '1' THEN '非营业客车' WHEN '3' THEN '营业客车' WHEN '4' THEN '非营业货车' WHEN '5' THEN '营业货车' WHEN '6' THEN '特种车' WHEN '7' THEN '摩托车' WHEN '8' THEN '拖拉机' WHEN '9' THEN '挂车' WHEN '10' THEN '企业用车' ELSE uc.attribute_value END AS `车辆使用性质`,
    NULL AS `抵押权人(选填)`, NULL AS `抵押登记日期(选填)`, ar.remark AS `备注`,
    (SELECT JSON_ARRAYAGG(JSON_OBJECT('id',c.id,'order_index',c.order_index,'name',c.name,'mobile',c.mobile,'relation',c.relation)) FROM assets_lease.asset_contact c WHERE c.asset_code=ar.asset_code) AS `__contacts_json`, ar.status AS `__asset_status`, d.model AS `__raw_model`, NOW() AS `__extracted_at`
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
WHERE ar.project_code='PRJ-ZH-001'
ORDER BY ar.asset_code;

-- 2. 银行卡列表（7列）
SELECT ar.asset_code AS `资产编号`, cb.partner_agree_id AS `协议号`, cb.bank_name AS `银行名称`, cb.card_name AS `持卡人姓名`, cb.card_no AS `银行卡卡号`, cb.phone AS `银行预留手机号`, cb.create_time AS `绑定日期`
FROM assets_lease.asset_record ar
         JOIN assets_saps.card_on_application coa ON coa.application_no = ar.asset_code
         JOIN assets_saps.card_binding cb ON cb.serial_no = coa.bind_serial_no
WHERE ar.project_code = 'PRJ-ZH-001'
ORDER BY ar.asset_code, coa.id;

-- 3. 客户账单原始导出（31列）
SELECT cb.application_no AS `资产编号`, cb.bill_no AS `客户账单编号`, cb.customer_name AS `客户名称`, ao.cooperation_name AS `合作方名称`, cb.pid AS `合作方账单编号`,
       cb.period AS `当前期数`, cb.total_period AS `总期数`, cb.start_date AS `本期开始日期`, cb.end_date AS `本期结束日期`,
       cb.unpaid_amount AS `未还总额(元)`, cb.total_amount AS `应还总额(元)`, cb.principal AS `应还本金(元)`,
       IFNULL(lx.total_amount,0) AS `应还利息(元)`, IFNULL(fwf.total_amount,0) AS `应还服务费(元)`, IFNULL(fx.total_amount,0) AS `应还罚息(元)`, IFNULL(lgj.total_amount,0) AS `应还留购价(元)`, IFNULL(tqjq.total_amount,0) AS `应还提前结清手续费(元)`,
       IFNULL(bj.paid_amount,0) AS `实还本金(元)`, IFNULL(lx.paid_amount,0) AS `实还利息(元)`, IFNULL(fwf.paid_amount,0) AS `实还服务费(元)`, IFNULL(fx.paid_amount,0) AS `实还罚息(元)`, IFNULL(lgj.paid_amount,0) AS `实还留购价(元)`, IFNULL(tqjq.paid_amount,0) AS `实还提前结清手续费(元)`,
       GREATEST(DATEDIFF(COALESCE(DATE(cb.settlement_time),DATE('2026-10-28')),cb.last_grace_date),0) AS `逾期天数`,
       CASE cb.settlement_status WHEN 0 THEN '未还款' WHEN 1 THEN '已还款' WHEN 2 THEN '部分还款' ELSE CAST(cb.settlement_status AS CHAR) END AS `客户还款状态`, cb.settlement_time AS `客户实还时间`, ao.assets_tag AS `资产标签`,
       cb.is_delete+0 AS `__is_delete`, bj.unpaid_amount AS `__unpaid_principal`, lx.unpaid_amount AS `__unpaid_interest`, NOW() AS `__extracted_at`
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
