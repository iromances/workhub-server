# 叉车 Nacos 和 XXL-JOB 升级方案与改动点

版本 v1.0；日期 2026-10-30；状态：待实施方案。业务线 BL000008 / lease-saas。需求编码：REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba。

目标：Nacos 服务端及客户端 **2.5.4**，XXL-JOB 调度中心及官方 core **2.5.0**。共用版本、数据迁移、验收、切换和回退规则见 [总方案](design.md)；本文列明本业务线相对消费分期示例的具体改动。代码基线为刷新后的 `origin/master` 固定提交，生产运行状态与实际任务记录待发布前核对。

## 1 范围与升级路径

叉车以 `lease-public` 为主，不能仅接入消费分期已经改好的 `thctay-public`。18 个有 bootstrap 的后台仓库中，16 个直接继承 lease-dependencies；lease-intfgps 和 lease-workflow 分别走另外两条路径。

| 范围 | 现状 | 改动 |
| --- | --- | --- |
| lease-public | master `5b032cfe`，dependencies/schedule/MQ 均为旧 0.0.1 | 新增或准备专用升级分支，仅三个受影响构件拟升 0.0.2 |
| lease-authing/bpm/contract/crm/esb/finance/gateway/gps/intfcommon/intfrisk/marketing/marketing-back/opgateway/payment/risk/saps | 根 POM 直接继承 lease-dependencies 0.0.1 | 父改 0.0.2，清理局部覆盖；无应用任务的服务仍迁 Nacos；有传递 MQ 的还验证公共任务 |
| lease-intfgps | 继承 thctay-dependencies 0.0.1，使用 thctay 公共件 | 改用新版 thctay-dependencies 0.0.2；不误换为 lease-dependencies |
| lease-workflow | 独立 Boot 2.0.2 / Cloud Finchley.SR2 / Alibaba 2.1.0 | 在本工程 dependencyManagement 显式管理 nacos-client 2.5.4，保留 starter 方式；单独做旧栈兼容验证，不能写成 Boot 2.2.9 已验证 |
| lease-admin / lease-portal | 前端 | 无本次 Java 依赖改动，回归其网关接口 |
| lease-scaffold | 无可核对远程分支 | 归为范围缺口，不声称已检查源码或已排除生产部署 |

## 2 lease-public 的改动

| 文件或构件 | 具体改动 |
| --- | --- |
| lease-dependencies/pom.xml | 项目拟升 0.0.2；新增 nacos-client 2.5.4 管理；xxl-job-core 2.2.0 改 2.5.0；已有 lease-schedule.version、lease-mq.version 改 0.0.2；其他组件版本不变 |
| lease-schedule/pom.xml | 父改为 lease-dependencies 0.0.2，保留 core 无版本声明 |
| lease-mq/pom.xml | 父改 0.0.2，删除 lease-schedule 的显式 0.0.1，继续引用原版本的 common/cache |
| lease-schedule/BaseJobHandler.java | 当前三组主分支未发现其在用继承，拟删除旧接口基类；发布前复核其他 lease-public 使用方，有实际使用则先适配其契约 |
| lease-schedule/XxlJobConfig.java | 保留初始化及 accessToken 读取；默认日志拟由 30 改 90 天，与新版 thctay 统一；环境显式值及单实例装配另核对 |
| lease-mq/MessageSenderRetrySchedule.java | 保留 handler 名；改为无参 void，从 XxlJobHelper 取参数，继续 JSON 解析为 MessageSenderRetryScheduleDTO 并调用 retry(dto) |
| 根聚合工程及其他模块 | 保留原版本，不全量替换公共模块版本和父引用 |

**lease MQ 的参数必须保留。** 当前源码的行为为 `retry(JSONObject.parseObject(paramsStr, MessageSenderRetryScheduleDTO.class))`；与 thctay 的 `retry()` 不同。验证合法筛选参数、空参数和错误参数，避免迁移后扩大消息重投范围。

可复用新版 thctay-public 的依赖隔离和入口改造方法，不能把其类文件或包名直接替换进 lease 工程。新版 lease 构件被智能柜同时引用，两条业务线共同冻结版本，但各自的应用切换状态分开管理。

## 3 应用依赖与任务改动

| 文件 | 现有显式依赖 | 改动 |
| --- | --- | --- |
| [lease-crm/lease-crm-service/pom.xml](http://10.10.116.21:10000/lease-saas/lease-crm/-/blob/e197280806d2b61c236ed7f37e186cdee0c7ee6e/lease-crm-service/pom.xml) | `lease-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [lease-finance/lease-finance-service/pom.xml](http://10.10.116.21:10000/lease-saas/lease-finance/-/blob/e27e8ade3fc501b3b42a1b8b3b962b95485de98c/lease-finance-service/pom.xml) | `lease-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [lease-marketing/lease-marketing-schedule/pom.xml](http://10.10.116.21:10000/lease-saas/lease-marketing/-/blob/b37cacf9ce9a36cdb43b500ccfe6a3a54af0656e/lease-marketing-schedule/pom.xml) | `lease-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [lease-risk/lease-risk-schedule/pom.xml](http://10.10.116.21:10000/lease-saas/lease-risk/-/blob/d2252d011876611f2e0779f01e35c2cd38cfaa33/lease-risk-schedule/pom.xml) | `lease-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [lease-risk/lease-risk-schedule/pom.xml](http://10.10.116.21:10000/lease-saas/lease-risk/-/blob/d2252d011876611f2e0779f01e35c2cd38cfaa33/lease-risk-schedule/pom.xml) | `xxl-job-core:2.2.0` | 删除旧显式版本，由已升级父工程管理 |

lease-risk 虽未发现本地 @XxlJob 入口，risk-schedule 仍声明旧 core 和 schedule，必须清理版本覆盖。lease-contract、lease-intfcommon 等不通过 MQ 的引入路径也应验证最终包包含正确执行器配置，而非只检查源码有无 @XxlJob。

| 仓库 | 主分支提交 | 源码入口数 | 处理范围 |
| --- | --- | ---: | --- |
| [lease-contract](http://10.10.116.21:10000/lease-saas/lease-contract/-/blob/8b7ceaad06b3f4c7a7d72eb7d1805117c996def1/pom.xml) | `8b7ceaad` | 9 | 任务签名、参数与结果反馈 |
| [lease-crm](http://10.10.116.21:10000/lease-saas/lease-crm/-/blob/e197280806d2b61c236ed7f37e186cdee0c7ee6e/pom.xml) | `e1972808` | 1 | 任务签名、参数与结果反馈 |
| [lease-finance](http://10.10.116.21:10000/lease-saas/lease-finance/-/blob/e27e8ade3fc501b3b42a1b8b3b962b95485de98c/pom.xml) | `e27e8ade` | 13 | 任务签名、参数与结果反馈 |
| [lease-intfcommon](http://10.10.116.21:10000/lease-saas/lease-intfcommon/-/blob/e2e21f468567eaae2c6988fe84c417bb992401fc/pom.xml) | `e2e21f46` | 11 | 任务签名、参数与结果反馈 |
| [lease-marketing](http://10.10.116.21:10000/lease-saas/lease-marketing/-/blob/b37cacf9ce9a36cdb43b500ccfe6a3a54af0656e/pom.xml) | `b37cacf9` | 4 | 任务签名、参数与结果反馈 |
| [lease-payment](http://10.10.116.21:10000/lease-saas/lease-payment/-/blob/b77618ab722f02d5da8c2035417a191c1f1a7fa9/pom.xml) | `b77618ab` | 6 | 任务签名、参数与结果反馈 |
| [lease-saps](http://10.10.116.21:10000/lease-saas/lease-saps/-/blob/51add5ccfb04cd717a481d6079ef064b13d08c5d/pom.xml) | `51add5cc` | 27 | 任务签名、参数与结果反馈 |

合计 71 个应用源码入口，另有 lease-mq 1 个公共入口。原 handler 字符串、参数含义和任务依赖保留，完整路径见 [叉车源码与任务清单](叉车源码与任务清单_20261030.md)。

### 3.1 特殊入口与失败反馈

- lease-finance 的 AutoRetryConsumer、SupplementInfoOrderCallbackJob 两个 IJobHandler 子类实现无参 void execute，保留原重试、逐笔处理与异常边界。
- lease-contract 的 silentAuthExpireJob 有原失败返回，必须映射到调度结果。
- lease-intfcommon 的 syncChannelHealthStatsJob、sendChannelHealthReportJob、liangZiSmsReportQueryJob、smsBillingStatsJob，以及 lease-saps 的 customerBillReportSchedule、customerPayMonthReportSchedule，重点验证异常/失败反馈不能在换 JAR 后变成成功。
- lease-marketing 4 个、lease-saps 2 个 protected 入口也全部适配。
- lease-payment 的 RemitSchedule 三个入口以及 lease-saps 的 invoiceNoticeSchedule 带 RefreshScope，覆盖刷新后的执行、日志和回调。

### 3.2 手工调用

已定位 lease-finance 的 ApplyController 调用 RetrySubmitRiskControlOrAuditFlowJob.paymentResultHandle；lease-payment 的 ScheduleTestController/TestController 调用代付状态、通知、凭证、对账和时间轮入口；lease-saps 的控制器调用账单、催收、业务日、报表、发票和结算任务。目录名含 test 的 Controller 位于 src/main，也属于应用编译和接口兼容范围。

这些方法保留显式参数业务入口，XXL 注解只放在无参 wrapper；同时核对 src/test 的直接调用和断言。不能把所有手工调用一并改为读取 XxlJobHelper。

## 4 Nacos 与跨业务线依赖

各后台服务分别更新 config/discovery 的地址和 bootstrap 可用鉴权。保留 application.name、namespace、group、`common.yaml`、`redis.yaml`、`rabbitmq.yaml` 等各自已有共享配置及 profile 规则。

`lease-intfcommon` 同时有根 bootstrap 以及 assets/equip/loan 子目录配置，一套代码服务多条业务线；本次仅冻结叉车使用的打包与启动选择，其他部署继续用旧构件/配置或另排迁移，不能全目录替换所有地址。`lease-workflow` 使用旧 `ext-config`，保留其含义并验证配置加载，不借本次中间件升级改写业务流程引擎。

叉车与智能柜源码出现相同生产 Nacos 端点和 namespace，且 lease-finance 存在 ol-order/ol-saps 客户端。部分客户端有显式 URL 属性，空值时可能走发现；必须核对实际值，分别验证两种调用路径。智能柜还通过发现调用 lease-contract、lease-marketing 等共享服务，因此迁移窗口不能只按 GitLab 项目组划分。

执行器使用原 appname，新中心与执行器的非空 Token 一致；日志默认按本稿拟 90 天，显式远程值不能遗漏。没有源码任务的方法仓库仍需确认是否装配公共 MQ handler。

## 5 回归与切换

- 金融申请、审核/风控流程、担保签约、放款结果及回调、资方还款计划同步，覆盖 lease-finance 和流程/风控/合同依赖。
- 代付查询、通知、凭证、对账；账单扣款、逾期、租售月结和报表；重复触发不改变既有幂等与锁语义。
- 短信回执、渠道健康统计、余额查询、语音重试及公共消息重投；合法参数、失败、空数据与再次执行均有隔离及环境测试。
- 保留一次性回填/迁移任务原停用状态，不因源码存在就启用；租售数据修复等入口按中心快照恢复。

发布顺序为新公共件 → 应用依赖与入口 → 新中心/新库及配置演练 → 跨线联调 → 停旧触发并清在途 → 切应用和配置 → 按原快照恢复。回退按总方案成套恢复，尤其保留旧 lease 构件与 workflow 旧包。

## 6 发布前补齐

目标实例/库名/端口/鉴权和调度 Token、lease 新构件版本与发布分支、workflow 兼容结果、intfcommon 各部署实际打包选择、与智能柜共享注册域的边界、线上执行器及任务记录、维护窗口与补跑责任。当前方案未执行构建和环境验证。
