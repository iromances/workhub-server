# 智能柜 Nacos 和 XXL-JOB 升级方案与改动点

版本 v1.0；日期 2026-10-30；状态：待实施方案。业务线 BL000005 / ol。需求编码：REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba。

目标：Nacos 服务端及客户端 **2.5.4**，XXL-JOB 调度中心及官方 core **2.5.0**。共用版本、数据迁移、验收、切换和回退规则见 [总方案](design.md)；本文列明本业务线相对消费分期示例的具体改动。代码基线为刷新后的 `origin/master` 固定提交，生产运行状态与实际任务记录待发布前核对。

## 1 三条依赖升级路径

智能柜同时使用两套公共父工程，另有独立 Boot 应用。需要迁移的 Nacos 应用在源码中可分为以下 9 个；其他仓库是否参与发布按真实部署确认。

| 仓库 | 父工程与基线 | 改动 |
| --- | --- | --- |
| ol-order / ol-payment / ol-saps / ol-gateway | lease-dependencies 0.0.1 | 改用拟发布的 lease-dependencies 0.0.2，随之使用新版 lease-schedule/MQ；保留应用名 ol-* |
| ol-risk / ol-intf-risk / ol-opgateway / ol-partner-gateway | thctay-dependencies 0.0.1 | 改用 thctay-dependencies 0.0.2；ol-risk 清理旧 core/schedule 覆盖 |
| ol-marketing | Boot 2.2.9 父工程，自管 Alibaba 2.2.1 | 在本工程 dependencyManagement 显式管理 nacos-client 2.5.4；不误改为公共父工程，不主动加入未使用的 XXL 依赖 |
| common-order | Java 21 / Boot 3.3.5，主分支未发现 Nacos/XXL 声明 | 不套用旧 Java 8 方案；只核对实际接口依赖与部署范围 |
| ol-intf-junxin | 独立 Boot 2.6.13，未发现本次中间件声明 | 本次无已定位的依赖改造，保留接口联调 |
| ol-admin / ol-h5 / ol-agent-h5 / ol-dealer-h5 / ol-rent-mp | 前端仓库 | 无 Java 依赖改动，验证网关和业务接口 |

`ol-order/order-model` 对 thctay-security、`ol-marketing/marketing-api` 对 lease-permission 等未受影响公共组件的原版本保留；不能因混用公共组件就把所有构件都升到 0.0.2。

## 2 公共件和 POM 改动

依赖 [叉车方案的 lease-public 改动](叉车Nacos和XXLJOB升级方案改动点_20261030.md#2-lease-public-的改动)，以及已有 thctay-public 升级分支。lease MQ 必须继续解析 MessageSenderRetryScheduleDTO，不能照搬 thctay 的无参 retry。

| 文件 | 现有显式依赖 | 改动 |
| --- | --- | --- |
| [ol-order/order-service/pom.xml](http://10.10.116.21:10000/ol/ol-order/-/blob/2152d2f86dbed7af93743fc6a79cef0cbf4d12da/order-service/pom.xml) | `lease-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [ol-risk/risk-schedule/pom.xml](http://10.10.116.21:10000/ol/ol-risk/-/blob/5a7655fb9b19aa161e2197349a2e2323885bff46/risk-schedule/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [ol-risk/risk-schedule/pom.xml](http://10.10.116.21:10000/ol/ol-risk/-/blob/5a7655fb9b19aa161e2197349a2e2323885bff46/risk-schedule/pom.xml) | `xxl-job-core:2.2.0` | 删除旧显式版本，由已升级父工程管理 |

ol-payment 与 ol-saps 根 POM 已有无版本 lease-mq，父版本更新后由新父管理；仍需检查最终依赖树、制品包及组件扫描。Gateway 无本地任务入口也需迁 Nacos。公共件源码存在不等于制品库或 Jenkins 已具备新版，发布前锁定版本和提交。

## 3 任务改造规模

| 仓库 | 主分支提交 | 源码入口数 | 处理范围 |
| --- | --- | ---: | --- |
| [ol-order](http://10.10.116.21:10000/ol/ol-order/-/blob/2152d2f86dbed7af93743fc6a79cef0cbf4d12da/pom.xml) | `2152d2f8` | 24 | 任务签名、参数与结果反馈 |
| [ol-payment](http://10.10.116.21:10000/ol/ol-payment/-/blob/d9cd90f61461d44cf024aae7f56ceab8256403b1/pom.xml) | `d9cd90f6` | 8 | 任务签名、参数与结果反馈 |
| [ol-risk](http://10.10.116.21:10000/ol/ol-risk/-/blob/5a7655fb9b19aa161e2197349a2e2323885bff46/pom.xml) | `5a7655fb` | 3 | 任务签名、参数与结果反馈 |
| [ol-saps](http://10.10.116.21:10000/ol/ol-saps/-/blob/3036e4e4ca17bf915da936830e65f1354c2a682c/pom.xml) | `3036e4e4` | 32 | 任务签名、参数与结果反馈 |

合计 67 个应用源码入口；ol-order/payment/saps 还需验证 lease-mq 公共重投任务，不能把该公共源码只计 1 次就当作只有一个执行器需要验收。完整清单见 [智能柜源码与任务清单](智能柜源码与任务清单_20261030.md)。

ol-saps 的 lastPayUnOverdueSchedule、overdueGuaranteeSchedule、wxSingleBillOverdeuNotifySchedule 已处于块注释中，不计入 32 个有效源码入口。旧中心如仍保留对应任务记录，应单独核对处理，不随迁移自动恢复。

### 3.1 ol-order 的 10 个 IJobHandler 子类

LeaseApplyCompletedJob、LeaseApplyPolicyJob、LeaseApplyPurchaseOrderJob、LeaseApplySubmitAuditJob、LyDeviceInfoSyncJob、OrderReportingSchedule、Push2LeaseFinanceCompensateJob、ThirdPartyCallbackRetryJob、YouLiKaDealerInventorySync、YouLiKaDeviceInfoSync，保留继承，统一实现无参 void execute()。Push2LeaseFinanceCompensateJob 的 applicationCode 与设备同步任务的筛选参数继续由 helper 读取，不能全部当成未使用的 param。

特别检查 installImageFingerprintBackfillJob、installImageRecognitionBackfillJob、purchaseOrderAttachmentRegenerateJob、thirdPartyCallbackRetryJob、YouLiKaDeviceInfoSync 的失败反馈。ThirdPartyCallbackRetryJob 的逐条捕获继续处理与最外层失败返回是两种语义，迁移时分别保持，不能把部分失败策略随意扩大或吞掉全局失败。

LeaseApplyPolicyJob 在无数据时返回成功，完成非空列表循环后返回 null；后者在旧执行器中为失败，迁移按原分支反馈，若要修正应单列行为变更。pushPurchaseOrder2SupplierCompensateJob 会返回被委托方法的结果，wrapper 也必须承接其失败码。

### 3.2 ol-saps 的手工返回值

`ScheduleTestController.initDepositReportHistory()` 在约第 107 行接收 `DepositMonthReportSchedule.execute(param)` 的 ReturnT，随后检查 result.getCode() 并在失败时中止历史月份循环。**该有参方法不能直接改为 void。**

方案：保留无注解的有参业务方法与返回值；为 depositMonthReportJob 提供无参 void 调度入口，读取调度参数、调用原业务方法并把失败码/消息写入 helper。若业务方法抛异常，仍向外传播。手工接口继续使用显式参数及原返回码判断，不依赖调度上下文。

同时保留 customerMonthRentBalanceReportJob、customerBillReportSchedule 的失败反馈，以及账单/回购/代偿/技术服务费/设备流水/通知/订单补偿等手工调用。ol-payment 的对账、支付查询、日归集及归集监控也有 Controller 直接调用，均按同样的 wrapper 原则处理。main 源码中的 test 控制器不能误当作不会编译的测试文件。

ol-saps 另有 3 个 protected 任务入口，同样纳入改造；billSettledNoticeSchedule、consumeFlowSettleSchedule 带 RefreshScope，需验证配置刷新后的执行。

## 4 Nacos 配置与共享服务

- ol-saps、ol-payment 虽然模块目录及包名仍为 lease-*，服务身份应保持 ol-saps/ol-payment；Data ID 与 appname 依据实际配置保留，不能按源码目录改成叉车名字。
- ol-opgateway 有单独 bootstrap-dev/test/prod.yml 与 prefix=ol-opgateway，分别修改地址和鉴权；其他服务按多文档 bootstrap 的实际 profile 更新。
- 保留 `common.yaml`、`redis.yaml`、`rabbitmq.yaml`、group、namespace ID 和加载顺序；test 源码的本机地址不是可直接用于发布的新中心目标。
- ol-saps 的源码通过服务发现访问 lease-contract、lease-marketing、ol-marketing、ol-order 等；部分外部客户端通过 URL 属性访问 lease-saps/intfcommon/xxt-pay。逐项确认属性是否生效，不能把所有依赖都认定为注册发现，也不能认为切完 ol 组就已完成全部联调。
- ol-order 的 FinanceClient 使用 `${external.lease}/lease-finance`，须验证该网关/转发入口及后端；lease-finance 反向调用 ol-order/ol-saps，迁移时一并验收。
- ol-payment 还声明 ol-capital 客户端，但本次 WorkHub 的 ol 仓库清单未包含同名仓库；确认实际使用、URL/服务实例及责任系统，不凭名称新增或迁移服务。

叉车与智能柜源码中出现共用生产端点和 namespace，实际共享情况以部署配置为准。先确定同注册域的完整调用闭环，再确定分批切换或联合窗口。

## 5 回归与切换

重点回归：采购单推送/收货/物流/验收、设备和库存同步、订单完成/审核、优利卡接口及回调重试、融资推送；支付结果通知、对账与归集；租金/押金报表、账单及逾期费、回购/代偿/分润/服务费结算、设备流水、消息重投。

隔离测试必须覆盖有参手工返回值、失败后再次执行和空数据分支；环境测试验证真实配置加载、订阅和跨线链路。图片回填、附件重建和历史报表等一次性任务按旧中心快照和业务确认管理，不随迁移自动启动。

先完成两套公共件 → 9 个 Nacos 应用适配和 67 个入口处理 → 新中心/库演练 → 与叉车及共享支付/合同/短信联调 → 停旧触发并清在途 → 切应用和配置 → 按快照恢复。回退恢复对应应用旧包、旧 lease/thctay 构件与两个中心配置；已经产生的业务结果另行对账，不以平台回退代替业务补偿。

## 6 发布前补齐

两个公共构件版本及制品、共享服务调用方式、ol-capital 实际归属、源库版本及启停记录、新环境参数、测试数据、发布窗口与补跑负责人。当前 ol-saps 工作区有未提交改动，本稿读取的是 origin/master `3036e4e4`；实施前在确认的研发分支重新核对清单，不能覆盖既有工作。
