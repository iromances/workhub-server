# 物流平台 Nacos 和 XXL-JOB 升级进度清单

业务线：BL000006 物流平台；GitLab 项目组：xxt-plateform；环境：测试 test。核查基准：2026 年 11 月 3 日 15:18，北京时间。目标：Nacos 服务端及客户端 2.5.4，XXL-JOB 调度中心及官方 core 2.5.0。

当前进度：公共组件和部分应用已有升级代码，两个新测试中心已能访问；业务应用改造尚未收尾，尚未形成完整的构建、部署和联合验收记录。下面逐仓库列出已确认完成、仍有问题及下一步，覆盖 54 个仓库；Nacos 运行实例单列。实际在运应用和共享实例的发布范围还需冻结。

## 当前可确认的完成事项

- [x] 新 Nacos 控制台显示 2.5.4；sit 空间已有 59 条配置。
- [x] 新 XXL-JOB 控制台显示 2.5.0；已有 245 条任务和 20 个执行器定义。
- [x] thctay-public 的 dependencies、schedule、MQ 已声明新版 0.0.2-SNAPSHOT，管理目标 client/core 版本。
- [x] bpm-framework 根工程、7 个子模块父版本及内部版本属性已升至 0.0.3-SNAPSHOT。
- [x] 7 个应用的 123 个业务任务入口已改为无参 void；公共 MQ 另有 1 个入口已改。
- [x] intf-risk、marketing、marketing-back 的 R 分支、risk-backend、scene 的测试配置已声明新 Nacos 地址。

这些勾选项只表示上述具体改动或控制台事实已确认。任务入口适配为 123/256，约 48.0%；不据此计算整个升级项目完成率。245 条调度记录与 256 个源码入口不是同一口径，不能直接相减判断漏迁。

| 观察项 | 核查结果 | 对进度的含义 |
| --- | --- | --- |
| 存在本次升级分支的仓库 | 16 个 | 代表有源码进展，包含公共件和部分改造仓库 |
| 业务任务入口 | 已改 123，仍为旧签名 133 | CRM 29；其他 7 个未见升级分支的仓库合计 104 |
| Nacos sit 非空服务 | 0 | 尚未确认应用接入新注册中心 |
| XXL 在线执行器机器 | 0 | 20 个执行器定义尚未体现在线接入 |
| XXL 运行报表累计调度 | 0 | 尚无新中心任务执行记录可用于验收 |
| 已完成全项验收的业务子系统 | 本次核查尚不能确认 | 需补构建、部署 SHA、运行和业务回归证据 |

## 清单口径

- “已确认完成”写具体源码改动或运行观察；“下一步及待验证”不能自动视为失败，也不能计为已通过。
- “未见升级分支”表示本次远程核查范围未找到本次升级分支，表中只展示主分支或默认分支现状；不判断他人尚未提交的本地工作。
- 测试配置中的 `127.0.0.1:8848` 为源码占位值，实际启动参数可能覆盖；本清单将其列为待核配置，不认定运行环境一定使用该值。
- 表中父依赖“0.0.1/0.0.2/0.0.3”均指对应 `-SNAPSHOT` 版本；“dependencies”指 `com.thchengtay:thctay-dependencies`。
- 分支简称：R 为 `origin/release/nacos-2.5.4-xxljob-2.5.0_20261027`；F 为 `origin/feature/nacos-2.5.4-xxljob-2.5.0_20261102`；M 为 `origin/master`。每行提交链接固定到核查 SHA，不跟随分支后续变化。

## 优先处理的问题

| 清单项 | 涉及子系统 | 已确认的问题 | 完成条件 |
| --- | --- | --- | --- |
| P01 旧公共件覆盖 | thctay-payment、thctay-eas | payment 固定 MQ/schedule 0.0.1；eas 固定 schedule 0.0.1 | 清理覆盖，依赖树和成品包确认目标公共件 |
| P02 CRM 未完成核心适配 | thctay-crm | 父依赖及 29 个入口仍旧，基类处理与方案存在差异 | 新父依赖、无参入口、基类及失败语义共同通过验证 |
| P03 SAPS 父制品隔离 | thctay-saps、saps-account-checker | 新旧父 POM 继续复用 saps 0.0.1 坐标 | 落实隔离方案，CI 可稳定解析指定的新旧父制品 |
| P04 执行器配置差异 | saps-account-checker | 自定义日志默认 30 天，公共件默认 90 天 | 统一约定并验证实际值；只装配正确的执行器 |
| P05 发布分支差异 | thctay-marketing-back | R 已声明新地址，F 仍为本机占位值 | 冻结最终分支及 SHA，并核对配置和依赖同时具备 |
| P06 旧框架父链 | intf-sence-back | 仍继承 bpm-framework 0.0.2 | 切至新版父链并验证 MQ 等传递依赖 |
| P07 剩余 104 个入口 | ETC、intf-hs、cashloan、contract、invoice、bank、etc-issue | 已核对主分支仍为旧入口，未见升级分支 | 逐仓库完成适配；覆盖多省和共享服务范围 |
| P08 测试接入与交付追溯 | 所有在运应用、xxl-job-admin | 新中心尚无在线应用/执行器接入证据；新 admin 包与旧仓库基线未对应 | 固定部署来源和 SHA，完成配置、注册、调度和业务回归 |

优先顺序：公共件与父链 → 清理旧依赖和完成业务入口 → 冻结发布分支及启动配置 → 核对迁移数据 → 构建部署和联合验收。ETC、intf-hs、CRM 合计 116 个旧入口，是当前任务适配的主要工作量。

## 公共组件进度

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **thctay-public** · 公共件源码已改；[R d44d555c25](http://10.10.116.21:10000/xxt-plateform/thctay-public/-/tree/d44d555c252b751af4d367fdea656fdb1fda41cb) | dependencies、schedule、MQ 已使用 0.0.2-SNAPSHOT；管理 nacos-client 2.5.4、xxl-job-core 2.5.0；公共 MQ 入口 1/1 为无参 void；默认日志 90 天。 | 本次尚未核到构件发布及最终制品校验记录；业务应用仍有旧公共件覆盖。 | 核对 CI 使用的固定提交、三个新版构件及校验值；验证 MQ 重投；保留旧版和未受影响构件。 |
| **bpm-framework** · 父链源码已改；[R 2e473432a6](http://10.10.116.21:10000/xxt-plateform/bpm-framework/-/tree/2e473432a6071b8afa24737fa8638fa04c37be47) | 根版本、7 个子模块父引用及 bpm.version 已升 0.0.3-SNAPSHOT；父 dependencies 为 0.0.2-SNAPSHOT。 | intf-sence-back 尚未跟随；框架升版不能证明所有使用方已完成升级。 | 发布并验证框架构件；验证 risk-backend 的旧框架到新框架兼容性及流程功能。 |

## 有业务任务的子系统进度

共 15 个仓库、256 个业务入口，包含共享合同 5 个和共享业财 16 个；公共 MQ 不在本表分母内。已改签名仍须通过参数、失败反馈和业务回归。

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **thctay-crm** · 部分改造；[R 3f0b297410](http://10.10.116.21:10000/xxt-plateform/thctay-crm/-/tree/3f0b2974104389d3f020fe196a710155722bbbbc) | 已提交部分旧任务类继承、调试调用及 bootstrap 调整。 | 29 个入口仍为有参 ReturnT；父 dependencies 仍为 0.0.1；AbstractJob 仍是旧抽象签名；旧 IJobHandler 类删除继承的处理与方案不同。 | 补齐父依赖和 29 个入口；明确基类实现方式；验证 InsuranceServerSchedule 的 null 失败语义、兼容 handler 和失败反馈。 |
| **thctay-etc** · 未见升级分支；[M dcacecb9c2](http://10.10.116.21:10000/xxt-plateform/thctay-etc/-/tree/dcacecb9c2eaab95bad04d1a5f8826d0480285b2) | 本次未核到已完成的升级事项。 | 55 个入口仍为旧签名；父 dependencies 0.0.1；AfterSaleWorkOrderUploadDealJob 仍引用 XxlJobLogger。 | 完成 55 个入口、旧日志 API 和依赖升级；保留售后手工重试调用；验证重复触发及批量注销。 |
| **intf-hs** · 未见升级分支；[M df6684c3ef](http://10.10.116.21:10000/xxt-plateform/intf-hs/-/tree/df6684c3efb1134f7d084c4cea8f84861d611059) | 本次未核到已完成的升级事项。 | 32 个入口仍为旧签名；父 dependencies 0.0.1；common/intf-common 的 schedule 仍固定 0.0.1。 | 按各省 domain 子应用核对构建和部署；清理覆盖；验证 consumeGain 失败反馈、参数及手工调用。 |
| **thctay-saps** · 部分改造；[F 4bb146cff7](http://10.10.116.21:10000/xxt-plateform/thctay-saps/-/tree/4bb146cff746bde1cf8c52c8581effae94166d87) | 父 dependencies 已升 0.0.2；59/59 个入口为无参 void。 | 自身父 POM 及内部模块仍复用 0.0.1，checker 仍继承该坐标；与方案建议的新旧制品隔离有差异；测试源码配置仍有本机占位地址。 | 落实 SAPS/checker 版本隔离；冻结实际启动配置；验证账单、扣款、结算、手工接口、参数及刷新后再次调度。 |
| **thctay-payment** · 部分改造；[F e8841c66fa](http://10.10.116.21:10000/xxt-plateform/thctay-payment/-/tree/e8841c66fa27c96ab12fab08954b2b79c6d1ff9a) | 父 dependencies 已升 0.0.2；21/21 个入口为无参 void。 | 根 POM 的 thctay-mq、thctay-schedule 仍显式指定 0.0.1；测试源码配置仍有本机占位地址。 | 清理两个旧版本覆盖并核对成品包；验证 AbstractLocalJob 手工时间窗口、直接调用返回协议和支付补偿幂等。 |
| **thctay-eas** · 部分改造；[F f5aa9fe4e1](http://10.10.116.21:10000/xxt-plateform/thctay-eas/-/tree/f5aa9fe4e1bb4dfac2659fc7fb4f00795b447087) | 父 dependencies 已升 0.0.2；16/16 个入口为无参 void。 | 根 POM 的 thctay-schedule 仍显式指定 0.0.1；测试源码配置仍有本机占位地址；共享实例边界尚未冻结。 | 清理旧依赖；验证手工凭证生成及各业务线共享调用；补实际启动配置与执行验证。 |
| **thctay-marketing** · 源码关键项已改 待验证；[R e1d7db1853](http://10.10.116.21:10000/xxt-plateform/thctay-marketing/-/tree/e1d7db18539d0f99dc2acc36def5943d602b54d0) | 父 dependencies 已升 0.0.2；12/12 个入口为无参 void；test 的 config/discovery 已声明新 Nacos 地址。 | 尚未核到该提交的构建与部署验收；配置声明不代表运行生效。 | 验证参数、失败反馈及任务业务行为；核对实际 profile、注册订阅、MQ 公共任务和调度回调。 |
| **thctay-account** · 部分改造；[F 8ae51cdc7a](http://10.10.116.21:10000/xxt-plateform/thctay-account/-/tree/8ae51cdc7a43fa95c8aea3c5269266ef1f659c72) | 父 dependencies 已升 0.0.2；7/7 个入口为无参 void。 | sit 源码仍配置本机占位地址，实际部署覆盖尚未核定。 | 冻结实际地址和 profile；验证手工对账入口、参数、失败反馈及账户汇总。 |
| **saps-account-checker** · 部分改造；[F 84c5abc618](http://10.10.116.21:10000/xxt-plateform/saps-account-checker/-/tree/84c5abc6181f5d92834a266c971289024a19e43b) | 4/4 个入口为无参 void。 | 仍继承 thctay-saps 0.0.1；自定义 XxlJobConfig 的日志默认 30 天，与新公共件默认 90 天不同；sit 源码仍是本机占位地址。 | 随 SAPS 落实父 POM 隔离；核对唯一执行器装配、Token、地址和日志留存；验证对账任务。 |
| **thctay-scene** · 源码关键项已改 待验证；[R 70f064d7c7](http://10.10.116.21:10000/xxt-plateform/thctay-scene/-/tree/70f064d7c721ad4fce1e9afe339c6c4e3a8f10dd) | 父 dependencies 已升 0.0.2；bpm-engine 引用 0.0.3；4/4 个入口为无参 void；sit 的 config/discovery 已声明新地址。 | 尚未核到对应构建、部署及业务回归。 | 验证引擎批处理、参数与结果反馈；核对新版框架依赖及新注册域调用。 |
| **thctay-cashloan** · 未见升级分支；[M b23696139d](http://10.10.116.21:10000/xxt-plateform/thctay-cashloan/-/tree/b23696139d350f4e9302fe48c5928283315570be) | 本次未核到已完成的升级事项。 | 7 个入口仍为旧签名；父 dependencies 0.0.1。 | 升级依赖和入口；重点验证 repayApplyJob 失败反馈、MQ 公共任务及 Nacos 接入。 |
| **thctay-contract** · 未见升级分支；[M 3182bff529](http://10.10.116.21:10000/xxt-plateform/thctay-contract/-/tree/3182bff5291ec7e1ef9a8f14f80fa46ba0c64bbc) | 本次未核到已完成的升级事项。 | 5 个入口仍为旧签名；父 dependencies 0.0.1；共享实例是否同批迁移未冻结。 | 完成入口及依赖升级；验证签约、公共 MQ 和跨业务线调用；协调共享实例窗口。 |
| **invoice-exinterface** · 未见升级分支；[M 2b283bdf6e](http://10.10.116.21:10000/xxt-plateform/invoice-exinterface/-/tree/2b283bdf6e7d830691298e0b29e6d34fcbad4136) | 本次未核到已完成的升级事项。 | 3 个入口仍为旧签名；父 dependencies 0.0.1；exinterface-service 的 schedule 固定 0.0.1。 | 升级父依赖并清理覆盖；适配开票任务、参数及回调；验证实际 Nacos/XXL 配置。 |
| **intf-bank** · 未见升级分支；[M 6b5a422edc](http://10.10.116.21:10000/xxt-plateform/intf-bank/-/tree/6b5a422edc9790655663693e2c801389828e0717) | 本次未核到已完成的升级事项。 | 1 个入口仍为旧签名；父 dependencies 0.0.1；intfbank-service 的 schedule 固定 0.0.1。 | 升级父依赖并清理覆盖；完成入口、参数、结果反馈及环境接入验证。 |
| **thctay-etc-issue** · 未见升级分支；[M 8761b83781](http://10.10.116.21:10000/xxt-plateform/thctay-etc-issue/-/tree/8761b83781fe22450d6ba1e6c83d189c1080f635) | 本次未核到已完成的升级事项。 | 1 个入口仍为旧签名；父 dependencies 0.0.1；schedule 固定 0.0.1。 | 升级依赖和入口；验证 etcIssuePushProcessJob 在配置刷新后的再次调度。 |

## 无自定义业务任务但已有改造的子系统

这些仓库已核对的远程提交未发现自定义业务任务入口，仍需核对 Nacos、公共 MQ 传递依赖及部署配置。

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **intf-risk** · 部分改造；[R d4320324cd](http://10.10.116.21:10000/xxt-plateform/intf-risk/-/tree/d4320324cdf1b32b8e067a57a26b7d5b05a746b7) | 父 dependencies 已升 0.0.2；test 的 config/discovery 已声明新 Nacos 地址。 | 本次尚未核到对应构建和运行验收记录。 | 验证配置加载、注册订阅及实际调用；确认 profile 与部署 SHA。 |
| **thctay-auth** · 部分改造；[R 29ffce84ef](http://10.10.116.21:10000/xxt-plateform/thctay-auth/-/tree/29ffce84ef1ea902259809d8f86e984136917462) | 父 dependencies 已升 0.0.2；直接声明 nacos-client 并由父工程管理版本。 | test 源码仍为本机占位地址，实际启动覆盖尚未核定。 | 补齐实际启动配置；验证鉴权服务及其上下游服务发现。 |
| **thctay-marketing-back** · 分支待整合；[F 2f5ebb8a98](http://10.10.116.21:10000/xxt-plateform/thctay-marketing-back/-/tree/2f5ebb8a98724190fbd910a766dd66b5e108fd19)；[R e5e5e2ba0f](http://10.10.116.21:10000/xxt-plateform/thctay-marketing-back/-/tree/e5e5e2ba0fd72d37bca80b4d06ea13aa53c43cb5) | R、F 两分支均已将父 dependencies 升为 0.0.2；R 的 sit config/discovery 已声明新地址。 | R 与 F 内容不一致：F 的 sit 仍为本机占位地址，不能把两分支的完成项自动合并计为一个发布包。 | 明确最终发布分支，合并应保留的改动并重新固定 SHA；验证后台配置与下游调用。 |
| **thctay-risk-backend** · 部分改造；[R cedeb919d2](http://10.10.116.21:10000/xxt-plateform/thctay-risk-backend/-/tree/cedeb919d2b0448f651c584dbc5b8604e395bb94) | 父 bpm-framework 已升 0.0.3；sit 的 config/discovery 已声明新地址。 | 原父框架为 0.0.1，跨旧版本的兼容结果尚未确认。 | 验证流程、规则及后台功能；核对 MQ 传递依赖、构建产物及部署 SHA。 |
| **thctay-wms** · 部分改造；[R 4360ad111e](http://10.10.116.21:10000/xxt-plateform/thctay-wms/-/tree/4360ad111ed342a219c59a49c4d55dcf97931134) | 父 dependencies 已升 0.0.2；原 schedule 0.0.1 显式依赖已从根 POM 移除。 | sit 源码仍为本机占位地址；移除依赖后的成品包与运行结果尚未确认。 | 核对实际启动配置及依赖必要性；验证库存相关功能和服务发现。 |
| **thctay-saps-core-common** · 范围差异待核；[F 0e088ce5ac](http://10.10.116.21:10000/xxt-plateform/thctay-saps-core-common/-/tree/0e088ce5ac02df0d601c960fc227db047e0a67c9) | 父 dependencies 已升 0.0.2。 | 该仓库是公共模型，方案原要求保持现有引用；当前父版本调整是否必要及消费方影响待核。 | 明确保留该改动的理由与影响范围，验证消费方解析和模型兼容；不计作一个已部署业务服务。 |

## 仍使用旧框架父链的子系统

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **intf-sence-back** · 未见升级分支；[M f2d197825c](http://10.10.116.21:10000/xxt-plateform/intf-sence-back/-/tree/f2d197825c7d203a92a6e1a61c04952d1d3bead5) | 本次未核到已完成的升级事项。 | 父 bpm-framework 仍为 0.0.2；引用的 MQ 未随新父链确认到 0.0.2。 | 将父链对齐 0.0.3，验证业务流程及实际 MQ/core/client 解析，再完成测试接入。 |

## 尚未接入新版依赖的网关

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **thctay-front-gateway** · 未见升级分支；[M ebe4720564](http://10.10.116.21:10000/xxt-plateform/thctay-front-gateway/-/tree/ebe47205640caeeeb750a6b6457e6e854d1c21be) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖；核对多份 bootstrap、实际 profile、共享配置、网关路由和下游订阅。 |
| **thctay-ebaby-gateway** · 未见升级分支；[M 03c3040592](http://10.10.116.21:10000/xxt-plateform/thctay-ebaby-gateway/-/tree/03c30405922bf14a1ecf7afc0cbb7e7c80d925da) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖；核对多份 bootstrap、实际 profile、共享配置、网关路由和下游订阅。 |
| **thctay-op-gateway** · 未见升级分支；[M 2b1164dd9c](http://10.10.116.21:10000/xxt-plateform/thctay-op-gateway/-/tree/2b1164dd9ca35c51f5d5ed778a80adc92c181bde) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖；核对多份 bootstrap、实际 profile、共享配置、网关路由和下游订阅。 |
| **thctay-marketing-gateway** · 未见升级分支；[M a1e958046c](http://10.10.116.21:10000/xxt-plateform/thctay-marketing-gateway/-/tree/a1e958046c84ef5b1cc7ebdd5335c631f15448f5) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖；核对多份 bootstrap、实际 profile、共享配置、网关路由和下游订阅。 |
| **thctay-partner-gateway** · 未见升级分支；[M fd1a1e1525](http://10.10.116.21:10000/xxt-plateform/thctay-partner-gateway/-/tree/fd1a1e1525364f1683d3ac8215f1f0c02e64688f) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖；核对多份 bootstrap、实际 profile、共享配置、网关路由和下游订阅。 |

## 尚未接入新版依赖的接口子系统

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **insurance-exinterface** · 未见升级分支；[M 71f393bb75](http://10.10.116.21:10000/xxt-plateform/insurance-exinterface/-/tree/71f393bb7563bb5cc95f1fb8375faed3740139eb) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **intf-abc** · 未见升级分支；[M 63fc29d582](http://10.10.116.21:10000/xxt-plateform/intf-abc/-/tree/63fc29d5828f3a68517b20043847c2af11845a17) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **intf-boc** · 未见升级分支；[M 31417e771e](http://10.10.116.21:10000/xxt-plateform/intf-boc/-/tree/31417e771e3dffacf41102cfb570a7376e686c7a) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **intf-ccb** · 未见升级分支；[M 7e27b68f34](http://10.10.116.21:10000/xxt-plateform/intf-ccb/-/tree/7e27b68f3452fd39c5dd0711080544ddcf0d839b) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **intf-common** · 未见升级分支；[M 13f4fad4b0](http://10.10.116.21:10000/xxt-plateform/intf-common/-/tree/13f4fad4b0d3d1f68bf11f744f9d331a18abfeee) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1；已有 MQ 依赖，还需验证公共重投入口。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 验证 MQ 重投参数和回调。 |
| **intf-credit** · 未见升级分支；[M 453d37b07f](http://10.10.116.21:10000/xxt-plateform/intf-credit/-/tree/453d37b07fbdbc60694385c63478e06e242f56d5) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **intf-gs** · 未见升级分支；[M 9457089520](http://10.10.116.21:10000/xxt-plateform/intf-gs/-/tree/945708952046fc213e9ca76b142018fc19ffc7cc) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **intf-icbc** · 未见升级分支；[M 6feab6a665](http://10.10.116.21:10000/xxt-plateform/intf-icbc/-/tree/6feab6a665c8f6d8337faec8441ae6bdfd2a3b60) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **intf-qdccb** · 未见升级分支；[M 2f1fdc95ea](http://10.10.116.21:10000/xxt-plateform/intf-qdccb/-/tree/2f1fdc95ea39e6db0d73dfd022110c6eac61d6d0) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |

## 尚未接入新版依赖的其他后台子系统

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **saps-back** · 未见升级分支；[M aa1f91664d](http://10.10.116.21:10000/xxt-plateform/saps-back/-/tree/aa1f91664d8b16d0858fce087961a254f76fa764) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **thctay-cms** · 未见升级分支；[M 3c5a41bc0d](http://10.10.116.21:10000/xxt-plateform/thctay-cms/-/tree/3c5a41bc0d56092c5a3590ed8ba9fc4ac568d400) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1；已有 MQ 依赖，还需验证公共重投入口。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 验证 MQ 重投参数和回调。 |
| **thctay-devicemanage** · 未见升级分支；[M 21fe064e5d](http://10.10.116.21:10000/xxt-plateform/thctay-devicemanage/-/tree/21fe064e5d66ab55c63decd2f380cce1f874b1c8) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1；已有 core 依赖，需核对实际执行器用途。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **thctay-ems-logistics** · 未见升级分支；[M 793a0df73b](http://10.10.116.21:10000/xxt-plateform/thctay-ems-logistics/-/tree/793a0df73b024c4242e73dbb863e0bbf6c0435e1) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **thctay-lianrongbao** · 未见升级分支；[M 59aeb2ef4d](http://10.10.116.21:10000/xxt-plateform/thctay-lianrongbao/-/tree/59aeb2ef4d0ea52cf174066eb9c1777ae5dd82a6) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **thctay-message** · 未见升级分支；[M bc28a3081f](http://10.10.116.21:10000/xxt-plateform/thctay-message/-/tree/bc28a3081ffdf82faa84a0c19402f4b7e201fa96) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **thctay-report** · 未见升级分支；[M 54efa6a4a8](http://10.10.116.21:10000/xxt-plateform/thctay-report/-/tree/54efa6a4a8a4b2c1d37cb419eaf8f92128e93b2b) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **thctay-sf-logistics** · 未见升级分支；[M 9ebec82269](http://10.10.116.21:10000/xxt-plateform/thctay-sf-logistics/-/tree/9ebec822690d7c40b6dc2b5c342980e5e8d953cc) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 |
| **thctay-user** · 未见升级分支；[M c10fef4cb7](http://10.10.116.21:10000/xxt-plateform/thctay-user/-/tree/c10fef4cb7908e1c6513744a3e5c683c9dce9b85) | 本次未核到已完成的升级事项。 | 已核对主分支仍继承 dependencies 0.0.1；已有 MQ 依赖，还需验证公共重投入口。 | 更新父依赖和实际 Nacos 配置，核对最终包，完成注册订阅及本系统业务回归。 验证 MQ 重投参数和回调。 |

## 调度中心与辅助仓库

| 子系统及核对版本 | 已确认完成的事项 | 仍存在的问题或差异 | 下一步及待验证事项 |
| --- | --- | --- | --- |
| **xxl-job-admin** · 新中心版本已确认；[M 3332af1565](http://10.10.116.21:10000/xxt-plateform/xxl-job-admin/-/tree/3332af1565c8365f2603d741a6ad0fd30faefde8) | 测试控制台已显示 2.5.0；已有 245 条任务、20 个执行器定义。 | 本仓库已核对的 master 仍继承 xxl-job 2.2.0；新中心部署包来源与仓库提交尚未对应；在线执行器 0。 | 登记新中心来源和部署版本，核对旧定制功能、迁移一致性、Token、执行器接入和回调。 |
| **mock-server** · 是否纳入待定；[mock_v1 05bddfd9b6](http://10.10.116.21:10000/xxt-plateform/mock-server/-/tree/05bddfd9b684822b915741ef804c80787be6b54d) | 已识别为使用 Nacos 的联调辅助仓库；未计入 256 个业务入口。 | 默认 mock_v1 仍继承 dependencies 0.0.1，并直接调用 Nacos 接口；尚未确认参与本批联调。 | 先确认是否在用；若纳入，适配客户端及直接注册、心跳和鉴权接口。 |
| **thctay-scaffold** · 模板维护项；[M 36d4f0d571](http://10.10.116.21:10000/xxt-plateform/thctay-scaffold/-/tree/36d4f0d571337bc72a14df0ccc338d368f369fb0) | 已确认为生成模板，不计作在运服务。 | 生成模板仍引用 dependencies 0.0.1 及旧依赖体系。 | 根据模板面向的新旧技术线安排更新；不计入测试部署完成率。 |
| **xxt-front-scafold** · 本次无需中间件源码改造；[M 9267d9dfc2](http://10.10.116.21:10000/xxt-plateform/xxt-front-scafold/-/tree/9267d9dfc24403e01c3e484d1228f1fd66301fd4) | 已识别为前端仓库，本次未纳入 Java 中间件升级。 | 未发现本次直接改造项。 | 随相关网关完成页面及接口回归。 |
| **thctay-guangdong-tools** · 本次未发现直接改造项；[M 69cbbdb2f1](http://10.10.116.21:10000/xxt-plateform/thctay-guangdong-tools/-/tree/69cbbdb2f1314b64c6c85174f0846c987ac9f52c) | 已核对主分支，未发现本次 Nacos/XXL 直接引用。 | 未发现本次直接改造项。 | 若实际部署新增相关依赖，重新核定范围。 |
| **etc-tools** · 本次未发现直接改造项；[ql-etc-tools 71a93c6e2a](http://10.10.116.21:10000/xxt-plateform/etc-tools/-/tree/71a93c6e2a5d5c8240180d25f3d6f88e107c590e) | 已核对默认 ql-etc-tools，未发现本次 Nacos/XXL 直接引用。 | 没有 origin/master；当前按默认分支识别范围。 | 确认实际在用版本；仅在发现相关依赖时纳入改造。 |
| **etc-doc** · 资料仓库；[main 1be2be6ec0](http://10.10.116.21:10000/xxt-plateform/etc-doc/-/tree/1be2be6ec0e5430032dea538cc51630c169439ad) | 已识别为资料仓库，默认分支为 main。 | 无运行服务的升级验收项。 | 按实际迁移结果维护相关说明。 |

## 测试环境与迁移清单

| 改造点 | 已确认完成的事项 | 尚待完成或验收 |
| --- | --- | --- |
| Nacos 服务端 | [新中心](http://10.10.116.110:8848/nacos)可访问，版本 2.5.4，standalone 模式 | 服务稳定性、实际鉴权、网络及客户端连接验证 |
| Nacos 配置数据 | sit namespace ID 为 `bae98fd5-aa76-427b-a5fc-c32f81befa38`，已有 59 条配置 | 与旧中心核对 namespace、Data ID、group、正文/MD5、共享配置、权限及增量差异 |
| Nacos 应用接入 | 5 个仓库的升级分支已声明新测试地址，见逐系统清单 | 逐应用核对 config/discovery、启动 profile、覆盖参数和凭据；验证配置拉取、刷新、注册、订阅、重连及真实调用；sit 非空服务当前为 0 |
| XXL 调度中心 | [新中心](http://10.10.116.110:28080/xxl-job-admin/)可访问，版本 2.5.0 | 登记部署包来源及必要定制承接，核对与数据库和应用配置的对应关系 |
| XXL 执行器定义和任务 | 已有 20 个执行器定义、245 条任务；定义包含示例项 | 对照旧中心核对 ID/appname、handler、参数、Cron、路由/阻塞、超时/重试、子任务、GLUE、权限与启停状态；尚未确认全量迁移一致 |
| XXL 注册和执行 | 在线机器 0、累计调度 0 为本次观察值 | 核对 admin.addresses、同环境一致的非空 Token、原 appname、双向地址/端口、日志路径/留存；完成受控测试触发、日志和回调验证 |
| 新旧中心切换 | 已有方案约定 | 保存旧任务启停快照，关闭旧触发并处理在途后切换；按原状态恢复，验证无双重调度 |
| 回退 | 已有成套回退方案约定 | 准备旧应用、旧公共件、旧配置及旧中心，演练恢复并核对已执行业务及补跑清单 |

## 每个在运子系统的完成验收清单

每个子系统须按适用项独立勾选，并附发布 SHA 和验证记录。无定时任务的服务仍须验证 Nacos；公共模型、工具及资料仓库按其范围使用构件验证或标记不适用。

- [ ] 发布范围已冻结：包含在运实例、intf-hs 各省应用、共享合同/业财及外部调用关系。
- [ ] 分支、源码 SHA、公共件版本和构建产物来源明确；marketing-back 等多分支差异已处理。
- [ ] 业务源码和测试源码编译通过；依赖树及可执行包仅包含目标 client/core 和正确的公共件，旧显式覆盖已清除。
- [ ] 全部 handler 名称、大小写、参数解析、空参数、默认日期、筛选范围、失败码、异常及成功消息保持原约定。
- [ ] 直接调用任务的 Controller/服务/测试保留显式参数和所需返回协议；手工入口不依赖调度上下文。
- [ ] 自定义基类、旧日志 API、protected 方法、RefreshScope 刷新后的再次调度均完成验证。
- [ ] Nacos config/discovery 的地址、namespace、group、Data ID、共享配置和鉴权实际生效；注册、订阅、刷新和断线重连通过。
- [ ] 执行器成功注册；Token、端口、日志路径和留存正确；触发、日志、结果、回调、超时及失败重试通过。
- [ ] 核心业务回归通过，包括适用的支付补偿、账单、结算、开票、签约、凭证、账户核对、ETC、网关和跨系统调用。
- [ ] 并发、锁、幂等、重复触发、停启状态及一次性补偿任务边界验证通过。
- [ ] 完成迁移一致性核对和联合回退演练，登记部署 SHA、遗留问题及验收结果。

测试源码被跳过、未发布到目标环境或仅修改方法签名，均不满足以上整体完成条件。当前不把任何业务子系统标记为“升级已验收完成”。

## 来源与后续维护

- 方案依据：[物流平台升级方案与改动点](物流平台Nacos和XXLJOB升级方案改动点_20261030.md)、[共用迁移和验收方案](design.md)、[任务入口及直接调用清单](物流平台源码与任务清单_20261030.md)。原方案是 2026-10-30 的规划基线，进度以本清单的核查时点为准。
- 原始参考：[桌面消费分期升级方案](/Users/aslight/Desktop/nacos和xxljob升级/消费分期Nacos和XXLJOB升级迁移方案_20261028.md)。原方案中的地址、账号及脚本库名不直接套用物流环境。
- 固定提交证据：[逐仓库源码快照](证据/升级进度核查_20261103_1518/逐仓库源码快照.json)、[补充源码证据](证据/升级进度核查_20261103_1518/补充源码证据.json)、[逐子系统进度条目](证据/升级进度核查_20261103_1518/逐子系统进度条目.json)。54 个仓库对应 55 份分支快照，marketing-back 的 R/F 分别保留。
- 环境依据：[核查口径与环境快照](证据/升级进度核查_20261103_1518/核查口径与环境快照.json)。控制台观察来自本次对话已完成的只读核查；本次文档整理沿用 15:18 快照，未重新刷新运行状态。测试环境应用实际部署 SHA 尚未确认。
- 数据库资源匹配限制：已查询的 10.10.116.24 和公共 10.10.116.53 资源返回设备租赁或叉车/智能柜数据，未与物流新中心对应；本清单未把这些数据库结果作为物流迁移完成依据。
- 后续更新时按子系统补充改造提交、构建产物、实际部署 SHA 与验证结果；将本行对应的问题关闭后再更新状态，不以某个公共件已升级替代全部使用方验收。
