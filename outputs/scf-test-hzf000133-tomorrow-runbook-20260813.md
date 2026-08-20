# HZF000133 明日进件执行单

## 固定上下文

- 业务线：保费分期 `BL000001`，GitLab group `scf`
- 环境：测试环境
- 代码分支：`master`（用户指定）
- 源配置：`HZF000144 / XXT-PRJ-ZY-A / PRD-ZY-A001`
- 目标配置：`HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001`
- 目标产品：仅 9 期，`amp_product.id=278`

## 明日执行顺序

1. 数据库客户端先开启“遇错停止/Stop on error”；任何 DML 报错都立即执行 `ROLLBACK`，不要继续到 `COMMIT`。
2. 先执行只读核验脚本的 A 段：`scf-test-hzf000133-preflight-postcheck-20260813.sql`。
3. 确认目标主数据各 1 条、启用，支付路由为 `DS/JDPAY`，JDPAY 支付机构参数和协议支付费率存在，DEF 的 BCM Mock 配置就绪；同时确认源数量仍为 `15/89/44/8/32/39/1/1/8`（其中项目附件 44 条），四类配置重复键均为 0。目标项目附件与 `BCM/SINGLE_REMIT` 执行前都应为 0，主脚本执行后应分别为 44 和 1。
4. 执行基础配置脚本：`scf-test-hzf000133-config-copy-20260813.sql`。
5. 执行合同脚本：`scf-test-hzf000133-contract-config-20260813.sql`。
6. 再执行只读核验脚本的 B 段；任何硬性数量或关键值不符，都不要开始进件。
7. 执行 B09 环境健康检查。若不是 `READY`，执行 B09-1，按查询出的每个 `transfer_no` 在 SAPS XXL-Job 管理端单独触发 `transferTransactionSchedule`，再重跑 B09；否则新单即使缴费成功，也可能停在放款 `340`，无法出客户账单。
8. 必须新建一笔进件，不要续用昨晚旧单；页面会同时显示 3/6/9 期，必须人工选择 **9 期**。取得新申请单号后替换核验脚本中的 `@application_no`，用 C 段跟踪。
9. 链路走完后执行 C08：只有 `end_to_end_result=PASS` 才算验收完成；`WAIT` 按 C01~C07 定位等待环节，`FAIL` 立即停止后续操作。

> 两份 DML 脚本现在都有执行前和 `COMMIT` 前强制守卫，并且都只允许完整执行一次。若看到 `chk_hzf000133_*_guard` 约束失败，立即在同一会话执行 `ROLLBACK;`，不得开启“忽略错误继续”，不得手工单独执行文件后面的 `COMMIT`，也不得重跑脚本。

## 最终文件校验

- 已用 MySQL 8.4.10 隔离实例对三份 SQL 做全文件解析，未出现 `1064` 语法错误；测试库版本为 MySQL 8.4.1，相关写入表均为 InnoDB。
- 基础配置 SQL SHA-256：`4c695210f00c479ee79de5ac52184eae13caf3ccec8a6a10373da3737d551f10`
- 合同配置 SQL SHA-256：`a7e8922b5f2b41594f82a5a6839c82bb7911dce5459540b93a875e91650f7f5f`
- 核验 SQL SHA-256：`edb84b96115bf3fb0ac8ded08e73a45926114f6be571868c78c94c417a0bcc83`
- 若明日文件摘要不同，先重新确认差异，不要直接执行。

## 执行后放行条件

| 检查项 | 必须值 |
| --- | --- |
| 产品配置组 | 15 |
| 产品配置明细 | 89 |
| 项目附件配置 | 44 |
| 渠道组织配置 | 8 |
| 合同配置 | 32 |
| 合同签署人 | 39 |
| 居间服务协议 | 0 |
| 仅商业险客户签约命中 | 4份合同、5个签署人 |
| 投保签章配置 | 3份附件合同、3个签署人 |
| PaymentConfig/pay_channel | `JDPAY` |
| JDPAY 路由必填字段 | 6项存在性均为 `1` |
| BCM 单笔代付手续费 | `HZF000133/BCM/SINGLE_REMIT` 恰好 1 条，固定手续费 `0` |
| BCM 测试代付配置 | DEF `bcmMockFlag=true`，`bcmMockUrl` 非空 |
| SAPS 业务配置 | 8 |
| SAPS statement_scene | 0 |
| 产品期数 | `9` |
| 还款计划策略 | `ZY_Customer` |
| 产品主表 repayment_type | `4` |
| 产品主表 project_no | `XXT-PRJ-YW-A` |
| 新单固化 tech_service_fee_ratio | `0.005` |
| 缴费单默认科目 | 4 个必需科目齐全 |
| 账单默认科目 | 5 个必需科目齐全 |
| 客户账单本金科目 | `DEF/101`、`DEF/10102` 两种科目均存在（按编码去重校验） |
| 9 期资金方 | 中化 `ZJF000015` + 通汇 `ZJF000011` 均启用且支持 9 期 |
| 最终放行闸门 | 预检 `B08` 只能为 `GO` |
| 放款环境门禁 | `B09.environment_result=READY` |
| 最终链路验收 | 新单核验 `C08` 只能为 `PASS` |

## 流程观察点

1. 前端期数选项写死显示 3/6/9，但后端会按产品明细校验；本产品必须选择 9 期。选 3 或 6 会返回“产品暂不支持该期数”，不是配置脚本未生效。
2. 保险公司签约完成后应生成首付缴费单。
3. 缴费单 `status=4,notify=1` 才表示支付成功并已成功通知订单系统；`status=4,notify=0` 是待通知补偿。
4. 缴费结果通知成功后，订单应进入 `330` 待放款并生成放款单；山西成功单的基准是放款单 `status=351` 且 `payment_time` 非空。
5. 放款成功状态被业务系统处理时，会同步生成正式还款计划并调用 SAPS 出账；正常情况下不需要等定时任务，9 期应生成 9 条客户账单，且 C04-1 每期 `detail_diff_must_be_0=0`。

### 进件当日即时判断

- 到 `102`（预审通过）但还没有 `application_loan.loan_period`：还只是预审后未填写/保存分期方案，不是产品配置失败。
- 昨晚旧单 `APA260813205018000001` 在产品明细为空时已经预审通过，`tech_service_fee_ratio` 被固化成 `0`；脚本执行后不能继续该单，必须新建进件并确认该字段固化为 `0.005`。
- 提交方案前必须确认首笔款大于 0，且 C01-1 的 `first_payment_diff_must_be_0=0`。
- 项目单笔范围为 `1~500000` 元；目标项目总额度 1000 万、当前保证金 100 万、保证金率 10%。若在范围内仍提示“额度验证失败”，先查在贷统计接口，不要重复执行配置脚本。
- 保司必填：保司名称、收款账号、开户行、联行号、付款摘要、盖章方式。为保证自动走到缴费单，本次建议选择线上盖章，并为每辆车上传清晰的商业险投保单原件。
- 提交进件前执行 C01-1A：`insurance_company_required_fields_present=1`，线上盖章时 `vci_file_count=vehicle_count`，且 `positive_vci_amount_count=vehicle_count`。
- 到 `220`：待绑卡；到 `230`：客户合同待签；到 `231`：保司投保单待签；到 `240`：应已触发缴费单生成。
- 绑卡确认成功后，C01-3 必须查到 `HZF000133/JDPAY/status=1`，且两个协议号存在性均为 `1`；目标渠道当前还没有历史成功绑卡记录，所以这是今日第一笔真实验证。
- C01-2 中客户签约和每辆车投保签章最终都必须为 `SIGN_SUCCESS`；否则先处理签约，不要把问题归到 SAPS。

## 缴费单和放款单未自动生成时

1. 订单为 `240`、`application_loan.first_payment_slip_no` 为空：先用 C01-1 确认首笔款和费用守恒，再等待 `autoRetryGeneratePaymentSlip`。取得本次测试授权和有效鉴权后，可按单笔调用 `POST /api/application/v2/payment/slip`，请求体仅传 `{"applicationNo":"新申请单号"}`。代码同时检查状态、既有缴费单号并加单笔锁，但仍禁止并发重复点击。
2. C02 已有缴费单：不得再调生成接口；先核对 C02-1 的三个差额是否均为 0。
3. 缴费单 `status=4,notify=0,retry_count<5`：不要重复付款；完成时间超过 180 秒后等待/按单笔触发 `paymentSlipRetryNotifySchedule`，任务参数可用 `{"serialNo":"缴费单号"}`。最多重试 5 次，达到 5 次时不要反复重跑，应先查通知失败原因。
4. 订单已为 `330`、但 C03 无放款单：等待 `autoRetryApplyLoan`，或按单笔任务参数 `["新申请单号"]` 触发。如必须人工调用，只能在确认 C03 无有效放款单、取得高风险写链路授权后，按单笔使用 `GET /api/loan/zyLoanApply?applicationNo={新申请单号}`。该接口会发起真实测试环境放款申请，不得盲目重试。
5. C03 已有有效放款单：不得再调 `zyLoanApply`；改为跟踪该放款单的签约、放款和状态查询。
6. 放款单为 `340` 且 C03-1 的 `payment_result=2` 超过10分钟：执行 C03-2。若 SAPS 转账为 `status=2` 且 `real_retry_count<5`，从 **SAPS 的 XXL-Job 管理端**触发 `transferTransactionSchedule`，任务参数为 `{"transferNos":["C03-2查到的transfer_no"]}`。该任务会用 `payment_req_id` 到支付系统查单，再由 SAPS 流程回调订单系统。禁止再次申请放款。
7. 不要使用 scf-order 的 `loanPaymentQueryStatusJob`：`master` 创建 SAPS 转账时 `business_no=申请单号`，该任务却用 `loan_payment.pay_apply_no` 查询，键不一致，处理中订单会查不到。
8. 不要通过 `/api/debug/xxljob` 间接触发上述单笔任务：该接口会再次 JSON 序列化参数，容易让任务收到带引号或结构不符的值。直接在对应服务的 XXL-Job 管理端填写原始 JSON。
9. `B09=READY` 只表示当前可补偿积压已清空。当前受控数据库看不到 XXL-Job 管理库，无法证明定时调度已经恢复；新单进入 `340` 超过 5 分钟时，仍必须执行 C03-2 并按该单 `transfer_no` 补偿。

## 客户账单未生成时的处置顺序

1. 先执行核验脚本 C03、C05、C06，确认放款单是否已到 `351`，以及正式还款计划是否已生成。
2. `repayment_plan` 为 0 条：不要直接补推；说明放款成功结果尚未完成业务处理，先排查/触发正常的放款状态查询链路。
3. 已有 9 条主计划且均为 `status=1,is_lock=0`，但客户账单为 0：说明订单侧认为已推送成功，转查 SAPS `generateBillV2` 调用和事务日志，禁止重复无脑补推。
4. 已有 9 条主计划且为 `status=0,is_lock=1`：等待计划创建满 5 分钟后，可在取得本次测试授权和有效鉴权的前提下，按单笔调用 `GET /api/loan/pushAllRepaymentPlan?loanApplicationNo={放款申请号}`。
5. 不要用空参数批量补跑：`master` 的定时处理把任务参数原样作为 `loan_id` 条件，空字符串通常查不到数据；若传入 `null` 批量执行，代码又没有把每组计划正确收敛到当前分组，存在串单风险。

## 重要风险边界

- 当前只读库实测：8月13日山西两笔新单的缴费单均已 `status=4,notify=1`，但放款仍停在 340。对应 SAPS 转账均为 `status=2 / WaitLoanOrderPaymentCallBackTask / real_retry_count=0`，支付系统 BCM mock 订单也为 `status=2`。截至 2026-08-14，订单库共有 **57 笔**超时 340；SAPS 有 **2 笔**仍可补偿、**3 笔**已耗尽 5 次。最近一次订单放款成功时间为 **2026-07-14 15:35:15**。明天要验证到客户账单，必须先让 SAPS `transferTransactionSchedule` 能正常查单推进。
- 2026-08-14 已直接验证测试环境 BCM Mock `310204`：HTTP 200，`particular_code=0000`，`stat=1`（成功）。因此当前阻塞不是 Mock 不返回成功，而是 SAPS 的处理中转账尚未执行查单补偿。触发 B09-1 列出的两笔后，应先观察 SAPS 转账变为成功、订单放款变为 `351`，再确认客户账单生成；若仍未推进，再查 SAPS/订单回调日志。

- 不要复制或新建 `statement_scene` / `statement_scene_rule`。当前 `master` 的 SAPS 扩展点只注册了 `XXT-PRJ-ZY-A`；目标项目配了场景反而可能中断缴费结果通知或回滚客户账单。
- 不要复制 `org_cooperation`。目标新自营接口按 `crm_channel` 和 `amp_project.channel_codes=HZF000133` 找项目；`org_cooperation` 是旧合作方 API 的鉴权主体，包含企业证照、法人、银行卡等渠道专属资料，复制山西记录会造成主体冒用。目标项目 `cooperation_id` 为空不阻塞 `/api/application/v2/blank`。
- 不要复制山西支付密钥、证书、JDPAY 商户参数、支付路由和 JDPAY 费率。目标已单独配置 `DS/JDPAY`、`channel_pay_config` 和 `0.002` 协议支付费率。唯一需要复制的是不含密钥的 BCM 单笔代付手续费规则，并把合作方编码替换为 `HZF000133`。
- 放款无需新增目标 `DF` 路由或 `mer_channel_config`：`master` 的 SAPS `PaymentTask` 明确指定 BCM，支付侧 `BcmStrategy` 使用服务级交行参数；BCM Mock 开关和地址由现有 DEF 配置兜底。
- 目标 SAPS 当前没有渠道余额账户。这不影响生成缴费单，也不影响通过 JDPAY 支付；但不能按山西“余额账户自动结算”的方式测试。
- 客户账单不是首付成功后立即生成：还需要放款申请、放款成功、放款成功结果被订单系统处理并生成正式还款计划。定时任务只是失败后的补偿路径，不应作为首次出账的唯一依赖。
- `product_instance` 不是本链路的强制前置；`master` 在无实例时会回退当前产品配置。但新单仍必须在脚本后创建，因为 `tech_service_fee_ratio` 会在预审通过时提前固化。
- 项目表中的 `period=3` 是“非商业险融资期数”；目标项目 `insurance_type=1`（仅商业险），所以它不控制本次 9 期产品，脚本不会把该字段改成 9。
- 合同服务请求当前把 `productCode` 固定传为 `PRD-ZY-A001`。本地合同选择仍按目标项目 `XXT-PRJ-YW-A`，且该固定值是现有自营合同服务路由值；今日先沿用，不要改数据库合同模板编码。若发起签约返回合同平台“产品不存在/无权限”等路由类错误，再升级为代码适配项。
- `HZF000133` 昨晚已有一笔预审通过单在上传身份证时出现过一次 OCR 失败记录。该单仍到达 `102`，说明不是目标渠道配置错误；今日上传材料若再次 OCR 失败，应换清晰原图/重传，不要改本次配置脚本。
- 当前 WorkHub 测试上下文没有 scf 服务入口和 scf 服务器日志权限，因此这次只能完成代码、脚本和只读数据库核查，无法在今晚替你做真实接口/支付/放款正向验证。
- 支付和放款是高风险写链路，执行时只能使用已确认的测试数据、测试商户和允许支付/放款的安全策略。

## 已核对的成功基准

- 山西 9 期成功样本：`APA260714141026000001`。
- 该单首付缴费单成功，放款单为 `351`，SAPS 最终生成 9 条客户账单。
- 山西应用和放款单的 `funder_code=GYS000006` 是现有自营链路的实际值，因此江苏易吾空白订单出现相同值不是本次配置错误。
