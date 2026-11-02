# 消费分期 Nacos 与 XXL-JOB 升级实施方案

版本：v2.8（部门执行版）　修订日期：2026年10月22日

适用业务线：消费分期 BL000004／asset-plateform。本文用于各部门分工、开发、演练和上线准备；生产切换须满足第四节的放行条件。当前已完成现状与代码核查，尚未完成升级构建、联调、恢复演练或生产变更。

## 一 系统现状、升级目标与实施约定

**在同一维护窗口一次性切换 Nacos、XXL-JOB 及适配后的业务应用。停机按约 2 小时规划，窗口内预留整体回退与恢复验证时间；不分 A/B 批上线。**

| 对象 | 当前版本 | 目标版本 | 执行要求 |
| --- | --- | --- | --- |
| Nacos 服务端 | **1.3.2**；单节点 | **2.5.4**；单节点，使用独立新库 | 新实例部署；保留旧实例及旧库供回退 |
| Nacos 客户端 | **nacos-client 1.2.1** | **nacos-client 2.5.4** | 14 个后端纳入改造；配置中心和注册中心同时适配 |
| XXL-JOB 调度中心 | **admin 2.2.0**；单节点 | **admin 2.5.0**；为单节点 | 保留旧中心及旧库；新旧中心不得同时调度同一任务 |
| XXL-JOB 执行器依赖 | **xxl-job-core 2.2.0**；risk-schedule 有局部版本覆盖 | **xxl-job-core 2.5.0** | 清除旧版本覆盖，适配业务及公共任务入口；每个应用准备一个包含全部适配的目标包 |
| MySQL | **8.0.28-231003** | **8.0.28-231003** | 不变 |
| JDK | **Oracle Java 1.8.0_361** | **Oracle Java 1.8.0_361** | 不变 |
| 业务 Spring Boot | **2.2.9.RELEASE** | **2.2.9.RELEASE** | XXL 新调度中心单独使用其发行版自带的 Boot 2.7.18 |
| 业务 Spring Cloud | **Hoxton.SR7** |  **Hoxton.SR7** | 不变 |
| 业务 Spring Cloud Alibaba | **2.2.1.RELEASE** | **2.2.1.RELEASE** | 验证旧 Starter 与 nacos-client 2.5.4 的配置加载、刷新和注册兼容性 |
| 业务 Spring Framework | Boot BOM 默认管理 **5.2.8.RELEASE** | 随保留的 Boot 组合管理，不单独升级 | 核对最终依赖及打包结果；框架组合若未通过验收，调整方案并重新验证 |
| 仓库与改造范围 | 17 个仓库：14 个 Java 后端、2 个前端、1 个文档仓库 | 14 个后端完成适配与构建；前端和文档仓库不改 Java 依赖 | 实际生产发布实例按冻结清单；wolease 是否在用见 D1 |
| Nacos 配置与服务 | prod 16 条配置、public 1 条；prod 可见 13 个有实例的服务、29 个健康实例 | 完整迁移冻结清单中的配置；在用应用重新注册 | 保留 namespace ID、dataId、group 和配置内容；数量按最终快照核对，观察清单见附录三 |
| XXL 执行器与任务 | 8 个分组、13 个在线地址；83 条任务：68 RUNNING、15 STOP | 保留任务 ID、handler、参数、子任务关系及原启停状态 | 逐项迁移校验；支付、放款、代扣、账单和补偿不得重复处理，清单见附录三 |
| 安全整改 | 已有等保问题清单；T38/T41 漏洞明细和复测口径仍需安全部门提供 | 适用测评问题完成整改和复测 | 口令、MFA、传输加密和审计按改造项 10、11 执行；仅升级版本不作为销项依据 |

依据说明：服务端、MySQL、资源归属和生产依赖基线由业务方确认；JDK 来自上述主机的 java -version 截图。控制台数量为 2026年10月22日只读观察值；代码依赖与任务入口基线为 2026年10月19日，Spring 版本依据源码父 POM 链及 BOM（附录二 [1]、[2]）。两条配置引用于 2026年10月22日刷新 master 后专项复核。提交及技术依据见附录二。

上线成功标准：在用服务注册／发现及配置加载／刷新正常；任务触发、参数、结果、回调和告警正常；关键业务与账务核对通过；原漏洞检查项复测通过；整体回退演练通过。上线日期、维护时段和回退决策时点在排期时落实，见 D4。

## 二 代码核查与配置处理

### 已确认的代码改造范围

- 公共父工程为 `thctay-dependencies:0.0.1-SNAPSHOT`；13 个后端直接继承，`amp-risk-backend` 经 `bpm-framework:0.0.2-SNAPSHOT` 间接继承。
- `thctay-schedule` 的 BaseJobHandler 使用旧接口；XxlJobConfig 允许空 Token，执行日志默认 30 天。`risk-schedule/pom.xml` 单独固定 xxl-job-core 2.2.0，必须清除覆盖。
- `thctay-mq/MessageSenderRetrySchedule` 另有 1 个旧任务入口；订单、支付、清结算、风控、公共接口有直接 MQ 公共组件依赖。
- 两个 gateway 固定 Gateway 2.2.3.RELEASE；risk 存在旧日志版本属性，纳入依赖冲突验证。

| 业务仓库 | 任务模块或目录 | 旧入口数 |
| --- | --- | --- |
| amp-capital | capital-schedule | 26 |
| amp-saps | saps-schedule | 30 |
| amp-payment | payment-schedule | 13 |
| amp-order | amp-order-service / service/xxljob | 8 |
| amp-ops-reporting | amp-ops-reporting-service / service/schedule | 6 |
| amp-risk | risk-schedule | 3 |
| 合计 | 以上均为有效 @XxlJob 注解入口 | 86 |
| 另计公共组件 | thctay-mq / MessageSenderRetrySchedule | 1 |

这里的 86+1 是源码入口数，83 是生产任务记录数，不能互相替代。RUNNING 表示启用定时调度，不表示任务正在执行或执行成功；STOP 任务仍可能作为子任务使用。

### 两条配置的处理结论

| 配置 | 已核查结论 | 执行决定 |
| --- | --- | --- |
| thctay-contract.yml | 当前 14 个后端代码未发现直接读取本套配置。独立合同服务虽加载同名 dataId，但代码中的 prod 指向另一套 Nacos 地址及 namespace | 原样保留迁移；不因此新增发布应用，也不迁移合同服务的另一套 Nacos |
| amp-risk-scene-backend.yml | 当前代码未发现引用；风控后台应用名是 amp-risk-backend，对应 amp-risk-backend.yml | 旧配置保留迁移；验收现有风控后台配置，不新增风控场景发布系统 |

静态代码不能排除生产启动参数覆盖，因此保留配置、不做删除清理。源码与地址证据见附录四；合同调用的具体回归项见改造项 7、12。

## 三 版本切换带来的变化

| 对象 | 升级带来的变化 | 消费分期需要落实的改造及验证 |
| --- | --- | --- |
| Nacos 客户端通信 | 源码依赖从 1.2.1 升到 2.5.4，注册、订阅和配置通信采用 2.x 的 gRPC 长连接机制。[15](https://nacos.io/docs/v2.5/upgrading/200-compatibility/) | 14 个后端更新依赖；除默认 8848 外，放通客户端到服务端的 9848，并调整负载均衡和防火墙。测试冷启动、断线重连、配置刷新、服务发现及网关路由。 |
| Nacos 服务端和数据结构 | 目标为 2.5.4，需使用对应数据库结构；2.5 系列对配置灰度及灰度历史记录做了调整。2.x 客户端不能连接旧 1.x 服务端。[7](https://nacos.io/docs/v2.5/manual/admin/upgrading/)[15](https://nacos.io/docs/v2.5/upgrading/200-compatibility/) | 本方案新建单节点实例和独立新库，迁移配置、namespace ID、权限及所需元数据；旧历史记录另行归档。部署规模仍为单节点，不增加集群节点。 |
| XXL 任务调用接口 | 执行器依赖从 2.2.0 升到 2.5.0。旧方式通过方法参数接收任务参数并返回 ReturnT；新入口采用无参方法，通过 XxlJobHelper 获取参数、记录日志和上报结果。[6](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)[16](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java) | 适配 86 个业务入口和 1 个公共入口，修改 thctay-schedule、thctay-mq；保留 handler 名称，核对参数、失败上报、异常、重试和业务结果。 |
| XXL 调度数据库 | 相较官方 2.2.0，目标结构包含 schedule_type、schedule_conf、misfire_strategy 等字段，并调整注册唯一索引及日志索引。[10](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)[11](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql) | 将原 job_cron 等数据迁入目标结构，保留任务 ID、执行器组、参数及子任务关系；明确窗口期间任务的补跑规则，避免漏跑或重复执行。生产版本已确认为 2.2.0，仍须核对实际表结构是否有定制差异。 |
| Spring 与第三方依赖 | XXL 2.5.0 调度中心使用自身的 Boot 2.7.18，并更新 Netty、Gson、SLF4J、Groovy 等依赖；xxl-job-core 的 Spring 依赖为 provided。[3](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/pom.xml)[4](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/pom.xml) | 业务应用 Java 8、Boot 2.2.9、Cloud Hoxton.SR7、Alibaba 2.2.1 本轮暂保留，不代表该组合已通过兼容验证。检查 Maven 最终解析和打包结果，完成网关、Redis/Redisson、日志和任务回归；失败则阻断上线。 |

旧 XXL 方法参数和 ReturnT 不能原样沿用：只换 jar 可能丢失任务参数、忽略失败返回。统一适配方式及代码示例见改造项 4、5。Nacos 和 XXL 的发布修复记录见附录二 [13]、[14]；安全整改以原扫描项复测结果为准。

## 四 执行范围、部门分工与上线条件

### 应用范围和修改位置

以下路径相对于各仓库根目录。14 个后端均纳入代码适配与构建；实际生产发布实例以改造项 1 冻结的清单为准。`amp-wolease` 当前未见在线服务或执行器地址，其是否停用由业务负责人在演练前确认：已停用则记录并排除本次生产启动，仍需运行则补齐部署与验证；不得在切换时临时启动未知用途实例。

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

各仓库根 pom.xml 同步调整；risk-backend 的 BPM 父链一并发布。amp-admin、amp-unionpay-h5 和 amp-doc 不改 Java 依赖。公共组件采用独立固定发布版本，不覆盖旧 SNAPSHOT。

WorkHub 登记的 intf-paychannel、thctay-contract、thctay-eas、xxt-pay、xxt-pay-gateway 作为业务接口联调对象，不因接口调用而自动加入本套中间件迁移。已由代码确认的合同联调涉及订单、支付和资金业务，具体 URL 与用例见改造项 7、12。

### 部门任务单

各部门领取下表任务并指定一名执行负责人；发布负责人汇总姓名、完成日期、产物位置和验收结果。职责按角色分配，本文不虚构人员名单。

| 牵头部门／角色 | 负责改造项 | 必须交付的结果 | 完成时点 |
| --- | --- | --- | --- |
| 发布负责人 | 1、13、14；协调全部任务 | 责任人清单、最终应用／实例清单、窗口安排、放行记录和回退决策时点 | 演练前确定负责人；切换前完成放行 |
| 公共组件研发 | 2、3、4 | 固定版本父 POM、schedule／mq／BPM 适配包、依赖检查记录 | 业务联调前 |
| 各业务系统研发 | 1、5、7、12；配合 9、13、14 | 应用目标包及校验和、任务适配、配置映射、业务测试和回退旧包 | 联合演练前 |
| 运维 | 6、7、9、10、11、13、14 | 新环境、网络、密钥引用、部署配置、日志采集、切换与回退操作单 | 联合演练前 |
| DBA | 1、6、8、11、14 | 实际 DDL 差异、备份、迁移／校验 SQL、恢复结果及耗时 | 联合演练前；窗口内执行最终同步 |
| 安全 | 1、10、11、12 | T38/T41 明细、认证／加密／审计验收口径、复测报告 | 开发前明确检查项；切换前复测通过 |
| 测试 | 12、13、14 | 用例结果、联合演练报告、回退验证和生产冒烟清单 | 切换前 |
| 业务负责人 | 1、12、13、14 | wolease 范围结论、任务补跑规则、资金幂等核对及业务恢复确认 | 范围在演练前；补跑规则在排期时、切换前完成 |

### 尚需确认的决策与输入

| 编号 | 待落实事项 | 责任方 | 截止条件／缺失时处理 |
| --- | --- | --- | --- |
| D1 | amp-wolease 是否仍需在生产运行 | 业务负责人、应用研发 | 联合演练前确认并冻结发布实例范围 |
| D2 | 新 Nacos 单节点实例及新库、新 XXL 单节点中心及新库的资源与部署位置 | 运维、DBA、发布负责人 | 单节点方式已确定；环境搭建前落实主机、端口和数据库部署位置，确保新旧环境隔离并保留回退条件 |
| D3 | T38/T41 漏洞名称／编号、对象、端口及复测标准；身份策略与密码算法验收口径 | 安全 | 开发整改前提供；切换前所有适用项有复测结论 |
| D4 | 上线日期、最终维护时段、回退决策人和任务补跑规则 | 发布负责人、业务负责人 | 排期时确定；约 2 小时窗口需容纳实测切换或回退，规则未落实不切换 |

MySQL 版本、服务端版本、生产依赖与 master 一致、资源独占范围均已确认，不再重复收集。两条旧配置和 public 配置按保留迁移处理；删除清理不属于本次升级。

### 实施前必须产出的材料

这些材料由执行部门生成，不作为要求业务方重新确认现状的问题。

| 产物 | 必含内容 | 验收方 |
| --- | --- | --- |
| 发布基线 | 各应用 Git SHA、包／配置版本、校验和、负责人、运行实例、旧包位置；核对原核查后的增量 | 发布负责人、研发 |
| 数据迁移包 | 两套中间件实际 DDL 与定制差异、备份与恢复记录、迁移和校验 SQL；17 条配置／83 条任务／8 个执行器的差异说明，最终数量按冻结快照 | DBA、应用研发 |
| 任务恢复清单 | 原启停状态、参数、Cron、路由、阻塞、重试、超时、child_jobid；逐任务补跑与防重规则；核实任务 13→8 的关系 | 业务负责人、测试 |
| 运行与安全配置 | 新旧地址、端口、namespace/group、appname、密钥引用、合同 URL 有效值、账号权限、传输和日志策略 | 运维、安全 |
| 验证报告 | 改造项 12 的适用用例、漏洞复测、联合切换及整体回退耗时、问题关闭记录 | 测试、安全、业务负责人 |

**切换放行条件：D1—D4 已落实；上述材料齐备；改造项 2—12 验收通过；改造项 14 演练满足窗口；发布负责人汇总研发、运维、DBA、测试、安全和业务确认。任一关键条件未满足则不进入生产切换。**

## 五 具体可执行改造项

### 改造项 1 冻结发布、配置和任务清单

牵头：发布负责人；执行：业务研发、运维、DBA、安全。

修改位置：发布基线、配置迁移包、任务恢复清单。

1. 采用第一节已确认版本；固定各应用本次发布 SHA，核对原基线后的代码增量。对第四节 14 个后端逐项记录运行／停用状态、负责人、目标包和旧包，形成最终生产实例清单。
2. 导出 Nacos namespace ID、dataId、group、type、content 摘要及权限元数据；导出 XXL 任务和执行器完整字段。与附录三观察值比较，记录新增、停用和变更，窗口内再做最终冻结快照。
3. DBA 比对实际 DDL 与目标结构并形成迁移包；业务负责人填写逐任务补跑和防重规则；安全部门落实 D3 的漏洞及验收清单。

**完成条件：第四节各项交付物均有负责人和产物路径；发布范围、配置清单与任务恢复清单可逐项核对。**

### 改造项 2 统一发布版本并升级公共依赖

责任部门：公共组件维护人、各系统研发

修改位置：thctay-public 的 dependencies、schedule、mq；bpm-framework；14 个后端 pom.xml

1. 建立唯一发布版本清单，每个应用准备一个包含本次全部改造的目标包，统一纳入 Nacos 客户端升级及适用的 XXL 执行器、任务适配。记录 Maven 版本、Git SHA、包校验和及配置版本；保留升级前完整基线，不覆盖旧 SNAPSHOT。

2. 在新 thctay-dependencies 的 dependencyManagement 显式管理 com.alibaba.nacos:nacos-client:2.5.4 和 com.xuxueli:xxl-job-core:2.5.0。仅设置同名 property 不作为完成依据。

3. 发布适配后的 thctay-schedule、thctay-mq；更新 capital、payment、ops-reporting 根 POM，risk-schedule、wolease-service 的 schedule 固定版本；更新公共 mq 对 schedule 的引用。

4. 删除 risk-schedule 中 xxl-job-core 的 2.2.0 局部版本；同步更新 bpm-framework 父依赖及 risk-backend 的父版本。其他公共模块保持原版本，避免整套 SNAPSHOT 被动漂移。

**完成条件：所有目标包的 nacos-client 均为 2.5.4；包含 XXL 的应用仅有 xxl-job-core 2.5.0，并加载对应新版公共组件；包与配置归入同一发布版本清单。**

## 六 框架与公共组件改造

### 改造项 3 固定 Spring 边界并处理依赖冲突

责任部门：公共组件维护人、网关和风控研发

修改位置：thctay-dependencies/pom.xml；amp-risk/pom.xml；两个 gateway 的 pom.xml

1. 本轮业务应用暂保留 Boot 2.2.9、Cloud Hoxton.SR7、Alibaba 2.2.1。XXL 调度中心使用其 2.5.0 发行版自己的 Boot 2.7.18；不因此替换所有业务 Spring jar。[3](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/pom.xml)[4](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/pom.xml)

2. 检查目标包中 Netty、Gson、SLF4J、Logback、Groovy 的实际解析结果。以 XXL 2.5.0 声明的 Netty 4.1.116.Final、Gson 2.11.0 作为集成起点；通过当前 BOM 的显式依赖管理落实，不让旧 Boot 静默回落。网关、Redis/Redisson 一并回归。

3. 日志框架暂保持现有 Boot 支持的同代组合；不直接把 SLF4J 换成 2.x 配 Logback 1.2。核实并清除 risk 的遗留版本覆盖，将最终组合写入版本清单。所有新旧依赖仍需扫描，不能把上游声明版本当作安全认证。

**完成条件：编译、启动、日志及业务回归均通过；若出现无法解决的兼容错误或框架自身阻断漏洞，停止本次上线，另排 Boot / Cloud / Alibaba 整体升级。[5](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)**

### 改造项 4 改造公共调度组件和消息重投任务

责任部门：公共组件研发

修改位置：thctay-schedule 下 BaseJobHandler.java、XxlJobConfig.java；thctay-mq 下 MessageSenderRetrySchedule.java

1. BaseJobHandler 按 2.5.0 的 IJobHandler 改为 void execute()：从 XxlJobHelper.getJobParam() 取参，调用原 process(String)，将非成功结果映射为 handleFail；异常保留堆栈并继续抛出。

2. XxlJobLogger 改为 XxlJobHelper.log。XxlJobConfig 增加 Token 非空、appname 非空、管理地址非空的启动检查；生产环境缺参直接启动失败。保留已有端口和实例地址绑定逻辑。

3. MessageSenderRetrySchedule 使用与业务任务相同的无参入口方式。消息重投既要验证任务成功回调，也要验证消息幂等和失败重试，不把这一个公共入口遗漏在 86 个业务入口之外。

**完成条件：公共包在 Java 8 下构建成功；正确 Token 可通信、空或错误 Token 被拒绝；消息重投只产生预期的一次业务效果。**

## 七 业务任务代码如何改

### 改造项 5 为 86 个业务任务适配新的调用接口

责任部门：capital、saps、payment、order、ops-reporting、risk 研发

修改位置：第二节所列 6 个任务模块，完整 handler 清单见附录一

1. 统一将 @XxlJob 放在 public void 无参方法上，handler 字符串原样保留。可以保留原 ReturnT&lt;String&gt; process(String) 作为内部业务方法，由新入口取参、调用并映射结果，避免同时重写业务逻辑。

2. order 的 ApplicationPreliminaryAuthSubmitJob、ApplicationRefactoringJob、PreliminaryRequestRecordJob、SyncApplyBankCardDataJob 及 ops-reporting 的 DeviceFinanceRecordFixedJob 共 5 个类直接继承 IJobHandler；改为普通 Spring Bean 并增加新入口，删除旧接口的 @Override。

3. order 的 AbstractXXLJob 与 3 个子类可以继续保留内部 process(String) 契约，把 @XxlJob 移到新入口。已有事务、代理和异常处理在服务层验证，不能因增加内部调用而绕过原有事务边界。

**完成条件：86 个业务入口与改造项 4 的 1 个公共入口均完成无参 void 适配；原 handler 无改名、无重复注册；指定参数、失败返回和抛异常三类结果均与预期一致。[6](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)**

### 以支付签章任务为例的适配方式

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

## 八 Nacos 服务端和客户端改造

### 改造项 6 部署单节点 Nacos 2.5.4 并迁移配置

责任部门：运维、DBA、业务研发

修改位置：新实例 conf/application.properties、standalone 启动参数、新数据库、配置导出文件

1. 按单节点 standalone 模式部署 2.5.4，配置外置 MySQL 独立数据源及运维入口，用目标版本 mysql-schema.sql 初始化空的新库。新旧实例不共享数据库；保留旧 1.x 实例，不直接覆盖原安装目录；本次不配置多节点 cluster.conf。[7](https://nacos.io/docs/v2.5/manual/admin/upgrading/)[8](https://nacos.io/docs/v2.5/manual/admin/deployment/deployment-overview/)

2. 导出并在新实例重建原 namespace ID，导入冻结清单中的全部 dataId/group/type/content，包括两条未发现代码引用的配置及 public 配置；确认共享配置、扩展配置和 profile 对应项。自定义角色、权限、服务元数据及持久化实例分别迁移，不能仅依赖配置 ZIP。

3. 启用 nacos.core.auth.enabled=true；设置 nacos.core.auth.plugin.nacos.token.secret.key、nacos.core.auth.server.identity.key/value 的自定义安全值，并纳入该实例的受控部署配置。为应用创建受限账号，设置管理员强口令；不沿用默认密钥或开启 UserAgent 白名单。[9](https://nacos.io/docs/v2.5/manual/admin/auth/)

4. 对配置逐项比较内容摘要和命名空间归属；业务服务切换后重新注册临时实例。存量配置历史单独归档可检索，不承诺配置导入会自动迁移历史记录。

**完成条件：单节点正常运行并连接新 MySQL 库；原配置清单逐项一致；匿名读取或修改被拒绝；在测试环境重启实例后，客户端能够重连，配置加载与服务注册／发现恢复。单节点故障切换不属于本次交付能力。**

| 通信方向 | 默认端口 | 具体动作 |
| --- | --- | --- |
| 业务客户端 → Nacos | 8848、9848 | 分别放通 HTTP 主端口与 gRPC TCP 通道；入口映射保持 +1000 偏移 |
| 运维终端 → 管理入口 | HTTPS 入口 | 限制到指定管理网段；服务端实际端口以部署配置为准 |

### 改造项 7 修改 14 个后端的 Nacos 启动配置

责任部门：各系统研发、运维

修改位置：第四节 bootstrap 路径及生产部署参数

1. 在统一目标包中落实 nacos-client 2.5.4。将 spring.cloud.nacos.config 与 discovery 下的 server-addr、username、password 分别注入；密码通过部署密钥配置，不写入 Git 或文档。

2. namespace 使用原 ID；group、file-extension、prefix、shared-configs / extension-configs 及 refresh 语义保持。应用启动前必须能得到地址和凭据，不能仅把这些值放在尚未连接成功的 Nacos 内。

3. 在测试环境验证旧 Alibaba Starter 与新 SDK 的启动、配置刷新、实例上下线、Feign/Ribbon 调用和网关路由。清理测试客户端快照后再做冷启动，避免本地缓存掩盖连接失败。

4. 按第二节代码核查保留两条旧配置；核对订单 `api.contract`、支付 `agree.sign.contractSignUrl`、资金 `commonParam.contractSignUrl` 的有效值及来源。风控后台验证 `amp-risk-backend.yml`，不将其改成 `amp-risk-scene-backend.yml`。不因合同公共服务存在同名配置而改动其另一套 Nacos。

**完成条件：14 个后端逐一完成构建；最终生产发布清单中的应用提交目标包与联合测试记录；服务端为 2.5.4 后再接入 2.x 客户端，客户端 2.5.4 不指向旧 1.x 实例。**

## 九 XXL 调度中心和数据库改造

### 改造项 8 迁移任务数据并保留任务身份

责任部门：DBA、调度平台研发

修改位置：XXL 新数据库；版本化的迁移 SQL、校验 SQL、任务停启清单

1. 从经核验的生产库备份恢复出独立新库；在新库上执行增量迁移。保留任务 ID、group ID、handler、Cron、参数、子任务 ID、重试及阻塞策略。禁止把包含初始化账号和示例任务的全量建表脚本直接执行到旧库或已恢复的新库。

2. 新库全部任务保持停止。保留原启停状态到迁移清单，清理新库中的旧自动注册地址；手动地址重新登记为目标执行器地址。新调度中心不得在正式切换前触发旧执行器。

3. 按下表 2.2.0 → 2.5.0 差异比对实际 SHOW CREATE TABLE，将定制差异纳入可重复演练的迁移脚本。[10](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)[11](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql)

**完成条件：新旧任务记录逐项校验，ID 及子任务关系保持；新库没有处于运行状态的迁移任务；恢复、迁移和数据校验演练通过。**

| 表 | 必须落实的迁移 |
| --- | --- |
| xxl_job_info | 增加 schedule_type、schedule_conf、misfire_strategy；原定时任务将 job_cron 复制到 schedule_conf 并设置 CRON；原 job_cron 核对后归档，并删除或改为可空，避免新建任务插入失败；停机期间补跑按清单执行。 |
| xxl_job_info | glue_remark 新结构为非空，先处理旧 NULL；保存并核对任务参数、超时、失败重试、阻塞策略和子任务关系。 |
| xxl_job_group | address_list 调整为 text；增加 update_time；区分自动和手动注册清理旧地址。 |
| xxl_job_registry | 检查重复键后按目标结构建立 registry_group/key/value 唯一索引；新库注册地址重新生成。 |
| xxl_job_log_report 与 log | 补充 report.update_time 和目标版本日志索引；按数据量评估索引工期，不在生产临时尝试。 |

### 改造项 9 部署 2.5.0 调度中心并切换执行器

责任部门：运维、调度平台研发、各业务研发

修改位置：xxl-job-admin 配置；XxlJobConfig 读取的部署参数

1. 先验证新数据库与发行版自带 Connector/J 9.1.0 的兼容性、时区及字符集。新中心连接新库，配置环境专用随机 xxl.job.accessToken，替换示例 default_token。目标包执行器使用完全相同 Token 和新 admin.addresses，不同时填写新旧中心地址。

2. 保留执行器 appname；核对 executor.address / ip / port 的可达性和端口占用。调度中心到执行器、执行器到中心的注册和回调两个方向都测试。全部执行器随 Nacos 和业务应用在同一维护窗口切换。

3. 保持迁移任务停止，先运行经批准的测试任务验证注册、触发、回调、日志、失败告警及子任务；恢复任务时严格采用改造项 1 中的停启清单。

**完成条件：新中心只发现目标执行器；非法 Token 被拒绝；任务调用、回调、日志读取全部成功；未引入新旧中心同时调度同一任务的状态。**

## 十 安全配置和审计改造

### 改造项 10 补齐认证权限和传输保护

责任部门：安全、运维、平台研发

修改位置：Nacos 与 XXL 管理入口、应用账号、网络策略、传输通道

1. 管理入口接入公司统一身份认证或受控管理入口，落实个人账号、MFA、口令复杂度与到期、失败锁定和会话超时；阻断绕过入口直达管理功能。本次测评建议值为 5 次失败锁定 30 分钟、空闲 30 分钟退出；安全部门在 D3 中确定最终值，运维按确定值配置并提交实测结果。

2. Nacos 应用账号按 namespace 和资源授权；XXL 普通运维账号按执行器授权，管理员单独保管。严格限制可编辑 GLUE 或执行脚本的账号；缺少所需审计员等角色时，由统一管理平台补足并提交操作证据。

3. 浏览器管理入口启用 HTTPS TLS 1.2+；业务 SDK 的 HTTP/gRPC、调度中心与执行器双向链路、数据库连接逐条实施 TLS 或受控 IPsec 隧道。XXL Token 和 Nacos 鉴权只解决身份校验，不替代传输加密。

4. 运维提交每条链路的源、目的、协议、证书或隧道配置及验证结果；若 TLS 在代理终止，后段跨主机链路也要保护。存在 SM 算法要求时，由安全部门确认密码产品和实施口径后验收。

**完成条件：无共享管理员或默认弱密钥；错误身份和越权访问被拒绝；所有测评涉及的跨主机敏感传输链路均有加密证据。**

### 改造项 11 将日志保存和审计落实到配置

责任部门：运维、DBA、安全、应用研发

修改位置：调度中心日志、执行器日志、Nacos 操作日志、集中日志平台

1. 日志保存以至少 6 个自然月为验收要求。运维和 DBA 在演练前提交容量测算及具体清理策略：默认方案为 admin 的 xxl.job.logretentiondays 与执行器的 xxl.job.executor.logretentiondays 均为 200 天，并调整公共 XxlJobConfig 的 30 天默认值；如改用短期本地留存加集中归档，须提交覆盖至少 6 个自然月的检索和恢复证据。[12](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-admin/src/main/resources/application.properties)

2. 集中采集任务执行日志、调度记录、登录和管理操作记录；将 Nacos 配置变更及管理员操作审计纳入采集。任务运行日志不能代替管理员审计日志。

3. 定义日志只读审计权限、删除审批、备份及恢复验证；选一条历史记录从集中平台检索并恢复。对任务参数和配置内容中的凭据及个人信息脱敏。

**完成条件：能按用户、任务或配置变更追溯；保存周期覆盖至少 6 个月；清理策略、备份和恢复均有证据。**

| 原问题 | 本方案对应项 | 销项依据 |
| --- | --- | --- |
| T38、T41 漏洞 | 1、2、4、5、6、8、9、12 | 同一漏洞检查项复测通过；仅显示新版本号不够 |
| T11、T14、T18、T21、T25 | 6、9、10 | 口令、锁定、超时、MFA、账号和最小权限实测 |
| T16、T50、T56 | 7、9、10 | 浏览器及 SDK / RPC 双向传输保护实测 |
| T34、T35 | 11 | 集中审计、保存周期、检索和恢复证据 |
| T47、T52、T58 等 | 独立整改任务 | 可信验证、存储完整性和加密不因本次升级自动完成 |

## 十一 构建和测试验收

### 改造项 12 按模块构建并核验发布包

责任部门：各业务研发、测试

修改位置：各仓库本次升级分支、统一版本清单和 CI 流水线

1. 在隔离测试配置下先生成 effective-pom 和依赖树，再运行对应 Maven 模块的 test / package。涉及数据库、MQ 或支付接口的集成测试使用获准的测试环境，外部资金渠道使用桩或测试账户。

2. 静态检查统一目标包中全部 @XxlJob 为无参 void；旧 XxlJobLogger、旧 IJobHandler 方法覆盖已清理；依赖树和最终 BOOT-INF/lib 中无遗留 nacos-client 1.x、xxl-job-core 2.2.0。

3. 版本证据覆盖 14 个后端；MQ 公共任务的实际注册范围按运行配置核对。将构建 SHA、包校验和、依赖树、扫描报告和测试报告绑定到同一版本清单。

**完成条件：每个待发布应用均有可定位的构建、依赖和验收记录；下表全部适用用例通过，失败用例不得以“服务能启动”替代。**

供执行人员使用的命令示例，当前文档编制过程未执行升级构建：

```bash
mvn help:effective-pom -Doutput=target/effective-pom.xml
mvn dependency:tree -Dverbose -DoutputFile=target/dependency-tree.txt
mvn -pl capital-start -am verify
mvn -pl payment-controller -am verify
mvn -pl saps-controller -am verify
mvn -pl amp-order-start -am verify
mvn -pl amp-ops-reporting-start -am verify
mvn -pl risk-start -am verify
```

以上模块命令分别在各自仓库根目录执行；其余后端按第四节启动模块构建，单模块工程在根目录 verify。不得跳过本次适配新增的测试。

| 测试项 | 执行方式和通过标准 |
| --- | --- |
| Nacos 冷启动和配置 | 清测试缓存后启动；配置值正确；修改测试配置可刷新；错误凭据或越权读取被拒绝 |
| 合同接口联调 | 订单预审／进件要素核验、支付协议签约、资金合同流程创建通过；三个合同 URL 切换前后一致且生产不回落到 SIT。主动调用在获准测试环境完成，生产按既定冒烟与业务观察验证 |
| 风控后台配置 | amp-risk-backend 按现有 namespace/group 加载 amp-risk-backend.yml，刷新及后台功能正常；不把保留的旧配置当成新增发布系统 |
| 单节点重启和网络恢复 | 在测试环境验证 Nacos 单节点重启、gRPC 连接中断后的重连及注册／配置恢复；记录中断期间行为和恢复耗时，恢复后 Feign、网关调用成功，无持续注册失败 |
| 任务参数 | 每个在用 handler 覆盖空参、有效参数、非法参数；指定单号只影响该单 |
| 失败反馈 | 人为制造业务失败返回和异常；中心必须记录失败并触发既定告警 |
| 支付和账务幂等 | 同单重复触发、失败重试、回调延迟或重复；无重复扣款、放款、账单和通知 |
| 调度策略 | Cron、路由、分片、阻塞、超时、子任务按生产清单逐项核对；未使用能力标不适用 |
| Spring 与三方依赖 | 两个网关、风控、支付、清结算均启动并执行业务；无 LinkageError、日志绑定错误 |
| 安全和日志 | 按原漏洞、接口、账号及权限复测；日志采集、保存、恢复有效 |

## 十二 一次性切换与整体回退

### 改造项 13 在同一维护窗口完成生产切换

窗口约束：按约 2 小时规划一次性切换，具体日期及补跑规则排期时确定。部署新中间件、准备应用包、迁移脚本开发和首次恢复演练在窗口前完成；窗口内执行冻结、最终同步、统一切换、联合验收及必要回退。演练记录各步骤耗时。设窗口结束时间为 T_end，实测整体回退与恢复验证耗时为 R，发布负责人确定缓冲时间 B，则最晚回退决策时点为 T_end − R − B；到该时点仍未通过关键验收即执行回退。约 2 小时为规划值，演练超出预算须调整方案或窗口。

责任部门：发布负责人、运维、DBA、业务研发、测试

修改位置：统一发布窗口、应用包及配置清单、任务停启清单

1. 窗口前完成新 Nacos 单节点实例、新 XXL 单节点中心、目标包及整体回退演练；第四节最终发布清单内的应用及相关接口验证就绪后进入窗口。冻结 Nacos 配置和 XXL 任务定义，入口停接新请求，停用全部迁移任务，按清单暂停 MQ 消费，等待在途请求和任务结束。

2. 完成 Nacos 最终配置导出、XXL 库备份及摘要校验，将最终数据同步至新 Nacos 实例和新调度库，执行迁移并复核。新旧中心的迁移任务均保持停用。按改造项 1 冻结的实例清单统一发布应用：全部在用应用指向新 Nacos，含执行器的应用同时指向新 XXL 中心；已确认停用的系统保持停用。

3. 流量、MQ 消费和生产任务保持暂停，联合验证服务注册、配置加载刷新、关键交易及执行器注册、触发、回调。核对上述三个合同 URL 和风控后台实际配置来源；生产验证按已批准的冒烟清单执行，不为验证临时发起真实签约或支付。全部通过后由发布负责人统一确认，按已演练的恢复清单开放流量、MQ 和任务，仅恢复原应启用的任务；任一关键项失败即执行整体回退。

4. 旧中心的迁移任务持续停用；新旧中心不得重复调度。窗口内完成恢复验证，切换后连续观察至少 24 小时并覆盖下一次日切和对账；月度等低频任务保留后续核查责任和日期，演练中另做对应场景验证。错过窗口的任务按登记规则补跑，资金任务先核对账务。

**完成条件：窗口内联合验收通过并恢复业务；观察期确认注册数、关键交易、补偿、日切和对账正常，无重复调度或处理，由业务负责人确认完成。**

### 改造项 14 演练并执行整体回退

责任部门：发布负责人、运维、DBA、研发、业务值守

修改位置：升级前完整版本清单、旧配置、旧 Nacos 实例、旧调度库、任务执行对照表

1. 上线前演练完整回退，测量恢复时间并记录最晚回退时刻。服务注册缺失、配置错误、关键交易失败、任务误处理或重复处理触发停止恢复业务并整体回退；涉及资金重复时立即停用相关任务并核对账务。

2. 回退先关闭新侧流量，暂停 MQ 消费和新中心任务，等待在途处理结束。冻结新侧配置修改，记录切换后已执行的任务及业务结果，核对需要保留的配置差异。

3. 按升级前清单恢复全部旧应用包及 Nacos、XXL 配置，接回旧 Nacos 实例和旧调度中心、旧调度库。验证旧服务注册、配置和执行器回调后恢复业务；按执行对照表恢复任务，已执行任务不得自动重放。不能仅把新客户端地址改回旧中间件。

4. 旧中间件、数据库和版本包保留到验收及回退期结束。回退应用或中间件不能撤销已经发生的支付、账单或外部通知，业务数据按现有核对和补偿流程处理。

**完成条件：至少完成一次 Nacos、XXL 及全部应用的联合回退演练；恢复时限满足窗口，验证旧服务与旧执行器恢复，完成在途任务和已执行任务核对。**

其他业务线复用：沿用部门分工、14 个改造项及放行条件，重新核实本业务线的版本、依赖、系统／实例、配置和任务清单；消费分期数量不直接套用。

## 附录一 任务入口改造清单

以下为 origin/master 去除注释后的有效 @XxlJob 入口。业务入口按改造项 5、公共入口按改造项 4 增加无参适配入口，handler 名称原样保留。源码文件名用于在对应模块内定位，统计不包含生产数据库任务实例。

### amp-capital  共 26 个入口

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

### amp-saps  共 30 个入口

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

### amp-payment  共 13 个入口

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

### amp-order  共 8 个入口

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

### amp-ops-reporting  共 6 个入口

模块：amp-ops-reporting-service；仓库提交：a6a7c8d9b835

| 源码类 | handler 名称 |
| --- | --- |
| ConsumerFinanceRecordFixedJob | FinanceReportJob |
| CustomerSupportWeekReportJob | CustomerSupportReportJob |
| DeviceFinanceRecordFixedJob | DeviceFinanceRecordFixedJob |
| ETCReportJob | ETCReportJob |
| ETCReportWeekRefreshJob | etcReportWeekRefreshJob |
| LogisticsReportJob | LogisticsReportJob |

### amp-risk  共 3 个入口

模块：risk-schedule；仓库提交：545bdaee6621

| 源码类 | handler 名称 |
| --- | --- |
| BatchBpmEngineJob | batchStrategy |
| BatchBpmEngineJob | singleInvokeEngine |
| BatchBpmEngineJob | batchEngineNotify |

### thctay-public  共 1 个入口

模块：thctay-mq；仓库提交：f0bfbdcea977

| 源码类 | handler 名称 |
| --- | --- |
| MessageSenderRetrySchedule | messageSenderRetrySchedule |

## 附录二 代码基线与技术依据

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

公共组件原核查引用：`thctay-public` 为 `f0bfbdcea977`，位置 `/Users/aslight/IDEAWorkspace/thctay-public`；`bpm-framework` 为 `43e5efa9fbde`，位置 `/Users/aslight/IDEAWorkspace/xxt-plateform/bpm-framework`。公共父链需由改造项 2 固定新发布版本。

### 官方技术依据

- [1. Spring Cloud Alibaba 2.2.1 BOM](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-dependencies/pom.xml)
- [2. Spring Boot 2.2.9 BOM](https://raw.githubusercontent.com/spring-projects/spring-boot/v2.2.9.RELEASE/spring-boot-project/spring-boot-dependencies/pom.xml)
- [3. XXL-JOB 2.5.0 版本依赖](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/pom.xml)
- [4. xxl-job-core 2.5.0 依赖与 provided Spring](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/pom.xml)
- [5. Spring Boot 2.7 社区维护结束说明](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)
- [6. XXL-JOB 2.5.0 MethodJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)
- [7. Nacos 2.5 升级手册](https://nacos.io/docs/v2.5/manual/admin/upgrading/)
- [8. Nacos 部署与端口说明](https://nacos.io/docs/v2.5/manual/admin/deployment/deployment-overview/)
- [9. Nacos 权限校验说明](https://nacos.io/docs/v2.5/manual/admin/auth/)
- [10. XXL-JOB 2.2.0 数据库结构](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)
- [11. XXL-JOB 2.5.0 数据库结构](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql)
- [12. XXL-JOB 2.5.0 管理中心配置](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-admin/src/main/resources/application.properties)
- [13. Nacos 2.5.4 发布记录](https://github.com/alibaba/nacos/releases/tag/2.5.4)
- [14. XXL-JOB 2.5.0 发布记录](https://github.com/xuxueli/xxl-job/releases/tag/2.5.0)
- [15. Nacos 2.x 客户端通信与兼容性说明](https://nacos.io/docs/v2.5/upgrading/200-compatibility/)
- [16. XXL-JOB 2.2.0 MethodJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)
- [17. Spring Cloud Alibaba 2.2.1 Nacos 配置加载器](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-starters/spring-cloud-starter-alibaba-nacos-config/src/main/java/com/alibaba/cloud/nacos/client/NacosPropertySourceLocator.java)：核对应用名、prefix/name、共享配置、扩展配置及 profile 的加载规则。

框架生命周期：保留业务 Java 8／旧 Spring 组合须通过本次兼容和安全验收；后续生命周期升级另行安排。XXL 调度中心采用 Boot 2.7.18 也不能替代依赖漏洞扫描。[5](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)

## 附录三 生产控制台观察清单

观察日期：2026年10月22日。此清单用于演练和冻结快照的差异核对；不替代最终配置及数据库迁移包。

### Nacos 配置与在线服务

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

### XXL 执行器与任务

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

## 附录四 两条配置的源码核查证据

范围：消费分期 17 个仓库及 WorkHub 登记的独立合同服务；分支为附录二记录的 origin/master。检索覆盖完整 dataId、应用名、prefix/name、shared/extension 配置、配置读取 API 和启动脚本。

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

**代码证据定位**（行号对应本次核查的远程提交；提交列表见附录二）：

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

## 修订记录

| 版本 | 日期 | 变更内容 | 历史文件 |
| --- | --- | --- | --- |
| v2.8 | 2026-10-22 | 将已确认系统现状合并到首节目标表，按组件并列展示当前版本、目标版本及执行要求；拆分 Nacos 和 XXL 的服务端、客户端版本。 | 当前文档 |
| v2.7 | 2026-10-22 | 按业务方要求，Nacos 与 XXL 调度中心均维持单节点；同步调整资源项、部署步骤、端口范围及重启恢复验收，保留一次性切换和整体回退。 | [v2.7 Markdown](历史版本/v2.7-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.6 | 2026-10-22 | 整理为部门执行版：集中已确认现状、待落实决策与交付物，明确部门职责及上线条件；合并重复叙述，保留 14 项改造和完整证据。 | [v2.6 Markdown](历史版本/v2.6-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.5 | 2026-10-22 | 完成两条配置的代码引用核查及业务调用影响分析。 | [v2.5 Markdown](历史版本/v2.5-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.4 | 2026-10-22 | 确认 MySQL 8.0.28-231003；按约 2 小时规划维护窗口；说明合同及风控场景配置对迁移范围和验证的影响。 | [v2.4 Markdown](历史版本/v2.4-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.3 | 2026-10-22 | 补充用户确认的生产版本、JDK、单节点 MySQL 和独占范围；增加 Safari 核查的配置、服务、执行器及任务数量，明确剩余信息与实施产物。 | [v2.3 Markdown](历史版本/v2.3-20261022/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.2 | 2026-10-20 | 改为 Markdown 主文档；新增版本切换变化、任务参数和失败处理影响、升级收益及实施影响，保留一次性切换安排。 | [v2.2 Markdown](历史版本/v2.2-20261022修订前/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.1 | 2026-10-19 | 调整为同一维护窗口统一发布、联合验收和整体回退。 | [v2.1 Word](历史版本/v2.1-20261019/消费分期Nacos和XXLJOB升级实施方案_20261019.docx) |
| v2.0 | 2026-10-19 | 整理消费分期系统现状、14 项改造、任务清单及技术依据。 | [v2.0 Word](历史版本/v2.0-20261019/消费分期Nacos和XXLJOB升级实施方案_20261019.docx) |
