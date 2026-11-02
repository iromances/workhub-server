# 物流平台 叉车 智能柜 Nacos 和 XXL-JOB 升级总方案

版本：v1.0；日期：2026-10-30；状态：方案草案，供研发和发布评审。需求编码：REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba。

参照用户提供的消费分期主方案 v1.6 与改动清单，目标为 **Nacos 服务端和客户端 2.5.4、官方原版 XXL-JOB 2.5.0**。三个业务线分别处理依赖、任务入口和环境配置，沿用原业务规则。物流平台以 `thctay-public` 为主；叉车和智能柜必须同时处理 `lease-public` 体系及独立使用 `thctay-public` 的应用。

| 业务线 | WorkHub 编码与项目组 | 业务方案 | 主分支源码任务入口数 |
| --- | --- | --- | ---: |
| 物流平台 | BL000006 / xxt-plateform | [物流平台升级方案与改动点](物流平台Nacos和XXLJOB升级方案改动点_20261030.md) | 256，含共享合同 5、共享业财 16；扣除这两项为 235 |
| 叉车 | BL000008 / lease-saas | [叉车升级方案与改动点](叉车Nacos和XXLJOB升级方案改动点_20261030.md) | 71 |
| 智能柜 | BL000005 / ol | [智能柜升级方案与改动点](智能柜Nacos和XXLJOB升级方案改动点_20261030.md) | 67 |

两个公共 MQ 各有 1 个 `messageSenderRetrySchedule` 源码入口，单独计数；同一公共任务可能随多个执行器部署。源码共核对三个项目组的 92 个仓库，394 个应用方法注解及 2 个公共方法注解。**这些数字不是调度中心任务记录数、在线执行器数或生产启用数**；同一 handler 可以对应多条调度记录，未启用任务和一次性补偿也包含在源码清单中。

## 1 核对范围与基线

- 业务线和仓库映射来自 WorkHub `get_business_line_context`；使用 `sync_gitlab_group_repositories` 刷新后，按 `origin/master` 的固定提交读取 POM、Java 和 bootstrap 文件。工作区未提交修改未作为基线。
- 目标环境按测试验证、生产切换两阶段规划。本次未连接三条业务线的运行实例或调度数据库，源码基线不证明生产当前部署版本。当前服务端版本、MySQL/JDK 实际版本、目标地址、资源及发布分支在发布准备阶段登记。
- 未指定关联审批或研发需求编号；本次新建跨业务线方案整理条目，归档于 BL000006 目录，覆盖另外两条业务线，不改写消费分期既有需求及其审批历史。
- `etc-doc`、`etc-tools`、`mock-server` 无 `origin/master`；补充查看其已有默认分支只用于识别范围，不替代生产基线。`lease-scaffold` 没有可核对的远程分支。细节见物流和叉车方案。
- 各仓库完整 SHA、配置文件位置、入口名称、直接调用位置见各业务线源码清单及 [机器可核对清单](证据/源码核对清单.json)。计数剔除 Java 注释及 `src/test`，公共 MQ 单列。

## 2 与消费分期示例的差异

| 项目 | 本次处理 |
| --- | --- |
| 公共依赖 | 复用已核对的 `thctay-public` 升级分支；另行改造 `lease-dependencies`、`lease-schedule`、`lease-mq` |
| 日志留存冲突 | 示例主方案写 90 天，示例改动清单写 60 天。当前远程 public 升级提交 `d44d555c` 的代码为 90 天，两套 public 的 master 均为 30 天。本稿拟统一目标默认 90 天；部署显式值须一并核对，不能声称已生效 |
| 公共 MQ 参数 | thctay 的重投任务不使用旧参数；lease 的任务将参数解析为 `MessageSenderRetryScheduleDTO`，必须保留 |
| 中间父 POM | 物流 `intf-sence-back` 用 bpm-framework 0.0.2，`thctay-risk-backend` 用 0.0.1；还有 checker 继承 thctay-saps 的链路 |
| 不继承 public 的 Nacos 应用 | `lease-workflow`、`ol-marketing` 单独管理 Nacos 客户端版本，不能只改 public |
| 手工入口返回值 | `ol-saps` 的押金历史报表接口消费任务返回码，应保留其有参业务返回值；调度入口另设无参 void |
| 服务边界 | 合同、业财和支付等共享服务依据真实部署关系分批；不能把消费分期“外部系统全部不迁”的结论直接套给物流平台 |
| 技术栈 | public 体系保留 Java 8 / Boot 2.2.9 / Cloud Hoxton.SR7 / Alibaba 2.2.1；workflow 使用更早的独立 BOM；common-order 为 Java 21 / Boot 3.3.5，未发现本次中间件引用，不强行改为 Java 8 |

`thctay-public` 目标分支为已有 `origin/release/nacos-2.5.4-xxljob-2.5.0_20261027`，当前核对 SHA 见物流附录；应用仓库及 lease-public 未查到该同名远程分支。示例列出的本地提交不是本次统一构建凭据，实施时需冻结每个仓库的升级分支及实际构建 SHA。

## 3 公共依赖版本与构建顺序

| 构件 | 本次基线 | 目标方案 |
| --- | --- | --- |
| com.thchengtay:thctay-dependencies | 0.0.1-SNAPSHOT | 0.0.2-SNAPSHOT，已在上述远程升级分支声明 |
| com.thchengtay:thctay-schedule / thctay-mq | 0.0.1-SNAPSHOT | 0.0.2-SNAPSHOT，复用适配源码 |
| com.lease:lease-dependencies | 0.0.1-SNAPSHOT | 拟 0.0.2-SNAPSHOT，新增 nacos-client 2.5.4 管理，core 改为 2.5.0 |
| com.lease:lease-schedule / lease-mq | 0.0.1-SNAPSHOT | 拟 0.0.2-SNAPSHOT，同步父版本、公共任务及调度配置 |
| com.thchengtay:bpm-framework | 根工程 0.0.2-SNAPSHOT；使用方还存在 0.0.1 | 拟 0.0.3-SNAPSHOT，父用新版 thctay-dependencies，核对全部子模块及使用方 |
| com.thchengtay:thctay-saps | 0.0.1-SNAPSHOT，且被 checker 跨仓库继承 | 本稿建议父及继承版本的内部模块整体升至 0.0.2-SNAPSHOT，checker 对齐，避免覆盖旧父 POM |
| 两套 public 的根聚合工程及未受影响模块 | 原版本 | 保留；不对 common、cache、tools、security、permission 等统一升号 |
| nacos-client / xxl-job-core | public 的 Alibaba BOM 管理 Nacos 1.2.1；core 显式 2.2.0 | 2.5.4 / 官方原版 2.5.0；最终以依赖树与成品包验证 |

上表“拟”版本须在实施时核对制品库占用并冻结；它们不是本轮已发布制品。父 POM 的 `dependencyManagement` 仅管理版本，不应给所有服务强加 schedule 或 MQ 依赖。保留已有 starter 与业务依赖路径，不同时引入两套执行器配置。

构建顺序：准备现有旧版及未受影响公共件 → 构建新版 dependencies → schedule → MQ → bpm-framework / thctay-saps 等中间父工程与模块 → 各业务应用及 checker。Jenkins 与本地分别锁定提交和构件校验值；不能靠本地 Maven 仓库“刚好已有新版”保证 CI 正确。升级公共件使用新版本坐标，保留旧制品回退；本轮仅形成方案，没有执行依赖安装或构建。

## 4 任务入口统一适配规则

1. 每个 `@XxlJob` 方法改为无参 `void`，保留 handler 的原字符串、大小写、Bean 名和注册方式。清单中的 protected 方法也必须处理；建议统一为 public 调度入口。
2. 原方法实际使用参数时，调度入口以 `XxlJobHelper.getJobParam()` 获取，保持原解析、默认日期、筛选条件及空参数行为。未使用参数的不新增读取。
3. 原成功返回改为正常结束，提前成功改为 `return;`。原失败返回转换为 `XxlJobHelper.handleFail(...)` 并结束，或保留异常向外传播；既有逐笔捕获、部分失败继续等业务策略保持原义。需要保留成功结果消息时使用 helper 写入结果。
4. `extends IJobHandler` 的类保留继承和 `@Override`，实现无参 `void execute()`；应用自定义基类分别核对，不能以删除公共 BaseJobHandler 代替所有基类处理。
5. 对 Controller、服务、测试直接调用的任务，保留无注解、有显式参数的业务方法。调用方使用返回值时保留其结果对象和失败语义，调度 wrapper 负责把结果转换为 XXL-JOB 状态；手工调用不得依赖调度上下文。仅修改调度注解所在的方法，不能全仓删除 `ReturnT`。
6. 在用 `XxlJobLogger.log` 改为 `XxlJobHelper.log`；普通业务日志保留。覆盖 `@RefreshScope` 代理在启动和配置刷新后的真实执行。
7. 原调度方法若返回 null，先按旧运行时语义处理，不直接改成正常结束。已确认 thctay-crm 的 InsuranceServerSchedule，以及 ol-order 的 LeaseApplyPolicyJob 有这类方法返回；本稿按旧失败反馈映射为 handleFail，若业务希望修正为成功须明确记录为行为变更。不要把异步 lambda 内的 return null 误判为任务返回值。委托业务方法返回 ReturnT 的入口也要透传映射其失败码。

旧版 null 返回按失败处理的依据为 [2.2.0 JobThread](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/xxl-job-core/src/main/java/com/xxl/job/core/thread/JobThread.java) 的结果判断。

官方 2.5.0 的 [MethodJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java) 不消费方法返回值，并对有参方法传入空参数；仅换 JAR 会丢失参数或失败反馈。[IJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/IJobHandler.java) 和 [XxlJobHelper](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/context/XxlJobHelper.java) 是本稿入口及结果映射依据。

## 5 Nacos 配置与数据迁移

### 5.1 应用配置

| 配置面 | 改动与验收 |
| --- | --- |
| config / discovery | 两处均切换对应环境的新地址并提供客户端鉴权；凭据在 bootstrap 阶段可用，不只放入尚未读取的远程配置 |
| 应用标识 | 保留 application.name、config 的 prefix/name、namespace ID、服务 group、配置 group、文件扩展名和 profile 加载规则 |
| 多配置文件 | 同时核对 bootstrap.yml、bootstrap-test.yml、bootstrap-prod.yml、打包 profile 和启动参数，不能只改一个默认文件 |
| shared / extension / ext-config | 逐项迁移 Data ID、group、refresh 设置；配置 group 与发现 group 分别核对，不因配置用 COMMON_GROUP 就给发现也填同值 |
| 远程与部署覆盖 | 静态源码中的地址可能为本机占位值；以实际启动参数、部署文件、环境和远程配置确认生效值，不把源码值写成生产实况 |
| 网络 | 按实际主端口验证客户端 gRPC 偏移端口；同时验证注册、订阅、配置拉取与刷新、断线重连和消费端真实调用 |

所核对主分支 bootstrap 文件未发现显式 username/password；这不等于运行环境没有注入鉴权。沿用各系统既有配置交付机制，不强制改造为环境变量，也不将示例的测试地址、账号、Token 复制到三条业务线。

Nacos 默认主端口 8848 对应客户端 gRPC 9848；自定义主端口 P 时通常对应 P+1000，代理需支持 TCP 转发。目标端口映射与集群内部通信按实际部署验证，依据 [Nacos 2.5 部署说明](https://nacos.io/docs/v2.5/manual/admin/deployment/deployment-overview/)。服务注册成功不代表调用方已取得健康实例。

### 5.2 服务端与迁移数据

采用新实例、新库和保留旧实例的迁移方式；生产单机或集群按该业务线现有部署与可用性要求确定，不沿用消费分期单节点作为已定事实。源库名、源版本、鉴权方式及租户范围核实后，按 2.5.4 官方 schema 形成逐列迁移方案。

核对 namespace、配置正文与 MD5、Data ID/group、共享配置、容量、标签、历史记录；已有账号/角色/权限及服务端鉴权身份配置另行核对。原示例的六表复制清单及 `amp_nacos` 库名不能当作三个目标库的完整迁移脚本。相同 namespace 名称也不能替代 namespace ID 核对。迁移期间冻结配置写入或安排增量差异补齐。

叉车与智能柜部分源码中的生产 Nacos 地址和 namespace 相同，并存在相互调用；这提示可能共用注册域，须由运行配置确认。若共用，按一套注册域统筹窗口或先保证跨阶段调用可达，不将两个中心割裂切换。

## 6 XXL-JOB 调度中心与配置迁移

新建官方 2.5.0 调度中心及独立库，保留旧库和旧中心。物流组现有 xxl-job-admin 2.2.0 源码还接入旧 Nacos starter，应作为独立系统迁移：以新官方中心为基线承接数据库、通知及部署配置，并评估需要保留的定制功能，不能只修改旧中心的 parent 版本。

| 迁移对象 | 方案 |
| --- | --- |
| 执行器分组 | 保留 ID/appname；自动地址由新执行器注册，手工地址逐项确认，不清空需要手工维护的地址 |
| 任务 | 逐列复制并保留 handler、参数、Cron、路由、阻塞、超时、失败重试、子任务关系、GLUE 类型与代码；按真实旧版本映射 schedule_type/conf、misfire_strategy 等字段 |
| 启停状态 | 单独保存旧状态快照，新任务全部保持停止；切换后只恢复原启用项及经确认的依赖任务 |
| 用户与权限 | 保留经确认的账号及执行器权限；跨业务线合并中心时先评估 ID 与权限冲突，不直接拼接多个源库 |
| 注册、日志、统计 | 旧注册信息不复制，历史日志统计留在旧库查询；确认新库调度锁记录及必要索引 |
| 通信配置 | admin.addresses 为新中心含正确 context path；非空 accessToken 在同环境中心与执行器一致；保持原 appname，检查 address/ip/port 和回调双向连通 |
| 执行日志 | 检查可写路径及显式 logretentiondays；拟统一默认 90 天；配置类在启动时初始化，按部署重启生效 |

表结构以 [XXL-JOB 2.5.0 官方脚本](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql) 为准。使用独立业务目标库并核对脚本中的 CREATE DATABASE/USE；只选建表部分，不直接执行官方 init data，不套用 `amp_xxl_job` 或示例任务 ID。只有真实源结构确认后，才生成三条业务线的迁移 SQL。

## 7 验证与切换

| 阶段 | 必须通过的检查 |
| --- | --- |
| 静态与构建 | 对照源码入口清单核对 handler 不变；业务源码及测试源码编译；effective POM、依赖树、可执行包只保留目标 core/client 与正确公共件；检查 Netty/Gson/Jackson/日志依赖组合 |
| 隔离入口测试 | 使用官方 MethodJobHandler 和业务桩覆盖正常参数、空参数、解析失败、无数据、失败反馈、异常传播、失败后再次执行、手工调用与返回值消费；不以调用真实付款接口验证 wrapper |
| 测试环境 | 配置鉴权、注册与订阅、跨服务调用、刷新与重连；执行器注册、触发、日志、回调、超时、停止恢复；原重试、幂等、锁与并发批次边界不变 |
| 数据迁移演练 | 配置键与内容校验；任务记录与 handler 映射；原启停快照、执行器、权限和子任务关系一致；演练期间仅一套中心调度 |
| 切换门槛 | 目标参数、部署包、SHA、任务快照、共享依赖、维护窗口、补跑清单和回退负责人齐备；测试结论已记录 |

切换顺序：冻结配置与调度变更并做快照 → 停旧中心对应任务 → 等待在途任务及旧回调完成 → 启动目标应用并切换 Nacos/XXL 配置 → 验证关键调用和新执行器 → 按快照逐项恢复。不得让新旧中心同时触发同一业务任务；停止调度不会自动撤销在途付款、通知或锁。

若出现持续配置拉取失败、服务不可发现、核心调用失败、任务回调错误或重复业务处理，停止新触发，处理在途任务及回调后，成套恢复旧应用包、旧公共件、旧 Nacos 和旧中心配置。Nacos 2.x 客户端不能仅改回 1.x 服务端地址，见 [官方兼容性说明](https://nacos.io/docs/v2.5/upgrading/200-compatibility/)。回退不自动撤销已产生的业务数据，按实际业务流水核对补偿和补跑。

## 8 待落实事项与交付状态

| 待落实事项 | 影响与完成时点 |
| --- | --- |
| 实际在运应用、共享实例及跨线调用 | 冻结迁移清单前；含 thctay-contract/eas、lease-contract/marketing/intfcommon 等 |
| 旧新服务端版本、实例、库名、部署方式及端口 | 编写迁移 SQL 与测试环境部署前 |
| 各环境启动配置、鉴权与调度 Token | 联调前，凭据通过既有受控配置提供 |
| 新构件版本占用、实际发布分支及提交 | 构建前，尤其 lease-public、bpm-framework、thctay-saps/checker |
| 源库任务记录、启停、子任务、GLUE、失败补跑 | 演练前；一次性回填任务不得按源码存在就默认启用 |
| workflow 旧栈兼容性及物流定制 admin 功能差异 | 联合回归前；兼容问题单独评估，不默默扩大为所有业务升级 Spring |
| 发布窗口、回退决策与补跑责任 | 生产切换前 |

本轮完成文档和静态源码核对，没有改动业务源码、运行配置或数据库，也没有执行构建、任务触发和部署。因此不登记为系统已升级，不改写现有系统设计中的运行版本。实施任务与验收计划见 [Tasks](tasks.md)，范围与成功标准见 [Proposal](proposal.md)。

原始参考文件及 SHA-256 见 [来源登记](证据/来源与范围.json)；原件保持原位置和原字节。
