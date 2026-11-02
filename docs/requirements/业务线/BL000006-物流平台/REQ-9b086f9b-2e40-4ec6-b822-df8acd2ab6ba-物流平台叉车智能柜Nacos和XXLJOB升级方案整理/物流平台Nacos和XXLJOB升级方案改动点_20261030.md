# 物流平台 Nacos 和 XXL-JOB 升级方案与改动点

版本 v1.0；日期 2026-10-30；状态：待实施方案。业务线 BL000006 / xxt-plateform。需求编码：REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba。

目标：Nacos 服务端及客户端 **2.5.4**，XXL-JOB 调度中心及官方 core **2.5.0**。共用版本、数据迁移、验收、切换和回退规则见 [总方案](design.md)；本文列明本业务线相对消费分期示例的具体改动。代码基线为刷新后的 `origin/master` 固定提交，生产运行状态与实际任务记录待发布前核对。

## 1 范围与升级路径

物流平台主要继承 `thctay-dependencies:0.0.1-SNAPSHOT`。通常只需切换到 0.0.2-SNAPSHOT，再处理旧版本覆盖、任务入口及配置；下列例外必须单独安排。

| 仓库或范围 | 现状 | 具体改动 |
| --- | --- | --- |
| thctay-public | master `3bb5e766`；升级分支已有新版源码 | 复用远程升级提交 `d44d555c` 中 dependencies/schedule/MQ 0.0.2；验证官方 core 2.5.0 与 client 2.5.4；不重装未升号模块 |
| 直接继承 thctay-dependencies 的应用 | 旧父版本 0.0.1 | 根 POM 切至 0.0.2；各子模块保留正确的应用父引用；按下表去掉局部旧覆盖；无业务任务的应用也须处理 Nacos，引用 MQ 的还须回归公共任务 |
| bpm-framework | 本仓库根 0.0.2，父 thctay-dependencies 0.0.1 | 父升 0.0.2，框架拟发布 0.0.3；7 个子模块的父版本及内部引用同步核对 |
| intf-sence-back | 父 bpm-framework 0.0.2 | 改用升级框架 0.0.3，确认传递 MQ 为 0.0.2 |
| thctay-risk-backend | 父 bpm-framework 0.0.1 | 不能当作当前已用 0.0.2；迁至新框架时补做 0.0.1 到新基线的兼容检查及流程回归 |
| thctay-saps → saps-account-checker | checker 以 thctay-saps 0.0.1 为父，不能直接随 public 自动解析到新父 | 建议 thctay-saps 父 POM 及继承版本的内部模块升 0.0.2，checker 指向新父；保留旧 0.0.1 制品，核对跨仓库解析。新坐标实施前检查占用 |
| saps-account-checker 的 XxlJobConfig | 本仓库自带配置类，默认日志 30 天 | 与新公共配置统一非空 Token、双向地址及拟 90 天默认；检查是否与传递 schedule 配置重复装配，最终只初始化正确执行器 |
| thctay-saps-core-common | 独立公共模型组件，无本次任务入口 | 不因名称含 saps 就随应用批量升版；已有引用及版本保留 |
| thctay-contract / thctay-eas | 同属该组且被 WorkHub 标为全局共享服务 | 单列 5 / 16 个任务入口；共享实例同批迁移须覆盖其他业务线，独立实例则分别冻结。不默认排除，也不默认改所有使用方 |
| xxl-job-admin | 2.2.0 parent，显式引入 Nacos starter 2.2.1 | 独立迁至官方 2.5.0 中心，承接已有 Nacos/数据库/告警及必要定制，检查配置引导顺序；不按普通业务应用只换 public |

`intf-paychannel`、`xxt-pay`、`xxt-pay-gateway` 归属其他项目组，本次按外部接口依赖联调。是否需要其注册信息随中心迁移，由实际调用方式确定。

`thctay-scaffold` 是模板工程，本轮将生成模板里的旧父 POM/依赖作为后续维护项，不视作线上服务。`xxt-front-scafold` 为前端；`thctay-guangdong-tools` 主分支未发现 Nacos/XXL 引用，不批量修改。`etc-doc` 仅有 main；`etc-tools` 默认 ql-etc-tools 未发现本次直接引用；`mock-server` 默认 mock_v1 引用 Nacos 且直接访问 `/nacos/v1/ns`，如参与联调须核对鉴权、服务注册和心跳兼容，不能只改客户端版本。后三者不混入 master 的入口计数，实际发布分支待确认。

## 2 公共件与 POM 改动

thctay-public 已有升级分支保留聚合工程原版本，三个受影响模块升 0.0.2；删除其旧 BaseJobHandler、MQ 改为无参 process、仍调用原无参 retry。当前三组目标主分支未发现对公共 BaseJobHandler 的在用继承，发布给其他消费方前仍需确认更广范围。

日志留存以本次远程升级代码的 **90 天** 作为拟统一值；示例改动清单的 60 天不作为当前源码事实。

| 文件 | 现有显式依赖 | 改动 |
| --- | --- | --- |
| [intf-bank/intfbank-service/pom.xml](http://10.10.116.21:10000/xxt-plateform/intf-bank/-/blob/6b5a422edc9790655663693e2c801389828e0717/intfbank-service/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [intf-hs/common/intf-common/pom.xml](http://10.10.116.21:10000/xxt-plateform/intf-hs/-/blob/df6684c3efb1134f7d084c4cea8f84861d611059/common/intf-common/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [invoice-exinterface/exinterface-service/pom.xml](http://10.10.116.21:10000/xxt-plateform/invoice-exinterface/-/blob/2b283bdf6e7d830691298e0b29e6d34fcbad4136/exinterface-service/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [thctay-eas/pom.xml](http://10.10.116.21:10000/xxt-plateform/thctay-eas/-/blob/7b4b1567b182a6b0997490a449c3c60c8123b4dd/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [thctay-etc-issue/pom.xml](http://10.10.116.21:10000/xxt-plateform/thctay-etc-issue/-/blob/8761b83781fe22450d6ba1e6c83d189c1080f635/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [thctay-payment/pom.xml](http://10.10.116.21:10000/xxt-plateform/thctay-payment/-/blob/ee34164c6651ec2e0d9832c4d4e388647e6d549b/pom.xml) | `thctay-mq:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [thctay-payment/pom.xml](http://10.10.116.21:10000/xxt-plateform/thctay-payment/-/blob/ee34164c6651ec2e0d9832c4d4e388647e6d549b/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |
| [thctay-wms/pom.xml](http://10.10.116.21:10000/xxt-plateform/thctay-wms/-/blob/bde451827a511bda1d0e9e2a3382d06635e7b2c7/pom.xml) | `thctay-schedule:0.0.1-SNAPSHOT` | 删除旧显式版本，由已升级父工程管理 |

保留已有无版本的 core/MQ 依赖。`intf-hs` 的多个 domain 子项目和公共模块分别检查，不能只构建根目录认定全部省份应用完成。公共 MQ 影响的直接声明仓库包括 intf-common、intf-hs 的 gzgs/jsgs/sanchuan 服务、intf-sence-back、thctay-account/cashloan/cms/contract/crm/etc/marketing/payment/saps/user，框架及其派生服务还可能传递引入。是否注册公共任务须结合最终包、组件扫描和执行器清单验证。

## 3 任务改造规模

| 仓库 | 主分支提交 | 源码入口数 | 处理范围 |
| --- | --- | ---: | --- |
| [intf-bank](http://10.10.116.21:10000/xxt-plateform/intf-bank/-/blob/6b5a422edc9790655663693e2c801389828e0717/pom.xml) | `6b5a422e` | 1 | 任务签名、参数与结果反馈 |
| [intf-hs](http://10.10.116.21:10000/xxt-plateform/intf-hs/-/blob/df6684c3efb1134f7d084c4cea8f84861d611059/pom.xml) | `df6684c3` | 32 | 任务签名、参数与结果反馈 |
| [invoice-exinterface](http://10.10.116.21:10000/xxt-plateform/invoice-exinterface/-/blob/2b283bdf6e7d830691298e0b29e6d34fcbad4136/pom.xml) | `2b283bdf` | 3 | 任务签名、参数与结果反馈 |
| [saps-account-checker](http://10.10.116.21:10000/xxt-plateform/saps-account-checker/-/blob/5f976e9a937f52d5727898a33eda8741230d60af/pom.xml) | `5f976e9a` | 4 | 任务签名、参数与结果反馈 |
| [thctay-account](http://10.10.116.21:10000/xxt-plateform/thctay-account/-/blob/54d001b2527df6058de8485de927651a1b39edb7/pom.xml) | `54d001b2` | 7 | 任务签名、参数与结果反馈 |
| [thctay-cashloan](http://10.10.116.21:10000/xxt-plateform/thctay-cashloan/-/blob/b23696139d350f4e9302fe48c5928283315570be/pom.xml) | `b2369613` | 7 | 任务签名、参数与结果反馈 |
| [thctay-contract](http://10.10.116.21:10000/xxt-plateform/thctay-contract/-/blob/3182bff5291ec7e1ef9a8f14f80fa46ba0c64bbc/pom.xml) | `3182bff5` | 5 | 共享服务候选；冻结部署边界后决定是否同批迁移 |
| [thctay-crm](http://10.10.116.21:10000/xxt-plateform/thctay-crm/-/blob/89e9678c449babc67d595dd90153d297d7c12060/pom.xml) | `89e9678c` | 29 | 任务签名、参数与结果反馈 |
| [thctay-eas](http://10.10.116.21:10000/xxt-plateform/thctay-eas/-/blob/7b4b1567b182a6b0997490a449c3c60c8123b4dd/pom.xml) | `7b4b1567` | 16 | 共享服务候选；冻结部署边界后决定是否同批迁移 |
| [thctay-etc](http://10.10.116.21:10000/xxt-plateform/thctay-etc/-/blob/dcacecb9c2eaab95bad04d1a5f8826d0480285b2/pom.xml) | `dcacecb9` | 55 | 任务签名、参数与结果反馈 |
| [thctay-etc-issue](http://10.10.116.21:10000/xxt-plateform/thctay-etc-issue/-/blob/8761b83781fe22450d6ba1e6c83d189c1080f635/pom.xml) | `8761b837` | 1 | 任务签名、参数与结果反馈 |
| [thctay-marketing](http://10.10.116.21:10000/xxt-plateform/thctay-marketing/-/blob/211febabfc8f50e56e10779cc3cf3c8af9bd7918/pom.xml) | `211febab` | 12 | 任务签名、参数与结果反馈 |
| [thctay-payment](http://10.10.116.21:10000/xxt-plateform/thctay-payment/-/blob/ee34164c6651ec2e0d9832c4d4e388647e6d549b/pom.xml) | `ee34164c` | 21 | 任务签名、参数与结果反馈 |
| [thctay-saps](http://10.10.116.21:10000/xxt-plateform/thctay-saps/-/blob/4f4a228aba8190c99ca78d4e55e9d66f1e80d244/pom.xml) | `4f4a228a` | 59 | 任务签名、参数与结果反馈 |
| [thctay-scene](http://10.10.116.21:10000/xxt-plateform/thctay-scene/-/blob/95e9640e7cda7d2db3f9025b613d3c6487ad8dbb/pom.xml) | `95e9640e` | 4 | 任务签名、参数与结果反馈 |

合计 256 个应用源码入口；其中共享合同与业财 21 个单列，其他 235 个。另加 thctay-mq 的 1 个公共重投入口。完整 handler、路径、签名、参数使用及调用点见 [物流平台源码与任务清单](物流平台源码与任务清单_20261030.md)。每个应用最终发布包应一次性包含该应用全部任务适配。

### 3.1 基类与特殊 API

- **thctay-crm 的 9 个 IJobHandler 子类**：NewModelOverdueMQRetryConsumerJob、NewModelApplicationAutoSignJob、NewModelApplicationNoticeRetryJob、NewModelApplicationSubmitRiskSystemJob、NewModelJiangSuOrderConsumerJob、NewModelVehicleIssueStatusQueryJob、PlatformEtcNoticeRetryJob、ProductChangeExecuteJob、ProductInstanceVehicleRepairJob，保留继承并实现无参 void execute。
- **thctay-crm 的 AbstractJob**：原抽象 `ReturnT<String> process(String)` 与 5 个子类同步处理。InsuranceServerSchedule、PostSalesWaitExecuteDetailSchedule、UpdateProductConfigSchedule、PlatformOrderStatusPollJob、PlatformSignPollJob 共 7 个注解入口，后两类还各有兼容 handler，名称全部保留。基类可统一为 void process()；有参处理保留为独立业务方法，兼容入口继续传递其原合作方参数。
- **thctay-payment 的 AbstractLocalJob**：仅保存手工设置的参数，不是旧 XXL 抽象接口；保留 put/get/clearParams 及 TradeSyncJobHandler 的手工时间窗口行为，改造 inTimeWheelPadding 调度入口即可。
- **thctay-etc / AfterSaleWorkOrderUploadDealJob**：替换在用 XxlJobLogger，三个 handler 均核对；业务批量注销结果及原调用不变。
- **protected 入口**：thctay-account 3、thctay-marketing 8、thctay-saps 31，共 42 个也在适配范围，不能只处理 public 方法。

### 3.2 直接调用和失败结果

手工接口直接调用已定位于 intf-hs、thctay-account、thctay-eas、thctay-etc、thctay-payment、thctay-saps。对有参方法保留无注解业务入口，调度入口单独读取上下文；例如售后重试、手工对账、时间轮补偿及账单任务，不能改成手工接口调用无参入口后取不到参数。

重点验证 `intf-hs` 的 consumeGain、`thctay-cashloan` 的 repayApplyJob、`thctay-crm` 的 pushPersonVehicleOrderBatch 等失败返回。原 HTTP 接口也有 ReturnT 返回值时继续保留接口协议，不能全仓替换 ReturnT。

`InsuranceServerSchedule.process` 最后返回 null，旧 2.2.0 执行器将其记为失败；迁移需显式保留失败反馈，或另行确认修正成功语义，不能无意间变成成功。PlatformEtcNoticeRetryJob 的 null 位于异步 lambda 内，其任务本身返回成功，应保持这一区别。

带 RefreshScope 的 etcIssuePushProcessJob、partnerBillRemitSchedule、loanBillAgreementPaymentSchedule，必须验证刷新后的再次调度。

## 4 Nacos 与执行器配置

- 所有在运后台应用切换 config/discovery 地址及 bootstrap 可用的账号凭据；保留自身服务名、namespace ID、group、Data ID 后缀。核对公共配置，如 `file.yaml`、`common.yaml`，以各应用原配置为准。
- op/marketing/ebaby 等网关有多份 `bootstrap-*.yml`，不能只改 bootstrap.yml；网关路由、下游服务订阅与直接 URL 配置均需验证。
- 多个源文件 test/sit/pre 为本机占位地址，不能据此填写目标环境。测试和生产分别建立实际参数表；源 Nacos/XXL 版本及库名待确认，不套用消费分期 amp 库名。
- 新中心保持各执行器原 appname；公共 XxlJobConfig 与 checker 自定义配置均检查 admin.addresses、accessToken、address/ip/port、日志路径及留存。AppName 不从仓库名猜测，以上线记录与旧中心为准。

## 5 回归重点与发布顺序

重点链路：网关到后台的发现与路由；CRM 进件/产品同步；ETC 高速消费、售后与重试；account 账户汇总和核对；payment 支付状态/对账/通知补偿；saps 账单、扣款、结算及报表；intf-hs 多省应用；共享合同签约及业财凭证。

先验证 public、框架及 thctay-saps/checker 父链，再完成全部入口和配置适配。隔离验证覆盖业务桩，环境回归使用确认的测试数据。付款、账单生成、注销、补偿和一次性历史处理须验证重复触发、失败重试、锁及原启停状态。

切换按 [总方案第 7 节](design.md#7-验证与切换) 执行：旧任务停触发并清在途 → 切换完整应用包及两个中心配置 → 验证调用和注册 → 按快照恢复。共享服务与不同省份应用的分批边界要先冻结。回退同时恢复旧应用、旧公共件、旧配置与中心，不只回改 Nacos 地址。

## 6 发布前补齐

实际在运服务与省份应用清单、共享合同/业财范围、定制 admin 差异、两条 bpm 父链的兼容结果、checker 父 POM 制品隔离、原任务及子任务快照、目标主机/库名/端口/鉴权、发布窗口和补跑规则。当前是源码方案，未标记任何应用已升级或测试通过。
