# 江苏易吾新自营渠道配置清单

## 排查上下文

| 项目 | 值 |
| --- | --- |
| 业务线 | 保费分期（BL000001） |
| GitLab 项目组 | `scf` |
| 环境 | 测试环境 |
| 分支 | `master`（用户指定） |
| DB 来源 | WorkHub 受控只读 DB：`bl000001-test-db-10-10-116-33-3308` |
| 源配置 | 山西自营：`HZF000144 / XXT-PRJ-ZY-A / PRD-ZY-A001`（有效产品 `id=265`） |
| 目标配置 | 江苏易吾：`HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001`（产品 `id=278`） |
| SQL | `scf-test-hzf000133-config-copy-20260813.sql`、`scf-test-hzf000133-contract-config-20260813.sql` |

> “完全复制”按“复制山西运行规则和配置结构”处理。目标编码、9 期费率、项目额度/地域、JDPAY 路由属于江苏易吾自身业务值，不用山西值覆盖；放款手续费复制山西 BCM 规则，但 `merchant_no` 替换为 `HZF000133`。项目附件 44 条已纳入复制；旧 `org_cooperation` 含真实主体资料且新自营链路不读取，不复制。

## 配置矩阵

| 编号 | 维度 | 配置项 | 当前状态 | 处理方式 | 目标值 | 表 | 字典/取值说明 | SQL |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P01 | 项目 | 项目主数据、渠道绑定 | 已配置 | 页面，无需处理 | `XXT-PRJ-YW-A`；渠道 `HZF000133`；启用 | `scf_self_order.amp_project` | `status=1` 启用 | 保留现值，不复制山西编码和额度 |
| P02 | 项目 | 风控模型 | 已配置且与山西一致 | 页面，无需处理 | 准入/申请均为 `PRD-JBR-001` | `scf_self_order.project_config` | 风控产品编码 | 保留现值 |
| P03 | 项目 | 险种范围 | 已配置，不能照抄山西 | 页面，无需处理 | `insurance_type=1`，仅商业险 | `scf_self_order.project_config` | `1=商业险`，`2=商业险+非商业险` | 保留现值；山西为 `2` |
| P04 | 项目 | 项目控制期数 | 现值 `period=3`，但不控制本产品 | 页面，无需处理 | 商业险实际期数由产品费率和产品配置限定为 9 | `scf_self_order.project_config` | 该字段是“非商业险融资期数”；目标 `insurance_type=1` 时不生效 | 保留 `3`，不改为 9 |
| P05 | 项目 | 单笔限额/地域 | 已配置，属于江苏易吾自身业务值 | 页面，无需处理 | `1~500000`；全国省份 | `scf_self_order.project_config` | 金额为元；`city_codes` 为行政区划码 | 保留现值 |
| P06 | 项目 | 产品关系 | 已配置 | 页面，无需处理 | `PRD-YW-A001`，A/B/C 三级 | `scf_self_order.project_config` | `product_ids` JSON | 保留目标产品编码 |
| P07 | 项目 | 项目标识完整性 | 部分缺失 | 脚本 | `project_no=XXT-PRJ-YW-A` | `scf_self_order.project_config` | 与 `project_id` 一致 | 主脚本 `S01` |
| P08 | 项目 | 保证金配置明细 | 部分缺失 | 脚本 | `MarginRatio=0.1` | `scf_self_order.project_config` | `project_detail_key=MarginRatio` | 主脚本 `S01` |
| P09 | 项目 | 合同模板 | 缺失（目标 0 条） | 脚本 | 复制山西有效合同 32 条；排除居间协议 | `scf_self_order.contract_config` | 排除 `insurance.intermediary.service.agreement` | 合同脚本 |
| P10 | 项目 | 合同签署人 | 缺失 | 脚本 | 随合同复制山西有效签署人，当前预计 39 条 | `scf_self_order.contract_config_signers` | 按场景+模板映射新合同主键 | 合同脚本 |
| P10-1 | 项目 | 仅商业险客户签约运行集 | 缺失，随合同脚本生成 | 脚本 | `BINDING_CARD` 命中 4 份合同、5 个签署人；不含居间及非商业险合同 | `contract_config` / `contract_config_signers` | `use_condition.insuranceType` 匹配 `1` | 预检脚本 `B05` 校验 |
| P10-2 | 项目 | 投保签章运行集 | 缺失，随合同脚本生成 | 脚本 | `BINDING_SUCCESS` 3 份附件模板、3 个签署人 | `contract_config` / `contract_config_signers` | 无对应原始保单时填充器跳过该份模板 | 预检脚本 `B05` 校验 |
| P11 | 项目 | SAPS 项目账单日期规则 | 缺失 | 脚本 | 复制 `lastGraceDate`、`lastPayDate` | `scf_self_saps.business_config` | `query_type=PROJECT`，`group_key=StatementConfig` | 主脚本 `S08` |
| P12 | 项目 | SAPS 结算场景 | 当前 0 条，执行后必须保持 0 | 不处理 | 暂不复制 | `scf_self_saps.statement_scene` / `statement_scene_rule` | master 仅注册山西项目扩展坐标；误配会中断缴费通知或客户账单事务 | 已从执行脚本彻底移除 |
| P13 | 项目 | 项目附件运行配置 | 缺失：山西 44 条，目标 0 条 | 脚本 | 原样复制山西 44 条（进件 14、放款阶段 30；其中 8 条允许补传） | `scf_self_order.project_attachment_config` | 页面附件回显及保单补传按 `project_no` 查询；源含语义相同历史重复，保留原运行集合 | 主脚本 `S01-1`；预检 `A05/B01/B08` |
| P14 | 项目/渠道 | 旧合作方组织 | 目标无记录，且目标项目 `cooperation_id` 为空 | 不处理 | 保持为空 | `scf_self_order.org_cooperation` / `amp_project.cooperation_id` | 新自营 `/api/application/v2/blank` 使用 `crm_channel` 与 `amp_project.channel_codes`；旧表含主体、证件、银行卡等渠道专属资料 | 禁止从 `HZF000144` 复制；不是当前新自营进件前置 |
| D01 | 产品 | 产品主数据 | 已配置 | 页面，无需处理 | `PRD-YW-A001`；启用 | `scf_self_order.amp_product` | `status=1` 启用 | 保留目标编码/名称 |
| D02 | 产品 | 还款方式 | 缺失 | 页面可配/脚本 | `repayment_type=4`，复制山西 | `scf_self_order.amp_product` | `4=提前付息按月还本` | 主脚本 `S02` |
| D03 | 产品 | 宽限天数 | 缺失 | 页面可配/脚本 | `grace_period_days=0` | `scf_self_order.amp_product` | 天数 | 主脚本 `S02` |
| D04 | 产品 | 商业险费率/期数 | 已配置，不能照抄山西 | 页面，无需处理 | 仅 9 期；渠道 `0.038`、运营 `0.032`、合计 `7.00%` | `scf_self_order.amp_product` | `commercial_insurance_rate` JSON | 保留现值 |
| D05 | 产品 | 首付/罚息 | 已配置且与山西一致 | 页面，无需处理 | 首付 `0.1`；罚息 `0.003` | `scf_self_order.amp_product` | 比例小数 | 保留现值 |
| D06 | 产品 | 产品配置组 | 缺失：山西 15 组，目标 0 组 | 页面“产品配置导入”或脚本；本次给脚本 | 复制山西全部 15 个有效组 | `scf_self_order.product_detail_group` | `label_code` 见下表 | 主脚本 `S03` |
| D07 | 产品 | 产品配置明细 | 缺失：山西 89 条，目标 0 条 | 页面“产品配置导入”或脚本；本次给脚本 | 复制山西全部 89 条有效明细 | `scf_self_order.product_detail` | `code/default_value/data_type` | 主脚本 `S04` |
| D08 | 产品 | 支持期数明细 | 复制后需覆盖 | 脚本 | `period=9` | `scf_self_order.product_detail` | `BASE_CONFIG.period`，分号分隔 | 主脚本 `S05` |
| D09 | 产品 | 首付比例明细 | 复制后需覆盖 | 脚本 | `InitialPaymentRatio={"9":0.1}` | `scf_self_order.product_detail` | JSON：期数→比例 | 主脚本 `S05` |
| D10 | 产品 | 费率上下限明细 | 复制后需覆盖 | 脚本 | `customerFeeRateMin={"9":0.001}`；`Max={"9":0.36}` | `scf_self_order.product_detail` | JSON：期数→年化范围 | 主脚本 `S05` |
| D11 | 产品 | 资金方服务费 | 复制后需收敛 | 脚本 | `fpFeeRate={"9":0.04}` | `scf_self_order.product_detail` | JSON：期数→比例；山西 9 期值沿用 | 主脚本 `S05` |
| D12 | 产品 | 还款计划策略 | 缺失，会被进件计算实际读取 | 脚本 | `repaymentPlanCalculation=ZY_Customer` | `scf_self_order.product_detail` | 对应 Spring 策略 `ZY_Customer_Calculation` / 校验策略 `ZY_Customer` | 随 `S04` 复制 |
| D13 | 产品 | 先行通服务费 | 缺失，后续结算使用 | 脚本 | `plateformFeeRate=0.005` | `scf_self_order.product_detail` | 比例小数 | 随 `S04` 复制 |
| D13-1 | 产品 | 申请单固化先行通费率 | 昨晚旧单已错误固化为 `0` | 新建进件 | 新单预审通过后 `application_loan.tech_service_fee_ratio=0.005` | `scf_self_order.application_loan` | 预审通过时从产品明细固化；旧单不会自动回填 | 预检脚本 `C01-1` 校验，禁止续用旧单 |
| D14 | 产品 | 还款日规则 | 缺失 | 脚本 | 原样复制山西三段日期规则 | `scf_self_order.product_detail` | JSON 规则 | 随 `S04` 复制 |
| D15 | 产品 | 绑卡支付渠道 | 缺失；山西历史值与目标路由不一致 | 脚本 | `payment_support_channel=JDPAY` | `scf_self_order.product_detail` | 支付渠道编码 | 主脚本 `S05` |
| D16 | 产品 | 缴费单科目 | 默认配置已齐全 | 默认字典，无需处理 | `DEF` 下 `201/10201/10203/103` 共 4 条 | `scf_self_saps.payment_slip_subject` | 产品专属不存在时自动回退 `DEF` | 预检脚本 `B06` 校验 |
| D17 | 产品 | 账单科目 | 默认配置已齐全 | 默认字典，无需处理 | `DEF` 下本链路必需科目 `101/10102/10201/10202/103` 共 5 条 | `scf_self_saps.bill_subject` | 产品专属不存在时自动回退 `DEF` | 预检脚本 `B06` 校验 |
| D18 | 产品 | 客户账单本金科目 | 默认配置已齐全 | 默认字典，无需处理 | `DEF/101`、`DEF/10102` 两种科目均存在 | `scf_self_saps.customer_bill_subject` | 商业险及非商业险本金；按 `DISTINCT subject_no` 校验，忽略现库历史重复行 | 预检 `B06/B08`；链路核验 `C04-1/C08` |
| D19 | 产品 | 9 期资金方主数据 | 已配置且启用 | 字典，无需处理 | 中化 `ZJF000015`支持 9 期商业险；通汇 `ZJF000011`支持 9 期 | `scf_self_order.org_funding_party` | 对应 `firstFunder/secondFunder` | 主脚本执行守卫 + 预检 `B07` |
| C01 | 渠道 | 渠道主数据 | 已配置 | 页面，无需处理 | `HZF000133`；自营；启用 | `scf_self_order.crm_channel` | `channel_type=2` 自营；`status=1` | 保留现值 |
| C02 | 渠道 | 订单类型 | 缺失 | 页面可配/脚本 | `orderType=2` | `scf_self_order.org_config` | `1=个人，2=企业，3=个人挂靠` | 主脚本 `S06` |
| C03 | 渠道 | 代偿付款 | 缺失，页面字段禁用 | 脚本 | `compensationPayment=1` | `scf_self_order.org_config` | `1=是，0=否` | 主脚本 `S06` |
| C04 | 渠道 | 退保现金余款结算 | 缺失，页面字段禁用 | 脚本 | `surrenderCashSurplusSettlement=1` | `scf_self_order.org_config` | `1=每月1日` | 主脚本 `S06` |
| C05 | 渠道 | 首付方式 | 缺失，页面字段禁用 | 脚本 | `downPaymentMethod=1` | `scf_self_order.org_config` | `1=客户直付，2=渠道转付` | 主脚本 `S06` |
| C06 | 渠道 | 线下还款方式 | 缺失，页面字段禁用 | 脚本 | `offlineRepaymentMethod=1` | `scf_self_order.org_config` | `1=客户直付，2=渠道转付` | 主脚本 `S06` |
| C07 | 渠道 | 清算方式 | 缺失，页面字段禁用 | 脚本 | `channelClearing=2` | `scf_self_order.org_config` | `1=实时，2=归集代付` | 主脚本 `S06` |
| C08 | 渠道 | 分润结算 | 缺失，页面字段禁用 | 脚本 | `channelProfitSharingSettlement=0` | `scf_self_order.org_config` | `1=结清后月结，0=不分润` | 主脚本 `S06` |
| C09 | 渠道 | 归集代付结算周期 | 缺失 | 页面可配/脚本 | `collectionPaymentSettlementCycle=1` | `scf_self_order.org_config` | `1=D+1` | 主脚本 `S06` |
| C10 | 渠道 | 支付业务渠道 | 缺失 | 脚本 | `PaymentConfig/pay_channel=JDPAY` | `scf_self_payment.business_config` | `query_type=COOPERATION` | 主脚本 `S07` |
| C11 | 渠道 | 支付路由 | 已配置，不能照抄山西 | 脚本已存在，无需处理 | `DS/JDPAY`，项目/产品均为目标编码 | `scf_self_payment.router` | 山西当前是 `DF/ALLINPAY`，不能覆盖 | 保留现值 |
| C12 | 渠道 | 支付机构参数 | 已配置 | 脚本已存在，无需处理 | `JDPAY-HZF000133-DEF` 有效 | `scf_self_payment.channel_pay_config` | 含商户/密钥/证书等渠道专属信息 | 绝不复制山西密钥 |
| C13 | 渠道 | 支付手续费 | 已配置，不能照抄山西 | 脚本已存在，无需处理 | JDPAY 协议支付 `0.002` | `scf_self_payment.channel_fee_config` | `AGREEMENT_PAYMENT` | 保留现值；山西 JDPAY 为 `0.0023` |
| C13-1 | 渠道 | BCM 单笔代付手续费 | **缺失，是放款硬前置** | 脚本 | 复制山西规则：`BCM/SINGLE_REMIT`、固定手续费 `0`、`card_type=1`、`bank_rule=OTHER`、平台手续费启用；`merchant_no=HZF000133` | `scf_self_payment.channel_fee_config` | `RemitBizServiceImpl.calculatePayFee` 强制按合作方+渠道+交易模式查询，缺失直接报 `CONFIG_NOT_EXIST` | 主脚本 `S07` |
| C13-2 | 渠道 | BCM 测试代付开关/地址 | DEF 已配置，可自动回退 | 默认字典，无需处理 | `RemitConfig/bcmMockFlag=true`；`bcmMockUrl` 非空 | `scf_self_payment.business_config` | `query_key=DEF, query_type=PROJECT`；项目无专属值时自动回退 | 主脚本守卫 + 预检 `B06-1` |
| C13-3 | 渠道 | 放款支付路由/渠道机构参数 | 无目标专属 BCM 配置，但本链路不读取 | 不处理 | 无 | `router` / `mer_channel_config` | SAPS `PaymentTask` 明确指定 `BCM`；`BcmStrategy` 使用服务级交行参数和上述 `RemitConfig` | 不复制山西 `DF` 路由或机构配置 |
| C14 | 渠道 | SAPS 支付结果通知 | 缺失 | 脚本 | `noticePaymentResult=false` | `scf_self_saps.business_config` | `AgreementPay` 组 | 主脚本 `S08` |
| C15 | 渠道 | SAPS 账单宽限日 | 缺失 | 脚本 | `1\|24-EM;4-6;14-6;` | `scf_self_saps.business_config` | `billConfig/graceDayFunder` | 主脚本 `S08` |
| C16 | 渠道 | SAPS 对账账户 | 缺失 | 脚本 | 测试环境山西四项均为 `test` | `scf_self_saps.business_config` | `accountBank/accountName/accountNo/unionBankCode` | 主脚本 `S08` |
| C17 | 渠道 | SAPS 余额账户 | 当前没有 | 不处理 | 保持无账户，首付通过 JDPAY 支付 | `scf_self_saps.account` | 缺少 `EXCESS_ACCOUNT` 时仅跳过余额自动结算，不阻塞缴费单创建 | 无 SQL；禁止复制山西余额 |
| T01 | 进件数据 | 保司收款及盖章信息 | 随新单填写 | 页面 | 名称/收款账号/开户行/联行号/摘要/盖章方式全部非空 | `application_insurance_company` | 提交进件强校验 | 预检 `C01-1A` |
| T02 | 进件数据 | 车辆商业险投保单 | 随新单上传 | 页面 | 线上盖章时每辆车必须有 `original_vci_file`，商业险金额大于 0 | `application_vehicle` | 提交进件及 `BINDING_SUCCESS` 签章前置 | 预检 `C01-1A` |

## 产品配置组字典（山西源的 15 组 / 89 条）

| `label_code` | 名称 | 明细数 |
| --- | --- | ---: |
| `BASE_CONFIG` | 基础配置 | 27 |
| `signConfig_500` | 商业险投保单签署配置 | 3 |
| `loan_attachment_check` | 放款附件校验配置 | 11 |
| `signConfig_510` | 交强险投保单签署配置 | 3 |
| `signConfig_520` | 附加险投保单签署配置 | 3 |
| `signConfig_220` | 委托代理服务合同签署配置 | 3 |
| `signConfig_230` | 委托扣款服务协议及授权书签署配置 | 4 |
| `signConfig_210` | 委托收付款协议签署配置 | 4 |
| `signConfig_211` | 委托支付协议签署配置 | 3 |
| `SAPS_CONFIG` | SAPS 配置 | 5 |
| `PAY_CONFIG` | 支付配置 | 1 |
| `APPLY_VALIDATION` | 活体视频校验配置 | 1 |
| `signConfig_800` | 保理合同签署配置 | 5 |
| `LOAN_VERIFICATION` | 放款 V1 接口合同校验 | 13 |
| `signConfig_240` | 应收账款转让通知书及回执签署配置 | 3 |

## 执行顺序

1. 先执行只读脚本 `scf-test-hzf000133-preflight-postcheck-20260813.sql` 的 A 段。
2. 执行主配置脚本 `scf-test-hzf000133-config-copy-20260813.sql`。
3. 执行合同脚本 `scf-test-hzf000133-contract-config-20260813.sql`。
4. 两份 DML 都只允许完整执行一次；若守卫报错，在同一会话执行 `ROLLBACK`，禁止忽略错误或重跑。
5. 执行只读脚本 B 段；只有 `B08.gate_result=GO` 才能进件，并确认 SAPS 场景仍为 0。
6. 执行环境门禁 B09；完整验证到客户账单前必须为 `READY`。若阻塞，执行 B09-1，按每个 `transfer_no` 单独触发 SAPS `transferTransactionSchedule` 后重查。
7. 新进件页面会显示 3/6/9，人工选择 9；3/6 会被后端产品配置拒绝。
8. 新进件后把申请单号填入只读脚本 C 段，跟踪缴费单、放款单、正式还款计划和 9 条客户账单；`C04-1` 每期账单与客户科目明细差额必须为 0，最终必须满足 `C08.end_to_end_result=PASS`。

> 2026-08-14 只读实测：目标 BCM 单笔代付手续费为 0 条，现已补入主脚本；`B09=BLOCKED_BY_STALE_SAPS_LOAN_TRANSFER`。订单库有 57 笔超时 340；其中 SAPS 有 2 笔 `status=2/real_retry_count=0` 仍可由 `transferTransactionSchedule` 补偿，另有 3 笔已耗尽 5 次。最近一次成功放款为 2026-07-14 15:35:15。后者属于测试环境补偿链路异常，会阻断“缴费成功后生成 9 期客户账单”的最终验证。

> 已直接验证测试环境 BCM Mock `310204` 返回 `particular_code=0000/stat=1`，说明 Mock 查单可返回成功；B09 阻塞的处置重点是触发 SAPS 查单补偿并确认回调，不需要修改 BCM Mock 或新增放款路由。`B09=READY` 仅代表当前积压清空，受控库不含 XXL-Job 管理表，不能据此认定自动调度已恢复。

## 仍需代码适配的点

- `loanSignJob` 的待放款成功合同盖章查询仍硬编码 `HZF000144`。它发生在放款成功、客户账单之后，不阻塞本次“出客户账单”，但江苏易吾放款后合同盖章不会自动执行。
- `scf-saps` 山西结算扩展坐标只绑定 `XXT-PRJ-ZY-A`。本次不配置目标结算场景，因此不阻塞缴费结果通知和客户账单保存；但后续对账/清结算能力尚未完成。
- `FinanceApplicationService` 还有 `HZF000144` 硬编码，影响后续融资申请链路，不是本次首次放款出客户账单的前置条件。
- `pushRepaymentPlanSchedule` 的空任务参数会被原样作为 `loan_id` 查询条件，通常查不到待推计划；而 `null` 批量路径又存在分组参数未收敛的问题。正常出账走放款成功处理时的同步推送；补偿时只能按单笔放款申请号触发。
