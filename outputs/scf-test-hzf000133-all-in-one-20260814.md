# HZF000133 新渠道进件配置、执行与验收手册（合并版）

> 业务线：保费分期 `BL000001`｜GitLab 组：`scf`｜环境：测试环境｜分支：`master`
>
> 源配置：`HZF000144 / XXT-PRJ-ZY-A / PRD-ZY-A001`
>
> 目标配置：`HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001`
>
> 生成日期：2026-08-14。本文件整合执行手册、配置清单和三份完整 SQL；原 SQL 文件仍是实际执行源文件。

## 一、明日进件执行手册

## HZF000133 明日进件执行单

### 固定上下文

- 业务线：保费分期 `BL000001`，GitLab group `scf`
- 环境：测试环境
- 代码分支：`master`（用户指定）
- 源配置：`HZF000144 / XXT-PRJ-ZY-A / PRD-ZY-A001`
- 目标配置：`HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001`
- 目标产品：仅 9 期，`amp_product.id=278`

### 明日执行顺序

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

### 最终文件校验

- 已用 MySQL 8.4.10 隔离实例对三份 SQL 做全文件解析，未出现 `1064` 语法错误；测试库版本为 MySQL 8.4.1，相关写入表均为 InnoDB。
- 基础配置 SQL SHA-256：`4c695210f00c479ee79de5ac52184eae13caf3ccec8a6a10373da3737d551f10`
- 合同配置 SQL SHA-256：`a7e8922b5f2b41594f82a5a6839c82bb7911dce5459540b93a875e91650f7f5f`
- 核验 SQL SHA-256：`edb84b96115bf3fb0ac8ded08e73a45926114f6be571868c78c94c417a0bcc83`
- 若明日文件摘要不同，先重新确认差异，不要直接执行。

### 执行后放行条件

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

### 流程观察点

1. 前端期数选项写死显示 3/6/9，但后端会按产品明细校验；本产品必须选择 9 期。选 3 或 6 会返回“产品暂不支持该期数”，不是配置脚本未生效。
2. 保险公司签约完成后应生成首付缴费单。
3. 缴费单 `status=4,notify=1` 才表示支付成功并已成功通知订单系统；`status=4,notify=0` 是待通知补偿。
4. 缴费结果通知成功后，订单应进入 `330` 待放款并生成放款单；山西成功单的基准是放款单 `status=351` 且 `payment_time` 非空。
5. 放款成功状态被业务系统处理时，会同步生成正式还款计划并调用 SAPS 出账；正常情况下不需要等定时任务，9 期应生成 9 条客户账单，且 C04-1 每期 `detail_diff_must_be_0=0`。

#### 进件当日即时判断

- 到 `102`（预审通过）但还没有 `application_loan.loan_period`：还只是预审后未填写/保存分期方案，不是产品配置失败。
- 昨晚旧单 `APA260813205018000001` 在产品明细为空时已经预审通过，`tech_service_fee_ratio` 被固化成 `0`；脚本执行后不能继续该单，必须新建进件并确认该字段固化为 `0.005`。
- 提交方案前必须确认首笔款大于 0，且 C01-1 的 `first_payment_diff_must_be_0=0`。
- 项目单笔范围为 `1~500000` 元；目标项目总额度 1000 万、当前保证金 100 万、保证金率 10%。若在范围内仍提示“额度验证失败”，先查在贷统计接口，不要重复执行配置脚本。
- 保司必填：保司名称、收款账号、开户行、联行号、付款摘要、盖章方式。为保证自动走到缴费单，本次建议选择线上盖章，并为每辆车上传清晰的商业险投保单原件。
- 提交进件前执行 C01-1A：`insurance_company_required_fields_present=1`，线上盖章时 `vci_file_count=vehicle_count`，且 `positive_vci_amount_count=vehicle_count`。
- 到 `220`：待绑卡；到 `230`：客户合同待签；到 `231`：保司投保单待签；到 `240`：应已触发缴费单生成。
- 绑卡确认成功后，C01-3 必须查到 `HZF000133/JDPAY/status=1`，且两个协议号存在性均为 `1`；目标渠道当前还没有历史成功绑卡记录，所以这是今日第一笔真实验证。
- C01-2 中客户签约和每辆车投保签章最终都必须为 `SIGN_SUCCESS`；否则先处理签约，不要把问题归到 SAPS。

### 缴费单和放款单未自动生成时

1. 订单为 `240`、`application_loan.first_payment_slip_no` 为空：先用 C01-1 确认首笔款和费用守恒，再等待 `autoRetryGeneratePaymentSlip`。取得本次测试授权和有效鉴权后，可按单笔调用 `POST /api/application/v2/payment/slip`，请求体仅传 `{"applicationNo":"新申请单号"}`。代码同时检查状态、既有缴费单号并加单笔锁，但仍禁止并发重复点击。
2. C02 已有缴费单：不得再调生成接口；先核对 C02-1 的三个差额是否均为 0。
3. 缴费单 `status=4,notify=0,retry_count<5`：不要重复付款；完成时间超过 180 秒后等待/按单笔触发 `paymentSlipRetryNotifySchedule`，任务参数可用 `{"serialNo":"缴费单号"}`。最多重试 5 次，达到 5 次时不要反复重跑，应先查通知失败原因。
4. 订单已为 `330`、但 C03 无放款单：等待 `autoRetryApplyLoan`，或按单笔任务参数 `["新申请单号"]` 触发。如必须人工调用，只能在确认 C03 无有效放款单、取得高风险写链路授权后，按单笔使用 `GET /api/loan/zyLoanApply?applicationNo={新申请单号}`。该接口会发起真实测试环境放款申请，不得盲目重试。
5. C03 已有有效放款单：不得再调 `zyLoanApply`；改为跟踪该放款单的签约、放款和状态查询。
6. 放款单为 `340` 且 C03-1 的 `payment_result=2` 超过10分钟：执行 C03-2。若 SAPS 转账为 `status=2` 且 `real_retry_count<5`，从 **SAPS 的 XXL-Job 管理端**触发 `transferTransactionSchedule`，任务参数为 `{"transferNos":["C03-2查到的transfer_no"]}`。该任务会用 `payment_req_id` 到支付系统查单，再由 SAPS 流程回调订单系统。禁止再次申请放款。
7. 不要使用 scf-order 的 `loanPaymentQueryStatusJob`：`master` 创建 SAPS 转账时 `business_no=申请单号`，该任务却用 `loan_payment.pay_apply_no` 查询，键不一致，处理中订单会查不到。
8. 不要通过 `/api/debug/xxljob` 间接触发上述单笔任务：该接口会再次 JSON 序列化参数，容易让任务收到带引号或结构不符的值。直接在对应服务的 XXL-Job 管理端填写原始 JSON。
9. `B09=READY` 只表示当前可补偿积压已清空。当前受控数据库看不到 XXL-Job 管理库，无法证明定时调度已经恢复；新单进入 `340` 超过 5 分钟时，仍必须执行 C03-2 并按该单 `transfer_no` 补偿。

### 客户账单未生成时的处置顺序

1. 先执行核验脚本 C03、C05、C06，确认放款单是否已到 `351`，以及正式还款计划是否已生成。
2. `repayment_plan` 为 0 条：不要直接补推；说明放款成功结果尚未完成业务处理，先排查/触发正常的放款状态查询链路。
3. 已有 9 条主计划且均为 `status=1,is_lock=0`，但客户账单为 0：说明订单侧认为已推送成功，转查 SAPS `generateBillV2` 调用和事务日志，禁止重复无脑补推。
4. 已有 9 条主计划且为 `status=0,is_lock=1`：等待计划创建满 5 分钟后，可在取得本次测试授权和有效鉴权的前提下，按单笔调用 `GET /api/loan/pushAllRepaymentPlan?loanApplicationNo={放款申请号}`。
5. 不要用空参数批量补跑：`master` 的定时处理把任务参数原样作为 `loan_id` 条件，空字符串通常查不到数据；若传入 `null` 批量执行，代码又没有把每组计划正确收敛到当前分组，存在串单风险。

### 重要风险边界

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

### 已核对的成功基准

- 山西 9 期成功样本：`APA260714141026000001`。
- 该单首付缴费单成功，放款单为 `351`，SAPS 最终生成 9 条客户账单。
- 山西应用和放款单的 `funder_code=GYS000006` 是现有自营链路的实际值，因此江苏易吾空白订单出现相同值不是本次配置错误。

---

## 二、项目／产品／渠道配置清单

## 江苏易吾新自营渠道配置清单

### 排查上下文

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

### 配置矩阵

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

### 产品配置组字典（山西源的 15 组 / 89 条）

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

### 执行顺序

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

### 仍需代码适配的点

- `loanSignJob` 的待放款成功合同盖章查询仍硬编码 `HZF000144`。它发生在放款成功、客户账单之后，不阻塞本次“出客户账单”，但江苏易吾放款后合同盖章不会自动执行。
- `scf-saps` 山西结算扩展坐标只绑定 `XXT-PRJ-ZY-A`。本次不配置目标结算场景，因此不阻塞缴费结果通知和客户账单保存；但后续对账/清结算能力尚未完成。
- `FinanceApplicationService` 还有 `HZF000144` 硬编码，影响后续融资申请链路，不是本次首次放款出客户账单的前置条件。
- `pushRepaymentPlanSchedule` 的空任务参数会被原样作为 `loan_id` 查询条件，通常查不到待推计划；而 `null` 批量路径又存在分组参数未收敛的问题。正常出账走放款成功处理时的同步推送；补偿时只能按单笔放款申请号触发。

---

## 三、基础配置 SQL

源文件：`scf-test-hzf000133-config-copy-20260813.sql`

```sql
-- 保费分期测试环境：江苏易吾新自营渠道配置补齐
-- 业务线：BL000001 保费分期（GitLab group: scf）
-- 环境/分支：测试环境 / master（用户指定）
-- 源：HZF000144 / XXT-PRJ-ZY-A / PRD-ZY-A001
-- 目标：HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001
--
-- 重要边界：
-- 1. 复制山西自营的配置结构和运行规则，但不覆盖目标唯一编码、项目额度、地域、支付路由和费率。
-- 2. 江苏易吾产品已确认只支持 9 期；复制产品明细后强制把期数相关配置收敛为 9 期。
-- 3. 本脚本不复制支付密钥、证书等渠道专属敏感数据；仅复制运行时强制查询的 BCM 单笔代付手续费规则，并把合作方编码替换为目标渠道。
-- 4. 合同配置见同目录 scf-test-hzf000133-contract-config-20260813.sql。
-- 5. 明确不复制 SAPS statement_scene/statement_scene_rule：master 代码尚未注册江苏易吾项目扩展点；误配会中断缴费单通知或客户账单事务。
-- 6. DML 本身使用 INSERT ... SELECT ... NOT EXISTS；但执行守卫会要求目标仍为已核对的空白基线，防止并发/误重跑。
-- 7. 项目附件配置属于运行配置：页面附件回显和放款后保单补传都会按项目号读取，随项目复制山西现有 44 条。
-- 8. org_cooperation 不复制：新自营链路使用 crm_channel + amp_project.channel_codes；目标项目 cooperation_id 为空是当前模型设计，
--    旧合作方表还包含主体、证件、银行卡等渠道专属资料，不能从山西渠道冒名复制。
-- 9. 必须在“遇错停止”模式下执行；任一 guard 报错立即 ROLLBACK，禁止跳过错误继续 COMMIT。

SET @source_channel = 'HZF000144';
SET @target_channel = 'HZF000133';
SET @source_project = 'XXT-PRJ-ZY-A';
SET @target_project = 'XXT-PRJ-YW-A';
SET @source_product = 'PRD-ZY-A001';
SET @target_product = 'PRD-YW-A001';

-- 为临时守卫表显式选定 schema，避免客户端未预选数据库时报 No database selected。
USE `scf_self_order`;

-- 执行前可视化核对。
SELECT code, id, status, repayment_type, grace_period_days
FROM `scf_self_order`.amp_product
WHERE code IN (@source_product, @target_product)
  AND is_delete = 0
  AND is_cancel = 0
ORDER BY code, id;

-- 强制执行守卫（MySQL 8.4 CHECK 约束）。
-- 下列任意一条不满足都会直接报错，不得手工忽略错误继续执行。
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_config_guard`;
CREATE TEMPORARY TABLE `scf_self_order`.`_hzf000133_config_guard` (
    check_name VARCHAR(100) NOT NULL PRIMARY KEY,
    passed TINYINT NOT NULL,
    CONSTRAINT `chk_hzf000133_config_guard` CHECK (passed = 1)
);

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('source_and_target_product_unique',
 IF((SELECT COUNT(*) FROM `scf_self_order`.amp_product
     WHERE code IN (@source_product,@target_product) AND is_delete=0 AND is_cancel=0)=2
    AND (SELECT COUNT(*) FROM `scf_self_order`.amp_product
         WHERE code=@source_product AND id=265 AND status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.amp_product
         WHERE code=@target_product AND id=278 AND status=1 AND is_delete=0 AND is_cancel=0)=1,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('source_config_counts_15_89_44_8_1_1_8',
 IF((SELECT COUNT(*) FROM `scf_self_order`.product_detail_group
     WHERE product_id=265 AND is_delete=0 AND is_cancel=0)=15
    AND (SELECT COUNT(*) FROM `scf_self_order`.product_detail
         WHERE product_id=265 AND is_delete=0 AND is_cancel=0)=89
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@source_project AND is_delete=0 AND is_cancel=0)=44
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_config
         WHERE org_code=@source_channel AND org_type='CHANNEL' AND org_group_key='DEFAULT'
           AND is_delete=0 AND is_cancel=0)=8
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key=@source_channel AND query_type='COOPERATION'
           AND group_key='PaymentConfig' AND config_key='pay_channel'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@source_channel AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT'
           AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
           AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN (@source_channel,@source_project) AND is_delete=0 AND is_cancel=0)=8,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('target_config_still_empty',
 IF((SELECT COUNT(*) FROM `scf_self_order`.product_detail_group WHERE product_id=278)=0
    AND (SELECT COUNT(*) FROM `scf_self_order`.product_detail WHERE product_id=278)=0
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@target_project)=0
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_config WHERE org_code=@target_channel)=0
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config WHERE query_key=@target_channel)=0
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@target_channel AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT')=0
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN (@target_channel,@target_project))=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('source_config_natural_keys_unique',
 IF((SELECT COUNT(*) FROM (
       SELECT label_code
       FROM `scf_self_order`.product_detail_group
       WHERE product_id=265 AND is_delete=0 AND is_cancel=0
       GROUP BY label_code HAVING COUNT(*)>1
     ) duplicated_group)=0
    AND (SELECT COUNT(*) FROM (
       SELECT g.label_code,d.code
       FROM `scf_self_order`.product_detail d
       JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=265 AND g.product_id=265
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0
       GROUP BY g.label_code,d.code HAVING COUNT(*)>1
     ) duplicated_detail)=0
    AND (SELECT COUNT(*) FROM (
       SELECT org_type,org_group_key,org_detail_key
       FROM `scf_self_order`.org_config
       WHERE org_code=@source_channel AND org_type='CHANNEL' AND org_group_key='DEFAULT'
         AND is_delete=0 AND is_cancel=0
       GROUP BY org_type,org_group_key,org_detail_key HAVING COUNT(*)>1
     ) duplicated_org)=0
    AND (SELECT COUNT(*) FROM (
       SELECT query_type,group_key,config_key
       FROM `scf_self_payment`.business_config
       WHERE query_key=@source_channel AND query_type='COOPERATION'
         AND group_key='PaymentConfig' AND config_key='pay_channel'
         AND is_delete=0 AND is_cancel=0
       GROUP BY query_type,group_key,config_key HAVING COUNT(*)>1
     ) duplicated_payment_business)=0
    AND (SELECT COUNT(*) FROM (
       SELECT query_key,query_type,group_key,config_key
       FROM `scf_self_saps`.business_config
       WHERE query_key IN (@source_channel,@source_project) AND is_delete=0 AND is_cancel=0
       GROUP BY query_key,query_type,group_key,config_key HAVING COUNT(*)>1
     ) duplicated_saps_business)=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('target_master_data_ready',
 IF((SELECT COUNT(*) FROM `scf_self_order`.amp_project
     WHERE no=@target_project AND channel_codes=@target_channel AND status=1
       AND effect_date<=NOW()
       AND margin_ratio=10 AND current_margin_amount>0 AND total_limit>0
       AND supplier_id=6 AND technical_service_provider_id=2
       AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.crm_channel
         WHERE channel_code=@target_channel AND type=2 AND status=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_config
         WHERE project_id=@target_project AND insurance_type='1'
           AND JSON_LENGTH(product_ids)=3
           AND JSON_CONTAINS(product_ids,JSON_OBJECT('productId',@target_product,'level','A'))
           AND JSON_CONTAINS(product_ids,JSON_OBJECT('productId',@target_product,'level','B'))
           AND JSON_CONTAINS(product_ids,JSON_OBJECT('productId',@target_product,'level','C'))
           AND single_limit_min=1 AND single_limit_max=500000
           AND pre_risk_model='PRD-JBR-001' AND apply_risk_model='PRD-JBR-001'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_config
         WHERE project_id=@target_project AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_funding_party
         WHERE org_code='ZJF000015' AND status=1
           AND JSON_CONTAINS(periods,'"9"')
           AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
           AND supported_insurance_types='1'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_funding_party
         WHERE org_code='ZJF000011' AND status=1
           AND JSON_CONTAINS(periods,'"9"')
           AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
           AND is_delete=0 AND is_cancel=0)=1,1,0));

-- 支付和出账公共运行项必须在第一条 DML 前已就绪；本脚本不会复制目标专属密钥或路由。
INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('target_payment_and_saps_runtime_ready',
 IF((SELECT COUNT(*) FROM `scf_self_payment`.router
     WHERE cooperation_code=@target_channel AND project_no=@target_project
       AND product_code=@target_product AND payment_product='DS' AND channel='JDPAY'
       AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_pay_config
         WHERE cooperation_code=@target_channel AND channel_code='JDPAY' AND status=1
           AND channel_url IS NOT NULL AND channel_url<>''
           AND member_id IS NOT NULL AND member_id<>''
           AND terminal_id IS NOT NULL AND terminal_id<>''
           AND resv_one IS NOT NULL AND resv_one<>''
           AND notify_url IS NOT NULL AND notify_url<>''
           AND pay_config_code IS NOT NULL AND pay_config_code<>''
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_pay_config tgt
         JOIN `scf_self_payment`.channel_pay_config src
           ON src.cooperation_code=@source_channel AND src.channel_code='JDPAY' AND src.status=1
          AND src.is_delete=0 AND src.is_cancel=0
         WHERE tgt.cooperation_code=@target_channel AND tgt.channel_code='JDPAY' AND tgt.status=1
           AND tgt.channel_url=src.channel_url AND tgt.member_id=src.member_id
           AND tgt.terminal_id=src.terminal_id AND tgt.resv_one=src.resv_one
           AND tgt.notify_url=src.notify_url
           AND tgt.is_delete=0 AND tgt.is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@target_channel AND channel_code='JDPAY'
           AND transaction_mode='AGREEMENT_PAYMENT' AND channel_fee_rate=0.002
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
           AND config_key='bcmMockFlag' AND config_value='true' AND config_status=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
           AND config_key='bcmMockUrl' AND config_value IS NOT NULL AND config_value<>''
           AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.payment_slip_subject
         WHERE product_code='DEF' AND subject_no IN ('201','10201','10203','103')
           AND is_delete=0 AND is_cancel=0)=4
    AND (SELECT COUNT(*) FROM `scf_self_saps`.bill_subject
         WHERE product_code='DEF' AND subject_no IN ('101','10102','10201','10202','103')
           AND is_delete=0 AND is_cancel=0)=5
    AND (SELECT COUNT(DISTINCT subject_no) FROM `scf_self_saps`.customer_bill_subject
         WHERE product_code='DEF' AND subject_no IN ('101','10102')
           AND is_delete=0 AND is_cancel=0)=2,1,0));

START TRANSACTION;

/* ============================================================
 * S01 项目控制补齐：只补标识完整性和保证金配置，不覆盖已确认的商业险/额度/地域/产品关系。
 * ============================================================ */
USE `scf_self_order`;

UPDATE project_config
SET project_no = @target_project,
    modify_time = NOW(),
    modify_by = 'codex_config_copy'
WHERE project_id = @target_project
  AND is_delete = 0
  AND is_cancel = 0
  AND project_no IS NULL;

UPDATE project_config
SET project_detail_key = 'MarginRatio',
    value = '0.1',
    modify_time = NOW(),
    modify_by = 'codex_config_copy'
WHERE project_id = @target_project
  AND is_delete = 0
  AND is_cancel = 0
  AND project_detail_key IS NULL;

/* ============================================================
 * S01-1 项目附件配置：复制山西项目 44 条运行配置。
 * 源表存在语义相同的历史重复记录；这里按源记录完整复制，保证页面回显、合同附件和保单补传行为一致。
 * ============================================================ */
INSERT INTO project_attachment_config (
    project_no, must_exist, type, cn_name, en_name, stage, class_name, default_url,
    create_by, create_time, suffix, modify_by, modify_time,
    is_delete, delete_by, delete_time, is_cancel, version, remark,
    can_supplement, value_type
)
SELECT
    @target_project, src.must_exist, src.type, src.cn_name, src.en_name, src.stage,
    src.class_name, src.default_url,
    'codex_config_copy', NOW(), src.suffix, NULL, NULL,
    src.is_delete, NULL, NULL, src.is_cancel, COALESCE(src.version, 0), src.remark,
    src.can_supplement, src.value_type
FROM project_attachment_config src
WHERE src.project_no=@source_project
  AND src.is_delete=0
  AND src.is_cancel=0
  AND NOT EXISTS (
      SELECT 1 FROM project_attachment_config tgt
      WHERE tgt.project_no=@target_project
  );

/* ============================================================
 * S02 产品主表：复制非费率运行属性。
 * 保留目标 commercial_insurance_rate/non_commercial_insurance_rate、首付比例和罚息率。
 * ============================================================ */
UPDATE amp_product tgt
JOIN amp_product src
  ON src.code = @source_product
 AND src.is_delete = 0
 AND src.is_cancel = 0
SET tgt.repayment_type = src.repayment_type,
    tgt.grace_period_days = src.grace_period_days,
    tgt.project_no = @target_project,
    tgt.modify_time = NOW(),
    tgt.modify_by = 'codex_config_copy'
WHERE tgt.code = @target_product
  AND tgt.is_delete = 0
  AND tgt.is_cancel = 0;

SELECT id INTO @source_product_id
FROM amp_product
WHERE code = @source_product AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC LIMIT 1;

SELECT id INTO @target_product_id
FROM amp_product
WHERE code = @target_product AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC LIMIT 1;

/* ============================================================
 * S03 产品配置组：复制山西有效的 15 个组。
 * ============================================================ */
INSERT INTO product_detail_group (
    product_id, label_code, label_name, order_index,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_product_id, src.label_code, src.label_name, src.order_index,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM product_detail_group src
WHERE src.product_id = @source_product_id
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM product_detail_group tgt
      WHERE tgt.product_id = @target_product_id
        AND tgt.label_code = src.label_code
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

/* ============================================================
 * S04 产品配置明细：复制山西有效的 89 条明细。
 * ============================================================ */
INSERT INTO product_detail (
    product_id, detail_group_id, code, name, default_value, data_type, order_index,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_product_id,
    tgt_group.id,
    src_detail.code,
    src_detail.name,
    src_detail.default_value,
    src_detail.data_type,
    src_detail.order_index,
    'codex_config_copy',
    NOW(),
    0,
    0,
    COALESCE(src_detail.version, 0),
    src_detail.remark
FROM product_detail src_detail
JOIN product_detail_group src_group
  ON src_group.id = src_detail.detail_group_id
 AND src_group.product_id = @source_product_id
 AND src_group.is_delete = 0
 AND src_group.is_cancel = 0
JOIN product_detail_group tgt_group
 ON tgt_group.product_id = @target_product_id
 AND tgt_group.label_code = src_group.label_code
 AND tgt_group.is_delete = 0
 AND tgt_group.is_cancel = 0
WHERE src_detail.product_id = @source_product_id
  AND src_detail.is_delete = 0
  AND src_detail.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM product_detail exists_detail
      WHERE exists_detail.product_id = @target_product_id
        AND exists_detail.detail_group_id = tgt_group.id
        AND exists_detail.code = src_detail.code
        AND exists_detail.is_delete = 0
        AND exists_detail.is_cancel = 0
  );

/* ============================================================
 * S05 目标产品只支持 9 期：覆盖复制进来的山西 3/6/9 期明细。
 * 费率仍使用 amp_product 中江苏易吾现有 9 期费率：渠道 3.8%、运营 3.2%。
 * ============================================================ */
UPDATE product_detail d
JOIN product_detail_group g ON g.id = d.detail_group_id
SET d.default_value = CASE d.code
        WHEN 'period' THEN '9'
        WHEN 'InitialPaymentRatio' THEN '{"9":0.1}'
        WHEN 'customerFeeRateMax' THEN '{"9":0.36}'
        WHEN 'customerFeeRateMin' THEN '{"9":0.001}'
        WHEN 'fpFeeRate' THEN '{"9":0.04}'
        ELSE d.default_value
    END,
    d.modify_time = NOW(),
    d.modify_by = 'codex_config_copy'
WHERE d.product_id = @target_product_id
  AND g.product_id = @target_product_id
  AND g.label_code = 'BASE_CONFIG'
  AND g.is_delete = 0
  AND g.is_cancel = 0
  AND d.code IN ('period','InitialPaymentRatio','customerFeeRateMax','customerFeeRateMin','fpFeeRate')
  AND d.is_delete = 0
  AND d.is_cancel = 0;

-- 山西产品历史值为 BAOFUPAY，但江苏易吾支付路由已配置为 JDPAY；保持同一支付渠道，避免绑卡与收银台路由不一致。
UPDATE product_detail d
JOIN product_detail_group g ON g.id = d.detail_group_id
SET d.default_value = 'JDPAY',
    d.modify_time = NOW(),
    d.modify_by = 'codex_config_copy'
WHERE d.product_id = @target_product_id
  AND g.product_id = @target_product_id
  AND g.label_code = 'PAY_CONFIG'
  AND g.is_delete = 0
  AND g.is_cancel = 0
  AND d.code = 'payment_support_channel'
  AND d.is_delete = 0
  AND d.is_cancel = 0;

/* ============================================================
 * S06 渠道组织配置：复制山西 DEFAULT 组全部 8 项。
 * ============================================================ */
INSERT INTO org_config (
    org_code, org_type, org_group_key, org_detail_key, value, default_value,
    udf_1, udf_2, udf_3, udf_4, udf_5,
    udf_11, udf_12, udf_13, udf_14, udf_15,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_channel, src.org_type, src.org_group_key, src.org_detail_key,
    src.value, src.default_value,
    src.udf_1, src.udf_2, src.udf_3, src.udf_4, src.udf_5,
    src.udf_11, src.udf_12, src.udf_13, src.udf_14, src.udf_15,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM org_config src
WHERE src.org_code = @source_channel
  AND src.org_type = 'CHANNEL'
  AND src.org_group_key = 'DEFAULT'
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM org_config tgt
      WHERE tgt.org_code = @target_channel
        AND tgt.org_type = src.org_type
        AND tgt.org_group_key = src.org_group_key
        AND tgt.org_detail_key = src.org_detail_key
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

/* ============================================================
 * S07 支付业务配置：收银台支付渠道使用 JDPAY；放款固定走 BCM，补齐 SINGLE_REMIT 手续费。
 * 不覆盖已存在的 router、channel_pay_config 和 JDPAY 协议支付费率。
 * ============================================================ */
USE `scf_self_payment`;

INSERT INTO business_config (
    query_key, query_type, group_key, group_desc,
    config_key, config_value, config_value_desc, config_status,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    @target_channel, 'COOPERATION', src.group_key, src.group_desc,
    src.config_key, 'JDPAY', src.config_value_desc, src.config_status,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM business_config src
WHERE src.query_key = @source_channel
  AND src.query_type = 'COOPERATION'
  AND src.group_key = 'PaymentConfig'
  AND src.config_key = 'pay_channel'
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM business_config tgt
      WHERE tgt.query_key = @target_channel
        AND tgt.query_type = 'COOPERATION'
        AND tgt.group_key = src.group_key
        AND tgt.config_key = src.config_key
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

-- 即使数据库中存在人工预置的同名有效项，也统一收敛到目标实际支付通道。
UPDATE business_config
SET config_value = 'JDPAY',
    config_status = 1,
    modify_time = NOW(),
    modify_by = 'codex_config_copy'
WHERE query_key = @target_channel
  AND query_type = 'COOPERATION'
  AND group_key = 'PaymentConfig'
  AND config_key = 'pay_channel'
  AND is_delete = 0
  AND is_cancel = 0;

-- scf-payment 的代付手续费计算会强制按 merchant_no + BCM + SINGLE_REMIT 查询；缺失会直接抛 CONFIG_NOT_EXIST。
-- master 的 SAPS PaymentTask 已显式指定 BCM，因此无需复制山西的 DF router 或 mer_channel_config。
INSERT INTO channel_fee_config (
    merchant_no, channle_id, channel_code, transaction_mode,
    biz_type, card_type, bank_rule, channel_fee_rule,
    start_range, end_range, channel_fee, channel_fee_rate,
    platform_fee, platform_fee_rate, platform_fee_enable,
    min_fee_amount, max_fee_amount, calculate_rule, main_account_belong, fee_desc,
    create_by, create_time, modify_by, modify_time, delete_by, delete_time,
    is_delete, is_cancel, version, remark
)
SELECT
    @target_channel, src.channle_id, src.channel_code, src.transaction_mode,
    src.biz_type, src.card_type, src.bank_rule, src.channel_fee_rule,
    src.start_range, src.end_range, src.channel_fee, src.channel_fee_rate,
    src.platform_fee, src.platform_fee_rate, src.platform_fee_enable,
    src.min_fee_amount, src.max_fee_amount, src.calculate_rule, src.main_account_belong, src.fee_desc,
    'codex_config_copy', NOW(), NULL, NULL, NULL, NULL,
    0, 0, COALESCE(src.version, 0), src.remark
FROM channel_fee_config src
WHERE src.merchant_no = @source_channel
  AND src.channel_code = 'BCM'
  AND src.transaction_mode = 'SINGLE_REMIT'
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM channel_fee_config tgt
      WHERE tgt.merchant_no = @target_channel
        AND tgt.channel_code = 'BCM'
        AND tgt.transaction_mode = 'SINGLE_REMIT'
  );

/* ============================================================
 * S08 SAPS 业务配置：渠道维度 6 项 + 项目维度 2 项。
 * 测试环境山西银行账户四项本身均为 test，复制后仍为 test。
 * ============================================================ */
USE `scf_self_saps`;

INSERT INTO business_config (
    query_key, query_type, group_key, group_desc,
    config_key, config_value, config_value_desc, config_status,
    create_by, create_time, is_delete, is_cancel, version, remark
)
SELECT
    CASE
        WHEN src.query_key = @source_channel THEN @target_channel
        WHEN src.query_key = @source_project THEN @target_project
    END,
    src.query_type, src.group_key, src.group_desc,
    src.config_key, src.config_value, src.config_value_desc, src.config_status,
    'codex_config_copy', NOW(), 0, 0, COALESCE(src.version, 0), src.remark
FROM business_config src
WHERE src.query_key IN (@source_channel, @source_project)
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM business_config tgt
      WHERE tgt.query_key = CASE
              WHEN src.query_key = @source_channel THEN @target_channel
              WHEN src.query_key = @source_project THEN @target_project
            END
        AND tgt.group_key = src.group_key
        AND tgt.config_key = src.config_key
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

-- 本脚本到此只完成进件、缴费和出客户账单所需的基础配置。
-- statement_scene/statement_scene_rule 必须保持 0 条，待 scf-saps 为 XXT-PRJ-YW-A 增加扩展点并部署后另行配置。

-- COMMIT 前强制语义验收。任一项报错时立即手工 ROLLBACK。
INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_counts_15_89_44_8_1_1_8',
 IF((SELECT COUNT(*) FROM `scf_self_order`.product_detail_group
     WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=15
    AND (SELECT COUNT(*) FROM `scf_self_order`.product_detail
         WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=89
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@target_project AND is_delete=0 AND is_cancel=0)=44
    AND (SELECT COUNT(*) FROM `scf_self_order`.org_config
         WHERE org_code=@target_channel AND org_type='CHANNEL' AND org_group_key='DEFAULT'
           AND is_delete=0 AND is_cancel=0)=8
    AND (SELECT COUNT(*) FROM `scf_self_payment`.business_config
         WHERE query_key=@target_channel AND query_type='COOPERATION'
           AND group_key='PaymentConfig' AND config_key='pay_channel'
           AND config_value='JDPAY' AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no=@target_channel AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT'
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN (@target_channel,@target_project) AND config_status=1
           AND is_delete=0 AND is_cancel=0)=8,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_product_and_project_values',
 IF((SELECT COUNT(*) FROM `scf_self_order`.amp_product
     WHERE id=278 AND code=@target_product AND project_no=@target_project
       AND repayment_type='4' AND grace_period_days=0 AND status=1
       AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_order`.project_config
         WHERE project_id=@target_project AND project_no=@target_project
           AND project_detail_key='MarginRatio' AND CAST(value AS DECIMAL(10,4))=0.1
           AND insurance_type='1' AND is_delete=0 AND is_cancel=0)=1,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_runtime_product_values',
 IF((SELECT COUNT(*)
     FROM `scf_self_order`.product_detail d
     JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
     WHERE d.product_id=278 AND g.product_id=278
       AND g.label_code='BASE_CONFIG' AND d.code='period' AND d.default_value='9'
       AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='BASE_CONFIG' AND d.code='InitialPaymentRatio'
           AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.1'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='BASE_CONFIG' AND d.code='plateformFeeRate' AND d.default_value='0.005'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='BASE_CONFIG' AND d.code='repaymentPlanCalculation' AND d.default_value='ZY_Customer'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.product_detail d
         JOIN `scf_self_order`.product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278
           AND g.label_code='PAY_CONFIG' AND d.code='payment_support_channel' AND d.default_value='JDPAY'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1,1,0));

-- 不只核对数量：除 6 个目标专属覆盖项外，83 条产品明细必须与山西相同；组织和 SAPS 的 8 项业务值必须逐项相同。
INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_copied_business_values_equal_source',
 IF((SELECT COUNT(*)
     FROM `scf_self_order`.product_detail sd
     JOIN `scf_self_order`.product_detail_group sg
       ON sg.id=sd.detail_group_id AND sg.product_id=265
      AND sg.is_delete=0 AND sg.is_cancel=0
     JOIN `scf_self_order`.product_detail_group tg
       ON tg.product_id=278 AND tg.label_code=sg.label_code
      AND tg.is_delete=0 AND tg.is_cancel=0
     JOIN `scf_self_order`.product_detail td
       ON td.product_id=278 AND td.detail_group_id=tg.id AND td.code=sd.code
      AND td.is_delete=0 AND td.is_cancel=0
     WHERE sd.product_id=265 AND sd.is_delete=0 AND sd.is_cancel=0
       AND NOT ((sg.label_code='BASE_CONFIG'
                 AND sd.code IN ('period','InitialPaymentRatio','customerFeeRateMax','customerFeeRateMin','fpFeeRate'))
                OR (sg.label_code='PAY_CONFIG' AND sd.code='payment_support_channel'))
       AND tg.label_name <=> sg.label_name
       AND tg.order_index <=> sg.order_index
       AND td.name <=> sd.name
       AND td.default_value <=> sd.default_value
       AND td.data_type <=> sd.data_type
       AND td.order_index <=> sd.order_index)=83
    AND (SELECT COALESCE(SUM(CRC32(CONCAT_WS('#',
              COALESCE(must_exist,'<NULL>'),COALESCE(type,'<NULL>'),COALESCE(cn_name,'<NULL>'),
              COALESCE(en_name,'<NULL>'),COALESCE(stage,'<NULL>'),COALESCE(class_name,'<NULL>'),
              COALESCE(default_url,'<NULL>'),COALESCE(suffix,'<NULL>'),COALESCE(is_delete,'<NULL>'),
              COALESCE(is_cancel,'<NULL>'),COALESCE(version,'<NULL>'),COALESCE(remark,'<NULL>'),
              COALESCE(can_supplement,'<NULL>'),COALESCE(value_type,'<NULL>')))),0)
         FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@source_project AND is_delete=0 AND is_cancel=0)
        =
        (SELECT COALESCE(SUM(CRC32(CONCAT_WS('#',
              COALESCE(must_exist,'<NULL>'),COALESCE(type,'<NULL>'),COALESCE(cn_name,'<NULL>'),
              COALESCE(en_name,'<NULL>'),COALESCE(stage,'<NULL>'),COALESCE(class_name,'<NULL>'),
              COALESCE(default_url,'<NULL>'),COALESCE(suffix,'<NULL>'),COALESCE(is_delete,'<NULL>'),
              COALESCE(is_cancel,'<NULL>'),COALESCE(version,'<NULL>'),COALESCE(remark,'<NULL>'),
              COALESCE(can_supplement,'<NULL>'),COALESCE(value_type,'<NULL>')))),0)
         FROM `scf_self_order`.project_attachment_config
         WHERE project_no=@target_project AND is_delete=0 AND is_cancel=0)
    AND (SELECT COUNT(*)
         FROM `scf_self_order`.org_config src
         JOIN `scf_self_order`.org_config tgt
           ON tgt.org_code=@target_channel
          AND tgt.org_type=src.org_type
          AND tgt.org_group_key=src.org_group_key
          AND tgt.org_detail_key=src.org_detail_key
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         WHERE src.org_code=@source_channel
           AND src.org_type='CHANNEL' AND src.org_group_key='DEFAULT'
           AND src.is_delete=0 AND src.is_cancel=0
           AND tgt.value <=> src.value
           AND tgt.default_value <=> src.default_value
           AND tgt.udf_1 <=> src.udf_1 AND tgt.udf_2 <=> src.udf_2
           AND tgt.udf_3 <=> src.udf_3 AND tgt.udf_4 <=> src.udf_4
           AND tgt.udf_5 <=> src.udf_5 AND tgt.udf_11 <=> src.udf_11
           AND tgt.udf_12 <=> src.udf_12 AND tgt.udf_13 <=> src.udf_13
           AND tgt.udf_14 <=> src.udf_14 AND tgt.udf_15 <=> src.udf_15)=8
    AND (SELECT COUNT(*)
         FROM `scf_self_saps`.business_config src
         JOIN `scf_self_saps`.business_config tgt
           ON tgt.query_key=CASE WHEN src.query_key=@source_channel THEN @target_channel
                                 WHEN src.query_key=@source_project THEN @target_project END
          AND tgt.group_key=src.group_key
          AND tgt.config_key=src.config_key
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         WHERE src.query_key IN (@source_channel,@source_project)
           AND src.is_delete=0 AND src.is_cancel=0
           AND tgt.query_type <=> src.query_type
           AND tgt.group_desc <=> src.group_desc
           AND tgt.config_value <=> src.config_value
           AND tgt.config_value_desc <=> src.config_value_desc
           AND tgt.config_status <=> src.config_status)=8
    AND (SELECT COUNT(*)
         FROM `scf_self_payment`.channel_fee_config src
         JOIN `scf_self_payment`.channel_fee_config tgt
           ON tgt.merchant_no=@target_channel
          AND tgt.channel_code=src.channel_code
          AND tgt.transaction_mode=src.transaction_mode
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         WHERE src.merchant_no=@source_channel
           AND src.channel_code='BCM' AND src.transaction_mode='SINGLE_REMIT'
           AND src.is_delete=0 AND src.is_cancel=0
           AND tgt.channle_id <=> src.channle_id
           AND tgt.biz_type <=> src.biz_type AND tgt.card_type <=> src.card_type
           AND tgt.bank_rule <=> src.bank_rule AND tgt.channel_fee_rule <=> src.channel_fee_rule
           AND tgt.start_range <=> src.start_range AND tgt.end_range <=> src.end_range
           AND tgt.channel_fee <=> src.channel_fee AND tgt.channel_fee_rate <=> src.channel_fee_rate
           AND tgt.platform_fee <=> src.platform_fee AND tgt.platform_fee_rate <=> src.platform_fee_rate
           AND tgt.platform_fee_enable <=> src.platform_fee_enable
           AND tgt.min_fee_amount <=> src.min_fee_amount AND tgt.max_fee_amount <=> src.max_fee_amount
           AND tgt.calculate_rule <=> src.calculate_rule
           AND tgt.main_account_belong <=> src.main_account_belong)=1,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_config_guard` VALUES
('post_statement_scene_still_zero',
 IF((SELECT COUNT(*) FROM `scf_self_saps`.statement_scene
     WHERE capital_project_code=@target_project AND is_delete=0 AND is_cancel=0)=0,1,0));

COMMIT;
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_config_guard`;

/* ============================================================
 * 执行后核对
 * ============================================================ */
USE `scf_self_order`;
SELECT 'product_group' AS config_type, COUNT(*) AS actual_count
FROM product_detail_group WHERE product_id = @target_product_id AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'product_detail', COUNT(*)
FROM product_detail WHERE product_id = @target_product_id AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'project_attachment_config', COUNT(*)
FROM project_attachment_config WHERE project_no = @target_project AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'org_config', COUNT(*)
FROM org_config WHERE org_code = @target_channel AND org_group_key = 'DEFAULT' AND is_delete = 0 AND is_cancel = 0;

SELECT d.code,d.default_value
FROM product_detail d
JOIN product_detail_group g ON g.id=d.detail_group_id
WHERE d.product_id=@target_product_id
  AND g.label_code='BASE_CONFIG'
  AND d.code IN ('period','InitialPaymentRatio','fpFeeRate','repaymentPlanCalculation','plateformFeeRate','repayDateRule')
  AND d.is_delete=0 AND d.is_cancel=0
ORDER BY d.code;

USE `scf_self_payment`;
SELECT query_key,group_key,config_key,config_value,config_status
FROM business_config
WHERE query_key=@target_channel AND is_delete=0 AND is_cancel=0;

SELECT merchant_no,channel_code,transaction_mode,card_type,bank_rule,
       channel_fee_rule,channel_fee,channel_fee_rate,platform_fee_enable
FROM channel_fee_config
WHERE merchant_no=@target_channel
  AND channel_code='BCM'
  AND transaction_mode='SINGLE_REMIT'
  AND is_delete=0 AND is_cancel=0;

USE `scf_self_saps`;
SELECT query_key,query_type,group_key,config_key,config_value,config_status
FROM business_config
WHERE query_key IN (@target_channel,@target_project) AND is_delete=0 AND is_cancel=0
ORDER BY query_key,group_key,config_key;

SELECT capital_project_code,scene_code,strategy_code,payment_mode,scene_type
FROM statement_scene
WHERE capital_project_code=@target_project AND is_delete=0 AND is_cancel=0
ORDER BY scene_code;
```

---

## 四、合同配置 SQL

源文件：`scf-test-hzf000133-contract-config-20260813.sql`

```sql
-- 保费分期测试环境：为江苏易吾项目复制老自营合同配置
-- 源项目：XXT-PRJ-ZY-A
-- 目标项目：XXT-PRJ-YW-A
-- 规则：复制源项目全部有效合同配置及有效签署人，但排除居间服务协议。
-- 排除模板：insurance.intermediary.service.agreement
--
-- 说明：
-- 1. 脚本虽使用 INSERT ... SELECT ... NOT EXISTS，但强守卫要求目标配置为空；本脚本只能完整执行一次，禁止重跑。
-- 2. 不复制源记录主键；目标合同与签署人使用新自增主键。
-- 3. 不修改、不删除已有目标配置。
-- 4. 执行前后均提供核对 SQL；预期新增合同 32 条、签署人 39 条
--    （以执行时源项目有效数据为准）。
-- 5. 必须在“遇错停止”模式下执行；任一 guard 报错立即 ROLLBACK，禁止跳过错误继续 COMMIT。

USE `scf_self_order`;

SET @source_project_code = 'XXT-PRJ-ZY-A';
SET @target_project_code = 'XXT-PRJ-YW-A';
SET @excluded_template_code = 'insurance.intermediary.service.agreement';

-- 执行前源数据硬校验：当前测试库应为 33 个合同、42 个签署人；排除居间后为 32/39。
SELECT
    (SELECT COUNT(*) FROM contract_config
     WHERE project_code = @source_project_code AND is_delete = 0 AND is_cancel = 0) AS source_contract_should_be_33,
    (SELECT COUNT(*) FROM contract_config c
     JOIN contract_config_signers s ON s.contract_config_id = c.id
     WHERE c.project_code = @source_project_code
       AND c.is_delete = 0 AND c.is_cancel = 0
       AND s.is_delete = 0 AND s.is_cancel = 0) AS source_signer_should_be_42;

-- 执行前核对：目标项目不应存在居间协议。
SELECT
    c.project_code,
    c.scene_code,
    c.template_code,
    c.template_name,
    c.is_delete,
    c.is_cancel
FROM contract_config c
WHERE c.project_code IN (@source_project_code, @target_project_code)
ORDER BY c.project_code, c.scene_code, c.template_code;

SELECT COUNT(*) AS expected_contract_count
FROM contract_config c
WHERE c.project_code = @source_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
  AND c.template_code <> @excluded_template_code;

SELECT COUNT(*) AS expected_signer_count
FROM contract_config c
JOIN contract_config_signers s ON s.contract_config_id = c.id
WHERE c.project_code = @source_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
  AND s.is_delete = 0
  AND s.is_cancel = 0
  AND c.template_code <> @excluded_template_code;

-- 强制执行守卫：防止源配置已变更、目标被并发写入或源自然键重复时继续复制。
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_contract_guard`;
CREATE TEMPORARY TABLE `scf_self_order`.`_hzf000133_contract_guard` (
    check_name VARCHAR(100) NOT NULL PRIMARY KEY,
    passed TINYINT NOT NULL,
    CONSTRAINT `chk_hzf000133_contract_guard` CHECK (passed = 1)
);

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('source_contract_counts_33_42_excluded_32_39',
 IF((SELECT COUNT(*) FROM contract_config
     WHERE project_code=@source_project_code AND is_delete=0 AND is_cancel=0)=33
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@source_project_code
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=42
    AND (SELECT COUNT(*) FROM contract_config
         WHERE project_code=@source_project_code AND template_code<>@excluded_template_code
           AND is_delete=0 AND is_cancel=0)=32
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@source_project_code AND c.template_code<>@excluded_template_code
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=39,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('source_contract_natural_keys_unique',
 IF((SELECT COUNT(*) FROM (
       SELECT scene_code,template_code
       FROM contract_config
       WHERE project_code=@source_project_code AND template_code<>@excluded_template_code
         AND is_delete=0 AND is_cancel=0
       GROUP BY scene_code,template_code HAVING COUNT(*)>1
     ) duplicated_contract)=0
    AND (SELECT COUNT(*) FROM (
       SELECT c.scene_code,c.template_code,s.identity,s.identity_name,s.identity_type,s.sign_type,s.sort
       FROM contract_config c
       JOIN contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code=@source_project_code AND c.template_code<>@excluded_template_code
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0
       GROUP BY c.scene_code,c.template_code,s.identity,s.identity_name,s.identity_type,s.sign_type,s.sort
       HAVING COUNT(*)>1
     ) duplicated_signer)=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('target_contracts_still_empty',
 IF((SELECT COUNT(*) FROM contract_config WHERE project_code=@target_project_code)=0,1,0));

-- 强制执行顺序：基础配置脚本必须已完整提交，且目标仍保持 9 期/JDPAY 运行值。
INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('base_config_ready_before_contract_copy',
 IF((SELECT COUNT(*) FROM product_detail_group
     WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=15
    AND (SELECT COUNT(*) FROM product_detail
         WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=89
    AND (SELECT COUNT(*) FROM project_attachment_config
         WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=44
    AND (SELECT COUNT(*) FROM org_config
         WHERE org_code='HZF000133' AND org_type='CHANNEL' AND org_group_key='DEFAULT'
           AND is_delete=0 AND is_cancel=0)=8
    AND (SELECT COUNT(*)
         FROM product_detail d JOIN product_detail_group g ON g.id=d.detail_group_id
         WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
           AND d.code='period' AND d.default_value='9'
           AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_payment`.business_config
         WHERE query_key='HZF000133' AND query_type='COOPERATION'
           AND group_key='PaymentConfig' AND config_key='pay_channel'
           AND config_value='JDPAY' AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*)
         FROM `scf_self_payment`.channel_fee_config
         WHERE merchant_no='HZF000133' AND channel_code='BCM'
           AND transaction_mode='SINGLE_REMIT'
           AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
           AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
           AND is_delete=0 AND is_cancel=0)=1
    AND (SELECT COUNT(*) FROM `scf_self_saps`.business_config
         WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A') AND config_status=1
           AND is_delete=0 AND is_cancel=0)=8,1,0));

START TRANSACTION;

-- 复制合同配置。业务字段与老自营保持一致，仅替换项目编号。
INSERT INTO contract_config (
    main_id,
    tenant_code,
    project_code,
    template_code,
    template_name,
    template_name_custom,
    template_version,
    template_url,
    user_type,
    scene_code,
    scene_title,
    `generateType`,
    use_condition,
    silent_sign,
    contract_no_rule,
    ref_other_contract,
    sign_mode,
    biz_type,
    contract_vars,
    sort,
    callback_url,
    jump_back_url,
    hide,
    create_time,
    create_by,
    is_delete,
    is_cancel,
    version,
    remark
)
SELECT
    src.main_id,
    src.tenant_code,
    @target_project_code,
    src.template_code,
    src.template_name,
    src.template_name_custom,
    src.template_version,
    src.template_url,
    src.user_type,
    src.scene_code,
    src.scene_title,
    src.`generateType`,
    src.use_condition,
    src.silent_sign,
    src.contract_no_rule,
    src.ref_other_contract,
    src.sign_mode,
    src.biz_type,
    src.contract_vars,
    src.sort,
    src.callback_url,
    src.jump_back_url,
    src.hide,
    NOW(),
    'contract_config_copy',
    0,
    0,
    src.version,
    src.remark
FROM contract_config src
WHERE src.project_code = @source_project_code
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND src.template_code <> @excluded_template_code
  AND NOT EXISTS (
      SELECT 1
      FROM contract_config tgt
      WHERE tgt.project_code = @target_project_code
        AND tgt.template_code <=> src.template_code
        AND tgt.scene_code <=> src.scene_code
        AND tgt.is_delete = 0
        AND tgt.is_cancel = 0
  );

-- 复制签署人配置，通过“场景 + 模板编码”映射目标合同主键。
INSERT INTO contract_config_signers (
    contract_config_id,
    identity,
    identity_name,
    identity_type,
    sign_type,
    sort,
    create_time,
    create_by,
    is_delete,
    is_cancel,
    version,
    remark
)
SELECT
    tgt.id,
    src_signer.identity,
    src_signer.identity_name,
    src_signer.identity_type,
    src_signer.sign_type,
    src_signer.sort,
    NOW(),
    'contract_config_copy',
    0,
    0,
    src_signer.version,
    src_signer.remark
FROM contract_config src
JOIN contract_config_signers src_signer
  ON src_signer.contract_config_id = src.id
JOIN contract_config tgt
  ON tgt.project_code = @target_project_code
 AND tgt.template_code <=> src.template_code
 AND tgt.scene_code <=> src.scene_code
 AND tgt.is_delete = 0
 AND tgt.is_cancel = 0
WHERE src.project_code = @source_project_code
  AND src.is_delete = 0
  AND src.is_cancel = 0
  AND src_signer.is_delete = 0
  AND src_signer.is_cancel = 0
  AND src.template_code <> @excluded_template_code
  AND NOT EXISTS (
      SELECT 1
      FROM contract_config_signers exists_signer
      WHERE exists_signer.contract_config_id = tgt.id
        AND exists_signer.identity <=> src_signer.identity
        AND exists_signer.identity_name <=> src_signer.identity_name
        AND exists_signer.identity_type <=> src_signer.identity_type
        AND exists_signer.sign_type <=> src_signer.sign_type
        AND exists_signer.sort <=> src_signer.sort
        AND exists_signer.is_delete = 0
        AND exists_signer.is_cancel = 0
  );

-- COMMIT 前强制语义验收。
INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_contract_counts_32_39_no_intermediary',
 IF((SELECT COUNT(*) FROM contract_config
     WHERE project_code=@target_project_code AND is_delete=0 AND is_cancel=0)=32
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@target_project_code
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=39
    AND (SELECT COUNT(*) FROM contract_config
         WHERE project_code=@target_project_code AND template_code=@excluded_template_code
           AND is_delete=0 AND is_cancel=0)=0,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_binding_card_ci_configs_4_signers_5',
 IF((SELECT COUNT(*) FROM contract_config c
     WHERE c.project_code=@target_project_code AND c.scene_code='BINDING_CARD'
       AND c.template_code<>@excluded_template_code
       AND (c.use_condition IS NULL OR c.use_condition=''
            OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
       AND c.is_delete=0 AND c.is_cancel=0)=4
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@target_project_code AND c.scene_code='BINDING_CARD'
           AND c.template_code<>@excluded_template_code
           AND (c.use_condition IS NULL OR c.use_condition=''
                OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=5,1,0));

INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_binding_success_configs_3_signers_3',
 IF((SELECT COUNT(*) FROM contract_config
     WHERE project_code=@target_project_code AND scene_code='BINDING_SUCCESS'
       AND `generateType`='ATTACHMENT' AND is_delete=0 AND is_cancel=0)=3
    AND (SELECT COUNT(*) FROM contract_config c
         JOIN contract_config_signers s ON s.contract_config_id=c.id
         WHERE c.project_code=@target_project_code AND c.scene_code='BINDING_SUCCESS'
           AND c.`generateType`='ATTACHMENT'
           AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=3,1,0));

-- 不只核对数量：32 个合同和 39 个签署人的复制业务字段必须与山西源配置一致。
INSERT INTO `scf_self_order`.`_hzf000133_contract_guard` VALUES
('post_contract_business_values_equal_source',
 IF((SELECT COUNT(*)
     FROM contract_config src
     JOIN contract_config tgt
       ON tgt.project_code=@target_project_code
      AND tgt.scene_code <=> src.scene_code
      AND tgt.template_code <=> src.template_code
      AND tgt.is_delete=0 AND tgt.is_cancel=0
     WHERE src.project_code=@source_project_code
       AND src.template_code<>@excluded_template_code
       AND src.is_delete=0 AND src.is_cancel=0
       AND tgt.main_id <=> src.main_id
       AND tgt.tenant_code <=> src.tenant_code
       AND tgt.template_name <=> src.template_name
       AND tgt.template_name_custom <=> src.template_name_custom
       AND tgt.template_version <=> src.template_version
       AND tgt.template_url <=> src.template_url
       AND tgt.user_type <=> src.user_type
       AND tgt.scene_title <=> src.scene_title
       AND tgt.`generateType` <=> src.`generateType`
       AND tgt.use_condition <=> src.use_condition
       AND tgt.silent_sign <=> src.silent_sign
       AND tgt.contract_no_rule <=> src.contract_no_rule
       AND tgt.ref_other_contract <=> src.ref_other_contract
       AND tgt.sign_mode <=> src.sign_mode
       AND tgt.biz_type <=> src.biz_type
       AND tgt.contract_vars <=> src.contract_vars
       AND tgt.sort <=> src.sort
       AND tgt.callback_url <=> src.callback_url
       AND tgt.jump_back_url <=> src.jump_back_url
       AND tgt.hide <=> src.hide
       AND tgt.version <=> src.version
       AND tgt.remark <=> src.remark)=32
    AND (SELECT COUNT(*)
         FROM contract_config src
         JOIN contract_config_signers ss ON ss.contract_config_id=src.id
         JOIN contract_config tgt
           ON tgt.project_code=@target_project_code
          AND tgt.scene_code <=> src.scene_code
          AND tgt.template_code <=> src.template_code
          AND tgt.is_delete=0 AND tgt.is_cancel=0
         JOIN contract_config_signers ts
           ON ts.contract_config_id=tgt.id
          AND ts.identity <=> ss.identity
          AND ts.identity_name <=> ss.identity_name
          AND ts.identity_type <=> ss.identity_type
          AND ts.sign_type <=> ss.sign_type
          AND ts.sort <=> ss.sort
          AND ts.is_delete=0 AND ts.is_cancel=0
         WHERE src.project_code=@source_project_code
           AND src.template_code<>@excluded_template_code
           AND src.is_delete=0 AND src.is_cancel=0
           AND ss.is_delete=0 AND ss.is_cancel=0
           AND ts.version <=> ss.version
           AND ts.remark <=> ss.remark)=39,1,0));

COMMIT;
DROP TEMPORARY TABLE IF EXISTS `scf_self_order`.`_hzf000133_contract_guard`;

-- 执行后核对。
SELECT
    c.scene_code,
    COUNT(*) AS contract_count
FROM contract_config c
WHERE c.project_code = @target_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
GROUP BY c.scene_code
ORDER BY c.scene_code;

SELECT COUNT(*) AS actual_contract_count
FROM contract_config c
WHERE c.project_code = @target_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0;

SELECT COUNT(*) AS actual_signer_count
FROM contract_config c
JOIN contract_config_signers s ON s.contract_config_id = c.id
WHERE c.project_code = @target_project_code
  AND c.is_delete = 0
  AND c.is_cancel = 0
  AND s.is_delete = 0
  AND s.is_cancel = 0;

SELECT COUNT(*) AS intermediary_agreement_count_should_be_zero
FROM contract_config c
WHERE c.project_code = @target_project_code
  AND c.template_code = @excluded_template_code
  AND c.is_delete = 0
  AND c.is_cancel = 0;

-- 如需回滚本脚本本次新增的目标项目合同配置，请人工确认后依次执行：
-- DELETE s
-- FROM contract_config_signers s
-- JOIN contract_config c ON c.id = s.contract_config_id
-- WHERE c.project_code = @target_project_code
--   AND s.create_by = 'contract_config_copy';
--
-- DELETE FROM contract_config
-- WHERE project_code = @target_project_code
--   AND create_by = 'contract_config_copy';
```

---

## 五、执行前／执行后／新单链路核验 SQL

源文件：`scf-test-hzf000133-preflight-postcheck-20260813.sql`

```sql
-- 保费分期测试环境：江苏易吾 HZF000133 执行前/执行后核验
-- 环境/分支：test / master（用户指定）
-- 本文件只包含 SELECT/SET 只读语句，不修改数据库。
-- 目标：HZF000133 / XXT-PRJ-YW-A / PRD-YW-A001（产品 id=278）

/* ======================== A. 执行前基线 ======================== */

-- A01 主数据必须各 1 条，且全部启用。
SELECT id, no, name, channel_codes, status, effect_date,
       margin_ratio, current_margin_amount, total_limit,
       supplier_id, technical_service_provider_id
FROM scf_self_order.amp_project
WHERE no = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

SELECT id, code, name, project_no, status, repayment_type,
       active_time, invalid_time, customer_down_payment_ratio,
       commercial_insurance_rate, penalty_interest_rate, grace_period_days
FROM scf_self_order.amp_product
WHERE code = 'PRD-YW-A001' AND is_delete = 0 AND is_cancel = 0;

SELECT id, channel_code, type, status, tenant_code, user_code, auth_code
FROM scf_self_order.crm_channel
WHERE channel_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

-- 9 期资金方主数据：中化必须启用、支持 9 期商业险；通汇必须启用并支持 9 期。
SELECT org_code, org_name, status, periods, supported_periods, supported_insurance_types
FROM scf_self_order.org_funding_party
WHERE org_code IN ('ZJF000015','ZJF000011')
  AND is_delete = 0 AND is_cancel = 0
ORDER BY org_code;

-- A02 项目关系必须只有目标产品；商业险-only 是目标现状。
SELECT id, project_id, project_no, product_ids, insurance_type,
       period_type, period, project_detail_key, value
FROM scf_self_order.project_config
WHERE project_id = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

-- A03 支付专属配置已存在，不允许从山西覆盖。
SELECT cooperation_code, project_no, product_code, payment_product, channel
FROM scf_self_payment.router
WHERE cooperation_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

SELECT id, cooperation_code, channel_code, query_key, status
FROM scf_self_payment.channel_pay_config
WHERE cooperation_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

-- JDPAY 运行必填字段：必须均非空；敏感值只核验存在性，不在结果中展示。
SELECT id, cooperation_code, channel_code, query_key, status,
       CASE WHEN channel_url IS NOT NULL AND channel_url <> '' THEN 1 ELSE 0 END AS channel_url_present,
       CASE WHEN member_id IS NOT NULL AND member_id <> '' THEN 1 ELSE 0 END AS member_id_present,
       CASE WHEN terminal_id IS NOT NULL AND terminal_id <> '' THEN 1 ELSE 0 END AS terminal_id_present,
       CASE WHEN resv_one IS NOT NULL AND resv_one <> '' THEN 1 ELSE 0 END AS resv_one_present,
       CASE WHEN notify_url IS NOT NULL AND notify_url <> '' THEN 1 ELSE 0 END AS notify_url_present,
       CASE WHEN pay_config_code IS NOT NULL AND pay_config_code <> '' THEN 1 ELSE 0 END AS pay_config_code_present
FROM scf_self_payment.channel_pay_config
WHERE cooperation_code = 'HZF000133'
  AND channel_code = 'JDPAY'
  AND status = 1
  AND is_delete = 0 AND is_cancel = 0;

-- 目标 JDPAY 端点/商户/应用/租户/通知地址应与当前已成功运行的山西 JDPAY 配置一致；仅编码可不同。
-- 只返回 0/1，不展示敏感值。
SELECT CASE WHEN t.channel_url=s.channel_url THEN 1 ELSE 0 END AS channel_url_same_as_running_source,
       CASE WHEN t.member_id=s.member_id THEN 1 ELSE 0 END AS member_id_same_as_running_source,
       CASE WHEN t.terminal_id=s.terminal_id THEN 1 ELSE 0 END AS terminal_id_same_as_running_source,
       CASE WHEN t.resv_one=s.resv_one THEN 1 ELSE 0 END AS tenant_same_as_running_source,
       CASE WHEN t.notify_url=s.notify_url THEN 1 ELSE 0 END AS notify_url_same_as_running_source
FROM scf_self_payment.channel_pay_config t
JOIN scf_self_payment.channel_pay_config s
  ON s.cooperation_code='HZF000144' AND s.channel_code='JDPAY' AND s.status=1
 AND s.is_delete=0 AND s.is_cancel=0
WHERE t.cooperation_code='HZF000133' AND t.channel_code='JDPAY' AND t.status=1
  AND t.is_delete=0 AND t.is_cancel=0;

SELECT id, merchant_no, channel_code, transaction_mode, card_type, bank_rule,
       channel_fee_rule, channel_fee, channel_fee_rate, platform_fee_enable
FROM scf_self_payment.channel_fee_config
WHERE merchant_no = 'HZF000133' AND is_delete = 0 AND is_cancel = 0;

-- 放款固定走 BCM；目标执行前应没有 SINGLE_REMIT，主脚本执行后必须精确为 1 条。
-- BCM Mock 使用 DEF 回退，以下两项必须启用；URL 只校验非空，不复制为目标项目专属配置。
SELECT query_key, query_type, group_key, config_key,
       CASE WHEN config_key='bcmMockUrl' THEN IF(config_value IS NULL OR config_value='',0,1)
            ELSE (config_value='true') END AS runtime_value_ready,
       config_status
FROM scf_self_payment.business_config
WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
  AND config_key IN ('bcmMockFlag','bcmMockUrl')
  AND is_delete=0 AND is_cancel=0
ORDER BY config_key;

-- A04 当前缺口基线：执行前通常为 0/0/0/0/0/0/0/0。
SELECT
  (SELECT COUNT(*) FROM scf_self_order.product_detail_group
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_group_count,
  (SELECT COUNT(*) FROM scf_self_order.product_detail
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_detail_count,
  (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
   WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0) AS project_attachment_count,
  (SELECT COUNT(*) FROM scf_self_order.org_config
   WHERE org_code = 'HZF000133' AND is_delete = 0 AND is_cancel = 0) AS org_config_count,
  (SELECT COUNT(*) FROM scf_self_order.contract_config
   WHERE project_code = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0) AS contract_count,
  (SELECT COUNT(*) FROM scf_self_payment.business_config
   WHERE query_key = 'HZF000133' AND is_delete = 0 AND is_cancel = 0) AS payment_business_count,
  (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
   WHERE merchant_no='HZF000133' AND channel_code='BCM'
     AND transaction_mode='SINGLE_REMIT') AS bcm_single_remit_fee_count,
  (SELECT COUNT(*) FROM scf_self_saps.business_config
   WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A') AND is_delete = 0 AND is_cancel = 0) AS saps_business_count;

-- A05 山西源数据必须仍为：15 / 89 / 44 / 8 / 32 / 39 / 1 / 1 / 8。
-- 任一数量变化都停止执行，先重新比对源配置，不要按旧预期继续复制。
SELECT
  (SELECT COUNT(*)
   FROM scf_self_order.product_detail_group g
   JOIN scf_self_order.amp_product p ON p.id = g.product_id
   WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
     AND g.is_delete = 0 AND g.is_cancel = 0) AS source_group_should_be_15,
  (SELECT COUNT(*)
   FROM scf_self_order.product_detail d
   JOIN scf_self_order.amp_product p ON p.id = d.product_id
   WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
     AND d.is_delete = 0 AND d.is_cancel = 0) AS source_detail_should_be_89,
  (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
   WHERE project_no='XXT-PRJ-ZY-A' AND is_delete=0 AND is_cancel=0) AS source_attachment_should_be_44,
  (SELECT COUNT(*) FROM scf_self_order.org_config
   WHERE org_code = 'HZF000144' AND org_type = 'CHANNEL' AND org_group_key = 'DEFAULT'
     AND is_delete = 0 AND is_cancel = 0) AS source_org_should_be_8,
  (SELECT COUNT(*) FROM scf_self_order.contract_config
   WHERE project_code = 'XXT-PRJ-ZY-A'
     AND template_code <> 'insurance.intermediary.service.agreement'
     AND is_delete = 0 AND is_cancel = 0) AS source_contract_should_be_32,
  (SELECT COUNT(*)
   FROM scf_self_order.contract_config c
   JOIN scf_self_order.contract_config_signers s ON s.contract_config_id = c.id
   WHERE c.project_code = 'XXT-PRJ-ZY-A'
     AND c.template_code <> 'insurance.intermediary.service.agreement'
     AND c.is_delete = 0 AND c.is_cancel = 0
     AND s.is_delete = 0 AND s.is_cancel = 0) AS source_signer_should_be_39,
  (SELECT COUNT(*) FROM scf_self_payment.business_config
   WHERE query_key = 'HZF000144' AND query_type='COOPERATION'
     AND group_key = 'PaymentConfig' AND config_key = 'pay_channel'
     AND is_delete = 0 AND is_cancel = 0) AS source_payment_should_be_1,
  (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
   WHERE merchant_no='HZF000144' AND channel_code='BCM'
     AND transaction_mode='SINGLE_REMIT'
     AND is_delete=0 AND is_cancel=0) AS source_bcm_single_remit_should_be_1,
  (SELECT COUNT(*) FROM scf_self_saps.business_config
   WHERE query_key IN ('HZF000144','XXT-PRJ-ZY-A')
     AND is_delete = 0 AND is_cancel = 0) AS source_saps_should_be_8;

-- A06 源自然键必须都无重复；四项必须全部为 0。
SELECT 'product_group_duplicate' AS check_item, COUNT(*) AS duplicate_key_count
FROM (
  SELECT g.label_code
  FROM scf_self_order.product_detail_group g
  JOIN scf_self_order.amp_product p ON p.id = g.product_id
  WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
    AND g.is_delete = 0 AND g.is_cancel = 0
  GROUP BY g.label_code HAVING COUNT(*) > 1
) x
UNION ALL
SELECT 'product_detail_duplicate', COUNT(*)
FROM (
  SELECT g.label_code, d.code
  FROM scf_self_order.product_detail d
  JOIN scf_self_order.product_detail_group g ON g.id = d.detail_group_id
  JOIN scf_self_order.amp_product p ON p.id = d.product_id
  WHERE p.code = 'PRD-ZY-A001' AND p.is_delete = 0 AND p.is_cancel = 0
    AND g.is_delete = 0 AND g.is_cancel = 0
    AND d.is_delete = 0 AND d.is_cancel = 0
  GROUP BY g.label_code, d.code HAVING COUNT(*) > 1
) x
UNION ALL
SELECT 'org_config_duplicate', COUNT(*)
FROM (
  SELECT org_type, org_group_key, org_detail_key
  FROM scf_self_order.org_config
  WHERE org_code = 'HZF000144' AND is_delete = 0 AND is_cancel = 0
  GROUP BY org_type, org_group_key, org_detail_key HAVING COUNT(*) > 1
) x
UNION ALL
SELECT 'contract_config_duplicate', COUNT(*)
FROM (
  SELECT scene_code, template_code
  FROM scf_self_order.contract_config
  WHERE project_code = 'XXT-PRJ-ZY-A'
    AND template_code <> 'insurance.intermediary.service.agreement'
    AND is_delete = 0 AND is_cancel = 0
  GROUP BY scene_code, template_code HAVING COUNT(*) > 1
) x;

/* ======================== B. 执行后验收 ======================== */

-- B01 硬性数量：15 / 89 / 44 / 8 / 32 / 39 / 1 / 1 / 8（含项目附件 44 条、BCM 单笔代付手续费 1 条）。
SELECT
  (SELECT COUNT(*) FROM scf_self_order.product_detail_group
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_group_should_be_15,
  (SELECT COUNT(*) FROM scf_self_order.product_detail
   WHERE product_id = 278 AND is_delete = 0 AND is_cancel = 0) AS product_detail_should_be_89,
  (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
   WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0) AS project_attachment_should_be_44,
  (SELECT COUNT(*) FROM scf_self_order.org_config
   WHERE org_code = 'HZF000133' AND org_type = 'CHANNEL' AND org_group_key = 'DEFAULT'
     AND is_delete = 0 AND is_cancel = 0) AS org_config_should_be_8,
  (SELECT COUNT(*) FROM scf_self_order.contract_config
   WHERE project_code = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0) AS contract_should_be_32,
  (SELECT COUNT(*) FROM scf_self_order.contract_config c
   JOIN scf_self_order.contract_config_signers s ON s.contract_config_id = c.id
   WHERE c.project_code = 'XXT-PRJ-YW-A'
     AND c.is_delete = 0 AND c.is_cancel = 0
     AND s.is_delete = 0 AND s.is_cancel = 0) AS signer_should_be_39,
  (SELECT COUNT(*) FROM scf_self_payment.business_config
   WHERE query_key = 'HZF000133' AND query_type='COOPERATION'
     AND group_key = 'PaymentConfig' AND config_key = 'pay_channel'
     AND config_value = 'JDPAY' AND config_status = 1
     AND is_delete = 0 AND is_cancel = 0) AS payment_config_should_be_1,
  (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
   WHERE merchant_no='HZF000133' AND channel_code='BCM' AND transaction_mode='SINGLE_REMIT'
     AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
     AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
     AND is_delete=0 AND is_cancel=0) AS bcm_single_remit_fee_should_be_1,
  (SELECT COUNT(*) FROM scf_self_saps.business_config
   WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A')
     AND config_status = 1 AND is_delete = 0 AND is_cancel = 0) AS saps_business_should_be_8;

-- B02 9 期及核心运行配置必须完全一致。
SELECT g.label_code, d.code, d.default_value
FROM scf_self_order.product_detail_group g
JOIN scf_self_order.product_detail d
  ON d.detail_group_id = g.id AND d.product_id = g.product_id
WHERE g.product_id = 278
  AND g.is_delete = 0 AND g.is_cancel = 0
  AND d.is_delete = 0 AND d.is_cancel = 0
  AND d.code IN (
      'period','InitialPaymentRatio','customerFeeRateMax','customerFeeRateMin','fpFeeRate',
      'repaymentPlanCalculation','plateformFeeRate','repayDateRule',
      'firstFunder','secondFunder','payment_support_channel'
  )
ORDER BY g.label_code, d.code;

-- 预期关键值：
-- period=9
-- InitialPaymentRatio={"9":0.1}
-- customerFeeRateMax={"9":0.36}
-- customerFeeRateMin={"9":0.001}
-- fpFeeRate={"9":0.04}
-- repaymentPlanCalculation=ZY_Customer
-- plateformFeeRate=0.005
-- payment_support_channel=JDPAY

-- B03 项目、产品回填；目标商业险费率仍为 9 期 3.8% + 3.2%，不得被山西覆盖。
SELECT id, code, project_no, repayment_type, grace_period_days,
       commercial_insurance_rate, customer_down_payment_ratio, penalty_interest_rate
FROM scf_self_order.amp_product
WHERE id = 278;

SELECT id, project_id, project_no, project_detail_key, value,
       insurance_type, period_type, period, product_ids
FROM scf_self_order.project_config
WHERE project_id = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

-- B04 结算场景必须仍为 0；非 0 立即停止进件并回查误执行脚本。
SELECT COUNT(*) AS statement_scene_must_be_0
FROM scf_self_saps.statement_scene
WHERE capital_project_code = 'XXT-PRJ-YW-A' AND is_delete = 0 AND is_cancel = 0;

-- B05 居间协议必须为 0。
SELECT COUNT(*) AS intermediary_contract_must_be_0
FROM scf_self_order.contract_config
WHERE project_code = 'XXT-PRJ-YW-A'
  AND template_code = 'insurance.intermediary.service.agreement'
  AND is_delete = 0 AND is_cancel = 0;

-- 目标仅商业险（insuranceType=1）：BINDING_CARD 运行时必须匹配 4 份合同、5 个签署人。
-- 非商业险模板的 use_condition={"insuranceType":"2"} 不应进入本次签约流程。
SELECT COUNT(*) AS binding_card_ci_contract_should_be_4,
       SUM(signer_count) AS binding_card_ci_signer_should_be_5
FROM (
  SELECT c.id, COUNT(s.id) AS signer_count
  FROM scf_self_order.contract_config c
  LEFT JOIN scf_self_order.contract_config_signers s
    ON s.contract_config_id = c.id
   AND s.is_delete = 0 AND s.is_cancel = 0
  WHERE c.project_code = 'XXT-PRJ-YW-A'
    AND c.scene_code = 'BINDING_CARD'
    AND c.template_code <> 'insurance.intermediary.service.agreement'
    AND c.is_delete = 0 AND c.is_cancel = 0
    AND (
      c.use_condition IS NULL OR c.use_condition = ''
      OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition, '$.insuranceType')) LIKE '%1%'
    )
  GROUP BY c.id
) binding_card_ci;

-- 投保签章配置必须为 3 份附件模板、3 个签署人。
-- 实际车辆缺少某一种原始保单时，对应填充器返回空并跳过该模板。
SELECT COUNT(*) AS binding_success_contract_should_be_3,
       SUM(signer_count) AS binding_success_signer_should_be_3
FROM (
  SELECT c.id, COUNT(s.id) AS signer_count
  FROM scf_self_order.contract_config c
  LEFT JOIN scf_self_order.contract_config_signers s
    ON s.contract_config_id = c.id
   AND s.is_delete = 0 AND s.is_cancel = 0
  WHERE c.project_code = 'XXT-PRJ-YW-A'
    AND c.scene_code = 'BINDING_SUCCESS'
    AND c.generateType = 'ATTACHMENT'
    AND c.is_delete = 0 AND c.is_cancel = 0
  GROUP BY c.id
) binding_success;

-- B06 SAPS 使用产品专属配置，不存在时回退 DEF；以下默认科目是生成缴费单和客户账单的硬前置。
-- 必须依次为 4 / 5 / 2。客户账单科目按编码去重，忽略现库历史重复行；无需再复制产品专属科目。
SELECT 'payment_slip_subject_DEF' AS check_item, COUNT(*) AS actual_count, 4 AS expected_count
FROM scf_self_saps.payment_slip_subject
WHERE product_code = 'DEF'
  AND subject_no IN ('201','10201','10203','103')
  AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'bill_subject_DEF', COUNT(*), 5
FROM scf_self_saps.bill_subject
WHERE product_code = 'DEF'
  AND subject_no IN ('101','10102','10201','10202','103')
  AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'customer_bill_subject_101_10102_DEF', COUNT(DISTINCT subject_no), 2
FROM scf_self_saps.customer_bill_subject
WHERE product_code = 'DEF'
  AND subject_no IN ('101','10102')
  AND is_delete = 0 AND is_cancel = 0;

-- B06-1 放款支付运行项：依次必须为 1 / 1 / 1。
SELECT 'target_BCM_SINGLE_REMIT_fee' AS check_item, COUNT(*) AS actual_count, 1 AS expected_count
FROM scf_self_payment.channel_fee_config
WHERE merchant_no='HZF000133' AND channel_code='BCM' AND transaction_mode='SINGLE_REMIT'
  AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
  AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
  AND is_delete=0 AND is_cancel=0
UNION ALL
SELECT 'DEF_bcmMockFlag_true', COUNT(*), 1
FROM scf_self_payment.business_config
WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
  AND config_key='bcmMockFlag' AND config_value='true' AND config_status=1
  AND is_delete=0 AND is_cancel=0
UNION ALL
SELECT 'DEF_bcmMockUrl_present', COUNT(*), 1
FROM scf_self_payment.business_config
WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
  AND config_key='bcmMockUrl' AND config_value IS NOT NULL AND config_value<>'' AND config_status=1
  AND is_delete=0 AND is_cancel=0;

-- B07 资金方硬校验：两项都必须为 1。
SELECT 'first_funder_ZJF000015_9_ci' AS check_item, COUNT(*) AS actual_count, 1 AS expected_count
FROM scf_self_order.org_funding_party
WHERE org_code = 'ZJF000015' AND status = 1
  AND JSON_CONTAINS(periods,'"9"')
  AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
  AND supported_insurance_types = '1'
  AND is_delete = 0 AND is_cancel = 0
UNION ALL
SELECT 'second_funder_ZJF000011_9', COUNT(*), 1
FROM scf_self_order.org_funding_party
WHERE org_code = 'ZJF000011' AND status = 1
  AND JSON_CONTAINS(periods,'"9"')
  AND JSON_EXTRACT(supported_periods,'$."9"') IS NOT NULL
  AND is_delete = 0 AND is_cancel = 0;

-- B08 最终单行放行闸门：只有 gate_result=GO 才允许新建进件。
-- 任一项不满足都返回 STOP，再根据 B01~B07 定位具体项。
SELECT CASE WHEN
  (SELECT COUNT(*) FROM scf_self_order.product_detail_group
   WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=15
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail
       WHERE product_id=278 AND is_delete=0 AND is_cancel=0)=89
  AND (SELECT COUNT(*) FROM scf_self_order.project_attachment_config
       WHERE project_no='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=44
  AND (SELECT COUNT(*) FROM scf_self_order.org_config
       WHERE org_code='HZF000133' AND org_type='CHANNEL' AND org_group_key='DEFAULT'
         AND is_delete=0 AND is_cancel=0)=8
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config
       WHERE project_code='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=32
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       JOIN scf_self_order.contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code='XXT-PRJ-YW-A'
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=39
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config
       WHERE project_code='XXT-PRJ-YW-A'
         AND template_code='insurance.intermediary.service.agreement'
         AND is_delete=0 AND is_cancel=0)=0
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       WHERE c.project_code='XXT-PRJ-YW-A' AND c.scene_code='BINDING_CARD'
         AND (c.use_condition IS NULL OR c.use_condition=''
              OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
         AND c.is_delete=0 AND c.is_cancel=0)=4
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       JOIN scf_self_order.contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code='XXT-PRJ-YW-A' AND c.scene_code='BINDING_CARD'
         AND (c.use_condition IS NULL OR c.use_condition=''
              OR JSON_UNQUOTE(JSON_EXTRACT(c.use_condition,'$.insuranceType')) LIKE '%1%')
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=5
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config
       WHERE project_code='XXT-PRJ-YW-A' AND scene_code='BINDING_SUCCESS'
         AND `generateType`='ATTACHMENT' AND is_delete=0 AND is_cancel=0)=3
  AND (SELECT COUNT(*) FROM scf_self_order.contract_config c
       JOIN scf_self_order.contract_config_signers s ON s.contract_config_id=c.id
       WHERE c.project_code='XXT-PRJ-YW-A' AND c.scene_code='BINDING_SUCCESS'
         AND c.`generateType`='ATTACHMENT'
         AND c.is_delete=0 AND c.is_cancel=0 AND s.is_delete=0 AND s.is_cancel=0)=3
  AND (SELECT COUNT(*) FROM scf_self_payment.business_config
       WHERE query_key='HZF000133' AND query_type='COOPERATION'
         AND group_key='PaymentConfig' AND config_key='pay_channel'
         AND config_value='JDPAY' AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.router
       WHERE cooperation_code='HZF000133' AND project_no='XXT-PRJ-YW-A'
         AND product_code='PRD-YW-A001' AND payment_product='DS' AND channel='JDPAY'
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_pay_config
       WHERE cooperation_code='HZF000133' AND channel_code='JDPAY' AND status=1
         AND channel_url<>'' AND member_id<>'' AND terminal_id<>'' AND resv_one<>''
         AND notify_url<>'' AND pay_config_code<>''
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_pay_config t
       JOIN scf_self_payment.channel_pay_config s
         ON s.cooperation_code='HZF000144' AND s.channel_code='JDPAY' AND s.status=1
        AND s.is_delete=0 AND s.is_cancel=0
       WHERE t.cooperation_code='HZF000133' AND t.channel_code='JDPAY' AND t.status=1
         AND t.channel_url=s.channel_url AND t.member_id=s.member_id
         AND t.terminal_id=s.terminal_id AND t.resv_one=s.resv_one
         AND t.notify_url=s.notify_url
         AND t.is_delete=0 AND t.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
       WHERE merchant_no='HZF000133' AND channel_code='JDPAY'
         AND transaction_mode='AGREEMENT_PAYMENT' AND channel_fee_rate=0.002
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.channel_fee_config
       WHERE merchant_no='HZF000133' AND channel_code='BCM'
         AND transaction_mode='SINGLE_REMIT'
         AND card_type=1 AND bank_rule='OTHER' AND channel_fee_rule='1'
         AND channel_fee=0 AND channel_fee_rate=0 AND platform_fee_enable=1
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.business_config
       WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
         AND config_key='bcmMockFlag' AND config_value='true' AND config_status=1
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_payment.business_config
       WHERE query_key='DEF' AND query_type='PROJECT' AND group_key='RemitConfig'
         AND config_key='bcmMockUrl' AND config_value IS NOT NULL AND config_value<>''
         AND config_status=1 AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_saps.business_config
       WHERE query_key IN ('HZF000133','XXT-PRJ-YW-A') AND config_status=1
         AND is_delete=0 AND is_cancel=0)=8
  AND (SELECT COUNT(*) FROM scf_self_saps.statement_scene
       WHERE capital_project_code='XXT-PRJ-YW-A' AND is_delete=0 AND is_cancel=0)=0
  AND (SELECT COUNT(*) FROM scf_self_order.amp_product
       WHERE id=278 AND code='PRD-YW-A001' AND project_no='XXT-PRJ-YW-A'
         AND repayment_type='4' AND grace_period_days=0 AND status=1
         AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.project_config
       WHERE project_id='XXT-PRJ-YW-A' AND project_no='XXT-PRJ-YW-A'
         AND project_detail_key='MarginRatio' AND CAST(value AS DECIMAL(10,4))=0.1
         AND insurance_type='1' AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='period' AND d.default_value='9'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='InitialPaymentRatio'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.1'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='fpFeeRate'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.04'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='customerFeeRateMax'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.36'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='customerFeeRateMin'
         AND JSON_UNQUOTE(JSON_EXTRACT(d.default_value,'$."9"'))='0.001'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='plateformFeeRate' AND d.default_value='0.005'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='BASE_CONFIG'
         AND d.code='repaymentPlanCalculation' AND d.default_value='ZY_Customer'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.product_detail d
       JOIN scf_self_order.product_detail_group g ON g.id=d.detail_group_id
       WHERE d.product_id=278 AND g.product_id=278 AND g.label_code='PAY_CONFIG'
         AND d.code='payment_support_channel' AND d.default_value='JDPAY'
         AND d.is_delete=0 AND d.is_cancel=0 AND g.is_delete=0 AND g.is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_saps.payment_slip_subject
       WHERE product_code='DEF' AND subject_no IN ('201','10201','10203','103')
         AND is_delete=0 AND is_cancel=0)=4
  AND (SELECT COUNT(*) FROM scf_self_saps.bill_subject
       WHERE product_code='DEF' AND subject_no IN ('101','10102','10201','10202','103')
         AND is_delete=0 AND is_cancel=0)=5
  AND (SELECT COUNT(DISTINCT subject_no) FROM scf_self_saps.customer_bill_subject
       WHERE product_code='DEF' AND subject_no IN ('101','10102')
         AND is_delete=0 AND is_cancel=0)=2
  AND (SELECT COUNT(*) FROM scf_self_order.org_funding_party
       WHERE org_code='ZJF000015' AND status=1 AND JSON_CONTAINS(periods,'"9"')
         AND supported_insurance_types='1' AND is_delete=0 AND is_cancel=0)=1
  AND (SELECT COUNT(*) FROM scf_self_order.org_funding_party
       WHERE org_code='ZJF000011' AND status=1 AND JSON_CONTAINS(periods,'"9"')
         AND is_delete=0 AND is_cancel=0)=1
THEN 'GO' ELSE 'STOP' END AS gate_result;

-- B09 环境待补偿队列门禁（不属于 HZF000133 配置）。
-- 正确补偿入口是 SAPS 的 transferTransactionSchedule；它按 transfer_no 定位，并用 payment_req_id 去支付系统查单。
-- scf-order 的 loanPaymentQueryStatusJob 存在查询键不一致：SAPS business_no 存的是申请单号，代码却用 LP 支付号查询，不能用于本次补偿。
-- READY 只表示当前没有可补偿的超时处理中转账，不等于已经证明 XXL 定时调度恢复；新单仍须按 C03-2 观察。
SELECT CASE WHEN
  (SELECT COUNT(*) FROM scf_self_saps.transfer_transaction
   WHERE transfer_type=10 AND status=2
     AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
     AND real_retry_count<5 AND is_delete=0 AND is_cancel=0)=0
  THEN 'READY' ELSE 'BLOCKED_BY_STALE_SAPS_LOAN_TRANSFER' END AS environment_result,
  (SELECT COUNT(*) FROM scf_self_order.loan_order
   WHERE status='340' AND create_time<DATE_SUB(NOW(),INTERVAL 10 MINUTE)
     AND is_delete=0 AND is_cancel=0) AS stale_loan_340_count,
  (SELECT COUNT(*) FROM scf_self_saps.transfer_transaction
   WHERE transfer_type=10 AND status=2
     AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
     AND real_retry_count<5 AND is_delete=0 AND is_cancel=0) AS saps_transfer_waiting_retry_count,
  (SELECT COUNT(*) FROM scf_self_saps.transfer_transaction
   WHERE transfer_type=10 AND status=2
     AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
     AND real_retry_count>=5 AND is_delete=0 AND is_cancel=0) AS saps_transfer_retry_exhausted_count,
  (SELECT MAX(payment_time) FROM scf_self_order.loan_order
   WHERE status='351' AND is_delete=0 AND is_cancel=0) AS latest_success_payment_time;

-- B09-1 需要补偿的 SAPS 放款转账明细。每个 transfer_no 单独触发一次 transferTransactionSchedule，随后重跑 B09。
-- 任务参数：{"transferNos":["查询结果中的 transfer_no"]}。Mock 310204 已实测返回 stat=1（成功）。
SELECT transfer_no, business_no AS application_no, order_id AS pay_apply_no,
       payment_req_id, cooperation_code, status, real_retry_count,
       running_status, instance_status, order_time
FROM scf_self_saps.transfer_transaction
WHERE transfer_type=10 AND status=2
  AND order_time<DATE_SUB(NOW(),INTERVAL 5 MINUTE)
  AND real_retry_count<5 AND is_delete=0 AND is_cancel=0
ORDER BY order_time, id;

/* ======================== C. 新进件链路跟踪 ======================== */

-- 必须是配置脚本执行后创建并完成预审的新申请单。
-- 不要使用旧单 APA260813205018000001：其 tech_service_fee_ratio 已在明细为空时固化为 0。
SET @application_no = '请替换为新申请单号';

-- C01 进件主状态、签约及首付缴费单引用。
SELECT a.application_no, a.status, a.project_no, a.product_code, a.partner_code,
       a.funder_code, a.sign_status, a.start_sign_flow_date,
       al.loan_period, al.first_payment_status, al.first_payment_slip_no,
       al.loan_application_no, al.loan_time
FROM scf_self_order.amp_application a
LEFT JOIN scf_self_order.application_loan al
  ON al.application_no = a.application_no AND al.is_delete = 0 AND al.is_cancel = 0
WHERE a.application_no = @application_no AND a.is_delete = 0 AND a.is_cancel = 0;

-- C01-1 费用汇总守恒：差额必须为 0；首笔款必须大于 0。
-- 仅商业险产品的 other_fee 为未融资交强险/附加险/车船税，会一并进入首笔款，属于正常逻辑。
SELECT al.application_no, al.loan_period, al.initial_payment_ratio,
       al.tech_service_fee_ratio, al.repayment_type,
       al.initial_payment, al.operation_service_fee, al.channel_service_fee, al.other_fee,
       al.first_payment,
       al.first_payment - (
         COALESCE(al.initial_payment,0)
         + COALESCE(al.operation_service_fee,0)
         + COALESCE(al.channel_service_fee,0)
         + COALESCE(al.other_fee,0)
       ) AS first_payment_diff_must_be_0
FROM scf_self_order.application_loan al
WHERE al.application_no = @application_no AND al.is_delete = 0 AND al.is_cancel = 0;

-- 必须值：loan_period=9、initial_payment_ratio=0.1、tech_service_fee_ratio=0.005、repayment_type=4。

-- C01-1A 保司与车辆材料完整性：提交进件前检查。
-- insurance_company_required_fields_present 必须为 1；选择线上盖章时，vci_file_count 必须等于 vehicle_count。
SELECT aic.application_no,
       CASE WHEN aic.company_name IS NOT NULL AND aic.company_name<>''
                  AND aic.receiving_account IS NOT NULL AND aic.receiving_account<>''
                  AND aic.receiving_bank IS NOT NULL AND aic.receiving_bank<>''
                  AND aic.cnaps IS NOT NULL AND aic.cnaps<>''
                  AND aic.payment_summary IS NOT NULL AND aic.payment_summary<>''
                  AND aic.sign_mode IS NOT NULL
            THEN 1 ELSE 0 END AS insurance_company_required_fields_present,
       aic.sign_mode,
       COUNT(av.id) AS vehicle_count,
       SUM(CASE WHEN av.original_vci_file IS NOT NULL AND av.original_vci_file<>'' THEN 1 ELSE 0 END) AS vci_file_count,
       SUM(CASE WHEN av.vci_amount IS NOT NULL AND av.vci_amount>0 THEN 1 ELSE 0 END) AS positive_vci_amount_count
FROM scf_self_order.application_insurance_company aic
LEFT JOIN scf_self_order.application_vehicle av
  ON av.application_no=aic.application_no AND av.is_delete=0 AND av.is_cancel=0
WHERE aic.application_no=@application_no AND aic.is_delete=0 AND aic.is_cancel=0
GROUP BY aic.application_no, aic.company_name, aic.receiving_account,
         aic.receiving_bank, aic.cnaps, aic.payment_summary, aic.sign_mode;

-- C01-2 签约流程：BINDING_CARD 和每辆车的 BINDING_SUCCESS 最终都应为 SIGN_SUCCESS。
SELECT cp.biz_source_code, cp.scene_code, cp.contract_process_code,
       cp.sign_status, cp.create_time, cp.modify_time
FROM scf_self_order.contract_process cp
WHERE (cp.biz_source_code = @application_no
       OR cp.biz_source_code LIKE CONCAT(@application_no, '#%'))
  AND cp.scene_code IN ('BINDING_CARD','BINDING_SUCCESS')
  AND cp.is_delete = 0 AND cp.is_cancel = 0
ORDER BY cp.id;

-- C01-3 绑卡：申请/确认成功后，目标渠道必须出现 JDPAY 有效协议（status=1）。
SELECT application_no, mer_id, cid, status, card_type,
       CASE WHEN agree_id IS NOT NULL AND agree_id <> '' THEN 1 ELSE 0 END AS agree_id_present,
       CASE WHEN platform_agree_id IS NOT NULL AND platform_agree_id <> '' THEN 1 ELSE 0 END AS platform_agree_id_present,
       create_time
FROM scf_self_payment.card_binding
WHERE application_no = @application_no
  AND mer_id = 'HZF000133'
  AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C01-4 产品实例仅作观察，不是本链路的强制放行条件。
-- master 在无 product_instance 时会回退当前产品配置，并用 application_loan 的期数/对客费率覆盖。
SELECT pi.id, pi.application_no, pi.prod_code, pi.product_id,
       pi.status, pi.type, pi.repayment_type,
       COUNT(pid.id) AS instance_override_count
FROM scf_self_order.product_instance pi
LEFT JOIN scf_self_order.product_instance_detail pid
  ON pid.product_instance_id = pi.id
 AND pid.is_delete = 0 AND pid.is_cancel = 0
WHERE pi.application_no = @application_no
  AND pi.is_delete = 0 AND pi.is_cancel = 0
GROUP BY pi.id, pi.application_no, pi.prod_code, pi.product_id,
         pi.status, pi.type, pi.repayment_type;

-- C02 缴费单：生成后应有 1 条；支付成功时 status=4。
-- status=4 且 notify=0 表示支付已成功、但还没成功通知订单系统；定时任务最多重试 5 次。
SELECT serial_no, application_no, status, project_no, product_code,
       total_amount, paid_amount, unpaid_amount, payment_type,
       notify, retry_count, fail_msg, create_time, finish_time
FROM scf_self_saps.payment_slip
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C02-1 缴费单与费用科目必须守恒：三个差额都必须为 0。
-- 未支付时：total=unpaid、paid=0；支付完成时：total=paid、unpaid=0。
SELECT ps.serial_no,
       ps.total_amount,
       COALESCE(SUM(d.total_amount),0) AS detail_total_amount,
       ps.total_amount - COALESCE(SUM(d.total_amount),0) AS total_diff_must_be_0,
       ps.paid_amount - COALESCE(SUM(d.paid_amount),0) AS paid_diff_must_be_0,
       ps.unpaid_amount - COALESCE(SUM(d.unpaid_amount),0) AS unpaid_diff_must_be_0
FROM scf_self_saps.payment_slip ps
LEFT JOIN scf_self_saps.payment_slip_fee_detail d
  ON d.serial_no = ps.serial_no AND d.is_delete = 0 AND d.is_cancel = 0
WHERE ps.application_no = @application_no
  AND ps.is_delete = 0 AND ps.is_cancel = 0
GROUP BY ps.serial_no, ps.total_amount, ps.paid_amount, ps.unpaid_amount;

-- C03 放款单：成功基准 status=351、payment_time 非空。
SELECT application_no, loan_application_no, status, loan_period,
       cooperation_code, funder_code, payment_time, sign_status,
       financing_status, create_time
FROM scf_self_order.loan_order
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C03-1 放款支付明细：payment_result=2 表示处理中；超过10分钟时继续执行 C03-2 定位 SAPS 转账。
SELECT lo.application_no, lo.loan_application_no, lo.status AS loan_status,
       lp.payment_channel, lp.payment_result, lp.payment_amt,
       lp.payment_time, lp.reason, lp.create_time, lp.modify_time
FROM scf_self_order.loan_order lo
LEFT JOIN scf_self_order.loan_payment lp
  ON lp.loan_application_no=lo.loan_application_no AND lp.is_delete=0 AND lp.is_cancel=0
WHERE lo.application_no=@application_no AND lo.is_delete=0 AND lo.is_cancel=0
ORDER BY lp.id;

-- C03-2 SAPS/支付系统代付状态：处理中超过5分钟时，按 transfer_no 单笔触发 SAPS transferTransactionSchedule。
-- XXL-Job 原始任务参数：{"transferNos":["这里填 transfer_no"]}。禁止再次申请放款。
SELECT lo.application_no, lo.loan_application_no, lo.status AS loan_status,
       lp.pay_apply_no, lp.payment_result AS order_payment_result,
       tt.transfer_no, tt.payment_req_id, tt.status AS saps_transfer_status,
       tt.real_retry_count, tt.running_status, tt.instance_status,
       ob.serial_no AS payment_serial_no, ob.status AS payment_order_status,
       ob.channel_id AS payment_channel, ob.create_time AS payment_create_time,
       ob.modify_time AS payment_modify_time
FROM scf_self_order.loan_order lo
LEFT JOIN scf_self_order.loan_payment lp
  ON lp.loan_application_no=lo.loan_application_no AND lp.is_delete=0 AND lp.is_cancel=0
LEFT JOIN scf_self_saps.transfer_transaction tt
  ON tt.business_no=lo.application_no AND tt.transfer_type=10
 AND tt.order_id=lp.pay_apply_no AND tt.is_delete=0 AND tt.is_cancel=0
LEFT JOIN scf_self_payment.order_business ob
  ON ob.biz_serial_no=tt.payment_req_id AND ob.cooperation_code=tt.cooperation_code
 AND ob.is_delete=0 AND ob.is_cancel=0
WHERE lo.application_no=@application_no AND lo.is_delete=0 AND lo.is_cancel=0
ORDER BY tt.id, ob.id;

-- C04 客户账单：9 期产品应生成 9 条。
SELECT application_no, project_code, product_code, total_period,
       COUNT(*) AS bill_count, MIN(create_time) AS first_bill_time
FROM scf_self_saps.customer_bill
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
GROUP BY application_no, project_code, product_code, total_period;

-- C04-1 每一期客户账单与客户科目明细必须守恒；应返回 9 行，detail_diff_must_be_0 全为 0。
-- 目标为商业险-only，但正式计划若包含非商业险本金，DEF/10102 也必须能生成客户明细。
SELECT cb.period, cb.bill_no, cb.total_amount,
       COALESCE(SUM(d.total_amount),0) AS customer_detail_total,
       cb.total_amount-COALESCE(SUM(d.total_amount),0) AS detail_diff_must_be_0,
       GROUP_CONCAT(CONCAT(d.subject_no,':',d.total_amount) ORDER BY d.subject_no) AS customer_details
FROM scf_self_saps.customer_bill cb
LEFT JOIN scf_self_saps.customer_bill_fee_detail d
  ON d.bill_no=cb.bill_no AND d.is_delete=0 AND d.is_cancel=0
WHERE cb.application_no=@application_no AND cb.is_delete=0 AND cb.is_cancel=0
GROUP BY cb.id,cb.period,cb.bill_no,cb.total_amount
ORDER BY cb.period;

-- C05 正式还款计划/推送状态诊断。
-- 正常应有 9 条主计划；推送成功后 status=1、is_lock=0。
-- 若放款成功后存在 status=0、is_lock=1，表示待推送或上次推送失败。
SELECT loan_id, application_no, total_period, category, status, is_lock,
       COUNT(*) AS plan_count, MIN(create_time) AS first_plan_time
FROM scf_self_order.repayment_plan
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
GROUP BY loan_id, application_no, total_period, category, status, is_lock
ORDER BY category, status, is_lock;

-- C06 还款计划汇总主表应有 1 条，total_period=9。
SELECT loan_id, application_no, project_no, product_code, total_period,
       create_time
FROM scf_self_order.loan_repayment_plan
WHERE application_no = @application_no AND is_delete = 0 AND is_cancel = 0
ORDER BY id DESC;

-- C07 全链路单行摘要：便于快速判断卡在哪一层。
SELECT a.application_no,
       a.status AS application_status,
       al.loan_period,
       al.first_payment_status,
       al.first_payment_slip_no,
       ps.status AS payment_slip_status,
       ps.notify AS payment_slip_notify,
       ps.retry_count AS payment_slip_retry_count,
       lo.loan_application_no,
       lo.status AS loan_status,
       lo.payment_time,
       (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
        WHERE rp.application_no=a.application_no AND rp.category=1
          AND rp.is_delete=0 AND rp.is_cancel=0) AS main_plan_count,
       (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no
          AND cb.is_delete=0 AND cb.is_cancel=0) AS customer_bill_count
FROM scf_self_order.amp_application a
LEFT JOIN scf_self_order.application_loan al
  ON al.application_no=a.application_no AND al.is_delete=0 AND al.is_cancel=0
LEFT JOIN scf_self_saps.payment_slip ps
  ON ps.application_no=a.application_no AND ps.is_delete=0 AND ps.is_cancel=0
LEFT JOIN scf_self_order.loan_order lo
  ON lo.application_no=a.application_no AND lo.is_delete=0 AND lo.is_cancel=0
WHERE a.application_no=@application_no AND a.is_delete=0 AND a.is_cancel=0
ORDER BY ps.id DESC, lo.id DESC
LIMIT 1;

-- C08 最终验收门禁：PASS 才表示“新渠道进件→缴费成功通知→放款成功→9期客户账单”完整通过。
-- WAIT 表示链路尚未走完；FAIL 表示已出现终态冲突、非目标编码或期数/账单结构异常。
SELECT CASE
  WHEN a.application_no IS NULL THEN 'FAIL'
  WHEN a.project_no<>'XXT-PRJ-YW-A' OR a.product_code<>'PRD-YW-A001' OR a.partner_code<>'HZF000133' THEN 'FAIL'
  WHEN al.loan_period IS NOT NULL AND al.loan_period<>9 THEN 'FAIL'
  WHEN ps.status IS NOT NULL AND ps.status IN (3,5) THEN 'FAIL'
  WHEN lo.status IS NOT NULL AND lo.status IN ('320','350','352','355','360','361') THEN 'FAIL'
  WHEN (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
        WHERE rp.application_no=a.application_no AND rp.category=1
          AND rp.is_delete=0 AND rp.is_cancel=0)>0
       AND NOT ((SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
                 WHERE rp.application_no=a.application_no AND rp.category=1
                   AND rp.total_period=9 AND rp.period BETWEEN 1 AND 9
                   AND rp.is_delete=0 AND rp.is_cancel=0)=9
                AND (SELECT COUNT(DISTINCT rp.period) FROM scf_self_order.repayment_plan rp
                     WHERE rp.application_no=a.application_no AND rp.category=1
                       AND rp.is_delete=0 AND rp.is_cancel=0)=9) THEN 'FAIL'
  WHEN (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no AND cb.is_delete=0 AND cb.is_cancel=0)>0
       AND NOT ((SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
                 WHERE cb.application_no=a.application_no
                   AND cb.project_code='XXT-PRJ-YW-A' AND cb.product_code='PRD-YW-A001'
                   AND cb.total_period=9 AND cb.period BETWEEN 1 AND 9
                   AND cb.is_delete=0 AND cb.is_cancel=0)=9
                AND (SELECT COUNT(DISTINCT cb.period) FROM scf_self_saps.customer_bill cb
                     WHERE cb.application_no=a.application_no
                       AND cb.is_delete=0 AND cb.is_cancel=0)=9) THEN 'FAIL'
  WHEN (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no AND cb.is_delete=0 AND cb.is_cancel=0)>0
       AND EXISTS (SELECT 1
                   FROM scf_self_saps.customer_bill cb
                   WHERE cb.application_no=a.application_no
                     AND cb.is_delete=0 AND cb.is_cancel=0
                     AND cb.total_amount<>(SELECT COALESCE(SUM(d.total_amount),0)
                                          FROM scf_self_saps.customer_bill_fee_detail d
                                          WHERE d.bill_no=cb.bill_no
                                            AND d.is_delete=0 AND d.is_cancel=0)) THEN 'FAIL'
  WHEN a.status='351'
       AND al.loan_period=9 AND al.initial_payment_ratio=0.1
       AND al.tech_service_fee_ratio=0.005 AND al.repayment_type=4
       AND ps.status=4 AND ps.notify=1
       AND ps.total_amount=ps.paid_amount AND ps.unpaid_amount=0
       AND lo.status='351' AND lo.loan_period=9 AND lo.payment_time IS NOT NULL
       AND (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
            WHERE rp.application_no=a.application_no AND rp.category=1
              AND rp.total_period=9 AND rp.period BETWEEN 1 AND 9
              AND rp.status='1' AND rp.is_lock='0'
              AND rp.is_delete=0 AND rp.is_cancel=0)=9
       AND (SELECT COUNT(DISTINCT rp.period) FROM scf_self_order.repayment_plan rp
            WHERE rp.application_no=a.application_no AND rp.category=1
              AND rp.is_delete=0 AND rp.is_cancel=0)=9
       AND (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
            WHERE cb.application_no=a.application_no
              AND cb.project_code='XXT-PRJ-YW-A' AND cb.product_code='PRD-YW-A001'
              AND cb.total_period=9 AND cb.period BETWEEN 1 AND 9
              AND cb.is_delete=0 AND cb.is_cancel=0)=9
       AND (SELECT COUNT(DISTINCT cb.period) FROM scf_self_saps.customer_bill cb
            WHERE cb.application_no=a.application_no
              AND cb.is_delete=0 AND cb.is_cancel=0)=9
       AND (SELECT COUNT(*)
            FROM scf_self_saps.customer_bill cb
            WHERE cb.application_no=a.application_no
              AND cb.is_delete=0 AND cb.is_cancel=0
              AND cb.total_amount=(SELECT COALESCE(SUM(d.total_amount),0)
                                   FROM scf_self_saps.customer_bill_fee_detail d
                                   WHERE d.bill_no=cb.bill_no
                                     AND d.is_delete=0 AND d.is_cancel=0))=9
    THEN 'PASS'
  ELSE 'WAIT'
END AS end_to_end_result,
       input.application_no, a.status AS application_status,
       al.loan_period, al.first_payment_slip_no,
       ps.status AS payment_slip_status, ps.notify AS payment_slip_notify,
       lo.status AS loan_status,
       (SELECT COUNT(*) FROM scf_self_order.repayment_plan rp
        WHERE rp.application_no=a.application_no AND rp.category=1
          AND rp.is_delete=0 AND rp.is_cancel=0) AS main_plan_count,
       (SELECT COUNT(*) FROM scf_self_saps.customer_bill cb
        WHERE cb.application_no=a.application_no
          AND cb.is_delete=0 AND cb.is_cancel=0) AS customer_bill_count
FROM (SELECT @application_no AS application_no) input
LEFT JOIN scf_self_order.amp_application a
  ON a.application_no=input.application_no AND a.is_delete=0 AND a.is_cancel=0
LEFT JOIN scf_self_order.application_loan al
  ON al.application_no=a.application_no AND al.is_delete=0 AND al.is_cancel=0
LEFT JOIN scf_self_saps.payment_slip ps
  ON ps.id=(SELECT MAX(ps2.id) FROM scf_self_saps.payment_slip ps2
            WHERE ps2.application_no=a.application_no AND ps2.is_delete=0 AND ps2.is_cancel=0)
LEFT JOIN scf_self_order.loan_order lo
  ON lo.id=(SELECT MAX(lo2.id) FROM scf_self_order.loan_order lo2
            WHERE lo2.application_no=a.application_no AND lo2.is_delete=0 AND lo2.is_cancel=0)
;
```

