# 消费分期 Nacos 与 XXL-JOB 升级实施方案

版本：v3.5　修订日期：2026年10月22日

适用业务线：消费分期／asset-plateform

## 一 系统现状与升级目标

| 对象 | 当前版本 | 目标版本 | 执行要求 |
| --- | --- | --- | --- |
| Nacos 服务端 | **1.3.2**； | **2.5.4**； | 单节点，使用独立新库，新实例部署；保留旧实例及旧库供回退 |
| Nacos 客户端 | **nacos-client 1.2.1** | **nacos-client 2.5.4** | 14 个应用服务 |
| XXL-JOB 调度中心 | **admin 2.2.0**； | **admin 2.5.0**； | 单节点，保留旧中心及旧库；新旧中心不得同时调度同一任务 |
| XXL-JOB 执行器依赖 | **xxl-job-core 2.2.0**； | **xxl-job-core 2.5.0** | 清除旧版本覆盖，适配业务及公共任务入口；每个应用准备一个包含全部适配的目标包,risk-schedule 有局部版本覆盖 |
| MySQL | **8.0.28-231003** | **8.0.28-231003** | 不变 |
| JDK | **Oracle Java 1.8.0_361** | **Oracle Java 1.8.0_361** | 不变 |
| 业务 Spring Boot | **2.2.9.RELEASE** | **2.2.9.RELEASE** | XXL 新调度中心单独使用其发行版自带的 Boot 2.7.18 |
| 业务 Spring Cloud | **Hoxton.SR7** |  **Hoxton.SR7** | 不变 |
| 业务 Spring Cloud Alibaba | **2.2.1.RELEASE** | **2.2.1.RELEASE** | 不变 |



## 二 版本升级带来的主要变化

本次比较范围：Nacos 服务端 **1.3.2 → 2.5.4**、客户端 **1.2.1 → 2.5.4**；XXL-JOB 调度中心和执行器依赖 **2.2.0 → 2.5.0**。以下列出与消费分期实施直接相关的变化。

| 变化项 | 升级后的变化 | 消费分期需要处理的影响 |
| --- | --- | --- |
| Nacos 通信方式 | 2.x 客户端使用 gRPC 长连接进行配置及服务发现通信，新增客户端端口 9848，原 8848 仍需保留 | 放通两个端口，检查代理和端口映射；验证配置刷新、服务注册及断线重连。单节点部署方式不变 |
| Nacos 客户端兼容性 | 2.x 客户端不能连接 1.x 服务端 | 14 个后端升级依赖并连接新 Nacos；回退必须同时恢复旧应用包、旧配置和旧服务端，不能只改回旧地址 |
| Nacos 配置与数据库 | 跨大版本涉及存储结构和部署配置变化，不能将旧库原样交给新版本直接使用 | 本方案采用新实例、新库，按 2.5.4 建表，再迁移配置并核对 namespace、group、dataId；现有管理账号及权限按原状迁移。配置业务内容和服务名称保持。该方案属于新环境迁移，不是原实例直接覆盖升级 |
| XXL 任务入口 | 旧式带 String 参数、返回 ReturnT 的任务方式改为无参入口；用 XxlJobHelper 获取参数、记录日志和反馈结果 | 适配 86 个业务入口及 1 个公共 MQ 入口；直接继承旧 IJobHandler 的类需调整。仅换依赖可能导致参数变成 null、返回的失败结果不被识别；未被使用的 BaseJobHandler 按方案删除 |
| XXL 数据库结构 | 调度类型、调度配置、错过调度策略等字段，以及注册表唯一索引、日志索引发生变化 | 在新库执行结构和数据转换，保留任务 ID、Cron、参数、启停状态和子任务关系；原 job_cron 转入 schedule_conf，具体字段见步骤 4。不能只替换 admin 包 |
| XXL 调度稳定性 | 2.5.0 修复调度线程池拒绝、已关闭任务被启动等问题，优化注册、日志查询及清理，并支持配置通信超时 | 回归停止／恢复任务、注册、回调和网络异常；错过的任务按本业务补跑规则处理，升级不保证自动补齐漏跑任务 |
| Java 与 Spring 依赖 | 目标版本可使用 Java 8；新 XXL 调度中心采用 Boot 2.7.18，客户端同时带来 Netty、Gson、日志等依赖变化 | 本次保留业务 JDK 和 Spring 版本，不因调度中心升级而要求所有应用同步升级 Spring；仍须检查依赖冲突并通过回归，不能只看编译成功 |

**应用接入方式保持现状：**Nacos 不开启应用鉴权，业务应用不新增账号密码或密钥；XXL-JOB 中心与执行器继续使用空 Token，不增加 Token 必填校验。管理页面继续沿用现有登录账号及权限。

身份与权限类等保问题另行整改，不作为本次版本升级的完成项；传输保护、日志留存和集中审计仍按后续步骤执行。T38／T41 按原扫描项复测，升级版本本身不代表漏洞已销项。

技术依据见文末附录中的依赖、接口、数据库结构、版本记录及发行配置。XXL-JOB 2.5.0 发布说明中的“无缝升级”针对 2.4.x，不能直接套用于当前 2.2.0。Nacos 本次采用全新 2.5.4 环境导入旧数据，不采用原实例或旧库结构升级路线。

## 三 升级步骤

整体策略：**提前准备新中间件和应用包；保留旧环境、旧包和旧配置，失败时整体回退。Nacos 和 XXL 调度中心均维持单节点。**

### 步骤 1 准备新环境

1. 新建单节点 Nacos 2.5.4、单节点 XXL-JOB 2.5.0，分别使用独立新库。新调度中心在正式切换前不得触发生产执行器。
2. Nacos 按单机模式运行，连接独立新 MySQL 空库，仅用 2.5.4 自带 `mysql-schema.sql` 创建原生表结构；数据按步骤 4.1 导入，不恢复旧版表结构，不执行 `upgrade-2.5.4.sql` 或旧库 ALTER 升级脚本。放通应用到 Nacos 的 8848、9848 端口；9848 为新客户端使用的 gRPC 通道，有端口映射时保持主端口 +1000 的偏移。管理入口限制到指定网段。
3. XXL 新中心连接新库，验证自带 Connector/J 9.1.0 与现有 MySQL 的兼容性、时区和字符集。
4. 按下表落实本次安全整改，包括管理入口访问限制、传输加密和日志策略；这些是本方案增加的整改要求，不是升级版本后自动生效或全部强制开启的功能。

#### 1.1 安全与日志配置

| 项目 | 执行要求与证据 |
| --- | --- |
| 保持现有接入方式 | 新 Nacos 设置 `nacos.core.auth.enabled=false`；应用不新增账号密码或密钥。XXL 新中心与执行器均设置 `xxl.job.accessToken=`（空值），清空发行配置中的 `default_token`；管理页面登录方式保持现状 |
| 管理入口 | 保留现有管理登录方式和账号权限，管理入口限制到指定网段；本次不新增统一身份、MFA 或口令策略改造 |
| 传输 | 管理入口 HTTPS TLS 1.2+；SDK HTTP／gRPC、调度与执行器双向链路、数据库连接使用 TLS 或受控 IPsec。代理后的跨主机链路同样保护；提交源／目的、证书或隧道和验证记录，SM 算法口径由安全确定 |
| 日志留存 | 至少 6 个自然月。admin 的 xxl.job.logretentiondays 与执行器的 xxl.job.executor.logretentiondays 默认按 200 天规划，公共 XxlJobConfig 的 30 天默认值一并修改；先测算容量。改为短期本地加集中归档时，须证明覆盖 6 个自然月的检索和恢复 |
| 集中审计 | 采集执行日志、调度记录、登录／管理操作、Nacos 配置变更；任务日志不能代替管理审计。配置审计只读权限、删除审批、备份与恢复，脱敏凭据和个人信息并抽样验证检索／恢复 |

### 步骤 2 升级公共组件

1. 在 `thctay-public/thctay-dependencies` 的 dependencyManagement 中统一管理 `nacos-client:2.5.4`、`xxl-job-core:2.5.0`，发布新的固定版本，不覆盖旧 SNAPSHOT。
2. **删除 `thctay-schedule` 中的 `BaseJobHandler.java`**。已核查消费分期 14 个后端、public 和 bpm-framework，未发现其使用方；新版源码和包中均不保留该类。
3. 修改 `XxlJobConfig`：生产 appname、管理地址缺失时启动失败；保留现有空 Token 接入方式，不增加 Token 必填校验；日志默认留存从 30 天调整为本方案策略。修改 `MessageSenderRetrySchedule`，通过 XxlJobHelper 获取参数、记录日志和反馈结果。
4. 发布新版 schedule、mq，并更新 mq 对 schedule 的引用；同步更新 `bpm-framework` 的公共父依赖。检查 Netty、Gson 和日志依赖冲突，依赖检查要求见下文。

#### 2.1 依赖检查

检查 Maven 最终解析及 BOOT-INF/lib，避免旧 BOM 覆盖目标依赖。以 XXL 2.5.0 声明的 Netty 4.1.116.Final、Gson 2.11.0 为集成起点并扫描验证；检查 Groovy、SLF4J、Logback。保持日志框架兼容组合，不混用 SLF4J 2.x 与 Logback 1.2。两个 gateway 的 Gateway 2.2.3.RELEASE、risk 的旧日志版本覆盖，以及网关、Redis/Redisson 均纳入回归。

框架生命周期：保留业务 Java 8／旧 Spring 组合须通过本次兼容和安全验收；后续生命周期升级另行安排。XXL 调度中心采用 Boot 2.7.18 也不能替代依赖漏洞扫描。[5](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)

#### 2.2 公共消息重投任务

模块：thctay-mq；仓库提交：f0bfbdcea977

| 源码类 | handler 名称 |
| --- | --- |
| MessageSenderRetrySchedule | messageSenderRetrySchedule |

#### 2.3 公共组件核查基线

公共组件原核查引用：`thctay-public` 为 `f0bfbdcea977`，位置 `/Users/aslight/IDEAWorkspace/thctay-public`；`bpm-framework` 为 `43e5efa9fbde`，位置 `/Users/aslight/IDEAWorkspace/xxt-plateform/bpm-framework`。公共父链需由步骤 2 固定新发布版本。

**完成标准：公共组件在 Java 8 下干净构建成功，无 BaseJobHandler 残留；新组件版本固定，可供业务应用引用，旧包可用于回退。**

### 步骤 3 适配各业务应用

1. 14 个后端更新父工程及公共组件引用；风控后台经 `bpm-framework` 更新。删除 `risk-schedule` 单独指定的 XXL 2.2.0；更新资金、支付、运营报表、风控、wolease 单独指定的 schedule 版本。仓库和修改路径见下表。
2. 适配 86 个业务任务入口，handler 名称保持。改为 `public void` 无参入口，用 XxlJobHelper 获取参数和上报结果；原业务逻辑可保留。公共 MQ 另有 1 个入口，由步骤 2 处理。任务清单、特殊类处理和代码示例见下文。
3. 修改 Nacos 配置中心和注册中心的地址，保留 namespace ID、group、扩展名及刷新语义，不新增账号密码或密钥配置。含执行器的应用同时配置新 XXL 地址，Token 保持现有空值，保留 appname，核对注册、调度和回调双向可达。
4. 每个应用生成一个包含本次全部适配的目标包，固定 Git SHA、包／配置版本和校验和，保留旧包。业务发布范围按实际在用实例冻结，已停用系统不重新启动。

#### 3.1 应用范围、引用与修改路径

消费分期共 17 个仓库：14 个 Java 后端、2 个前端、1 个文档仓库。下表后端均纳入构建；实际生产发布实例按冻结清单，wolease 状态按 D1 落实。amp-admin、amp-unionpay-h5、amp-doc 不改 Java 依赖。

| 仓库 | Nacos 启动配置位置 | XXL 改动 |
| --- | --- | --- |
| amp-auth | auth-controller/src/main/resources/config/bootstrap.yml | 无本仓库入口 |
| amp-capital | capital-start/src/main/resources/bootstrap.yml | 26 |
| amp-gateway | src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-op-gateway | src/main/resources/bootstrap-*.yml<br>以生产实际激活的 profile 为准 | 无本仓库入口 |
| amp-intf-common | intfcommon-start/src/main/resources/bootstrap.yml | 公共 MQ 任务 |
| amp-intf-risk | intfrisk-start/src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-intf-risk-jt | intfrisk-start/src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-ops-reporting | amp-ops-reporting-start/src/main/resources/bootstrap.yml | 6 |
| amp-order | amp-order-start/src/main/resources/bootstrap.yml | 8 |
| amp-payment | payment-controller/src/main/resources/bootstrap.yml | 13 |
| amp-risk | risk-start/src/main/resources/bootstrap.yml | 3 |
| amp-risk-backend | src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-saps | saps-controller/src/main/resources/bootstrap.yml | 30 |
| amp-wolease | amp-wolease-start/src/main/resources/bootstrap.yml | 引用 schedule |

- 13 个后端直接继承 `thctay-dependencies:0.0.1-SNAPSHOT`；风控后台经 `bpm-framework:0.0.2-SNAPSHOT` 间接继承。公共父工程及应用父引用一起更新。
- Nacos 未发现应用自行指定版本；XXL core 仅 risk-schedule 单独指定 2.2.0，需删除。schedule 的显式版本位于 capital、payment、ops-reporting 根 POM、risk-schedule/pom.xml、amp-wolease-service/pom.xml；这 5 处及公共 mq 的引用同步更新，其他公共模块避免随 SNAPSHOT 漂移。
- 公共 MQ 在订单、支付、清结算、风控、公共接口中有直接依赖，运行时核实消息重投任务的实际注册范围。
- 订单核对 `api.contract`，支付核对 `agree.sign.contractSignUrl`，资金核对 `commonParam.contractSignUrl`；切换前后有效值应一致，资金 URL 不回落到 SIT 默认值。风控后台继续加载 `amp-risk-backend.yml`。
- WorkHub 登记的 intf-paychannel、thctay-contract、thctay-eas、xxt-pay、xxt-pay-gateway 为外部接口联调对象，不自动迁移其应用或中间件。两条保留配置的代码证据见步骤 4“两条配置的核查与处理”。

#### 3.2 应用连接配置

| 对象 | 必须落实的配置或核对 |
| --- | --- |
| Nacos 客户端 | config 与 discovery 分别修改 server-addr；不新增 username、password 或密钥；保留 namespace ID、group、file-extension、prefix、shared-configs／extension-configs、refresh |
| XXL 通信 | 中心与执行器的 xxl.job.accessToken 均保持空值；新中心发行配置中的 default_token 必须清空，避免与现有执行器不匹配；admin.addresses 只填写新中心，保留 appname，核对 executor.address／ip／port 和双向注册、调度、回调 |

#### 3.3 特殊任务类与适配示例

特殊任务类：

- order 的 ApplicationPreliminaryAuthSubmitJob、ApplicationRefactoringJob、PreliminaryRequestRecordJob、SyncApplyBankCardDataJob，及 ops-reporting 的 DeviceFinanceRecordFixedJob 共 5 类直接继承 IJobHandler，改成普通 Spring Bean 和无参任务入口，删除旧接口 @Override。
- order 的 AbstractXXLJob 及 3 个子类保留内部 process(String) 契约，将 @XxlJob 移到新入口；验证事务、代理及异常处理不因内部调用变化失效。
- 在用代码的 XxlJobLogger 改用 XxlJobHelper.log，保留异常堆栈及失败反馈。

修改 payment-schedule/src/main/java/com/amp/payment/schedule/AgreementSignFileSchedule.java。原 process(String) 去掉 @XxlJob，保留其业务逻辑；新增如下入口。XxlJobHelper 需要新增 import。

```java
@XxlJob("agreementSignFileSchedule")
public void runJob() throws Exception {
    String args = XxlJobHelper.getJobParam();
    ReturnT<String> result = process(args);
    if (result == null || result.getCode() != ReturnT.SUCCESS_CODE) {
        XxlJobHelper.handleFail(result == null
            ? "Job returned null" : result.getMsg());
        return;
    }
    XxlJobHelper.handleSuccess();
}
```

必须验证：传入测试申请单号时只处理该单；原有空参数批处理语义保持；错误 JSON 可被标记为失败。2.5.0 的反射调用为旧方法的各参数位置传入 null，并忽略返回值，仅改依赖可能造成参数丢失或失败被记为成功。[6](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)

#### 3.4 业务任务入口清单

以下为 origin/master 中有效的 86 个业务 @XxlJob 入口，按本步骤适配，handler 名称原样保留；公共 MQ 的 1 个入口见步骤 2。源码入口数不等于生产任务记录数。

##### amp-capital  共 26 个入口

模块：capital-schedule；仓库提交：742871aefc30

| 源码类 | handler 名称 |
| --- | --- |
| AssetTransferContractSyncSchedule | assetTransferContractSyncJob |
| FunderTransferQuerySchedule | funderTransferQueryJob |
| LoanAttachmentSignSchedule | loanAttachmentSignJob |
| LoanBalanceStatisticsSchedule | loanBalanceStatisticsJob |
| LoanInitPaymentAgainSchedule | loanInitPaymentAgainJob |
| LoanInitPaymentAgainSchedule | loanInProgressJob |
| LoanOrderNotifyPayResultSchedule | loanOrderNotifyPayResultJob |
| LoanOrderSignedAttachmentSyncSchedule | loanOrderSignedAttachmentSyncSchedule |
| LoanPaymentQueryStatusSchedule | loanPaymentQueryStatusJob |
| LoanPaymentQueryStatusSchedule | loanExceptionNoticeJob |
| LoanPaymentSchedule | loanPaymentJob |
| LoanRiskSchedule | loanRiskJob |
| LoanStatisticsSchedule | loanStatisticsJob |
| OfflinePaymentAggregateSchedule | offlinePaymentAggregateJob |
| OfflinePaymentAggregateSchedule | offlinePaymentPushJob |
| OfflinePaymentAggregateSchedule | offlinePaymentSuccessCountCheckJob |
| RefactoringAssetBillDetailSchedule | refactoringAssetBillDetailJob |
| RefactoringLoanSchedule | refactoringLoanJob |
| RefactoringLoanSchedule | refactoringLoanSyncJob |
| RefactoringLoanSchedule | queryRefactoringLoanStatusJob |
| RefactoringLoanSchedule | queryRefactoringLoanStatusExceptionJob |
| RefactoringLogisticsSchedule | refactoringLogisticsUpdateJob |
| RefactoringPushAssetTransferSchedule | refactoringPushAssetTransferJob |
| RepaymentPlanLockSchedule | repaymentPlanLockJob |
| RepaymentPlanPushSchedule | pushRepaymentPlanSchedule |
| SupplementContractSyncSchedule | supplementContractSyncJob |

##### amp-saps  共 30 个入口

模块：saps-schedule；仓库提交：47c88d6eb1fb

| 源码类 | handler 名称 |
| --- | --- |
| ActiveSettlementNotifyCompensateSchedule | activeSettlementResultNotifySchedule |
| AgreementPaySchedule | agreementPaySchedule |
| AgreementPayWatchSchedule | agreementPayWatchSchedule |
| CustomerOrderSchedule | queryOrderAndProcessSchedule |
| FunderBillSyncSchedule | funderBillSyncSchedule |
| OrderReconciliationSchedule | orderReconciliationSchedule |
| ProjectLoanAnalyseSchedule | projectLoanAnalyseSchedule |
| RepaymentPlanOverdueSchedule | repaymentPlanOverdueSchedule |
| BillAutoSettlementSchedule | billAutoSettlementSchedule |
| PaymentQuerySchedule | paymentQuerySchedule |
| AggPayStatementGenerateSchedule | aggPayStatementGenerateSchedule |
| BaoliServiceFeeGenerateSchedule | baoliServiceFeeGenerateSchedule |
| BuyBackStatementGenerateSchedule | buyBackStatementGenerateSchedule |
| ConsulationFeeStatementGenerateSchedule | consulationFeeStatementGenerateSchedule |
| DsdfPaySchedule | dsdfPaySchedule |
| DsdfRemitQuerySchedule | dsdfRemitQuerySchedule |
| FunderFeeGenerateSchedule | statementGenerateSchedule |
| FunderOverdueFeeStatementGenerateSchedule | funderOverdueFeeStatementGenerateSchedule |
| FunderStatementNoticeSchedule | funderStatementNoticeSchedule |
| FunderStatementSchedule | funderStatementSchedule |
| MandatoryBuyBackAnalyzeSchedule | mandatoryBuyBackAnalyzeSchedule |
| OfflineStatementGenerateSchedule | offlineStatementGenerateSchedule |
| PaymentFeeStatementGenerateSchedule | paymentFeeStatementGenerateSchedule |
| PlateformStatementGenerateSchedule | plateformStatementGenerateSchedule |
| RefundStatementGenerateSchedule | refundStatementGenerateSchedule |
| RefundStatementOverdueSchedule | refundStatementOverdueSchedule |
| RefundTicketSchedule | refundTicketSchedule |
| StatementNoticeSchedule | statementNoticeSchedule |
| StatementOverdueSchedule | statementOverdueSchedule |
| TradeInStatementGenerateSchedule | tradeInStatementGenerateSchedule |

##### amp-payment  共 13 个入口

模块：payment-schedule；仓库提交：0c83df979d2d

| 源码类 | handler 名称 |
| --- | --- |
| AgreementSignFileSchedule | agreementSignFileSchedule |
| ChannelWarningSchedule | channelWarningSchedule |
| DownloadDigitalBankSlipSchedule | downloadDigitalBankSlipSchedule |
| OrderBatchSchedule | batchStartAction |
| PayCheckSchedule | payCheckSchedule |
| PayCheckSchedule | payCheckMergeSchedule |
| PayCheckSchedule | checkSummaryNotifySchedule |
| PayCheckSchedule | payPushCheck |
| PayResultNotifyCompensateSchedule | channelQuerySchedule |
| PayResultNotifyCompensateSchedule | payResultNotifySchedule |
| PushStatement2SapsSchedule | pushStatement2SapsSchedule |
| RemitNotifySchedule | remitNotifySchedule |
| RemitStatusSchedule | remitStatusSchedule |

##### amp-order  共 8 个入口

模块：amp-order-service；仓库提交：f696b160af80

| 源码类 | handler 名称 |
| --- | --- |
| ApplicationAutoSubmitAuditJob | ApplicationAutoSubmitAuditJob |
| ApplicationPreliminaryAuthSubmitJob | ApplicationPreliminaryAuthSubmitJob |
| ApplicationRefactoringJob | ApplicationRefactoringJob |
| AssetHandlerApplicationQueryAuditResultJob | AssetHandlerApplicationQueryAuditResultJob |
| MonitorJob | MonitorJob |
| PreliminaryRequestRecordJob | PreliminaryRequestRecordJob |
| SupplementAssetHandlerRetryJob | supplementAssetHandlerRetryJob |
| SyncApplyBankCardDataJob | SyncApplyBankCardDataJob |

##### amp-ops-reporting  共 6 个入口

模块：amp-ops-reporting-service；仓库提交：a6a7c8d9b835

| 源码类 | handler 名称 |
| --- | --- |
| ConsumerFinanceRecordFixedJob | FinanceReportJob |
| CustomerSupportWeekReportJob | CustomerSupportReportJob |
| DeviceFinanceRecordFixedJob | DeviceFinanceRecordFixedJob |
| ETCReportJob | ETCReportJob |
| ETCReportWeekRefreshJob | etcReportWeekRefreshJob |
| LogisticsReportJob | LogisticsReportJob |

##### amp-risk  共 3 个入口

模块：risk-schedule；仓库提交：545bdaee6621

| 源码类 | handler 名称 |
| --- | --- |
| BatchBpmEngineJob | batchStrategy |
| BatchBpmEngineJob | singleInvokeEngine |
| BatchBpmEngineJob | batchEngineNotify |

#### 3.5 构建与依赖检查

在隔离测试配置下生成 effective-pom 和依赖树，再执行各模块干净构建；数据库、MQ 和外部资金渠道使用获准测试环境、桩或测试账户。检查所有目标 @XxlJob 入口为无参 void，无旧接口覆盖、旧 XxlJobLogger、旧依赖和 BaseJobHandler class 残留；将构建 SHA、包校验和、依赖树、扫描与测试报告关联到同一发布清单。

以下命令供执行人员使用，本文编制过程未执行升级构建：

```bash
mvn help:effective-pom -Doutput=target/effective-pom.xml
mvn dependency:tree -Dverbose -DoutputFile=target/dependency-tree.txt
mvn -pl capital-start -am clean verify
mvn -pl payment-controller -am clean verify
mvn -pl saps-controller -am clean verify
mvn -pl amp-order-start -am clean verify
mvn -pl amp-ops-reporting-start -am clean verify
mvn -pl risk-start -am clean verify
```

以上模块命令分别在各自仓库根目录执行；其余后端按本步骤的启动模块构建，单模块工程在根目录执行 clean verify。不得跳过本次适配新增的测试。

#### 3.6 代码核查基线

业务代码位于 `/Users/aslight/IDEAWorkspace/asset-plateform`。下表 17 个业务仓库于 2026年10月22日通过 WorkHub 刷新 origin/master；其 SHA 前缀与原 2026年10月19日核查表一致。依赖、任务入口和配置引用的核查范围不同，不将配置专项核查表述为升级代码已通过测试。实施时在发布基线中记录实际使用的完整 SHA。

| 仓库 | origin/master SHA（2026年10月22日） |
| --- | --- |
| amp-admin | `b69dc1e6544f8f8ba081ce25a0029f0bc7be8b11` |
| amp-auth | `72721bb795280e9abbd2f8849c0020efbf93ebab` |
| amp-capital | `742871aefc308d23d1ecc9aa757fa518632441d3` |
| amp-doc | `4a148a418301c4abf540ded85e9446461dc362e3` |
| amp-gateway | `218a0a5643b54755ee9a7df64c7c7025c592764a` |
| amp-intf-common | `ec1e4df4867b6a8c26f6fb2a6665cd03a6ef0779` |
| amp-intf-risk | `b54773cd9e25083a7ba80040a400c6b6fd6c4357` |
| amp-intf-risk-jt | `f1a87f39cb03107105bacbd6686805a3a15820ff` |
| amp-op-gateway | `cff6a6fd09d991d3d2e43715f07e14ecae881e17` |
| amp-ops-reporting | `a6a7c8d9b83527b45eb7c80dbbcfc3aa521a9e3f` |
| amp-order | `f696b160af8081f9a2090aca35ced64c2d5005af` |
| amp-payment | `0c83df979d2db10ba38ea382ca95a2d01ddf0ca2` |
| amp-risk | `545bdaee66216dbc3f024573d9b098316f669209` |
| amp-risk-backend | `74f1df26d41894ca2ec25cd2f8014b4f0e824e2f` |
| amp-saps | `47c88d6eb1fbde8eec379a04b6a11a67232fc134` |
| amp-unionpay-h5 | `34bb33b997ecaff13adad7413fa65deca3901361` |
| amp-wolease | `13c029e58e4d7b356b1e14fe9dea4260a8082f54` |
| xxt-plateform/thctay-contract（补查） | `3182bff5291ec7e1ef9a8f14f80fa46ba0c64bbc` |

**完成标准：14 个后端完成构建；目标包使用 Nacos 2.5.4，含 XXL 的应用使用 core 2.5.0；在用任务参数、失败反馈和异常处理正确。**

### 步骤 4 迁移演练与测试

1. **向全新 Nacos 导入旧数据。**按步骤 4.1 在新环境创建命名空间、导入配置并迁移必要的管理数据；prod 16 条和 public 1 条配置为当前核对基线，最终数量以冻结快照为准。`thctay-contract.yml`、`amp-risk-scene-backend.yml` 均保留，不新增对应发布系统；配置历史另行归档。
2. **迁移 XXL 数据。**从旧库备份恢复到独立新库，再执行已验证的增量迁移；保留任务 ID、分组、handler、Cron、参数、子任务及原启停状态。核对现有 8 个分组、83 条任务（68 启用、15 停止），迁移后先全部保持停止，清理旧注册地址。不要对已有数据的库直接执行全量初始化脚本。字段差异见下表。
3. **联调和复测。**验证配置冷启动／刷新、单节点重启重连、服务发现、网关、任务触发／回调／告警和资金幂等；重点回归订单、支付、资金的合同接口及风控后台。执行原漏洞和安全配置复测，完整用例见本步骤的回归清单。
4. **联合演练。**至少完成一次全部应用、中间件切换及整体回退，记录耗时。业务方落实漏跑补跑、防重规则及任务 13→8 的实际子任务关系；DBA 验证备份恢复。低频月度任务在测试环境模拟验证并安排上线后核查日期。

#### 4.1 Nacos 配置与服务迁移

观察日期：2026年10月22日。此清单用于演练和冻结快照的差异核对；不替代最终配置及数据库迁移包。

**迁移路线：全新部署 2.5.4 → 初始化新版空库 → 从 1.3.2 导出数据 → 导入新环境 → 核对及应用验证。旧实例和旧库保持原状供回退。**

1. **备份与导出。**完整备份旧库供回退和历史查询；从旧控制台分别导出 prod、public 的配置 ZIP，并记录 namespace ID、dataId、group、类型和内容校验值。旧库备份不作为整库恢复包灌入新库。
2. **创建命名空间。**在新控制台创建 prod，使用原 namespace ID；public 使用新环境自带的默认空间，不另建一个名为 public 的自定义空间。
3. **导入当前配置。**分别在对应空间导入旧 ZIP。先在演练环境验证跨版本导入结果；对导入格式或元数据不兼容的项，通过新控制台或目标版本配置 API 逐条发布并补齐，不通过修改新版表结构解决。保持 dataId、group、类型、内容和必要的描述／标签一致；地址等本次计划变更项单独登记。
4. **处理 ZIP 不包含的数据。**按下表迁移或归档，不把配置 ZIP 当作全量数据库备份。
5. **校验并切换。**逐条比对配置标识和内容，验证新应用冷启动读取、配置刷新和服务发现。正式窗口冻结旧配置后重新导出并同步最终差异；新环境中仅演练使用、已删除或过期的配置按冻结清单核对清理，避免导入后残留。

| 数据 | 处理方式 |
| --- | --- |
| 当前正式配置 | 导出／导入到新 Nacos，由新版本写入其原生表结构；核对数量及内容校验值 |
| 命名空间 | 在新环境创建并保留原 ID、名称和描述，不能只复制配置文件 |
| 管理账号与角色权限 | 单独迁移现有 users、roles、permissions 数据，按目标字段显式映射，保留密码哈希；先核对字段长度及冲突。仅准备数据导入 SQL，不携带 CREATE／ALTER／DROP，不新增应用鉴权 |
| 配置变更历史 | 完整保留在旧库备份及历史归档中，本次不导入新控制台；新控制台从导入后开始积累历史 |
| Beta／标签／聚合配置 | 导出前检查是否有数据及实际使用方；若存在，单独制定与新版能力匹配的数据转换和验证，不能默认被正式配置 ZIP 完整覆盖 |
| 服务和实例 | 临时实例由应用切换后重新注册；自定义服务元数据、持久化实例若存在，通过新版本控制台或 API 单独重建并核对，不从配置库复制在线注册状态 |

本路线不需要旧库表结构升级脚本。主要交付物为配置 ZIP、命名空间清单、必要的管理数据导入 SQL、特殊配置处理记录及迁移校验结果。

- 命名空间 `prod` 的 ID 为 `8941c382-6c67-4de8-a6fe-6ae6b8d9003b`，共 16 条配置，均为 `DEFAULT_GROUP`。迁移保留原 ID、dataId、group，不能仅创建同名空间。
- 16 个 dataId：`amp-intfcommon.yml`、`amp-order.yml`、`amp-auth`、`amp-gateway`、`amp-risk.yml`、`amp-intfrisk.yml`、`amp-capital.yml`、`amp-opgateway`、`thctay-contract.yml`、`amp-saps`、`amp-payment`、`amp-risk-scene-backend.yml`、`amp-risk-backend.yml`、`amp-ops-reporting`、`amp-wolease`、`amp-intf-risk-jt.yml`。有无 `.yml` 后缀均按原值保留。
- `public` 另有 1 条配置：dataId=`nacos.cfg.dataIdfoo`、group=`foo`。随本次配置完整保留迁移，不做清理。
- prod 服务列表在“隐藏空服务”开启时可见 13 个服务、29 个实例，健康实例数均等于实例数；每个服务的逻辑集群数为 1、保护阈值触发状态为 false。这里的服务实例数不代表 Nacos 服务端节点数。

| prod 服务名 | 实例数／健康实例数 |
| --- | --- |
| amp-risk-backend | 1／1 |
| amp-order | 4／4 |
| amp-capital | 4／4 |
| amp-intf-risk-jt | 1／1 |
| amp-opgateway | 1／1 |
| amp-intfcommon | 3／3 |
| amp-risk | 2／2 |
| amp-auth | 1／1 |
| amp-saps | 3／3 |
| amp-gateway | 4／4 |
| amp-payment | 3／3 |
| amp-ops-reporting | 1／1 |
| amp-intfrisk | 1／1 |
| 合计 | 29／29 |

#### 4.2 两条配置的核查与处理

范围：消费分期 17 个仓库及 WorkHub 登记的独立合同服务；分支为步骤 3“代码核查基线”记录的 origin/master。检索覆盖完整 dataId、应用名、prefix/name、shared/extension 配置、配置读取 API 和启动脚本。

Nacos 配置加载器会根据 prefix/name 或 spring.application.name 拼接扩展名及 profile，并单独加载共享和扩展项，不能只凭完整文件名字面量判断是否被使用。[17](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-starters/spring-cloud-starter-alibaba-nacos-config/src/main/java/com/alibaba/cloud/nacos/client/NacosPropertySourceLocator.java)

| 对象 | 核查证据 |
| --- | --- |
| thctay-contract.yml | 消费分期 14 个后端无明确引用；独立合同服务应用名为 thctay-contract，prod 配置指向 172.16.224.221:8848／bae98fd5-aa76-427b-a5fc-c32f81befa38，与消费分期 Nacos 及 namespace 不同 |
| amp-risk-scene-backend.yml | 当前代码无该名称引用；amp-risk-backend 的应用名为 amp-risk-backend，prod 使用消费分期 namespace／DEFAULT_GROUP／yml，未配置指向旧名称的 prefix 或共享导入 |
| amp-intf-common 字符串命中 | META-INF/app.properties 中 app.name=thctay-contract；bootstrap 的 spring.application.name 为 amp-intfcommon。未发现将前者转为该 Nacos dataId 的代码，不将字符串命中认定为读取关系 |
| amp-intf-risk-jt 共享配置 | 非 prod 段引用 common.yaml、redis.yaml，prod 段未引入本次两条配置 |
| 订单合同调用 | ContractFeignClient 使用 $contract$，拦截器读取 api.contract 替换 URL；进件预审与放款申请校验逻辑调用 validation |
| 支付合同调用 | 当前 ContractApi 使用 lease-contract 和 agree.sign.contractSignUrl，旧 thctay-contract 注解已注释；业务代码调用 createSignContract |
| 资金合同调用 | LeaseContractClient 使用 commonParam.contractSignUrl，缺省值是 SIT 地址；LoanAttachmentServiceImpl 调用 createSignContract。未读取生产有效 URL，不认定生产正在使用 SIT |

**代码证据定位**（行号对应本次核查的远程提交；提交列表见步骤 3“代码核查基线”）：

- [合同应用名与 prod 配置](</Users/aslight/IDEAWorkspace/xxt-plateform/thctay-contract/contract-start/src/main/resources/bootstrap.yml:3>)
- [合同 prod 地址与 namespace](</Users/aslight/IDEAWorkspace/xxt-plateform/thctay-contract/contract-start/src/main/resources/bootstrap.yml:69>)
- [风控后台应用名](</Users/aslight/IDEAWorkspace/asset-plateform/amp-risk-backend/src/main/resources/bootstrap.yml:3>)
- [风控后台 prod 配置](</Users/aslight/IDEAWorkspace/asset-plateform/amp-risk-backend/src/main/resources/bootstrap.yml:106>)
- [公共接口应用名](</Users/aslight/IDEAWorkspace/asset-plateform/amp-intf-common/intfcommon-start/src/main/resources/bootstrap.yml:3>)
- [公共接口的 app.name 命中](</Users/aslight/IDEAWorkspace/asset-plateform/amp-intf-common/intfcommon-start/src/main/resources/META-INF/app.properties:1>)
- [嘉泰风控接口 prod 配置](</Users/aslight/IDEAWorkspace/asset-plateform/amp-intf-risk-jt/intfrisk-start/src/main/resources/bootstrap.yml:119>)
- [订单合同 Client](</Users/aslight/IDEAWorkspace/asset-plateform/amp-order/amp-order-api/src/main/java/com/thchengtay/order/api/ContractFeignClient.java:17>)
- [订单 URL 替换逻辑](</Users/aslight/IDEAWorkspace/asset-plateform/amp-order/amp-order-start/src/main/java/com/thchengtay/order/interceptor/OkHttpLoggingInterceptor.java:43>)
- [订单预审调用](</Users/aslight/IDEAWorkspace/asset-plateform/amp-order/amp-order-service/src/main/java/com/thchengtay/order/service/application/impl/ApplicationPreliminaryServiceImpl.java:93>)
- [订单进件调用](</Users/aslight/IDEAWorkspace/asset-plateform/amp-order/amp-order-service/src/main/java/com/thchengtay/order/service/application/impl/ApplicationLoanServiceImpl.java:539>)
- [支付合同 Client](</Users/aslight/IDEAWorkspace/asset-plateform/amp-payment/payment-api/src/main/java/com/amp/payment/api/service/ContractApi.java:15>)
- [支付合同流程调用](</Users/aslight/IDEAWorkspace/asset-plateform/amp-payment/payment-business/src/main/java/com/amp/payment/business/service/impl/AgreementPayThctServiceImpl.java:359>)
- [资金合同 Client](</Users/aslight/IDEAWorkspace/asset-plateform/amp-capital/capital-api/src/main/java/com/amp/capital/api/LeaseContractClient.java:22>)
- [资金合同流程调用](</Users/aslight/IDEAWorkspace/asset-plateform/amp-capital/capital-service/src/main/java/com/amp/capital/service/loan/impl/LoanAttachmentServiceImpl.java:1061>)


核查结论限于静态代码，未核验运行命令行、环境变量覆盖及在线监听记录。本文已选择保留迁移；若另行清理配置，再补运行证据。

#### 4.3 XXL 执行器、任务与数据库迁移

| 对象 | 必须落实的配置或核对 |
| --- | --- |
| XXL 迁移状态 | 保留原启停快照，新库先停全部任务；清理旧自动注册地址，手动地址按目标实例登记；上线后仅恢复原应启用任务 |

全部执行器分组为自动注册。在线地址数与 Nacos 实例数用途不同，订单 4／2、风控 2／1、清结算 3／1 的差异需按部署配置核对，不按 Nacos 实例数直接补建执行器。

| 执行器 AppName | 在线地址数 | 任务记录数 | RUNNING | STOP | STOP 任务 ID |
| --- | --- | --- | --- | --- | --- |
| amp-capital | 4 | 25 | 22 | 3 | 81、47、16 |
| amp-ops-reporting | 1 | 6 | 6 | 0 | — |
| amp-order | 2 | 8 | 7 | 1 | 66 |
| amp-payment | 3 | 17 | 14 | 3 | 54、18、17 |
| amp-risk | 1 | 2 | 1 | 1 | 86 |
| amp-saps | 1 | 24 | 18 | 6 | 49、42、41、40、25、8 |
| amp-wolease | 0 | 0 | 0 | 0 | — |
| xxl-job-executor-sample | 0 | 1 | 0 | 1 | 1 |
| 合计 | 13 | 83 | 68 | 15 | 15 条 |

迁移核对重点：

1. `payCheckSchedule` 对应多个生产任务 ID／Cron，逐条保留参数，不按 handler 去重。
2. 清结算任务 13 描述为“还款计划逾期天数计算（子任务8）”，任务 8 描述为“回购对账单生成（任务13触发）”且为 STOP。DBA 核实 child_jobid，业务方确定恢复规则；不删除任务 8，不擅自改为定时启用。
3. 任务包括凌晨日切、每月 1 日账单、09:31—09:37 对账、10:15／14:15／20:15 代扣批次。业务方据完整任务链确定维护窗口和补跑顺序。
4. 目前已有列表观察值，参数、路由、阻塞、重试、超时及子任务字段须由完整数据库迁移快照补齐。原 STOP 记录（包括示例任务）保留原状态。

DBA 对实际 SHOW CREATE TABLE 与目标结构逐项比对，包含生产定制差异；在备份恢复出的新库执行增量脚本并校验，不能以官方全量脚本覆盖数据。[10](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)[11](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql)

| 表 | 必须落实的迁移 |
| --- | --- |
| xxl_job_info | 增加 schedule_type、schedule_conf、misfire_strategy；原定时任务将 job_cron 复制到 schedule_conf 并设置 CRON；原 job_cron 核对后归档，并删除或改为可空，避免新建任务插入失败；停机期间补跑按清单执行。 |
| xxl_job_info | glue_remark 在 2.2.0、2.5.0 均允许 NULL，无需因升级清洗为空字符串；保存并核对任务参数、超时、失败重试、阻塞策略和子任务关系。 |
| xxl_job_group | address_list 调整为 text；增加 update_time；区分自动和手动注册清理旧地址。 |
| xxl_job_registry | 检查重复键后按目标结构建立 registry_group/key/value 唯一索引；新库注册地址重新生成。 |
| xxl_job_log_report 与 log | 补充 report.update_time 和目标版本日志索引；按数据量评估索引工期，不在生产临时尝试。 |

#### 4.4 业务与安全回归清单

| 测试项 | 执行方式和通过标准 |
| --- | --- |
| Nacos 冷启动和配置 | 清测试缓存后启动；配置值正确；修改测试配置可刷新；沿用现有无账号密码接入方式，注册与服务发现正常 |
| 合同接口联调 | 订单预审／进件要素核验、支付协议签约、资金合同流程创建通过；三个合同 URL 切换前后一致且生产不回落到 SIT。主动调用在获准测试环境完成，生产按既定冒烟与业务观察验证 |
| 风控后台配置 | amp-risk-backend 按现有 namespace/group 加载 amp-risk-backend.yml，刷新及后台功能正常；不把保留的旧配置当成新增发布系统 |
| 单节点重启和网络恢复 | 在测试环境验证 Nacos 单节点重启、gRPC 连接中断后的重连及注册／配置恢复；记录中断期间行为和恢复耗时，恢复后 Feign、网关调用成功，无持续注册失败 |
| 任务参数 | 每个在用 handler 覆盖空参、有效参数、非法参数；指定单号只影响该单 |
| 失败反馈 | 人为制造业务失败返回和异常；中心必须记录失败并触发既定告警 |
| 支付和账务幂等 | 同单重复触发、失败重试、回调延迟或重复；无重复扣款、放款、账单和通知 |
| 调度策略 | Cron、路由、分片、阻塞、超时、子任务按生产清单逐项核对；未使用能力标不适用 |
| Spring 与三方依赖 | 两个网关、风控、支付、清结算均启动并执行业务；无 LinkageError、日志绑定错误 |
| 安全和日志 | 按 T38／T41 原漏洞与接口进行复测；日志采集、保存、恢复有效；身份与权限整改不计入本次升级验收 |

| 原问题 | 对应工作 | 销项依据 |
| --- | --- | --- |
| T38、T41 | 步骤 1—4 的升级与漏洞整改 | 同一漏洞检查项复测通过，版本号不能替代复测 |
| T11、T14、T18、T21、T25 | 身份与权限问题另行整改，本次保持现有接入及管理方式 | 不作为本次升级完成项，不以升级版本销项 |
| T16、T50、T56 | 传输保护 | 浏览器、SDK／RPC、数据库等涉及链路的加密证据 |
| T34、T35 | 集中审计和日志留存 | 保存周期、检索及恢复证据 |
| T47、T52、T58 等 | 独立整改任务 | 可信验证、存储完整性和加密不因本次升级自动完成 |

**完成标准：第四节事项已落实；发布包、配置／数据库迁移包、任务恢复清单和测试／复测报告齐备；切换与回退演练能满足约 2 小时窗口。**

### 步骤 5 维护窗口内一次性切换

进入窗口前，由发布负责人汇总研发、运维、DBA、测试、安全和业务的放行确认。设窗口结束时间为 T_end，实测回退及恢复验证耗时为 R，缓冲时间为 B，**最晚回退决策时点为 T_end − R − B**；演练超预算则先调整方案或窗口。

1. 冻结配置和任务定义，暂停业务入口、生产任务及相关 MQ 消费，等待在途请求和任务结束。
2. 导出并向新 Nacos 导入最终配置，逐项核对数据；备份 XXL 数据库，完成 XXL 新库的最终同步、结构迁移和校验；新旧调度中心均保持相关任务停用。
3. 按冻结清单统一发布在用应用：全部指向新 Nacos，含执行器的应用同时指向新 XXL。Nacos 2.x 客户端不能接回旧 1.x 服务端；已确认停用的系统保持停用。

**完成标准：目标包与配置全部切换完成，注册、配置和执行器就绪；保持业务入口、MQ 和任务暂停，进入步骤 6 联合验收。**

### 步骤 6 验收并恢复业务，失败时整体回退

1. 按既定生产冒烟清单验证配置、服务注册、关键业务及执行器触发、回调和日志；核对合同 URL 与风控后台配置来源。不为验证临时发起真实签约或支付。
2. 验收通过后，发布负责人统一确认恢复流量、MQ 和原应启用的任务。原 STOP 状态保持，旧中心任务持续停用；漏跑任务按规则补跑，资金任务先核对账务。
3. 关键验收失败或到达最晚回退时点仍未通过，执行整体回退：停止新侧流量、消费和调度，等待在途处理结束并记录已执行结果；恢复全部旧应用包和旧配置，接回旧 Nacos、旧 XXL 中心及旧库；验证后恢复业务，已执行任务不得自动重放。**不能只把新版应用的连接地址改回旧中间件。**
4. 恢复后连续观察至少 24 小时，并覆盖下一次日切和对账；月度任务按登记日期继续核查。旧环境与旧包保留到验收及回退期结束。回退不撤销已发生的支付、账单或通知，业务数据按核对和补偿流程处理。

**完成标准：业务负责人确认业务恢复，配置／注册正常，关键任务及账务核对通过，无重复调度或处理。**

## 四 实施前待落实事项

| 编号 | 待落实事项 | 责任方 | 截止条件／缺失时处理 |
| --- | --- | --- | --- |
| D1 | amp-wolease 是否仍需在生产运行 | 业务负责人、应用研发 | 联合演练前确认并冻结发布实例范围 |
| D2 | 新 Nacos 单节点实例及新库、新 XXL 单节点中心及新库的资源与部署位置 | 运维、DBA、发布负责人 | 单节点方式已确定；环境搭建前落实主机、端口和数据库部署位置，确保新旧环境隔离并保留回退条件 |
| D3 | T38/T41 漏洞名称／编号、对象、端口及复测标准；传输保护的密码算法验收口径 | 安全 | 开发整改前提供；切换前所有适用项有复测结论 |
| D4 | 上线日期、最终维护时段、回退决策人和任务补跑规则 | 发布负责人、业务负责人 | 排期时确定；约 2 小时窗口需容纳实测切换或回退，规则未落实不切换 |

D1—D4 均有明确结论并完成步骤 1—4 后，才进入生产切换。各部门指定执行负责人，由发布负责人汇总产物位置、完成日期和验收结果。

其他业务线复用时，沿用上述六步，重新核实本业务线的版本、依赖、系统实例、配置和任务；不直接套用消费分期数量。

## 修订记录

| 版本 | 日期 | 变更内容 | 历史文件 |
| --- | --- | --- | --- |
| v3.5 | 2026-10-22 | 明确 Nacos 全新部署 2.5.4，使用新版原生表结构导入旧数据；不执行旧库结构升级，补充各类数据迁移与校验方式。 | 当前文档 |
| v3.4 | 2026-10-22 | 按业务方要求取消新增鉴权改造，Nacos 与 XXL 保持现有接入方式；同步调整部署、应用配置、启动校验与验收，身份权限问题另行整改。 | [v3.4 Markdown](历史版本/v3.4-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v3.3 | 2026-10-22 | 在升级步骤前增加版本变化说明，明确通信、任务接口、数据库、依赖和回退影响，区分版本要求与安全整改。 | [v3.3 Markdown](历史版本/v3.3-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v3.2 | 2026-10-22 | 各步骤的官方技术依据链接集中移至文末附录，实施说明、任务清单和代码核查内容保留原步骤。 | [v3.2 Markdown](历史版本/v3.2-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v3.1 | 2026-10-22 | 将原附录的配置、任务、代码、迁移、验收及来源依据归入对应实施步骤，取消独立附录；保留六步主线与原执行要求。 | [v3.1 Markdown](历史版本/v3.1-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v3.0 | 2026-10-22 | 按六个实施步骤重排并精简正文；保留版本对照、责任与验收、待落实事项、切换回退和完整技术清单；BaseJobHandler 继续按删除方案执行。 | [v3.0 Markdown](历史版本/v3.0-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.9 | 2026-10-22 | 根据源码引用核查及业务方决定，将 BaseJobHandler 处理方案改为删除，同步修改代码现状、改造项 4 和构建验收要求。 | [v2.9 Markdown](历史版本/v2.9-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.8 | 2026-10-22 | 将已确认系统现状合并到首节目标表，按组件并列展示当前版本、目标版本及执行要求；拆分 Nacos 和 XXL 的服务端、客户端版本。 | [v2.8 Markdown](历史版本/v2.8-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.7 | 2026-10-22 | 按业务方要求，Nacos 与 XXL 调度中心均维持单节点；同步调整资源项、部署步骤、端口范围及重启恢复验收，保留一次性切换和整体回退。 | [v2.7 Markdown](历史版本/v2.7-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.6 | 2026-10-22 | 整理为部门执行版：集中已确认现状、待落实决策与交付物，明确部门职责及上线条件；合并重复叙述，保留 14 项改造和完整证据。 | [v2.6 Markdown](历史版本/v2.6-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.5 | 2026-10-22 | 完成两条配置的代码引用核查及业务调用影响分析。 | [v2.5 Markdown](历史版本/v2.5-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.4 | 2026-10-22 | 确认 MySQL 8.0.28-231003；按约 2 小时规划维护窗口；说明合同及风控场景配置对迁移范围和验证的影响。 | [v2.4 Markdown](历史版本/v2.4-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.3 | 2026-10-22 | 补充用户确认的生产版本、JDK、单节点 MySQL 和独占范围；增加 Safari 核查的配置、服务、执行器及任务数量，明确剩余信息与实施产物。 | [v2.3 Markdown](历史版本/v2.3-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.2 | 2026-10-20 | 改为 Markdown 主文档；新增版本切换变化、任务参数和失败处理影响、升级收益及实施影响，保留一次性切换安排。 | [v2.2 Markdown](历史版本/v2.2-20261022修订前/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.1 | 2026-10-19 | 调整为同一维护窗口统一发布、联合验收和整体回退。 | [v2.1 Word](历史版本/v2.1-20261019/消费分期Nacos和XXLJOB升级实施方案_20261019.docx) |
| v2.0 | 2026-10-19 | 整理消费分期系统现状、14 项改造、任务清单及技术依据。 | [v2.0 Word](历史版本/v2.0-20261019/消费分期Nacos和XXLJOB升级实施方案_20261019.docx) |

## 附录 技术依据

以下为实施方案引用的官方文档、版本记录及源码，保留原编号便于与正文对应。

- [1. Spring Cloud Alibaba 2.2.1 BOM](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-dependencies/pom.xml)
- [2. Spring Boot 2.2.9 BOM](https://raw.githubusercontent.com/spring-projects/spring-boot/v2.2.9.RELEASE/spring-boot-project/spring-boot-dependencies/pom.xml)
- [3. XXL-JOB 2.5.0 版本依赖](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/pom.xml)
- [4. xxl-job-core 2.5.0 依赖与 provided Spring](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/pom.xml)
- [5. Spring Boot 2.7 社区维护结束说明](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)
- [6. XXL-JOB 2.5.0 MethodJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)
- [7. Nacos 2.5 升级手册](https://nacos.io/docs/v2.5/manual/admin/upgrading/)
- [8. Nacos 部署与端口说明](https://nacos.io/docs/v2.5/manual/admin/deployment/deployment-overview/)
- [10. XXL-JOB 2.2.0 数据库结构](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)
- [11. XXL-JOB 2.5.0 数据库结构](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql)
- [12. XXL-JOB 2.5.0 管理中心配置](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-admin/src/main/resources/application.properties)
- [13. Nacos 2.5.4 发布记录](https://github.com/alibaba/nacos/releases/tag/2.5.4)
- [14. XXL-JOB 2.5.0 发布记录](https://github.com/xuxueli/xxl-job/releases/tag/2.5.0)
- [15. Nacos 2.x 客户端通信与兼容性说明](https://nacos.io/docs/v2.5/upgrading/200-compatibility/)
- [16. XXL-JOB 2.2.0 MethodJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)
- [17. Spring Cloud Alibaba 2.2.1 Nacos 配置加载器](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-starters/spring-cloud-starter-alibaba-nacos-config/src/main/java/com/alibaba/cloud/nacos/client/NacosPropertySourceLocator.java)：核对应用名、prefix/name、共享配置、扩展配置及 profile 的加载规则。
- [18. Nacos 2.5.4 发行配置](https://raw.githubusercontent.com/alibaba/nacos/2.5.4/distribution/conf/application.properties)：核对关闭鉴权的配置。
- [19. XXL-JOB 2.5.0 执行器启动代码](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/executor/XxlJobExecutor.java)：空 Token 告警与启动行为。
